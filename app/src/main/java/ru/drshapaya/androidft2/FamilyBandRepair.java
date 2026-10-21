package ru.drshapaya.androidft2;

import java.util.*;

/** A complete candidate for torn family bands, evaluated before any coordinates are applied. */
final class FamilyBandRepair {
    private static final float W = TreeLayoutEngine.CARD_W, G = TreeLayoutEngine.GRID;
    private FamilyBandRepair() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.isEmpty()) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        LayoutConstraints validator = new LayoutConstraints();
        if (!validator.validate(graph, before).isValid()) return false;
        Quality old = quality(graph, before);
        if (old.tears == 0 && old.detached < 5 * G && old.unionSlack < 5 * G) return false;
        Model model = build(state, graph, before);
        if (model == null) return false;
        // Preserve settled anchors unless a torn co-parent household first needs
        // to be rebuilt. The complete candidate still has to improve total
        // attachment error and preserve established ancestry sides.
        float tolerance = old.householdSlack > 5 * G ? Float.POSITIVE_INFINITY : G / 2;
        model.tolerance = tolerance / G;
        for (Equation eq : model.settled) {
            if (eq.b != eq.d || !Float.isFinite(tolerance)) continue;
            model.hard.add(new Bound(eq.a, eq.b, tolerance / G - eq.c));
            model.hard.add(new Bound(eq.b, eq.a, tolerance / G + eq.c));
        }
        double[] x = solve(model);
        if (x == null) return false;
        LayoutSnapshot candidate = before;
        for (Band band : model.bands) for (Unit unit : band.units) for (String id : unit.ids) {
            float desired = (float)(x[band.index] * G + band.offsets.get(id));
            candidate = candidate.shifted(Collections.singleton(id), desired - before.positionOf(id).x, 0);
        }
        candidate = polish(state, graph, before, candidate);
        Quality next = quality(graph, candidate);
        if (!validator.validate(graph, candidate).isValid() || !keepsOriginSides(graph, before, candidate)
            || !FamilyAnchorProjection.preservesExplicitAncestrySide(graph, before, candidate)
            || next.terminalTears > old.terminalTears || next.detached > old.detached + G
            || next.anchorError > old.anchorError + G || next.cost() >= old.cost() - G) return false;
        candidate.applyTo(state);
        return true;
    }

    private static LayoutSnapshot polish(TreeState state, FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot candidate) {
        LayoutSnapshot best = candidate; double cost = quality(graph, candidate).cost();
        try {
            candidate.applyTo(state);
            for (int pass = 0; pass < 8; pass++) {
                boolean changed = FamilyAnchorProjection.improve(state);
                changed |= SiblingBoundaryProjection.improve(state);
                changed |= FamilyAnchorProjection.balanceCompetingOrigins(state);
                changed |= TerminalSiblingBands.improve(state);
                LayoutSnapshot next = LayoutSnapshot.capture(state); Quality q = quality(graph, next);
                if (q.cost() < cost && q.householdSlack < G / 2) { best = next; cost = q.cost(); }
                if (!changed) break;
            }
        } finally { before.applyTo(state); }
        return best;
    }

    private static Model build(TreeState state, FamilyLayoutGraph graph, LayoutSnapshot before) {
        Map<String, Integer> distance = rootDistances(graph);
        Map<String, Unit> byPerson = new HashMap<>();
        Map<Float, List<Unit>> rows = new TreeMap<>();
        for (String id : graph.people.keySet()) {
            if (byPerson.containsKey(id)) continue;
            Unit unit = new Unit(); ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(id);
            while (!queue.isEmpty()) {
                String next = queue.removeFirst(); if (byPerson.containsKey(next)) continue;
                if (before.positionOf(next).y != before.positionOf(id).y) return null;
                byPerson.put(next, unit); unit.ids.add(next);
                for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(next)) queue.addAll(pair.people);
            }
            unit.ids.sort(Comparator.comparingDouble(p -> before.positionOf(p).x));
            unit.oldLeft = before.positionOf(unit.ids.get(0)).x;
            for (String person : unit.ids) unit.distance = Math.min(unit.distance, distance.getOrDefault(person, Integer.MAX_VALUE));
            unit.width = unit.ids.size() * (W + G) - G;
            unit.leaf = unit.ids.size() == 1 && graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty();
            rows.computeIfAbsent(before.positionOf(id).y, k -> new ArrayList<>()).add(unit);
        }
        Model model = new Model();
        for (List<Unit> row : rows.values()) {
            Map<Unit, Unit> roots = new HashMap<>(); for (Unit unit : row) roots.put(unit, unit);
            for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
                Unit first = null;
                for (String child : family.children) {
                    Unit unit = byPerson.get(child); if (!roots.containsKey(unit)) continue;
                    if (first == null) first = unit; else join(roots, first, unit);
                }
            }
            for (Set<String> children : graph.childrenByParent.values()) {
                Unit first = null;
                for (String child : children) {
                    Unit unit = byPerson.get(child); if (!roots.containsKey(unit)) continue;
                    if (first == null) first = unit; else join(roots, first, unit);
                }
            }
            for (String id : graph.people.keySet()) for (String peer : graph.siblingsByPerson.getOrDefault(id, Collections.emptySet())) {
                Unit a = byPerson.get(id), b = byPerson.get(peer);
                if (roots.containsKey(a) && roots.containsKey(b)) join(roots, a, b);
            }
            Map<Unit, Band> groups = new LinkedHashMap<>();
            for (Unit unit : row) {
                unit.leftOrigin = Double.POSITIVE_INFINITY; unit.rightOrigin = Double.NEGATIVE_INFINITY;
                for (String id : unit.ids) if (distance.getOrDefault(id, Integer.MAX_VALUE) == unit.distance)
                    for (String parent : graph.parentsByChild.getOrDefault(id, Collections.emptySet())) {
                    Unit origin = byPerson.get(parent);
                    if (origin.rank < 0) return null;
                    double order = origin.rank + (origin.ids.indexOf(parent) + .5) / origin.ids.size();
                    unit.leftOrigin = Math.min(unit.leftOrigin, order);
                    unit.rightOrigin = Math.max(unit.rightOrigin, order);
                }
                Band band = groups.computeIfAbsent(find(roots, unit), k -> new Band());
                band.units.add(unit); unit.band = band;
            }
            List<Unit> known = new ArrayList<>();
            for (Unit unit : row) if (Double.isFinite(unit.rightOrigin)) known.add(unit);
            for (Unit unit : row) if (!Double.isFinite(unit.rightOrigin) && !known.isEmpty()) {
                Unit left = null, right = null;
                for (Unit peer : known) {
                    if (peer.oldLeft <= unit.oldLeft && (left == null || peer.oldLeft > left.oldLeft)) left = peer;
                    if (peer.oldLeft >= unit.oldLeft && (right == null || peer.oldLeft < right.oldLeft)) right = peer;
                }
                double key;
                if (left == null) key = right.rightOrigin - 1;
                else if (right == null) key = left.rightOrigin + 1;
                else if (left == right) key = left.rightOrigin;
                else key = left.rightOrigin + (right.rightOrigin - left.rightOrigin)
                    * (unit.oldLeft - left.oldLeft) / (right.oldLeft - left.oldLeft);
                unit.leftOrigin = unit.rightOrigin = key;
            }
            List<Band> bands = new ArrayList<>(groups.values());
            for (Band band : bands) {
                band.units.sort(Comparator.comparingDouble((Unit u) -> Double.isFinite(u.rightOrigin) ? u.rightOrigin : u.oldLeft / (W + G))
                    .thenComparingDouble(u -> u.leftOrigin).thenComparingDouble(u -> u.oldLeft));
                band.key = Double.NEGATIVE_INFINITY;
                int nearest = Integer.MAX_VALUE; for (Unit unit : band.units) nearest = Math.min(nearest, unit.distance);
                for (Unit unit : band.units) if (unit.distance == nearest) band.key = Math.max(band.key, unit.rightOrigin);
                if (!Double.isFinite(band.key)) band.key = band.units.get(0).oldLeft / (W + G);
            }
            bands.sort(Comparator.comparingDouble((Band b) -> b.key).thenComparingDouble(b -> b.units.get(0).oldLeft));
            List<Unit[]> edges = originEdges(graph, byPerson, before, row);
            List<Band[]> bandEdges = new ArrayList<>();
            for (Unit[] edge : edges) if (edge[0].band != edge[1].band) bandEdges.add(new Band[]{edge[0].band, edge[1].band});
            bands = topological(bands, bandEdges);
            if (bands == null) return null;
            for (Band band : bands) {
                List<Unit[]> inside = new ArrayList<>();
                for (Unit[] edge : edges) if (edge[0].band == band && edge[1].band == band) inside.add(edge);
                List<Unit> ordered = topological(band.units, inside);
                if (ordered == null) return null;
                band.units.clear(); band.units.addAll(ordered);
                optimizeOrder(band.units, graph, byPerson, inside);
                float cursor = 0;
                for (int i = 0; i < band.units.size(); i++) {
                    Unit unit = band.units.get(i);
                    if (i > 0) {
                        Unit previous = band.units.get(i - 1);
                        boolean leaves = previous.leaf && unit.leaf && sameParents(graph, previous.ids.get(0), unit.ids.get(0));
                        cursor += leaves ? G : 5 * G;
                    }
                    for (int j = 0; j < unit.ids.size(); j++) band.offsets.put(unit.ids.get(j), cursor + j * (W + G));
                    cursor += unit.width;
                }
                band.width = cursor;
            }
            int rank = 0; List<Band> flat = new ArrayList<>();
            for (Band group : bands) for (Unit unit : group.units) {
                Band band = new Band(); band.units.add(unit); band.width = unit.width;
                for (int j = 0; j < unit.ids.size(); j++) band.offsets.put(unit.ids.get(j), j * (W + G));
                band.index = model.bands.size(); model.bands.add(band); flat.add(band);
                unit.band = band; unit.rank = rank++;
            }
            model.rows.add(flat);
        }
        int zero = model.bands.size(); model.zero = zero;
        for (Band band : model.bands) {
            double sum = 0; int count = 0;
            for (Unit unit : band.units) for (String id : unit.ids) {
                float offset = band.offsets.get(id); sum += before.positionOf(id).x - offset; count++;
                if (state.people.get(id).pinned || id.equals(state.rootId)) {
                    double fixed = (before.positionOf(id).x - offset) / G;
                    model.hard.add(new Bound(band.index, zero, fixed)); model.hard.add(new Bound(zero, band.index, -fixed));
                }
            }
            band.initial = sum / count / G;
            model.hard.add(new Bound(zero, band.index, 0));
        }
        for (List<Band> row : model.rows) for (int i = 1; i < row.size(); i++) {
            Band a = row.get(i - 1), b = row.get(i);
            Unit ua = a.units.get(0), ub = b.units.get(0);
            float gap = ua.leaf && ub.leaf && sameParents(graph, ua.ids.get(0), ub.ids.get(0)) ? G : 5 * G;
            model.hard.add(new Bound(a.index, b.index, -(a.width + gap) / G));
        }
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            Band parent = byPerson.get(family.parents.get(0)).band;
            for (String id : family.parents) if (byPerson.get(id).band != parent) return null;
            Collection<String> anchor = family.children;
            List<String> leaves = leaves(graph, family);
            float oldParent = FamilyAnchorProjection.average(family.parents, before);
            float direct = Math.abs(FamilyAnchorProjection.spanCenter(family.children, before) - oldParent);
            if (leaves.size() >= 2 && family.children.size() == leaves.size() + 1
                && Math.abs(FamilyAnchorProjection.spanCenter(leaves, before) - oldParent) < direct) anchor = leaves;
            double parentOffset = 0;
            for (String id : family.parents) parentOffset += parent.offsets.get(id) + W / 2;
            parentOffset /= family.parents.size();
            String left = null, right = null;
            for (String id : anchor) {
                Unit unit = byPerson.get(id);
                if (left == null || unit.rank < byPerson.get(left).rank || (unit.rank == byPerson.get(left).rank && unit.band.offsets.get(id) < byPerson.get(left).band.offsets.get(left))) left = id;
                if (right == null || unit.rank > byPerson.get(right).rank || (unit.rank == byPerson.get(right).rank && unit.band.offsets.get(id) > byPerson.get(right).band.offsets.get(right))) right = id;
            }
            Band leftBand = byPerson.get(left).band, rightBand = byPerson.get(right).band;
            double constant = (parentOffset - (leftBand.offsets.get(left) + rightBand.offsets.get(right)) / 2 - W / 2) / G;
            float error = Math.abs(FamilyAnchorProjection.spanCenter(anchor, before) - oldParent);
            Equation equation = new Equation(parent.index, leftBand.index, rightBand.index, constant, error <= G / 2 ? 4 : 1);
            model.soft.add(equation);
            if (error <= G / 2 && intrusions(graph, before, anchor) == 0
                && compactParents(family.parents, before)) model.settled.add(equation);
        }
        return model;
    }

    private static Map<String, Integer> rootDistances(FamilyLayoutGraph graph) {
        Map<String, Integer> result = new HashMap<>(); ArrayDeque<String> queue = new ArrayDeque<>();
        if (graph.people.containsKey(graph.rootId)) { queue.add(graph.rootId); result.put(graph.rootId, 0); }
        while (!queue.isEmpty()) {
            String id = queue.removeFirst(); Set<String> neighbors = new LinkedHashSet<>();
            neighbors.addAll(graph.parentsByChild.getOrDefault(id, Collections.emptySet()));
            neighbors.addAll(graph.childrenByParent.getOrDefault(id, Collections.emptySet()));
            for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(id)) neighbors.addAll(pair.people);
            for (String peer : neighbors) if (!result.containsKey(peer)) { result.put(peer, result.get(id) + 1); queue.add(peer); }
        }
        return result;
    }

    private static List<Unit[]> originEdges(FamilyLayoutGraph graph, Map<String, Unit> units,
        LayoutSnapshot before, List<Unit> row) {
        List<Unit[]> result = new ArrayList<>();
        for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnits) {
            if (pair.people.size() != 2) continue;
            Iterator<String> iterator = pair.people.iterator(); String child = iterator.next(), peer = iterator.next();
            Set<String> a = graph.parentsByChild.getOrDefault(child, Collections.emptySet()), b = graph.parentsByChild.getOrDefault(peer, Collections.emptySet());
            if (a.isEmpty() || b.isEmpty() || !Collections.disjoint(a, b)) continue;
            float side = before.positionOf(child).x - before.positionOf(peer).x;
            Unit ua = units.get(a.iterator().next()), ub = units.get(b.iterator().next());
            if (ua == ub || !row.contains(ua) || !row.contains(ub)) continue;
            result.add(side < 0 ? new Unit[]{ua, ub} : new Unit[]{ub, ua});
        }
        return result;
    }

    private static <T> List<T> topological(List<T> preferred, List<T[]> edges) {
        List<T> result = new ArrayList<>();
        while (result.size() < preferred.size()) {
            T found = null;
            for (T node : preferred) {
                if (result.contains(node)) continue;
                boolean blocked = false;
                for (T[] edge : edges) if (edge[1] == node && !result.contains(edge[0])) { blocked = true; break; }
                if (!blocked) { found = node; break; }
            }
            if (found == null) return null;
            result.add(found);
        }
        return result;
    }

    private static void optimizeOrder(List<Unit> units, FamilyLayoutGraph graph,
        Map<String, Unit> byPerson, List<Unit[]> edges) {
        if (units.size() < 3) return;
        List<Set<Unit>> groups = new ArrayList<>();
        List<Collection<String>> children = new ArrayList<>(graph.childrenByParent.values());
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) children.add(family.children);
        for (Collection<String> ids : children) {
            Set<Unit> group = new HashSet<>(); for (String id : ids) group.add(byPerson.get(id));
            if (group.size() > 1 && units.containsAll(group) && !groups.contains(group)) groups.add(group);
        }
        List<Unit> preferred = new ArrayList<>(units);
        double score = orderCost(units, groups, edges, preferred);
        for (int pass = 0; pass < units.size() * 2; pass++) {
            List<Unit> best = null;
            for (int from = 0; from < units.size(); from++) for (int to = 0; to < units.size(); to++) {
                if (from == to || (units.size() > 48 && Math.abs(from - to) > 1)) continue;
                List<Unit> candidate = new ArrayList<>(units); Unit unit = candidate.remove(from); candidate.add(to, unit);
                double cost = orderCost(candidate, groups, edges, preferred);
                if (cost < score - .5) { score = cost; best = candidate; }
            }
            if (best == null) break;
            units.clear(); units.addAll(best);
        }
    }

    private static double orderCost(List<Unit> units, List<Set<Unit>> groups, List<Unit[]> edges, List<Unit> preferred) {
        double cost = 0;
        for (Unit[] edge : edges) if (units.indexOf(edge[0]) > units.indexOf(edge[1])) cost += 1e9;
        for (Set<Unit> group : groups) {
            int lo = units.size(), hi = -1;
            for (Unit unit : group) { int at = units.indexOf(unit); lo = Math.min(lo, at); hi = Math.max(hi, at); }
            cost += (hi - lo + 1 - group.size()) * 1e6;
        }
        for (int i = 0; i < units.size(); i++) {
            Unit a = units.get(i); cost += Math.abs(i - preferred.indexOf(a));
            for (int j = i + 1; j < units.size(); j++) if (a.leftOrigin > units.get(j).rightOrigin) cost += 100;
        }
        return cost;
    }

    private static double[] solve(Model model) {
        int size = model.bands.size() + 1;
        double[] x = new double[size]; for (Band band : model.bands) x[band.index] = band.initial;
        int[] degree = new int[size]; int maximum = 1;
        for (Equation eq : model.soft) { degree[eq.a] += eq.weight; degree[eq.b] += eq.weight; degree[eq.d] += eq.weight; maximum = Math.max(maximum, Math.max(degree[eq.a], Math.max(degree[eq.b], degree[eq.d]))); }
        // Bounds have integral right sides. Rounding a feasible solution therefore
        // preserves all differences, including pins and the grid anchor.
        if (!feasible(model.hard, x)) return null;
        if (!Double.isFinite(model.tolerance)) {
            minimizeBlocks(model, x);
            double origin = x[model.zero];
            for (int i = 0; i < size; i++) x[i] = Math.round(x[i] - origin);
            if (!feasible(model.hard, x)) return null;
            origin = x[model.zero]; for (int i = 0; i < size; i++) x[i] -= origin;
            return x;
        }
        double step = .35 / maximum;
        for (int iteration = 0; iteration < 1200; iteration++) {
            double[] gradient = new double[size];
            for (Equation eq : model.soft) { double error = eq.error(x) * eq.weight; gradient[eq.a] += error; gradient[eq.b] -= error / 2; gradient[eq.d] -= error / 2; }
            for (int i = 0; i < size; i++) x[i] -= step * gradient[i];
            for (int sweep = 0; sweep < 12; sweep++) {
                for (Bound bound : model.hard) bound.project(x);
                for (Equation eq : model.settled) if (eq.b != eq.d) eq.project(x, model.tolerance);
            }
        }
        for (int sweep = 0; sweep < 2000; sweep++) {
            for (Bound bound : model.hard) bound.project(x);
            double violation = 0;
            for (Equation eq : model.settled) if (eq.b != eq.d) { violation = Math.max(violation, Math.abs(eq.error(x)) - model.tolerance); eq.project(x, model.tolerance); }
            if (violation < 1e-6) break;
        }
        if (!feasible(model.hard, x)) return null;
        double origin = x[model.zero]; for (int i = 0; i < size; i++) x[i] = Math.round(x[i] - origin);
        if (!feasible(model.hard, x)) return null;
        origin = x[model.zero]; for (int i = 0; i < size; i++) x[i] -= origin;
        for (Equation eq : model.settled) if (Math.abs(eq.error(x)) > model.tolerance + .51) return null;
        return x;
    }

    /** Exact line minimization carries a tight row run together instead of
     * freezing its members against one another during independent projections. */
    private static void minimizeBlocks(Model model, double[] x) {
        for (int pass = 0; pass < 300; pass++) {
            double largest = 0;
            for (int ordinal = 0; ordinal < x.length - 1; ordinal++) {
                int seed = pass % 2 == 0 ? ordinal : x.length - 2 - ordinal;
                for (int direction : new int[]{-1, 1}) {
                    boolean[] moving = new boolean[x.length]; moving[seed] = true;
                    boolean added;
                    do {
                        added = false;
                        for (Bound bound : model.hard) {
                            if (moving[bound.a] == moving[bound.b]) continue;
                            int coefficient = (moving[bound.a] ? 1 : 0) - (moving[bound.b] ? 1 : 0);
                            if (coefficient * direction <= 0 || bound.limit - x[bound.a] + x[bound.b] > 1e-6) continue;
                            int peer = moving[bound.a] ? bound.b : bound.a;
                            if (!moving[peer]) { moving[peer] = true; added = true; }
                        }
                    } while (added && !moving[model.zero]);
                    if (moving[model.zero]) continue;
                    double gradient = 0, curvature = 0;
                    for (Equation eq : model.soft) {
                        double coefficient = (moving[eq.a] ? 1 : 0) - (moving[eq.b] ? .5 : 0) - (moving[eq.d] ? .5 : 0);
                        gradient += eq.weight * coefficient * eq.error(x);
                        curvature += eq.weight * coefficient * coefficient;
                    }
                    if (curvature == 0) continue;
                    double delta = -gradient / curvature;
                    if (delta * direction <= 1e-6) continue;
                    double available = Double.POSITIVE_INFINITY;
                    for (Bound bound : model.hard) {
                        int coefficient = (moving[bound.a] ? 1 : 0) - (moving[bound.b] ? 1 : 0);
                        if (coefficient * direction > 0) available = Math.min(available, Math.max(0, bound.limit - x[bound.a] + x[bound.b]));
                    }
                    delta = Math.copySign(Math.min(Math.abs(delta), available), delta);
                    largest = Math.max(largest, Math.abs(delta));
                    for (int i = 0; i < x.length; i++) if (moving[i]) x[i] += delta;
                }
            }
            if (largest < 1e-4) break;
        }
    }

    /** Difference constraints: propagation either produces feasibility or exposes a cycle. */
    private static boolean feasible(List<Bound> bounds, double[] x) {
        for (int pass = 0; pass < x.length; pass++) {
            boolean changed = false;
            for (Bound bound : bounds) if (x[bound.a] > x[bound.b] + bound.limit + 1e-7) {
                x[bound.a] = x[bound.b] + bound.limit; changed = true;
            }
            if (!changed) return true;
        }
        return false;
    }

    static Quality quality(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        Quality result = new Quality();
        Set<String> visited = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (visited.contains(id)) continue;
            Set<String> household = new HashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(id);
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            while (!queue.isEmpty()) {
                String next = queue.removeFirst(); if (!household.add(next)) continue;
                visited.add(next); lo = Math.min(lo, snapshot.positionOf(next).x); hi = Math.max(hi, snapshot.positionOf(next).x + W);
                for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(next)) queue.addAll(pair.people);
            }
            result.householdSlack += Math.max(0, hi - lo - household.size() * (W + G) + G);
        }
        for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnits) if (pair.people.size() == 2) {
            Iterator<String> iterator = pair.people.iterator();
            result.unionSlack += Math.max(0, Math.abs(snapshot.positionOf(iterator.next()).x - snapshot.positionOf(iterator.next()).x) - W - G);
        }
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            float parent = FamilyAnchorProjection.average(family.parents, snapshot);
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            for (String id : family.children) {
                lo = Math.min(lo, snapshot.positionOf(id).x + W / 2); hi = Math.max(hi, snapshot.positionOf(id).x + W / 2);
            }
            result.detached += Math.max(0, Math.max(lo - parent, parent - hi) - 5 * G);
            List<String> leaves = leaves(graph, family);
            double error = Math.abs((lo + hi) / 2 - parent);
            if (leaves.size() >= 2 && family.children.size() == leaves.size() + 1)
                error = Math.min(error, Math.abs(FamilyAnchorProjection.spanCenter(leaves, snapshot) - parent));
            result.anchorError += error;
            result.tears += intrusions(graph, snapshot, family.children);
            result.terminalTears += intrusions(graph, snapshot, leaves);
        }
        return result;
    }

    private static boolean compactParents(Collection<String> parents, LayoutSnapshot snapshot) {
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (String id : parents) { lo = Math.min(lo, snapshot.positionOf(id).x); hi = Math.max(hi, snapshot.positionOf(id).x + W); }
        return hi - lo <= parents.size() * (W + G) - G + .5f;
    }

    static int intrusions(FamilyLayoutGraph graph, LayoutSnapshot snapshot, Collection<String> children) {
        if (children.size() < 2) return 0;
        Set<String> own = new HashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(children);
        float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
        for (String id : children) { lo = Math.min(lo, snapshot.positionOf(id).x + W / 2); hi = Math.max(hi, snapshot.positionOf(id).x + W / 2); }
        while (!queue.isEmpty()) {
            String next = queue.removeFirst(); if (!own.add(next)) continue;
            for (FamilyLayoutGraph.PartnerUnit pair : graph.partnerUnitsOf(next)) queue.addAll(pair.people);
        }
        int count = 0; float row = snapshot.positionOf(children.iterator().next()).y;
        for (String id : graph.people.keySet()) {
            LayoutSnapshot.Position p = snapshot.positionOf(id);
            if (!own.contains(id) && p.y == row && p.x + W / 2 > lo && p.x + W / 2 < hi) count++;
        }
        return count;
    }

    private static boolean keepsOriginSides(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        for (String child : graph.people.keySet()) for (String peer : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
            Set<String> a = graph.parentsByChild.getOrDefault(child, Collections.emptySet()), b = graph.parentsByChild.getOrDefault(peer, Collections.emptySet());
            if (a.isEmpty() || b.isEmpty() || !Collections.disjoint(a, b)) continue;
            float side = before.positionOf(child).x - before.positionOf(peer).x;
            float old = FamilyAnchorProjection.average(a, before) - FamilyAnchorProjection.average(b, before);
            float next = FamilyAnchorProjection.average(a, after) - FamilyAnchorProjection.average(b, after);
            if (old * side > 0 && next * side < 0) {
                return false;
            }
        }
        return true;
    }
    private static boolean sameParents(FamilyLayoutGraph graph, String a, String b) {
        Set<String> parents = graph.parentsByChild.getOrDefault(a, Collections.emptySet());
        return !parents.isEmpty() && parents.equals(graph.parentsByChild.get(b));
    }
    private static List<String> leaves(FamilyLayoutGraph graph, FamilyLayoutGraph.ParentFamily family) {
        List<String> result = new ArrayList<>();
        for (String id : family.children) if (graph.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()
            && graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()) result.add(id);
        return result;
    }
    private static Unit find(Map<Unit, Unit> roots, Unit unit) { Unit parent = roots.get(unit); if (parent != unit) roots.put(unit, parent = find(roots, parent)); return parent; }
    private static void join(Map<Unit, Unit> roots, Unit a, Unit b) { roots.put(find(roots, a), find(roots, b)); }
    static final class Quality {
        int tears, terminalTears; double detached, unionSlack, anchorError, householdSlack;
        double cost() { return tears * G + terminalTears * (W + 5 * G) + detached + unionSlack * 3 + anchorError * .25; }
    }
    private static final class Unit {
        final List<String> ids = new ArrayList<>(); float oldLeft, width; double leftOrigin, rightOrigin; int rank = -1, distance = Integer.MAX_VALUE; boolean leaf; Band band;
    }
    private static final class Band {
        final List<Unit> units = new ArrayList<>(); final Map<String, Float> offsets = new HashMap<>(); int index; float width; double key, initial;
    }
    private static final class Model {
        final List<Band> bands = new ArrayList<>(); final List<List<Band>> rows = new ArrayList<>();
        final List<Bound> hard = new ArrayList<>(); final List<Equation> soft = new ArrayList<>(), settled = new ArrayList<>(); int zero; double tolerance;
    }
    private static final class Equation {
        final int a, b, d, weight; final double c;
        Equation(int a, int b, int d, double c, int weight) { this.a = a; this.b = b; this.d = d; this.c = c; this.weight = weight; }
        double error(double[] x) { return x[a] - (x[b] + x[d]) / 2 + c; }
        void project(double[] x, double tolerance) {
            double error = error(x), excess = Math.abs(error) - tolerance;
            if (excess <= 0) return;
            double delta = Math.copySign(excess, error) / (b == d ? 2 : 1.5);
            x[a] -= delta; x[b] += delta / 2; x[d] += delta / 2;
        }
    }
    private static final class Bound {
        final int a, b; final double limit;
        Bound(int a, int b, double limit) { this.a = a; this.b = b; this.limit = Math.floor(limit + 1e-7); }
        void project(double[] x) { double error = x[a] - x[b] - limit; if (error > 0) { x[a] -= error / 2; x[b] += error / 2; } }
    }
}
