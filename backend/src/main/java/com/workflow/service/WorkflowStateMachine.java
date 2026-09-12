package com.workflow.service;

import com.workflow.domain.*;
import com.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core workflow state machine.
 * Supports fully generic arbitrary graph shapes (DAGs).
 */
@Service
public class WorkflowStateMachine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStateMachine.class);

    private final TaskInstanceRepository taskInstanceRepo;
    private final WorkflowDefinitionRepository definitionRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

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
        TaskInstance completedTask = allTasks.stream()
            .filter(t -> t.getTaskKey().equals(completedTaskKey))
            .findFirst().orElseThrow();

        if (completedTask.getStatus() == TaskStatus.APPROVED) {
            for (TaskDependency dep : definition.getDependencies()) {
                if (dep.getFromTaskKey().equals(completedTaskKey)) {
                    if (dep.getConditionExpression() != null && !dep.getConditionExpression().trim().isEmpty()) {
                        boolean edgePassed = evaluateCondition(dep.getConditionExpression(), instance.getVariables());
                        if (!edgePassed) {
                            String successorKey = dep.getToTaskKey();
                            TaskInstance successor = allTasks.stream()
                                .filter(t -> t.getTaskKey().equals(successorKey))
                                .findFirst().orElse(null);
                            if (successor != null && !isTerminal(successor.getStatus())) {
                                log.info("[{}] Edge condition '{}' from '{}' evaluated to false. Skipping successor '{}'", 
                                    instance.getId(), dep.getConditionExpression(), completedTaskKey, successorKey);
                                successor.setStatus(TaskStatus.SKIPPED);
                                taskInstanceRepo.save(successor);
                            }
                        }
                    }
                }
            }
        }

        recomputeReadiness(instance);
    }

    @Transactional
    public void recomputeReadiness(WorkflowInstance instance) {
        WorkflowDefinition definition = definitionRepo.findById(instance.getDefinitionId()).orElseThrow();
        Map<String, Set<String>> predecessors = buildPredecessorMap(definition);

        boolean changed;
        do {
            changed = false;
            List<TaskInstance> allTasks = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
            Map<String, TaskStatus> statusMap = allTasks.stream()
                .collect(Collectors.toMap(TaskInstance::getTaskKey, TaskInstance::getStatus));

            for (TaskInstance task : allTasks) {
                if (isTerminal(task.getStatus()) || task.getStatus() == TaskStatus.READY || task.getStatus() == TaskStatus.IN_PROGRESS) {
                    continue; // Only check PENDING
                }
                
                Set<String> preds = predecessors.getOrDefault(task.getTaskKey(), Set.of());
                
                boolean allPredsTerminal = true;
                boolean allPredsSkipped = true;
                boolean anyPredRejected = false;
                boolean anyPredApproved = false;
                
                for (String p : preds) {
                    TaskStatus ps = statusMap.get(p);
                    if (ps == null) continue;
                    if (!isTerminal(ps)) {
                        allPredsTerminal = false;
                        break;
                    }
                    if (ps != TaskStatus.SKIPPED) allPredsSkipped = false;
                    if (ps == TaskStatus.REJECTED) anyPredRejected = true;
                    if (ps == TaskStatus.APPROVED) anyPredApproved = true;
                }

                if (!preds.isEmpty() && allPredsTerminal) {
                    if (anyPredRejected) {
                        task.setStatus(TaskStatus.SKIPPED);
                        taskInstanceRepo.save(task);
                        changed = true;
                    } else if (allPredsSkipped) {
                        task.setStatus(TaskStatus.SKIPPED);
                        taskInstanceRepo.save(task);
                        changed = true;
                    } else if (anyPredApproved) {
                        task.setStatus(TaskStatus.READY);
                        taskInstanceRepo.save(task);
                        changed = true;
                        autoExecuteIfApplicable(instance, task, definition);
                    }
                } else if (preds.isEmpty()) {
                    task.setStatus(TaskStatus.READY);
                    taskInstanceRepo.save(task);
                    changed = true;
                    autoExecuteIfApplicable(instance, task, definition);
                }
            }
        } while (changed);
        
        List<TaskInstance> refreshed = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
        checkWorkflowCompletion(instance, refreshed);
    }

    private void autoExecuteIfApplicable(WorkflowInstance instance, TaskInstance task, WorkflowDefinition definition) {
        TaskDefinition def = definition.getTasks().stream()
            .filter(t -> t.getTaskKey().equals(task.getTaskKey()))
            .findFirst().orElse(null);
            
        if (def != null && def.getNodeType() == NodeType.CONDITIONAL) {
            log.info("[{}] Auto-executing CONDITIONAL node '{}'", instance.getId(), task.getTaskKey());
            task.setStatus(TaskStatus.APPROVED);
            taskInstanceRepo.save(task);
            
            // Immediately evaluate conditional edges for this auto-approved node
            List<TaskInstance> allTasks = taskInstanceRepo.findByWorkflowInstanceId(instance.getId());
            for (TaskDependency dep : definition.getDependencies()) {
                if (dep.getFromTaskKey().equals(task.getTaskKey())) {
                    if (dep.getConditionExpression() != null && !dep.getConditionExpression().trim().isEmpty()) {
                        boolean edgePassed = evaluateCondition(dep.getConditionExpression(), instance.getVariables());
                        if (!edgePassed) {
                            String successorKey = dep.getToTaskKey();
                            TaskInstance successor = allTasks.stream()
                                .filter(t -> t.getTaskKey().equals(successorKey))
                                .findFirst().orElse(null);
                            if (successor != null && !isTerminal(successor.getStatus())) {
                                log.info("[{}] Edge condition '{}' from '{}' evaluated to false. Skipping successor '{}'", 
                                    instance.getId(), dep.getConditionExpression(), task.getTaskKey(), successorKey);
                                successor.setStatus(TaskStatus.SKIPPED);
                                taskInstanceRepo.save(successor);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean evaluateCondition(String expression, Map<String, String> variables) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            if (variables != null) {
                variables.forEach(context::setVariable);
            }
            Boolean result = expressionParser.parseExpression(expression).getValue(context, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            log.error("Failed to evaluate SpEL expression: {}", expression, e);
            return false;
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
