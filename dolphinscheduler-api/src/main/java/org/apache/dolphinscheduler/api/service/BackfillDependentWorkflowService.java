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

import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;

import java.util.List;

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
}
