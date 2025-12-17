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

import org.apache.dolphinscheduler.api.dto.workflow.WorkflowBackFillRequest;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.WorkflowLineageService;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowDTO;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowRequestTransformer;
import org.apache.dolphinscheduler.common.enums.ComplementDependentMode;
import org.apache.dolphinscheduler.common.enums.ExecutionOrder;
import org.apache.dolphinscheduler.common.enums.FailureStrategy;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.Priority;
import org.apache.dolphinscheduler.common.enums.ReleaseState;
import org.apache.dolphinscheduler.common.enums.RunMode;
import org.apache.dolphinscheduler.common.enums.TaskDependType;
import org.apache.dolphinscheduler.common.enums.WarningType;
import org.apache.dolphinscheduler.common.model.Server;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.Schedule;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.repository.WorkflowDefinitionDao;
import org.apache.dolphinscheduler.dao.utils.WorkerGroupUtils;
import org.apache.dolphinscheduler.extract.base.client.Clients;
import org.apache.dolphinscheduler.extract.master.IWorkflowControlClient;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import org.apache.dolphinscheduler.plugin.task.api.model.DateInterval;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;
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
    private RegistryClient registryClient;

    @Autowired
    private WorkflowLineageService workflowLineageService;

    @Autowired
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Autowired
    private ProcessService processService;

    @Autowired
    private BackfillWorkflowRequestTransformer backfillWorkflowRequestTransformer;

    @Override
    public List<Integer> execute(final BackfillWorkflowDTO backfillWorkflowDTO) {
        // todo: directly call the master api to do backfill
        List<Integer> workflowInstanceIdList;
        if (backfillWorkflowDTO.getBackfillParams().getRunMode() == RunMode.RUN_MODE_SERIAL) {
            workflowInstanceIdList = doSerialBackfillWorkflow(backfillWorkflowDTO);
        } else {
            workflowInstanceIdList = doParallelBackfillWorkflow(backfillWorkflowDTO);
        }

        // Trigger dependent workflows after all root workflow instances are created
        // This ensures dependent workflows are only triggered once, regardless of parallel partitions
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        if (backfillParams.getBackfillDependentMode() == ComplementDependentMode.ALL_DEPENDENT) {
            doBackfillDependentWorkflow(backfillWorkflowDTO);
        }

        return workflowInstanceIdList;
    }

    private List<Integer> doSerialBackfillWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO) {
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();
        final List<ZonedDateTime> backfillTimeList = backfillParams.getBackfillDateList();
        if (backfillParams.getExecutionOrder() == ExecutionOrder.DESC_ORDER) {
            Collections.sort(backfillTimeList, Collections.reverseOrder());
        } else {
            Collections.sort(backfillTimeList);
        }

        final Integer workflowInstanceId = doBackfillWorkflow(backfillWorkflowDTO, backfillTimeList);
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
        for (List<ZonedDateTime> datePartition : Lists.partition(listDate, expectedParallelismNumber)) {
            final Integer workflowInstanceId = doBackfillWorkflow(backfillWorkflowDTO, datePartition);
            workflowInstanceIdList.add(workflowInstanceId);
        }
        return workflowInstanceIdList;
    }

    private Integer doBackfillWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO,
                                       final List<ZonedDateTime> backfillDateList) {
        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            throw new ServiceException("no master server available");
        }

        // Convert ZonedDateTime to String only when needed for RPC call
        List<String> backfillTimeList = backfillDateList.stream()
                .map(DateUtils::dateToString)
                .collect(Collectors.toList());

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
        return backfillTriggerResponse.getWorkflowInstanceId();
    }

    /**
     * Trigger backfill for dependent workflows recursively
     * This method finds all downstream dependent workflows and triggers backfill for each of them
     * using the same serial/parallel logic as the main workflow
     *
     * @param backfillWorkflowDTO the backfill workflow DTO
     */
    private void doBackfillDependentWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO) {
        final WorkflowDefinition workflowDefinition = backfillWorkflowDTO.getWorkflowDefinition();
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();

        boolean allLevelDependent = backfillParams.isAllLevelDependent();

        List<DependentWorkflowDefinition> allDependentWorkflows =
                getAllDependentWorkflows(
                        workflowDefinition.getCode(),
                        allLevelDependent);

        if (allDependentWorkflows.isEmpty()) {
            log.info("No dependent workflows found for workflow definition code: {}.",
                    workflowDefinition.getCode());
            return;
        }

        log.info("Found {} dependent workflows for workflow definition code: {}.",
                allDependentWorkflows.size(), workflowDefinition.getCode());

        RunMode runMode = backfillParams.getRunMode();

        for (DependentWorkflowDefinition dependentWorkflowDefinition : allDependentWorkflows) {
            try {
                // The backfill dates of dependent workflows are consistent with the main workflow.
                // In the future, we can consider calculating the backfill dates of dependent workflows
                // based on the dependency cycle.
                BackfillWorkflowDTO dependentBackfillDTO = buildDependentBackfillDTO(
                        backfillWorkflowDTO, dependentWorkflowDefinition);

                // Recursively trigger dependent workflow using the same serial/parallel logic
                if (runMode == RunMode.RUN_MODE_SERIAL) {
                    doSerialBackfillWorkflow(dependentBackfillDTO);
                } else {
                    doParallelBackfillWorkflow(dependentBackfillDTO);
                }
            } catch (Exception e) {
                log.error("Failed to trigger backfill for dependent workflow definition code: {}, error: {}",
                        dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage(), e);
            }
        }

        log.info("All {} dependent workflows have been triggered.", allDependentWorkflows.size());
    }

    /**
     * Build BackfillWorkflowDTO for dependent workflow
     * Only execution time, execution mode, and dependent mode use the original workflow's parameters.
     * Other configurations use the dependent workflow's own configuration.
     */
    private BackfillWorkflowDTO buildDependentBackfillDTO(final BackfillWorkflowDTO originalBackfillDTO,
                                                          final DependentWorkflowDefinition dependentWorkflowDefinition) {
        // Check if the dependent workflow is online
        long dependentWorkflowCode = dependentWorkflowDefinition.getWorkflowDefinitionCode();
        WorkflowDefinition dependentWorkflow = workflowDefinitionDao.queryByCode(dependentWorkflowCode).orElse(null);
        if (dependentWorkflow == null) {
            throw new ServiceException(
                    "Dependent workflow definition not found, workflowDefinitionCode: " + dependentWorkflowCode);
        }

        if (!ReleaseState.ONLINE.equals(dependentWorkflow.getReleaseState())) {
            throw new ServiceException(
                    "Dependent workflow definition is not online, workflowDefinitionCode: " + dependentWorkflowCode);
        }

        // Get Schedule for dependent workflow to retrieve configuration
        List<Schedule> schedules =
                processService.queryReleaseSchedulerListByWorkflowDefinitionCode(dependentWorkflowCode);
        Schedule schedule = schedules.isEmpty() ? null : schedules.get(0);

        // If schedule is null, create a default Schedule with default values to avoid NPE
        if (schedule == null) {
            schedule = Schedule.builder()
                    .failureStrategy(FailureStrategy.CONTINUE)
                    .warningType(WarningType.NONE)
                    .workflowInstancePriority(Priority.MEDIUM)
                    .tenantCode(originalBackfillDTO.getTenantCode())
                    .environmentCode(null)
                    .build();
        }

        // Get original workflow's parameters
        BackfillWorkflowDTO.BackfillParamsDTO originalParams = originalBackfillDTO.getBackfillParams();

        // Build BackfillTime from originalBackfillDTO's backfillDateList
        // Convert ZonedDateTime list to comma-separated date string
        String complementScheduleDateList = calculateDependentBackfillDates(originalParams.getBackfillDateList(),
                dependentWorkflowDefinition, originalBackfillDTO.getWorkflowDefinition().getCode()).stream()
                        .map(DateUtils::dateToString)
                        .collect(Collectors.joining(","));

        WorkflowBackFillRequest.BackfillTime backfillTime = WorkflowBackFillRequest.BackfillTime.builder()
                .complementScheduleDateList(complementScheduleDateList)
                .build();

        // Build WorkflowBackFillRequest for dependent workflow
        String workerGroup = WorkerGroupUtils.getWorkerGroupOrDefault(
                dependentWorkflowDefinition.getWorkerGroup());

        WorkflowBackFillRequest dependentBackfillRequest = WorkflowBackFillRequest.builder()
                .loginUser(originalBackfillDTO.getLoginUser())
                .workflowDefinitionCode(dependentWorkflowCode)
                .startNodes(null) // In backfill scenario, startNodes is null
                .failureStrategy(schedule.getFailureStrategy())
                .taskDependType(TaskDependType.TASK_POST)
                .execType(originalBackfillDTO.getExecType())
                .warningType(schedule.getWarningType())
                .warningGroupId(dependentWorkflow.getWarningGroupId())
                .workflowInstancePriority(schedule.getWorkflowInstancePriority())
                .workerGroup(workerGroup)
                .tenantCode(schedule.getTenantCode())
                .environmentCode(schedule.getEnvironmentCode())
                .startParamList(dependentWorkflow.getGlobalParams())
                .dryRun(Flag.NO)
                .backfillRunMode(originalParams.getRunMode())
                .backfillTime(backfillTime)
                .expectedParallelismNumber(originalParams.getExpectedParallelismNumber())
                // Disable recursive execution because dependent workflows are pre-extracted via
                // getAllDependentWorkflows, which also handles circular dependencies
                .backfillDependentMode(ComplementDependentMode.OFF_MODE)
                .allLevelDependent(false)
                .executionOrder(originalParams.getExecutionOrder())
                .build();

        return backfillWorkflowRequestTransformer.transform(dependentBackfillRequest);
    }

    /**
     * Get all dependent workflows (flattened list, no level grouping)
     * If allLevelDependent is true, recursively get all downstream workflows
     * If allLevelDependent is false, only get Level 1 workflows
     *
     * @param workflowDefinitionCode the workflow definition code
     * @param allLevelDependent whether to trigger all levels of dependencies
     * @return list of all dependent workflow definitions
     */
    private List<DependentWorkflowDefinition> getAllDependentWorkflows(
                                                                       long workflowDefinitionCode,
                                                                       boolean allLevelDependent) {
        List<DependentWorkflowDefinition> allWorkflows = new ArrayList<>();
        Set<Long> processedWorkflowCodes = new HashSet<>();

        // Level 1: directly dependent on upstream
        List<DependentWorkflowDefinition> level1Workflows =
                workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowDefinitionCode);

        // Filter out the current workflow itself to avoid self-triggering
        level1Workflows = level1Workflows.stream()
                .filter(def -> def.getWorkflowDefinitionCode() != workflowDefinitionCode)
                .collect(Collectors.toList());

        if (level1Workflows.isEmpty()) {
            return allWorkflows;
        }

        // Add Level 1 workflows
        for (DependentWorkflowDefinition def : level1Workflows) {
            if (processedWorkflowCodes.add(def.getWorkflowDefinitionCode())) {
                allWorkflows.add(def);
            }
        }

        if (!allLevelDependent) {
            // Only Level 1
            return allWorkflows;
        }

        // For all level dependent, recursively traverse downstream workflows
        List<DependentWorkflowDefinition> currentLevelWorkflows = new ArrayList<>(level1Workflows);

        while (true) {
            List<DependentWorkflowDefinition> nextLevelWorkflows = new ArrayList<>();

            for (DependentWorkflowDefinition dependentWorkflowDefinition : currentLevelWorkflows) {
                List<DependentWorkflowDefinition> downstreamList =
                        workflowLineageService.queryDownstreamDependentWorkflowDefinitions(
                                dependentWorkflowDefinition.getWorkflowDefinitionCode());

                for (DependentWorkflowDefinition downstream : downstreamList) {
                    // Duplicate prevention: only add if not already processed
                    if (downstream.getWorkflowDefinitionCode() != workflowDefinitionCode
                            && processedWorkflowCodes.add(downstream.getWorkflowDefinitionCode())) {
                        nextLevelWorkflows.add(downstream);
                        allWorkflows.add(downstream);
                    }
                }
            }

            if (nextLevelWorkflows.isEmpty()) {
                break;
            }

            currentLevelWorkflows = new ArrayList<>(nextLevelWorkflows);
        }

        log.info("Found {} dependent workflows (all levels) for workflow definition code: {}",
                allWorkflows.size(), workflowDefinitionCode);
        return allWorkflows;
    }

    /**
     * Calculate dependent backfill dates based on dependency cycle configuration.
     * Only includes dates where the calculated dependent date intervals overlap with upstream backfill dates.
     * 
     * <p>Example: Downstream dependency cycle > upstream cycle
     * <pre>
     * Upstream: daily backfill on 2025-01-13(Mon) ~ 2025-01-20(Mon)
     * Downstream: depends on upstream with cycle=WEEK, dateValue="lastMonday"
     * 
     * Result: Only 2025-01-20 triggers downstream backfill, because its "lastMonday" (2025-01-13)
     * exists in upstream list. Other dates don't trigger because their "lastMonday" is outside the upstream range.
     * </pre>
     * 
     * @param upstreamBackfillDateList upstream workflow's backfill date list
     * @param dependentWorkflowDefinition dependent workflow definition containing dependency configuration
     * @param upstreamWorkflowCode upstream workflow code to match the specific dependency item
     * @return calculated backfill date list for dependent workflow
     */
    private List<ZonedDateTime> calculateDependentBackfillDates(
                                                                List<ZonedDateTime> upstreamBackfillDateList,
                                                                DependentWorkflowDefinition dependentWorkflowDefinition,
                                                                long upstreamWorkflowCode) {

        List<ZonedDateTime> dependentBackfillDateList = new ArrayList<>();

        String dateValue = getDependentDateValue(dependentWorkflowDefinition, upstreamWorkflowCode);

        if (dateValue == null || dateValue.isEmpty()) {
            log.debug("No dateValue found, returning empty list");
            return new ArrayList<>();
        }

        for (ZonedDateTime upstreamBackfillDate : upstreamBackfillDateList) {
            // Convert ZonedDateTime to Date for DependentUtils
            Date upstreamDate = Date.from(upstreamBackfillDate.toInstant());

            // Use DependentUtils.getDateIntervalList(Date, String) to calculate dependent date intervals
            List<DateInterval> dateIntervalList = DependentUtils.getDateIntervalList(upstreamDate, dateValue);

            if (dateIntervalList != null && !dateIntervalList.isEmpty()) {
                // Check if any date in upstream list falls within the calculated dependent date intervals
                boolean foundMatch = false;
                for (DateInterval interval : dateIntervalList) {
                    // Check each upstream date to see if it falls within this interval
                    for (ZonedDateTime checkDate : upstreamBackfillDateList) {
                        Date checkDateAsDate = Date.from(checkDate.toInstant());

                        // Check if checkDate is within [interval.startTime, interval.endTime]
                        if (!checkDateAsDate.before(interval.getStartTime())
                                && !checkDateAsDate.after(interval.getEndTime())) {
                            if (!dependentBackfillDateList.contains(upstreamBackfillDate)) {
                                // Downstream backfill date matches the dependency cycle with upstream
                                dependentBackfillDateList.add(upstreamBackfillDate);
                            }
                            foundMatch = true;
                            break;
                        }
                    }
                    if (foundMatch) {
                        break;
                    }
                }
            }
        }

        log.debug("Calculated {} dependent backfill dates from {} upstream dates",
                dependentBackfillDateList.size(), upstreamBackfillDateList.size());
        return dependentBackfillDateList;
    }

    /**
     * Get dateValue from dependent workflow definition for the specified upstream workflow
     */
    private String getDependentDateValue(DependentWorkflowDefinition dependentWorkflowDefinition,
                                         long upstreamWorkflowCode) {
        try {
            DependentParameters dependentParameters =
                    dependentWorkflowDefinition.getDependentParameters();

            List<DependentTaskModel> dependentTaskModelList =
                    dependentParameters.getDependence().getDependTaskList();

            for (DependentTaskModel dependentTaskModel : dependentTaskModelList) {
                List<DependentItem> dependentItemList =
                        dependentTaskModel.getDependItemList();

                for (DependentItem dependentItem : dependentItemList) {
                    if (upstreamWorkflowCode == dependentItem.getDefinitionCode()) {
                        return dependentItem.getDateValue();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse dependent parameters for workflow {}: {}",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage());
        }

        return null;
    }

}
