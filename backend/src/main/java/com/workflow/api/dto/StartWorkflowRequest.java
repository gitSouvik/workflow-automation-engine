package com.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public class StartWorkflowRequest {
    @NotBlank
    private String definitionId;
    @NotBlank
    private String title;
    @NotBlank
    private String initiatorId;

    public String getDefinitionId() { return definitionId; }
    public void setDefinitionId(String definitionId) { this.definitionId = definitionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getInitiatorId() { return initiatorId; }
    public void setInitiatorId(String initiatorId) { this.initiatorId = initiatorId; }
}
