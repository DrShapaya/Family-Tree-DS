package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Keeps each partner's complete ancestry on its outward side. */
final class AncestrySideCandidateGenerator implements LayoutCandidateGenerator {
    private final float branchGap;

    AncestrySideCandidateGenerator(float branchGap) {
        this.branchGap = Math.max(0f, branchGap);
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            if (unit.people.size() != 2) continue;
            List<String> members = new ArrayList<>(unit.people);
            members.sort(Comparator
                .comparingDouble((String id) -> center(snapshot.positionOf(id)))
                .thenComparing(id -> id));
            String leftPersonId = members.get(0);
            String rightPersonId = members.get(1);
            FamilyLayoutGraph.ParentFamily leftFamily = graph.parentFamilyOf(leftPersonId);
            FamilyLayoutGraph.ParentFamily rightFamily = graph.parentFamilyOf(rightPersonId);
            if (leftFamily == null || rightFamily == null || leftFamily == rightFamily) continue;

            Set<String> leftIds = graph.lineageBranch(leftFamily.parents);
            Set<String> rightIds = graph.lineageBranch(rightFamily.parents);
            if (leftIds.isEmpty()
                || rightIds.isEmpty()
                || !Collections.disjoint(leftIds, rightIds)) continue;
            BranchContour left = BranchContour.from(snapshot, leftIds);
            BranchContour right = BranchContour.from(snapshot, rightIds);
            LayoutSnapshot.Position leftPerson = snapshot.positionOf(leftPersonId);
            LayoutSnapshot.Position rightPerson = snapshot.positionOf(rightPersonId);
            if (leftPerson == null || rightPerson == null) continue;

            float leftShift = snapDown(AncestrySideRules.requiredLeftShift(left, leftPerson));
            float rightShift = snapUp(AncestrySideRules.requiredRightShift(right, rightPerson));
            if (leftShift < -0.5f) {
                result.add(new ShiftLayoutOperation(
                    leftIds,
                    leftShift,
                    0f,
                    "ancestry-outward-left:" + unit.id));
            } else {
                leftShift = 0f;
            }
            if (rightShift > 0.5f) {
                result.add(new ShiftLayoutOperation(
                    rightIds,
                    rightShift,
                    0f,
                    "ancestry-outward-right:" + unit.id));
            } else {
                rightShift = 0f;
            }
            if (leftShift < -0.5f && rightShift > 0.5f) {
                result.add(new CompositeLayoutOperation(Arrays.asList(
                    new ShiftLayoutOperation(leftIds, leftShift, 0f, "ancestry-left"),
                    new ShiftLayoutOperation(rightIds, rightShift, 0f, "ancestry-right")),
                    "ancestry-outward-both:" + unit.id));
            }

            BranchContour outwardLeft = left.shifted(leftShift);
            BranchContour outwardRight = right.shifted(rightShift);
            float gapDeficit = outwardRight.requiredRightShiftFrom(outwardLeft, branchGap);
            if (gapDeficit > 0.5f) {
                float moveLeft = -snapUp(gapDeficit / 2f);
                float moveRight = snapUp(gapDeficit + moveLeft);
                result.add(new ShiftLayoutOperation(
                    leftIds,
                    leftShift - snapUp(gapDeficit),
                    0f,
                    "ancestry-gap-left:" + unit.id));
                result.add(new ShiftLayoutOperation(
                    rightIds,
                    rightShift + snapUp(gapDeficit),
                    0f,
                    "ancestry-gap-right:" + unit.id));
                if (Math.abs(moveLeft) > 0.5f && Math.abs(moveRight) > 0.5f) {
                    result.add(new CompositeLayoutOperation(Arrays.asList(
                        new ShiftLayoutOperation(
                            leftIds,
                            leftShift + moveLeft,
                            0f,
                            "ancestry-gap-split-left"),
                        new ShiftLayoutOperation(
                            rightIds,
                            rightShift + moveRight,
                            0f,
                            "ancestry-gap-split-right")),
                        "ancestry-gap-split:" + unit.id));
                }
            }
        }
        return result;
    }

    private static double center(LayoutSnapshot.Position position) {
        return position == null ? Double.POSITIVE_INFINITY : position.x;
    }

    private static float snapUp(float value) {
        if (value <= 0f) return 0f;
        return (float) Math.ceil(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }

    private static float snapDown(float value) {
        if (value >= 0f) return 0f;
        return (float) Math.floor(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }
}
