/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.api.executor.workflow;

import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.WorkerGroupService;
import org.apache.dolphinscheduler.api.service.WorkflowLineageService;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowDTO;
import org.apache.dolphinscheduler.common.enums.ComplementDependentMode;
import org.apache.dolphinscheduler.common.enums.ExecutionOrder;
import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.common.enums.RunMode;
import org.apache.dolphinscheduler.common.model.Server;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.repository.CommandDao;
import org.apache.dolphinscheduler.dao.repository.WorkflowDefinitionDao;
import org.apache.dolphinscheduler.extract.base.client.Clients;
import org.apache.dolphinscheduler.extract.master.IWorkflowControlClient;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import org.apache.dolphinscheduler.plugin.task.api.model.DateInterval;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.utils.DependentUtils;
import org.apache.dolphinscheduler.registry.api.RegistryClient;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.apache.dolphinscheduler.service.process.ProcessService;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Slf4j
@Component
public class BackfillWorkflowExecutorDelegate implements IExecutorDelegate<BackfillWorkflowDTO, List<Integer>> {

    @Autowired
    private CommandDao commandDao;

    @Autowired
    private ProcessService processService;

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private WorkflowLineageService workflowLineageService;

    @Autowired
    private WorkerGroupService workerGroupService;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Override
    public List<Integer> execute(final BackfillWorkflowDTO backfillWorkflowDTO) {
        // todo: directly call the master api to do backfill
        if (backfillWorkflowDTO.getBackfillParams().getRunMode() == RunMode.RUN_MODE_SERIAL) {
            return doSerialBackfillWorkflow(backfillWorkflowDTO);
        } else {
            return doParallelBackfillWorkflow(backfillWorkflowDTO);
        }
    }

