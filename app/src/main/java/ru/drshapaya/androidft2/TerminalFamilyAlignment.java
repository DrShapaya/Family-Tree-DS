package ru.drshapaya.androidft2;

import java.util.*;

/** Aligns a terminal child family with its own parent household after the
 * contour passes have settled.  It moves complete households or descendant
 * contours, never individual cards, and accepts only hard-valid candidates. */
final class TerminalFamilyAlignment {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float W = TreeLayoutEngine.CARD_W;
    private TerminalFamilyAlignment() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.size() < 2) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, current).isValid()) return false;
        boolean changed = false;
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
        final LayoutSnapshot ordering = current;
        families.sort(Comparator.comparingDouble(f -> ordering.positionOf(f.parents.get(0)).y));
        for (FamilyLayoutGraph.ParentFamily family : families) {
            if (family.parents.size() < 2 || family.children.isEmpty()) continue;
            if (!sameRow(family.parents, current) || !sameRow(family.children, current)) continue;
            List<String> terminalChildren = new ArrayList<>();
            boolean allTerminal = true;
            for (String child : family.children) {
                boolean terminal = graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty();
                if (terminal) terminalChildren.add(child);
                else allTerminal = false;
            }
            if (terminalChildren.isEmpty()) continue;
            float parentCenter = center(family.parents, current);
            float childCenter = center(terminalChildren, current);
            float delta = Math.round((childCenter - parentCenter) / GRID) * GRID;
            if (Math.abs(delta) < GRID) continue;
            Set<String> parentUnit = graph.partnerHousehold(family.parents);
            Set<String> childUnit = graph.descendantBranch(family.children);
            LayoutSnapshot best = current;
            float bestScore = Math.abs(parentCenter - childCenter);
            List<Set<String>> candidates = new ArrayList<>();
            // A continuing child branch may contain the root or a pinned
            // junction.  That must only disqualify moving that child branch;
            // the parents can still be projected toward an independent leaf.
            if (!fixed(state, parentUnit)) candidates.add(parentUnit);
            if (allTerminal && !fixed(state, childUnit)) candidates.add(childUnit);
            if (candidates.isEmpty()) continue;
            for (Set<String> moving : candidates) {
                float shift = moving == parentUnit ? delta : -delta;
                for (int step = 0; Math.abs(shift) >= GRID / 2; step++, shift -= Math.copySign(GRID, shift)) {
                    LayoutSnapshot candidate = current.shifted(moving, shift, 0);
                    if (!constraints.validate(graph, candidate).isValid()) continue;
                    if (!preservesLocalParentSides(graph, family, current, candidate)) continue;
                    // Centering must not buy alignment by pressing a household
                    // against a neighbouring family band.
                    if (!TerminalRowSpacing.keepsBandSpacing(graph, current, candidate)) continue;
                    // Terminal children have no continuing partner contour;
                    // their complete parent household may use any valid slot
                    // in the same generation row.  The hard validator still
                    // rejects overlaps and generation violations.
                    float score = Math.abs(center(family.parents, candidate) - center(family.children, candidate));
                    if (score + GRID / 2 < bestScore) { best = candidate; bestScore = score; }
                }
            }
            if (best != current) { current = best; changed = true; }
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static boolean sameRow(Collection<String> ids, LayoutSnapshot s) {
        float y = s.positionOf(ids.iterator().next()).y;
        for (String id : ids) if (s.positionOf(id).y != y) return false;
        return true;
    }
    private static boolean fixed(TreeState state, Collection<String> ids) {
        for (String id : ids) if (state.people.get(id).pinned || id.equals(state.rootId)) return true;
        return false;
    }
    private static float center(Collection<String> ids, LayoutSnapshot s) {
        float sum = 0; for (String id : ids) sum += s.positionOf(id).x + W / 2; return sum / ids.size();
    }
    private static boolean preservesLocalParentSides(FamilyLayoutGraph graph,
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot before, LayoutSnapshot after) {
        float oldParent = center(family.parents, before), newParent = center(family.parents, after);
        for (String child : family.children) for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
            if (graph.parentsByChild.getOrDefault(partner, Collections.emptySet()).isEmpty()) continue;
            float oldHousehold = (before.positionOf(child).x + before.positionOf(partner).x + W) / 2f;
            float newHousehold = (after.positionOf(child).x + after.positionOf(partner).x + W) / 2f;
            if ((oldParent - oldHousehold) * (newParent - newHousehold) < 0) return false;
        }
        return true;
    }
}
