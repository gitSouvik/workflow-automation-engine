package com.workflow.service;

import com.workflow.api.dto.StartWorkflowRequest;
import com.workflow.domain.*;
import com.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkflowInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceService.class);

    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskInstanceRepo;
    private final ApprovalEventRepository approvalEventRepo;
    private final WorkflowStateMachine stateMachine;

    public WorkflowInstanceService(WorkflowDefinitionRepository definitionRepo,
                                    WorkflowInstanceRepository instanceRepo,
                                    TaskInstanceRepository taskInstanceRepo,
                                    ApprovalEventRepository approvalEventRepo,
                                    WorkflowStateMachine stateMachine) {
        this.definitionRepo = definitionRepo;
        this.instanceRepo = instanceRepo;
        this.taskInstanceRepo = taskInstanceRepo;
        this.approvalEventRepo = approvalEventRepo;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public WorkflowInstance startInstance(StartWorkflowRequest request) {
        WorkflowDefinition definition = definitionRepo.findById(request.getDefinitionId())
            .orElseThrow(() -> new WorkflowDefinitionService.ResourceNotFoundException("WorkflowDefinition", request.getDefinitionId()));

        WorkflowInstance instance = new WorkflowInstance(
            definition.getId(), definition.getName(), request.getTitle(), request.getInitiatorId());
        
        if (request.getVariables() != null) {
            instance.getVariables().putAll(request.getVariables());
        }
        
        instance = instanceRepo.save(instance);

        for (TaskDefinition taskDef : definition.getTasks()) {
            TaskInstance ti = new TaskInstance(instance, taskDef.getTaskKey(), taskDef.getName(), taskDef.getAssigneeRole());
            instance.getTaskInstances().add(ti);
        }
        instanceRepo.save(instance);

        stateMachine.recomputeReadiness(instance);

        log.info("Started workflow instance [{}] '{}' from definition '{}'",
            instance.getId(), instance.getTitle(), definition.getName());

        return instanceRepo.findById(instance.getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public WorkflowInstance getInstance(String instanceId) {
        return instanceRepo.findById(instanceId)
            .orElseThrow(() -> new WorkflowDefinitionService.ResourceNotFoundException("WorkflowInstance", instanceId));
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> getAllInstances() {
        return instanceRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<TaskInstance> getTaskInstances(String instanceId) {
        return taskInstanceRepo.findByWorkflowInstanceId(instanceId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalEvent> getHistory(String instanceId) {
        getInstance(instanceId);
        return approvalEventRepo.findByWorkflowInstanceIdOrderByTimestampAsc(instanceId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowInstance> getRunningInstances() {
        return instanceRepo.findAllRunning();
    }
}
