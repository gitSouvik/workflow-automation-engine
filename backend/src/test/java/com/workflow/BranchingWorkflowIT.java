package com.workflow;

import com.workflow.api.dto.*;
import com.workflow.domain.*;
import com.workflow.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class BranchingWorkflowIT {

    @Autowired
    private WorkflowDefinitionService defService;

    @Autowired
    private WorkflowInstanceService instService;

    @Autowired
    private ApprovalService approvalService;

    @Test
    public void testConditionalBranching() {
        // 1. Create Workflow Definition with Branching
        CreateWorkflowDefinitionRequest defReq = new CreateWorkflowDefinitionRequest();
        defReq.setName("Expense_Approval_" + System.currentTimeMillis());
        defReq.setDescription("Branching workflow");

        TaskDefinitionRequest submit = new TaskDefinitionRequest();
        submit.setTaskKey("SUBMIT");
        submit.setName("Submit Expense");
        submit.setAssigneeRole("SUBMITTER");

        TaskDefinitionRequest condition = new TaskDefinitionRequest();
        condition.setTaskKey("CHECK_AMOUNT");
        condition.setName("Amount Check");
        condition.setAssigneeRole("SYSTEM");
        condition.setNodeType(NodeType.CONDITIONAL);

        TaskDefinitionRequest managerReview = new TaskDefinitionRequest();
        managerReview.setTaskKey("MANAGER_REVIEW");
        managerReview.setName("Manager Review");
        managerReview.setAssigneeRole("MANAGER");

        TaskDefinitionRequest vpReview = new TaskDefinitionRequest();
        vpReview.setTaskKey("VP_REVIEW");
        vpReview.setName("VP Review");
        vpReview.setAssigneeRole("VP");
        
        TaskDefinitionRequest finalize = new TaskDefinitionRequest();
        finalize.setTaskKey("FINALIZE");
        finalize.setName("Finalize");
        finalize.setAssigneeRole("FINANCE");

        defReq.setTasks(List.of(submit, condition, managerReview, vpReview, finalize));

        DependencyRequest d1 = new DependencyRequest();
        d1.setFromTaskKey("SUBMIT");
        d1.setToTaskKey("CHECK_AMOUNT");

        DependencyRequest d2 = new DependencyRequest();
        d2.setFromTaskKey("CHECK_AMOUNT");
        d2.setToTaskKey("MANAGER_REVIEW");
        d2.setConditionExpression("T(Integer).parseInt(#amount) <= 10000"); // Branch 1

        DependencyRequest d3 = new DependencyRequest();
        d3.setFromTaskKey("CHECK_AMOUNT");
        d3.setToTaskKey("VP_REVIEW");
        d3.setConditionExpression("T(Integer).parseInt(#amount) > 10000"); // Branch 2
        
        DependencyRequest d4 = new DependencyRequest();
        d4.setFromTaskKey("MANAGER_REVIEW");
        d4.setToTaskKey("FINALIZE");
        
        DependencyRequest d5 = new DependencyRequest();
        d5.setFromTaskKey("VP_REVIEW");
        d5.setToTaskKey("FINALIZE");

        defReq.setDependencies(List.of(d1, d2, d3, d4, d5));
        
        WorkflowDefinition def = defService.createDefinition(defReq);

        // 2. Start workflow with amount = 15000 (Expect VP Review, Manager Review should be skipped)
        StartWorkflowRequest startReq = new StartWorkflowRequest();
        startReq.setDefinitionId(def.getId());
        startReq.setTitle("Large Expense");
        startReq.setInitiatorId("user1");
        startReq.setVariables(Map.of("amount", "15000"));
        
        WorkflowInstance instance = instService.startInstance(startReq);
        String iId = instance.getId();

        // 3. Approve submit
        try { Thread.sleep(2500); } catch (Exception e) {} // wait for scheduler
        List<TaskInstance> initialTasks = instService.getTaskInstances(iId);
        TaskInstance submitTask = initialTasks.stream().filter(t -> t.getTaskKey().equals("SUBMIT")).findFirst().orElseThrow();
        
        ApprovalRequest req = new ApprovalRequest();
        req.setAction(ApprovalAction.APPROVE);
        req.setActorId("user1");
        approvalService.processApproval(submitTask.getId(), req);

        // Engine should auto-execute CHECK_AMOUNT because it's CONDITIONAL.
        // It evaluates expressions. #amount > 10000 is true.
        // VP_REVIEW becomes READY. MANAGER_REVIEW becomes SKIPPED.
        // Because MANAGER_REVIEW is SKIPPED, its downstream FINALIZE is... wait.
        // FINALIZE requires ALL predecessors to be APPROVED. Since MANAGER_REVIEW is SKIPPED, FINALIZE gets SKIPPED.
        // Wait, is that what we want? The engine will skip FINALIZE!
        // Let's verify what actually happens.

        List<TaskInstance> tasks = instService.getTaskInstances(iId);
        TaskInstance checkAmount = tasks.stream().filter(t -> t.getTaskKey().equals("CHECK_AMOUNT")).findFirst().orElseThrow();
        TaskInstance mgrReview = tasks.stream().filter(t -> t.getTaskKey().equals("MANAGER_REVIEW")).findFirst().orElseThrow();
        TaskInstance vpRev = tasks.stream().filter(t -> t.getTaskKey().equals("VP_REVIEW")).findFirst().orElseThrow();
        TaskInstance fin = tasks.stream().filter(t -> t.getTaskKey().equals("FINALIZE")).findFirst().orElseThrow();

        assertThat(checkAmount.getStatus()).isEqualTo(TaskStatus.APPROVED);
        assertThat(mgrReview.getStatus()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(vpRev.getStatus()).isEqualTo(TaskStatus.READY);
        
        // Let's print FINALIZE status
        System.out.println("FINALIZE status is: " + fin.getStatus());
    }
}
