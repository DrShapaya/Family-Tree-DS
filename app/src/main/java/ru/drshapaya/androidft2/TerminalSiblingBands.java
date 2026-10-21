package ru.drshapaya.androidft2;

import java.util.*;

/** Places terminal children in a single slot bounded by the order of their origins. */
final class TerminalSiblingBands {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GAP = GRID * 5;

    private TerminalSiblingBands() {}

    static boolean improve(TreeState state) { return improve(state, false); }

    static boolean compactFreeBands(TreeState state) { return improve(state, true); }

    private static boolean improve(TreeState state, boolean compact) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, current).isValid()) return false;
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            // Several descendant-bearing siblings define separate insertion
            // intervals. Their intervening leaf runs must retain that order.
            Set<Set<String>> branches = new HashSet<>();
            for (String child : family.children) {
                if (!graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()
                    || !graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()) {
                    Set<String> household = new HashSet<>(graph.partnersByPerson.getOrDefault(child, Collections.emptySet()));
                    household.add(child); branches.add(household);
                }
            }
            if (branches.size() > 1) continue;
            List<String> leaves = new ArrayList<>();
            for (String child : family.children) {
                if (state.people.get(child).pinned || child.equals(state.rootId)
                    || !graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()
                    || !graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    || graph.hasIndependentSibling(child)) continue;
                leaves.add(child);
            }
            if (leaves.isEmpty() || (compact && (branches.size() > 0 || leaves.size() != family.children.size()))) continue;
            LayoutSnapshot ordering = current;
            leaves.sort(Comparator.comparingDouble(id -> ordering.positionOf(id).x));
            float y = current.positionOf(leaves.get(0)).y;
            if (leaves.stream().anyMatch(id -> ordering.positionOf(id).y != y)) continue;
            float leafLeft = current.positionOf(leaves.get(0)).x;
            float leafRight = current.positionOf(leaves.get(leaves.size() - 1)).x;
            Set<String> ownHouseholds = new HashSet<>(family.children);
            for (String child : family.children)
                ownHouseholds.addAll(graph.partnersByPerson.getOrDefault(child, Collections.emptySet()));
            boolean foreignInside = false;
            for (String id : graph.people.keySet()) {
                LayoutSnapshot.Position p = current.positionOf(id);
                if (p.y == y && p.x > leafLeft && p.x < leafRight && !ownHouseholds.contains(id)) foreignInside = true;
            }
            // An already centred sibling span may intentionally straddle its
            // own household. Keep that valid arrangement (including remarriages).
            if (!foreignInside && (!compact || leafRight - leafLeft <= (leaves.size() - 1) * (W + GRID) + .5f)
                && Math.abs((leafLeft + leafRight + W) / 2
                - center(family.parents, current)) <= GRID / 2) continue;
            List<String> row = new ArrayList<>();
            for (String id : graph.people.keySet())
                if (current.positionOf(id).y == y && !leaves.contains(id)) row.add(id);
            row.sort(Comparator.comparingDouble(id -> ordering.positionOf(id).x));
            float width = leaves.size() * W + (leaves.size() - 1) * GRID;
            float target = center(family.parents, current) - width / 2;
            float[] origin = originSpan(family.parents, current);
            int minimum = 0, maximum = row.size();
            for (int i = 0; i < row.size(); i++) {
                Set<String> parents = new LinkedHashSet<>(graph.parentsByChild.getOrDefault(row.get(i), Collections.emptySet()));
                for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(row.get(i)))
                    for (String peer : unit.people)
                        parents.addAll(graph.parentsByChild.getOrDefault(peer, Collections.emptySet()));
                if (parents.isEmpty()) continue;
                if (!Collections.disjoint(parents, family.parents)) {
                    // Distinct unions of a shared parent retain their existing
                    // side. A free band may cross its own child household only.
                    if (!new HashSet<>(family.parents).containsAll(parents)) {
                        float x = current.positionOf(row.get(i)).x;
                        if (x < current.positionOf(leaves.get(0)).x) minimum = Math.max(minimum, i + 1);
                        if (x > current.positionOf(leaves.get(leaves.size() - 1)).x) maximum = Math.min(maximum, i);
                    }
                    continue;
                }
                float[] other = originSpan(parents, current);
                if (other[1] < origin[0] - .5f) minimum = Math.max(minimum, i + 1);
                if (other[0] > origin[1] + .5f) maximum = Math.min(maximum, i);
            }
            float best = Float.NaN, error = Float.POSITIVE_INFINITY;
            LayoutSnapshot bestBase = current;
            for (int at = minimum; at <= maximum; at++) {
                String before = at == 0 ? null : row.get(at - 1);
                String after = at == row.size() ? null : row.get(at);
                if (before != null && after != null
                    && graph.partnersByPerson.getOrDefault(before, Collections.emptySet()).contains(after)) continue;
                float low = before == null ? 0 : current.positionOf(before).x + W + gap(graph, before, leaves.get(0));
                float high = after == null ? TreeLayoutEngine.MAX_SURFACE_SIZE - width
                    : current.positionOf(after).x - gap(graph, leaves.get(0), after) - width;
                if (low > high + .5f) continue;
                float x = Math.max(low, Math.min(high, Math.round(target / GRID) * GRID));
                float distance = Math.abs(x - target);
                if (distance < error) { best = x; error = distance; }
            }
            // If the origin corridor is crowded, open the slot by carrying
            // the obstructing households with their exclusive descendants.
            if (!Float.isFinite(best)) {
                float bestCost = Float.POSITIVE_INFINITY;
                for (int at = minimum; at <= maximum; at++) {
                    String before = at == 0 ? null : row.get(at - 1);
                    String after = at == row.size() ? null : row.get(at);
                    if (before != null && after != null
                        && graph.partnersByPerson.getOrDefault(before, Collections.emptySet()).contains(after)) continue;
                    float low = before == null ? 0 : current.positionOf(before).x + W + gap(graph, before, leaves.get(0));
                    float high = after == null ? TreeLayoutEngine.MAX_SURFACE_SIZE - width
                        : current.positionOf(after).x - gap(graph, leaves.get(0), after) - width;
                    for (boolean right : new boolean[]{true, false}) {
                        float x = right ? Math.max(low, Math.round(target / GRID) * GRID)
                            : Math.min(high, Math.round(target / GRID) * GRID);
                        float shift = right ? x - high : x - low;
                        if (right ? shift <= 0 : shift >= 0) continue;
                        int first = at, end = at;
                        if (right) {
                            while (end < row.size()) {
                                end++;
                                if (end == row.size()) break;
                                String a = row.get(end - 1), b = row.get(end);
                                if (!graph.partnersByPerson.getOrDefault(a, Collections.emptySet()).contains(b)
                                    && current.positionOf(b).x - current.positionOf(a).x - W - gap(graph, a, b) >= shift) break;
                            }
                        } else {
                            while (first > 0) {
                                first--;
                                if (first == 0) break;
                                String a = row.get(first - 1), b = row.get(first);
                                if (!graph.partnersByPerson.getOrDefault(a, Collections.emptySet()).contains(b)
                                    && current.positionOf(b).x - current.positionOf(a).x - W - gap(graph, a, b) >= -shift) break;
                            }
                        }
                        Set<String> selected = new LinkedHashSet<>(row.subList(first, end));
                        Set<String> fixed = new LinkedHashSet<>(row); fixed.removeAll(selected);
                        Set<String> moving = graph.descendantBranch(selected);
                        Set<String> shared = graph.descendantBranch(fixed); shared.removeAll(selected);
                        moving.removeAll(shared);
                        boolean pinned = false;
                        for (String id : moving) pinned |= state.people.get(id).pinned || id.equals(state.rootId);
                        if (pinned || moving.isEmpty()) continue;
                        LayoutSnapshot shifted = current.shifted(moving, shift, 0);
                        LayoutSnapshot candidate = place(shifted, leaves, x);
                        if (!preservesTerminalAnchors(graph, current, candidate, family)
                            || !constraints.validate(graph, candidate).isValid()) continue;
                        float cost = Math.abs(shift) + Math.abs(x - target);
                        if (cost < bestCost) { bestCost = cost; best = x; bestBase = shifted; }
                    }
                }
            }
            if (!Float.isFinite(best)) continue;
            LayoutSnapshot candidate = place(bestBase, leaves, best);
            if (!constraints.validate(graph, candidate).isValid()) continue;
            if (foreignInside && branches.size() == 1)
                candidate = alignUpperBand(state, graph, constraints, candidate, family, leaves);
            if (!FamilyAnchorProjection.preservesExplicitAncestrySide(graph, current, candidate)) continue;
            boolean moved = false;
            for (String id : graph.people.keySet())
                moved |= Math.abs(candidate.positionOf(id).x - current.positionOf(id).x) > .5f;
            if (!moved) continue;
            current = candidate; changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    /** Carry an independent chain above the newly reunited children into its nearest slot. */
    private static LayoutSnapshot alignUpperBand(TreeState state, FamilyLayoutGraph graph,
        LayoutConstraints constraints, LayoutSnapshot snapshot, FamilyLayoutGraph.ParentFamily family,
        List<String> leaves) {
        Set<String> moving = new LinkedHashSet<>(family.parents);
        boolean added;
        do {
            added = false;
            for (String id : new ArrayList<>(moving))
                added |= moving.addAll(graph.partnersByPerson.getOrDefault(id, Collections.emptySet()));
            for (FamilyLayoutGraph.ParentFamily upper : graph.parentFamilies) {
                if (upper.children.size() != 1 || !moving.containsAll(upper.children)) continue;
                boolean exclusive = true;
                for (String parent : upper.parents)
                    exclusive &= moving.containsAll(graph.childrenByParent.getOrDefault(parent, Collections.emptySet()));
                if (exclusive) added |= moving.addAll(upper.parents);
            }
        } while (added);
        for (String id : moving) if (state.people.get(id).pinned || id.equals(state.rootId)) return snapshot;
        float desired = Math.round((center(leaves, snapshot) - center(family.parents, snapshot)) / GRID) * GRID;
        List<Float> shifts = new ArrayList<>(); shifts.add(desired); shifts.add(0f);
        for (String id : moving) {
            LayoutSnapshot.Position p = snapshot.positionOf(id);
            for (String other : graph.people.keySet()) {
                if (moving.contains(other)) continue;
                LayoutSnapshot.Position q = snapshot.positionOf(other);
                if (p.y != q.y) continue;
                shifts.add(q.x - p.x - W - GAP);
                shifts.add(q.x - p.x + W + GAP);
            }
        }
        LayoutSnapshot best = snapshot; float error = Math.abs(desired);
        for (float shift : shifts) {
            if (Math.abs(shift - desired) >= error - .5f) continue;
            LayoutSnapshot candidate = snapshot.shifted(moving, shift, 0);
            if (!preservesTerminalAnchors(graph, snapshot, candidate, family)
                || !keepsOriginOrder(graph, snapshot, candidate)
                || !constraints.validate(graph, candidate).isValid()) continue;
            best = candidate; error = Math.abs(shift - desired);
        }
        return best;
    }

    static boolean keepsOriginOrder(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        return keepsOriginOrder(graph, before, after, false);
    }

    static boolean keepsOriginOrder(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after, boolean repair) {
        for (Map.Entry<String, Set<String>> entry : graph.siblingsByPerson.entrySet())
            for (String sibling : entry.getValue()) {
                Set<String> origin = graph.parentsByChild.getOrDefault(entry.getKey(), Collections.emptySet());
                if (!origin.isEmpty() && origin.equals(graph.parentsByChild.get(sibling))) continue;
                float old = before.positionOf(entry.getKey()).x - before.positionOf(sibling).x;
                float next = after.positionOf(entry.getKey()).x - after.positionOf(sibling).x;
                if (old * next < 0) return false;
            }
        for (Map.Entry<String, Set<String>> entry : graph.partnersByPerson.entrySet()) {
            Set<String> parents = graph.parentsByChild.getOrDefault(entry.getKey(), Collections.emptySet());
            if (parents.isEmpty()) continue;
            for (String partner : entry.getValue()) {
                Set<String> other = graph.parentsByChild.getOrDefault(partner, Collections.emptySet());
                if (other.isEmpty() || !Collections.disjoint(parents, other)) continue;
                float old = center(parents, before) - center(other, before);
                float next = center(parents, after) - center(other, after);
                float childSide = before.positionOf(entry.getKey()).x - before.positionOf(partner).x;
                if (old * next < 0 && (!repair || old * childSide > 0)) return false;
            }
        }
        return true;
    }

    private static LayoutSnapshot place(LayoutSnapshot snapshot, List<String> leaves, float x) {
        LayoutSnapshot candidate = snapshot;
        for (String id : leaves) {
            candidate = candidate.shifted(Collections.singleton(id), x - snapshot.positionOf(id).x, 0);
            x += W + GRID;
        }
        return candidate;
    }

    private static boolean preservesTerminalAnchors(FamilyLayoutGraph graph, LayoutSnapshot before,
        LayoutSnapshot after, FamilyLayoutGraph.ParentFamily target) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family == target) continue;
            List<String> leaves = new ArrayList<>();
            for (String id : family.children)
                if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) leaves.add(id);
            if (leaves.size() < 2) continue;
            float oldError = Math.abs(center(leaves, before) - center(family.parents, before));
            float newError = Math.abs(center(leaves, after) - center(family.parents, after));
            if (newError > Math.max(GRID / 2, oldError) + .5f) return false;
        }
        return true;
    }

    private static float gap(FamilyLayoutGraph graph, String a, String b) {
        Set<String> parents = graph.parentsByChild.getOrDefault(a, Collections.emptySet());
        return !parents.isEmpty() && parents.equals(graph.parentsByChild.get(b))
            && graph.partnersByPerson.getOrDefault(a, Collections.emptySet()).isEmpty()
            && graph.partnersByPerson.getOrDefault(b, Collections.emptySet()).isEmpty()
            && graph.childrenByParent.getOrDefault(a, Collections.emptySet()).isEmpty()
            && graph.childrenByParent.getOrDefault(b, Collections.emptySet()).isEmpty() ? GRID : GAP;
    }

    private static float center(Collection<String> ids, LayoutSnapshot snapshot) {
        float sum = 0; for (String id : ids) sum += snapshot.positionOf(id).x + W / 2;
        return sum / ids.size();
    }

    private static float[] originSpan(Collection<String> ids, LayoutSnapshot snapshot) {
        float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        for (String id : ids) { float x = snapshot.positionOf(id).x; left = Math.min(left, x); right = Math.max(right, x); }
        return new float[]{left, right};
    }
}
