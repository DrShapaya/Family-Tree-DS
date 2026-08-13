package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Places the children of every union around their shared parent as separate branches.
 * The shared person stays fixed; the other partners move outward to the axis of their
 * own child group. This models remarriage without treating all partners as one couple.
 */
final class MultiUnionFamilyCandidateGenerator implements LayoutCandidateGenerator {
    private static final float EPSILON = 0.5f;
    private final float familyGap;

    MultiUnionFamilyCandidateGenerator(float familyGap) {
        this.familyGap = Math.max(0f, familyGap);
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (String sharedId : graph.people.keySet()) {
            List<UnionBranch> branches = unionBranches(graph, snapshot, sharedId);
            if (branches.size() < 2) continue;
            LayoutSnapshot.Position shared = snapshot.positionOf(sharedId);
            if (shared == null || !shared.isFinite()) continue;

            branches.sort(Comparator
                .comparingDouble((UnionBranch branch) -> branch.partnerX)
                .thenComparing(branch -> branch.unit.id));
            float[] relativeShifts = packedShifts(branches);
            float packedLeft = Float.MAX_VALUE;
            float packedRight = -Float.MAX_VALUE;
            for (int index = 0; index < branches.size(); index++) {
                packedLeft = Math.min(
                    packedLeft,
                    branches.get(index).contour.left() + relativeShifts[index]);
                packedRight = Math.max(
                    packedRight,
                    branches.get(index).contour.right() + relativeShifts[index]);
            }
            float sharedCenter = shared.x + TreeLayoutEngine.CARD_W / 2f;
            float commonShift = snap(sharedCenter - (packedLeft + packedRight) / 2f);

            List<LayoutOperation> operations = new ArrayList<>();
            LinkedHashSet<String> claimed = new LinkedHashSet<>();
            boolean ambiguous = false;
            for (int index = 0; index < branches.size(); index++) {
                UnionBranch branch = branches.get(index);
                float childShift = snap(relativeShifts[index] + commonShift);
                if (!Collections.disjoint(claimed, branch.childBranchIds)) {
                    ambiguous = true;
                    break;
                }
                claimed.addAll(branch.childBranchIds);
                if (Math.abs(childShift) > EPSILON) {
                    operations.add(new ShiftLayoutOperation(
                        branch.childBranchIds,
                        childShift,
                        0f,
                        "multi-union-children:" + branch.unit.id));
                }

                float childCenter = branch.directChildCenter + childShift;
                float desiredPartnerX = snap(
                    2f * childCenter - shared.x - TreeLayoutEngine.CARD_W);
                float partnerShift = snap(desiredPartnerX - branch.partnerX);
                Set<String> partnerBranch = partnerBranch(graph, branch.partnerId);
                if (!Collections.disjoint(claimed, partnerBranch)) {
                    ambiguous = true;
                    break;
                }
                claimed.addAll(partnerBranch);
                if (Math.abs(partnerShift) > EPSILON) {
                    operations.add(new ShiftLayoutOperation(
                        partnerBranch,
                        partnerShift,
                        0f,
                        "multi-union-partner:" + branch.unit.id));
                }
            }
            if (!ambiguous && !operations.isEmpty()) {
                result.add(new CompositeLayoutOperation(
                    operations,
                    "multi-union-family:" + sharedId));
            }
        }
        return result;
    }

    private float[] packedShifts(List<UnionBranch> branches) {
        float[] shifts = new float[branches.size()];
        for (int index = 1; index < branches.size(); index++) {
            BranchContour left = branches.get(index - 1).contour.shifted(shifts[index - 1]);
            BranchContour right = branches.get(index).contour;
            float actualGap = right.horizontalGapFrom(left);
            shifts[index] = Float.isFinite(actualGap) && actualGap != Float.MAX_VALUE
                ? snap(familyGap - actualGap)
                : 0f;
        }
        return shifts;
    }

    private static List<UnionBranch> unionBranches(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        String sharedId
    ) {
        List<UnionBranch> result = new ArrayList<>();
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(sharedId)) {
            if (unit.people.size() != 2 || unit.children.isEmpty()) continue;
            String partnerId = "";
            for (String id : unit.people) if (!id.equals(sharedId)) partnerId = id;
            LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
            if (partner == null || !partner.isFinite()) continue;
            LinkedHashSet<String> childIds = new LinkedHashSet<>();
            for (String childId : unit.children) {
                childIds.addAll(graph.descendantBranch(Collections.singleton(childId)));
            }
            if (childIds.isEmpty()) continue;
            float directCenter = directCenter(unit.children, snapshot);
            if (!Float.isFinite(directCenter)) continue;
            result.add(new UnionBranch(
                unit,
                partnerId,
                partner.x,
                childIds,
                BranchContour.from(snapshot, childIds),
                directCenter));
        }
        return result;
    }

    private static Set<String> partnerBranch(FamilyLayoutGraph graph, String partnerId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(partnerId);
        result.addAll(graph.lineageBranch(graph.parentsByChild.getOrDefault(
            partnerId,
            Collections.emptySet())));
        return result;
    }

    private static float directCenter(Iterable<String> ids, LayoutSnapshot snapshot) {
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

    private static final class UnionBranch {
        final FamilyLayoutGraph.PartnerUnit unit;
        final String partnerId;
        final float partnerX;
        final Set<String> childBranchIds;
        final BranchContour contour;
        final float directChildCenter;

        UnionBranch(
            FamilyLayoutGraph.PartnerUnit unit,
            String partnerId,
            float partnerX,
            Set<String> childBranchIds,
            BranchContour contour,
            float directChildCenter
        ) {
            this.unit = unit;
            this.partnerId = partnerId;
            this.partnerX = partnerX;
            this.childBranchIds = childBranchIds;
            this.contour = contour;
            this.directChildCenter = directChildCenter;
        }
    }
}
