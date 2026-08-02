package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic, grid-based family layout.
 *
 * <p>The engine treats partners/co-parents as a row block, assigns every block
 * to one generation and then packs each generation around the actual parent
 * and child anchors. This keeps the two ancestry sides stable without forcing
 * either side to "own" a shared child block.</p>
 */
final class TreeLayoutEngine {
    static final float CARD_W = 280f;
    static final float CARD_H = 160f;
    static final float GRID = 40f;
    static final float LEVEL_GAP = GRID * 12f;
    static final float SIBLING_GAP = GRID;

    static final float SURFACE_W = 24000f;
    static final float SURFACE_H = 16000f;
    static final int MIN_SURFACE_W = 1200;
    static final int MIN_SURFACE_H = 800;
    static final int MAX_SURFACE_SIZE = 100000;
    private static final float PARTNER_GAP = GRID;
    private static final float SIBLING_FAMILY_GAP = GRID * 2f;
    private static final float BRANCH_GAP = GRID * 5f;
    private static final float MARGIN_X = GRID * 4f;
    private static final float MARGIN_Y = GRID * 4f;
    // Place a four-row card fully between generation guides. This used to be
    // GRID * 9 for three-row cards and left the new card one row below the line.
    private static final float GUIDE_CARD_OFFSET = LEVEL_GAP - CARD_H;
    private static final float ORDER_SCALE = GRID * 30f;
    private static final int ALIGNMENT_PASSES = 6;

    private TreeLayoutEngine() {
    }

    static int normalizeSurfaceWidth(int value) {
        return Math.max(MIN_SURFACE_W, Math.min(MAX_SURFACE_SIZE, value));
    }

    static int normalizeSurfaceHeight(int value) {
        return Math.max(MIN_SURFACE_H, Math.min(MAX_SURFACE_SIZE, value));
    }

    static float surfaceWidth(TreeState state) {
        return state == null ? SURFACE_W : normalizeSurfaceWidth(state.workspaceWidth);
    }

    static float surfaceHeight(TreeState state) {
        return state == null ? SURFACE_H : normalizeSurfaceHeight(state.workspaceHeight);
    }

    static void ensurePositions(TreeState state) {
        if (state == null || state.people.isEmpty()) return;
        if (!hasUsableSavedLayout(state)) layout(state);
        int index = 0;
        for (Person person : state.people.values()) {
            if (!isValidPosition(person)) {
                Point open = findOpenSpot(
                    state,
                    new Point(
                        surfaceWidth(state) / 2f + (index % 4) * (CARD_W + GRID),
                        surfaceHeight(state) / 2f + GRID * 14f + (index / 4) * (CARD_H + GRID)));
                person.x = open.x;
                person.y = open.y;
            } else {
                person.x = clampSnap(person.x, 0f, surfaceWidth(state) - CARD_W);
                person.y = clampSnap(person.y, 0f, surfaceHeight(state) - CARD_H);
            }
            index++;
        }
    }

    private static boolean hasUsableSavedLayout(TreeState state) {
        if (!hasSavedPositions(state)) return false;
        Relations relations = buildRelations(state);
        UnitGraph graph = makeUnits(state, relations);
        return !graph.units.isEmpty() && assignLevelsFromSavedRows(state, graph, relations);
    }

    static boolean hasSavedPositions(TreeState state) {
        if (state == null) return false;
        for (Person person : state.people.values()) {
            if (isValidPosition(person)) return true;
        }
        return false;
    }

    static void layout(TreeState state) {
        if (state == null || state.people.isEmpty()) return;

        Relations relations = buildRelations(state);
        UnitGraph graph = makeUnits(state, relations);
        if (graph.units.isEmpty()) return;

        String rootId = state.people.containsKey(state.rootId)
            ? state.rootId
            : state.people.keySet().iterator().next();
        state.rootId = rootId;
        Unit root = graph.personToUnit.get(rootId);
        if (root == null) root = graph.units.get(0);

        assignLevels(state, graph, relations, root);
        assignOrderHints(state, graph, relations, root);
        Map<Integer, List<Unit>> rows = makeRows(state, graph);
        seedRows(rows, root);
        alignRows(rows, graph, relations, root.level);
        separateFamilyComponents(graph, relations, root);
        applyPositions(state, graph);
    }

    private static Relations buildRelations(TreeState state) {
        Relations result = new Relations();
        // Build directed generations first, then partner/family blocks.
        for (Relation link : state.links) {
            if (link == null
                || !state.people.containsKey(link.from)
                || !state.people.containsKey(link.to)
                || link.from.equals(link.to)) {
                continue;
            }
            if ("parent".equals(link.type)) {
                addToMapSet(result.parentsByChild, link.to, link.from);
                addToMapSet(result.childrenByParent, link.from, link.to);
            } else if ("sibling".equals(link.type)) {
                addToMapSet(result.siblingsByPerson, link.from, link.to);
                addToMapSet(result.siblingsByPerson, link.to, link.from);
            }
        }
        for (Relation link : state.links) {
            if (link == null
                || !state.people.containsKey(link.from)
                || !state.people.containsKey(link.to)
                || link.from.equals(link.to)) {
                continue;
            }
            boolean partner = "partner".equals(link.type) || "family".equals(link.type);
            if (partner) {
                addToMapSet(result.partnersByPerson, link.from, link.to);
                addToMapSet(result.partnersByPerson, link.to, link.from);
                if ("left".equals(link.side)) {
                    result.preferredSide.put(link.to, 0);
                    result.preferredSide.putIfAbsent(link.from, 2);
                } else {
                    result.preferredSide.put(link.to, 2);
                    result.preferredSide.putIfAbsent(link.from, 0);
                }
            }
        }
        return result;
    }

