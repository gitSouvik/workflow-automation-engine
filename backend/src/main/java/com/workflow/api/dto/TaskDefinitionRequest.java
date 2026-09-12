package com.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public class TaskDefinitionRequest {
    @NotBlank
    private String taskKey;
    @NotBlank
    private String name;
    private String description;
    @NotBlank
    private String assigneeRole;
    private int displayOrder;

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
}
