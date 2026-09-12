package com.workflow.service;

import com.workflow.api.dto.*;
import com.workflow.domain.*;
import com.workflow.repository.*;
import com.workflow.service.DagCycleDetector.CycleDetectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDefinitionService.class);

    private final WorkflowDefinitionRepository definitionRepo;
    private final DagCycleDetector cycleDetector;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitionRepo, DagCycleDetector cycleDetector) {
        this.definitionRepo = definitionRepo;
        this.cycleDetector = cycleDetector;
    }

    @Transactional
    public WorkflowDefinition createDefinition(CreateWorkflowDefinitionRequest request) {
        WorkflowDefinition definition = new WorkflowDefinition(request.getName(), request.getDescription());
        definition = definitionRepo.save(definition);

        Set<String> taskKeys = new HashSet<>();
        for (TaskDefinitionRequest taskReq : request.getTasks()) {
            if (!taskKeys.add(taskReq.getTaskKey())) {
                throw new IllegalArgumentException("Duplicate task key: " + taskReq.getTaskKey());
            }
            TaskDefinition task = new TaskDefinition(definition, taskReq.getTaskKey(), taskReq.getName(), taskReq.getAssigneeRole());
            task.setDescription(taskReq.getDescription());
            task.setDisplayOrder(taskReq.getDisplayOrder());
            definition.getTasks().add(task);
        }

        for (DependencyRequest depReq : request.getDependencies()) {
            TaskDependency dep = new TaskDependency(definition, depReq.getFromTaskKey(), depReq.getToTaskKey());
            definition.getDependencies().add(dep);
        }

        // CYCLE DETECTION — validate before persisting
        try {
            List<String> order = cycleDetector.validateAndSort(taskKeys, definition.getDependencies());
            log.info("Workflow definition '{}' validated. Topological order: {}", definition.getName(), order);
        } catch (CycleDetectedException e) {
            throw new InvalidDagException("Workflow definition contains a cycle: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new InvalidDagException("Invalid DAG: " + e.getMessage());
        }

        return definitionRepo.save(definition);
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listDefinitions() {
        return definitionRepo.findAll();
    }

    @Transactional(readOnly = true)
    public WorkflowDefinition getDefinition(String id) {
        return definitionRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("WorkflowDefinition", id));
    }

    public static class InvalidDagException extends RuntimeException {
        public InvalidDagException(String message) { super(message); }
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String type, String id) { super(type + " not found: " + id); }
    }
}
