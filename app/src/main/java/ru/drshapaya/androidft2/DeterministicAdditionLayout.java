package ru.drshapaya.androidft2;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Repairs local family geometry through fixed constraints, without scoring layouts. */
final class DeterministicAdditionLayout {
    private DeterministicAdditionLayout() {}

    static void repair(
        TreeState state,
        Collection<String> addedIds,
        String action
    ) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        // One operation per distinct shared-parent family; these are independent
        // constraints, not competing complete layouts selected by a score.
        for (LayoutOperation operation : new MultiUnionFamilyCandidateGenerator(
            TreeLayoutEngine.GRID * 5f).generate(graph, current)) {
            if (movable(state, graph.rootId, operation.affectedIds())) {
                current = operation.apply(current);
            }
        }
        current = placeAncestryOutsidePartners(state, graph, current);
        current = separateOverlappingBranches(
            state, graph, current, addedIds, action);
        current.applyTo(state);
    }

    private static LayoutSnapshot separateOverlappingBranches(
        TreeState state,
        FamilyLayoutGraph graph,
        LayoutSnapshot current,
        Collection<String> addedIds,
        String action
    ) {
        LayoutConstraints constraints = new LayoutConstraints();
        int limit = Math.max(1, graph.people.size() * 2);
        for (int step = 0; step < limit; step++) {
            LayoutConstraints.Violation overlap = null;
            for (LayoutConstraints.Violation violation : constraints.validate(graph, current).violations) {
                if ("card-overlap".equals(violation.code)
                    || "card-spacing".equals(violation.code)) {
                    overlap = violation;
                    break;
                }
            }
            if (overlap == null) break;
            String movingId = addedIds != null && addedIds.contains(overlap.firstId)
                ? overlap.firstId : overlap.secondId;
            String fixedId = movingId.equals(overlap.firstId)
                ? overlap.secondId : overlap.firstId;
            Set<String> moving = collisionBranch(
                graph, movingId, fixedId, addedIds, action);
            if (!movable(state, graph.rootId, moving) || moving.contains(fixedId)) {
                movingId = fixedId;
                fixedId = movingId.equals(overlap.firstId)
                    ? overlap.secondId : overlap.firstId;
                moving = collisionBranch(graph, movingId, fixedId, addedIds, action);
            }
            if (!movable(state, graph.rootId, moving) || moving.contains(fixedId)) break;
            float shift = nearestCollisionFreeShift(current, moving);
            if (Math.abs(shift) < 0.5f) break;
            current = current.shifted(moving, shift, 0f);
        }
        return current;
    }

    private static Set<String> collisionBranch(
        FamilyLayoutGraph graph,
        String movingId,
        String fixedId,
        Collection<String> addedIds,
        String action
    ) {
        if ("parents".equals(action) && addedIds != null && addedIds.contains(movingId)) {
            return graph.lineageBranch(Collections.singleton(movingId));
        }
        Set<String> parents = graph.parentsByChild.getOrDefault(
            movingId, Collections.emptySet());
        if (!parents.isEmpty()) {
            Set<String> family = graph.descendantBranch(parents);
            if (!family.contains(graph.rootId) && !family.contains(fixedId)) return family;
        }
        return graph.outwardBranch(movingId);
    }

    /** Project zero onto the legal grid intervals for a rigid branch. */
    private static float nearestCollisionFreeShift(
        LayoutSnapshot snapshot,
        Set<String> movingIds
    ) {
        List<float[]> forbidden = new ArrayList<>();
        float leftmost = Float.MAX_VALUE;
        for (String movingId : movingIds) {
            LayoutSnapshot.Position moving = snapshot.positionOf(movingId);
            if (moving == null || !moving.isFinite()) continue;
            leftmost = Math.min(leftmost, moving.x);
            for (String otherId : snapshot.positions.keySet()) {
                if (movingIds.contains(otherId)) continue;
                LayoutSnapshot.Position other = snapshot.positionOf(otherId);
                if (other == null || !other.isFinite()
                    || moving.y >= other.y + TreeLayoutEngine.CARD_H
                    || other.y >= moving.y + TreeLayoutEngine.CARD_H) continue;
                forbidden.add(new float[] {
                    other.x - TreeLayoutEngine.GRID - moving.x - TreeLayoutEngine.CARD_W,
                    other.x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID - moving.x
                });
            }
        }
        if (forbidden.isEmpty()) return 0f;
        float left = 0f;
        float right = 0f;
        for (int pass = 0; pass <= forbidden.size(); pass++) {
            float nextLeft = left;
            float nextRight = right;
            for (float[] interval : forbidden) {
                if (interval[0] < left + 0.5f && left < interval[1] - 0.5f) {
                    nextLeft = Math.min(nextLeft,
                        (float) Math.floor(interval[0] / TreeLayoutEngine.GRID)
                            * TreeLayoutEngine.GRID);
                }
                if (interval[0] < right + 0.5f && right < interval[1] - 0.5f) {
                    nextRight = Math.max(nextRight, snapUp(interval[1]));
                }
            }
            if (nextLeft == left && nextRight == right) break;
            left = nextLeft;
            right = nextRight;
        }
        if (leftmost + left < 0f) return right;
        return Math.abs(left) < Math.abs(right) ? left : right;
    }

    private static LayoutSnapshot placeAncestryOutsidePartners(
        TreeState state,
        FamilyLayoutGraph graph,
        LayoutSnapshot current
    ) {
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnits) {
            if (unit.people.size() != 2) continue;
            List<String> pair = new ArrayList<>(unit.people);
            LayoutSnapshot ordering = current;
            pair.sort((first, second) -> {
                FamilyLayoutGraph.PersonNode a = graph.people.get(first);
                FamilyLayoutGraph.PersonNode b = graph.people.get(second);
                if (a != null && b != null && PersonGender.MALE.equals(a.gender)
                    && PersonGender.FEMALE.equals(b.gender)) return -1;
                if (a != null && b != null && PersonGender.FEMALE.equals(a.gender)
                    && PersonGender.MALE.equals(b.gender)) return 1;
                return Float.compare(
                    ordering.positionOf(first).x,
                    ordering.positionOf(second).x);
            });
            String leftId = pair.get(0);
            String rightId = pair.get(1);
            FamilyLayoutGraph.ParentFamily leftFamily = graph.parentFamilyOf(leftId);
            FamilyLayoutGraph.ParentFamily rightFamily = graph.parentFamilyOf(rightId);
            if (leftFamily == null || rightFamily == null || leftFamily == rightFamily) continue;
            Set<String> left = graph.lineageBranch(leftFamily.parents);
            Set<String> right = graph.lineageBranch(rightFamily.parents);
            if (!Collections.disjoint(left, right)) continue;
            LayoutSnapshot.Position leftMember = current.positionOf(leftId);
            LayoutSnapshot.Position rightMember = current.positionOf(rightId);
            if (leftMember == null || rightMember == null) continue;
            float leftShift = snapDown(AncestrySideRules.requiredLeftShift(
                BranchContour.from(current, left), leftMember));
            float rightShift = snapUp(AncestrySideRules.requiredRightShift(
                BranchContour.from(current, right), rightMember));
            if (leftShift < 0f && movable(state, graph.rootId, left)) {
                current = current.shifted(left, leftShift, 0f);
            }
            if (rightShift > 0f && movable(state, graph.rootId, right)) {
                current = current.shifted(right, rightShift, 0f);
            }
        }
        return current;
    }

    private static boolean movable(TreeState state, String rootId, Set<String> ids) {
        if (ids.isEmpty() || ids.contains(rootId)) return false;
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person != null && person.pinned) return false;
        }
        return true;
    }

    private static float snapUp(float value) {
        return value <= 0f ? 0f
            : (float) Math.ceil(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }

    private static float snapDown(float value) {
        return value >= 0f ? 0f
            : (float) Math.floor(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }

}