    private List<Integer> doSerialBackfillWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO) {
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        final List<ZonedDateTime> backfillTimeList = backfillParams.getBackfillDateList();
        if (backfillParams.getExecutionOrder() == ExecutionOrder.DESC_ORDER) {
            Collections.sort(backfillTimeList, Collections.reverseOrder());
        } else {
            Collections.sort(backfillTimeList);
        }

        final Integer workflowInstanceId = doBackfillWorkflow(
                backfillWorkflowDTO,
                backfillTimeList.stream().map(DateUtils::dateToString).collect(Collectors.toList()));
        return Lists.newArrayList(workflowInstanceId);
    }

    private List<Integer> doParallelBackfillWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO) {
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        Integer expectedParallelismNumber = backfillParams.getExpectedParallelismNumber();

        List<ZonedDateTime> listDate = backfillParams.getBackfillDateList();
        if (expectedParallelismNumber != null) {
            expectedParallelismNumber = Math.min(listDate.size(), expectedParallelismNumber);
        } else {
            expectedParallelismNumber = listDate.size();
        }

        log.info("In parallel mode, current expectedParallelismNumber:{}", expectedParallelismNumber);
        final List<Integer> workflowInstanceIdList = Lists.newArrayList();
        for (List<ZonedDateTime> stringDate : Lists.partition(listDate, expectedParallelismNumber)) {
            final Integer workflowInstanceId = doBackfillWorkflow(
                    backfillWorkflowDTO,
                    stringDate.stream().map(DateUtils::dateToString).collect(Collectors.toList()));
            workflowInstanceIdList.add(workflowInstanceId);
        }
        return workflowInstanceIdList;
    }

    private Integer doBackfillWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO,
                                       final List<String> backfillTimeList) {
        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }

        final WorkflowDefinition workflowDefinition = backfillWorkflowDTO.getWorkflowDefinition();
        final WorkflowBackfillTriggerRequest backfillTriggerRequest = WorkflowBackfillTriggerRequest.builder()
                .userId(backfillWorkflowDTO.getLoginUser().getId())
                .backfillTimeList(backfillTimeList)
                .workflowCode(workflowDefinition.getCode())
                .workflowVersion(workflowDefinition.getVersion())
                .startNodes(backfillWorkflowDTO.getStartNodes())
                .failureStrategy(backfillWorkflowDTO.getFailureStrategy())
                .taskDependType(backfillWorkflowDTO.getTaskDependType())
                .warningType(backfillWorkflowDTO.getWarningType())
                .warningGroupId(backfillWorkflowDTO.getWarningGroupId())
                .workflowInstancePriority(backfillWorkflowDTO.getWorkflowInstancePriority())
                .workerGroup(backfillWorkflowDTO.getWorkerGroup())
                .tenantCode(backfillWorkflowDTO.getTenantCode())
                .environmentCode(backfillWorkflowDTO.getEnvironmentCode())
                .startParamList(backfillWorkflowDTO.getStartParamList())
                .dryRun(backfillWorkflowDTO.getDryRun())
                .build();

        final WorkflowBackfillTriggerResponse backfillTriggerResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .backfillTriggerWorkflow(backfillTriggerRequest);
        if (!backfillTriggerResponse.isSuccess()) {
            throw new ServiceException("Backfill workflow failed: " + backfillTriggerResponse.getMessage());
        }
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        if (backfillParams.getBackfillDependentMode() == ComplementDependentMode.ALL_DEPENDENT) {
            doBackfillDependentWorkflow(backfillWorkflowDTO, backfillTimeList);
        }
        return backfillTriggerResponse.getWorkflowInstanceId();
    }

    /**
     * Trigger backfill for dependent workflows
     * This method finds all downstream dependent workflows and triggers backfill for each of them
     * with the same backfill time list
     *
     * @param backfillWorkflowDTO the backfill workflow DTO
     * @param backfillTimeList the backfill time list
     */
    private void doBackfillDependentWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO,
                                             final List<String> backfillTimeList) {
        final WorkflowDefinition workflowDefinition = backfillWorkflowDTO.getWorkflowDefinition();
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();

        // Get allLevelDependent parameter
        boolean allLevelDependent = backfillParams.isAllLevelDependent();

        // Get dependent workflow definitions list (no cycle matching required)
        List<DependentWorkflowDefinition> dependentWorkflowDefinitionList =
                getComplementDependentDefinitionList(workflowDefinition.getCode(),
                        backfillWorkflowDTO.getWorkerGroup(),
                        allLevelDependent);

        if (dependentWorkflowDefinitionList.isEmpty()) {
            log.info("No dependent workflows found for workflow definition code: {}.",
                    workflowDefinition.getCode());
            return;
        }

        log.info("Found {} dependent workflows for workflow definition code: {}, will trigger backfill for each.",
                dependentWorkflowDefinitionList.size(), workflowDefinition.getCode());

        // Trigger backfill for each dependent workflow
        for (DependentWorkflowDefinition dependentWorkflowDefinition : dependentWorkflowDefinitionList) {
            try {
                // Calculate backfill dates for dependent workflow based on dependency cycle and dateValue
                List<String> dependentBackfillDates = calculateDependentBackfillDates(
                        workflowDefinition.getCode(),
                        backfillTimeList,
                        dependentWorkflowDefinition);

                if (dependentBackfillDates.isEmpty()) {
                    log.info(
                            "No valid backfill dates calculated for dependent workflow definition code: {}, skip triggering.",
                            dependentWorkflowDefinition.getWorkflowDefinitionCode());
                    continue;
                }

                triggerDependentWorkflowBackfill(backfillWorkflowDTO, dependentWorkflowDefinition,
                        dependentBackfillDates);
            } catch (Exception e) {
                log.error("Failed to trigger backfill for dependent workflow definition code: {}, error: {}",
                        dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage(), e);
                // Continue with other dependent workflows even if one fails
            }
        }
    }

    /**
     * Get complement dependent online workflow definition list with duplicate prevention
     * No cycle matching required, only calculate backfill dates based on dependency cycle and dateValue
     *
     * @param workflowDefinitionCode the workflow definition code
     * @param workerGroup the worker group
     * @param allLevelDependent whether to trigger all levels of dependencies
     * @return list of dependent workflow definitions
     */
    private List<DependentWorkflowDefinition> getComplementDependentDefinitionList(
                                                                                   long workflowDefinitionCode,
                                                                                   String workerGroup,
                                                                                   boolean allLevelDependent) {
        List<DependentWorkflowDefinition> dependentWorkflowDefinitionList =
                setWorkerGroupForDependentWorkflows(
                        workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowDefinitionCode),
                        workerGroup);

        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        // Filter out the current workflow itself to avoid self-triggering
        dependentWorkflowDefinitionList = dependentWorkflowDefinitionList.stream()
                .filter(def -> def.getWorkflowDefinitionCode() != workflowDefinitionCode)
                .collect(Collectors.toList());

        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        if (!allLevelDependent) {
            // Only direct downstream (Level 1)
            return dependentWorkflowDefinitionList;
        }

        // For all level dependent, traverse downstream workflows recursively
        // Use Set to prevent duplicate workflow definitions
        Set<Long> processedWorkflowCodes = new HashSet<>();
        for (DependentWorkflowDefinition def : dependentWorkflowDefinitionList) {
            processedWorkflowCodes.add(def.getWorkflowDefinitionCode());
        }

        List<DependentWorkflowDefinition> childList = new ArrayList<>(dependentWorkflowDefinitionList);

        while (true) {
            List<DependentWorkflowDefinition> childDependentList = new ArrayList<>();

            for (DependentWorkflowDefinition dependentWorkflowDefinition : childList) {
                List<DependentWorkflowDefinition> downstreamList =
                        setWorkerGroupForDependentWorkflows(
                                workflowLineageService.queryDownstreamDependentWorkflowDefinitions(
                                        dependentWorkflowDefinition.getWorkflowDefinitionCode()),
                                workerGroup);

                for (DependentWorkflowDefinition downstream : downstreamList) {
                    // Duplicate prevention: only add if not already processed
                    if (processedWorkflowCodes.add(downstream.getWorkflowDefinitionCode())) {
                        childDependentList.add(downstream);
                    } else {
                        log.debug("Skipping already processed workflow definition code: {}",
                                downstream.getWorkflowDefinitionCode());
                    }
                }
            }

            if (childDependentList.isEmpty()) {
                // No more downstream workflows, all levels have been traversed
                break;
            }

            dependentWorkflowDefinitionList.addAll(childDependentList);
            childList = new ArrayList<>(childDependentList);
        }

        // Filter out the current workflow itself again (in case it appears in recursive traversal)
        List<DependentWorkflowDefinition> filteredList = dependentWorkflowDefinitionList.stream()
                .filter(def -> def.getWorkflowDefinitionCode() != workflowDefinitionCode)
                .collect(Collectors.toList());

        if (filteredList.size() < dependentWorkflowDefinitionList.size()) {
            log.warn("Filtered out {} self-dependent workflow(s) from dependent list for workflow definition code: {}",
                    dependentWorkflowDefinitionList.size() - filteredList.size(), workflowDefinitionCode);
        }

        log.info("Found {} dependent workflow definitions (allLevelDependent={}) for workflow definition code: {}",
                filteredList.size(), allLevelDependent, workflowDefinitionCode);
        return filteredList;
    }

    /**
     * Set worker group for dependent workflows if not already set
     */
    private List<DependentWorkflowDefinition> setWorkerGroupForDependentWorkflows(
                                                                                  List<DependentWorkflowDefinition> dependentWorkflowDefinitionList,
                                                                                  String workerGroup) {
        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        List<Long> workflowDefinitionCodeList =
                dependentWorkflowDefinitionList.stream().map(DependentWorkflowDefinition::getWorkflowDefinitionCode)
                        .collect(Collectors.toList());

        Map<Long, String> workflowDefinitionWorkerGroupMap =
                workerGroupService.queryWorkerGroupByWorkflowDefinitionCodes(workflowDefinitionCodeList);

        for (DependentWorkflowDefinition dependentWorkflowDefinition : dependentWorkflowDefinitionList) {
            if (workflowDefinitionWorkerGroupMap
                    .get(dependentWorkflowDefinition.getWorkflowDefinitionCode()) == null) {
                dependentWorkflowDefinition.setWorkerGroup(workerGroup);
            }
        }

        return dependentWorkflowDefinitionList;
    }

    /**
     * Calculate backfill dates for dependent workflow based on dependency cycle and dateValue
     * 
     * @param upstreamWorkflowCode the upstream workflow definition code
     * @param upstreamBackfillDates the upstream workflow backfill dates
     * @param dependentWorkflowDefinition the dependent workflow definition
     * @return list of backfill dates for dependent workflow
     */
    private List<String> calculateDependentBackfillDates(long upstreamWorkflowCode,
                                                         List<String> upstreamBackfillDates,
                                                         DependentWorkflowDefinition dependentWorkflowDefinition) {
        List<String> dependentBackfillDates = new ArrayList<>();

        // Get dependency cycle and dateValue from dependent workflow definition
        String dateValue = getDependentDateValue(upstreamWorkflowCode, dependentWorkflowDefinition);
        if (dateValue == null || dateValue.isEmpty()) {
            log.warn(
                    "Cannot get dateValue for dependent workflow definition code: {}, upstream workflow code: {}, using original backfill dates.",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(), upstreamWorkflowCode);
            return upstreamBackfillDates;
        }

        Date currentDate = new Date();

        for (String upstreamDateStr : upstreamBackfillDates) {
            try {
                Date upstreamDate = DateUtils.stringToDate(upstreamDateStr);
                if (upstreamDate == null) {
                    log.warn("Invalid upstream backfill date: {}, skip.", upstreamDateStr);
                    continue;
                }

                // Calculate date intervals based on dateValue
                List<DateInterval> dateIntervals = DependentUtils.getDateIntervalList(upstreamDate, dateValue);

                for (DateInterval dateInterval : dateIntervals) {
                    // Use the start time of the interval as the backfill date
                    Date dependentDate = dateInterval.getStartTime();

                    // Check if the date is in the future (after today)
                    // Only skip future dates, as upstream has changed, downstream should backfill
                    if (dependentDate.after(currentDate)) {
                        log.info("Calculated dependent backfill date {} is in the future, skip for upstream date {}.",
                                DateUtils.dateToString(dependentDate), upstreamDateStr);
                        continue;
                    }

                    // Add to backfill dates list (avoid duplicates)
                    // Since upstream has changed, downstream should backfill regardless of upstream instance status
                    String dependentDateStr = DateUtils.dateToString(dependentDate);
                    if (!dependentBackfillDates.contains(dependentDateStr)) {
                        dependentBackfillDates.add(dependentDateStr);
                    }
                }
            } catch (Exception e) {
                log.error("Error calculating dependent backfill date for upstream date: {}, error: {}",
                        upstreamDateStr, e.getMessage(), e);
            }
        }

        log.info("Calculated {} backfill dates for dependent workflow definition code: {} based on dependency cycle.",
                dependentBackfillDates.size(), dependentWorkflowDefinition.getWorkflowDefinitionCode());
        return dependentBackfillDates;
    }

    /**
     * Get dateValue from dependent workflow definition for the specified upstream workflow
     */
    private String getDependentDateValue(long upstreamWorkflowCode,
                                         DependentWorkflowDefinition dependentWorkflowDefinition) {
        try {
            org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters dependentParameters =
                    dependentWorkflowDefinition.getDependentParameters();
            if (dependentParameters == null || dependentParameters.getDependence() == null) {
                return null;
            }

            List<org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel> dependentTaskModelList =
                    dependentParameters.getDependence().getDependTaskList();

            for (org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel dependentTaskModel : dependentTaskModelList) {
                List<DependentItem> dependentItemList = dependentTaskModel.getDependItemList();
                for (DependentItem dependentItem : dependentItemList) {
                    if (upstreamWorkflowCode == dependentItem.getDefinitionCode()) {
                        return dependentItem.getDateValue();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting dateValue from dependent workflow definition code: {}, error: {}",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage(), e);
        }
        return null;
    }

    /**
     * Trigger backfill for a single dependent workflow
     */
    private void triggerDependentWorkflowBackfill(final BackfillWorkflowDTO originalBackfillDTO,
                                                  final DependentWorkflowDefinition dependentWorkflowDefinition,
                                                  final List<String> backfillTimeList) {
        // Check if the dependent workflow is online
        long dependentWorkflowCode = dependentWorkflowDefinition.getWorkflowDefinitionCode();
        WorkflowDefinition dependentWorkflow = workflowDefinitionDao.queryByCode(dependentWorkflowCode).orElse(null);
        if (dependentWorkflow == null) {
            log.warn("Dependent workflow definition not found, workflowDefinitionCode: {}, skip triggering backfill.",
                    dependentWorkflowCode);
            return;
        }

        if (!ReleaseState.ONLINE.equals(dependentWorkflow.getReleaseState())) {
            log.warn(
                    "Dependent workflow definition is not online, workflowDefinitionCode: {}, releaseState: {}, skip triggering backfill.",
                    dependentWorkflowCode, dependentWorkflow.getReleaseState());
            return;
        }

        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }

        // Build backfill request for dependent workflow
        final WorkflowBackfillTriggerRequest dependentBackfillRequest = WorkflowBackfillTriggerRequest.builder()
                .userId(originalBackfillDTO.getLoginUser().getId())
                .backfillTimeList(backfillTimeList)
                .workflowCode(dependentWorkflowDefinition.getWorkflowDefinitionCode())
                .workflowVersion(dependentWorkflowDefinition.getWorkflowDefinitionVersion())
                .startNodes(dependentWorkflowDefinition.getTaskDefinitionCode() != 0
                        ? Lists.newArrayList(dependentWorkflowDefinition.getTaskDefinitionCode())
                        : null)
                .failureStrategy(originalBackfillDTO.getFailureStrategy())
                .taskDependType(originalBackfillDTO.getTaskDependType())
                .warningType(originalBackfillDTO.getWarningType())
                .warningGroupId(originalBackfillDTO.getWarningGroupId())
                .workflowInstancePriority(originalBackfillDTO.getWorkflowInstancePriority())
                .workerGroup(dependentWorkflowDefinition.getWorkerGroup() != null
                        ? dependentWorkflowDefinition.getWorkerGroup()
                        : originalBackfillDTO.getWorkerGroup())
                .tenantCode(originalBackfillDTO.getTenantCode())
                .environmentCode(originalBackfillDTO.getEnvironmentCode())
                .startParamList(originalBackfillDTO.getStartParamList())
                .dryRun(originalBackfillDTO.getDryRun())
                .build();

        final WorkflowBackfillTriggerResponse dependentBackfillResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .backfillTriggerWorkflow(dependentBackfillRequest);

        if (!dependentBackfillResponse.isSuccess()) {
            log.warn("Failed to trigger backfill for dependent workflow definition code: {}, message: {}",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(),
                    dependentBackfillResponse.getMessage());
        } else {
            log.info(
                    "Successfully triggered backfill for dependent workflow definition code: {}, workflow instance id: {}",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(),
                    dependentBackfillResponse.getWorkflowInstanceId());
        }
    }
}
