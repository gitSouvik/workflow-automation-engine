package com.workflow.scheduler;

import com.workflow.repository.TaskInstanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper service for transactional operations needed during boot recovery.
 * Extracted to a separate bean so Spring's transactional proxy applies correctly
 * when called from BootRecoveryService's @EventListener method.
 */
@Service
public class RecoveryHelperService {

    private final TaskInstanceRepository taskInstanceRepo;

    public RecoveryHelperService(TaskInstanceRepository taskInstanceRepo) {
        this.taskInstanceRepo = taskInstanceRepo;
    }

    @Transactional
    public int resetInProgressToReady() {
        return taskInstanceRepo.resetInProgressToReady();
    }
}
