package ru.drshapaya.androidft2;

import java.util.*;

/** Refresh upper family anchors after the child rows have finished moving. */
final class FamilyAnchorProjection {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float W = TreeLayoutEngine.CARD_W;
    private static final float GAP = GRID * 5;

    private FamilyAnchorProjection() {}

    static boolean balanceCompetingOrigins(TreeState state) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        boolean changed = false;
        Set<String> done = new HashSet<>();
        for (String child : graph.people.keySet()) {
            FamilyLayoutGraph.ParentFamily a = graph.parentFamilyOf(child);
            if (a == null || a.children.size() != 1 || a.parents.size() != 2) continue;
            for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
                FamilyLayoutGraph.ParentFamily b = graph.parentFamilyOf(partner);
                if (b == null || b.children.size() != 1 || b.parents.size() != 2
                    || !Collections.disjoint(a.parents, b.parents) || done.contains(child) || done.contains(partner)) continue;
                Set<String> moving = new LinkedHashSet<>(a.parents); moving.addAll(b.parents);
                boolean pinned = false;
                for (String id : moving) pinned |= state.people.get(id).pinned || id.equals(state.rootId);
                if (pinned) continue;
                float ea = current.positionOf(child).x + W / 2 - average(a.parents, current);
                float eb = current.positionOf(partner).x + W / 2 - average(b.parents, current);
                float desired = Math.round((ea + eb) / 2 / GRID) * GRID;
                float available = Math.abs(desired);
                for (String id : moving) {
                    LayoutSnapshot.Position p = current.positionOf(id);
                    if (desired < 0) available = Math.min(available, p.x);
                    for (String other : graph.people.keySet()) {
                        if (moving.contains(other)) continue;
                        LayoutSnapshot.Position q = current.positionOf(other);
                        if (q.y != p.y) continue;
                        float gap = desired > 0 ? q.x - p.x - W : p.x - q.x - W;
                        if (gap >= 0) available = Math.min(available, Math.max(0, gap - GAP));
                    }
                }
                float delta = Math.copySign((float)Math.floor(available / GRID) * GRID, desired);
                if (Math.max(Math.abs(ea - delta), Math.abs(eb - delta)) >= Math.max(Math.abs(ea), Math.abs(eb)) - .5f) continue;
                LayoutSnapshot candidate = current.shifted(moving, delta, 0);
                if (!preservesExplicitAncestrySide(graph, current, candidate)
                    || !constraints.validate(graph, candidate).isValid()) continue;
                current = candidate; changed = true; done.add(child); done.add(partner);
            }
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    static boolean improve(TreeState state) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        LayoutConstraints constraints = new LayoutConstraints();
        if (!constraints.validate(graph, current).isValid()) return false;
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
        LayoutSnapshot ordering = current;
        families.sort(Comparator.comparingDouble((FamilyLayoutGraph.ParentFamily f) ->
            ordering.positionOf(f.children.iterator().next()).y).reversed());
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : families) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            // A small private leaf union must not pull a shared household away
            // from its larger family. Its free children have their own projection.
            Set<String> parentHousehold = graph.partnerHousehold(family.parents);
            boolean privateLeaves = parentHousehold.size() > family.parents.size();
            for (String child : family.children) privateLeaves &= graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty();
            if (privateLeaves) {
                boolean larger = false;
                for (FamilyLayoutGraph.ParentFamily other : families) larger |= other.children.size() > family.children.size()
                    && parentHousehold.containsAll(other.parents);
                if (larger) continue;
            }
            float anchor = spanCenter(family.children, current);
            float parent = average(family.parents, current);
            float childLeft = Float.POSITIVE_INFINITY, childRight = Float.NEGATIVE_INFINITY;
            for (String id : family.children) { float x = current.positionOf(id).x + W / 2; childLeft = Math.min(childLeft, x); childRight = Math.max(childRight, x); }
            // A broad, shared descendant must not pull a correctly placed
            // parent out of the compact run of its other children.
            List<String> leaves = new ArrayList<>();
            for (String child : family.children)
                if (graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()) leaves.add(child);
            boolean centeredLeaf = false;
            for (String id : leaves) centeredLeaf |= Math.abs(current.positionOf(id).x + W / 2 - parent) <= GRID / 2;
            if (centeredLeaf && parent >= childLeft && parent <= childRight) continue;
            if (leaves.size() >= 2 && leaves.size() < family.children.size()) {
                float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
                for (String id : leaves) { float x = current.positionOf(id).x + W / 2; left = Math.min(left, x); right = Math.max(right, x); }
                if (parent >= left && parent <= right) continue;
            }
            float desired = Math.round((anchor - parent) / GRID) * GRID;
            // Small offsets can be necessary for collateral ancestry. Repair
            // detached anchors without churning already settled small trees.
            if (Math.abs(desired) <= GAP) continue;
            for (Set<String> moving : Arrays.asList(upperChain(graph, family.parents), graph.partnerHousehold(family.parents))) {
                desired = Math.round((anchor - average(family.parents, current)) / GRID) * GRID;
                if (Math.abs(desired) < GRID / 2) break;
                boolean fixed = false;
                for (String id : moving) fixed |= state.people.get(id).pinned || id.equals(state.rootId);
                if (fixed || !Collections.disjoint(moving, family.children)) continue;
                Set<Float> options = new LinkedHashSet<>(); options.add(desired);
                float alienLeft = Float.NEGATIVE_INFINITY, alienRight = Float.POSITIVE_INFINITY;
                // A child's spouse can connect this generation to a broad
                // collateral band. Keep the parent chain on its own origin's
                // side of that band even when its midpoint lies inside it.
                if (family.children.size() > 1) for (FamilyLayoutGraph.ParentFamily other : families) {
                    List<String> band = new ArrayList<>();
                    for (String id : other.children)
                        if (current.positionOf(id).y == current.positionOf(family.parents.get(0)).y
                            && graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                            && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) band.add(id);
                    if (band.size() < 2) continue;
                    float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
                    for (String id : band) { lo = Math.min(lo, current.positionOf(id).x); hi = Math.max(hi, current.positionOf(id).x + W); }
                    if (anchor >= lo && anchor <= hi) {
                        for (String child : family.children)
                            for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet()))
                                for (String origin : graph.parentsByChild.getOrDefault(partner, Collections.emptySet())) {
                                    if (!other.children.contains(origin)) continue;
                                    if (current.positionOf(child).x < current.positionOf(partner).x)
                                        alienRight = Math.min(alienRight, lo - GAP);
                                    else alienLeft = Math.max(alienLeft, hi + GAP);
                                }
                    }
                }
                for (String id : moving) {
                    LayoutSnapshot.Position p = current.positionOf(id);
                    for (String other : graph.people.keySet()) {
                        if (moving.contains(other)) continue;
                        LayoutSnapshot.Position q = current.positionOf(other);
                        if (p.y != q.y) continue;
                        options.add(q.x - p.x - W - GAP);
                        options.add(q.x - p.x + W + GAP);
                    }
                }
                LayoutSnapshot best = current;
                float error = Float.isFinite(alienLeft) || Float.isFinite(alienRight) ? Float.POSITIVE_INFINITY : Math.abs(desired);
                for (float delta : options) {
                    if (Math.abs(delta - desired) >= error - .5f) continue;
                    LayoutSnapshot candidate = current.shifted(moving, delta, 0);
                    float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
                    for (String id : family.parents) { lo = Math.min(lo, candidate.positionOf(id).x); hi = Math.max(hi, candidate.positionOf(id).x + W); }
                    if (lo < alienLeft || hi > alienRight) continue;
                    if (Math.abs(delta - desired) < .5f && family.children.size() == 1)
                        candidate = clearLeafBands(state, graph, constraints, current, candidate, moving);
                    if (candidate == null) continue;
                    if (Math.abs(delta - desired) < .5f && family.children.size() > 1
                        && hasCenteredJunction(graph, family, current) && !constraints.validate(graph, candidate).isValid())
                        candidate = openContour(state, graph, current, family, moving, delta);
                    if (!TerminalSiblingBands.keepsOriginOrder(graph, current, candidate)
                        || !preservesExplicitAncestrySide(graph, current, candidate)
                        || !preservesRowGaps(graph, current, candidate)
                        || !keepsCenteredFamilies(graph, current, candidate, family)
                        || !constraints.validate(graph, candidate).isValid()) continue;
                    best = candidate; error = Math.abs(delta - desired);
                }
                if (best != current) { current = best; changed = true; }
            }
            if (family.children.size() == 1) {
                String child = family.children.iterator().next();
                boolean competingOrigins = false;
                for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
                    FamilyLayoutGraph.ParentFamily origin = graph.parentFamilyOf(partner);
                    competingOrigins |= origin != null && origin != family && origin.children.size() == 1
                        && origin.parents.size() > 1 && family.parents.size() > 1;
                }
                if (competingOrigins) continue;
                // If the upper row is blocked, translate only the exclusive
                // lower branch. Shared junctions stay fixed, and no other
                // attachment may become worse.
                float delta = Math.round((average(family.parents, current) - current.positionOf(child).x - W / 2) / GRID) * GRID;
                if (Math.abs(delta) < GRID / 2) continue;
                Set<String> household = new LinkedHashSet<>();
                for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(child)) household.addAll(unit.people);
                Set<String> moving = graph.descendantBranch(household);
                Set<String> otherRoots = new LinkedHashSet<>();
                float row = current.positionOf(child).y;
                for (String id : graph.people.keySet())
                    if (current.positionOf(id).y == row && !household.contains(id)) otherRoots.add(id);
                Set<String> shared = graph.descendantBranch(otherRoots); shared.removeAll(household);
                moving.removeAll(shared);
                boolean fixed = false;
                for (String id : moving) fixed |= state.people.get(id).pinned || id.equals(state.rootId);
                if (fixed || !Collections.disjoint(moving, family.parents)) continue;
                LayoutSnapshot candidate = current.shifted(moving, delta, 0);
                boolean worsens = false;
                for (FamilyLayoutGraph.ParentFamily other : families) {
                    if (other == family) continue;
                    float old = visualError(graph, other, current);
                    float next = visualError(graph, other, candidate);
                    worsens |= next > Math.max(old, GRID / 2) + .5f;
                }
                if (!worsens && TerminalSiblingBands.keepsOriginOrder(graph, current, candidate)
                    && preservesExplicitAncestrySide(graph, current, candidate)
                    && preservesRowGaps(graph, current, candidate)
                    && constraints.validate(graph, candidate).isValid()) { current = candidate; changed = true; }
            }
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    static boolean preservesRowGaps(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        List<String> ids = new ArrayList<>(graph.people.keySet());
        ids.sort(Comparator.comparingDouble(id -> after.positionOf(id).x));
        for (int i = 0; i < ids.size(); i++) for (int j = i + 1; j < ids.size(); j++) {
            String a = ids.get(i), b = ids.get(j);
            LayoutSnapshot.Position p = after.positionOf(a), q = after.positionOf(b);
            float gap = q.x - p.x - W;
            if (gap >= GAP - .5f) break;
            if (p.y != q.y) continue;
            boolean partners = false;
            for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(a)) partners |= unit.people.contains(b);
            if (partners) continue;
            Set<String> parents = graph.parentsByChild.getOrDefault(a, Collections.emptySet());
            boolean plain = graph.partnersByPerson.getOrDefault(a, Collections.emptySet()).isEmpty()
                && graph.partnersByPerson.getOrDefault(b, Collections.emptySet()).isEmpty()
                && graph.childrenByParent.getOrDefault(a, Collections.emptySet()).isEmpty()
                && graph.childrenByParent.getOrDefault(b, Collections.emptySet()).isEmpty();
            boolean siblings = (!parents.isEmpty() && parents.equals(graph.parentsByChild.get(b)))
                || graph.siblingsByPerson.getOrDefault(a, Collections.emptySet()).contains(b);
            if (plain && siblings) continue;
            float old = Math.abs(before.positionOf(a).x - before.positionOf(b).x) - W;
            if (gap < Math.min(GAP, old) - .5f) return false;
        }
        return true;
    }

    static boolean preservesExplicitAncestrySide(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            boolean collateral = family.children.size() > 1;
            for (String id : family.parents) collateral |= graph.hasIndependentSibling(id);
            if (!collateral) continue;
            for (String child : family.children)
                for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
                    if (graph.parentsByChild.getOrDefault(partner, Collections.emptySet()).isEmpty()) continue;
                    float left = Math.min(before.positionOf(child).x, before.positionOf(partner).x);
                    float right = Math.max(before.positionOf(child).x, before.positionOf(partner).x) + W;
                    float nextLeft = Math.min(after.positionOf(child).x, after.positionOf(partner).x);
                    float nextRight = Math.max(after.positionOf(child).x, after.positionOf(partner).x) + W;
                    for (String parent : family.parents) {
                        float old = before.positionOf(parent).x, next = after.positionOf(parent).x;
                        if (before.positionOf(child).x < before.positionOf(partner).x && old + W <= left + .5f
                            && next + W > nextLeft + .5f && next < nextRight - .5f) return false;
                        if (before.positionOf(child).x > before.positionOf(partner).x && old >= right - .5f
                            && next < nextRight - .5f && next + W > nextLeft + .5f) return false;
                    }
                }
        }
        return true;
    }

    private static boolean hasCenteredJunction(FamilyLayoutGraph graph, FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot) {
        for (String child : family.children)
            for (FamilyLayoutGraph.ParentFamily lower : graph.parentFamilies)
                if (lower.parents.contains(child) && lower.children.size() == 1
                    && Math.abs(spanCenter(lower.children, snapshot) - average(lower.parents, snapshot)) <= GRID / 2) return true;
        return false;
    }

    /** Release the obstructing side as atomic households and compact leaf runs. */
    private static LayoutSnapshot openContour(TreeState state, FamilyLayoutGraph graph, LayoutSnapshot before,
        FamilyLayoutGraph.ParentFamily family, Set<String> anchor, float delta) {
        List<Set<String>> units = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (!visited.add(id)) continue;
            Set<String> unit = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(id);
            while (!queue.isEmpty()) {
                String next = queue.removeFirst(); unit.add(next);
                for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(next))
                    for (String peer : pair.people) if (visited.add(peer)) queue.add(peer);
            }
            units.add(unit);
        }
        for (FamilyLayoutGraph.ParentFamily other : graph.parentFamilies) {
            List<String> leaves = new ArrayList<>();
            for (String id : other.children)
                if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && !graph.hasIndependentSibling(id)) leaves.add(id);
            if (leaves.size() < 2) continue;
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            float y = before.positionOf(leaves.get(0)).y; boolean sameRow = true;
            for (String id : leaves) { LayoutSnapshot.Position p = before.positionOf(id); sameRow &= p.y == y; lo = Math.min(lo, p.x); hi = Math.max(hi, p.x + W); }
            if (!sameRow || hi - lo != leaves.size() * (W + GRID) - GRID) continue;
            Set<String> band = new LinkedHashSet<>(leaves);
            units.removeIf(u -> !Collections.disjoint(u, band)); units.add(band);
        }
        Set<String> fixedChildren = new HashSet<>(family.children);
        for (String child : family.children)
            for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(child)) fixedChildren.addAll(pair.people);
        float cut = delta > 0 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        for (String id : family.parents) {
            float x = before.positionOf(id).x;
            cut = delta > 0 ? Math.max(cut, x + W) : Math.min(cut, x);
        }
        Set<String> moving = new LinkedHashSet<>(anchor);
        for (Set<String> unit : units) {
            if (!Collections.disjoint(unit, fixedChildren)) continue;
            boolean side = true, pinned = false;
            for (String id : unit) {
                float x = before.positionOf(id).x;
                side &= delta > 0 ? x >= cut : x + W <= cut;
                pinned |= state.people.get(id).pinned;
            }
            if (side && !pinned) moving.addAll(unit);
        }
        return before.shifted(moving, delta, 0);
    }

    private static float visualError(FamilyLayoutGraph graph, FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot) {
        List<String> leaves = new ArrayList<>();
        for (String id : family.children)
            if (graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) leaves.add(id);
        float direct = Math.abs(spanCenter(family.children, snapshot) - average(family.parents, snapshot));
        return leaves.size() >= 2 && family.children.size() == leaves.size() + 1
            ? Math.min(direct, Math.abs(spanCenter(leaves, snapshot) - average(family.parents, snapshot))) : direct;
    }

    private static LayoutSnapshot clearLeafBands(TreeState state, FamilyLayoutGraph graph,
        LayoutConstraints constraints, LayoutSnapshot before, LayoutSnapshot candidate, Set<String> moving) {
        if (constraints.validate(graph, candidate).isValid()) return candidate;
        Set<FamilyLayoutGraph.ParentFamily> blockers = new LinkedHashSet<>();
        for (String id : moving) {
            LayoutSnapshot.Position p = candidate.positionOf(id);
            for (String other : graph.people.keySet()) {
                if (moving.contains(other)) continue;
                LayoutSnapshot.Position q = candidate.positionOf(other);
                if (p.y != q.y || q.x + W + GAP <= p.x || p.x + W + GAP <= q.x) continue;
                if (state.people.get(other).pinned || !graph.partnersByPerson.getOrDefault(other, Collections.emptySet()).isEmpty()
                    || !graph.childrenByParent.getOrDefault(other, Collections.emptySet()).isEmpty()
                    || graph.hasIndependentSibling(other)) return candidate;
                FamilyLayoutGraph.ParentFamily family = graph.parentFamilyOf(other);
                if (family == null) return candidate;
                blockers.add(family);
            }
        }
        for (FamilyLayoutGraph.ParentFamily family : blockers) {
            List<String> leaves = new ArrayList<>();
            for (String child : family.children)
                if (!state.people.get(child).pinned && !child.equals(state.rootId)
                    && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && !graph.hasIndependentSibling(child)) leaves.add(child);
            LayoutSnapshot ordering = before;
            leaves.sort(Comparator.comparingDouble(id -> ordering.positionOf(id).x));
            float row = before.positionOf(leaves.get(0)).y;
            float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            for (String id : moving) if (candidate.positionOf(id).y == row) {
                left = Math.min(left, candidate.positionOf(id).x); right = Math.max(right, candidate.positionOf(id).x + W);
            }
            float width = leaves.size() * W + (leaves.size() - 1) * GRID;
            LayoutSnapshot best = null; float movement = Float.POSITIVE_INFINITY;
            for (float x : new float[]{right + GAP, left - GAP - width}) {
                LayoutSnapshot placed = candidate; float cursor = x;
                for (String id : leaves) { placed = placed.shifted(Collections.singleton(id), cursor - placed.positionOf(id).x, 0); cursor += W + GRID; }
                float distance = Math.abs(x - before.positionOf(leaves.get(0)).x);
                if (distance < movement && constraints.validate(graph, placed).isValid()) { best = placed; movement = distance; }
            }
            if (best == null) return candidate;
            candidate = best;
        }
        return candidate;
    }

    static Set<String> upperChain(FamilyLayoutGraph graph, Collection<String> start) {
        Set<String> moving = new LinkedHashSet<>(start);
        boolean added;
        do {
            added = false;
            for (String id : new ArrayList<>(moving))
                for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsOf(id)) added |= moving.addAll(unit.people);
            for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
                if (family.children.size() != 1 || !moving.containsAll(family.children)) continue;
                boolean exclusive = true;
                for (String parent : family.parents)
                    exclusive &= moving.containsAll(graph.childrenByParent.getOrDefault(parent, Collections.emptySet()));
                if (exclusive) added |= moving.addAll(family.parents);
            }
        } while (added);
        return moving;
    }

    private static boolean keepsCenteredFamilies(FamilyLayoutGraph graph, LayoutSnapshot before,
        LayoutSnapshot after, FamilyLayoutGraph.ParentFamily target) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family == target) continue;
            if (before.positionOf(family.parents.get(0)).y < before.positionOf(target.parents.get(0)).y) continue;
            float old = Math.abs(spanCenter(family.children, before) - average(family.parents, before));
            float next = Math.abs(spanCenter(family.children, after) - average(family.parents, after));
            if (old <= GRID / 2 && next > old + .5f) {
                boolean secondary = !Collections.disjoint(family.parents, target.parents)
                    && family.children.size() < target.children.size();
                for (String child : family.children) secondary &= graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    && graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty();
                if (!secondary) return false;
            }
        }
        return true;
    }

    static float spanCenter(Collection<String> ids, LayoutSnapshot snapshot) {
        float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        for (String id : ids) { float x = snapshot.positionOf(id).x; left = Math.min(left, x); right = Math.max(right, x + W); }
        return (left + right) / 2;
    }

    static float average(Collection<String> ids, LayoutSnapshot snapshot) {
        float sum = 0; for (String id : ids) sum += snapshot.positionOf(id).x + W / 2;
        return sum / ids.size();
    }
}
