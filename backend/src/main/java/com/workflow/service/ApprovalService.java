package com.workflow.service;

import com.workflow.api.dto.ApprovalRequest;
import com.workflow.domain.*;
import com.workflow.repository.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Handles approve/reject actions with optimistic concurrency and idempotency.
 *
 * OPTIMISTIC CONCURRENCY — HOW IT WORKS:
 * WorkflowInstance has a @Version field (Long). JPA increments it on every save.
 *
 * When two approvers act on the same workflow simultaneously:
 *   Thread 1: reads instance@version=5, saves → version becomes 6 ✓ 
 *   Thread 2: reads instance@version=5, tries to save → DB has version=6, expects 5 → conflict
 *   → ObjectOptimisticLockingFailureException → ConflictException → HTTP 409
 *
 * We perform a manual version bump (increment and save) at the START of each
 * approval transaction. This creates the contention point: two concurrent
 * transactions that both read version=N will both try to write version=N+1,
 * but only one can succeed. The key is that we saveAndFlush to make the
 * conflict visible within the current transaction rather than at commit time.
 *
 * IDEMPOTENCY:
 * Same actor + same action on a terminal task returns the existing ApprovalEvent.
 * Different actor on terminal task returns ConflictException.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final TaskInstanceRepository taskInstanceRepo;
    private final WorkflowInstanceRepository instanceRepo;
    private final ApprovalEventRepository approvalEventRepo;
    private final WorkflowStateMachine stateMachine;
    private final EntityManager em;

    public ApprovalService(TaskInstanceRepository taskInstanceRepo,
                            WorkflowInstanceRepository instanceRepo,
                            ApprovalEventRepository approvalEventRepo,
                            WorkflowStateMachine stateMachine,
                            EntityManager em) {
        this.taskInstanceRepo = taskInstanceRepo;
        this.instanceRepo = instanceRepo;
        this.approvalEventRepo = approvalEventRepo;
        this.stateMachine = stateMachine;
        this.em = em;
    }

    @Transactional
    public ApprovalEvent processApproval(String taskInstanceId, ApprovalRequest request) {
        TaskInstance task = taskInstanceRepo.findById(taskInstanceId)
            .orElseThrow(() -> new WorkflowDefinitionService.ResourceNotFoundException("TaskInstance", taskInstanceId));

        final String instanceId = task.getWorkflowInstance().getId();
        WorkflowInstance instance = instanceRepo.findById(instanceId)
            .orElseThrow(() -> new WorkflowDefinitionService.ResourceNotFoundException("WorkflowInstance", instanceId));

        // OPTIMISTIC CONCURRENCY CONTROL — Manual version-check-and-increment:
        //
        // We use a direct SQL UPDATE with a version predicate:
        //   UPDATE workflow_instances SET version = version + 1
        //   WHERE id = ? AND version = ?  (current version we read)
        //
        // If 0 rows are updated, another transaction already incremented the version.
        // This is the hand-rolled equivalent of JPA's @Version, and works correctly
        // even when two transactions execute in the same JVM thread pool.
        //
        // WHY THIS APPROACH:
        // - JPA's @Version conflict fires at *commit time* (end of transaction)
        // - For tests (and some DB configs), both concurrent transactions may flush
        //   before either commits, allowing both to see consistent reads
        // - This explicit UPDATE with version predicate fires the conflict *immediately*
        //   when the second thread runs the UPDATE, finding the version already changed
        long expectedVersion = instance.getVersion();
        int updatedRows = instanceRepo.incrementVersionIfMatch(instanceId, expectedVersion);

        if (updatedRows == 0) {
            log.warn("[{}] Optimistic lock conflict: version {} already updated by concurrent transaction",
                instanceId, expectedVersion);
            throw new ConflictException(
                "Concurrent modification detected for workflow [" + instanceId + "]. Another approver acted simultaneously. Please retry.");
        }

        // Refresh instance to get updated version
        em.refresh(instance);

        // IDEMPOTENCY: check if task is already terminal
        if (isTerminal(task.getStatus())) {
            List<ApprovalEvent> existing = approvalEventRepo.findByTaskInstanceId(taskInstanceId);
            for (ApprovalEvent event : existing) {
                if (event.getActorId().equals(request.getActorId()) && event.getAction() == request.getAction()) {
                    log.info("Idempotent approval for task [{}] by [{}]", taskInstanceId, request.getActorId());
                    return event;
                }
            }
            throw new ConflictException("Task [" + taskInstanceId + "] already completed with status: "
                + task.getStatus() + ". Another actor has already acted on this task.");
        }

        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new TaskNotActionableException(
                "Task [" + taskInstanceId + "] is in state " + task.getStatus() + " — must be IN_PROGRESS");
        }

        try {
            TaskStatus newStatus = request.getAction() == ApprovalAction.APPROVE ? TaskStatus.APPROVED : TaskStatus.REJECTED;
            Instant now = Instant.now();

            task.setStatus(newStatus);
            task.setCompletedAt(now);
            task.setComment(request.getComment());
            if (task.getStartedAt() != null) {
                task.setStepDurationMs(now.toEpochMilli() - task.getStartedAt().toEpochMilli());
            }
            taskInstanceRepo.save(task);

            ApprovalEvent event = new ApprovalEvent(
                task.getId(), instance.getId(), task.getTaskKey(),
                request.getActorId(), request.getAction(), request.getComment());
            event = approvalEventRepo.save(event);

            stateMachine.advanceWorkflow(instance, task.getTaskKey());

            log.info("[{}] Task '{}' {} by '{}'", instance.getId(), task.getTaskKey(), newStatus, request.getActorId());
            return event;

        } catch (OptimisticLockingFailureException e) {
            log.warn("[{}] Optimistic lock conflict on task '{}'", instance.getId(), task.getTaskKey());
            throw new ConflictException("Concurrent modification detected for workflow [" + instance.getId() + "]. Please retry.");
        }
    }

    @Transactional(readOnly = true)
    public List<TaskInstance> getPendingApprovals(String userId, String role) {
        if (role != null && !role.isBlank()) return taskInstanceRepo.findPendingByRole(role);
        return taskInstanceRepo.findPendingByUser(userId);
    }

    private boolean isTerminal(TaskStatus status) {
        return status == TaskStatus.APPROVED || status == TaskStatus.REJECTED || status == TaskStatus.SKIPPED;
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    public static class TaskNotActionableException extends RuntimeException {
        public TaskNotActionableException(String message) { super(message); }
    }
}
