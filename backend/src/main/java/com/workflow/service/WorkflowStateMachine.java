package com.workflow.service;

import com.workflow.domain.*;
import com.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core workflow state machine.
 * All state is DB-driven — no in-memory state, safe across crashes.
 *
 * READINESS RULE: A task becomes READY iff ALL its predecessors are APPROVED.
 */
@Service
public class WorkflowStateMachine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStateMachine.class);

    private final TaskInstanceRepository taskInstanceRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowInstanceRepository instanceRepo;

    public WorkflowStateMachine(TaskInstanceRepository taskInstanceRepo,
                                 WorkflowDefinitionRepository definitionRepo,
                                 WorkflowInstanceRepository instanceRepo) {
        this.taskInstanceRepo = taskInstanceRepo;
        this.definitionRepo = definitionRepo;
        this.instanceRepo = instanceRepo;
    }

    @Transactional
    public void advanceWorkflow(WorkflowInstance instance, String completedTaskKey) {
        WorkflowDefinition definition = definitionRepo.findById(instance.getDefinitionId())
            .orElseThrow(() -> new IllegalStateException("Definition not found: " + instance.getDefinitionId()));

        List<TaskInstance> allTasks = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
        Map<String, TaskStatus> taskStatusMap = allTasks.stream()
            .collect(Collectors.toMap(TaskInstance::getTaskKey, TaskInstance::getStatus));

        Map<String, Set<String>> predecessors = buildPredecessorMap(definition);

        List<String> successors = definition.getDependencies().stream()
            .filter(d -> d.getFromTaskKey().equals(completedTaskKey))
            .map(TaskDependency::getToTaskKey)
            .toList();

        TaskStatus completedStatus = taskStatusMap.get(completedTaskKey);

        for (String successorKey : successors) {
            TaskInstance successor = allTasks.stream()
                .filter(t -> t.getTaskKey().equals(successorKey))
                .findFirst().orElse(null);

            if (successor == null || isTerminal(successor.getStatus())) continue;

            if (completedStatus == TaskStatus.REJECTED) {
                log.info("[{}] Task '{}' rejected → cascading SKIP to '{}'", instance.getId(), completedTaskKey, successorKey);
                successor.setStatus(TaskStatus.SKIPPED);
                taskInstanceRepo.save(successor);
                advanceWorkflow(instance, successorKey);
            } else if (completedStatus == TaskStatus.APPROVED) {
                Set<String> preds = predecessors.getOrDefault(successorKey, Set.of());
                boolean allPredsApproved = preds.stream().allMatch(p -> taskStatusMap.get(p) == TaskStatus.APPROVED);
                if (allPredsApproved && successor.getStatus() == TaskStatus.PENDING) {
                    log.info("[{}] All predecessors of '{}' approved → READY", instance.getId(), successorKey);
                    successor.setStatus(TaskStatus.READY);
                    taskInstanceRepo.save(successor);
                }
            }
        }

        List<TaskInstance> refreshed = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
        checkWorkflowCompletion(instance, refreshed);
    }

    @Transactional
    public void recomputeReadiness(WorkflowInstance instance) {
        WorkflowDefinition definition = definitionRepo.findById(instance.getDefinitionId()).orElseThrow();
        List<TaskInstance> allTasks = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
        Map<String, TaskStatus> statusMap = allTasks.stream()
            .collect(Collectors.toMap(TaskInstance::getTaskKey, TaskInstance::getStatus));
        Map<String, Set<String>> predecessors = buildPredecessorMap(definition);

        for (TaskInstance task : allTasks) {
            if (task.getStatus() != TaskStatus.PENDING) continue;
            Set<String> preds = predecessors.getOrDefault(task.getTaskKey(), Set.of());
            boolean allApproved = preds.stream().allMatch(p -> statusMap.get(p) == TaskStatus.APPROVED);
            if (allApproved) {
                log.info("[{}] Recompute: marking '{}' as READY", instance.getId(), task.getTaskKey());
                task.setStatus(TaskStatus.READY);
                taskInstanceRepo.save(task);
            }
        }
    }

    private void checkWorkflowCompletion(WorkflowInstance instance, List<TaskInstance> tasks) {
        boolean anyRejected = tasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.REJECTED);
        boolean anyActive = tasks.stream().anyMatch(t ->
            t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.READY || t.getStatus() == TaskStatus.IN_PROGRESS);

        if (!anyActive) {
            instance.setStatus(anyRejected ? WorkflowStatus.REJECTED : WorkflowStatus.COMPLETED);
            instance.setCompletedAt(java.time.Instant.now());
            instanceRepo.save(instance);
            log.info("[{}] Workflow {}", instance.getId(), instance.getStatus());
        }
    }

    private Map<String, Set<String>> buildPredecessorMap(WorkflowDefinition definition) {
        return definition.getDependencies().stream()
            .collect(Collectors.groupingBy(
                TaskDependency::getToTaskKey,
                Collectors.mapping(TaskDependency::getFromTaskKey, Collectors.toSet())
            ));
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.APPROVED || status == TaskStatus.REJECTED || status == TaskStatus.SKIPPED;
    }
}
