package ru.drshapaya.androidft2;

import java.util.*;

/** Keeps children of one parent family together while honoring a continuing
 * child's side toward the other parent's ancestry. */
final class FamilyChildBandCompaction {
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GAP = TreeLayoutEngine.GRID * 5;

    private FamilyChildBandCompaction() {}

    static boolean improve(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            List<String> children = new ArrayList<>(family.children);
            if (children.size() < 3) continue;
            boolean rootChild = false;
            for (String child : children)
                rootChild |= child.equals(state.rootId)
                    || graph.partnerHousehold(Collections.singleton(child)).contains(state.rootId);
            if (rootChild) continue;
            float row = current.positionOf(children.get(0)).y;
            boolean sameRow = true;
            for (String child : children) sameRow &= current.positionOf(child).y == row;
            if (!sameRow) continue;
            List<Set<String>> units = new ArrayList<>();
            for (String child : children) {
                Set<String> unit = graph.partnerHousehold(Collections.singleton(child));
                boolean unitSameRow = true;
                for (String member : unit) unitSameRow &= current.positionOf(member).y == row;
                if (!unitSameRow) { units.clear(); break; }
                if (unit.stream().anyMatch(id -> state.people.get(id).pinned || id.equals(state.rootId))) { units.clear(); break; }
                units.add(unit);
            }
            if (units.size() != children.size()) continue;
            List<Set<String>> ordered = new ArrayList<>(units);
            LayoutSnapshot ordering = current;
            ordered.sort(Comparator.comparingDouble(unit -> left(unit, ordering)));
            Set<Set<String>> rightSet = new HashSet<>(), leftSet = new HashSet<>();
            float parentCenter = FamilyAnchorProjection.average(family.parents, current);
            for (int i = 0; i < children.size(); i++) {
                String child = children.get(i);
                for (String peer : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
                    FamilyLayoutGraph.ParentFamily other = graph.parentFamilyOf(peer);
                    if (other == null || other == family) continue;
                    float otherCenter = FamilyAnchorProjection.average(other.parents, current);
                    if (otherCenter < parentCenter) rightSet.add(units.get(i));
                    else if (otherCenter > parentCenter) leftSet.add(units.get(i));
                }
            }
            ordered.removeAll(rightSet); ordered.removeAll(leftSet);
            List<Set<String>> finalOrder = new ArrayList<>(leftSet);
            finalOrder.sort(Comparator.comparingDouble(unit -> FamilyChildBandCompaction.left(unit, ordering)));
            finalOrder.addAll(ordered);
            List<Set<String>> rightOrder = new ArrayList<>(rightSet);
            rightOrder.sort(Comparator.comparingDouble(unit -> FamilyChildBandCompaction.left(unit, ordering)));
            finalOrder.addAll(rightOrder);
            float anchor = rightSet.isEmpty() ? left(finalOrder.get(0), current) : left(rightOrder.get(rightOrder.size() - 1), current);
            float cursor = anchor;
            Map<Set<String>, Float> targets = new HashMap<>();
            if (!rightSet.isEmpty()) {
                targets.put(rightOrder.get(rightOrder.size() - 1), anchor);
                cursor = anchor;
                for (int i = finalOrder.size() - 2; i >= 0; i--) {
                    Set<String> unit = finalOrder.get(i);
                    cursor -= width(unit, current) + GAP;
                    targets.put(unit, cursor);
                }
            } else {
                cursor = anchor;
                for (Set<String> unit : finalOrder) {
                    targets.put(unit, cursor);
                    cursor += width(unit, current) + GAP;
                }
            }
            LayoutSnapshot candidate = current;
            for (Set<String> unit : finalOrder) {
                Set<String> moving = graph.descendantBranch(unit);
                float delta = targets.get(unit) - left(unit, current);
                if (Math.abs(delta) < .5f) continue;
                candidate = candidate.shifted(moving, delta, 0);
            }
            if (candidate == current || !constraints.validate(graph, candidate).isValid()
                || !TerminalSiblingBands.keepsOriginOrder(graph, current, candidate, true)
                || !FamilyAnchorProjection.preservesExplicitAncestrySide(graph, current, candidate)
                || FamilyBandRepair.intrusions(graph, candidate, family.children) > FamilyBandRepair.intrusions(graph, current, family.children)) continue;
            float oldSpan = span(children, current), newSpan = span(children, candidate);
            if (newSpan >= oldSpan - GAP) continue;
            current = candidate; changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static float left(Collection<String> ids, LayoutSnapshot snapshot) {
        float result = Float.POSITIVE_INFINITY; for (String id : ids) result = Math.min(result, snapshot.positionOf(id).x); return result;
    }
    private static float width(Collection<String> ids, LayoutSnapshot snapshot) {
        float lo = left(ids, snapshot), hi = Float.NEGATIVE_INFINITY;
        for (String id : ids) hi = Math.max(hi, snapshot.positionOf(id).x + W);
        return hi - lo;
    }
    private static float span(Collection<String> ids, LayoutSnapshot snapshot) {
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (String id : ids) { lo = Math.min(lo, snapshot.positionOf(id).x); hi = Math.max(hi, snapshot.positionOf(id).x + W); }
        return hi - lo;
    }
}
