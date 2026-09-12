package com.workflow.domain;

import jakarta.persistence.*;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "task_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_definition_id", "task_key"}))
public class TaskDefinition {

    @Id
    @Column(nullable = false, updatable = false)
    private String id = UUID.randomUUID().toString();

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "task_key", nullable = false)
    private String taskKey;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String assigneeRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType = NodeType.APPROVAL;

    private int displayOrder;

    public TaskDefinition() {}

    public TaskDefinition(WorkflowDefinition def, String taskKey, String name, String assigneeRole) {
        this.workflowDefinition = def;
        this.taskKey = taskKey;
        this.name = name;
        this.assigneeRole = assigneeRole;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public WorkflowDefinition getWorkflowDefinition() { return workflowDefinition; }
    public void setWorkflowDefinition(WorkflowDefinition workflowDefinition) { this.workflowDefinition = workflowDefinition; }
    public String getTaskKey() { return taskKey; }
    public void setTaskKey(String taskKey) { this.taskKey = taskKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAssigneeRole() { return assigneeRole; }
    public void setAssigneeRole(String assigneeRole) { this.assigneeRole = assigneeRole; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }
}
