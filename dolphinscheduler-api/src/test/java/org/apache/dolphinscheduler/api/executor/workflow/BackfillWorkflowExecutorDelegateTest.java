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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.api.service.WorkflowLineageService;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowDTO;
import org.apache.dolphinscheduler.api.validator.workflow.BackfillWorkflowRequestTransformer;
import org.apache.dolphinscheduler.common.enums.CommandType;
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
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.User;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.repository.WorkflowDefinitionDao;
import org.apache.dolphinscheduler.extract.base.client.Clients;
import org.apache.dolphinscheduler.extract.master.IWorkflowControlClient;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerRequest;
import org.apache.dolphinscheduler.extract.master.transportor.workflow.WorkflowBackfillTriggerResponse;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel;
import org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters;
import org.apache.dolphinscheduler.registry.api.RegistryClient;
import org.apache.dolphinscheduler.registry.api.enums.RegistryNodeType;
import org.apache.dolphinscheduler.service.process.ProcessService;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Test for BackfillWorkflowExecutorDelegate
 */
@ExtendWith(MockitoExtension.class)
public class BackfillWorkflowExecutorDelegateTest {

    @InjectMocks
    private BackfillWorkflowExecutorDelegate backfillWorkflowExecutorDelegate;

    @Mock
    private WorkflowLineageService workflowLineageService;

    @Mock
    private RegistryClient registryClient;

    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Mock
    private ProcessService processService;

    @Mock
    private BackfillWorkflowRequestTransformer backfillWorkflowRequestTransformer;

    private Method calculateDependentBackfillDatesMethod;
    private Method getAllDependentWorkflowsMethod;
    private Method doBackfillWorkflowMethod;
    private Method doSerialBackfillWorkflowMethod;
    private Method doParallelBackfillWorkflowMethod;
    private Method doBackfillDependentWorkflowMethod;
    private Method buildDependentBackfillDTOMethod;
    private Method getDependentDateValueMethod;

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

        doBackfillWorkflowMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "doBackfillWorkflow",
                BackfillWorkflowDTO.class,
                List.class);
        doBackfillWorkflowMethod.setAccessible(true);

        doSerialBackfillWorkflowMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "doSerialBackfillWorkflow",
                BackfillWorkflowDTO.class);
        doSerialBackfillWorkflowMethod.setAccessible(true);

        doParallelBackfillWorkflowMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "doParallelBackfillWorkflow",
                BackfillWorkflowDTO.class);
        doParallelBackfillWorkflowMethod.setAccessible(true);

        doBackfillDependentWorkflowMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "doBackfillDependentWorkflow",
                BackfillWorkflowDTO.class);
        doBackfillDependentWorkflowMethod.setAccessible(true);

        buildDependentBackfillDTOMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "buildDependentBackfillDTO",
                BackfillWorkflowDTO.class,
                DependentWorkflowDefinition.class);
        buildDependentBackfillDTOMethod.setAccessible(true);

        getDependentDateValueMethod = BackfillWorkflowExecutorDelegate.class.getDeclaredMethod(
                "getDependentDateValue",
                DependentWorkflowDefinition.class,
                long.class);
        getDependentDateValueMethod.setAccessible(true);
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

        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Empty result because lastMonthBegin (2024-12-01) is not in upstream list
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
        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "day", "last1Days");

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
    @MockitoSettings(strictness = Strictness.LENIENT)
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

        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, rootWorkflowCode, true);

        // Assert: Empty result when no dependencies exist
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testGetDependentDateValue_Success() throws Exception {
        DependentWorkflowDefinition dependentWorkflowDefinition = createDependentWorkflowDefinition(
                100L, 200L, "day", "last1Days");

        String result = (String) getDependentDateValueMethod.invoke(
                backfillWorkflowExecutorDelegate,
                dependentWorkflowDefinition,
                100L);

        Assertions.assertNotNull(result);
        Assertions.assertEquals("last1Days", result);
    }

    @Test
    public void testCalculateDependentBackfillDates_NullDateValue() throws Exception {
        DependentWorkflowDefinition dependentWorkflowDefinition = new DependentWorkflowDefinition();
        dependentWorkflowDefinition.setWorkflowDefinitionCode(200L);
        dependentWorkflowDefinition.setTaskParams("{}");

        List<ZonedDateTime> upstreamBackfillDateList = new ArrayList<>();
        upstreamBackfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));

        @SuppressWarnings("unchecked")
        List<ZonedDateTime> result = (List<ZonedDateTime>) calculateDependentBackfillDatesMethod.invoke(
                backfillWorkflowExecutorDelegate,
                upstreamBackfillDateList,
                dependentWorkflowDefinition,
                100L);

        // Should return empty list when dateValue is null
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testGetAllDependentWorkflows_SelfDependency() throws Exception {
        long rootWorkflowCode = 100L;
        DependentWorkflowDefinition selfDependency = new DependentWorkflowDefinition();
        selfDependency.setWorkflowDefinitionCode(rootWorkflowCode);

        List<DependentWorkflowDefinition> level1List = new ArrayList<>();
        level1List.add(selfDependency);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(rootWorkflowCode))
                .thenReturn(level1List);

        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, rootWorkflowCode, true);

        // Self-dependency should be filtered out
        Assertions.assertNotNull(result);
        Assertions.assertEquals(0, result.size());
    }

    @Test
    public void testGetAllDependentWorkflows_CircularDependency() throws Exception {
        long workflowA = 100L;
        long workflowB = 200L;
        long workflowC = 300L;

        DependentWorkflowDefinition defB = new DependentWorkflowDefinition();
        defB.setWorkflowDefinitionCode(workflowB);
        DependentWorkflowDefinition defC = new DependentWorkflowDefinition();
        defC.setWorkflowDefinitionCode(workflowC);
        DependentWorkflowDefinition defA = new DependentWorkflowDefinition();
        defA.setWorkflowDefinitionCode(workflowA);

        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowA))
                .thenReturn(Collections.singletonList(defB));
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowB))
                .thenReturn(Collections.singletonList(defC));
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowC))
                .thenReturn(Collections.singletonList(defA));

        @SuppressWarnings("unchecked")
        List<DependentWorkflowDefinition> result = (List<DependentWorkflowDefinition>) getAllDependentWorkflowsMethod
                .invoke(backfillWorkflowExecutorDelegate, workflowA, true);

        // Circular dependency should be handled (duplicates filtered)
        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size()); // B and C, A is filtered as self-dependency
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == workflowB));
        Assertions.assertTrue(result.stream().anyMatch(w -> w.getWorkflowDefinitionCode() == workflowC));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoSerialBackfillWorkflow_AscendingOrder() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 3);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(1001);
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            @SuppressWarnings("unchecked")
            List<Integer> result = (List<Integer>) doSerialBackfillWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(1001, result.get(0));
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoSerialBackfillWorkflow_DescendingOrder() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.DESC_ORDER, 3);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(1001);
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            @SuppressWarnings("unchecked")
            List<Integer> result = (List<Integer>) doSerialBackfillWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            // Verify dates are sorted in descending order (checked via reflection of internal state)
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoParallelBackfillWorkflow() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_PARALLEL, ExecutionOrder.ASC_ORDER, 5);
        backfillWorkflowDTO.getBackfillParams().setExpectedParallelismNumber(2);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response1 = WorkflowBackfillTriggerResponse.success(1001);
        WorkflowBackfillTriggerResponse response2 = WorkflowBackfillTriggerResponse.success(1002);
        WorkflowBackfillTriggerResponse response3 = WorkflowBackfillTriggerResponse.success(1003);

        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response1, response2, response3);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            @SuppressWarnings("unchecked")
            List<Integer> result = (List<Integer>) doParallelBackfillWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO);

            // 5 dates with parallelism 2 = 3 partitions (2+2+1)
            Assertions.assertNotNull(result);
            Assertions.assertEquals(3, result.size());
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoBackfillWorkflow_Success() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        List<ZonedDateTime> backfillDateList = Collections.singletonList(
                DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(1001);
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            Integer result = (Integer) doBackfillWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO, backfillDateList);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1001, result);
        }
    }

    @Test
    public void testDoBackfillWorkflow_NoMasterServer() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        List<ZonedDateTime> backfillDateList = Collections.singletonList(
                DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));

        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.empty());

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            doBackfillWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO, backfillDateList);
        });
        Assertions.assertTrue(exception.getCause() instanceof ServiceException);
        Assertions.assertTrue(exception.getCause().getMessage().contains("no master server available"));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoBackfillWorkflow_FailureResponse() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        List<ZonedDateTime> backfillDateList = Collections.singletonList(
                DateUtils.stringToZoneDateTime("2025-01-13 00:00:00"));

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.fail("Backfill failed");
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            Exception exception = Assertions.assertThrows(Exception.class, () -> {
                doBackfillWorkflowMethod.invoke(
                        backfillWorkflowExecutorDelegate, backfillWorkflowDTO, backfillDateList);
            });
            Assertions.assertTrue(exception.getCause() instanceof ServiceException);
            Assertions.assertTrue(exception.getCause().getMessage().contains("Backfill workflow failed"));
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testExecute_SerialMode_WithoutDependent() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        backfillWorkflowDTO.getBackfillParams().setBackfillDependentMode(ComplementDependentMode.OFF_MODE);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(1001);
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            List<Integer> result = backfillWorkflowExecutorDelegate.execute(backfillWorkflowDTO);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            Assertions.assertEquals(1001, result.get(0));
            // Verify dependent workflow is not triggered
            verify(workflowLineageService, never()).queryDownstreamDependentWorkflowDefinitions(anyLong());
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testExecute_ParallelMode_WithDependent() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_PARALLEL, ExecutionOrder.ASC_ORDER, 2);
        backfillWorkflowDTO.getBackfillParams().setBackfillDependentMode(ComplementDependentMode.ALL_DEPENDENT);
        backfillWorkflowDTO.getBackfillParams().setExpectedParallelismNumber(2);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(1001);
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(anyLong()))
                .thenReturn(new ArrayList<>());

        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            List<Integer> result = backfillWorkflowExecutorDelegate.execute(backfillWorkflowDTO);

            Assertions.assertNotNull(result);
            Assertions.assertEquals(1, result.size());
            // Verify dependent workflow lookup was called
            verify(workflowLineageService, times(1))
                    .queryDownstreamDependentWorkflowDefinitions(anyLong());
        }
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testBuildDependentBackfillDTO_Success() throws Exception {
        BackfillWorkflowDTO originalDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinition(100L, 200L, "day", "last1Days");

        WorkflowDefinition dependentWorkflow = new WorkflowDefinition();
        dependentWorkflow.setCode(200L);
        dependentWorkflow.setReleaseState(ReleaseState.ONLINE);
        dependentWorkflow.setVersion(1);
        dependentWorkflow.setWarningGroupId(1);

        when(workflowDefinitionDao.queryByCode(200L)).thenReturn(Optional.of(dependentWorkflow));
        when(processService.queryReleaseSchedulerListByWorkflowDefinitionCode(200L))
                .thenReturn(new ArrayList<>());

        BackfillWorkflowDTO transformedDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        when(backfillWorkflowRequestTransformer.transform(any())).thenReturn(transformedDTO);

        BackfillWorkflowDTO result = (BackfillWorkflowDTO) buildDependentBackfillDTOMethod.invoke(
                backfillWorkflowExecutorDelegate, originalDTO, dependentDef);

        Assertions.assertNotNull(result);
        verify(workflowDefinitionDao, times(1)).queryByCode(200L);
        verify(processService, times(1)).queryReleaseSchedulerListByWorkflowDefinitionCode(200L);
    }

    @Test
    public void testBuildDependentBackfillDTO_WorkflowNotFound() throws Exception {
        BackfillWorkflowDTO originalDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinition(100L, 200L, "day", "last1Days");

        when(workflowDefinitionDao.queryByCode(200L)).thenReturn(Optional.empty());

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            buildDependentBackfillDTOMethod.invoke(
                    backfillWorkflowExecutorDelegate, originalDTO, dependentDef);
        });
        Assertions.assertTrue(exception.getCause() instanceof ServiceException);
        Assertions.assertTrue(exception.getCause().getMessage().contains("Dependent workflow definition not found"));
    }

    @Test
    public void testBuildDependentBackfillDTO_WorkflowNotOnline() throws Exception {
        BackfillWorkflowDTO originalDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinition(100L, 200L, "day", "last1Days");

        WorkflowDefinition dependentWorkflow = new WorkflowDefinition();
        dependentWorkflow.setCode(200L);
        dependentWorkflow.setReleaseState(ReleaseState.OFFLINE);

        when(workflowDefinitionDao.queryByCode(200L)).thenReturn(Optional.of(dependentWorkflow));

        Exception exception = Assertions.assertThrows(Exception.class, () -> {
            buildDependentBackfillDTOMethod.invoke(
                    backfillWorkflowExecutorDelegate, originalDTO, dependentDef);
        });
        Assertions.assertTrue(exception.getCause() instanceof ServiceException);
        Assertions.assertTrue(exception.getCause().getMessage().contains("is not online"));
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoBackfillDependentWorkflow_NoDependencies() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        backfillWorkflowDTO.getBackfillParams().setAllLevelDependent(false);

        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(anyLong()))
                .thenReturn(new ArrayList<>());

        doBackfillDependentWorkflowMethod.invoke(
                backfillWorkflowExecutorDelegate, backfillWorkflowDTO);

        // Should return without error when no dependencies
        verify(workflowLineageService, times(1))
                .queryDownstreamDependentWorkflowDefinitions(anyLong());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testDoBackfillDependentWorkflow_WithDependencies() throws Exception {
        BackfillWorkflowDTO backfillWorkflowDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        backfillWorkflowDTO.getBackfillParams().setAllLevelDependent(false);

        DependentWorkflowDefinition dependentDef = createDependentWorkflowDefinition(100L, 200L, "day", "last1Days");
        when(workflowLineageService.queryDownstreamDependentWorkflowDefinitions(100L))
                .thenReturn(Collections.singletonList(dependentDef));

        WorkflowDefinition dependentWorkflow = new WorkflowDefinition();
        dependentWorkflow.setCode(200L);
        dependentWorkflow.setReleaseState(ReleaseState.ONLINE);
        dependentWorkflow.setVersion(1);
        dependentWorkflow.setWarningGroupId(1);

        when(workflowDefinitionDao.queryByCode(200L)).thenReturn(Optional.of(dependentWorkflow));
        when(processService.queryReleaseSchedulerListByWorkflowDefinitionCode(200L))
                .thenReturn(new ArrayList<>());

        BackfillWorkflowDTO transformedDTO = createBackfillWorkflowDTO(
                RunMode.RUN_MODE_SERIAL, ExecutionOrder.ASC_ORDER, 1);
        when(backfillWorkflowRequestTransformer.transform(any())).thenReturn(transformedDTO);

        Server masterServer = new Server();
        masterServer.setHost("localhost");
        masterServer.setPort(5678);
        when(registryClient.getRandomServer(RegistryNodeType.MASTER))
                .thenReturn(Optional.of(masterServer));

        WorkflowBackfillTriggerResponse response = WorkflowBackfillTriggerResponse.success(2001);
        try (MockedStatic<Clients> clientsMock = org.mockito.Mockito.mockStatic(Clients.class)) {
            IWorkflowControlClient client = org.mockito.Mockito.mock(IWorkflowControlClient.class);
            org.mockito.Mockito.when(client.backfillTriggerWorkflow(any(WorkflowBackfillTriggerRequest.class)))
                    .thenReturn(response);
            @SuppressWarnings("unchecked")
            Clients.JdkDynamicRpcClientProxyBuilder<IWorkflowControlClient> builder = org.mockito.Mockito.mock(
                    Clients.JdkDynamicRpcClientProxyBuilder.class);
            org.mockito.Mockito.when(builder.withHost(anyString())).thenReturn(client);
            clientsMock.when(() -> Clients.withService(IWorkflowControlClient.class)).thenReturn(builder);

            doBackfillDependentWorkflowMethod.invoke(
                    backfillWorkflowExecutorDelegate, backfillWorkflowDTO);

            // Should process dependent workflow
            verify(workflowDefinitionDao, times(1)).queryByCode(200L);
        }
    }

    /**
     * Helper method to create BackfillWorkflowDTO
     */
    private BackfillWorkflowDTO createBackfillWorkflowDTO(RunMode runMode, ExecutionOrder executionOrder,
                                                          int dateCount) {
        User user = new User();
        user.setId(1);

        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setCode(100L);
        workflowDefinition.setVersion(1);
        workflowDefinition.setReleaseState(ReleaseState.ONLINE);

        List<ZonedDateTime> backfillDateList = new ArrayList<>();
        for (int i = 0; i < dateCount; i++) {
            backfillDateList.add(DateUtils.stringToZoneDateTime("2025-01-" + (13 + i) + " 00:00:00"));
        }

        BackfillWorkflowDTO.BackfillParamsDTO backfillParams = BackfillWorkflowDTO.BackfillParamsDTO.builder()
                .runMode(runMode)
                .backfillDateList(backfillDateList)
                .expectedParallelismNumber(null)
                .backfillDependentMode(ComplementDependentMode.OFF_MODE)
                .allLevelDependent(false)
                .executionOrder(executionOrder)
                .build();

        return BackfillWorkflowDTO.builder()
                .loginUser(user)
                .workflowDefinition(workflowDefinition)
                .startNodes(null)
                .failureStrategy(FailureStrategy.CONTINUE)
                .taskDependType(TaskDependType.TASK_POST)
                .execType(CommandType.COMPLEMENT_DATA)
                .warningType(WarningType.NONE)
                .warningGroupId(null)
                .runMode(runMode)
                .workflowInstancePriority(Priority.MEDIUM)
                .workerGroup("default")
                .tenantCode("default")
                .environmentCode(null)
                .startParamList(null)
                .dryRun(Flag.NO)
                .backfillParams(backfillParams)
                .build();
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

        DependentItem dependentItem = new DependentItem();
        dependentItem.setDefinitionCode(upstreamWorkflowCode);
        dependentItem.setCycle(cycle);
        dependentItem.setDateValue(dateValue);

        DependentTaskModel dependentTaskModel = new DependentTaskModel();
        List<DependentItem> dependentItemList = new ArrayList<>();
        dependentItemList.add(dependentItem);
        dependentTaskModel.setDependItemList(dependentItemList);

        DependentParameters.Dependence dependence = new DependentParameters.Dependence();
        List<DependentTaskModel> dependTaskList = new ArrayList<>();
        dependTaskList.add(dependentTaskModel);
        dependence.setDependTaskList(dependTaskList);

        DependentParameters dependentParameters = new DependentParameters();
        dependentParameters.setDependence(dependence);

        definition.setTaskParams(JSONUtils.toJsonString(dependentParameters));

        return definition;
    }
}
