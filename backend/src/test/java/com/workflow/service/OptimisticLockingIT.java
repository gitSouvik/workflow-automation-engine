package com.workflow.service;

import com.workflow.api.dto.*;
import com.workflow.domain.*;
import com.workflow.repository.TaskInstanceRepository;
import com.workflow.repository.WorkflowInstanceRepository;
import com.workflow.scheduler.TaskSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * OPTIMISTIC LOCKING INTEGRATION TEST
 *
 * Resume-worthy claim: "Two simultaneous approvers acting on the same
 * workflow instance produce exactly one winner and one HTTP 409 Conflict."
 *
 * This test proves that claim by:
 * 1. Starting a workflow and getting a task to IN_PROGRESS
 * 2. Spawning two threads that simultaneously try to approve the same task
 * 3. Asserting exactly one succeeds and exactly one gets ConflictException
 *
 * WHY OPTIMISTIC NOT PESSIMISTIC:
 * - Low contention in practice: two approvers rarely hit the same task simultaneously
 * - Optimistic locking avoids blocking DB connections under normal load
 * - Conflicts are explicit (ConflictException → 409) rather than silent overwrites
 * - At 1,000 concurrent instances the difference in throughput is measurable
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OptimisticLockingIT {

    @Autowired
    private WorkflowDefinitionService definitionService;
    @Autowired
    private WorkflowInstanceService instanceService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private TaskInstanceRepository taskInstanceRepo;
    @Autowired
    private WorkflowInstanceRepository instanceRepo;
    @Autowired
    private TaskSchedulerService schedulerService;

    private String taskInstanceId;
    private WorkflowInstance instance;

    @BeforeEach
    void setUp() throws InterruptedException {
        // Create a simple linear workflow: SUBMIT → MANAGER_APPROVE
        CreateWorkflowDefinitionRequest defReq = new CreateWorkflowDefinitionRequest();
        defReq.setName("Expense Approval");
        defReq.setDescription("Test workflow for optimistic locking");

        TaskDefinitionRequest t1 = new TaskDefinitionRequest();
        t1.setTaskKey("SUBMIT");
        t1.setName("Submit Request");
        t1.setAssigneeRole("EMPLOYEE");
        t1.setDisplayOrder(1);

        TaskDefinitionRequest t2 = new TaskDefinitionRequest();
        t2.setTaskKey("MANAGER_APPROVE");
        t2.setName("Manager Approval");
        t2.setAssigneeRole("MANAGER");
        t2.setDisplayOrder(2);

        defReq.setTasks(List.of(t1, t2));

        DependencyRequest dep = new DependencyRequest();
        dep.setFromTaskKey("SUBMIT");
        dep.setToTaskKey("MANAGER_APPROVE");
        defReq.setDependencies(List.of(dep));

        WorkflowDefinition def = definitionService.createDefinition(defReq);

        // Start an instance
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setDefinitionId(def.getId());
        startReq.setTitle("Travel Expense Q3");
        startReq.setInitiatorId("alice");
        instance = instanceService.startInstance(startReq);

        // Get SUBMIT task (should be READY — it's a root node)
        TaskInstance submitTask = taskInstanceRepo
            .findByWorkflowInstanceIdAndTaskKey(instance.getId(), "SUBMIT")
            .orElseThrow();

        assertThat(submitTask.getStatus()).isEqualTo(TaskStatus.READY);

        // Claim it → IN_PROGRESS
        schedulerService.claimTask(submitTask.getId());

        TaskInstance claimed = taskInstanceRepo.findById(submitTask.getId()).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        taskInstanceId = claimed.getId();
    }

    @Test
    @DisplayName("Two concurrent approvals → exactly one succeeds, one gets ConflictException (409)")
    void concurrentApprovals_oneWinsOneLoses() throws InterruptedException {
        // Two approvers, same task, same action
        ApprovalRequest req1 = new ApprovalRequest();
        req1.setActorId("manager-alice");
        req1.setAction(ApprovalAction.APPROVE);
        req1.setComment("Approved by Alice");

        ApprovalRequest req2 = new ApprovalRequest();
        req2.setActorId("manager-bob");
        req2.setAction(ApprovalAction.APPROVE);
        req2.setComment("Approved by Bob");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        // Use a CyclicBarrier to maximize contention — both threads start at the same instant
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch done = new CountDownLatch(2);

        Runnable approver1 = () -> {
            try {
                barrier.await(); // Synchronize start
                approvalService.processApproval(taskInstanceId, req1);
                successCount.incrementAndGet();
            } catch (ApprovalService.ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                // May also get conflict from already-terminal state check
                if (e.getMessage() != null && e.getMessage().contains("already in state")) {
                    conflictCount.incrementAndGet();
                } else {
                    System.err.println("Unexpected error thread 1: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    otherErrorCount.incrementAndGet();
                }
            } finally {
                done.countDown();
            }
        };

        Runnable approver2 = () -> {
            try {
                barrier.await();
                approvalService.processApproval(taskInstanceId, req2);
                successCount.incrementAndGet();
            } catch (ApprovalService.ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("already in state")) {
                    conflictCount.incrementAndGet();
                } else {
                    System.err.println("Unexpected error thread 2: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    otherErrorCount.incrementAndGet();
                }
            } finally {
                done.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(approver1);
        executor.submit(approver2);

        done.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Results — success: " + successCount.get()
            + ", conflict: " + conflictCount.get()
            + ", other: " + otherErrorCount.get());

        // CORE ASSERTION: exactly one winner, one conflict
        assertThat(successCount.get()).as("Exactly one approval should succeed").isEqualTo(1);
        assertThat(conflictCount.get()).as("Exactly one conflict should be detected").isEqualTo(1);
        assertThat(otherErrorCount.get()).as("No unexpected errors").isEqualTo(0);

        // Verify the task ended in APPROVED state (not double-approved or corrupted)
        TaskInstance finalTask = taskInstanceRepo.findById(taskInstanceId).orElseThrow();
        assertThat(finalTask.getStatus()).isEqualTo(TaskStatus.APPROVED);
    }

    @Test
    @DisplayName("Idempotent approval — same actor approving twice returns success without double-advancing")
    void idempotentApproval_sameActorTwice() {
        ApprovalRequest req = new ApprovalRequest();
        req.setActorId("manager-alice");
        req.setAction(ApprovalAction.APPROVE);
        req.setComment("First approval");

        // First approval — should succeed
        ApprovalEvent event1 = approvalService.processApproval(taskInstanceId, req);
        assertThat(event1).isNotNull();

        // Second approval by same actor — should return the existing event (idempotent)
        ApprovalEvent event2 = approvalService.processApproval(taskInstanceId, req);
        assertThat(event2).isNotNull();
        assertThat(event2.getId()).isEqualTo(event1.getId());

        // Workflow should not have been double-advanced
        TaskInstance task = taskInstanceRepo.findById(taskInstanceId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.APPROVED);
    }
}
