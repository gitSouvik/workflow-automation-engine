package com.workflow.metrics;

import com.workflow.domain.WorkflowStatus;
import com.workflow.repository.TaskInstanceRepository;
import com.workflow.repository.WorkflowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects and serves workflow metrics via SSE.
 * Pushes snapshots every 2 seconds to all connected dashboard clients.
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final WorkflowInstanceRepository instanceRepo;
    private final TaskInstanceRepository taskInstanceRepo;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<LatencyPoint> latencyHistory = new CopyOnWriteArrayList<>();

    public MetricsService(WorkflowInstanceRepository instanceRepo, TaskInstanceRepository taskInstanceRepo) {
        this.instanceRepo = instanceRepo;
        this.taskInstanceRepo = taskInstanceRepo;
    }

    @Transactional(readOnly = true)
    public MetricsSnapshot getSnapshot() {
        long running = instanceRepo.countByStatus(WorkflowStatus.RUNNING);
        long completed = instanceRepo.countByStatus(WorkflowStatus.COMPLETED);
        long rejected = instanceRepo.countByStatus(WorkflowStatus.REJECTED);
        long failed = instanceRepo.countByStatus(WorkflowStatus.FAILED);

        List<Long> durations = taskInstanceRepo.findAllStepDurations();
        long p50 = computePercentile(durations, 50);
        long p99 = computePercentile(durations, 99);

        return new MetricsSnapshot(running, completed, rejected, failed,
            durations.size(), p50, p99, Instant.now(), new ArrayList<>(latencyHistory));
    }

    public SseEmitter createSseEmitter() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        log.info("SSE client connected. Total clients: {}", emitters.size());
        return emitter;
    }

    @Scheduled(fixedDelay = 2000)
    public void pushMetricsToClients() {
        if (emitters.isEmpty()) return;

        MetricsSnapshot snapshot = getSnapshot();
        latencyHistory.add(new LatencyPoint(snapshot.timestamp(), snapshot.p50Ms(), snapshot.p99Ms()));
        if (latencyHistory.size() > 60) latencyHistory.remove(0);

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("metrics").data(snapshot));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }

    private long computePercentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;
        int index = Math.max(0, (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    public record MetricsSnapshot(
        long runningCount, long completedCount, long rejectedCount, long failedCount,
        long totalStepsCompleted, long p50Ms, long p99Ms,
        Instant timestamp, List<LatencyPoint> latencyHistory) {}

    public record LatencyPoint(Instant timestamp, long p50Ms, long p99Ms) {}
}
