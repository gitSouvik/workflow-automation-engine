package com.workflow.service;

import com.workflow.domain.TaskDefinition;
import com.workflow.domain.TaskDependency;
import com.workflow.domain.WorkflowDefinition;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class WorkflowValidator {

    public void validate(WorkflowDefinition definition) {
        if (definition.getTasks() == null || definition.getTasks().isEmpty()) {
            throw new IllegalArgumentException("Workflow must have at least one task");
        }

        Map<String, List<String>> adjacencyList = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Set<String> allTaskKeys = new HashSet<>();

        for (TaskDefinition task : definition.getTasks()) {
            adjacencyList.put(task.getTaskKey(), new ArrayList<>());
            inDegree.put(task.getTaskKey(), 0);
            allTaskKeys.add(task.getTaskKey());
        }

        if (definition.getDependencies() != null) {
            for (TaskDependency dep : definition.getDependencies()) {
                if (!allTaskKeys.contains(dep.getFromTaskKey()) || !allTaskKeys.contains(dep.getToTaskKey())) {
                    throw new IllegalArgumentException("Dependency references unknown task keys");
                }
                adjacencyList.get(dep.getFromTaskKey()).add(dep.getToTaskKey());
                inDegree.put(dep.getToTaskKey(), inDegree.get(dep.getToTaskKey()) + 1);
            }
        }

        // 1. Cycle detection (Topological Sort using Kahn's algorithm)
        Queue<String> queue = new LinkedList<>();
        List<String> sorted = new ArrayList<>();
        
        List<String> startNodes = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
                startNodes.add(entry.getKey());
            }
        }

        if (startNodes.isEmpty()) {
             throw new IllegalArgumentException("Invalid graph: No start nodes found (orphan or cycle)");
        }

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            sorted.add(curr);

            for (String neighbor : adjacencyList.get(curr)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        if (sorted.size() != allTaskKeys.size()) {
            throw new IllegalArgumentException("Invalid graph: Cycle detected");
        }

        // 2. Orphan detection
        // Forward reachability (from start nodes)
        Set<String> reachableFromStart = new HashSet<>();
        Queue<String> reachQueue = new LinkedList<>(startNodes);
        reachableFromStart.addAll(startNodes);
        
        while (!reachQueue.isEmpty()) {
            String curr = reachQueue.poll();
            for (String neighbor : adjacencyList.get(curr)) {
                if (reachableFromStart.add(neighbor)) {
                    reachQueue.offer(neighbor);
                }
            }
        }

        if (reachableFromStart.size() != allTaskKeys.size()) {
            throw new IllegalArgumentException("Invalid graph: Contains orphan nodes not reachable from start nodes");
        }
        
        // Reverse reachability (to end nodes)
        Map<String, List<String>> reverseAdjList = new HashMap<>();
        for (String key : allTaskKeys) reverseAdjList.put(key, new ArrayList<>());
        List<String> endNodes = new ArrayList<>();
        
        if (definition.getDependencies() != null) {
            for (TaskDependency dep : definition.getDependencies()) {
                reverseAdjList.get(dep.getToTaskKey()).add(dep.getFromTaskKey());
            }
        }
        for (String key : allTaskKeys) {
            if (adjacencyList.get(key).isEmpty()) endNodes.add(key);
        }
        
        Set<String> reachableToEnd = new HashSet<>(endNodes);
        Queue<String> revQueue = new LinkedList<>(endNodes);
        while (!revQueue.isEmpty()) {
            String curr = revQueue.poll();
            for (String neighbor : reverseAdjList.get(curr)) {
                if (reachableToEnd.add(neighbor)) {
                    revQueue.offer(neighbor);
                }
            }
        }
        
        if (reachableToEnd.size() != allTaskKeys.size()) {
            throw new IllegalArgumentException("Invalid graph: Contains orphan nodes that cannot reach an end node");
        }
    }
}
