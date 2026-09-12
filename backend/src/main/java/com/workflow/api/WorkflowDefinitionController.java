package com.workflow.api;

import com.workflow.api.dto.CreateWorkflowDefinitionRequest;
import com.workflow.domain.WorkflowDefinition;
import com.workflow.service.WorkflowDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/definitions")
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    public WorkflowDefinitionController(WorkflowDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @PostMapping
    public ResponseEntity<WorkflowDefinition> createDefinition(@Valid @RequestBody CreateWorkflowDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.createDefinition(request));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowDefinition>> listDefinitions() {
        return ResponseEntity.ok(definitionService.listDefinitions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> getDefinition(@PathVariable String id) {
        return ResponseEntity.ok(definitionService.getDefinition(id));
    }
}
