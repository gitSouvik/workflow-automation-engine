package com.workflow.scheduler;

import com.workflow.domain.*;
import com.workflow.repository.*;
import com.workflow.service.WorkflowStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRASH RECOVERY SERVICE
 * Runs on ApplicationReadyEvent — reconstructs in-flight state from DB.
 *
 * Algorithm:
 * 1. Reset all IN_PROGRESS tasks → READY (they were claimed but not acted on before crash)
 * 2. For each RUNNING workflow instance, recompute task readiness
 */
@Service
public class BootRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(BootRecoveryService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskInstanceRepo;
    private final WorkflowStateMachine stateMachine;
    private final RecoveryHelperService recoveryHelper;

    public BootRecoveryService(WorkflowInstanceRepository instanceRepo,
                                TaskInstanceRepository taskInstanceRepo,
                                WorkflowStateMachine stateMachine,
                                RecoveryHelperService recoveryHelper) {
        this.instanceRepo = instanceRepo;
        this.taskInstanceRepo = taskInstanceRepo;
        this.stateMachine = stateMachine;
        this.recoveryHelper = recoveryHelper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        log.info("=== BOOT RECOVERY: Starting crash recovery scan ===");

        int resetCount = recoveryHelper.resetInProgressToReady();
        log.info("Boot recovery: reset {} IN_PROGRESS tasks to READY", resetCount);

        List<WorkflowInstance> running = instanceRepo.findAllRunning();
        log.info("Boot recovery: found {} running workflow instances to resume", running.size());

        for (WorkflowInstance instance : running) {
            try {
                stateMachine.recomputeReadiness(instance);
            } catch (Exception e) {
                log.error("Boot recovery: failed for instance [{}]: {}", instance.getId(), e.getMessage(), e);
            }
        }

        log.info("=== BOOT RECOVERY COMPLETE: {} instances resumed ===", running.size());
    }

    @Transactional(readOnly = true)
    public int countReadyTasks(String instanceId) {
        return (int) taskInstanceRepo.findByWorkflowInstanceId(instanceId).stream()
            .filter(t -> t.getStatus() == TaskStatus.READY).count();
    }
}
