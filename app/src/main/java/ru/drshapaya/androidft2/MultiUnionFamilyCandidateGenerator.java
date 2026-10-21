package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

            float sharedCenter = shared.x + TreeLayoutEngine.CARD_W / 2f;
            Map<UnionBranch, Float> branchShifts = directedPackedShifts(
                sharedCenter, branches);

            List<LayoutOperation> operations = new ArrayList<>();
            LinkedHashSet<String> claimed = new LinkedHashSet<>();
            boolean ambiguous = false;
            for (UnionBranch branch : branches) {
                float childShift = branchShifts.getOrDefault(branch, 0f);
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
                LayoutOperation candidate = new CompositeLayoutOperation(
                    operations, "multi-union-family:" + sharedId);
                if (siblingsRemainOutside(graph, candidate.apply(snapshot),
                    sharedId, branches)) result.add(candidate);
            }
        }
        return result;
    }

    private static boolean siblingsRemainOutside(
        FamilyLayoutGraph graph, LayoutSnapshot layout,
        String sharedId, List<UnionBranch> branches
    ) {
        FamilyLayoutGraph.SiblingGroup group = graph.siblingGroupByPerson.get(sharedId);
        if (group == null) return true;
        LayoutSnapshot.Position shared = layout.positionOf(sharedId);
        if (shared == null) return false;
        for (String siblingId : group.people) {
            if (sharedId.equals(siblingId)) continue;
            LayoutSnapshot.Position sibling = layout.positionOf(siblingId);
            if (sibling == null) continue;
            for (UnionBranch branch : branches) {
                LayoutSnapshot.Position partner = layout.positionOf(branch.partnerId);
                if (partner == null) continue;
                if (partner.x < shared.x && sibling.x < shared.x - EPSILON) return false;
                if (partner.x > shared.x && sibling.x > shared.x + EPSILON) return false;
            }
        }
        return true;
    }

    /** Packs separate unions on both sides of their shared person. */
    private Map<UnionBranch, Float> directedPackedShifts(
        float sharedCenter,
        List<UnionBranch> branches
    ) {
        List<UnionBranch> left = new ArrayList<>();
        List<UnionBranch> right = new ArrayList<>();
        Comparator<UnionBranch> order = Comparator
            .comparingDouble((UnionBranch branch) -> branch.directChildCenter)
            .thenComparing(branch -> branch.unit.id);
        List<UnionBranch> ordered = new ArrayList<>(branches);
        ordered.sort(order);
        for (int index = 0; index < ordered.size(); index++) {
            if (index < ordered.size() / 2) left.add(ordered.get(index));
            else right.add(ordered.get(index));
        }

        Map<UnionBranch, Float> result = new LinkedHashMap<>();
        putSideShifts(result, left, sharedCenter, -1);
        putSideShifts(result, right, sharedCenter, 1);
        if (!left.isEmpty() && !right.isEmpty()) {
            UnionBranch leftEdge = left.get(left.size() - 1);
            UnionBranch rightEdge = right.get(0);
            BranchContour placedLeft = leftEdge.contour.shifted(result.get(leftEdge));
            BranchContour placedRight = rightEdge.contour.shifted(result.get(rightEdge));
            float shortage = placedRight.requiredRightShiftFrom(placedLeft, familyGap);
            if (shortage > EPSILON) {
                float leftOutward = (float) Math.ceil(shortage / (2f * TreeLayoutEngine.GRID))
                    * TreeLayoutEngine.GRID;
                float rightOutward = (float) Math.ceil(
                    (shortage - leftOutward) / TreeLayoutEngine.GRID)
                    * TreeLayoutEngine.GRID;
                for (UnionBranch branch : left) {
                    result.put(branch, result.get(branch) - leftOutward);
                }
                for (UnionBranch branch : right) {
                    result.put(branch, result.get(branch) + rightOutward);
                }
            }
        }
        return result;
    }

    private void putSideShifts(
        Map<UnionBranch, Float> result,
        List<UnionBranch> branches,
        float sharedCenter,
        int side
    ) {
        if (branches.isEmpty()) return;
        float[] relative = packedShifts(branches);
        float commonShift;
        if (side > 0) {
            float nearest = Float.MAX_VALUE;
            for (int index = 0; index < branches.size(); index++) {
                nearest = Math.min(
                    nearest,
                    branches.get(index).directChildCenter + relative[index]);
            }
            commonShift = snap(
                sharedCenter + minimumUnionCenterOffset() - nearest);
        } else if (side < 0) {
            float nearest = -Float.MAX_VALUE;
            for (int index = 0; index < branches.size(); index++) {
                nearest = Math.max(
                    nearest,
                    branches.get(index).directChildCenter + relative[index]);
            }
            commonShift = snap(
                sharedCenter - minimumUnionCenterOffset() - nearest);
        } else {
            float packedLeft = Float.MAX_VALUE;
            float packedRight = -Float.MAX_VALUE;
            for (int index = 0; index < branches.size(); index++) {
                packedLeft = Math.min(
                    packedLeft,
                    branches.get(index).contour.left() + relative[index]);
                packedRight = Math.max(
                    packedRight,
                    branches.get(index).contour.right() + relative[index]);
            }
            commonShift = snap(sharedCenter - (packedLeft + packedRight) / 2f);
        }
        for (int index = 0; index < branches.size(); index++) {
            result.put(branches.get(index), snap(relative[index] + commonShift));
        }
    }

    private static float minimumUnionCenterOffset() {
        return (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID) / 2f;
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
        FamilyLayoutGraph.SiblingGroup siblingGroup =
            graph.siblingGroupByPerson.get(partnerId);
        if (siblingGroup != null) {
            for (String siblingId : siblingGroup.people) {
                if (!partnerId.equals(siblingId)) {
                    result.addAll(graph.descendantBranch(
                        Collections.singleton(siblingId)));
                }
            }
        }
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
