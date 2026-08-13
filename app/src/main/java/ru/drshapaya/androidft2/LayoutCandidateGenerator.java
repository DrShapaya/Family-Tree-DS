package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

interface LayoutCandidateGenerator {
    List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot);
}

/** Generates a centered, equally separated candidate for two isomorphic ancestry branches. */
final class SymmetricBranchCandidateGenerator implements LayoutCandidateGenerator {
    private final float branchGap;

    SymmetricBranchCandidateGenerator(float branchGap) {
        this.branchGap = Math.max(0f, branchGap);
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            if (unit.people.size() != 2) continue;
            List<String> members = new ArrayList<>(unit.people);
            String firstId = members.get(0);
            String secondId = members.get(1);
            if (!graph.structurallyMirrored(firstId, secondId)) continue;
            FamilyLayoutGraph.ParentFamily firstFamily = graph.parentFamilyOf(firstId);
            FamilyLayoutGraph.ParentFamily secondFamily = graph.parentFamilyOf(secondId);
            if (firstFamily == null || secondFamily == null || firstFamily == secondFamily) continue;

            Set<String> firstBranch = graph.ancestryBranch(firstFamily.parents);
            Set<String> secondBranch = graph.ancestryBranch(secondFamily.parents);
            if (firstBranch.isEmpty() || secondBranch.isEmpty()) continue;
            BranchContour firstContour = BranchContour.from(snapshot, firstBranch);
            BranchContour secondContour = BranchContour.from(snapshot, secondBranch);
            if (!firstContour.structurallyMatches(secondContour)) continue;

            boolean firstIsLeft = center(firstContour) <= center(secondContour);
            Set<String> leftIds = firstIsLeft ? firstBranch : secondBranch;
            Set<String> rightIds = firstIsLeft ? secondBranch : firstBranch;
            BranchContour left = firstIsLeft ? firstContour : secondContour;
            BranchContour right = firstIsLeft ? secondContour : firstContour;
            double axis = averageCenter(unit.people, snapshot);
            if (!Double.isFinite(axis)) continue;

            float commonShift = snap((float) (axis - (center(left) + center(right)) / 2d));
            BranchContour centeredLeft = left.shifted(commonShift);
            BranchContour centeredRight = right.shifted(commonShift);
            float deficit = centeredRight.requiredRightShiftFrom(centeredLeft, branchGap);
            float leftExtra = deficit <= 0f ? 0f : -snap(deficit / 2f);
            float rightExtra = deficit <= 0f ? 0f : snap(deficit + leftExtra);
            float leftShift = commonShift + leftExtra;
            float rightShift = commonShift + rightExtra;
            if (Math.abs(leftShift) < 0.5f && Math.abs(rightShift) < 0.5f) continue;

            result.add(new CompositeLayoutOperation(Arrays.asList(
                new ShiftLayoutOperation(leftIds, leftShift, 0f, "mirror-left"),
                new ShiftLayoutOperation(rightIds, rightShift, 0f, "mirror-right")),
                "mirror-ancestry:" + unit.id));
        }
        return result;
    }

    private static float center(BranchContour contour) {
        return (contour.left() + contour.right()) / 2f;
    }

    private static double averageCenter(Iterable<String> ids, LayoutSnapshot snapshot) {
        double sum = 0d;
        int count = 0;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            sum += position.x + TreeLayoutEngine.CARD_W / 2d;
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static float snap(float value) {
        return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }
}
