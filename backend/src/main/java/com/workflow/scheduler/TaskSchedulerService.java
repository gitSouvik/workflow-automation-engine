package com.workflow.scheduler;

import com.workflow.domain.*;
import com.workflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * CRASH-RECOVERABLE TASK SCHEDULER
 * Polls DB every 500ms for READY tasks → transitions to IN_PROGRESS.
 * All state is in SQL — no in-memory state, fully crash-safe.
 */
@Service
public class TaskSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulerService.class);

    private final TaskInstanceRepository taskInstanceRepo;
    private final WorkflowInstanceRepository instanceRepo;

    public TaskSchedulerService(TaskInstanceRepository taskInstanceRepo, WorkflowInstanceRepository instanceRepo) {
        this.taskInstanceRepo = taskInstanceRepo;
        this.instanceRepo = instanceRepo;
    }

    @Scheduled(fixedDelay = 500)
    public void claimReadyTasks() {
        List<TaskInstance> readyTasks = taskInstanceRepo.findByStatus(TaskStatus.READY);
        if (readyTasks.isEmpty()) return;
        log.debug("Scheduler tick: {} READY tasks found", readyTasks.size());

        for (TaskInstance task : readyTasks) {
            try {
                claimTask(task.getId());
            } catch (OptimisticLockingFailureException e) {
                log.debug("Task [{}] already claimed concurrently — skipping", task.getId());
            } catch (Exception e) {
                log.warn("Failed to claim task [{}]: {}", task.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void claimTask(String taskInstanceId) {
        TaskInstance task = taskInstanceRepo.findById(taskInstanceId).orElse(null);
        if (task == null || task.getStatus() != TaskStatus.READY) return;

        WorkflowInstance instance = instanceRepo.findById(task.getWorkflowInstance().getId()).orElse(null);
        if (instance == null || instance.getStatus() != WorkflowStatus.RUNNING) return;

        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(Instant.now());
        taskInstanceRepo.save(task);

        // Touch parent to bump @Version — prevents concurrent claim of same task
        instanceRepo.save(instance);

        log.info("[{}] Task '{}' claimed → IN_PROGRESS", instance.getId(), task.getTaskKey());
    }
}
