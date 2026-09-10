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

import static org.apache.dolphinscheduler.common.constants.Constants.LOCAL_PARAMS;
import static org.apache.dolphinscheduler.plugin.task.api.TaskConstants.LOCAL_PARAMS_LIST;

import org.apache.dolphinscheduler.api.dto.workflow.WorkflowDefinitionVariablesDTO;
import org.apache.dolphinscheduler.api.dto.workflowInstance.WorkflowInstanceTaskListDTO;
import org.apache.dolphinscheduler.api.dto.workflowInstance.WorkflowInstanceVariablesDTO;
import org.apache.dolphinscheduler.api.enums.Status;
import org.apache.dolphinscheduler.api.exceptions.ServiceException;
import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.dao.entity.DagData;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.WorkflowDefinition;
import org.apache.dolphinscheduler.dao.entity.WorkflowInstance;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.utils.GlobalParameterUtils;
import org.apache.dolphinscheduler.plugin.task.api.utils.PropertySensitiveUtils;
import org.apache.dolphinscheduler.plugin.task.api.utils.VarPoolUtils;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lombok.experimental.UtilityClass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * API-layer helpers for sensitive {@link Property} masking and keep-original merge.
 * <p>
 * PR1 of DSIP-105: no encryption. Crypto (PasswordUtils) belongs to a follow-up PR.
 * Controllers call {@code copyAndMask*} on HTTP responses; persistence and in-process
 * service results stay plaintext.
 */
@UtilityClass
public class SensitivePropertyUtils {

    public String mergeGlobalParams(String submittedGlobalParams, String existingGlobalParams) {
        List<Property> submittedProperties = GlobalParameterUtils.deserializeGlobalParameter(submittedGlobalParams);
        if (CollectionUtils.isEmpty(submittedProperties)) {
            return submittedGlobalParams;
        }
        List<Property> existingProperties = GlobalParameterUtils.deserializeGlobalParameter(existingGlobalParams);
        validateSensitivePlaceholders(submittedProperties, existingProperties);
        return GlobalParameterUtils.serializeGlobalParameter(
                PropertySensitiveUtils.mergeSensitiveValuePlaceholders(submittedProperties, existingProperties));
    }

    private String maskGlobalParams(String globalParams) {
        List<Property> properties = GlobalParameterUtils.deserializeGlobalParameter(globalParams);
        if (CollectionUtils.isEmpty(properties)) {
            return globalParams;
        }
        return GlobalParameterUtils.serializeGlobalParameter(PropertySensitiveUtils.maskSensitiveValues(properties));
    }

    private List<Property> maskSensitiveValues(List<Property> properties) {
        return PropertySensitiveUtils.maskSensitiveValues(properties);
    }

    private DagData maskDagData(DagData dagData) {
        if (dagData == null) {
            return null;
        }
        maskWorkflowDefinition(dagData.getWorkflowDefinition());
        if (CollectionUtils.isNotEmpty(dagData.getTaskDefinitionList())) {
            dagData.getTaskDefinitionList().forEach(SensitivePropertyUtils::maskTaskDefinition);
        }
        return dagData;
    }

    private WorkflowDefinition maskWorkflowDefinition(WorkflowDefinition workflowDefinition) {
        if (workflowDefinition == null) {
            return null;
        }
        List<Property> globalParams =
                PropertySensitiveUtils.maskSensitiveValues(
                        GlobalParameterUtils.deserializeGlobalParameter(workflowDefinition.getGlobalParams()));
        workflowDefinition.setGlobalParams(GlobalParameterUtils.serializeGlobalParameter(globalParams));
        workflowDefinition.setGlobalParamList(globalParams);
        // setGlobalParams does not rebuild the cached map; drop it so getters re-read masked values
        workflowDefinition.setGlobalParamMap(null);
        return workflowDefinition;
    }

    private TaskDefinition maskTaskDefinition(TaskDefinition taskDefinition) {
        if (taskDefinition == null) {
            return null;
        }
        taskDefinition.setTaskParams(maskLocalParamsInTaskParams(taskDefinition.getTaskParams()));
        taskDefinition.setTaskParamMap(null);
        return taskDefinition;
    }

