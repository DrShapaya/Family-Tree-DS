package ru.drshapaya.androidft2;

import java.util.*;

/** Removes empty horizontal corridors between independent households. */
final class GenerationGapCompaction {
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GAP = TreeLayoutEngine.GRID * 5;

    private GenerationGapCompaction() {}

    static boolean improve(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        Map<Float, List<Set<String>>> rows = households(graph, current);
        Set<String> rootBand = rootBand(graph, stateRoot(state));
        boolean changed = false;
        for (int pass = 0; pass < 8; pass++) {
            boolean moved = false;
            for (List<Set<String>> row : rows.values()) {
                LayoutSnapshot ordering = current;
                row.sort(Comparator.comparingDouble(unit -> left(unit, ordering)));
                for (int i = 1; i < row.size(); i++) {
                    Set<String> leftUnit = row.get(i - 1), rightUnit = row.get(i);
                    if (leftUnit.size() == 1 && rightUnit.size() == 1
                        && sameParents(graph, leftUnit.iterator().next(), rightUnit.iterator().next())) continue;
                    float gap = left(rightUnit, current) - right(leftUnit, current);
                    if (gap <= GAP + .5f) continue;
                    Set<String> moving = graph.descendantBranch(rightUnit);
                    if (!Collections.disjoint(moving, rootBand)) continue;
                    boolean fixed = false;
                    for (String id : moving) fixed |= state.people.get(id).pinned || id.equals(state.rootId);
                    if (fixed) continue;
                    float delta = -((float)Math.floor((gap - GAP) / TreeLayoutEngine.GRID)) * TreeLayoutEngine.GRID;
                    LayoutSnapshot candidate = current.shifted(moving, delta, 0);
                    if (!constraints.validate(graph, candidate).isValid()
                        || !TerminalSiblingBands.keepsOriginOrder(graph, current, candidate, true)
                        || !FamilyAnchorProjection.preservesExplicitAncestrySide(graph, current, candidate)
                        || !keepsSettledFamilies(graph, current, candidate)
                        || !FamilyCohesion.preserved(graph, current, candidate)
                        || !TerminalRowSpacing.keepsBandSpacing(graph, current, candidate)) continue;
                    current = candidate; changed = moved = true;
                }
            }
            if (!moved) break;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static Map<Float, List<Set<String>>> households(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        Map<Float, List<Set<String>>> rows = new TreeMap<>(); Set<String> seen = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (!seen.add(id)) continue;
            Set<String> unit = graph.partnerHousehold(Collections.singleton(id));
            seen.addAll(unit);
            float y = snapshot.positionOf(id).y; boolean same = true;
            for (String member : unit) same &= snapshot.positionOf(member).y == y;
            if (same) rows.computeIfAbsent(y, key -> new ArrayList<>()).add(unit);
        }
        return rows;
    }

    private static boolean keepsSettledFamilies(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.children.size() < 2) continue;
            float old = FamilyBandRepair.intrusions(graph, before, family.children);
            float next = FamilyBandRepair.intrusions(graph, after, family.children);
            if (old == 0 && next > 0) return false;
        }
        return true;
    }

    private static float left(Collection<String> ids, LayoutSnapshot snapshot) {
        float value = Float.POSITIVE_INFINITY;
        for (String id : ids) value = Math.min(value, snapshot.positionOf(id).x);
        return value;
    }

    private static float right(Collection<String> ids, LayoutSnapshot snapshot) {
        float value = Float.NEGATIVE_INFINITY;
        for (String id : ids) value = Math.max(value, snapshot.positionOf(id).x + W);
        return value;
    }

    private static boolean sameParents(FamilyLayoutGraph graph, String a, String b) {
        Set<String> parents = graph.parentsByChild.getOrDefault(a, Collections.emptySet());
        return !parents.isEmpty() && parents.equals(graph.parentsByChild.getOrDefault(b, Collections.emptySet()));
    }

    private static String stateRoot(TreeState state) { return state.rootId; }

    private static Set<String> rootBand(FamilyLayoutGraph graph, String root) {
        Set<String> result = new LinkedHashSet<>();
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (!family.children.contains(root)) continue;
            for (String child : family.children) result.addAll(graph.partnerHousehold(Collections.singleton(child)));
        }
        return result;
    }
}
