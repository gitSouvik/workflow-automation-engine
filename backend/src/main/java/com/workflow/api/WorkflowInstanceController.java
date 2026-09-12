package com.workflow.api;

import com.workflow.api.dto.StartWorkflowRequest;
import com.workflow.domain.*;
import com.workflow.service.WorkflowInstanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class WorkflowInstanceController {

    private final WorkflowInstanceService instanceService;

    public WorkflowInstanceController(WorkflowInstanceService instanceService) {
        this.instanceService = instanceService;
    }

    @PostMapping("/api/instances")
    public ResponseEntity<WorkflowInstance> startInstance(@Valid @RequestBody StartWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(instanceService.startInstance(request));
    }

    @GetMapping("/api/instances")
    public ResponseEntity<List<WorkflowInstance>> listInstances() {
        return ResponseEntity.ok(instanceService.getAllInstances());
    }

    @GetMapping("/api/instances/{id}")
    public ResponseEntity<WorkflowInstance> getInstance(@PathVariable String id) {
        return ResponseEntity.ok(instanceService.getInstance(id));
    }

    @GetMapping("/api/instances/{id}/tasks")
    public ResponseEntity<List<TaskInstance>> getTaskInstances(@PathVariable String id) {
        return ResponseEntity.ok(instanceService.getTaskInstances(id));
    }

    @GetMapping("/api/instances/{id}/history")
    public ResponseEntity<List<ApprovalEvent>> getHistory(@PathVariable String id) {
        return ResponseEntity.ok(instanceService.getHistory(id));
    }
}
