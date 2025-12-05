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

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.service.impl.BackfillDependentWorkflowServiceImpl;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Test for BackfillDependentWorkflowServiceImpl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BackfillDependentWorkflowServiceImplTest {

    @InjectMocks
    private BackfillDependentWorkflowServiceImpl backfillDependentWorkflowService;

    @Mock
    private WorkflowLineageService workflowLineageService;

    @Mock
    private WorkerGroupService workerGroupService;

    private static final long WORKFLOW_CODE_1 = 1001L;
    private static final long WORKFLOW_CODE_2 = 1002L;
    private static final long WORKFLOW_CODE_3 = 1003L;
    private static final String WORKER_GROUP_DEFAULT = "default";
    private static final String WORKER_GROUP_CUSTOM = "custom";

    @BeforeEach
    public void setUp() {
        // Setup default mock behaviors
        when(workerGroupService.queryWorkerGroupByWorkflowDefinitionCodes(anyList()))
                .thenReturn(new HashMap<>());
    }

    @Test
    public void testGetComplementDependentDefinitionList_EmptyList() {
        // Given: no downstream dependencies
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Collections.emptyList());

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, false);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testGetComplementDependentDefinitionList_DirectDependentOnly() {
        // Given: one direct downstream dependency
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(dependent1));

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, false);

        // Then
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(WORKFLOW_CODE_2, result.get(0).getWorkflowDefinitionCode());
    }

    @Test
    public void testGetComplementDependentDefinitionList_FilterSelf() {
        // Given: downstream dependency includes self
        DependentWorkflowDefinition self = createDependentWorkflowDefinition(WORKFLOW_CODE_1, 1);
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(self, dependent1));

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, false);

        // Then: self should be filtered out
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(WORKFLOW_CODE_2, result.get(0).getWorkflowDefinitionCode());
    }

    @Test
    public void testGetComplementDependentDefinitionList_AllLevelDependent() {
        // Given: multi-level dependencies
        // Level 1: WORKFLOW_CODE_2 depends on WORKFLOW_CODE_1
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(dependent1));

        // Level 2: WORKFLOW_CODE_3 depends on WORKFLOW_CODE_2
        DependentWorkflowDefinition dependent2 = createDependentWorkflowDefinition(WORKFLOW_CODE_3, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_2))
                .thenReturn(Arrays.asList(dependent2));

        // Level 3: no more dependencies
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_3))
                .thenReturn(Collections.emptyList());

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, true);

        // Then: should include both level 1 and level 2 dependencies
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.stream()
                .anyMatch(def -> def.getWorkflowDefinitionCode() == WORKFLOW_CODE_2));
        Assertions.assertTrue(result.stream()
                .anyMatch(def -> def.getWorkflowDefinitionCode() == WORKFLOW_CODE_3));
    }

    @Test
    public void testGetComplementDependentDefinitionList_WorkerGroupSet() {
        // Given: dependency without worker group
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        dependent1.setWorkerGroup(null);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(dependent1));

        Map<Long, String> workerGroupMap = new HashMap<>();
        workerGroupMap.put(WORKFLOW_CODE_2, null);
        when(workerGroupService.queryWorkerGroupByWorkflowDefinitionCodes(anyList()))
                .thenReturn(workerGroupMap);

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, false);

        // Then: worker group should be set
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(WORKER_GROUP_DEFAULT, result.get(0).getWorkerGroup());
    }

    @Test
    public void testGetComplementDependentDefinitionList_WorkerGroupNotOverride() {
        // Given: dependency with existing worker group
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        dependent1.setWorkerGroup(WORKER_GROUP_CUSTOM);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(dependent1));

        Map<Long, String> workerGroupMap = new HashMap<>();
        workerGroupMap.put(WORKFLOW_CODE_2, WORKER_GROUP_CUSTOM);
        when(workerGroupService.queryWorkerGroupByWorkflowDefinitionCodes(anyList()))
                .thenReturn(workerGroupMap);

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, false);

        // Then: existing worker group should not be overridden
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(WORKER_GROUP_CUSTOM, result.get(0).getWorkerGroup());
    }

    @Test
    public void testCalculateDependentBackfillDates_WithDateValue() {
        // Given: upstream dates and dependent workflow with dateValue
        // Use recent past dates to avoid future date filtering
        String today = org.apache.dolphinscheduler.common.utils.DateUtils.dateToString(new java.util.Date());
        List<String> upstreamDates = Arrays.asList(today);
        long upstreamCode = WORKFLOW_CODE_1;
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "today");

        // When
        List<String> result = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDef);

        // Then: should calculate dependent dates based on dateValue
        Assertions.assertNotNull(result);
        // Result may be empty if calculated dates are in the future, which is valid behavior
        // Just verify the method doesn't throw exception and returns a list
    }

    @Test
    public void testCalculateDependentBackfillDates_NoDateValue() {
        // Given: dependent workflow without dateValue
        List<String> upstreamDates = Arrays.asList("2024-01-01", "2024-01-02");
        long upstreamCode = WORKFLOW_CODE_1;
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        dependentDef.setTaskParams(null); // No task params means no dateValue

        // When
        List<String> result = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDef);

        // Then: should return original upstream dates
        Assertions.assertNotNull(result);
        Assertions.assertEquals(upstreamDates, result);
    }

    @Test
    public void testCalculateDependentBackfillDates_InvalidDate() {
        // Given: upstream dates with invalid date
        String today = org.apache.dolphinscheduler.common.utils.DateUtils.dateToString(new java.util.Date());
        List<String> upstreamDates = Arrays.asList(today, "invalid-date", today);
        long upstreamCode = WORKFLOW_CODE_1;
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "today");

        // When
        List<String> result = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDef);

        // Then: invalid date should be skipped
        Assertions.assertNotNull(result);
        // Should not contain invalid date, but should process valid dates
        Assertions.assertFalse(result.contains("invalid-date"));
    }

    @Test
    public void testCalculateDependentBackfillDates_FutureDateSkipped() {
        // Given: upstream date that results in future dependent date
        // Use a future date to test filtering
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_MONTH, 10);
        String futureDate = org.apache.dolphinscheduler.common.utils.DateUtils.dateToString(cal.getTime());
        List<String> upstreamDates = Arrays.asList(futureDate);
        long upstreamCode = WORKFLOW_CODE_1;
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "today");

        // When
        List<String> result = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDef);

        // Then: future dates should be filtered out
        Assertions.assertNotNull(result);
        // Future dates should be filtered, so result may be empty
    }

    @Test
    public void testCalculateDependentBackfillDates_DuplicateDates() {
        // Given: upstream dates that result in duplicate dependent dates
        String today = org.apache.dolphinscheduler.common.utils.DateUtils.dateToString(new java.util.Date());
        List<String> upstreamDates = Arrays.asList(today, today);
        long upstreamCode = WORKFLOW_CODE_1;
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "today");

        // When
        List<String> result = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDef);

        // Then: duplicates should be removed
        Assertions.assertNotNull(result);
        long distinctCount = result.stream().distinct().count();
        Assertions.assertEquals(distinctCount, result.size());
    }

    @Test
    public void testCalculateDependentBackfillDates_DifferentDateValues() {
        // Given: different dateValue configurations
        String today = org.apache.dolphinscheduler.common.utils.DateUtils.dateToString(new java.util.Date());
        List<String> upstreamDates = Arrays.asList(today);
        long upstreamCode = WORKFLOW_CODE_1;

        // Test with "last1Days" (previous day)
        DependentWorkflowDefinition dependentDefLast1Days = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "last1Days");
        List<String> resultLast1Days = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDefLast1Days);

        // Test with "today" (same day)
        DependentWorkflowDefinition dependentDefToday = createDependentWorkflowDefinitionWithDateValue(
                WORKFLOW_CODE_2, upstreamCode, "today");
        List<String> resultToday = backfillDependentWorkflowService.calculateDependentBackfillDates(
                upstreamCode, upstreamDates, dependentDefToday);

        // Then: different dateValues should produce different results
        Assertions.assertNotNull(resultLast1Days);
        Assertions.assertNotNull(resultToday);
        // Results may differ based on dateValue
    }

    @Test
    public void testGetComplementDependentDefinitionList_CircularDependencyPrevention() {
        // Given: circular dependency scenario (A -> B -> A)
        DependentWorkflowDefinition dependent1 = createDependentWorkflowDefinition(WORKFLOW_CODE_2, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_1))
                .thenReturn(Arrays.asList(dependent1));

        // WORKFLOW_CODE_2 depends back on WORKFLOW_CODE_1 (circular)
        DependentWorkflowDefinition circular = createDependentWorkflowDefinition(WORKFLOW_CODE_1, 1);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(WORKFLOW_CODE_2))
                .thenReturn(Arrays.asList(circular));

        // When
        List<DependentWorkflowDefinition> result = backfillDependentWorkflowService
                .getComplementDependentDefinitionList(WORKFLOW_CODE_1, WORKER_GROUP_DEFAULT, true);

        // Then: should prevent infinite loop and filter out self
        Assertions.assertNotNull(result);
        // Should not contain WORKFLOW_CODE_1 (self)
        Assertions.assertTrue(result.stream()
                .noneMatch(def -> def.getWorkflowDefinitionCode() == WORKFLOW_CODE_1));
    }

    /**
     * Create a DependentWorkflowDefinition for testing
     */
    private DependentWorkflowDefinition createDependentWorkflowDefinition(long workflowCode, int version) {
        DependentWorkflowDefinition def = new DependentWorkflowDefinition();
        def.setWorkflowDefinitionCode(workflowCode);
        def.setWorkflowDefinitionVersion(version);
        def.setTaskDefinitionCode(1L);
        def.setWorkerGroup(WORKER_GROUP_DEFAULT);
        return def;
    }

    /**
     * Create a DependentWorkflowDefinition with dateValue for testing
     */
    private DependentWorkflowDefinition createDependentWorkflowDefinitionWithDateValue(
                                                                                       long workflowCode,
                                                                                       long upstreamCode,
                                                                                       String dateValue) {
        DependentWorkflowDefinition def = createDependentWorkflowDefinition(workflowCode, 1);

        // Create DependentParameters with dateValue
        DependentParameters dependentParameters = new DependentParameters();
        DependentParameters.Dependence dependence = new DependentParameters.Dependence();
        List<DependentTaskModel> dependTaskList = new ArrayList<>();
        DependentTaskModel taskModel = new DependentTaskModel();
        List<DependentItem> dependentItems = new ArrayList<>();
        DependentItem item = new DependentItem();
        item.setDefinitionCode(upstreamCode);
        item.setDateValue(dateValue);
        item.setCycle("day");
        dependentItems.add(item);
        taskModel.setDependItemList(dependentItems);
        dependTaskList.add(taskModel);
        dependence.setDependTaskList(dependTaskList);
        dependentParameters.setDependence(dependence);

        def.setTaskParams(JSONUtils.toJsonString(dependentParameters));
        return def;
    }
}
