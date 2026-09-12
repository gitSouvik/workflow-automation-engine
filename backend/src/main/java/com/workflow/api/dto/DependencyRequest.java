package com.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public class DependencyRequest {
    @NotBlank
    private String fromTaskKey;
    @NotBlank
    private String toTaskKey;

    public String getFromTaskKey() { return fromTaskKey; }
    public void setFromTaskKey(String fromTaskKey) { this.fromTaskKey = fromTaskKey; }
    public String getToTaskKey() { return toTaskKey; }
    public void setToTaskKey(String toTaskKey) { this.toTaskKey = toTaskKey; }
}
