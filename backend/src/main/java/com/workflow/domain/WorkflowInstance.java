package com.workflow.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A running execution of a WorkflowDefinition.
 *
 * OPTIMISTIC CONCURRENCY: The `version` field is managed by JPA's @Version.
 * Every write to this entity increments version. Concurrent writes will cause
 * ObjectOptimisticLockingFailureException → translated to HTTP 409 Conflict.
 */
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String definitionId;

    @Column(nullable = false)
    private String definitionName;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String initiatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    /**
     * OPTIMISTIC CONCURRENCY CONTROL.
     * JPA increments this automatically on every flush.
     * Concurrent updates produce ObjectOptimisticLockingFailureException.
     */
    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaskInstance> taskInstances = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "workflow_variables", joinColumns = @JoinColumn(name = "workflow_instance_id"))
    @MapKeyColumn(name = "var_name")
    @Column(name = "var_value", length = 1000)
    private java.util.Map<String, String> variables = new java.util.HashMap<>();

    public WorkflowInstance() {}

    public WorkflowInstance(String definitionId, String definitionName, String title, String initiatorId) {
        this.definitionId = definitionId;
        this.definitionName = definitionName;
        this.title = title;
        this.initiatorId = initiatorId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getDefinitionName() { return definitionName; }
    public void setDefinitionName(String definitionName) { this.definitionName = definitionName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInitiatorId() { return initiatorId; }
    public void setInitiatorId(String initiatorId) { this.initiatorId = initiatorId; }
    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<TaskInstance> getTaskInstances() { return taskInstances; }
    public void setTaskInstances(List<TaskInstance> taskInstances) { this.taskInstances = taskInstances; }
    public java.util.Map<String, String> getVariables() { return variables; }
    public void setVariables(java.util.Map<String, String> variables) { this.variables = variables; }
}
