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
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test for BackfillWorkflowExecutorDelegate
 */
@ExtendWith(MockitoExtension.class)
public class BackfillWorkflowExecutorDelegateTest {

    @InjectMocks
    private BackfillWorkflowExecutorDelegate backfillWorkflowExecutorDelegate;

    private Method calculateDependentBackfillDatesMethod;

    @BeforeEach
    public void setUp() throws Exception {
        // Get private method using reflection
        calculateDependentBackfillDatesMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "calculateDependentBackfillDates",
                List.class,
                DependentWorkflowDefinition.class,
                long.class);
        calculateDependentBackfillDatesMethod.setAccessible(true);
    }

    /**
     * Test case: Downstream depends on weekly cycle (lastMonday)
     * Upstream backfills last week Mon-Sun
     * Expected: Only Sunday should trigger downstream (as it depends on lastMonday which is in the list)
     */
    @Test
    public void testCalculateDependentBackfillDates_WeeklyCycle_LastMonday() throws Exception {
        // Arrange: upstream backfills last week Mon(13th) - Sun(19th)
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-13T00:00:00Z")); // Monday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-14T00:00:00Z")); // Tuesday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-15T00:00:00Z")); // Wednesday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-16T00:00:00Z")); // Thursday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-17T00:00:00Z")); // Friday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-18T00:00:00Z")); // Saturday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-19T00:00:00Z")); // Sunday
        upstreamBackfillDateList.add(ZonedDateTime.parse("2025-01-20T00:00:00Z")); // Monday

        // Create dependent workflow definition with "lastMonday" dateValue
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "week", "lastMonday");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: Only Sunday (19th) should be in result
        // Because Sunday's lastMonday is Monday 13th, which exists in upstream list
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.size(),
                "Only Sunday should trigger downstream as its lastMonday (13th) is in the upstream list");
        Assertions.assertEquals("2025-01-20", result.get(0).toLocalDate().toString());
    }

    /**
     * Test case: Downstream depends on monthly cycle (lastMonthBegin)
     * Upstream backfills last week (all dates are in current month)
     * Expected: No dates should trigger downstream (lastMonthBegin is not in the list)
     */
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

        // Create dependent workflow definition with "lastMonthBegin" dateValue
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "month", "lastMonthBegin");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: No dates should be in result
        // Because lastMonthBegin (Dec 1st) is not in the upstream list
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size(),
                "No dates should trigger downstream as lastMonthBegin is not in upstream list");
    }

    /**
     * Test case: Downstream depends on hourly cycle (last1Hour)
     * Upstream backfills multiple hours
     * Expected: Hours that have previous hour in list should trigger downstream
     */
    @Test
    public void testCalculateDependentBackfillDates_HourlyCycle_Last1Hour() throws Exception {
        // Arrange: upstream backfills 5 consecutive hours
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 10:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 11:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 12:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 13:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 14:00:00"));

        // Create dependent workflow definition with "last1Hour" dateValue
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "hour", "last1Hour");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: 11:00, 12:00, 13:00, 14:00 should be in result (not 10:00 as its last1Hour is 9:00 which is not in
        // list)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size(),
                "4 hours should trigger downstream (all except 10:00)");
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 11));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 12));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 13));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getHour() == 14));
    }

    /**
     * Test case: Downstream depends on daily cycle (last1Days)
     * Upstream backfills multiple consecutive days
     * Expected: Days that have previous day in list should trigger downstream
     */
    @Test
    public void testCalculateDependentBackfillDates_DailyCycle_Last1Days() throws Exception {
        // Arrange: upstream backfills 5 consecutive days
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-10 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-11 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-12 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-14 00:00:00"));

        // Create dependent workflow definition with "last1Days" dateValue
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "day", "last1Days");

        // Act
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Assert: 11th, 12th, 13th, 14th should be in result (not 10th as its last1Days is 9th which is not in list)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(4, result.size(),
                "4 days should trigger downstream (all except Jan 10th)");
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 11));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 12));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 13));
        Assertions.assertTrue(result.stream().anyMatch(dt -> dt.getDayOfMonth() == 14));
    }

    /**
     * Test case: Empty upstream backfill list
     * Expected: Empty result
     */
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

        // Assert
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size(), "Empty upstream list should return empty result");
    }

    /**
     * Test case: Dependent node depends on two upstream workflows simultaneously
     * Upstream1 (100L): daily cycle, last1Days
     * Upstream2 (101L): hourly cycle, last1Hour
     * Expected: Each upstream is processed independently with its own dependency configuration
     */
    @Test
    public void testCalculateDependentBackfillDates_TwoUpstreams() throws Exception {
        // Arrange: Create a dependent workflow that depends on two upstreams
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinitionWithTwoUpstreams(
                100L, 101L, 200L,
                "day", "last1Days", // config for upstream1 (100L)
                "hour", "last1Hour"); // config for upstream2 (101L)

        // Upstream1 backfill dates: 5 consecutive days
        List<ZonedDateTime> upstream1BackfillDateList = new ArrayList<>();
        upstream1BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-10 00:00:00"));
        upstream1BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-11 00:00:00"));
        upstream1BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-12 00:00:00"));
        upstream1BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));
        upstream1BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-14 00:00:00"));

        // Upstream2 backfill dates: 5 consecutive hours
        List<ZonedDateTime> upstream2BackfillDateList = new ArrayList<>();
        upstream2BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 10:00:00"));
        upstream2BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 11:00:00"));
        upstream2BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 12:00:00"));
        upstream2BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 13:00:00"));
        upstream2BackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-15 14:00:00"));

        // Act: Calculate backfill dates for upstream1 (100L)
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result1 = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstream1BackfillDateList,
                dependentWorkflowDefinition,
                100L); // Query for upstream1

        // Act: Calculate backfill dates for upstream2 (101L)
        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result2 = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstream2BackfillDateList,
                dependentWorkflowDefinition,
                101L); // Query for upstream2

        // Assert: Upstream1 should return 4 dates (11-14, as their last1Days is in the list)
        Assertions.assertNotNull(result1);
        Assertions.assertEquals(4, result1.size(),
                "Upstream1 with daily cycle should return 4 dates");
        Assertions.assertTrue(result1.stream().anyMatch(dt -> dt.getDayOfMonth() == 11));
        Assertions.assertTrue(result1.stream().anyMatch(dt -> dt.getDayOfMonth() == 12));
        Assertions.assertTrue(result1.stream().anyMatch(dt -> dt.getDayOfMonth() == 13));
        Assertions.assertTrue(result1.stream().anyMatch(dt -> dt.getDayOfMonth() == 14));

        // Assert: Upstream2 should return 4 dates (11:00-14:00, as their last1Hour is in the list)
        Assertions.assertNotNull(result2);
        Assertions.assertEquals(4, result2.size(),
                "Upstream2 with hourly cycle should return 4 dates");
        Assertions.assertTrue(result2.stream().anyMatch(dt -> dt.getHour() == 11));
        Assertions.assertTrue(result2.stream().anyMatch(dt -> dt.getHour() == 12));
        Assertions.assertTrue(result2.stream().anyMatch(dt -> dt.getHour() == 13));
        Assertions.assertTrue(result2.stream().anyMatch(dt -> dt.getHour() == 14));
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

    /**
     * Helper method to create DependentWorkflowDefinition with two upstream dependencies
     * This simulates a downstream workflow that depends on two different upstream workflows
     */
    private DependentWorkflowDefinition createDependentWorkflowDefinitionWithTwoUpstreams(
                                                                                          long upstream1WorkflowCode,
                                                                                          long upstream2WorkflowCode,
                                                                                          long downstreamWorkflowCode,
                                                                                          String cycle1,
                                                                                          String dateValue1,
                                                                                          String cycle2,
                                                                                          String dateValue2) {
        DependentWorkflowDefinition definition = new DependentWorkflowDefinition();
        definition.setWorkflowDefinitionCode(downstreamWorkflowCode);
        definition.setTaskDefinitionCode(1000L);

        // Create DependentItem for upstream1
        DependentItem dependentItem1 = new DependentItem();
        dependentItem1.setDefinitionCode(upstream1WorkflowCode);
        dependentItem1.setCycle(cycle1);
        dependentItem1.setDateValue(dateValue1);

        // Create DependentItem for upstream2
        DependentItem dependentItem2 = new DependentItem();
        dependentItem2.setDefinitionCode(upstream2WorkflowCode);
        dependentItem2.setCycle(cycle2);
        dependentItem2.setDateValue(dateValue2);

        // Create DependentTaskModel with both items
        DependentTaskModel dependentTaskModel = new DependentTaskModel();
        List<DependentItem> dependentItemList = new ArrayList<>();
        dependentItemList.add(dependentItem1);
        dependentItemList.add(dependentItem2);
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
