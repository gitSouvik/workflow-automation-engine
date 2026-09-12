package com.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approval_events")
public class ApprovalEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String taskInstanceId;

    @Column(nullable = false)
    private String workflowInstanceId;

    @Column(nullable = false)
    private String taskKey;

    @Column(nullable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Column(nullable = false, updatable = false)
    private Instant timestamp = Instant.now();

    @Column(length = 2000)
    private String comment;

    public ApprovalEvent() {}

    public ApprovalEvent(String taskInstanceId, String workflowInstanceId, String taskKey,
                         String actorId, ApprovalAction action, String comment) {
        this.taskInstanceId = taskInstanceId;
        this.workflowInstanceId = workflowInstanceId;
        this.taskKey = taskKey;
        this.actorId = actorId;
        this.action = action;
        this.comment = comment;
    }

    public String getId() { return id; }
    public String getTaskInstanceId() { return taskInstanceId; }
    public String getWorkflowInstanceId() { return workflowInstanceId; }
    public String getTaskKey() { return taskKey; }
    public String getActorId() { return actorId; }
    public ApprovalAction getAction() { return action; }
    public Instant getTimestamp() { return timestamp; }
    public String getComment() { return comment; }
}
