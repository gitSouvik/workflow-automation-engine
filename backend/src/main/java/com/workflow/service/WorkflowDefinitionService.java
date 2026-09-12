package com.workflow.service;

import com.workflow.api.dto.*;
import com.workflow.domain.*;
import com.workflow.repository.*;
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
    private final WorkflowValidator workflowValidator;

    public WorkflowDefinitionService(WorkflowDefinitionRepository definitionRepo, WorkflowValidator workflowValidator) {
        this.definitionRepo = definitionRepo;
        this.workflowValidator = workflowValidator;
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
            if (taskReq.getNodeType() != null) {
                task.setNodeType(taskReq.getNodeType());
            }
            definition.getTasks().add(task);
        }

        for (DependencyRequest depReq : request.getDependencies()) {
            TaskDependency dep = new TaskDependency(definition, depReq.getFromTaskKey(), depReq.getToTaskKey());
            dep.setConditionExpression(depReq.getConditionExpression());
            definition.getDependencies().add(dep);
        }

        // VALIDATION — cycle and orphan detection
        try {
            workflowValidator.validate(definition);
            log.info("Workflow definition '{}' validated successfully.", definition.getName());
        } catch (IllegalArgumentException e) {
            throw new InvalidDagException(e.getMessage());
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
