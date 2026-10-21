package ru.drshapaya.androidft2;

import java.util.*;

/** Opens the semantic gap between neighbouring family bands after local
 * projections.  A complete suffix is translated together, so a repair never
 * leaves a child's descendants behind or creates a new overlap. */
final class TerminalRowSpacing {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GAP = GRID * 5f;

    private TerminalRowSpacing() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.size() < 2) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, current).isValid()) return false;
        boolean changed = false;
        for (int pass = 0; pass < 12; pass++) {
            boolean moved = false;
            Map<Float, List<Band>> rows = rows(graph, current);
            for (List<Band> row : rows.values()) {
                LayoutSnapshot ordering = current;
                row.sort(Comparator.comparingDouble(b -> left(b.people, ordering)));
                for (int i = 1; i < row.size(); i++) {
                    Band before = row.get(i - 1), after = row.get(i);
                    float actual = left(after.people, current) - right(before.people, current);
                    if (actual >= GAP - .5f) continue;
                    float shift = Math.round((GAP - actual) / GRID) * GRID;
                    if (shift < GRID) shift = GRID;
                    Set<String> suffix = new LinkedHashSet<>();
                    for (int j = i; j < row.size(); j++)
                        suffix.addAll(graph.descendantBranch(row.get(j).people));
                    if (fixed(state, suffix)) continue;
                    LayoutSnapshot candidate = current.shifted(suffix, shift, 0);
                    if (!accept(graph, constraints, current, candidate)) continue;
                    current = candidate; changed = moved = true;
                    break;
                }
                if (moved) break;
            }
            if (!moved) break;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    private static boolean accept(FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot before, LayoutSnapshot after) {
        return constraints.validate(graph, after).isValid()
            && TerminalSiblingBands.keepsOriginOrder(graph, before, after, true)
            && FamilyAnchorProjection.preservesExplicitAncestrySide(graph, before, after);
    }

    /** True when no pair of neighbouring family bands lost more of its semantic gap. */
    static boolean keepsBandSpacing(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        return spacingDeficit(graph, after) <= spacingDeficit(graph, before) + .5f;
    }

    private static float spacingDeficit(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        float deficit = 0;
        for (List<Band> row : rows(graph, snapshot).values()) {
            row.sort(Comparator.comparingDouble(b -> left(b.people, snapshot)));
            for (int i = 1; i < row.size(); i++) {
                Band before = row.get(i - 1), after = row.get(i);
                // Full siblings without own families form a 1-cell sibling run
                // even when a continuing sibling keeps their family split.
                if (terminalSiblings(graph, before.people, after.people)) continue;
                float gap = left(after.people, snapshot) - right(before.people, snapshot);
                deficit += Math.max(0f, GAP - gap);
            }
        }
        return deficit;
    }

    private static boolean terminalSiblings(FamilyLayoutGraph graph, Set<String> a, Set<String> b) {
        Set<String> origin = null;
        for (Set<String> band : Arrays.asList(a, b)) {
            for (String id : band) {
                Set<String> parents = graph.parentsByChild.getOrDefault(id, Collections.emptySet());
                if (parents.isEmpty()
                    || !graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()
                    || !graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()) return false;
                if (origin == null) origin = parents;
                else if (!origin.equals(parents)) return false;
            }
        }
        return true;
    }

    private static Map<Float, List<Band>> rows(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        Map<Float, List<Band>> result = new TreeMap<>();
        Map<String, Band> byPerson = new LinkedHashMap<>();
        for (String id : graph.people.keySet()) {
            Band band = byPerson.get(id);
            if (band == null) {
                band = new Band();
                byPerson.put(id, band);
                for (String member : graph.partnerHousehold(Collections.singleton(id))) {
                    if (snapshot.positionOf(member).y == snapshot.positionOf(id).y) {
                        band.people.add(member); byPerson.put(member, band);
                    }
                }
                result.computeIfAbsent(snapshot.positionOf(id).y, ignored -> new ArrayList<>()).add(band);
            }
        }
        // Terminal children with the same two origins form one family band.
        // Continuing children and explicit collateral sibling links stay as
        // separate bands so their ancestry side remains visible.
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.children.size() < 2) continue;
            float y = snapshot.positionOf(family.children.iterator().next()).y;
            boolean sameRow = true, terminal = true;
            for (String child : family.children) {
                sameRow &= snapshot.positionOf(child).y == y;
                terminal &= graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && !graph.hasIndependentSibling(child);
            }
            if (!sameRow || !terminal) continue;
            Band first = byPerson.get(family.children.iterator().next());
            if (first == null) continue;
            for (String child : family.children) {
                Band next = byPerson.get(child);
                if (next == null || next == first) continue;
                first.people.addAll(next.people);
                next.people.clear();
                byPerson.put(child, first);
            }
        }
        for (List<Band> row : result.values()) row.removeIf(b -> b.people.isEmpty());
        return result;
    }

    private static boolean fixed(TreeState state, Collection<String> ids) {
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person != null && (person.pinned || id.equals(state.rootId))) return true;
        }
        return false;
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

    private static final class Band {
        final Set<String> people = new LinkedHashSet<>();
    }
}
