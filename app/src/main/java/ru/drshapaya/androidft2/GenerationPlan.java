package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves pedigree collapse without ever giving equal-row preferences priority over ancestry. */
final class GenerationPlan {
    final Map<String, Integer> levels = new LinkedHashMap<>();
    final Map<String, String> partnerGroups = new LinkedHashMap<>();
    boolean relaxed;
    boolean cyclic;

    static GenerationPlan from(TreeState state) {
        GenerationPlan plan = new GenerationPlan();
        Map<String, List<Step>> adjacent = new LinkedHashMap<>();
        Map<String, Set<String>> parents = new LinkedHashMap<>();
        Map<String, Set<String>> children = new LinkedHashMap<>();
        List<Relation> equal = new ArrayList<>();
        for (String id : state.people.keySet()) adjacent.put(id, new ArrayList<>());
        for (Relation link : state.links) {
            if (link == null || !adjacent.containsKey(link.from) || !adjacent.containsKey(link.to)) continue;
            boolean parent = "parent".equals(link.type);
            if (!parent && !"partner".equals(link.type) && !"family".equals(link.type)
                && !"sibling".equals(link.type)) continue;
            adjacent.get(link.from).add(new Step(link.to, parent ? 1 : 0));
            adjacent.get(link.to).add(new Step(link.from, parent ? -1 : 0));
            if (parent) {
                children.computeIfAbsent(link.from, ignored -> new LinkedHashSet<>()).add(link.to);
                parents.computeIfAbsent(link.to, ignored -> new LinkedHashSet<>()).add(link.from);
            } else equal.add(link);
        }
        // Ordinary trees retain the existing layout exactly. Only incompatible
        // +1/0 equations need the more general directed-generation plan.
        for (String seed : adjacent.keySet()) {
            if (plan.levels.containsKey(seed)) continue;
            ArrayDeque<String> queue = new ArrayDeque<>();
            plan.levels.put(seed, 0);
            queue.add(seed);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                for (Step step : adjacent.get(id)) {
                    int expected = plan.levels.get(id) + step.delta;
                    Integer previous = plan.levels.putIfAbsent(step.id, expected);
                    if (previous == null) queue.addLast(step.id);
                    else if (previous != expected) plan.relaxed = true;
                }
            }
        }
        if (!plan.relaxed) return plan;

        Groups groups = new Groups(adjacent.keySet(), children);
        plan.cyclic = groups.topological().size() != adjacent.size();
        // Do not hide a true ancestry cycle by merging its people. It remains
        // a data-quality conflict; still give all cards finite bounded rows.
        if (!plan.cyclic) {
            for (Relation link : equal) {
                if (!"sibling".equals(link.type)) groups.merge(link.from, link.to);
            }
            for (Set<String> family : parents.values()) {
                // More than two parents may represent different families of
                // origin. They are not automatically partners with one another.
                if (family.size() == 2) groups.mergeAll(family);
            }
        }
        for (String id : adjacent.keySet()) plan.partnerGroups.put(id, groups.find(id));
        if (!plan.cyclic) {
            for (Relation link : equal) {
                if ("sibling".equals(link.type)) groups.merge(link.from, link.to);
            }
            for (Set<String> siblings : children.values()) groups.mergeAll(siblings);
        }

        Map<String, Set<String>> edges = groups.edges();
        List<String> order = groups.topological();
        // The only unprocessed nodes here belong to or follow actual input
        // cycles. Preserve their links and avoid an unbounded repair loop.
        for (String id : edges.keySet()) if (!order.contains(id)) order.add(id);
        Map<String, Integer> rows = new LinkedHashMap<>();
        for (String id : order) rows.put(id, 0);
        Set<String> visited = new LinkedHashSet<>();
        for (String id : order) {
            visited.add(id);
            for (String child : edges.get(id)) {
                if (!visited.contains(child)) rows.put(child, Math.max(rows.get(child), rows.get(id) + 1));
            }
        }
        // Lift short ancestry branches towards their children. In particular,
        // a parent with no recorded ancestors need not stay at the top row.
        if (!plan.cyclic) {
            for (int index = order.size() - 1; index >= 0; index--) {
                String id = order.get(index);
                if (edges.get(id).isEmpty()) continue;
                int row = Integer.MAX_VALUE;
                for (String child : edges.get(id)) row = Math.min(row, rows.get(child) - 1);
                rows.put(id, row);
            }
        }
        plan.levels.clear();
        for (String id : adjacent.keySet()) plan.levels.put(id, rows.get(groups.find(id)));
        return plan;
    }

    int difference(String from, String to) {
        return levels.getOrDefault(to, 0) - levels.getOrDefault(from, 0);
    }

    private static final class Step {
        final String id;
        final int delta;
        Step(String id, int delta) { this.id = id; this.delta = delta; }
    }

    private static final class Groups {
        final Map<String, String> roots = new LinkedHashMap<>();
        final Map<String, Set<String>> children;

        Groups(Set<String> ids, Map<String, Set<String>> children) {
            for (String id : ids) roots.put(id, id);
            this.children = children;
        }

        String find(String id) {
            String root = id;
            while (!roots.get(root).equals(root)) root = roots.get(root);
            while (!id.equals(root)) {
                String next = roots.put(id, root);
                id = next;
            }
            return root;
        }

        Map<String, Set<String>> edges() {
            Map<String, Set<String>> edges = new LinkedHashMap<>();
            for (String id : roots.keySet()) edges.computeIfAbsent(find(id), ignored -> new LinkedHashSet<>());
            for (Map.Entry<String, Set<String>> entry : children.entrySet()) {
                for (String child : entry.getValue()) edges.get(find(entry.getKey())).add(find(child));
            }
            return edges;
        }

        void mergeAll(Set<String> ids) {
            String first = null;
            for (String id : ids) {
                if (first == null) first = id;
                else merge(first, id);
            }
        }

        void merge(String first, String second) {
            String a = find(first), b = find(second);
            if (a.equals(b)) return;
            Map<String, Set<String>> edges = edges();
            if (reaches(a, b, edges) || reaches(b, a, edges)) return;
            roots.put(b, a);
        }

        List<String> topological() {
            Map<String, Set<String>> edges = edges();
            Map<String, Integer> incoming = new LinkedHashMap<>();
            for (String id : edges.keySet()) incoming.put(id, 0);
            for (Set<String> ids : edges.values()) for (String id : ids) incoming.put(id, incoming.get(id) + 1);
            ArrayDeque<String> queue = new ArrayDeque<>();
            for (String id : incoming.keySet()) if (incoming.get(id) == 0) queue.addLast(id);
            List<String> result = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                result.add(id);
                for (String child : edges.get(id)) {
                    int count = incoming.get(child) - 1;
                    incoming.put(child, count);
                    if (count == 0) queue.addLast(child);
                }
            }
            return result;
        }

        private static boolean reaches(String from, String to, Map<String, Set<String>> edges) {
            ArrayDeque<String> queue = new ArrayDeque<>();
            Set<String> seen = new LinkedHashSet<>();
            queue.add(from);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                if (id.equals(to)) return true;
                if (seen.add(id)) queue.addAll(edges.getOrDefault(id, Collections.emptySet()));
            }
            return false;
        }
    }
}
