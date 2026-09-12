package com.workflow.domain;

public enum TaskStatus {
    PENDING,       // Created, waiting for predecessor tasks to complete
    READY,         // All predecessors approved — can be claimed
    IN_PROGRESS,   // Claimed by the scheduler, awaiting human approval
    APPROVED,      // Approved by assignee
    REJECTED,      // Rejected by assignee
    SKIPPED        // Upstream rejection caused this to be skipped
}
