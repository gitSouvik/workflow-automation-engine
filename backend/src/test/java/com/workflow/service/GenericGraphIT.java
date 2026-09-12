package com.workflow.service;

import com.workflow.api.dto.ApprovalRequest;
import com.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.workflow.api.dto.DependencyRequest;
import com.workflow.api.dto.StartWorkflowRequest;
import com.workflow.api.dto.TaskDefinitionRequest;
import com.workflow.domain.ApprovalAction;
import com.workflow.domain.NodeType;
import com.workflow.domain.TaskInstance;
import com.workflow.domain.TaskStatus;
import com.workflow.domain.WorkflowDefinition;
import com.workflow.domain.WorkflowInstance;
import com.workflow.repository.TaskInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class GenericGraphIT {

    @Autowired
    private WorkflowDefinitionService definitionService;

    @Autowired
    private WorkflowInstanceService instanceService;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private TaskInstanceRepository taskInstanceRepo;

    @Test
    public void testStraightChain() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Chain-" + UUID.randomUUID());
        req.setTasks(List.of(
            createTask("A"),
            createTask("B"),
            createTask("C")
        ));
        req.setDependencies(List.of(
            createDep("A", "B"),
            createDep("B", "C")
        ));
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        WorkflowInstance inst = startInstance(def.getId(), Map.of());
        
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "A"));
        
        completeTask(inst.getId(), "A");
        assertEquals(TaskStatus.APPROVED, getStatus(inst.getId(), "A"));
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "B"));
        
        completeTask(inst.getId(), "B");
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "C"));
    }

    @Test
    public void testFiveWayJoin() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("5-Way-Join-" + UUID.randomUUID());
        
        List<TaskDefinitionRequest> tasks = new ArrayList<>();
        List<DependencyRequest> deps = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            tasks.add(createTask("P" + i));
            deps.add(createDep("P" + i, "JOIN"));
        }
        tasks.add(createTask("JOIN"));
        req.setTasks(tasks);
        req.setDependencies(deps);
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        WorkflowInstance inst = startInstance(def.getId(), Map.of());
        
        for (int i = 1; i <= 4; i++) {
            completeTask(inst.getId(), "P" + i);
            assertEquals(TaskStatus.PENDING, getStatus(inst.getId(), "JOIN")); 
        }
        
        completeTask(inst.getId(), "P5");
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "JOIN"));
    }

    @Test
    public void testEightWayFanOut() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("8-Way-FanOut-" + UUID.randomUUID());
        
        List<TaskDefinitionRequest> tasks = new ArrayList<>();
        List<DependencyRequest> deps = new ArrayList<>();
        
        tasks.add(createTask("ROOT"));
        for (int i = 1; i <= 8; i++) {
            tasks.add(createTask("C" + i));
            deps.add(createDep("ROOT", "C" + i));
        }
        req.setTasks(tasks);
        req.setDependencies(deps);
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        WorkflowInstance inst = startInstance(def.getId(), Map.of());
        
        completeTask(inst.getId(), "ROOT");
        
        for (int i = 1; i <= 8; i++) {
            assertEquals(TaskStatus.READY, getStatus(inst.getId(), "C" + i));
        }
    }

    @Test
    public void testAsymmetricMeshWithSkip() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Asymmetric-Mesh-" + UUID.randomUUID());
        
        List<TaskDefinitionRequest> tasks = new ArrayList<>();
        TaskDefinitionRequest root = createTask("ROOT");
        root.setNodeType(NodeType.CONDITIONAL);
        tasks.add(root);
        
        tasks.add(createTask("B1_1"));
        tasks.add(createTask("B1_2"));
        
        for (int i = 1; i <= 6; i++) {
            tasks.add(createTask("B2_" + i));
        }
        tasks.add(createTask("JOIN"));
        
        List<DependencyRequest> deps = new ArrayList<>();
        deps.add(createDepCond("ROOT", "B1_1", "T(Boolean).parseBoolean(#b1)"));
        deps.add(createDep("B1_1", "B1_2"));
        deps.add(createDep("B1_2", "JOIN"));
        
        deps.add(createDepCond("ROOT", "B2_1", "T(Boolean).parseBoolean(#b2)"));
        for (int i = 1; i < 6; i++) {
            deps.add(createDep("B2_" + i, "B2_" + (i + 1)));
        }
        deps.add(createDep("B2_6", "JOIN"));
        
        req.setTasks(tasks);
        req.setDependencies(deps);
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        // Only trigger B2
        WorkflowInstance inst = startInstance(def.getId(), Map.of("b1", "false", "b2", "true"));
        
        // Root is CONDITIONAL, so it auto-executes, skipping B1_1, and triggering B2_1
        assertEquals(TaskStatus.SKIPPED, getStatus(inst.getId(), "B1_1"));
        assertEquals(TaskStatus.SKIPPED, getStatus(inst.getId(), "B1_2"));
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "B2_1"));
        
        for (int i = 1; i <= 6; i++) {
            completeTask(inst.getId(), "B2_" + i);
        }
        
        // B1 branch is SKIPPED, B2 branch is APPROVED. The JOIN should be READY.
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "JOIN"));
    }

    @Test
    public void testCrossPollinatedDag() {
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Cross-Pollinated-" + UUID.randomUUID());
        
        req.setTasks(List.of(
            createTask("S1"), createTask("S2"),
            createTask("A"), createTask("B"), createTask("C"),
            createTask("D")
        ));
        req.setDependencies(List.of(
            createDep("S1", "A"),
            createDep("S1", "B"),
            createDep("S2", "B"),
            createDep("S2", "C"),
            createDep("A", "D"),
            createDep("B", "D"),
            createDep("C", "D")
        ));
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        WorkflowInstance inst = startInstance(def.getId(), Map.of());
        
        completeTask(inst.getId(), "S1");
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "A"));
        assertEquals(TaskStatus.PENDING, getStatus(inst.getId(), "B")); // Waiting for S2
        
        completeTask(inst.getId(), "S2");
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "B"));
        assertEquals(TaskStatus.READY, getStatus(inst.getId(), "C"));
    }

    @Test
    public void testProgrammaticRuntimeGeneration() {
        int dynamicN = 50; 
        CreateWorkflowDefinitionRequest req = new CreateWorkflowDefinitionRequest();
        req.setName("Dynamic-" + dynamicN + "-" + UUID.randomUUID());
        
        List<TaskDefinitionRequest> tasks = new ArrayList<>();
        List<DependencyRequest> deps = new ArrayList<>();
        for (int i = 0; i < dynamicN; i++) {
            tasks.add(createTask("NODE_" + i));
            if (i > 0) {
                deps.add(createDep("NODE_" + (i - 1), "NODE_" + i));
            }
        }
        req.setTasks(tasks);
        req.setDependencies(deps);
        
        WorkflowDefinition def = definitionService.createDefinition(req);
        WorkflowInstance inst = startInstance(def.getId(), Map.of());
        
        for (int i = 0; i < dynamicN; i++) {
            assertEquals(TaskStatus.READY, getStatus(inst.getId(), "NODE_" + i));
            completeTask(inst.getId(), "NODE_" + i);
        }
    }

    private TaskDefinitionRequest createTask(String key) {
        TaskDefinitionRequest t = new TaskDefinitionRequest();
        t.setTaskKey(key);
        t.setName(key);
        t.setAssigneeRole("ROLE");
        return t;
    }

    private DependencyRequest createDep(String from, String to) {
        DependencyRequest d = new DependencyRequest();
        d.setFromTaskKey(from);
        d.setToTaskKey(to);
        return d;
    }

    private DependencyRequest createDepCond(String from, String to, String cond) {
        DependencyRequest d = createDep(from, to);
        d.setConditionExpression(cond);
        return d;
    }

    private WorkflowInstance startInstance(String defId, Map<String, String> vars) {
        StartWorkflowRequest req = new StartWorkflowRequest();
        req.setDefinitionId(defId);
        req.setTitle("Test Title");
        req.setInitiatorId("user");
        req.setVariables(vars);
        return instanceService.startInstance(req);
    }

    private void completeTask(String instanceId, String taskKey) {
        TaskInstance task = taskInstanceRepo.findByWorkflowInstanceId(instanceId).stream()
                .filter(t -> t.getTaskKey().equals(taskKey)).findFirst().get();
        if (task.getStatus() == TaskStatus.READY) {
            task.setStatus(TaskStatus.IN_PROGRESS);
            taskInstanceRepo.save(task);
        }
        ApprovalRequest req = new ApprovalRequest();
        req.setAction(ApprovalAction.APPROVE);
        req.setActorId("user");
        approvalService.processApproval(task.getId(), req);
    }

    private TaskStatus getStatus(String instanceId, String key) {
        return taskInstanceRepo.findByWorkflowInstanceId(instanceId).stream()
                .filter(t -> t.getTaskKey().equals(key)).findFirst().get().getStatus();
    }
}
