package com.workflow.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

public class CreateWorkflowDefinitionRequest {
    @NotBlank
    private String name;
    private String description;

    @NotEmpty
    @Valid
    private List<TaskDefinitionRequest> tasks = new ArrayList<>();
    private List<DependencyRequest> dependencies = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TaskDefinitionRequest> getTasks() { return tasks; }
    public void setTasks(List<TaskDefinitionRequest> tasks) { this.tasks = tasks; }
    public List<DependencyRequest> getDependencies() { return dependencies; }
    public void setDependencies(List<DependencyRequest> dependencies) { this.dependencies = dependencies; }
}
