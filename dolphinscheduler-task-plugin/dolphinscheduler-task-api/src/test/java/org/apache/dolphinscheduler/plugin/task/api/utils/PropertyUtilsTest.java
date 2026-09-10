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

package org.apache.dolphinscheduler.plugin.task.api.utils;

import org.apache.dolphinscheduler.common.utils.JSONUtils;
import org.apache.dolphinscheduler.plugin.task.api.TaskConstants;
import org.apache.dolphinscheduler.plugin.task.api.enums.DataType;
import org.apache.dolphinscheduler.plugin.task.api.enums.Direct;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PropertyUtilsTest {

    @Test
    void mapFormatDropsSensitiveMask() {
        List<Property> startParams =
                PropertyUtils.startParamsTransformPropertyList("{\"pwd\":\"" + TaskConstants.SENSITIVE_DATA_MASK
                        + "\"}");

        Assertions.assertTrue(startParams.isEmpty());
    }

    @Test
    void listFormatDropsSensitiveMask() {
        Property submitted = Property.builder()
                .prop("pwd")
                .direct(Direct.IN)
                .type(DataType.VARCHAR)
                .value(TaskConstants.SENSITIVE_DATA_MASK)
                .sensitive(true)
                .build();
        String json = JSONUtils.toJsonString(Collections.singletonList(submitted));

        List<Property> startParams = PropertyUtils.startParamsTransformPropertyList(json);

        Assertions.assertTrue(startParams.isEmpty());
    }

    @Test
    void mapFormatKeepsRealOverrideAndDropsMask() {
        List<Property> startParams = PropertyUtils.startParamsTransformPropertyList(
                "{\"pwd\":\"" + TaskConstants.SENSITIVE_DATA_MASK + "\",\"name\":\"alice\"}");

        Assertions.assertEquals(1, startParams.size());
        Assertions.assertEquals("name", startParams.get(0).getProp());
        Assertions.assertEquals("alice", startParams.get(0).getValue());
    }

    @Test
    void listFormatKeepsEmptyStringAsRealOverride() {
        Property submitted = Property.builder()
                .prop("pwd")
                .direct(Direct.IN)
                .type(DataType.VARCHAR)
                .value("")
                .sensitive(true)
                .build();

        List<Property> startParams =
                PropertyUtils.startParamsTransformPropertyList(JSONUtils.toJsonString(Collections.singletonList(
                        submitted)));

        Assertions.assertEquals(1, startParams.size());
        Assertions.assertEquals("", startParams.get(0).getValue());
        Assertions.assertTrue(startParams.get(0).isSensitive());
    }

    @Test
    void listFormatKeepsSensitiveFlagOnRealValue() {
        Property submitted = Property.builder()
                .prop("pwd")
                .direct(Direct.IN)
                .type(DataType.VARCHAR)
                .value("new-secret")
                .sensitive(true)
                .build();

        List<Property> startParams =
                PropertyUtils.startParamsTransformPropertyList(JSONUtils.toJsonString(Collections.singletonList(
                        submitted)));

        Assertions.assertEquals(1, startParams.size());
        Assertions.assertEquals("new-secret", startParams.get(0).getValue());
        Assertions.assertTrue(startParams.get(0).isSensitive());
    }
}
