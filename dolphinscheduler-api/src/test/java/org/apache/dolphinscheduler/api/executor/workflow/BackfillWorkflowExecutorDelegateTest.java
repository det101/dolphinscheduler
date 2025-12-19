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

import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.service.WorkflowLineageService;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test for BackfillWorkflowExecutorDelegate
 */
@ExtendWith(MockitoExtension.class)
public class BackfillWorkflowExecutorDelegateTest {

    @InjectMocks
    private BackfillWorkflowExecutorDelegate backfillWorkflowExecutorDelegate;

    @Mock
    private WorkflowLineageService workflowLineageService;

    private Method calculateDependentBackfillDatesMethod;
    private Method getAllDependentWorkflowsMethod;

    @BeforeEach
    public void setUp() throws Exception {
        // Get private method using reflection
        calculateDependentBackfillDatesMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "calculateDependentBackfillDates",
                List.class,
                DependentWorkflowDefinition.class,
                long.class);
        calculateDependentBackfillDatesMethod.setAccessible(true);

        getAllDependentWorkflowsMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "getAllDependentWorkflows",
                long.class,
                boolean.class);
        getAllDependentWorkflowsMethod.setAccessible(true);
    }

    @Test
    public void testCalculateDependentBackfillDates_WeeklyCycle_LastMonday() throws Exception {
        // Arrange: upstream backfills last week Mon(13th) - Sun(19th) and next Monday(20th)
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-13T00:00:00Z")); // Monday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-14T00:00:00Z")); // Tuesday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-15T00:00:00Z")); // Wednesday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-16T00:00:00Z")); // Thursday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-17T00:00:00Z")); // Friday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-18T00:00:00Z")); // Saturday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-19T00:00:00Z")); // Sunday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-20T00:00:00Z")); // Monday

        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "week", "lastMonday");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: Only 2025-01-20 should be in result because its lastMonday (2025-01-13) exists in upstream list
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("2025-01-20", result.get(0).toLocalDate().toString());
    }

    @Test
    public void testCalculateDependentBackfillDates_MonthlyCycle_NoMatch() throws Exception {
        // Arrange: upstream backfills this month (Jan 13-19)
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-14 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-16 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-17 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-18 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-19 00:00:00"));

        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "month", "lastMonthBegin");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: Empty result because lastMonthBegin (2024-12-01) is not in upstream list
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testCalculateDependentBackfillDates_HourlyCycle_Last1Hour() throws Exception {
        // Arrange: upstream backfills 5 consecutive hours
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 10:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 11:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 12:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 13:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 14:00:00"));

        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "hour", "last1Hour");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: 11:00, 12:00, 13:00, 14:00 should be in result (10:00 excluded because its last1Hour 9:00 is not in
        // list)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 11));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 12));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 13));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 14));
    }

    @Test
    public void testCalculateDependentBackfillDates_DailyCycle_Last1Days() throws Exception {
        // Arrange: upstream backfills 5 consecutive days
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-10 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-11 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-12 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-14 00:00:00"));

        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "day", "last1Days");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: 11th, 12th, 13th, 14th should be in result (10th excluded because its last1Days 9th is not in list)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size());
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 11));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 12));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 13));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 14));
    }

    @Test
    public void testCalculateDependentBackfillDates_EmptyUpstreamList() throws Exception {
        // Arrange
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "day", "last1Days");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: Empty result when upstream list is empty
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testGetAllDependentWorkflows_OnlyLevel1() throws Exception {
        // Arrange: Root workflow A has Level 1 dependencies B and C, B has Level 2 dependency D
        long rootWorkflowCode = 100L;
        long level1WorkflowB = 200L;
        long level1WorkflowC = 300L;
        long level2WorkflowD = 400L;

        DependentWorkflowDefinition level1B = new DependentWorkflowDefinition();
        level1B.setWorkflowDefinitionCode(level1WorkflowB);
        DependentWorkflowDefinition level1C = new DependentWorkflowDefinition();
        level1C.setWorkflowDefinitionCode(level1WorkflowC);
        DependentWorkflowDefinition level2D = new DependentWorkflowDefinition();
        level2D.setWorkflowDefinitionCode(level2WorkflowD);

        List<DependentWorkflowDefinition> level1List = new ArrayList<>();
        level1List.add(level1B);
        level1List.add(level1C);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(rootWorkflowCode))
                .thenReturn(level1List);

        // Mock Level 2 dependency: B has downstream dependency D
        // This is crucial to verify that the code correctly stops at Level 1
        // If the code incorrectly recurses, it would find D and the test would fail
        List<DependentWorkflowDefinition> level2List = new ArrayList<>();
        level2List.add(level2D);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(level1WorkflowB))
                .thenReturn(level2List);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(level1WorkflowC))
                .thenReturn(new ArrayList<>());

        // Act: Get dependencies with allLevelDependent = false
        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, rootWorkflowCode, false);

        // Assert: Only Level 1 workflows (B and C) should be returned, Level 2 dependency D is excluded
        // Even though B has downstream dependency D, D should NOT be included because allLevelDependent = false
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size(), "Should only return Level 1 dependencies (B and C)");
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level1WorkflowB),
                "Level 1 workflow B should be included");
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level1WorkflowC),
                "Level 1 workflow C should be included");
        Assertions.assertFalse(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level2WorkflowD),
                "Level 2 workflow D should be excluded when allLevelDependent = false");
    }

    @Test
    public void testGetAllDependentWorkflows_AllLevels() throws Exception {
        // Arrange: Root workflow A has Level 1 dependencies B and C, B has Level 2 dependency D
        long rootWorkflowCode = 100L;
        long level1WorkflowB = 200L;
        long level1WorkflowC = 300L;
        long level2WorkflowD = 400L;

        DependentWorkflowDefinition level1B = new DependentWorkflowDefinition();
        level1B.setWorkflowDefinitionCode(level1WorkflowB);
        DependentWorkflowDefinition level1C = new DependentWorkflowDefinition();
        level1C.setWorkflowDefinitionCode(level1WorkflowC);
        DependentWorkflowDefinition level2D = new DependentWorkflowDefinition();
        level2D.setWorkflowDefinitionCode(level2WorkflowD);

        List<DependentWorkflowDefinition> level1List = new ArrayList<>();
        level1List.add(level1B);
        level1List.add(level1C);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(rootWorkflowCode))
                .thenReturn(level1List);

        List<DependentWorkflowDefinition> level2List = new ArrayList<>();
        level2List.add(level2D);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(level1WorkflowB))
                .thenReturn(level2List);

        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(level1WorkflowC))
                .thenReturn(new ArrayList<>());
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(level2WorkflowD))
                .thenReturn(new ArrayList<>());

        // Act: Get dependencies with allLevelDependent = true
        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, rootWorkflowCode, true);

        // Assert: All levels (B, C, D) should be returned in a flattened list
        Assertions.assertNotNull(result);
        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level1WorkflowB));
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level1WorkflowC));
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == level2WorkflowD));
    }

    @Test
    public void testGetAllDependentWorkflows_NoDependencies() throws Exception {
        // Arrange: Root workflow has no dependencies
        long rootWorkflowCode = 100L;
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(rootWorkflowCode))
                .thenReturn(new ArrayList<>());

        // Act
        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, rootWorkflowCode, true);

        // Assert: Empty result when no dependencies exist
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    /**
     * Helper method to create DependentWorkflowDefinition with taskParams
     */
    private DependentWorkflowDefinition createDependentWorkflowDefinition(
                                                                          long upstreamWorkflowCode,
                                                                          long downstreamWorkflowCode,
                                                                          String cycle,
                                                                          String dateValue) {
        DependentWorkflowDefinition definition = new DependentWorkflowDefinition();
        definition.setWorkflowDefinitionCode(downstreamWorkflowCode);
        definition.setTaskDefinitionCode(1000L);

        // Create DependentItem
        DependentItem dependentItem = new DependentItem();
        dependentItem.setDefinitionCode(upstreamWorkflowCode);
        dependentItem.setCycle(cycle);
        dependentItem.setDateValue(dateValue);

        // Create DependentTaskModel
        DependentTaskModel dependentTaskModel = new DependentTaskModel();
        List<DependentItem> dependentItemList = new ArrayList<>();
        dependentItemList.add(dependentItem);
        dependentTaskModel.setDependItemList(dependentItemList);

        // Create Dependence
        DependentParameters.Dependence dependence = new DependentParameters.Dependence();
        List<DependentTaskModel> dependTaskList = new ArrayList<>();
        dependTaskList.add(dependentTaskModel);
        dependence.setDependTaskList(dependTaskList);

        // Create DependentParameters
        DependentParameters dependentParameters = new DependentParameters();
        dependentParameters.setDependence(dependence);

        // Set taskParams as JSON string
        definition.setTaskParams(JSONUtils.toJsonString(dependentParameters));

        return definition;
    }
}
