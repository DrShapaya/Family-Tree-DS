package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds the shortest path between two people through stored relations. */
final class PersonPathFinder {
    private PersonPathFinder() {}

    static List<String> shortestPath(TreeState state, String startId, String endId) {
        List<String> path = new ArrayList<>();
        if (state == null || startId == null || endId == null
            || !state.people.containsKey(startId) || !state.people.containsKey(endId)) return path;
        if (startId.equals(endId)) {
            path.add(startId);
            return path;
        }
        Map<String, List<String>> graph = new HashMap<>();
        for (String id : state.people.keySet()) graph.put(id, new ArrayList<>());
        for (Relation link : state.links) {
            if (!graph.containsKey(link.from) || !graph.containsKey(link.to)) continue;
            graph.get(link.from).add(link.to);
            graph.get(link.to).add(link.from);
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, String> previous = new HashMap<>();
        queue.add(startId);
        previous.put(startId, "");
        while (!queue.isEmpty() && !previous.containsKey(endId)) {
            String current = queue.removeFirst();
            for (String next : graph.getOrDefault(current, Collections.emptyList())) {
                if (previous.containsKey(next)) continue;
                previous.put(next, current);
                queue.addLast(next);
            }
        }
        if (!previous.containsKey(endId)) return path;
        String current = endId;
        while (!current.isEmpty()) {
            path.add(0, current);
            current = previous.getOrDefault(current, "");
        }
        return path;
    }
}