    private TaskInstance maskTaskInstance(TaskInstance taskInstance) {
        if (taskInstance == null) {
            return null;
        }
        taskInstance.setTaskParams(maskLocalParamsInTaskParams(taskInstance.getTaskParams()));
        taskInstance.setVarPool(maskVarPool(taskInstance.getVarPool()));
        return taskInstance;
    }

    /**
     * JSON deep-copy then mask, so MyBatis-mapped entities (e.g. version list records) stay unchanged.
     */
    @SuppressWarnings("unchecked")
    public <T extends WorkflowDefinition> T copyAndMaskWorkflowDefinition(T workflowDefinition) {
        if (workflowDefinition == null) {
            return null;
        }
        T copy = copyOrThrow(workflowDefinition, (Class<T>) workflowDefinition.getClass(), "workflow definition");
        return (T) maskWorkflowDefinition(copy);
    }

    /**
     * JSON deep-copy then mask, so MyBatis-mapped entities (e.g. version list records) stay unchanged.
     */
    @SuppressWarnings("unchecked")
    public <T extends TaskDefinition> T copyAndMaskTaskDefinition(T taskDefinition) {
        if (taskDefinition == null) {
            return null;
        }
        T copy = copyOrThrow(taskDefinition, (Class<T>) taskDefinition.getClass(), "task definition");
        return (T) maskTaskDefinition(copy);
    }

    public DagData copyAndMaskDagData(DagData dagData) {
        if (dagData == null) {
            return null;
        }
        DagData copy = JSONUtils.parseObject(JSONUtils.toJsonString(dagData), DagData.class);
        if (copy == null) {
            return new DagData(copyAndMaskWorkflowDefinition(dagData.getWorkflowDefinition()),
                    dagData.getWorkflowTaskRelationList(),
                    copyTaskDefinitionList(dagData.getTaskDefinitionList()));
        }
        return maskDagData(copy);
    }

    public WorkflowInstance copyAndMaskWorkflowInstance(WorkflowInstance workflowInstance) {
        if (workflowInstance == null) {
            return null;
        }
        WorkflowInstance copy = copyOrThrow(workflowInstance, WorkflowInstance.class, "workflow instance");
        copy.setGlobalParams(maskGlobalParams(copy.getGlobalParams()));
        copy.setVarPool(maskVarPool(copy.getVarPool()));
        if (copy.getDagData() != null) {
            maskDagData(copy.getDagData());
        }
        return copy;
    }

    /**
     * JSON deep-copy then mask, so MyBatis-mapped task instances stay unchanged.
     */
    @SuppressWarnings("unchecked")
    public <T extends TaskInstance> T copyAndMaskTaskInstance(T taskInstance) {
        if (taskInstance == null) {
            return null;
        }
        T copy = copyOrThrow(taskInstance, (Class<T>) taskInstance.getClass(), "task instance");
        return (T) maskTaskInstance(copy);
    }

    public <T extends TaskInstance> List<T> copyAndMaskTaskInstances(List<T> taskInstances) {
        if (CollectionUtils.isEmpty(taskInstances)) {
            return taskInstances;
        }
        List<T> masked = new ArrayList<>(taskInstances.size());
        for (T taskInstance : taskInstances) {
            masked.add(copyAndMaskTaskInstance(taskInstance));
        }
        return masked;
    }

    public <T extends TaskInstance> PageInfo<T> copyAndMaskTaskInstancePage(PageInfo<T> pageInfo) {
        if (pageInfo == null) {
            return null;
        }
        PageInfo<T> copy = new PageInfo<>();
        copy.setTotal(pageInfo.getTotal());
        copy.setPageSize(pageInfo.getPageSize());
        copy.setCurrentPage(pageInfo.getCurrentPage());
        copy.setPageNo(pageInfo.getPageNo());
        copy.setTotalList(copyAndMaskTaskInstances(pageInfo.getTotalList()));
        return copy;
    }

    public WorkflowInstanceTaskListDTO copyAndMaskWorkflowInstanceTaskList(WorkflowInstanceTaskListDTO dto) {
        if (dto == null) {
            return null;
        }
        return new WorkflowInstanceTaskListDTO(
                dto.getWorkflowInstanceState(),
                copyAndMaskTaskInstances(dto.getTaskList()));
    }

