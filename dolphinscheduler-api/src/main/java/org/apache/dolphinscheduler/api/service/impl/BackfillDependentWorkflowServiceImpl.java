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

package org.apache.dolphinscheduler.api.service.impl;

import org.apache.dolphinscheduler.api.service.BackfillDependentWorkflowService;
import org.apache.dolphinscheduler.api.service.WorkerGroupService;
import org.apache.dolphinscheduler.api.service.WorkflowLineageService;
import org.apache.dolphinscheduler.common.utils.DateUtils;
import org.apache.dolphinscheduler.dao.entity.DependentWorkflowDefinition;
import org.apache.dolphinscheduler.plugin.task.api.model.DateInterval;
import org.apache.dolphinscheduler.plugin.task.api.model.DependentItem;
import org.apache.dolphinscheduler.plugin.task.api.utils.DependentUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BackfillDependentWorkflowServiceImpl implements BackfillDependentWorkflowService {

    @Autowired
    private WorkflowLineageService workflowLineageService;

    @Autowired
    private WorkerGroupService workerGroupService;

    @Override
    public List<DependentWorkflowDefinition> getComplementDependentDefinitionList(
                                                                                  long workflowDefinitionCode,
                                                                                  String workerGroup,
                                                                                  boolean allLevelDependent) {
        List<DependentWorkflowDefinition> dependentWorkflowDefinitionList =
                setWorkerGroupForDependentWorkflows(
                        workflowLineageService.queryDownstreamDependentWorkflowDefinitions(workflowDefinitionCode),
                        workerGroup);

        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        // Filter out the current workflow itself to avoid self-triggering
        dependentWorkflowDefinitionList = dependentWorkflowDefinitionList.stream()
                .filter(def -> def.getWorkflowDefinitionCode() != workflowDefinitionCode)
                .collect(Collectors.toList());

        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        if (!allLevelDependent) {
            // Only direct downstream (Level 1)
            return dependentWorkflowDefinitionList;
        }

        // For all level dependent, traverse downstream workflows recursively
        // Use Set to prevent duplicate workflow definitions
        Set<Long> processedWorkflowCodes = new HashSet<>();
        for (DependentWorkflowDefinition def : dependentWorkflowDefinitionList) {
            processedWorkflowCodes.add(def.getWorkflowDefinitionCode());
        }

        List<DependentWorkflowDefinition> childList = new ArrayList<>(dependentWorkflowDefinitionList);

        while (true) {
            List<DependentWorkflowDefinition> childDependentList = new ArrayList<>();

            for (DependentWorkflowDefinition dependentWorkflowDefinition : childList) {
                List<DependentWorkflowDefinition> downstreamList =
                        setWorkerGroupForDependentWorkflows(
                                workflowLineageService.queryDownstreamDependentWorkflowDefinitions(
                                        dependentWorkflowDefinition.getWorkflowDefinitionCode()),
                                workerGroup);

                for (DependentWorkflowDefinition downstream : downstreamList) {
                    // Duplicate prevention: only add if not already processed
                    if (processedWorkflowCodes.add(downstream.getWorkflowDefinitionCode())) {
                        childDependentList.add(downstream);
                    } else {
                        log.debug("Skipping already processed workflow definition code: {}",
                                downstream.getWorkflowDefinitionCode());
                    }
                }
            }

            if (childDependentList.isEmpty()) {
                // No more downstream workflows, all levels have been traversed
                break;
            }

            dependentWorkflowDefinitionList.addAll(childDependentList);
            childList = new ArrayList<>(childDependentList);
        }

        // Filter out the current workflow itself again (in case it appears in recursive traversal)
        List<DependentWorkflowDefinition> filteredList = dependentWorkflowDefinitionList.stream()
                .filter(def -> def.getWorkflowDefinitionCode() != workflowDefinitionCode)
                .collect(Collectors.toList());

        if (filteredList.size() < dependentWorkflowDefinitionList.size()) {
            log.warn("Filtered out {} self-dependent workflow(s) from dependent list for workflow definition code: {}",
                    dependentWorkflowDefinitionList.size() - filteredList.size(), workflowDefinitionCode);
        }

        log.info("Found {} dependent workflow definitions (allLevelDependent={}) for workflow definition code: {}",
                filteredList.size(), allLevelDependent, workflowDefinitionCode);
        return filteredList;
    }

    @Override
    public List<String> calculateDependentBackfillDates(
                                                        long upstreamWorkflowCode,
                                                        List<String> upstreamBackfillDates,
                                                        DependentWorkflowDefinition dependentWorkflowDefinition) {
        List<String> dependentBackfillDates = new ArrayList<>();

        // Get dependency cycle and dateValue from dependent workflow definition
        String dateValue = getDependentDateValue(upstreamWorkflowCode, dependentWorkflowDefinition);
        if (dateValue == null || dateValue.isEmpty()) {
            log.warn(
                    "Cannot get dateValue for dependent workflow definition code: {}, upstream workflow code: {}, using original backfill dates.",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(), upstreamWorkflowCode);
            return upstreamBackfillDates;
        }

        Date currentDate = new Date();

        for (String upstreamDateStr : upstreamBackfillDates) {
            try {
                Date upstreamDate = DateUtils.stringToDate(upstreamDateStr);
                if (upstreamDate == null) {
                    log.warn("Invalid upstream backfill date: {}, skip.", upstreamDateStr);
                    continue;
                }

                // Calculate date intervals based on dateValue
                List<DateInterval> dateIntervals = DependentUtils.getDateIntervalList(upstreamDate, dateValue);

                for (DateInterval dateInterval : dateIntervals) {
                    // Use the start time of the interval as the backfill date
                    Date dependentDate = dateInterval.getStartTime();

                    // Check if the date is in the future (after today)
                    // Only skip future dates, as upstream has changed, downstream should backfill
                    if (dependentDate.after(currentDate)) {
                        log.info("Calculated dependent backfill date {} is in the future, skip for upstream date {}.",
                                DateUtils.dateToString(dependentDate), upstreamDateStr);
                        continue;
                    }

                    // Add to backfill dates list (avoid duplicates)
                    // Since upstream has changed, downstream should backfill regardless of upstream instance status
                    String dependentDateStr = DateUtils.dateToString(dependentDate);
                    if (!dependentBackfillDates.contains(dependentDateStr)) {
                        dependentBackfillDates.add(dependentDateStr);
                    }
                }
            } catch (Exception e) {
                log.error("Error calculating dependent backfill date for upstream date: {}, error: {}",
                        upstreamDateStr, e.getMessage(), e);
            }
        }

        log.info("Calculated {} backfill dates for dependent workflow definition code: {} based on dependency cycle.",
                dependentBackfillDates.size(), dependentWorkflowDefinition.getWorkflowDefinitionCode());
        return dependentBackfillDates;
    }

    /**
     * Set worker group for dependent workflows if not already set
     */
    private List<DependentWorkflowDefinition> setWorkerGroupForDependentWorkflows(
                                                                                  List<DependentWorkflowDefinition> dependentWorkflowDefinitionList,
                                                                                  String workerGroup) {
        if (dependentWorkflowDefinitionList.isEmpty()) {
            return dependentWorkflowDefinitionList;
        }

        List<Long> workflowDefinitionCodeList =
                dependentWorkflowDefinitionList.stream().map(DependentWorkflowDefinition::getWorkflowDefinitionCode)
                        .collect(Collectors.toList());

        Map<Long, String> workflowDefinitionWorkerGroupMap =
                workerGroupService.queryWorkerGroupByWorkflowDefinitionCodes(workflowDefinitionCodeList);

        for (DependentWorkflowDefinition dependentWorkflowDefinition : dependentWorkflowDefinitionList) {
            if (workflowDefinitionWorkerGroupMap
                    .get(dependentWorkflowDefinition.getWorkflowDefinitionCode()) == null) {
                dependentWorkflowDefinition.setWorkerGroup(workerGroup);
            }
        }

        return dependentWorkflowDefinitionList;
    }

    /**
     * Get dateValue from dependent workflow definition for the specified upstream workflow
     */
    private String getDependentDateValue(long upstreamWorkflowCode,
                                         DependentWorkflowDefinition dependentWorkflowDefinition) {
        try {
            org.apache.dolphinscheduler.plugin.task.api.parameters.DependentParameters dependentParameters =
                    dependentWorkflowDefinition.getDependentParameters();
            if (dependentParameters == null || dependentParameters.getDependence() == null) {
                return null;
            }

            List<org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel> dependentTaskModelList =
                    dependentParameters.getDependence().getDependTaskList();

            for (org.apache.dolphinscheduler.plugin.task.api.model.DependentTaskModel dependentTaskModel : dependentTaskModelList) {
                List<DependentItem> dependentItemList = dependentTaskModel.getDependItemList();
                for (DependentItem dependentItem : dependentItemList) {
                    if (upstreamWorkflowCode == dependentItem.getDefinitionCode()) {
                        return dependentItem.getDateValue();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting dateValue from dependent workflow definition code: {}, error: {}",
                    dependentWorkflowDefinition.getWorkflowDefinitionCode(), e.getMessage(), e);
        }
        return null;
    }
}
