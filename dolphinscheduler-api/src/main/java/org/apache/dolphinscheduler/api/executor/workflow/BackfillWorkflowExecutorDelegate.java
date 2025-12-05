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
import org.apache.dolphinscheduler.api.service.BackfillDependentWorkflowService;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowDTO;
import org.apache.dolphinscheduler.common.enums.ComplementDependentMode;
import org.apache.dolphinscheduler.common.enums.ExecutionOrder;
import org.apache.dolphinscheduler.common.enums.RunMode;
import org.apache.dolphinscheduler.common.model.Server;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.extract.base.client.Clients;
import org.apache.dolphinscheduler.extract.master.IWorkflowControlClient;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import org.apache.dolphinscheduler.registry.api.RegistryClient;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private BackfillDependentWorkflowService backfillDependentWorkflowService;

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
     * with calculated backfill dates based on dependency configuration
     *
     * @param backfillWorkflowDTO the backfill workflow DTO
     * @param backfillTimeList the backfill time list
     */
    private void doBackfillDependentWorkflow(final BackfillWorkflowDTO backfillWorkflowDTO,
                                             final List<String> backfillTimeList) {
        final WorkflowDefinition workflowDefinition = backfillWorkflowDTO.getWorkflowDefinition();
        final BackfillWorkflowDTO.BackfillParamsDTO backfillParams = backfillWorkflowDTO.getBackfillParams();

        // Get dependent workflow definitions list
        List<DependentWorkflowDefinition> dependentWorkflowDefinitionList =
                backfillDependentWorkflowService.getComplementDependentDefinitionList(
                        workflowDefinition.getCode(),
                        backfillWorkflowDTO.getWorkerGroup(),
                        backfillParams.isAllLevelDependent());

        if (dependentWorkflowDefinitionList.isEmpty()) {
            log.info("No dependent workflows found for workflow definition code: {}.",
                    workflowDefinition.getCode());
            return;
        }

        log.info("Found {} dependent workflows for workflow definition code: {}, will trigger backfill for each.",
                dependentWorkflowDefinitionList.size(), workflowDefinition.getCode());

        // Batch query and validate dependent workflows (data access and validation logic in Service layer)
        Map<Long, WorkflowDefinition> dependentWorkflowMap =
                backfillDependentWorkflowService.batchQueryAndValidateDependentWorkflows(
                        dependentWorkflowDefinitionList);

        // Pre-fetch master server once to avoid repeated lookups
        final Server masterServer = registryClient.getRandomServer(RegistryNodeType.MASTER).orElse(null);
        if (masterServer == null) {
            log.error("No master server available, cannot trigger backfill for dependent workflows");
            return;
        }

        // Process each dependent workflow
        for (DependentWorkflowDefinition dependentWorkflowDefinition : dependentWorkflowDefinitionList) {
            try {
                // Prepare backfill dates (validation and calculation logic in Service layer)
                List<String> dependentBackfillDates =
                        backfillDependentWorkflowService.prepareDependentWorkflowBackfillDates(
                                workflowDefinition.getCode(),
                                backfillTimeList,
                                dependentWorkflowDefinition,
                                dependentWorkflowMap.get(dependentWorkflowDefinition.getWorkflowDefinitionCode()));

                if (dependentBackfillDates.isEmpty()) {
                    continue;
                }

                // Trigger backfill (only API call logic remains in Executor layer)
                triggerDependentWorkflowBackfill(backfillWorkflowDTO, dependentWorkflowDefinition,
                        dependentBackfillDates, masterServer);
            } catch (Exception e) {
                log.error("Failed to trigger backfill for dependent workflow definition code: {}, error: {}",
                        dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage(), e);
                // Continue with other dependent workflows even if one fails
            }
        }
    }

    /**
     * Trigger backfill for a single dependent workflow
     */
    private void triggerDependentWorkflowBackfill(final BackfillWorkflowDTO originalBackfillDTO,
                                                  final DependentWorkflowDefinition dependentWorkflowDefinition,
                                                  final List<String> backfillTimeList,
                                                  final Server masterServer) {
        // Build backfill request using service layer
        final WorkflowBackfillTriggerRequest dependentBackfillRequest =
                backfillDependentWorkflowService.buildDependentBackfillRequest(
                        originalBackfillDTO, dependentWorkflowDefinition, backfillTimeList);

        // Call master API to trigger backfill
        final WorkflowBackfillTriggerResponse dependentBackfillResponse = Clients
                .withService(IWorkflowControlClient.class)
                .withHost(masterServer.getHost() + ":" + masterServer.getPort())
                .backfillTriggerWorkflow(dependentBackfillRequest);

        // Log result
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