    public List<DagData> copyAndMaskDagDataList(List<DagData> dagDataList) {
        if (CollectionUtils.isEmpty(dagDataList)) {
            return dagDataList;
        }
        List<DagData> masked = new ArrayList<>(dagDataList.size());
        for (DagData dagData : dagDataList) {
            masked.add(copyAndMaskDagData(dagData));
        }
        return masked;
    }

    public <T extends WorkflowDefinition> List<T> copyAndMaskWorkflowDefinitions(List<T> workflowDefinitions) {
        if (CollectionUtils.isEmpty(workflowDefinitions)) {
            return workflowDefinitions;
        }
        List<T> masked = new ArrayList<>(workflowDefinitions.size());
        for (T workflowDefinition : workflowDefinitions) {
            masked.add(copyAndMaskWorkflowDefinition(workflowDefinition));
        }
        return masked;
    }

    public <T extends TaskDefinition> List<T> copyAndMaskTaskDefinitions(List<T> taskDefinitions) {
        if (CollectionUtils.isEmpty(taskDefinitions)) {
            return taskDefinitions;
        }
        List<T> masked = new ArrayList<>(taskDefinitions.size());
        for (T taskDefinition : taskDefinitions) {
            masked.add(copyAndMaskTaskDefinition(taskDefinition));
        }
        return masked;
    }

    public Map<Long, List<TaskDefinition>> copyAndMaskTaskDefinitionMap(Map<Long, List<TaskDefinition>> taskDefinitionMap) {
        if (taskDefinitionMap == null) {
            return null;
        }
        Map<Long, List<TaskDefinition>> masked = new LinkedHashMap<>();
        for (Map.Entry<Long, List<TaskDefinition>> entry : taskDefinitionMap.entrySet()) {
            masked.put(entry.getKey(), copyAndMaskTaskDefinitions(entry.getValue()));
        }
        return masked;
    }

    public <T extends WorkflowDefinition> PageInfo<T> copyAndMaskWorkflowDefinitionPage(PageInfo<T> pageInfo) {
        if (pageInfo == null) {
            return null;
        }
        PageInfo<T> copy = new PageInfo<>();
        copy.setTotal(pageInfo.getTotal());
        copy.setPageSize(pageInfo.getPageSize());
        copy.setCurrentPage(pageInfo.getCurrentPage());
        copy.setPageNo(pageInfo.getPageNo());
        copy.setTotalList(copyAndMaskWorkflowDefinitions(pageInfo.getTotalList()));
        return copy;
    }

    public <T extends TaskDefinition> PageInfo<T> copyAndMaskTaskDefinitionPage(PageInfo<T> pageInfo) {
        if (pageInfo == null) {
            return null;
        }
        PageInfo<T> copy = new PageInfo<>();
        copy.setTotal(pageInfo.getTotal());
        copy.setPageSize(pageInfo.getPageSize());
        copy.setCurrentPage(pageInfo.getCurrentPage());
        copy.setPageNo(pageInfo.getPageNo());
        copy.setTotalList(copyAndMaskTaskDefinitions(pageInfo.getTotalList()));
        return copy;
    }

    public WorkflowDefinitionVariablesDTO copyAndMaskWorkflowDefinitionVariables(WorkflowDefinitionVariablesDTO dto) {
        if (dto == null) {
            return null;
        }
        return new WorkflowDefinitionVariablesDTO(
                maskSensitiveValues(dto.getGlobalParams()),
                copyAndMaskLocalParamsMap(dto.getLocalParams()));
    }

