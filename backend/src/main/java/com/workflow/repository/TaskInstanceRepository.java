package com.workflow.repository;

import com.workflow.domain.TaskInstance;
import com.workflow.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskInstanceRepository extends JpaRepository<TaskInstance, String> {

    List<TaskInstance> findByWorkflowInstanceId(String workflowInstanceId);

    Optional<TaskInstance> findByWorkflowInstanceIdAndTaskKey(String workflowInstanceId, String taskKey);

    /** All tasks across any instance that are READY (awaiting scheduler claim). */
    List<TaskInstance> findByStatus(TaskStatus status);

    /** Pending approvals for a specific role — used by the "my approvals" endpoint. */
    @Query("SELECT t FROM TaskInstance t WHERE t.assigneeRole = :role AND t.status = 'IN_PROGRESS'")
    List<TaskInstance> findPendingByRole(@Param("role") String role);

    /** Pending approvals for a specific user — used by the "my approvals" endpoint. */
    @Query("SELECT t FROM TaskInstance t WHERE t.assigneeId = :userId AND t.status = 'IN_PROGRESS'")
    List<TaskInstance> findPendingByUser(@Param("userId") String userId);

    /** For crash recovery: find tasks stuck IN_PROGRESS that need to be reset to READY. */
    @Query("SELECT t FROM TaskInstance t WHERE t.status = 'IN_PROGRESS' AND t.workflowInstance.status = 'RUNNING'")
    List<TaskInstance> findAllInProgress();

    @Query("SELECT t FROM TaskInstance t WHERE t.workflowInstance.id = :instanceId AND t.status IN ('PENDING', 'READY', 'IN_PROGRESS')")
    List<TaskInstance> findActiveTasks(@Param("instanceId") String instanceId);

    /** Batch reset IN_PROGRESS → READY for crash recovery (tasks with no heartbeat). */
    @Modifying
    @Query("UPDATE TaskInstance t SET t.status = 'READY', t.startedAt = null, t.assigneeId = null WHERE t.status = 'IN_PROGRESS' AND t.workflowInstance.status = 'RUNNING'")
    int resetInProgressToReady();

    /** p50/p99 latency data — all completed step durations. */
    @Query("SELECT t.stepDurationMs FROM TaskInstance t WHERE t.stepDurationMs IS NOT NULL ORDER BY t.stepDurationMs")
    List<Long> findAllStepDurations();
}
