package ru.drshapaya.androidft2;

import java.util.*;

/** Compare neighbouring household slots against both generations, before moving anything. */
final class AncestorHouseholdSlots {
    private AncestorHouseholdSlots() {}

    static boolean improve(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        Map<Float, List<Set<String>>> rows = new TreeMap<>(); Set<String> visited = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (visited.contains(id)) continue;
            Set<String> unit = graph.partnerHousehold(Collections.singleton(id)); visited.addAll(unit);
            boolean same = true; float row = current.positionOf(id).y;
            for (String member : unit) same &= current.positionOf(member).y == row;
            if (same) rows.computeIfAbsent(row, k -> new ArrayList<>()).add(unit);
        }
        boolean changed = false;
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;
            for (List<Set<String>> row : rows.values()) {
                if (row.size() > 96) continue;
                LayoutSnapshot order = current; row.sort(Comparator.comparingDouble(u -> left(u, order)));
                for (int i = row.size() - 2; i >= 0; i--) {
                    Set<String> a = row.get(i), b = row.get(i + 1);
                    if (right(a, current) > left(b, current)) continue;
                    Set<String> members = new HashSet<>(a); members.addAll(b);
                    boolean fixed = false;
                    for (String id : members) fixed |= id.equals(state.rootId) || state.people.get(id).pinned;
                    if (fixed) continue;
                    float da = right(b, current) - right(a, current), db = left(a, current) - left(b, current);
                    LayoutSnapshot candidate = current.shifted(a, da, 0).shifted(b, db, 0);
                    if (!constraints.validate(graph, candidate).isValid()
                        || !TerminalSiblingBands.keepsOriginOrder(graph, current, candidate, true)
                        || !FamilyAnchorProjection.preservesExplicitAncestrySide(graph, current, candidate)
                        || !preservesBands(graph, current, candidate)) continue;
                    if (FamilyBandRepair.quality(graph, candidate).cost() >= FamilyBandRepair.quality(graph, current).cost() - TreeLayoutEngine.GRID) continue;
                    current = candidate; row.set(i, b); row.set(i + 1, a); changed = moved = true;
                }
            }
            if (!moved) break;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static boolean preservesBands(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (FamilyBandRepair.intrusions(graph, before, family.children) == 0
                && FamilyBandRepair.intrusions(graph, after, family.children) > 0) return false;
            if (family.children.contains(graph.rootId)) {
                float old = Math.abs(FamilyAnchorProjection.average(family.parents, before) - before.positionOf(graph.rootId).x - 140);
                float next = Math.abs(FamilyAnchorProjection.average(family.parents, after) - after.positionOf(graph.rootId).x - 140);
                if (old <= 20 && next > 20) return false;
            }
        }
        return true;
    }
    private static float left(Collection<String> unit, LayoutSnapshot snapshot) {
        float value = Float.POSITIVE_INFINITY; for (String id : unit) value = Math.min(value, snapshot.positionOf(id).x); return value;
    }
    private static float right(Collection<String> unit, LayoutSnapshot snapshot) {
        float value = Float.NEGATIVE_INFINITY; for (String id : unit) value = Math.max(value, snapshot.positionOf(id).x + TreeLayoutEngine.CARD_W); return value;
    }
}
