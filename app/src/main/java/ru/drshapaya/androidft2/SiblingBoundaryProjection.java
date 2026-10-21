package ru.drshapaya.androidft2;

import java.util.*;

/** Keeps terminal runs at a household boundary, including a single collateral sibling. */
final class SiblingBoundaryProjection {
    private SiblingBoundaryProjection() {}

    static boolean improve(TreeState state) { return improve(state, false); }

    static boolean compactCollateral(TreeState state) { return improve(state, true); }

    private static boolean improve(TreeState state, boolean compact) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (compact) {
                boolean rootFamily = false;
                for (String child : family.children)
                    rootFamily |= child.equals(state.rootId)
                        || graph.partnerHousehold(Collections.singleton(child)).contains(state.rootId);
                if (rootFamily) continue;
            }
            List<String> leaves = new ArrayList<>();
            Set<String> households = new LinkedHashSet<>();
            for (String child : family.children) {
                if (graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()) {
                    if (!state.people.get(child).pinned && !child.equals(state.rootId)
                        && !graph.hasIndependentSibling(child)) leaves.add(child);
                } else households.add(child);
            }
            if (leaves.isEmpty() || households.isEmpty() || (compact && households.size() != 1)) continue;
            LayoutSnapshot ordering = current;
            leaves.sort(Comparator.comparingDouble(id -> ordering.positionOf(id).x));
            float parent = FamilyAnchorProjection.average(family.parents, current);
            if (!compact && leaves.size() == 1 && Math.abs(current.positionOf(leaves.get(0)).x + 140 - parent) <= 20) continue;
            float left = current.positionOf(leaves.get(0)).x;
            float right = current.positionOf(leaves.get(leaves.size() - 1)).x + 280;
            float low = Float.NEGATIVE_INFINITY, high = Float.POSITIVE_INFINITY;
            for (String child : households) {
                for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(child)) {
                    if (unit.people.size() < 2) continue;
                    float a = Float.POSITIVE_INFINITY, b = Float.NEGATIVE_INFINITY;
                    for (String id : unit.people) { float x = current.positionOf(id).x; a = Math.min(a, x); b = Math.max(b, x); }
                    float x = current.positionOf(child).x;
                    if (leaves.size() == 1) {
                        if (x == b) low = Math.max(low, b + 280 + 200);
                        if (x == a) high = Math.min(high, a - 200 - 280);
                    } else if (Math.abs((left + right) / 2 - parent) > 20) {
                        if (right <= a) high = Math.min(high, a - 200 - (right - left));
                        if (left >= b + 280) low = Math.max(low, b + 280 + 200);
                    }
                }
            }
            float x = left;
            if (Float.isFinite(low)) x = leaves.size() == 1 ? low : Math.max(x, low);
            if (Float.isFinite(high)) {
                if (leaves.size() > 1 && high > left && high + (right - left) / 2 <= parent + 20) x = high;
                else x = Math.min(x, high);
            }
            if (low > high || Math.abs(x - left) < 20) continue;
            LayoutSnapshot candidate = current.shifted(leaves, x - left, 0);
            if (!constraints.validate(graph, candidate).isValid() && compact) {
                float width = right - left; float best = Float.POSITIVE_INFINITY;
                LayoutSnapshot alternative = null;
                Set<Float> positions = new LinkedHashSet<>(); positions.add(x);
                for (String other : graph.people.keySet()) {
                    if (leaves.contains(other) || current.positionOf(other).y != current.positionOf(leaves.get(0)).y) continue;
                    positions.add(current.positionOf(other).x + 480);
                    positions.add(current.positionOf(other).x - 200 - width);
                }
                for (float option : positions) {
                    if (option < low || option > high || Math.abs(option - x) >= best) continue;
                    LayoutSnapshot placed = current.shifted(leaves, option - left, 0);
                    if (constraints.validate(graph, placed).isValid() && TerminalSiblingBands.keepsOriginOrder(graph, current, placed)) {
                        alternative = placed; best = Math.abs(option - x);
                    }
                }
                if (alternative != null) candidate = alternative;
            }
            if (!constraints.validate(graph, candidate).isValid()) continue;
            current = candidate; changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }
}