    public WorkflowInstanceVariablesDTO copyAndMaskWorkflowInstanceVariables(WorkflowInstanceVariablesDTO dto) {
        if (dto == null) {
            return null;
        }
        return new WorkflowInstanceVariablesDTO(
                maskSensitiveValues(dto.getGlobalParams()),
                copyAndMaskLocalParamsMap(dto.getLocalParams()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> copyAndMaskLocalParamsMap(Map<String, Map<String, Object>> localParams) {
        if (localParams == null) {
            return null;
        }
        Map<String, Map<String, Object>> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : localParams.entrySet()) {
            Map<String, Object> inner = entry.getValue();
            if (inner == null) {
                masked.put(entry.getKey(), null);
                continue;
            }
            Map<String, Object> copied = new LinkedHashMap<>(inner);
            Object localParamsList = copied.get(LOCAL_PARAMS_LIST);
            if (localParamsList instanceof List) {
                copied.put(LOCAL_PARAMS_LIST, maskSensitiveValues((List<Property>) localParamsList));
            }
            masked.put(entry.getKey(), copied);
        }
        return masked;
    }

    private List<TaskDefinition> copyTaskDefinitionList(List<TaskDefinition> taskDefinitions) {
        if (CollectionUtils.isEmpty(taskDefinitions)) {
            return taskDefinitions;
        }
        List<TaskDefinition> copied = new ArrayList<>(taskDefinitions.size());
        for (TaskDefinition taskDefinition : taskDefinitions) {
            copied.add(copyAndMaskTaskDefinition(taskDefinition));
        }
        return copied;
    }

    public String mergeLocalParamsInTaskParams(String submittedTaskParams, String existingTaskParams) {
        return transformLocalParamsInTaskParams(submittedTaskParams, submittedProperties -> {
            List<Property> existingProperties = getLocalParams(existingTaskParams);
            validateSensitivePlaceholders(submittedProperties, existingProperties);
            return PropertySensitiveUtils.mergeSensitiveValuePlaceholders(submittedProperties, existingProperties);
        });
    }

    private String maskLocalParamsInTaskParams(String taskParams) {
        return transformLocalParamsInTaskParams(taskParams, PropertySensitiveUtils::maskSensitiveValues);
    }

    private String maskVarPool(String varPool) {
        if (StringUtils.isEmpty(varPool)) {
            return varPool;
        }
        List<Property> properties = VarPoolUtils.deserializeVarPool(varPool);
        if (CollectionUtils.isEmpty(properties)) {
            return varPool;
        }
        return VarPoolUtils.serializeVarPool(maskSensitiveValues(properties));
    }

    public List<Property> getLocalParams(String taskParams) {
        if (StringUtils.isEmpty(taskParams)) {
            return Collections.emptyList();
        }
        String localParams = JSONUtils.getNodeString(taskParams, LOCAL_PARAMS);
        if (StringUtils.isEmpty(localParams)) {
            return Collections.emptyList();
        }
        return JSONUtils.toList(localParams, Property.class);
    }

    public void validateSensitivePlaceholders(List<Property> submittedProperties, List<Property> existingProperties) {
        String invalidProp =
                PropertySensitiveUtils.findInvalidSensitivePlaceholderProp(submittedProperties, existingProperties);
        if (invalidProp != null) {
            throw new ServiceException(Status.REQUEST_PARAMS_NOT_VALID_ERROR,
                    "parameter '" + invalidProp
                            + "' cannot use ****** when creating, enabling, or disabling sensitive; please re-enter the value");
        }
    }

    private String transformLocalParamsInTaskParams(String taskParams,
                                                    Function<List<Property>, List<Property>> transformFunction) {
        if (StringUtils.isEmpty(taskParams)) {
            return taskParams;
        }
        ObjectNode taskParamsNode = JSONUtils.parseObject(taskParams);
        if (taskParamsNode == null) {
            return taskParams;
        }
        JsonNode localParamsNode = taskParamsNode.findValue(LOCAL_PARAMS);
        if (localParamsNode == null || localParamsNode.isNull()) {
            return taskParams;
        }
        List<Property> localParams = JSONUtils.toList(localParamsNode.toString(), Property.class);
        taskParamsNode.set(LOCAL_PARAMS, JSONUtils.toJsonNode(transformFunction.apply(localParams)));
        return JSONUtils.toJsonString(taskParamsNode);
    }

    private <T> T copyOrThrow(T source, Class<T> type, String name) {
        T copy = JSONUtils.parseObject(JSONUtils.toJsonString(source), type);
        if (copy == null) {
            throw new IllegalStateException("Failed to copy " + name + " for masking");
        }
        return copy;
    }
}
