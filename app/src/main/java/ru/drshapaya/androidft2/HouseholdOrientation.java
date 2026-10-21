package ru.drshapaya.androidft2;

import java.util.*;

/** Orient ancestor-only multi-union households using the order of their children. */
final class HouseholdOrientation {
    private HouseholdOrientation() {}

    static boolean improve(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        Set<String> visited = new HashSet<>(); boolean changed = false;
        for (String id : graph.people.keySet()) {
            if (visited.contains(id)) continue;
            List<String> unit = new ArrayList<>(graph.partnerHousehold(Collections.singleton(id)));
            visited.addAll(unit);
            if (unit.size() < 3) continue;
            boolean fixed = false; float row = current.positionOf(id).y;
            for (String member : unit) fixed |= state.people.get(member).pinned || member.equals(state.rootId)
                || !graph.parentsByChild.getOrDefault(member, Collections.emptySet()).isEmpty()
                || graph.hasIndependentSibling(member) || current.positionOf(member).y != row;
            if (fixed) continue;
            LayoutSnapshot ordering = current;
            unit.sort(Comparator.comparingDouble(p -> ordering.positionOf(p).x));
            LayoutSnapshot candidate = current;
            for (int i = 0; i < unit.size(); i++) {
                String member = unit.get(i), opposite = unit.get(unit.size() - 1 - i);
                candidate = candidate.shifted(Collections.singleton(member), current.positionOf(opposite).x - current.positionOf(member).x, 0);
            }
            if (!constraints.validate(graph, candidate).isValid()
                || !TerminalSiblingBands.keepsOriginOrder(graph, current, candidate)) continue;
            if (cost(graph, candidate, unit) >= cost(graph, current, unit) - TreeLayoutEngine.GRID) continue;
            current = candidate; changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static double cost(FamilyLayoutGraph graph, LayoutSnapshot snapshot, Collection<String> members) {
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(); double cost = 0;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) if (!Collections.disjoint(members, family.parents)) {
            families.add(family);
            cost += Math.abs(FamilyAnchorProjection.average(family.parents, snapshot)
                - FamilyAnchorProjection.spanCenter(family.children, snapshot));
        }
        for (int i = 0; i < families.size(); i++) for (int j = i + 1; j < families.size(); j++) {
            FamilyLayoutGraph.ParentFamily a = families.get(i), b = families.get(j);
            float parents = FamilyAnchorProjection.average(a.parents, snapshot) - FamilyAnchorProjection.average(b.parents, snapshot);
            float children = FamilyAnchorProjection.spanCenter(a.children, snapshot) - FamilyAnchorProjection.spanCenter(b.children, snapshot);
            if (parents * children < -.5f) cost += TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 5;
        }
        return cost;
    }
}
