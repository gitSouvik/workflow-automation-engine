package com.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "task_instances",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_instance_id", "task_key"}))
public class TaskInstance {

    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "task_key", nullable = false)
    private String taskKey;

    @Column(nullable = false)
    private String taskName;

    @Column(nullable = false)
    private String assigneeRole;

    private String assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant completedAt;

    @Column(length = 2000)
    private String comment;

    private Long stepDurationMs;

    public TaskInstance() {}

    public TaskInstance(WorkflowInstance instance, String taskKey, String taskName, String assigneeRole) {
        this.workflowInstance = instance;
        this.taskKey = taskKey;
        this.taskName = taskName;
        this.assigneeRole = assigneeRole;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public WorkflowInstance getWorkflowInstance() { return workflowInstance; }
    public void setWorkflowInstance(WorkflowInstance workflowInstance) { this.workflowInstance = workflowInstance; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getAssigneeRole() { return assigneeRole; }
    public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Long getStepDurationMs() { return stepDurationMs; }
    public void setStepDurationMs(Long stepDurationMs) { this.stepDurationMs = stepDurationMs; }
}