    private static UnitGraph makeUnits(TreeState state, Relations relations) {
        List<String> ids = new ArrayList<>(state.people.keySet());
        Disjoint disjoint = new Disjoint(ids);

        for (Map.Entry<String, Set<String>> entry : relations.partnersByPerson.entrySet()) {
            for (String partner : entry.getValue()) disjoint.union(entry.getKey(), partner);
        }
        // Co-parents form the same visual family block even when the partner
        // relation was not explicitly entered.
        for (Set<String> parents : relations.parentsByChild.values()) {
            String first = null;
            for (String parent : parents) {
                if (!state.people.containsKey(parent)) continue;
                if (first == null) first = parent;
                else disjoint.union(first, parent);
            }
        }

        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String id : ids) {
            groups.computeIfAbsent(disjoint.find(id), ignored -> new ArrayList<>()).add(id);
        }

        UnitGraph graph = new UnitGraph();
        for (List<String> people : groups.values()) {
            people.sort((a, b) -> comparePeople(state, relations, a, b));
            Unit unit = new Unit(String.join("|", people), people);
            for (String id : people) unit.personRefs.add(state.people.get(id));
            graph.units.add(unit);
            graph.unitById.put(unit.id, unit);
            for (String id : people) graph.personToUnit.put(id, unit);
        }

