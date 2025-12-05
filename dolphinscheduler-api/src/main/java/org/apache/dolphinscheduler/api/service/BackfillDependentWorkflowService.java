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

package org.apache.dolphinscheduler.api.service;

import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowDTO;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;

import java.util.List;
import java.util.Map;

/**
 * Service for handling backfill dependent workflow business logic
 */
public interface BackfillDependentWorkflowService {

    /**
     * Get complement dependent online workflow definition list with duplicate prevention
     * No cycle matching required, only calculate backfill dates based on dependency cycle and dateValue
     *
     * @param workflowDefinitionCode the workflow definition code
     * @param workerGroup the worker group
     * @param allLevelDependent whether to trigger all levels of dependencies
     * @return list of dependent workflow definitions
     */
    List<DependentWorkflowDefinition> getComplementDependentDefinitionList(
                                                                           long workflowDefinitionCode,
                                                                           String workerGroup,
                                                                           boolean allLevelDependent);

    /**
     * Calculate backfill dates for dependent workflow based on dependency cycle and dateValue
     *
     * @param upstreamWorkflowCode the upstream workflow definition code
     * @param upstreamBackfillDates the upstream workflow backfill dates
     * @param dependentWorkflowDefinition the dependent workflow definition
     * @return list of backfill dates for dependent workflow
     */
    List<String> calculateDependentBackfillDates(
                                                 long upstreamWorkflowCode,
                                                 List<String> upstreamBackfillDates,
                                                 DependentWorkflowDefinition dependentWorkflowDefinition);

    /**
     * Build backfill request for dependent workflow
     * This method encapsulates the business logic of constructing a backfill request
     * from the original backfill DTO and dependent workflow definition
     *
     * @param originalBackfillDTO the original backfill workflow DTO
     * @param dependentWorkflowDefinition the dependent workflow definition
     * @param backfillTimeList the calculated backfill time list for dependent workflow
     * @return the backfill trigger request
     */
    WorkflowBackfillTriggerRequest buildDependentBackfillRequest(
                                                                 BackfillWorkflowDTO originalBackfillDTO,
                                                                 DependentWorkflowDefinition dependentWorkflowDefinition,
                                                                 List<String> backfillTimeList);

    /**
     * Batch query dependent workflow definitions and validate them
     * This method encapsulates data access and validation logic
     *
     * @param dependentWorkflowDefinitionList list of dependent workflow definitions
     * @return map of workflow code to workflow definition (only includes valid online workflows)
     */
    Map<Long, WorkflowDefinition> batchQueryAndValidateDependentWorkflows(
                                                                          List<DependentWorkflowDefinition> dependentWorkflowDefinitionList);

    /**
     * Prepare dependent workflow backfill data
     * This method encapsulates the complete business logic: calculate dates and validate workflow
     *
     * @param upstreamWorkflowCode the upstream workflow code
     * @param upstreamBackfillDates the upstream backfill dates
     * @param dependentWorkflowDefinition the dependent workflow definition
     * @param dependentWorkflow the dependent workflow definition (validated)
     * @return calculated backfill dates, or empty list if validation fails or dates are invalid
     */
    List<String> prepareDependentWorkflowBackfillDates(
                                                       long upstreamWorkflowCode,
                                                       List<String> upstreamBackfillDates,
                                                       DependentWorkflowDefinition dependentWorkflowDefinition,
                                                       WorkflowDefinition dependentWorkflow);
}
