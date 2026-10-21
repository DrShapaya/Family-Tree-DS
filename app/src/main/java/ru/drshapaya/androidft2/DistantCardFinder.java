package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Finds disconnected cards outside the main tree cluster without depending on the UI. */
final class DistantCardFinder {
    private static final float FAR_CARD_DISTANCE = TreeLayoutEngine.GRID * 14f;

    static final class Bounds {
        float left;
        float top;
        float right;
        float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    private DistantCardFinder() {}

    static List<String> findIds(TreeState state) {
        List<String> result = new ArrayList<>();
        if (state == null || state.people.size() < 2) return result;
        Set<String> mainIds = mainTreeComponentIds(state);
        if (mainIds.isEmpty() || mainIds.size() == state.people.size()) return result;
        Set<String> otherIds = new HashSet<>(state.people.keySet());
        otherIds.removeAll(mainIds);
        Bounds mainBounds = boundsExcluding(state, otherIds);
        for (Person person : state.people.values()) {
            if (mainIds.contains(person.id)) continue;
            float dx = horizontalGap(person, mainBounds);
            float dy = verticalGap(person, mainBounds);
            if ((float) Math.hypot(dx, dy) > FAR_CARD_DISTANCE) result.add(person.id);
        }
        return result;
    }

    static Bounds boundsExcluding(TreeState state, Collection<String> excludedIds) {
        Set<String> excluded = new HashSet<>(excludedIds);
        Bounds bounds = new Bounds(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        for (Person person : state.people.values()) {
            if (excluded.contains(person.id)) continue;
            bounds.left = Math.min(bounds.left, person.x);
            bounds.top = Math.min(bounds.top, person.y);
            bounds.right = Math.max(bounds.right, person.x + TreeLayoutEngine.CARD_W);
            bounds.bottom = Math.max(bounds.bottom, person.y + TreeLayoutEngine.CARD_H);
        }
        if (bounds.left == Float.MAX_VALUE) {
            return new Bounds(0, 0, TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H);
        }
        return bounds;
    }

    private static Set<String> mainTreeComponentIds(TreeState state) {
        Set<String> visited = new HashSet<>();
        Set<String> best = new LinkedHashSet<>();
        for (String id : state.people.keySet()) {
            if (visited.contains(id)) continue;
            Set<String> component = connectedComponent(state, id);
            visited.addAll(component);
            boolean componentHasRoot = component.contains(state.rootId);
            boolean bestHasRoot = best.contains(state.rootId);
            if (componentHasRoot
                || best.isEmpty()
                || (!bestHasRoot && component.size() > best.size())) {
                best = component;
            }
        }
        return best;
    }

    private static Set<String> connectedComponent(TreeState state, String startId) {
        Set<String> component = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (state.people.containsKey(startId)) queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!component.add(id)) continue;
            for (Relation link : state.links) {
                if (id.equals(link.from) && state.people.containsKey(link.to)) queue.addLast(link.to);
                else if (id.equals(link.to) && state.people.containsKey(link.from)) queue.addLast(link.from);
            }
        }
        return component;
    }

    private static float horizontalGap(Person person, Bounds bounds) {
        float right = person.x + TreeLayoutEngine.CARD_W;
        if (right < bounds.left) return bounds.left - right;
        if (bounds.right < person.x) return person.x - bounds.right;
        return 0f;
    }

    private static float verticalGap(Person person, Bounds bounds) {
        float bottom = person.y + TreeLayoutEngine.CARD_H;
        if (bottom < bounds.top) return bounds.top - bottom;
        if (bounds.bottom < person.y) return person.y - bounds.bottom;
        return 0f;
    }
}
