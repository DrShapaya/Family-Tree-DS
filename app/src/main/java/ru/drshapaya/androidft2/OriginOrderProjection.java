package ru.drshapaya.androidft2;

import java.util.*;

/** Order ancestor households by their settled descendants, then pack rows jointly. */
final class OriginOrderProjection {
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float GAP = GRID * 5;
    private OriginOrderProjection() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot before = LayoutSnapshot.capture(state), current = before;
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, before).isValid()) return false;
        Set<Float> rows = new TreeSet<>(Comparator.reverseOrder());
        for (String id : graph.people.keySet()) rows.add(before.positionOf(id).y);
        boolean active = false;
        for (float row : rows) {
            List<Unit> units = units(state, graph, current, before, row);
            if (!active) for (Unit a : units) for (Unit b : units)
                if (a.narrow && b.narrow && a.left + a.width <= b.left && a.target > b.target + GRID) active = true;
            if (!active) continue;
            // Existing sibling bands remain atomic. Only an ancestor household
            // acquires a new position in the order; other units keep their order.
            units = order(units, graph, before, current);
            if (units == null) return false;
            float offset = 0;
            List<Block> blocks = new ArrayList<>();
            for (int i = 0; i < units.size(); i++) {
                Unit unit = units.get(i); unit.offset = offset;
                double weight = unit.fixed ? 1e14 : unit.settled ? 1e8 : unit.narrow ? 1 : 20;
                float target = unit.fixed ? unit.left + unit.width / 2 : unit.target;
                blocks.add(new Block(i, target - unit.width / 2 - offset, weight));
                while (blocks.size() > 1) {
                    Block a = blocks.get(blocks.size() - 2), b = blocks.get(blocks.size() - 1);
                    if (a.mean() <= b.mean()) break;
                    a.last = b.last; a.sum += b.sum; a.weight += b.weight; blocks.remove(blocks.size() - 1);
                }
                offset += unit.width + GAP;
            }
            for (Block block : blocks) for (int i = block.first; i <= block.last; i++) {
                Unit unit = units.get(i);
                float x = Math.round((Math.max(0, block.mean()) + unit.offset) / GRID) * GRID;
                if (unit.fixed && Math.abs(x - unit.left) > .5f) return false;
                if (unit.settled && Math.abs(x + unit.width / 2 - unit.target) > GRID / 2) return false;
                current = current.shifted(unit.ids, x - unit.left, 0);
            }
        }
        int oldInversions = inversions(graph, before), newInversions = inversions(graph, current);
        if (!active || !constraints.validate(graph, current).isValid()
            || !preservesCorrectOrigins(graph, before, current)
            || !FamilyAnchorProjection.preservesRowGaps(graph, before, current)
            || newInversions > oldInversions
            || (newInversions == oldInversions
                && error(graph, current) >= error(graph, before) - GRID)) return false;
        current.applyTo(state);
        return true;
    }

    private static boolean preservesCorrectOrigins(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        for (Map.Entry<String, Set<String>> entry : graph.siblingsByPerson.entrySet())
            for (String peer : entry.getValue())
                if ((before.positionOf(entry.getKey()).x - before.positionOf(peer).x)
                    * (after.positionOf(entry.getKey()).x - after.positionOf(peer).x) < 0) return false;
        for (Map.Entry<String, Set<String>> entry : graph.partnersByPerson.entrySet()) {
            Set<String> parents = graph.parentsByChild.getOrDefault(entry.getKey(), Collections.emptySet());
            if (parents.isEmpty()) continue;
            for (String peer : entry.getValue()) {
                Set<String> other = graph.parentsByChild.getOrDefault(peer, Collections.emptySet());
                if (other.isEmpty() || !Collections.disjoint(parents, other)) continue;
                float side = before.positionOf(entry.getKey()).x - before.positionOf(peer).x;
                float old = FamilyAnchorProjection.average(parents, before) - FamilyAnchorProjection.average(other, before);
                float next = FamilyAnchorProjection.average(parents, after) - FamilyAnchorProjection.average(other, after);
                if (old * side > 0 && next * side < 0) return false;
            }
        }
        return true;
    }

    private static List<Unit> order(List<Unit> units, FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot current) {
        Map<String, Unit> byPerson = new HashMap<>();
        Map<Unit, Set<Unit>> edges = new LinkedHashMap<>();
        Map<Unit, Integer> incoming = new HashMap<>();
        for (Unit unit : units) {
            for (String id : unit.ids) byPerson.put(id, unit);
            edges.put(unit, new LinkedHashSet<>()); incoming.put(unit, 0);
        }
        for (Map.Entry<String, Set<String>> entry : graph.partnersByPerson.entrySet()) {
            Set<String> parents = graph.parentsByChild.getOrDefault(entry.getKey(), Collections.emptySet());
            for (String peer : entry.getValue()) {
                Set<String> other = graph.parentsByChild.getOrDefault(peer, Collections.emptySet());
                if (parents.isEmpty() || other.isEmpty() || !Collections.disjoint(parents, other)) continue;
                float side = current.positionOf(entry.getKey()).x - current.positionOf(peer).x;
                float old = FamilyAnchorProjection.average(parents, before) - FamilyAnchorProjection.average(other, before);
                for (String a : parents) for (String b : other) {
                    Unit ua = byPerson.get(a), ub = byPerson.get(b);
                    if (ua == null || ub == null || ua == ub || (old * side <= 0 && !(ua.narrow && ub.narrow))) continue;
                    Unit left = side < 0 ? ua : ub, right = side < 0 ? ub : ua;
                    if (edges.get(left).add(right)) incoming.put(right, incoming.get(right) + 1);
                }
            }
        }
        PriorityQueue<Unit> ready = new PriorityQueue<>(Comparator.comparingDouble(
            (Unit u) -> u.narrow ? u.target : u.left + u.width / 2).thenComparingDouble(u -> u.left));
        for (Unit unit : units) if (incoming.get(unit) == 0) ready.add(unit);
        List<Unit> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            Unit unit = ready.remove(); result.add(unit);
            for (Unit next : edges.get(unit)) {
                int count = incoming.get(next) - 1; incoming.put(next, count);
                if (count == 0) ready.add(next);
            }
        }
        return result.size() == units.size() ? result : null;
    }

    private static int inversions(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        int count = 0;
        for (FamilyLayoutGraph.ParentFamily a : graph.parentFamilies)
            for (FamilyLayoutGraph.ParentFamily b : graph.parentFamilies) {
                if (snapshot.positionOf(a.parents.get(0)).y != snapshot.positionOf(b.parents.get(0)).y
                    || !Collections.disjoint(a.parents, b.parents)
                    || right(a.children, snapshot) > left(b.children, snapshot)) continue;
                if (FamilyAnchorProjection.average(a.parents, snapshot) > FamilyAnchorProjection.average(b.parents, snapshot)) count++;
            }
        return count;
    }

    private static List<Unit> units(TreeState state, FamilyLayoutGraph graph,
        LayoutSnapshot current, LayoutSnapshot before, float row) {
        List<Set<String>> groups = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (current.positionOf(id).y != row || !visited.add(id)) continue;
            Set<String> group = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(id);
            while (!queue.isEmpty()) {
                String next = queue.removeFirst(); group.add(next);
                for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(next))
                    for (String peer : pair.people)
                        if (current.positionOf(peer).y == row && visited.add(peer)) queue.add(peer);
            }
            groups.add(group);
        }
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            Set<String> leaves = new LinkedHashSet<>();
            for (String id : family.children)
                if (current.positionOf(id).y == row && graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && !graph.hasIndependentSibling(id)) leaves.add(id);
            if (leaves.size() < 2) continue;
            float lo = left(leaves, current), hi = right(leaves, current);
            if (hi - lo != leaves.size() * (W + GRID) - GRID) continue;
            groups.removeIf(g -> !Collections.disjoint(g, leaves)); groups.add(leaves);
        }
        List<Unit> result = new ArrayList<>();
        for (Set<String> ids : groups) {
            Unit unit = new Unit(); unit.ids = ids;
            unit.left = left(ids, current); unit.width = right(ids, current) - unit.left;
            unit.target = unit.left + unit.width / 2;
            Set<String> children = new LinkedHashSet<>();
            for (String id : ids) {
                children.addAll(graph.childrenByParent.getOrDefault(id, Collections.emptySet()));
                unit.fixed |= state.people.get(id).pinned || id.equals(state.rootId);
            }
            if (!children.isEmpty()) {
                unit.narrow = children.size() == 1;
                for (String id : ids) unit.narrow &= !graph.hasIndependentSibling(id);
                if (unit.narrow) unit.target = FamilyAnchorProjection.spanCenter(children, current);
                else {
                    Set<String> leaves = new LinkedHashSet<>();
                    for (String id : children)
                        if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty() && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) leaves.add(id);
                    Collection<String> anchor = children;
                    float oldCenter = FamilyAnchorProjection.average(ids, before);
                    float directError = Math.abs(FamilyAnchorProjection.spanCenter(children, before) - oldCenter);
                    if (directError > GRID / 2 && leaves.size() >= 2
                        && oldCenter >= left(leaves, before) && oldCenter <= right(leaves, before)) anchor = leaves;
                    unit.settled = Math.abs(FamilyAnchorProjection.spanCenter(anchor, before) - oldCenter) <= GRID / 2;
                    unit.target += FamilyAnchorProjection.spanCenter(anchor, current) - FamilyAnchorProjection.spanCenter(anchor, before);
                }
            }
            result.add(unit);
        }
        return result;
    }

    private static double error(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        double result = 0;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            float parent = FamilyAnchorProjection.average(family.parents, snapshot);
            float distance = Math.abs(FamilyAnchorProjection.spanCenter(family.children, snapshot) - parent);
            Set<String> leaves = new LinkedHashSet<>();
            for (String id : family.children)
                if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty() && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) leaves.add(id);
            if (leaves.size() >= 2 && family.children.size() == leaves.size() + 1)
                distance = Math.min(distance, Math.abs(FamilyAnchorProjection.spanCenter(leaves, snapshot) - parent));
            result += distance * distance;
        }
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
    private static final class Unit {
        Set<String> ids;
        float left, width, target, offset;
        boolean narrow, fixed, settled;
    }
    private static final class Block {
        final int first;
        int last;
        double sum, weight;
        Block(int index, double target, double weight) { first = last = index; this.weight = weight; sum = target * weight; }
        double mean() { return sum / weight; }
    }
}
