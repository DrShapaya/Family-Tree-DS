package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Emits reversible swaps of adjacent sibling subtrees without inspecting names. */
final class SiblingBranchSwapCandidateGenerator implements LayoutCandidateGenerator {
    private final float minimumGap;

    SiblingBranchSwapCandidateGenerator(float minimumGap) {
        this.minimumGap = Math.max(0f, minimumGap);
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        for (FamilyLayoutGraph.SiblingGroup group : graph.siblingGroups) {
            List<SiblingBranch> ordered = new ArrayList<>();
            for (String childId : group.people) {
                Set<String> ids = graph.descendantBranch(Collections.singleton(childId));
                if (ids.isEmpty() || ids.contains(graph.rootId)) continue;
                BranchContour contour = BranchContour.from(snapshot, ids);
                ordered.add(new SiblingBranch(childId, ids, contour));
            }
            ordered.sort(Comparator
                .comparingDouble((SiblingBranch branch) -> branch.contour.left())
                .thenComparing(branch -> branch.childId));
            for (int index = 0; index + 1 < ordered.size(); index++) {
                SiblingBranch left = ordered.get(index);
                SiblingBranch right = ordered.get(index + 1);
                if (!Collections.disjoint(left.ids, right.ids)) continue;

                float rightShift = snap(left.contour.left() - right.contour.left());
                BranchContour shiftedRight = right.contour.shifted(rightShift);
                float leftShift = snap(left.contour.requiredRightShiftFrom(
                    shiftedRight,
                    minimumGap));
                if (Math.abs(leftShift) < 0.5f && Math.abs(rightShift) < 0.5f) continue;

                String fingerprint = left.childId + "|" + right.childId
                    + "@" + Math.round(leftShift) + "," + Math.round(rightShift);
                if (!fingerprints.add(fingerprint)) continue;
                result.add(new CompositeLayoutOperation(Arrays.asList(
                    new ShiftLayoutOperation(right.ids, rightShift, 0f, "swap-sibling-left"),
                    new ShiftLayoutOperation(left.ids, leftShift, 0f, "swap-sibling-right")),
                    "swap-sibling-branches:" + left.childId + ":" + right.childId));
            }
        }
        return result;
    }

    private static float snap(float value) {
        return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }

    private static final class SiblingBranch {
        final String childId;
        final LinkedHashSet<String> ids;
        final BranchContour contour;

        SiblingBranch(String childId, Set<String> ids, BranchContour contour) {
            this.childId = childId;
            this.ids = new LinkedHashSet<>(ids);
            this.contour = contour;
        }
    }
}
