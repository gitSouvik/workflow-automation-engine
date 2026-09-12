package com.workflow.domain;

import jakarta.persistence.*;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "task_dependencies",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_definition_id", "from_task_key", "to_task_key"}))
public class TaskDependency {

    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "from_task_key", nullable = false)
    private String fromTaskKey;

    @Column(name = "to_task_key", nullable = false)
    private String toTaskKey;

    public TaskDependency() {}

    public TaskDependency(WorkflowDefinition def, String fromTaskKey, String toTaskKey) {
        this.workflowDefinition = def;
        this.fromTaskKey = fromTaskKey;
        this.toTaskKey = toTaskKey;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public void setWorkflowDefinition(WorkflowDefinition def) { this.workflowDefinition = def; }
    public String getFromTaskKey() { return fromTaskKey; }
    public void setFromTaskKey(String fromTaskKey) { this.fromTaskKey = fromTaskKey; }
    public String getToTaskKey() { return toTaskKey; }
    public void setToTaskKey(String toTaskKey) { this.toTaskKey = toTaskKey; }
}
