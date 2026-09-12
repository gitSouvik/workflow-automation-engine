package com.workflow.api;

import com.workflow.api.dto.ApprovalRequest;
import com.workflow.domain.*;
import com.workflow.service.ApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TaskInstance>> getPendingApprovals(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(approvalService.getPendingApprovals(userId, role));
    }

    @PostMapping("/{taskInstanceId}")
    public ResponseEntity<ApprovalEvent> processApproval(
            @PathVariable String taskInstanceId,
            @Valid @RequestBody ApprovalRequest request) {
        return ResponseEntity.ok(approvalService.processApproval(taskInstanceId, request));
    }
}
