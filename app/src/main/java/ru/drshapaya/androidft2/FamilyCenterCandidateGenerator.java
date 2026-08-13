package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Centers a parent family over its direct children, never over lower descendant rows. */
final class FamilyCenterCandidateGenerator implements LayoutCandidateGenerator {
    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            float parentCenter = directRowCenter(family.parents, snapshot);
            float childCenter = directRowCenter(family.children, snapshot);
            if (!Float.isFinite(parentCenter) || !Float.isFinite(childCenter)) continue;
            float delta = snap(childCenter - parentCenter);
            if (Math.abs(delta) < 0.5f) continue;

            Set<String> parentBranch = graph.lineageBranch(family.parents);
            if (!parentBranch.isEmpty()) {
                result.add(new ShiftLayoutOperation(
                    parentBranch,
                    delta,
                    0f,
                    "center-parents:" + family.id));
            }

            LinkedHashSet<String> childBranches = new LinkedHashSet<>();
            for (String childId : family.children) {
                childBranches.addAll(graph.descendantBranch(Collections.singleton(childId)));
            }
            if (!childBranches.isEmpty()) {
                result.add(new ShiftLayoutOperation(
                    childBranches,
                    -delta,
                    0f,
                    "center-children:" + family.id));
            }
        }
        return result;
    }

    private static float directRowCenter(
        Iterable<String> ids,
        LayoutSnapshot snapshot
    ) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            left = Math.min(left, position.x);
            right = Math.max(right, position.x + TreeLayoutEngine.CARD_W);
        }
        return left == Float.MAX_VALUE ? Float.NaN : (left + right) / 2f;
    }

    private static float snap(float value) {
        return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }
}
