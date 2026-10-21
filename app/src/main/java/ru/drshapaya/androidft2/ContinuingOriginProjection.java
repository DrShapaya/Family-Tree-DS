package ru.drshapaya.androidft2;

import java.util.*;

/** Put a continuing household on the sibling band's edge facing its other origin. */
final class ContinuingOriginProjection {
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float GAP = GRID * 5;

    private ContinuingOriginProjection() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, current).isValid()) return false;
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily origin : graph.parentFamilies) {
            List<String> band = leaves(graph, origin);
            if (band.size() < 2 || origin.children.size() != band.size() + 1) continue;
            String child = null;
            for (String id : origin.children) if (!band.contains(id)) child = id;
            Set<String> household = household(graph, child);
            if (household.size() != 2) continue;
            String spouse = null;
            for (String id : household) if (!id.equals(child)) spouse = id;
            FamilyLayoutGraph.ParentFamily other = graph.parentFamilyOf(spouse);
            if (other == null || !Collections.disjoint(origin.parents, other.parents)) continue;
            List<String> otherLeaves = leaves(graph, other);
            if (other.children.size() != otherLeaves.size() + 1 || otherLeaves.size() >= band.size()) continue;
            float lo = left(band, current), hi = right(band, current);
            float parentCenter = FamilyAnchorProjection.average(origin.parents, current);
            if (hi - lo != band.size() * (W + GRID) - GRID || parentCenter < lo || parentCenter > hi) continue;
            float otherCenter = FamilyAnchorProjection.average(other.parents, current);
            boolean toRight = otherCenter > parentCenter;
            if ((current.positionOf(child).x < current.positionOf(spouse).x) != toRight) continue;
            float householdLeft = left(household, current), householdRight = right(household, current);
            if (toRight ? householdRight > lo : householdLeft < hi) continue;
            float delta = toRight ? hi + GAP - householdLeft : lo - GAP - householdRight;
            Set<String> selected = new LinkedHashSet<>(household); selected.addAll(otherLeaves);
            Set<String> fixed = new LinkedHashSet<>();
            float row = current.positionOf(child).y;
            for (String id : graph.people.keySet())
                if (current.positionOf(id).y == row && !selected.contains(id)) fixed.add(id);
            Set<String> moving = graph.descendantBranch(selected);
            Set<String> shared = graph.descendantBranch(fixed); shared.removeAll(selected);
            moving.removeAll(shared);
            if (fixed(state, moving)) continue;
            LayoutSnapshot candidate = current.shifted(moving, delta, 0);
            // Moving the connector and its other free siblings changes the other
            // origin's anchor. Carry that exclusive upper chain in the same transaction.
            Set<String> upper = FamilyAnchorProjection.upperChain(graph, other.parents);
            if (!Collections.disjoint(upper, moving) || fixed(state, upper)) continue;
            float target = FamilyAnchorProjection.spanCenter(other.children, candidate);
            float edge = toRight ? right(household, candidate) + GRID : left(household, candidate) - GRID;
            target = toRight ? Math.max(target, edge) : Math.min(target, edge);
            float shift = Math.round((target - otherCenter) / GRID) * GRID;
            candidate = candidate.shifted(upper, shift, 0);
            if (!constraints.validate(graph, candidate).isValid()
                || !TerminalSiblingBands.keepsOriginOrder(graph, current, candidate)
                || !FamilyAnchorProjection.preservesRowGaps(graph, current, candidate)
                || error(graph, candidate) >= error(graph, current) - GRID / 2) continue;
            current = candidate; changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static double error(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        double result = 0;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            float anchor = FamilyAnchorProjection.average(family.parents, snapshot);
            float distance = Math.abs(FamilyAnchorProjection.spanCenter(family.children, snapshot) - anchor);
            List<String> leaves = leaves(graph, family);
            if (leaves.size() >= 2 && family.children.size() == leaves.size() + 1)
                distance = Math.min(distance, Math.abs(FamilyAnchorProjection.spanCenter(leaves, snapshot) - anchor));
            result += distance;
        }
        return result;
    }

    private static boolean fixed(TreeState state, Set<String> ids) {
        for (String id : ids) if (state.people.get(id).pinned || id.equals(state.rootId)) return true;
        return false;
    }

    private static List<String> leaves(FamilyLayoutGraph graph, FamilyLayoutGraph.ParentFamily family) {
        List<String> result = new ArrayList<>();
        for (String id : family.children)
            if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()
                && !graph.hasIndependentSibling(id)) result.add(id);
        return result;
    }

    private static Set<String> household(FamilyLayoutGraph graph, String id) {
        Set<String> result = new LinkedHashSet<>(); result.add(id);
        for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(id)) result.addAll(unit.people);
        return result;
    }
    private static float left(Collection<String> ids, LayoutSnapshot snapshot) {
        float x = Float.POSITIVE_INFINITY;
        for (String id : ids) x = Math.min(x, snapshot.positionOf(id).x);
        return x;
    }
    private static float right(Collection<String> ids, LayoutSnapshot snapshot) {
        float x = Float.NEGATIVE_INFINITY;
        for (String id : ids) x = Math.max(x, snapshot.positionOf(id).x + W);
        return x;
    }
}
