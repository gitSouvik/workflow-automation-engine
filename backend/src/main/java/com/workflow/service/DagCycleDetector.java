package com.workflow.service;

import com.workflow.domain.TaskDependency;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Implements Kahn's algorithm to detect cycles in a workflow DAG.
 * Called exactly once when a WorkflowDefinition is created.
 * If a cycle is detected, the definition is rejected with HTTP 422.
 */
@Component
public class DagCycleDetector {

    public List<String> validateAndSort(Set<String> taskKeys, List<TaskDependency> dependencies) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String key : taskKeys) {
            adj.put(key, new ArrayList<>());
            inDegree.put(key, 0);
        }

        for (TaskDependency dep : dependencies) {
            if (!taskKeys.contains(dep.getFromTaskKey())) {
                throw new IllegalArgumentException("Dependency references unknown task key: " + dep.getFromTaskKey());
            }
            if (!taskKeys.contains(dep.getToTaskKey())) {
                throw new IllegalArgumentException("Dependency references unknown task key: " + dep.getToTaskKey());
            }
            if (dep.getFromTaskKey().equals(dep.getToTaskKey())) {
                throw new CycleDetectedException("Self-referencing task: " + dep.getFromTaskKey());
            }
            adj.get(dep.getFromTaskKey()).add(dep.getToTaskKey());
            inDegree.merge(dep.getToTaskKey(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<String> topologicalOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            topologicalOrder.add(current);
            for (String neighbor : adj.get(current)) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) queue.add(neighbor);
            }
        }

        if (topologicalOrder.size() != taskKeys.size()) {
            Set<String> cycleNodes = new HashSet<>(taskKeys);
            cycleNodes.removeAll(topologicalOrder);
            throw new CycleDetectedException("Cycle detected involving nodes: " + cycleNodes);
        }

        return topologicalOrder;
    }

    public static class CycleDetectedException extends RuntimeException {
        public CycleDetectedException(String message) { super(message); }
    }
}