        for (Map.Entry<String, Set<String>> entry : relations.childrenByParent.entrySet()) {
            Unit parent = graph.personToUnit.get(entry.getKey());
            if (parent == null) continue;
            for (String childId : entry.getValue()) {
                Unit child = graph.personToUnit.get(childId);
                if (child == null || child == parent) continue;
                parent.children.add(child.id);
                child.parents.add(parent.id);
            }
        }
        return graph;
    }

    private static void assignLevels(
        TreeState state,
        UnitGraph graph,
        Relations relations,
        Unit root
    ) {
        // Preserve a manual generation plan only when every saved row agrees
        // with partner, sibling and parent/child constraints. A pile or a
        // single imported line must be rebuilt from the relationship graph.
        if (assignLevelsFromSavedRows(state, graph, relations)) return;

        Map<String, Integer> assigned = new HashMap<>();
        assignComponentLevels(root, 0, assigned, graph, relations);

        Integer rootYear = unitYear(root, state);
        List<Unit> remaining = new ArrayList<>(graph.units);
        remaining.sort((a, b) -> compareUnits(state, a, b));
        for (Unit unit : remaining) {
            if (assigned.containsKey(unit.id)) continue;
            int estimated = 0;
            Integer year = unitYear(unit, state);
            if (rootYear != null && year != null) {
                estimated = Math.round((year - rootYear) / 28f);
            }
            assignComponentLevels(unit, estimated, assigned, graph, relations);
        }

        for (Unit unit : graph.units) unit.level = assigned.getOrDefault(unit.id, 0);

        // Repair ambiguous/cyclic data conservatively while keeping the focal
        // generation stable whenever it participates in the conflict.
        for (int pass = 0; pass < graph.units.size() + 2; pass++) {
            boolean changed = false;
            for (Unit parent : graph.units) {
                for (String childId : parent.children) {
                    Unit child = graph.unitById.get(childId);
                    if (child == null || child.level > parent.level) continue;
                    if (edgeBelongsToCycle(parent, child, graph)) continue;
                    if (child == root) parent.level = child.level - 1;
                    else child.level = parent.level + 1;
                    changed = true;
                }
            }
            if (!changed) break;
        }

        int min = Integer.MAX_VALUE;
        for (Unit unit : graph.units) min = Math.min(min, unit.level);
        if (min == Integer.MAX_VALUE) min = 0;
        if (min < 0) {
            for (Unit unit : graph.units) unit.level -= min;
        }
    }

    private static boolean assignLevelsFromSavedRows(
        TreeState state,
        UnitGraph graph,
        Relations relations
    ) {
        if (savedRowsContainOverlaps(state)) return false;
        List<Float> saved = new ArrayList<>();
        for (Person person : state.people.values()) {
            if (isValidPosition(person)) saved.add(snap(person.y));
        }
        int required = Math.max(1, Math.round(state.people.size() * 0.65f));
        if (saved.size() < required) return false;
        saved.sort(Float::compareTo);

        List<Float> rows = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (float value : saved) {
            if (rows.isEmpty()
                || Math.abs(value - rows.get(rows.size() - 1)) > GRID * 2f) {
                rows.add(value);
                counts.add(1);
                continue;
            }
            int last = rows.size() - 1;
            int count = counts.get(last);
            rows.set(last, snap((rows.get(last) * count + value) / (count + 1f)));
            counts.set(last, count + 1);
        }
        if (rows.size() > 16 || rows.size() > Math.max(4, state.people.size() / 3)) {
            return false;
        }

        Map<String, Integer> personRows = new HashMap<>();
        for (Person person : state.people.values()) {
            if (!isValidPosition(person)) continue;
            personRows.put(person.id, nearestRowIndex(snap(person.y), rows));
        }

        for (Unit unit : graph.units) {
            List<Integer> hints = new ArrayList<>();
            for (Person person : unit.personRefs) {
                if (!isValidPosition(person)) continue;
                Integer row = personRows.get(person.id);
                if (row != null) hints.add(row);
            }
            if (hints.isEmpty()) return false;
            Collections.sort(hints);
            // Partners and co-parents are one visual block and therefore must
            // already agree on a generation before saved rows can be trusted.
            if (!hints.get(0).equals(hints.get(hints.size() - 1))) return false;
            unit.level = hints.get(hints.size() / 2);
        }

        boolean hasParentEdge = false;
        for (Unit parent : graph.units) {
            for (String childId : parent.children) {
                Unit child = graph.unitById.get(childId);
                if (child == null) continue;
                hasParentEdge = true;
                if (child.level != parent.level + 1) return false;
            }
        }
        if (hasParentEdge && rows.size() < 2) return false;

        for (Map.Entry<String, Set<String>> entry : relations.siblingsByPerson.entrySet()) {
            Unit first = graph.personToUnit.get(entry.getKey());
            if (first == null) continue;
            for (String siblingId : entry.getValue()) {
                Unit second = graph.personToUnit.get(siblingId);
                if (second != null && second != first && second.level != first.level) return false;
            }
        }
        return true;
    }

    private static boolean savedRowsContainOverlaps(TreeState state) {
        List<Person> people = new ArrayList<>(state.people.values());
        for (int i = 0; i < people.size(); i++) {
            Person first = people.get(i);
            if (!isValidPosition(first)) continue;
            for (int j = i + 1; j < people.size(); j++) {
                Person second = people.get(j);
                if (!isValidPosition(second)) continue;
                boolean horizontalOverlap = Math.abs(first.x - second.x) < CARD_W;
                boolean verticalOverlap = Math.abs(first.y - second.y) < CARD_H;
                if (horizontalOverlap && verticalOverlap) return true;
            }
        }
        return false;
    }

    private static int nearestRowIndex(float value, List<Float> rows) {
        int best = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < rows.size(); i++) {
            float next = Math.abs(value - rows.get(i));
            if (next < distance) {
                best = i;
                distance = next;
            }
        }
        return best;
    }

    private static boolean edgeBelongsToCycle(Unit parent, Unit child, UnitGraph graph) {
        ArrayDeque<Unit> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(child);
        while (!queue.isEmpty()) {
            Unit current = queue.removeFirst();
            if (current == null || !seen.add(current.id)) continue;
            if (current == parent) return true;
            for (String childId : current.children) {
                queue.add(graph.unitById.get(childId));
            }
        }
        return false;
    }

    private static void assignComponentLevels(
        Unit start,
        int startLevel,
        Map<String, Integer> assigned,
        UnitGraph graph,
        Relations relations
    ) {
        ArrayDeque<QueueLevel> queue = new ArrayDeque<>();
        queue.add(new QueueLevel(start, startLevel));
        while (!queue.isEmpty()) {
            QueueLevel item = queue.removeFirst();
            if (item.unit == null || assigned.containsKey(item.unit.id)) continue;
            assigned.put(item.unit.id, item.level);
            for (String parentId : item.unit.parents) {
                queue.add(new QueueLevel(graph.unitById.get(parentId), item.level - 1));
            }
            for (String childId : item.unit.children) {
                queue.add(new QueueLevel(graph.unitById.get(childId), item.level + 1));
            }
            for (String siblingId : siblingUnitIds(item.unit, relations, graph.personToUnit)) {
                queue.add(new QueueLevel(graph.unitById.get(siblingId), item.level));
            }
            for (String relativeId : relativeUnitIds(item.unit, relations, graph.personToUnit)) {
                queue.add(new QueueLevel(graph.unitById.get(relativeId), item.level));
            }
        }
    }

    private static void assignOrderHints(
        TreeState state,
        UnitGraph graph,
        Relations relations,
        Unit root
    ) {
        for (Unit unit : graph.units) {
            unit.orderHint = Double.NaN;
            unit.orderSamples = 0;
        }
        root.orderHint = 0d;
        root.orderSamples = 1;

        propagateHintsFrom(state, graph, relations, root);

        double disconnected = 4d;
        List<Unit> unresolved = new ArrayList<>();
        for (Unit unit : graph.units) {
            if (!Double.isFinite(unit.orderHint)) unresolved.add(unit);
        }
        unresolved.sort((a, b) -> compareUnits(state, a, b));
        for (Unit start : unresolved) {
            if (Double.isFinite(start.orderHint)) continue;
            start.orderHint = disconnected;
            start.orderSamples = 1;
            disconnected += 4d;
            propagateHintsFrom(state, graph, relations, start);
        }
    }

    private static void propagateHintsFrom(
        TreeState state,
        UnitGraph graph,
        Relations relations,
        Unit start
    ) {
        ArrayDeque<QueueDepth> queue = new ArrayDeque<>();
        queue.add(new QueueDepth(start, 0));
        Set<String> expanded = new HashSet<>();
        while (!queue.isEmpty()) {
            QueueDepth item = queue.removeFirst();
            Unit current = item.unit;
            if (current == null || !expanded.add(current.id)) continue;
            double spread = 1d / Math.pow(3d, item.depth + 1d);
            for (String parentId : current.parents) {
                Unit parent = graph.unitById.get(parentId);
                if (parent == null) continue;
                double hint = current.orderHint
                    + slotOffsetForParent(parent, current, relations) * spread;
                offerHintIfUnset(parent, hint);
                queue.add(new QueueDepth(parent, item.depth + 1));
            }
            List<Unit> children = childUnits(current, graph);
            children.sort((a, b) -> compareUnits(state, a, b));
            for (int i = 0; i < children.size(); i++) {
                Unit child = children.get(i);
                double offset = normalizedIndex(i, children.size());
                offerHintIfUnset(child, current.orderHint + offset * spread);
                queue.add(new QueueDepth(child, item.depth + 1));
            }
            List<Unit> siblings = new ArrayList<>();
            for (String siblingId : siblingUnitIds(
                current,
                relations,
                graph.personToUnit)) {
                Unit sibling = graph.unitById.get(siblingId);
                if (sibling != null) siblings.add(sibling);
            }
            siblings.sort((a, b) -> compareUnits(state, a, b));
            for (int i = 0; i < siblings.size(); i++) {
                Unit sibling = siblings.get(i);
                offerHintIfUnset(
                    sibling,
                    current.orderHint + normalizedIndex(i, siblings.size()) * spread);
                queue.add(new QueueDepth(sibling, item.depth + 1));
            }
            List<Unit> relatives = new ArrayList<>();
            for (String relativeId : relativeUnitIds(
                current,
                relations,
                graph.personToUnit)) {
                Unit relative = graph.unitById.get(relativeId);
                if (relative != null) relatives.add(relative);
            }
            relatives.sort((a, b) -> compareUnits(state, a, b));
            for (int i = 0; i < relatives.size(); i++) {
                Unit relative = relatives.get(i);
                offerHintIfUnset(
                    relative,
                    current.orderHint + normalizedIndex(i, relatives.size()) * spread);
                queue.add(new QueueDepth(relative, item.depth + 1));
            }
        }
    }

    private static void offerHintIfUnset(Unit unit, double hint) {
        if (!Double.isFinite(hint)) return;
        if (!Double.isFinite(unit.orderHint)) {
            unit.orderHint = hint;
            unit.orderSamples = 1;
        }
    }

    private static double slotOffsetForParent(
        Unit parent,
        Unit child,
        Relations relations
    ) {
        double sum = 0d;
        int count = 0;
        for (int i = 0; i < child.people.size(); i++) {
            String childPerson = child.people.get(i);
            Set<String> parents = relations.parentsByChild.getOrDefault(
                childPerson,
                Collections.emptySet());
            for (String parentPerson : parent.people) {
                if (!parents.contains(parentPerson)) continue;
                sum += normalizedIndex(i, child.people.size());
                count++;
            }
        }
        return count == 0 ? 0d : sum / count;
    }

    private static double normalizedIndex(int index, int size) {
        if (size <= 1) return 0d;
        return (index * 2d / (size - 1d)) - 1d;
    }

    private static Map<Integer, List<Unit>> makeRows(TreeState state, UnitGraph graph) {
        Map<Integer, List<Unit>> rows = new TreeMap<>();
        for (Unit unit : graph.units) {
            rows.computeIfAbsent(unit.level, ignored -> new ArrayList<>()).add(unit);
        }
        for (List<Unit> row : rows.values()) {
            row.sort((a, b) -> {
                int hint = Double.compare(a.orderHint, b.orderHint);
                return hint != 0 ? hint : compareUnits(state, a, b);
            });
        }
        return rows;
    }

    private static void seedRows(Map<Integer, List<Unit>> rows, Unit root) {
        for (List<Unit> row : rows.values()) {
            Map<String, Float> desired = new HashMap<>();
            for (Unit unit : row) {
                float center = (float) (unit.orderHint * ORDER_SCALE);
                Float pinned = pinnedCenter(unit);
                desired.put(unit.id, pinned == null ? center : pinned);
            }
            if (row.contains(root) && pinnedCenter(root) == null) desired.put(root.id, 0f);
            packRow(row, desired);
        }
    }

    private static void alignRows(
        Map<Integer, List<Unit>> rows,
        UnitGraph graph,
        Relations relations,
        int rootLevel
    ) {
        int minLevel = rows.isEmpty() ? 0 : rows.keySet().iterator().next();
        int maxLevel = 0;
        for (Integer level : rows.keySet()) maxLevel = Math.max(maxLevel, level);

        for (int pass = 0; pass < ALIGNMENT_PASSES; pass++) {
            for (int level = rootLevel - 1; level >= minLevel; level--) {
                List<Unit> row = rows.get(level);
                if (row != null) packAlignedRow(row, desiredCenters(row, graph, relations, true));
            }
            for (int level = rootLevel + 1; level <= maxLevel; level++) {
                List<Unit> row = rows.get(level);
                if (row != null) packAlignedRow(row, desiredCenters(row, graph, relations, false));
            }
            for (int level = minLevel; level <= maxLevel; level++) {
                if (level == rootLevel) continue;
                List<Unit> row = rows.get(level);
                if (row != null) packAlignedRow(
                    row,
                    desiredCenters(row, graph, relations, level < rootLevel));
            }
        }
    }

    /**
     * Barycentric row ordering reduces parent-line crossings before the exact
     * spacing pass. The id fallback keeps the result deterministic.
     */
    private static void packAlignedRow(List<Unit> row, Map<String, Float> desired) {
        row.sort((first, second) -> {
            int target = Float.compare(
                desired.getOrDefault(first.id, first.center),
                desired.getOrDefault(second.id, second.center));
            if (target != 0) return target;
            int hint = Double.compare(first.orderHint, second.orderHint);
            return hint != 0 ? hint : first.id.compareTo(second.id);
        });
        packRow(row, desired);
    }

    private static Map<String, Float> desiredCenters(
        List<Unit> row,
        UnitGraph graph,
        Relations relations,
        boolean useChildren
    ) {
        Map<String, Float> desired = new HashMap<>();
        for (Unit unit : row) {
            Float pinned = pinnedCenter(unit);
            if (pinned != null) {
                desired.put(unit.id, pinned);
                continue;
            }
            List<Float> anchors = useChildren
                ? anchorsInChildren(unit, graph, relations)
                : anchorsInParents(unit, graph, relations);
            if (anchors.isEmpty()) {
                desired.put(unit.id, unit.center);
                continue;
            }
            Collections.sort(anchors);
            float median;
            int middle = anchors.size() / 2;
            if ((anchors.size() & 1) == 1) median = anchors.get(middle);
            else median = (anchors.get(middle - 1) + anchors.get(middle)) / 2f;
            desired.put(unit.id, unit.center * 0.18f + median * 0.82f);
        }
        return desired;
    }

    private static List<Float> anchorsInChildren(
        Unit parent,
        UnitGraph graph,
        Relations relations
    ) {
        List<Float> anchors = new ArrayList<>();
        for (String childUnitId : parent.children) {
            Unit child = graph.unitById.get(childUnitId);
            if (child == null || child.level != parent.level + 1) continue;
            for (int i = 0; i < child.people.size(); i++) {
                String childPerson = child.people.get(i);
                Set<String> parentIds = relations.parentsByChild.getOrDefault(
                    childPerson,
                    Collections.emptySet());
                boolean connected = false;
                for (String parentPerson : parent.people) {
                    if (parentIds.contains(parentPerson)) {
                        connected = true;
                        break;
                    }
                }
                if (connected) anchors.add(personCenter(child, i));
            }
        }
        return anchors;
    }

    private static List<Float> anchorsInParents(
        Unit child,
        UnitGraph graph,
        Relations relations
    ) {
        List<Float> anchors = new ArrayList<>();
        for (int childIndex = 0; childIndex < child.people.size(); childIndex++) {
            String childPerson = child.people.get(childIndex);
            for (String parentPerson : relations.parentsByChild.getOrDefault(
                childPerson,
                Collections.emptySet())) {
                Unit parent = graph.personToUnit.get(parentPerson);
                if (parent == null || parent.level + 1 != child.level) continue;
                int parentIndex = parent.people.indexOf(parentPerson);
                if (parentIndex >= 0) anchors.add(personCenter(parent, parentIndex));
            }
        }
        return anchors;
    }

    private static float personCenter(Unit unit, int personIndex) {
        return unit.left() + personIndex * (CARD_W + PARTNER_GAP) + CARD_W / 2f;
    }

    /**
     * Packs a row with isotonic regression. The result is the closest ordered
     * placement to all desired centers while respecting every family gap.
     */
    private static void packRow(List<Unit> row, Map<String, Float> desired) {
        if (row == null || row.isEmpty()) return;
        int size = row.size();
        double[] cumulative = new double[size];
        double[] transformed = new double[size];
        double[] weights = new double[size];
        for (int i = 0; i < size; i++) {
            Unit unit = row.get(i);
            if (i > 0) {
                Unit previous = row.get(i - 1);
                cumulative[i] = cumulative[i - 1]
                    + previous.width / 2d
                    + gapBetween(previous, unit)
                    + unit.width / 2d;
            }
            float target = desired.getOrDefault(unit.id, unit.center);
            transformed[i] = target - cumulative[i];
            weights[i] = pinnedCenter(unit) == null ? 1d : 32d;
        }

        double[] fitted = isotonic(transformed, weights);
        float previousRight = Float.NEGATIVE_INFINITY;
        Unit previous = null;
        for (int i = 0; i < size; i++) {
            Unit unit = row.get(i);
            float center = snap((float) (fitted[i] + cumulative[i]) - unit.width / 2f)
                + unit.width / 2f;
            float left = center - unit.width / 2f;
            if (previous != null) {
                left = Math.max(left, previousRight + gapBetween(previous, unit));
            }
            left = snap(left);
            unit.center = left + unit.width / 2f;
            previousRight = left + unit.width;
            previous = unit;
        }
    }

    private static double[] isotonic(double[] values, double[] weights) {
        int n = values.length;
        double[] blockValue = new double[n];
        double[] blockWeight = new double[n];
        int[] blockStart = new int[n];
        int[] blockEnd = new int[n];
        int blocks = 0;

        for (int i = 0; i < n; i++) {
            blockValue[blocks] = values[i];
            blockWeight[blocks] = weights[i];
            blockStart[blocks] = i;
            blockEnd[blocks] = i;
            blocks++;
            while (blocks >= 2 && blockValue[blocks - 2] > blockValue[blocks - 1]) {
                int left = blocks - 2;
                double weight = blockWeight[left] + blockWeight[left + 1];
                blockValue[left] = (
                    blockValue[left] * blockWeight[left]
                        + blockValue[left + 1] * blockWeight[left + 1])
                    / weight;
                blockWeight[left] = weight;
                blockEnd[left] = blockEnd[left + 1];
                blocks--;
            }
        }

        double[] result = new double[n];
        for (int block = 0; block < blocks; block++) {
            for (int i = blockStart[block]; i <= blockEnd[block]; i++) {
                result[i] = blockValue[block];
            }
        }
        return result;
    }

    private static float gapBetween(Unit left, Unit right) {
        if (areSiblings(left, right)) {
            boolean leftSimple = left.people.size() == 1 && left.children.isEmpty();
            boolean rightSimple = right.people.size() == 1 && right.children.isEmpty();
            return leftSimple && rightSimple ? SIBLING_GAP : SIBLING_FAMILY_GAP;
        }
        return BRANCH_GAP;
    }

    private static boolean areSiblings(Unit first, Unit second) {
        if (first == null || second == null || first == second) return false;
        for (String parent : first.parents) {
            if (second.parents.contains(parent)) return true;
        }
        for (String sibling : first.siblings) {
            if (sibling.equals(second.id)) return true;
        }
        return false;
    }

    private static void separateFamilyComponents(
        UnitGraph graph,
        Relations relations,
        Unit root
    ) {
        List<List<Unit>> components = weakComponents(graph, relations);
        components.sort((a, b) -> {
            boolean pinnedA = componentHasPinned(a);
            boolean pinnedB = componentHasPinned(b);
            if (pinnedA != pinnedB) return pinnedA ? -1 : 1;
            if (pinnedA) return Float.compare(componentMin(a), componentMin(b));
            if (a.contains(root) != b.contains(root)) return a.contains(root) ? -1 : 1;
            return componentKey(a).compareTo(componentKey(b));
        });

        float cursor = MARGIN_X;
        for (List<Unit> component : components) {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (Unit unit : component) {
                min = Math.min(min, unit.left());
                max = Math.max(max, unit.right());
            }
            if (min == Float.MAX_VALUE) continue;
            float shift = componentHasPinned(component) ? 0f : snap(cursor - min);
            for (Unit unit : component) unit.center += shift;
            cursor = Math.max(cursor, max + shift + BRANCH_GAP);
        }
    }

    private static boolean componentHasPinned(List<Unit> component) {
        for (Unit unit : component) {
            if (pinnedCenter(unit) != null) return true;
        }
        return false;
    }

    private static float componentMin(List<Unit> component) {
        float result = Float.MAX_VALUE;
        for (Unit unit : component) result = Math.min(result, unit.left());
        return result;
    }

    private static List<List<Unit>> weakComponents(UnitGraph graph, Relations relations) {
        List<List<Unit>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Unit start : graph.units) {
            if (seen.contains(start.id)) continue;
            List<Unit> component = new ArrayList<>();
            ArrayDeque<Unit> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Unit unit = queue.removeFirst();
                if (unit == null || !seen.add(unit.id)) continue;
                component.add(unit);
                for (String id : unit.parents) queue.add(graph.unitById.get(id));
                for (String id : unit.children) queue.add(graph.unitById.get(id));
                for (String id : siblingUnitIds(unit, relations, graph.personToUnit)) {
                    queue.add(graph.unitById.get(id));
                }
                for (String id : relativeUnitIds(unit, relations, graph.personToUnit)) {
                    queue.add(graph.unitById.get(id));
                }
            }
            result.add(component);
        }
        return result;
    }

    private static String componentKey(List<Unit> units) {
        String result = "";
        for (Unit unit : units) {
            if (result.isEmpty() || unit.id.compareTo(result) < 0) result = unit.id;
        }
        return result;
    }

    private static void applyPositions(TreeState state, UnitGraph graph) {
        int maxLevel = 0;
        for (Unit unit : graph.units) maxLevel = Math.max(maxLevel, unit.level);
        List<Float> rowTops = generationRows(state, graph, maxLevel);

        float requiredRight = MARGIN_X;
        for (Unit unit : graph.units) requiredRight = Math.max(requiredRight, unit.right() + MARGIN_X);
        float requiredBottom = rowTops.isEmpty()
            ? MARGIN_Y + CARD_H
            : rowTops.get(rowTops.size() - 1) + CARD_H + MARGIN_Y;
        state.workspaceWidth = normalizeSurfaceWidth(Math.max(
            state.workspaceWidth,
            (int) Math.ceil(requiredRight / GRID) * (int) GRID));
        state.workspaceHeight = normalizeSurfaceHeight(Math.max(
            state.workspaceHeight,
            (int) Math.ceil(requiredBottom / GRID) * (int) GRID));

        for (Unit unit : graph.units) {
            float left = unit.left();
            float top = rowTops.get(Math.min(unit.level, rowTops.size() - 1));
            for (int i = 0; i < unit.people.size(); i++) {
                Person person = state.people.get(unit.people.get(i));
                if (person == null || person.pinned) continue;
                person.x = clampSnap(
                    left + i * (CARD_W + PARTNER_GAP),
                    0f,
                    surfaceWidth(state) - CARD_W);
                person.y = clampSnap(top, 0f, surfaceHeight(state) - CARD_H);
            }
        }
    }

    private static List<Float> generationRows(TreeState state, UnitGraph graph, int maxLevel) {
        float first = MARGIN_Y;
        List<Float> guideRows = new ArrayList<>();
        for (Guide guide : state.guides) {
            if ("h".equals(guide.axis) && Float.isFinite(guide.position)) {
                guideRows.add(guide.position + GUIDE_CARD_OFFSET);
            }
        }
        if (!guideRows.isEmpty()) {
            guideRows.sort(Float::compareTo);
            first = snap(guideRows.get(0));
        }
        for (Unit unit : graph.units) {
            boolean found = false;
            for (Person person : unit.personRefs) {
                if (person == null || !person.pinned || !isValidPosition(person)) continue;
                first = snap(person.y) - unit.level * LEVEL_GAP;
                found = true;
                break;
            }
            if (found) break;
        }

        List<Float> rows = new ArrayList<>();
        for (int level = 0; level <= maxLevel; level++) {
            rows.add(first + level * LEVEL_GAP);
        }
        return rows;
    }

    private static Float pinnedCenter(Unit unit) {
        for (int i = 0; i < unit.people.size(); i++) {
            Person person = unit.personRefs.get(i);
            if (person == null || !person.pinned || !isValidPosition(person)) continue;
            float left = person.x - i * (CARD_W + PARTNER_GAP);
            return left + unit.width / 2f;
        }
        return null;
    }

    private static List<Unit> childUnits(Unit unit, UnitGraph graph) {
        List<Unit> result = new ArrayList<>();
        for (String id : unit.children) {
            Unit child = graph.unitById.get(id);
            if (child != null) result.add(child);
        }
        return result;
    }

    private static Set<String> siblingUnitIds(
        Unit unit,
        Relations relations,
        Map<String, Unit> personToUnit
    ) {
        Set<String> result = new LinkedHashSet<>();
        for (String personId : unit.people) {
            for (String siblingId : relations.siblingsByPerson.getOrDefault(
                personId,
                Collections.emptySet())) {
                Unit sibling = personToUnit.get(siblingId);
                if (sibling != null && sibling != unit) {
                    result.add(sibling.id);
                    unit.siblings.add(sibling.id);
                    sibling.siblings.add(unit.id);
                }
            }
        }
        return result;
    }

    private static Set<String> relativeUnitIds(
        Unit unit,
        Relations relations,
        Map<String, Unit> personToUnit
    ) {
        Set<String> result = new LinkedHashSet<>();
        for (String personId : unit.people) {
            for (String relativeId : relations.relativesByPerson.getOrDefault(
                personId,
                Collections.emptySet())) {
                Unit relative = personToUnit.get(relativeId);
                if (relative != null && relative != unit) result.add(relative.id);
            }
        }
        return result;
    }

    private static int comparePeople(
        TreeState state,
        Relations relations,
        String first,
        String second
    ) {
        int rank = personSideRank(state, relations, first)
            - personSideRank(state, relations, second);
        if (rank != 0) return rank;
        int date = Integer.compare(
            dateValue(state.people.get(first)),
            dateValue(state.people.get(second)));
        if (date != 0) return date;
        int name = personName(state, first).compareToIgnoreCase(personName(state, second));
        return name != 0 ? name : first.compareTo(second);
    }

    private static int personSideRank(TreeState state, Relations relations, String id) {
        String gender = inferGender(state, id);
        if ("male".equals(gender)) return 0;
        if ("female".equals(gender)) return 2;
        return relations.preferredSide.getOrDefault(id, 1);
    }

    private static int compareUnits(TreeState state, Unit first, Unit second) {
        int dateFirst = Integer.MAX_VALUE;
        int dateSecond = Integer.MAX_VALUE;
        for (String id : first.people) {
            dateFirst = Math.min(dateFirst, dateValue(state.people.get(id)));
        }
        for (String id : second.people) {
            dateSecond = Math.min(dateSecond, dateValue(state.people.get(id)));
        }
        int date = Integer.compare(dateFirst, dateSecond);
        if (date != 0) return date;
        String nameFirst = namesFor(state, first);
        String nameSecond = namesFor(state, second);
        int name = nameFirst.compareToIgnoreCase(nameSecond);
        return name != 0 ? name : first.id.compareTo(second.id);
    }

    private static String namesFor(TreeState state, Unit unit) {
        List<String> names = new ArrayList<>();
        for (String id : unit.people) names.add(personName(state, id));
        names.sort(Comparator.naturalOrder());
        return String.join(" ", names);
    }

    private static String inferGender(TreeState state, String id) {
        return PersonGender.resolve(state.people.get(id));
    }

    private static int dateValue(Person person) {
        if (person == null) return Integer.MAX_VALUE;
        int year = parsePositive(person.bornYear);
        if (year <= 0) return Integer.MAX_VALUE;
        return year * 10000
            + Math.max(0, parsePositive(person.bornMonth)) * 100
            + Math.max(0, parsePositive(person.bornDay));
    }

    private static Integer unitYear(Unit unit, TreeState state) {
        int sum = 0;
        int count = 0;
        for (String id : unit.people) {
            Person person = state.people.get(id);
            int year = parsePositive(person == null ? "" : person.bornYear);
            if (year > 0) {
                sum += year;
                count++;
            }
        }
        return count == 0 ? null : Math.round((float) sum / count);
    }

    private static int parsePositive(String value) {
        try {
            int number = Integer.parseInt(value == null ? "" : value.trim());
            return number > 0 ? number : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String personName(TreeState state, String id) {
        Person person = state.people.get(id);
        return person == null || person.name == null ? "" : person.name;
    }

    private static void addToMapSet(Map<String, Set<String>> map, String key, String value) {
        map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
    }

    private static Point findOpenSpot(TreeState state, Point preferred) {
        List<Person> occupied = new ArrayList<>();
        for (Person person : state.people.values()) {
            if (isValidPosition(person)) occupied.add(person);
        }
        Point start = snapPoint(state, preferred);
        if (spotIsOpen(start, occupied)) return start;
        float maxRadius = Math.max(surfaceWidth(state), surfaceHeight(state));
        for (float radius = GRID; radius <= maxRadius; radius += GRID) {
            Point[] candidates = new Point[]{
                new Point(start.x + radius, start.y),
                new Point(start.x - radius, start.y),
                new Point(start.x, start.y + radius),
                new Point(start.x, start.y - radius),
                new Point(start.x + radius, start.y + radius),
                new Point(start.x - radius, start.y + radius),
                new Point(start.x + radius, start.y - radius),
                new Point(start.x - radius, start.y - radius)
            };
            for (Point candidate : candidates) {
                Point open = snapPoint(state, candidate);
                if (spotIsOpen(open, occupied)) return open;
            }
        }
        return start;
    }

    private static boolean spotIsOpen(Point candidate, List<Person> occupied) {
        for (Person person : occupied) {
            boolean separated = candidate.x + CARD_W + GRID <= person.x
                || person.x + CARD_W + GRID <= candidate.x
                || candidate.y + CARD_H + GRID <= person.y
                || person.y + CARD_H + GRID <= candidate.y;
            if (!separated) return false;
        }
        return true;
    }

    private static boolean isValidPosition(Person person) {
        return person != null && Float.isFinite(person.x) && Float.isFinite(person.y);
    }

    static float snap(float value) {
        return Math.round(value / GRID) * GRID;
    }

    private static Point snapPoint(TreeState state, Point point) {
        return new Point(
            clampSnap(point.x, 0f, surfaceWidth(state) - CARD_W),
            clampSnap(point.y, 0f, surfaceHeight(state) - CARD_H));
    }

    private static float clampSnap(float value, float min, float max) {
        return Math.min(max, Math.max(min, snap(value)));
    }

    private static final class Point {
        final float x;
        final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Unit {
        final String id;
        final List<String> people;
        final List<Person> personRefs = new ArrayList<>();
        final float width;
        final Set<String> parents = new LinkedHashSet<>();
        final Set<String> children = new LinkedHashSet<>();
        final Set<String> siblings = new LinkedHashSet<>();
        int level;
        float center;
        double orderHint;
        int orderSamples;

        Unit(String id, List<String> people) {
            this.id = id;
            this.people = people;
            this.width = people.size() * CARD_W
                + Math.max(0, people.size() - 1) * PARTNER_GAP;
        }

        float left() {
            return center - width / 2f;
        }

        float right() {
            return center + width / 2f;
        }
    }

    private static final class Relations {
        final Map<String, Set<String>> parentsByChild = new LinkedHashMap<>();
        final Map<String, Set<String>> childrenByParent = new LinkedHashMap<>();
        final Map<String, Set<String>> partnersByPerson = new LinkedHashMap<>();
        final Map<String, Set<String>> siblingsByPerson = new LinkedHashMap<>();
        final Map<String, Set<String>> relativesByPerson = new LinkedHashMap<>();
        final Map<String, Integer> preferredSide = new HashMap<>();
    }

    private static final class UnitGraph {
        final List<Unit> units = new ArrayList<>();
        final Map<String, Unit> unitById = new HashMap<>();
        final Map<String, Unit> personToUnit = new HashMap<>();
    }

    private static final class QueueLevel {
        final Unit unit;
        final int level;

        QueueLevel(Unit unit, int level) {
            this.unit = unit;
            this.level = level;
        }
    }

    private static final class QueueDepth {
        final Unit unit;
        final int depth;

        QueueDepth(Unit unit, int depth) {
            this.unit = unit;
            this.depth = depth;
        }
    }

    private static final class Disjoint {
        private final Map<String, String> parent = new HashMap<>();

        Disjoint(List<String> ids) {
            for (String id : ids) parent.put(id, id);
        }

        String find(String id) {
            String current = parent.get(id);
            if (current == null || current.equals(id)) return id;
            String root = find(current);
            parent.put(id, root);
            return root;
        }

        void union(String first, String second) {
            if (!parent.containsKey(first) || !parent.containsKey(second)) return;
            String rootFirst = find(first);
            String rootSecond = find(second);
            if (!rootFirst.equals(rootSecond)) parent.put(rootSecond, rootFirst);
        }
    }
}
