package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Creates spacing candidates for semantic sibling branches. */
final class SiblingSpacingCandidateGenerator implements LayoutCandidateGenerator {
    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (FamilyLayoutGraph.SiblingGroup group : graph.siblingGroups) {
            List<SiblingBranch> branches = branches(graph, group, snapshot);
            branches.sort(Comparator
                .comparingDouble((SiblingBranch branch) -> branch.contour.left())
                .thenComparing(branch -> branch.personId));
            for (int index = 0; index + 1 < branches.size(); index++) {
                SiblingBranch left = branches.get(index);
                SiblingBranch right = branches.get(index + 1);
                float actualGap = right.contour.horizontalGapFrom(left.contour);
                if (!Float.isFinite(actualGap)) continue;
                float desiredGap = desiredGap(graph, left, right);
                float delta = snap(desiredGap - actualGap);
                if (Math.abs(delta) < 0.5f) continue;

                LinkedHashSet<String> leftSide = union(branches, 0, index + 1);
                LinkedHashSet<String> rightSide = union(branches, index + 1, branches.size());
                result.add(new ShiftLayoutOperation(
                    rightSide,
                    delta,
                    0f,
                    "space-siblings-right:" + group.id));
                result.add(new ShiftLayoutOperation(
                    leftSide,
                    -delta,
                    0f,
                    "space-siblings-left:" + group.id));

                float leftDelta = -snap(delta / 2f);
                float rightDelta = delta + leftDelta;
                if (Math.abs(leftDelta) >= 0.5f && Math.abs(rightDelta) >= 0.5f) {
                    result.add(new CompositeLayoutOperation(Arrays.asList(
                        new ShiftLayoutOperation(
                            leftSide,
                            leftDelta,
                            0f,
                            "space-siblings-split-left"),
                        new ShiftLayoutOperation(
                            rightSide,
                            rightDelta,
                            0f,
                            "space-siblings-split-right")),
                        "space-siblings-split:" + group.id));
                }
            }
        }
        return result;
    }

    private static float desiredGap(
        FamilyLayoutGraph graph,
        SiblingBranch left,
        SiblingBranch right
    ) {
        FamilyLayoutGraph.ParentFamily leftFamily = graph.parentFamilyOf(left.personId);
        FamilyLayoutGraph.ParentFamily rightFamily = graph.parentFamilyOf(right.personId);
        // Half-siblings are related, but their exact parent sets form different
        // family branches. Collapsing that boundary to a child-sized gap mixes unions.
        if (leftFamily != rightFamily) return TreeLayoutEngine.GRID * 5f;
        return left.simple && right.simple
            ? TreeLayoutEngine.GRID
            : TreeLayoutEngine.GRID * 5f;
    }

    private static List<SiblingBranch> branches(
        FamilyLayoutGraph graph,
        FamilyLayoutGraph.SiblingGroup group,
        LayoutSnapshot snapshot
    ) {
        List<SiblingBranch> result = new ArrayList<>();
        for (String personId : group.people) {
            Set<String> ids = graph.descendantBranch(Collections.singleton(personId));
            result.add(new SiblingBranch(
                personId,
                ids,
                BranchContour.from(snapshot, ids),
                ids.size() == 1));
        }
        return result;
    }

    private static LinkedHashSet<String> union(
        List<SiblingBranch> branches,
        int from,
        int to
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int index = from; index < to; index++) result.addAll(branches.get(index).ids);
        return result;
    }

    private static float snap(float value) {
        return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }

    private static final class SiblingBranch {
        final String personId;
        final Set<String> ids;
        final BranchContour contour;
        final boolean simple;

        SiblingBranch(String personId, Set<String> ids, BranchContour contour, boolean simple) {
            this.personId = personId;
            this.ids = ids;
            this.contour = contour;
            this.simple = simple;
        }
    }
}
