package ru.drshapaya.androidft2;

import java.util.*;

/** Structural guard for late compaction passes: removing empty space must
 * not pull family bands apart or move children away from their parents.
 *
 * <p>The measure is a sum over same-row families, so a compaction pass that
 * restores a family in several unit moves may temporarily trade one family's
 * offset for a much tighter sibling band, but it may never make the tree less
 * cohesive overall. */
final class FamilyCohesion {
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float TOLERANCE = TreeLayoutEngine.GRID / 2f;

    private FamilyCohesion() {}

    static boolean preserved(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        return cost(graph, after) <= cost(graph, before) + TOLERANCE;
    }

    /** Sum of sibling-band widths and parent-to-children center offsets. */
    static float cost(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        float cost = 0;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.children.isEmpty() || !sameRow(family.children, snapshot)) continue;
            if (family.children.size() > 1) cost += span(family.children, snapshot);
            if (family.parents.isEmpty() || !sameRow(family.parents, snapshot)) continue;
            cost += Math.abs(center(family.parents, snapshot) - center(family.children, snapshot));
        }
        return cost;
    }

    private static boolean sameRow(Collection<String> ids, LayoutSnapshot snapshot) {
        float y = snapshot.positionOf(ids.iterator().next()).y;
        for (String id : ids) if (snapshot.positionOf(id).y != y) return false;
        return true;
    }

    private static float span(Collection<String> ids, LayoutSnapshot snapshot) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (String id : ids) {
            float x = snapshot.positionOf(id).x;
            min = Math.min(min, x); max = Math.max(max, x);
        }
        return max - min;
    }

    private static float center(Collection<String> ids, LayoutSnapshot snapshot) {
        float sum = 0;
        for (String id : ids) sum += snapshot.positionOf(id).x + W / 2f;
        return sum / ids.size();
    }
}
