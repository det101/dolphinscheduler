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

package org.apache.dolphinscheduler.api.utils;

import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowInstance;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.enums.DataType;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.utils.GlobalParameterUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SensitivePropertyUtilsTest {

    @Test
    void mergeGlobalParamsKeepsOriginalOnPlaceholder() {
        String existing = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123")));
        String submitted = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", TaskConstants.SENSITIVE_DATA_MASK)));

        String merged = SensitivePropertyUtils.mergeGlobalParams(submitted, existing);
        List<Property> properties = GlobalParameterUtils.deserializeGlobalParameter(merged);

        Assertions.assertEquals("Secret123", properties.get(0).getValue());
        Assertions.assertTrue(properties.get(0).isSensitive());
    }

    @Test
    void mergeGlobalParamsRejectsFalseToTrueWithPlaceholder() {
        String existing = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(nonSensitive("pwd", "plain")));
        String submitted = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", TaskConstants.SENSITIVE_DATA_MASK)));

        Assertions.assertThrows(ServiceException.class,
                () -> SensitivePropertyUtils.mergeGlobalParams(submitted, existing));
    }

    @Test
    void mergeGlobalParamsRejectsTrueToFalseWithPlaceholder() {
        String existing = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123")));
        String submitted = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(nonSensitive("pwd", TaskConstants.SENSITIVE_DATA_MASK)));

        Assertions.assertThrows(ServiceException.class,
                () -> SensitivePropertyUtils.mergeGlobalParams(submitted, existing));
    }

    @Test
    void mergeGlobalParamsKeepsOriginalAfterToggleBackToSensitive() {
        String existing = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123")));
        String submitted = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", TaskConstants.SENSITIVE_DATA_MASK)));

        String merged = SensitivePropertyUtils.mergeGlobalParams(submitted, existing);
        List<Property> properties = GlobalParameterUtils.deserializeGlobalParameter(merged);
        Assertions.assertEquals("Secret123", properties.get(0).getValue());
        Assertions.assertTrue(properties.get(0).isSensitive());
    }

    @Test
    void emptyStringIsPersistedAsEmpty() {
        String existing = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123")));
        String submitted = GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "")));

        String merged = SensitivePropertyUtils.mergeGlobalParams(submitted, existing);
        List<Property> properties = GlobalParameterUtils.deserializeGlobalParameter(merged);
        Assertions.assertEquals("", properties.get(0).getValue());
    }

    @Test
    void mergeLocalParamsRejectsTrueToFalseWithPlaceholder() {
        String existing =
                "{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\"abc\",\"sensitive\":true}]}";
        String submitted =
                "{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\",\"value\":\""
                        + TaskConstants.SENSITIVE_DATA_MASK + "\",\"sensitive\":false}]}";

        Assertions.assertThrows(ServiceException.class,
                () -> SensitivePropertyUtils.mergeLocalParamsInTaskParams(submitted, existing));
    }

    @Test
    void maskWorkflowDefinitionDoesNotMutateSourceListSemantics() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123"))));

        SensitivePropertyUtils.maskWorkflowDefinition(workflowDefinition);
        Assertions.assertTrue(workflowDefinition.getGlobalParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertFalse(workflowDefinition.getGlobalParams().contains("Secret123"));
    }

    @Test
    void maskLocalParamsInTaskParams() {
        String taskParams = "{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"abc\",\"sensitive\":true}]}";
        String masked = SensitivePropertyUtils.maskLocalParamsInTaskParams(taskParams);
        Assertions.assertTrue(masked.contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertFalse(masked.contains("\"abc\""));
    }

    @Test
    void maskTaskDefinition() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskParams("{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"abc\",\"sensitive\":true}]}");
        SensitivePropertyUtils.maskTaskDefinition(taskDefinition);
        Assertions.assertTrue(taskDefinition.getTaskParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
    }

    @Test
    void maskWorkflowDefinitionClearsCachedGlobalParamMap() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123"))));
        Assertions.assertEquals("Secret123", workflowDefinition.getGlobalParamMap().get("pwd"));

        SensitivePropertyUtils.maskWorkflowDefinition(workflowDefinition);
        Assertions.assertEquals(TaskConstants.SENSITIVE_DATA_MASK, workflowDefinition.getGlobalParamMap().get("pwd"));
    }

    @Test
    void copyAndMaskTaskDefinitionDoesNotMutateOriginal() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskParams("{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"abc\",\"sensitive\":true}]}");

        TaskDefinition masked = SensitivePropertyUtils.copyAndMaskTaskDefinition(taskDefinition);
        Assertions.assertTrue(masked.getTaskParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertTrue(taskDefinition.getTaskParams().contains("abc"));
        Assertions.assertNotSame(taskDefinition, masked);
    }

    @Test
    void copyAndMaskWorkflowDefinitionDoesNotMutatePersisted() {
        WorkflowDefinition persisted = new WorkflowDefinition();
        persisted.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123"))));

        WorkflowDefinition masked = SensitivePropertyUtils.copyAndMaskWorkflowDefinition(persisted);

        Assertions.assertTrue(persisted.getGlobalParams().contains("Secret123"));
        Assertions.assertFalse(persisted.getGlobalParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertTrue(masked.getGlobalParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertFalse(masked.getGlobalParams().contains("Secret123"));
        Assertions.assertNotSame(persisted, masked);
    }

    @Test
    void copyAndMaskWorkflowInstanceDoesNotMutatePersisted() {
        WorkflowInstance persisted = new WorkflowInstance();
        persisted.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(
                Collections.singletonList(sensitive("pwd", "Secret123"))));

        WorkflowInstance masked = SensitivePropertyUtils.copyAndMaskWorkflowInstance(persisted);

        Assertions.assertTrue(persisted.getGlobalParams().contains("Secret123"));
        Assertions.assertTrue(masked.getGlobalParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertNotSame(persisted, masked);
    }

    @Test
    void copyAndMaskTaskDefinitionMapDoesNotMutateOriginal() {
        TaskDefinition taskDefinition = new TaskDefinition();
        taskDefinition.setTaskParams("{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"abc\",\"sensitive\":true}]}");
        Map<Long, List<TaskDefinition>> map = new LinkedHashMap<>();
        map.put(1L, Collections.singletonList(taskDefinition));

        Map<Long, List<TaskDefinition>> masked = SensitivePropertyUtils.copyAndMaskTaskDefinitionMap(map);

        Assertions.assertNotSame(map, masked);
        Assertions.assertTrue(masked.get(1L).get(0).getTaskParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertTrue(taskDefinition.getTaskParams().contains("abc"));
    }

    @Test
    void copyAndMaskTaskInstanceDoesNotMutateOriginal() {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskParams("{\"localParams\":[{\"prop\":\"token\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"abc\",\"sensitive\":true}]}");
        taskInstance.setVarPool("[{\"prop\":\"pwd\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"Secret123\",\"sensitive\":true}]");

        TaskInstance masked = SensitivePropertyUtils.copyAndMaskTaskInstance(taskInstance);

        Assertions.assertTrue(masked.getTaskParams().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertTrue(masked.getVarPool().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertTrue(taskInstance.getTaskParams().contains("abc"));
        Assertions.assertTrue(taskInstance.getVarPool().contains("Secret123"));
        Assertions.assertNotSame(taskInstance, masked);
    }

    @Test
    void copyAndMaskWorkflowInstanceMasksVarPool() {
        WorkflowInstance persisted = new WorkflowInstance();
        persisted.setVarPool("[{\"prop\":\"pwd\",\"direct\":\"IN\",\"type\":\"VARCHAR\","
                + "\"value\":\"Secret123\",\"sensitive\":true}]");

        WorkflowInstance masked = SensitivePropertyUtils.copyAndMaskWorkflowInstance(persisted);

        Assertions.assertTrue(persisted.getVarPool().contains("Secret123"));
        Assertions.assertTrue(masked.getVarPool().contains(TaskConstants.SENSITIVE_DATA_MASK));
        Assertions.assertFalse(masked.getVarPool().contains("Secret123"));
    }

    private static Property sensitive(String prop, String value) {
        return Property.builder()
                .prop(prop)
                .direct(Direct.IN)
                .type(DataType.VARCHAR)
                .value(value)
                .sensitive(true)
                .build();
    }

    private static Property nonSensitive(String prop, String value) {
        return Property.builder()
                .prop(prop)
                .direct(Direct.IN)
                .type(DataType.VARCHAR)
                .value(value)
                .sensitive(false)
                .build();
    }
}
