package ru.drshapaya.androidft2;

import java.util.*;

/** Linear-time adjacency and ancestry SCC index, scoped to one immutable tree revision. */
final class TreeRelationsIndex {
    final Map<String, Set<String>> adjacent = new LinkedHashMap<>();
    final Map<String, Set<String>> children = new LinkedHashMap<>();
    final Map<String, Set<String>> parents = new LinkedHashMap<>();
    final Map<String, Set<String>> partners = new LinkedHashMap<>();
    final Set<String> linkedPeople = new HashSet<>();
    private final Map<String, Integer> ancestryComponents = new HashMap<>();

    TreeRelationsIndex(TreeState state) {
        this(state, true);
    }

    TreeRelationsIndex(TreeState state, boolean cycles) {
        for (String id : state.people.keySet()) {
            adjacent.put(id, new LinkedHashSet<>());
            children.put(id, new LinkedHashSet<>()); parents.put(id, new LinkedHashSet<>());
            partners.put(id, new LinkedHashSet<>());
        }
        for (Relation link : state.links) {
            if (adjacent.containsKey(link.from)) linkedPeople.add(link.from);
            if (adjacent.containsKey(link.to)) linkedPeople.add(link.to);
            if (!adjacent.containsKey(link.from) || !adjacent.containsKey(link.to)) continue;
            adjacent.get(link.from).add(link.to); adjacent.get(link.to).add(link.from);
            if ("parent".equals(link.type)) {
                children.get(link.from).add(link.to); parents.get(link.to).add(link.from);
            } else if ("partner".equals(link.type) || "family".equals(link.type)) {
                partners.get(link.from).add(link.to); partners.get(link.to).add(link.from);
            }
        }
        if (!cycles) return;
        List<String> finished = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : children.keySet()) {
            if (!seen.add(id)) continue;
            ArrayDeque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(id, children.get(id).iterator()));
            while (!stack.isEmpty()) {
                Frame top = stack.peek();
                if (!top.next.hasNext()) { finished.add(top.id); stack.pop(); continue; }
                String child = top.next.next();
                if (seen.add(child)) stack.push(new Frame(child, children.get(child).iterator()));
            }
        }
        int component = 0;
        for (int i=finished.size()-1; i>=0; i--) {
            String id = finished.get(i);
            if (ancestryComponents.containsKey(id)) continue;
            ArrayDeque<String> stack = new ArrayDeque<>(); stack.push(id);
            ancestryComponents.put(id, component);
            while (!stack.isEmpty()) {
                for (String parent : parents.get(stack.pop())) {
                    if (!ancestryComponents.containsKey(parent)) {
                        ancestryComponents.put(parent, component); stack.push(parent);
                    }
                }
            }
            component++;
        }
    }

    boolean ancestryCycle(String from, String to) {
        Integer a = ancestryComponents.get(from), b = ancestryComponents.get(to);
        return a != null && a.equals(b);
    }

    Set<String> component(String start) {
        Set<String> result = new LinkedHashSet<>();
        if (!adjacent.containsKey(start)) return result;
        ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(start); result.add(start);
        while (!queue.isEmpty()) for (String id : adjacent.get(queue.removeFirst()))
            if (result.add(id)) queue.addLast(id);
        return result;
    }

    private static final class Frame {
        final String id; final Iterator<String> next;
        Frame(String id, Iterator<String> next) { this.id=id; this.next=next; }
    }
}
