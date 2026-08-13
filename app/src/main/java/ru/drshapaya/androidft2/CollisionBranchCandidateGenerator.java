package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves card collisions by moving a semantic branch as one rigid block. The generator
 * deliberately emits alternatives in both directions; the scorer decides which alternative
 * preserves the root and the rest of the tree best.
 */
final class CollisionBranchCandidateGenerator implements LayoutCandidateGenerator {
    private static final float EPSILON = 0.5f;

    private final LayoutConstraints constraints;
    private final float minimumGap;

    CollisionBranchCandidateGenerator(LayoutConstraints constraints, float minimumGap) {
        this.constraints = constraints == null ? new LayoutConstraints() : constraints;
        this.minimumGap = Math.max(0f, minimumGap);
    }

    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        Set<String> fingerprints = new HashSet<>();
        LayoutConstraints.ValidationResult validation = constraints.validate(graph, snapshot);
        for (LayoutConstraints.Violation violation : validation.violations) {
            if (!"card-overlap".equals(violation.code)) continue;
            addMoves(result, fingerprints, graph, snapshot, violation.firstId, violation.secondId);
            addMoves(result, fingerprints, graph, snapshot, violation.secondId, violation.firstId);
        }
        return result;
    }

    private void addMoves(
        List<LayoutOperation> result,
        Set<String> fingerprints,
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        String movingPersonId,
        String fixedPersonId
    ) {
        LinkedHashSet<String> movingIds = new LinkedHashSet<>(
            graph.outwardBranch(movingPersonId));
        LinkedHashSet<String> fixedIds = new LinkedHashSet<>(
            graph.outwardBranch(fixedPersonId));
        if (movingIds.isEmpty() || movingIds.contains(graph.rootId)) return;

        fixedIds.removeAll(movingIds);
        if (fixedIds.isEmpty()) fixedIds.add(fixedPersonId);
        if (movingIds.contains(fixedPersonId)) return;

        BranchContour moving = BranchContour.from(snapshot, movingIds);
        BranchContour fixed = BranchContour.from(snapshot, fixedIds);
        float leftDistance = fixed.requiredRightShiftFrom(moving, minimumGap);
        float rightDistance = moving.requiredRightShiftFrom(fixed, minimumGap);

        addShift(result, fingerprints, movingIds, -snapOutward(leftDistance), "collision-left");
        addShift(result, fingerprints, movingIds, snapOutward(rightDistance), "collision-right");
    }

    private static void addShift(
        List<LayoutOperation> result,
        Set<String> fingerprints,
        Set<String> ids,
        float dx,
        String reason
    ) {
        if (!Float.isFinite(dx) || Math.abs(dx) < EPSILON) return;
        String fingerprint = new java.util.TreeSet<>(ids) + "@" + Math.round(dx);
        if (!fingerprints.add(fingerprint)) return;
        result.add(new ShiftLayoutOperation(ids, dx, 0f, reason));
    }

    private static float snapOutward(float distance) {
        if (!Float.isFinite(distance) || distance <= EPSILON) return 0f;
        return (float) Math.ceil(distance / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }
}
