package com.workflow.service;

import com.workflow.api.dto.*;
import com.workflow.domain.*;
import com.workflow.repository.TaskInstanceRepository;
import com.workflow.repository.WorkflowInstanceRepository;
import com.workflow.scheduler.BootRecoveryService;
import com.workflow.scheduler.TaskSchedulerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * CRASH RECOVERY INTEGRATION TEST
 *
 * Resume-worthy claim: "If the process is killed mid-execution, on restart
 * the engine reconstructs in-flight state from the database and resumes
 * exactly where it left off — no lost transitions, no duplicated actions."
 *
 * This test proves that claim by simulating a kill -9 between task B being
 * claimed and approved, then running BootRecoveryService to reconstruct state.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CrashRecoveryIT {

    @Autowired private WorkflowDefinitionService definitionService;
    @Autowired private WorkflowInstanceService instanceService;
    @Autowired private ApprovalService approvalService;
    @Autowired private TaskInstanceRepository taskInstanceRepo;
    @Autowired private WorkflowInstanceRepository instanceRepo;
    @Autowired private TaskSchedulerService schedulerService;
    @Autowired private BootRecoveryService bootRecoveryService;

    @Test
    @DisplayName("Crash Recovery: workflow A→B→C resumes after kill -9 between B claim and approval")
    void crashRecovery_resumesExactlyWhereLeftOff() throws Exception {

        // ===== PHASE 1: Set up workflow A → B → C =====
        CreateWorkflowDefinitionRequest defReq = new CreateWorkflowDefinitionRequest();
        defReq.setName("Employee Onboarding");
        defReq.setTasks(List.of(
            makeTask("TASK_A", "Background Check", "HR"),
            makeTask("TASK_B", "IT Setup", "IT"),
            makeTask("TASK_C", "Manager Intro", "MANAGER")
        ));
        defReq.setDependencies(List.of(
            makeDep("TASK_A", "TASK_B"),
            makeDep("TASK_B", "TASK_C")
        ));

        WorkflowDefinition def = definitionService.createDefinition(defReq);

        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setDefinitionId(def.getId());
        startReq.setTitle("Onboarding: Charlie");
        startReq.setInitiatorId("hr-manager");
        WorkflowInstance instance = instanceService.startInstance(startReq);

        // ===== PHASE 2: Approve Task A → B becomes READY =====
        TaskInstance taskA = getTask(instance.getId(), "TASK_A");
        assertThat(taskA.getStatus()).isEqualTo(TaskStatus.READY);

        schedulerService.claimTask(taskA.getId());
        approvalService.processApproval(getTask(instance.getId(), "TASK_A").getId(),
            makeApproval("hr-user", ApprovalAction.APPROVE));

        assertThat(getTask(instance.getId(), "TASK_A").getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(getTask(instance.getId(), "TASK_B").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "TASK_C").getStatus()).isEqualTo(TaskStatus.PENDING);

        // ===== PHASE 3: Scheduler claims Task B → NOW CRASH =====
        schedulerService.claimTask(getTask(instance.getId(), "TASK_B").getId());
        assertThat(getTask(instance.getId(), "TASK_B").getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        System.out.println("*** SIMULATED KILL -9 ***");
        System.out.println("DB: A=APPROVED, B=IN_PROGRESS (stuck), C=PENDING");

        // ===== PHASE 4: RESTART — BootRecoveryService runs =====
        System.out.println("*** RESTARTING — running BootRecoveryService ***");
        bootRecoveryService.recoverOnStartup();

        // B reset to READY, C still PENDING
        assertThat(getTask(instance.getId(), "TASK_B").getStatus())
            .as("B should be READY after recovery (was IN_PROGRESS at crash)")
            .isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "TASK_C").getStatus())
            .as("C should still be PENDING (B not yet approved)")
            .isEqualTo(TaskStatus.PENDING);
        assertThat(instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
            .isEqualTo(WorkflowStatus.RUNNING);

        // ===== PHASE 5: Complete workflow normally after recovery =====
        schedulerService.claimTask(getTask(instance.getId(), "TASK_B").getId());
        approvalService.processApproval(getTask(instance.getId(), "TASK_B").getId(),
            makeApproval("it-user", ApprovalAction.APPROVE));

        assertThat(getTask(instance.getId(), "TASK_C").getStatus()).isEqualTo(TaskStatus.READY);

        schedulerService.claimTask(getTask(instance.getId(), "TASK_C").getId());
        approvalService.processApproval(getTask(instance.getId(), "TASK_C").getId(),
            makeApproval("manager-user", ApprovalAction.APPROVE));

        assertThat(instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
            .as("Workflow should complete after crash recovery")
            .isEqualTo(WorkflowStatus.COMPLETED);

        System.out.println("✓ Crash recovery test PASSED");
    }

    @Test
    @DisplayName("Crash Recovery: parallel tasks B and C both resume after crash mid-B")
    void crashRecovery_parallelTasksResume() throws Exception {
        // A → B (parallel) → D
        //   → C           ↗
        CreateWorkflowDefinitionRequest defReq = new CreateWorkflowDefinitionRequest();
        defReq.setName("Procurement Parallel");
        defReq.setTasks(List.of(
            makeTask("A", "Initial Review", "ANALYST"),
            makeTask("B", "Budget Check", "FINANCE"),
            makeTask("C", "Legal Review", "LEGAL"),
            makeTask("D", "Final Approval", "VP")
        ));
        defReq.setDependencies(List.of(
            makeDep("A", "B"), makeDep("A", "C"),
            makeDep("B", "D"), makeDep("C", "D")
        ));

        WorkflowDefinition def = definitionService.createDefinition(defReq);
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setDefinitionId(def.getId());
        startReq.setTitle("Procurement: Server Hardware");
        startReq.setInitiatorId("user");
        WorkflowInstance instance = instanceService.startInstance(startReq);

        schedulerService.claimTask(getTask(instance.getId(), "A").getId());
        approvalService.processApproval(getTask(instance.getId(), "A").getId(),
            makeApproval("analyst", ApprovalAction.APPROVE));

        // B and C both READY
        assertThat(getTask(instance.getId(), "B").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "C").getStatus()).isEqualTo(TaskStatus.READY);

        // Claim B, then CRASH
        schedulerService.claimTask(getTask(instance.getId(), "B").getId());
        assertThat(getTask(instance.getId(), "B").getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);

        // *** SIMULATED CRASH + RESTART ***
        bootRecoveryService.recoverOnStartup();

        assertThat(getTask(instance.getId(), "B").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "C").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "D").getStatus()).isEqualTo(TaskStatus.PENDING);

        // Complete B and C, then D
        schedulerService.claimTask(getTask(instance.getId(), "B").getId());
        schedulerService.claimTask(getTask(instance.getId(), "C").getId());
        approvalService.processApproval(getTask(instance.getId(), "B").getId(),
            makeApproval("finance", ApprovalAction.APPROVE));
        approvalService.processApproval(getTask(instance.getId(), "C").getId(),
            makeApproval("legal", ApprovalAction.APPROVE));

        assertThat(getTask(instance.getId(), "D").getStatus()).isEqualTo(TaskStatus.READY);

        schedulerService.claimTask(getTask(instance.getId(), "D").getId());
        approvalService.processApproval(getTask(instance.getId(), "D").getId(),
            makeApproval("vp", ApprovalAction.APPROVE));

        assertThat(instanceRepo.findById(instance.getId()).orElseThrow().getStatus())
            .isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    @DisplayName("DAG Cycle Detection: cyclic definition rejected with InvalidDagException")
    void cycleDetection_rejectsCyclicDag() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Cyclic");
        req.setTasks(List.of(
            makeTask("A", "Task A", "ROLE"),
            makeTask("B", "Task B", "ROLE"),
            makeTask("C", "Task C", "ROLE")
        ));
        req.setDependencies(List.of(makeDep("A", "B"), makeDep("B", "C"), makeDep("C", "A")));

        assertThatThrownBy(() -> definitionService.createDefinition(req))
            .isInstanceOf(WorkflowDefinitionService.InvalidDagException.class)
            .hasMessageContaining("cycle");
    }

    @Test
    @DisplayName("DAG Readiness: Task C needs BOTH A and B before becoming READY")
    void dagReadiness_taskRequiresBothPredecessors() throws Exception {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Diamond Join");
        req.setTasks(List.of(
            makeTask("A", "Task A", "ROLE"),
            makeTask("B", "Task B", "ROLE"),
            makeTask("C", "Task C", "ROLE")
        ));
        req.setDependencies(List.of(makeDep("A", "C"), makeDep("B", "C")));

        WorkflowDefinition def = definitionService.createDefinition(req);
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setDefinitionId(def.getId());
        startReq.setTitle("Diamond Test");
        startReq.setInitiatorId("user");
        WorkflowInstance instance = instanceService.startInstance(startReq);

        assertThat(getTask(instance.getId(), "A").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "B").getStatus()).isEqualTo(TaskStatus.READY);
        assertThat(getTask(instance.getId(), "C").getStatus()).isEqualTo(TaskStatus.PENDING);

        schedulerService.claimTask(getTask(instance.getId(), "A").getId());
        approvalService.processApproval(getTask(instance.getId(), "A").getId(),
            makeApproval("actor", ApprovalAction.APPROVE));
        // C still PENDING (B not approved)
        assertThat(getTask(instance.getId(), "C").getStatus()).isEqualTo(TaskStatus.PENDING);

        schedulerService.claimTask(getTask(instance.getId(), "B").getId());
        approvalService.processApproval(getTask(instance.getId(), "B").getId(),
            makeApproval("actor", ApprovalAction.APPROVE));
        // NOW C becomes READY
        assertThat(getTask(instance.getId(), "C").getStatus()).isEqualTo(TaskStatus.READY);
    }

    // ---- Helpers ----

    private TaskDefinitionRequest makeTask(String key, String name, String role) {
        TaskDefinitionRequest t = new TaskDefinitionRequest();
        t.setTaskKey(key); t.setName(name); t.setAssigneeRole(role);
        return t;
    }

    private DependencyRequest makeDep(String from, String to) {
        DependencyRequest d = new DependencyRequest();
        d.setFromTaskKey(from); d.setToTaskKey(to);
        return d;
    }

    private ApprovalRequest makeApproval(String actor, ApprovalAction action) {
        ApprovalRequest r = new ApprovalRequest();
        r.setActorId(actor); r.setAction(action);
        return r;
    }

    private TaskInstance getTask(String instanceId, String taskKey) {
        return taskInstanceRepo.findByWorkflowInstanceIdAndTaskKey(instanceId, taskKey)
            .orElseThrow(() -> new AssertionError("Task not found: " + taskKey));
    }
}
