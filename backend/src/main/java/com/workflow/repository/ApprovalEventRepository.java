package com.workflow.repository;

import com.workflow.domain.ApprovalEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalEventRepository extends JpaRepository<ApprovalEvent, String> {

    List<ApprovalEvent> findByWorkflowInstanceIdOrderByTimestampAsc(String workflowInstanceId);

    List<ApprovalEvent> findByTaskInstanceId(String taskInstanceId);
}
