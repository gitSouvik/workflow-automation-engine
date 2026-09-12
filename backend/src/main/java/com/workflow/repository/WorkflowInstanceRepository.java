package com.workflow.repository;

import com.workflow.domain.WorkflowInstance;
import com.workflow.domain.WorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, String> {

    List<WorkflowInstance> findByStatus(WorkflowStatus status);

    List<WorkflowInstance> findByInitiatorId(String initiatorId);

    @Query("SELECT COUNT(w) FROM WorkflowInstance w WHERE w.status = :status")
    long countByStatus(@Param("status") WorkflowStatus status);

    /** Used by crash-recovery: find all in-flight instances to reconstruct state. */
    @Query("SELECT w FROM WorkflowInstance w WHERE w.status = 'RUNNING'")
    List<WorkflowInstance> findAllRunning();

    /**
     * Hand-rolled optimistic concurrency control:
     * Atomically increment version if and only if the current version matches expectedVersion.
     * Returns 1 if the update succeeded (version matched), 0 if another transaction already updated it.
     *
     * This fires the conflict check IMMEDIATELY (not at commit time), making it
     * safe for concurrent single-JVM threads and distributed multi-node deployments.
     */
    @Modifying
    @Query("UPDATE WorkflowInstance w SET w.version = w.version + 1 WHERE w.id = :id AND w.version = :expectedVersion")
    int incrementVersionIfMatch(@Param("id") String id, @Param("expectedVersion") Long expectedVersion);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT w FROM WorkflowInstance w WHERE w.id = :id")
    Optional<WorkflowInstance> findByIdForUpdate(@Param("id") String id);
}
