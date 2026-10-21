package ru.drshapaya.androidft2;

import java.util.*;

/** Aligns the root's direct parent family without moving the root household. */
final class RootLineageAnchor {
    private RootLineageAnchor() {}

    static boolean improve(TreeState state) {
        if (state == null || state.rootId == null) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        FamilyLayoutGraph.ParentFamily family = graph.parentFamilyOf(state.rootId);
        if (family == null || family.parents.isEmpty()) return false;
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        float target = before.positionOf(state.rootId).x + TreeLayoutEngine.CARD_W / 2f;
        float parent = FamilyAnchorProjection.average(family.parents, before);
        float delta = Math.round((target - parent) / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
        if (Math.abs(delta) < TreeLayoutEngine.GRID / 2) return false;
        Set<String> moving = FamilyAnchorProjection.upperChain(graph, family.parents);
        boolean fixed = false;
        for (String id : moving) fixed |= state.people.get(id).pinned || id.equals(state.rootId);
        if (fixed) return false;
        moving = carryBlockingHouseholds(state, graph, before, moving, delta);
        LayoutSnapshot candidate = before.shifted(moving, delta, 0);
        if (!new LayoutConstraints().validate(graph, candidate).isValid()
            || !TerminalSiblingBands.keepsOriginOrder(graph, before, candidate)
            || !FamilyAnchorProjection.preservesExplicitAncestrySide(graph, before, candidate)) {
            // If an upper collateral branch blocks the complete chain, move
            // the direct parents as one couple; never move either parent alone.
            moving = new LinkedHashSet<>(family.parents);
            candidate = before.shifted(moving, delta, 0);
            if (!new LayoutConstraints().validate(graph, candidate).isValid()
                || !TerminalSiblingBands.keepsOriginOrder(graph, before, candidate)) return false;
        }
        candidate.applyTo(state);
        return true;
    }

    private static Set<String> carryBlockingHouseholds(TreeState state, FamilyLayoutGraph graph,
        LayoutSnapshot snapshot, Set<String> moving, float delta) {
        Set<String> result = new LinkedHashSet<>(moving);
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        float row = snapshot.positionOf(moving.iterator().next()).y;
        for (String id : moving) { lo = Math.min(lo, snapshot.positionOf(id).x); hi = Math.max(hi, snapshot.positionOf(id).x + TreeLayoutEngine.CARD_W); }
        if (delta <= 0) return result;
        for (String id : graph.people.keySet()) {
            if (result.contains(id) || snapshot.positionOf(id).y != row) continue;
            float x = snapshot.positionOf(id).x;
            if (x + TreeLayoutEngine.CARD_W <= hi || x >= hi + delta) continue;
            Set<String> unit = graph.descendantBranch(graph.partnerHousehold(Collections.singleton(id)));
            boolean fixed = false;
            for (String member : unit) fixed |= state.people.get(member).pinned || member.equals(state.rootId);
            if (!fixed) result.addAll(unit);
        }
        return result;
    }
}
