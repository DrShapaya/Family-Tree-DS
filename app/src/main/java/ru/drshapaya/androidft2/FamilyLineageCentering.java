package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

/** Moves a whole parent lineage toward its children's span without crushing neighbouring branches. */
final class FamilyLineageCentering {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float CARD_W = TreeLayoutEngine.CARD_W;
    private static final float CARD_H = TreeLayoutEngine.CARD_H;
    private static final float BRANCH_GAP = GRID * 5f;

    private FamilyLineageCentering() {}

    static boolean improve(TreeState state, Collection<String> affectedIds) {
        if (state == null || state.people.size() < 3) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        boolean changed = false;
        for (int pass = 0; pass < 2; pass++) {
            List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
            LayoutSnapshot ordering = current;
            families.sort(Comparator.comparingDouble(
                (FamilyLayoutGraph.ParentFamily family) -> Math.abs(offset(family, ordering)))
                .reversed());
            boolean passChanged = false;
            for (FamilyLayoutGraph.ParentFamily family : families) {
                if (family.parents.isEmpty() || family.children.isEmpty()) continue;
                if (affectedIds != null && !affectedIds.isEmpty()
                    && (family.parents.size() != 2 || family.children.size() < 2)) continue;
                if (affectedIds != null && !affectedIds.isEmpty()
                    && Collections.disjoint(family.parents, affectedIds)
                    && Collections.disjoint(family.children, affectedIds)) continue;
                float desired = snap(offset(family, current));
                if (Math.abs(desired) < GRID - 0.5f) continue;
                Set<String> ancestry = graph.ancestryBranch(family.parents);
                // All three available moves include ancestry. If that ancestry
                // contains the fixed root, none can be accepted; avoid building
                // every collateral descendant set just to reject it afterwards.
                if (!movableLineage(state, family, ancestry)) continue;
                Set<String> wholeBranch = graph.lineageBranch(family.parents);
                Set<String> moving = movableLineage(state, family, wholeBranch)
                    ? wholeBranch : Collections.emptySet();
                Map<Integer, List<String>> occupiedRows = occupiedRows(current);
                float shift = moving.isEmpty() ? 0f : fittedShift(current, occupiedRows, moving, desired);
                if (Math.abs(shift) < Math.abs(desired) - 0.5f) {
                    Set<String> trailingBranch = directionalLineage(
                        graph, current, family.parents, desired);
                    if (movableLineage(state, family, trailingBranch)) {
                        float trailingShift = fittedShift(current, occupiedRows, trailingBranch, desired);
                        if (Math.abs(trailingShift) > Math.abs(shift) + 0.5f) {
                            moving = trailingBranch;
                            shift = trailingShift;
                        }
                    }
                }
                if (Math.abs(shift) < Math.abs(desired) - 0.5f) {
                    if (movableLineage(state, family, ancestry)) {
                        float ancestryShift = fittedShift(current, occupiedRows, ancestry, desired);
                        if (Math.abs(ancestryShift) > Math.abs(shift) + 0.5f) {
                            LayoutSnapshot moved = current.shifted(ancestry, ancestryShift, 0f);
                            if (doesNotWorsenOtherFamilies(graph, current, moved, family)) {
                                moving = ancestry;
                                shift = ancestryShift;
                            }
                        }
                    }
                }
                if (Math.abs(shift) < GRID - 0.5f) continue;
                LayoutSnapshot candidate = current.shifted(moving, shift, 0f);
                if (!constraints.validate(graph, candidate).isValid()
                    || Math.abs(offset(family, candidate))
                        >= Math.abs(offset(family, current)) - 0.5f) continue;
                current = candidate;
                passChanged = true;
                changed = true;
            }
            if (!passChanged) break;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static boolean doesNotWorsenOtherFamilies(FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot after, FamilyLayoutGraph.ParentFamily target) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family == target || family.parents.isEmpty() || family.children.isEmpty()) continue;
            if (Math.abs(offset(family, after)) > Math.abs(offset(family, before)) + GRID / 2f) {
                return false;
            }
        }
        return true;
    }

    private static boolean movableLineage(
        TreeState state,
        FamilyLayoutGraph.ParentFamily family,
        Set<String> lineage
    ) {
        if (lineage.isEmpty() || lineage.contains(state.rootId)
            || !Collections.disjoint(lineage, family.children)) return false;
        for (String id : lineage) {
            Person person = state.people.get(id);
            if (person != null && person.pinned) return false;
        }
        return true;
    }

    /** Move ancestry and only the collateral branches trailing the desired direction. */
    private static Set<String> directionalLineage(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        Collection<String> parents,
        float desired
    ) {
        LinkedHashSet<String> lineage = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(parents);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!graph.people.containsKey(id) || !lineage.add(id)) continue;
            queue.addAll(graph.partnersByPerson.getOrDefault(id, Collections.emptySet()));
            queue.addAll(graph.parentsByChild.getOrDefault(id, Collections.emptySet()));
            LayoutSnapshot.Position current = snapshot.positionOf(id);
            FamilyLayoutGraph.SiblingGroup siblings = graph.siblingGroupByPerson.get(id);
            if (current == null || siblings == null) continue;
            for (String siblingId : siblings.people) {
                if (siblingId.equals(id)) continue;
                LayoutSnapshot.Position sibling = snapshot.positionOf(siblingId);
                if (sibling == null || !sibling.isFinite()) continue;
                if ((desired > 0f && sibling.x < current.x - 0.5f)
                    || (desired < 0f && sibling.x > current.x + 0.5f)) {
                    lineage.addAll(graph.descendantBranch(Collections.singleton(siblingId)));
                }
            }
        }
        return lineage;
    }

    private static Map<Integer, List<String>> occupiedRows(LayoutSnapshot snapshot) {
        Map<Integer, List<String>> rows = new TreeMap<>();
        for (Map.Entry<String, LayoutSnapshot.Position> entry : snapshot.positions.entrySet()) {
            LayoutSnapshot.Position position = entry.getValue();
            if (position != null && position.isFinite()) {
                rows.computeIfAbsent(Math.round(position.y / GRID), ignored -> new ArrayList<>())
                    .add(entry.getKey());
            }
        }
        return rows;
    }

    private static float fittedShift(LayoutSnapshot snapshot, Map<Integer, List<String>> rows,
                                     Set<String> lineage, float desired) {
        float available = Math.abs(desired);
        boolean right = desired > 0f;
        for (String id : lineage) {
            LayoutSnapshot.Position moving = snapshot.positionOf(id);
            if (moving == null || !moving.isFinite()) return 0f;
            if (!right) available = Math.min(available, moving.x);
            // Compare only vertically overlapping rows. Ancestor branches in
            // a deep GEDCOM no longer scan every card in every generation.
            int firstRow = (int) Math.floor((moving.y - CARD_H) / GRID) + 1;
            int lastRow = (int) Math.ceil((moving.y + CARD_H) / GRID) - 1;
            for (int row = firstRow; row <= lastRow; row++) {
              for (String otherId : rows.getOrDefault(row, Collections.emptyList())) {
                if (lineage.contains(otherId)) continue;
                LayoutSnapshot.Position other = snapshot.positionOf(otherId);
                if (other == null || !other.isFinite()
                    || moving.y >= other.y + CARD_H || other.y >= moving.y + CARD_H) continue;
                float gap = right
                    ? other.x - moving.x - CARD_W
                    : moving.x - other.x - CARD_W;
                if (gap < -0.5f) continue;
                available = Math.min(available, gap - Math.min(BRANCH_GAP, gap));
              }
            }
        }
        float steps = (float) Math.floor((available + 0.5f) / GRID);
        return Math.copySign(Math.min(Math.abs(desired), steps * GRID), desired);
    }

    private static float offset(FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot) {
        float parentSum = 0f;
        int parents = 0;
        for (String id : family.parents) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            parentSum += position.x + CARD_W / 2f;
            parents++;
        }
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : family.children) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null || !position.isFinite()) continue;
            left = Math.min(left, position.x);
            right = Math.max(right, position.x + CARD_W);
        }
        if (parents == 0 || left == Float.MAX_VALUE) return 0f;
        return (left + right) / 2f - parentSum / parents;
    }

    private static float snap(float value) {
        return Math.round(value / GRID) * GRID;
    }
}
