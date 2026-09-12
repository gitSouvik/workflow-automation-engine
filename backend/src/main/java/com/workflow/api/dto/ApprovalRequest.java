package com.workflow.api.dto;

import com.workflow.domain.ApprovalAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ApprovalRequest {
    @NotBlank
    private String actorId;
    @NotNull
    private ApprovalAction action;
    private String comment;

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
