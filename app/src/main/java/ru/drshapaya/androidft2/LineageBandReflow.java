package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Places one-sided child families near their parents while moving whole lower branches. */
final class LineageBandReflow {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float CARD_W = TreeLayoutEngine.CARD_W;
    private static final float GAP = GRID * 5f;

    private LineageBandReflow() {}

    /** Recompute upper anchors as one transaction, including compact sibling bands. */
    static boolean alignTogether(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot before = LayoutSnapshot.capture(state), current = before;
        if (!constraints.validate(graph, before).isValid()) return false;
        Set<Float> rows = new java.util.TreeSet<>(Collections.reverseOrder());
        for (Person p : state.people.values()) rows.add(p.y);
        for (float row : rows) {
            LayoutSnapshot ordered = alignParentHouseholdOrder(state, graph, constraints, current, row);
            if (ordered != null) current = ordered;
            LayoutSnapshot next = centerParentRowTogether(state, graph, constraints, current, row, true);
            if (next != null) current = next;
        }
        if (!improvesFamilyCenters(graph, before, current)
            || !keepsRelativeAncestorOrder(graph, before, current)
            || !SiblingContourPacking.preservesProperlyAlignedLeaves(graph, before, current)) return false;
        current.applyTo(state);
        return true;
    }

    private static boolean keepsRelativeAncestorOrder(FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot after) {
        for (Map.Entry<String, Set<String>> entry : graph.parentsByChild.entrySet()) {
            String child = entry.getKey();
            for (String partner : graph.partnersByPerson.getOrDefault(child, Collections.emptySet())) {
                Set<String> otherParents = graph.parentsByChild.getOrDefault(partner, Collections.emptySet());
                if (otherParents.isEmpty() || !Collections.disjoint(entry.getValue(), otherParents)
                    || before.positionOf(child).x >= before.positionOf(partner).x) continue;
                float oldLeft = averageX(entry.getValue(), before), oldRight = averageX(otherParents, before);
                if (oldLeft <= oldRight && averageX(entry.getValue(), after) > averageX(otherParents, after) + .5f) return false;
            }
        }
        return true;
    }

    private static float averageX(Set<String> ids, LayoutSnapshot snapshot) {
        float sum = 0; for (String id : ids) sum += snapshot.positionOf(id).x;
        return sum / ids.size();
    }



    /** Parent anchors must be refreshed after the final free-leaf projection. */
    static boolean alignParents(TreeState state) {
        if (state == null || state.people.size() < 4) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        Set<Float> rows = new java.util.TreeSet<>(Collections.reverseOrder());
        for (Person person : state.people.values()) rows.add(person.y);
        boolean changed = false;
        for (float row : rows) {
            LayoutSnapshot next = alignParentHouseholdOrder(state, graph, constraints, current, row);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        for (float row : rows) {
            LayoutSnapshot next = centerParentRowTogether(state, graph, constraints, current, row);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        LayoutSnapshot centered = centerParentsInRowGaps(state, graph, constraints, current);
        if (centered != null) { current = centered; changed = true; }
        if (changed) current.applyTo(state);
        return changed;
    }

    /** Project a crowded parent generation together, so neighbours can release space. */
    private static LayoutSnapshot centerParentRowTogether(
        TreeState state, FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot current, float rowY
    ) {
        return centerParentRowTogether(state, graph, constraints, current, rowY, false);
    }

    private static LayoutSnapshot centerParentRowTogether(
        TreeState state, FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot current, float rowY, boolean together
    ) {
        List<Module> modules = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Person person : state.people.values()) {
            if (Math.abs(person.y - rowY) > .5f || visited.contains(person.id)) continue;
            Set<String> ids = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(person.id);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                if (!ids.add(id)) continue;
                if (state.people.get(id).pinned) return null;
                for (String peer : graph.partnersByPerson.getOrDefault(id, Collections.emptySet())) {
                    if (Math.abs(current.positionOf(peer).y - rowY) <= .5f) queue.add(peer);
                }
            }
            visited.addAll(ids);
            Module module = new Module(ids, BranchContour.from(current, ids), rowY);
            int count = 0;
            for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
                if (!ids.containsAll(family.parents) || family.parents.isEmpty()) continue;
                module.targetSum += offset(family, current);
                count++;
            }
            module.target = count == 0 ? 0f : snap(module.targetSum / count);
            modules.add(module);
        }
        if (modules.size() < 2) return null;
        if (together) modules = compactParentSiblingBands(graph, current, rowY, modules);

        modules.sort(Comparator.comparingDouble(module -> module.topLeft));
        double[] cumulative = new double[modules.size()], targets = new double[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) cumulative[i] = cumulative[i - 1] + requiredRelativeShift(graph, modules.get(i - 1), modules.get(i));
            targets[i] = modules.get(i).target - cumulative[i];
        }
        double[] fitted = isotonic(targets);
        LayoutSnapshot candidate = current;
        for (int i = 0; i < modules.size(); i++) {
            Module module = modules.get(i);
            module.shift = snap((float) (fitted[i] + cumulative[i]));
            for (int j = 0; j < i; j++) module.shift = Math.max(module.shift,
                modules.get(j).shift + snapCeil(requiredRelativeShift(graph, modules.get(j), module)));
            candidate = candidate.shifted(module.ids, module.shift, 0f);
        }
        if (!constraints.validate(graph, candidate).isValid()) return null;
        if (!together && (!preservesAncestrySides(graph, current, candidate)
            || !SiblingContourPacking.preservesAlignedLeaves(graph, current, candidate)
            || !improvesFamilyCenters(graph, current, candidate))) return null;
        return candidate;
    }

    private static List<Module> compactParentSiblingBands(FamilyLayoutGraph graph,
        LayoutSnapshot snapshot, float rowY, List<Module> modules) {
        modules.sort(Comparator.comparingDouble(m -> m.topLeft));
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            List<Module> group = new ArrayList<>(); Module branch = null;
            boolean valid = true;
            for (String child : family.children) {
                if (snapshot.positionOf(child).y != rowY) {valid = false; break;}
                Module found = null;
                for (Module m : modules) if (m.ids.contains(child)) {found = m; break;}
                if (found == null) {valid = false; break;}
                if (!group.contains(found)) group.add(found);
                if (!graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()) {
                    if (branch != null && branch != found) {valid = false; break;}
                    branch = found;
                } else if (!graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()) {
                    valid = false; break;
                }
            }
            if (!valid || branch == null || group.size() < 2) continue;
            group.sort(Comparator.comparingDouble(m -> m.topLeft));
            int first = modules.indexOf(group.get(0));
            for (int i = 0; i < group.size(); i++) {
                if (first + i >= modules.size() || modules.get(first + i) != group.get(i)) {valid = false; break;}
                if (i > 0) {
                    Module a = group.get(i - 1), b = group.get(i);
                    float actual = b.topLeft - a.contour.rows.get(Math.round(rowY / GRID)).right;
                    if (actual > (a.ids.size() == 1 && b.ids.size() == 1 ? GRID : GAP) + .5f) {valid = false; break;}
                }
            }
            if (!valid) continue;
            Set<String> ids = new LinkedHashSet<>(); for (Module m : group) ids.addAll(m.ids);
            Module band = new Module(ids, BranchContour.from(snapshot, ids), rowY); band.target = branch.target;
            modules.removeAll(group); modules.add(first, band);
        }
        return modules;
    }



    static boolean improve(TreeState state) {
        if (state == null || state.people.size() < 4) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        Set<Float> rows = new java.util.TreeSet<>();
        for (Person person : state.people.values()) rows.add(person.y);
        boolean changed = false;
        for (float rowY : rows) {
            LayoutSnapshot next = reflowRow(state, graph, constraints, current, rowY);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        for (float rowY : rows) {
            LayoutSnapshot next = packFamilyBands(state, graph, constraints,
                current, rowY);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        List<Float> parentRows = new ArrayList<>(rows);
        Collections.reverse(parentRows);
        for (float rowY : parentRows) {
            LayoutSnapshot next = alignParentHouseholdOrder(state, graph, constraints, current, rowY);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        LayoutSnapshot centered = centerParentsInRowGaps(state, graph,
            constraints, current);
        if (centered != null) {
            current = centered;
            changed = true;
        }
        for (float rowY : rows) {
            LayoutSnapshot next = orderSharedAncestryBranches(
                state, graph, constraints, current, rowY);
            if (next == null) continue;
            current = next;
            changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    /** Exchange inverted upper households, keeping their children in place. */
    private static LayoutSnapshot alignParentHouseholdOrder(
        TreeState state, FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot baseline, float rowY
    ) {
        List<Set<String>> households = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Person person : state.people.values()) {
            if (Math.abs(person.y - rowY) > 0.5f || visited.contains(person.id)) continue;
            Set<String> household = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(person.id);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                if (!household.add(id)) continue;
                for (String peer : graph.partnersByPerson.getOrDefault(id, Collections.emptySet())) {
                    if (Math.abs(baseline.positionOf(peer).y - rowY) <= 0.5f) queue.add(peer);
                }
            }
            visited.addAll(household);
            households.add(household);
        }
        households.sort(Comparator.comparingDouble(ids -> householdLeft(ids, baseline)));
        LayoutSnapshot current = baseline;
        boolean changed = false;
        for (int index = 1; index < households.size(); index++) {
            for (int at = index; at > 0; at--) {
                Set<String> left = households.get(at - 1), right = households.get(at);
                float leftTarget = householdAnchor(graph, current, left);
                float rightTarget = householdAnchor(graph, current, right);
                if (!Float.isFinite(leftTarget) || !Float.isFinite(rightTarget)
                    || leftTarget <= rightTarget + GRID) break;
                boolean fixed = false;
                for (String id : left) fixed |= state.people.get(id).pinned;
                for (String id : right) fixed |= state.people.get(id).pinned;
                if (fixed || explicitlyOrdered(graph, left, right)
                    || shareParentOrigin(graph, left, right)) break;
                boolean splitsBand = false;
                for (int earlier = 0; earlier < at - 1; earlier++)
                    splitsBand |= shareParentOrigin(graph, left, households.get(earlier));
                for (int later = at + 1; later < households.size(); later++)
                    splitsBand |= shareParentOrigin(graph, right, households.get(later));
                if (splitsBand) break;
                float leftEdge = householdLeft(left, current), rightEdge = householdLeft(right, current);
                float leftEnd = householdRight(left, current), rightEnd = householdRight(right, current);
                if (leftEnd > rightEdge) break;
                float leftWidth = leftEnd - leftEdge, rightWidth = rightEnd - rightEdge;
                float minimum = at < 2 ? 0f
                    : householdRight(households.get(at - 2), current) + GAP;
                float maximum = at + 1 >= households.size() ? Float.POSITIVE_INFINITY
                    : householdLeft(households.get(at + 1), current) - GAP - leftWidth;
                if (minimum + rightWidth + GAP > maximum) break;
                float rightX = Math.min(maximum - rightWidth - GAP,
                    Math.max(minimum, snap(rightTarget - rightWidth / 2f)));
                float leftX = Math.min(maximum,
                    Math.max(rightX + rightWidth + GAP, snap(leftTarget - leftWidth / 2f)));
                LayoutSnapshot candidate = current.shifted(right, rightX - rightEdge, 0f)
                    .shifted(left, leftX - leftEdge, 0f);
                if (!constraints.validate(graph, candidate).isValid()
                    || !preservesAncestrySides(graph, current, candidate)
                    || !improvesFamilyCenters(graph, current, candidate)) break;
                current = candidate;
                Collections.swap(households, at - 1, at);
                changed = true;
            }
        }
        return changed ? current : null;
    }

    private static boolean explicitlyOrdered(FamilyLayoutGraph graph, Set<String> left, Set<String> right) {
        for (String id : left) {
            if (!Collections.disjoint(graph.siblingsByPerson.getOrDefault(id, Collections.emptySet()), right)) return true;
        }
        return false;
    }

    private static boolean shareParentOrigin(FamilyLayoutGraph graph, Set<String> left, Set<String> right) {
        Set<String> parents = new HashSet<>();
        for (String id : left) parents.addAll(graph.parentsByChild.getOrDefault(id, Collections.emptySet()));
        for (String id : right) {
            if (!Collections.disjoint(parents, graph.parentsByChild.getOrDefault(id, Collections.emptySet()))) return true;
        }
        return false;
    }

    private static float householdLeft(Set<String> ids, LayoutSnapshot snapshot) {
        float left = Float.POSITIVE_INFINITY;
        for (String id : ids) left = Math.min(left, snapshot.positionOf(id).x);
        return left;
    }

    private static float householdRight(Set<String> ids, LayoutSnapshot snapshot) {
        float right = Float.NEGATIVE_INFINITY;
        for (String id : ids) right = Math.max(right, snapshot.positionOf(id).x + CARD_W);
        return right;
    }

    private static float householdAnchor(FamilyLayoutGraph graph, LayoutSnapshot snapshot, Set<String> ids) {
        Set<String> children = new LinkedHashSet<>(), parents = new LinkedHashSet<>();
        for (String id : ids) {
            children.addAll(graph.childrenByParent.getOrDefault(id, Collections.emptySet()));
            parents.addAll(graph.parentsByChild.getOrDefault(id, Collections.emptySet()));
        }
        if (!children.isEmpty()) {
            float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            for (String id : children) {
                left = Math.min(left, snapshot.positionOf(id).x + CARD_W / 2f);
                right = Math.max(right, snapshot.positionOf(id).x + CARD_W / 2f);
            }
            return (left + right) / 2f;
        }
        if (parents.isEmpty()) return Float.NaN;
        float sum = 0f;
        for (String id : parents) sum += snapshot.positionOf(id).x + CARD_W / 2f;
        return sum / parents.size();
    }

    private static boolean improvesFamilyCenters(FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after) {
        float oldTotal = 0f, newTotal = 0f, oldMaximum = 0f, newMaximum = 0f;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            float oldError = Math.abs(offset(family, before)), newError = Math.abs(offset(family, after));
            oldTotal += oldError;
            newTotal += newError;
            oldMaximum = Math.max(oldMaximum, oldError);
            newMaximum = Math.max(newMaximum, newError);
        }
        return newTotal < oldTotal - GRID / 2f && newMaximum <= oldMaximum + GRID / 2f;
    }

    /** Put a branch shared by two parent families between their own branches. */
    private static LayoutSnapshot orderSharedAncestryBranches(
        TreeState state, FamilyLayoutGraph graph,
        LayoutConstraints constraints, LayoutSnapshot current, float rowY
    ) {
        List<Module> modules = modulesAtRow(state, graph, current, rowY);
        if (modules.size() < 3) return null;
        modules.sort(Comparator.comparingDouble(module -> module.topLeft));
        List<Module> reordered = new ArrayList<>(modules);
        LayoutSnapshot local = current;
        for (Module bridge : modules) {
            Set<FamilyLayoutGraph.ParentFamily> origins = originsAtRow(
                graph, current, bridge, rowY);
            if (origins.size() < 2) continue;
            // Two marriages between the same origin families form one band.
            // A foreign branch between those junctions needlessly stretches
            // both sibling spans, even when it has no recorded parents.
            int bridgeAt = reordered.indexOf(bridge);
            int firstRelated = -1;
            for (int earlier = 0; earlier < bridgeAt; earlier++) {
                if (origins.equals(originsAtRow(graph, current, reordered.get(earlier), rowY))) {
                    firstRelated = earlier; break;
                }
            }
            if (firstRelated >= 0) {
                List<Module> foreign = new ArrayList<>();
                for (int at = firstRelated + 1; at < bridgeAt; at++) {
                    Module m = reordered.get(at);
                    if (Collections.disjoint(origins, originsAtRow(graph, current, m, rowY))) foreign.add(m);
                }
                reordered.removeAll(foreign);
                int after = reordered.indexOf(bridge) + 1;
                while (after < reordered.size()
                    && !Collections.disjoint(origins, originsAtRow(graph, current, reordered.get(after), rowY))) after++;
                reordered.addAll(after, foreign);
            }
            float leftmostParent = Float.POSITIVE_INFINITY;
            for (FamilyLayoutGraph.ParentFamily origin : origins) {
                leftmostParent = Math.min(leftmostParent, parentCenter(origin, current));
            }
            List<Module> rightBranches = new ArrayList<>();
            int bridgeIndex = reordered.indexOf(bridge);
            for (int earlier = 0; earlier < bridgeIndex; earlier++) {
                Module before = reordered.get(earlier);
                Set<FamilyLayoutGraph.ParentFamily> beforeOrigins = originsAtRow(
                    graph, current, before, rowY);
                if (beforeOrigins.size() != 1) continue;
                FamilyLayoutGraph.ParentFamily origin = beforeOrigins.iterator().next();
                boolean rightOrigin = origins.contains(origin)
                    && parentCenter(origin, current) > leftmostParent + GRID;
                // Half-sibling families from a repeated marriage have different
                // family keys, but share a parent. Their relative order must
                // follow the two union anchors too, otherwise the connectors
                // cross even though exact origin keys do not intersect.
                for (FamilyLayoutGraph.ParentFamily shared : origins) {
                    if (!Collections.disjoint(shared.parents, origin.parents)
                        && parentCenter(origin, current) > parentCenter(shared, current) + GRID) {
                        rightOrigin = true;
                    }
                }
                if (!rightOrigin) continue;
                rightBranches.add(before);
            }
            reordered.removeAll(rightBranches);
            reordered.addAll(reordered.indexOf(bridge) + 1, rightBranches);
            float cursor = bridge.contour.rows.get(Math.round(rowY / GRID)).right + GAP;
            for (Module branch : rightBranches) {
                boolean shallow = true;
                for (String id : branch.ids)
                    shallow &= Math.abs(local.positionOf(id).y - rowY) <= .5f;
                if (!shallow) continue;
                LayoutSnapshot placed = local.shifted(branch.ids, snap(cursor - branch.topLeft), 0);
                if (!constraints.validate(graph, placed).isValid()) continue;
                local = placed;
                cursor += branch.contour.rows.get(Math.round(rowY / GRID)).right - branch.topLeft + GAP;
            }
        }
        if (reordered.equals(modules)) return null;
        LayoutSnapshot candidate = placeOrderedModules(graph, current, reordered);
        if (candidate != null && constraints.validate(graph, candidate).isValid()) return candidate;
        // A global projection can hit a shared lower junction which stays
        // stationary. Compact only the reordered interval against its previous
        // neighbour, keeping every other branch at its existing coordinates.
        int first = 0, last = modules.size() - 1;
        while (first < modules.size() && modules.get(first) == reordered.get(first)) first++;
        while (last >= first && modules.get(last) == reordered.get(last)) last--;
        candidate = current;
        for (int i = first; i <= last; i++) {
            Module module = reordered.get(i);
            float shift = modules.get(first).topLeft - module.topLeft;
            for (int j = 0; j < i; j++) {
                Module before = reordered.get(j);
                float previousShift = j < first ? 0 : before.shift;
                shift = Math.max(shift, previousShift + snapCeil(requiredRelativeShift(graph, before, module)));
            }
            module.shift = shift;
            candidate = candidate.shifted(module.ids, shift, 0);
        }
        if (constraints.validate(graph, candidate).isValid()) return candidate;
        return local != current ? local : null;
    }

    private static Set<FamilyLayoutGraph.ParentFamily> originsAtRow(
        FamilyLayoutGraph graph, LayoutSnapshot snapshot, Module module, float rowY
    ) {
        Set<FamilyLayoutGraph.ParentFamily> origins = new LinkedHashSet<>();
        for (String id : module.ids) {
            LayoutSnapshot.Position point = snapshot.positionOf(id);
            if (point == null || Math.abs(point.y - rowY) > 0.5f) continue;
            FamilyLayoutGraph.ParentFamily origin = graph.parentFamilyOf(id);
            if (origin != null) origins.add(origin);
        }
        return origins;
    }

    private static LayoutSnapshot placeOrderedModules(
        FamilyLayoutGraph graph, LayoutSnapshot current, List<Module> modules
    ) {
        int n = modules.size();
        double[] cumulative = new double[n];
        double[] transformed = new double[n];
        for (int i = 0; i < n; i++) {
            if (i > 0) cumulative[i] = cumulative[i - 1]
                + requiredRelativeShift(graph, modules.get(i - 1), modules.get(i));
            transformed[i] = -cumulative[i];
        }
        double[] fitted = isotonic(transformed);
        for (int i = 0; i < n; i++) {
            Module module = modules.get(i);
            float shift = snap((float) (fitted[i] + cumulative[i]));
            float lower = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < i; j++) {
                Module before = modules.get(j);
                lower = Math.max(lower,
                    snapCeil(before.shift + requiredRelativeShift(graph, before, module)));
            }
            // This ordering pass intentionally allows an ancestry side to
            // change when the old side crosses another family's connector.
            module.shift = Math.max(shift, lower);
        }
        LayoutSnapshot candidate = current;
        for (Module module : modules) {
            if (Math.abs(module.shift) >= GRID / 2f) {
                candidate = candidate.shifted(module.ids, module.shift, 0f);
            }
        }
        return candidate;
    }

    /** Use an existing gap in a parent row before widening another generation. */
    private static LayoutSnapshot centerParentsInRowGaps(
        TreeState state, FamilyLayoutGraph graph,
        LayoutConstraints constraints, LayoutSnapshot baseline
    ) {
        LayoutSnapshot current = baseline;
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
        families.sort(Comparator.comparingDouble(
            (FamilyLayoutGraph.ParentFamily family) -> Math.abs(offset(family, baseline)))
            .reversed());
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : families) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            // A parent can still be visibly off-center while remaining inside
            // a wide child span.  Keep a small tolerance for naturally wide
            // families, but let the gap pass correct larger offsets.
            if (Math.abs(offset(family, current)) <= GRID / 2f) continue;
            float desired = snap(offset(family, current));
            if (Math.abs(desired) < GRID / 2f) continue;
            Set<String> household = new LinkedHashSet<>(family.parents);
            ArrayDeque<String> queue = new ArrayDeque<>(family.parents);
            float rowY = Float.NaN;
            boolean pinned = false;
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                LayoutSnapshot.Position point = current.positionOf(id);
                Person person = state.people.get(id);
                if (point == null || person == null) { pinned = true; break; }
                if (Float.isNaN(rowY)) rowY = point.y;
                if (Math.abs(point.y - rowY) > 0.5f || person.pinned) {
                    pinned = true;
                    break;
                }
                for (String partner : graph.partnersByPerson.getOrDefault(
                    id, Collections.emptySet())) {
                    LayoutSnapshot.Position peer = current.positionOf(partner);
                    if (peer != null && Math.abs(peer.y - rowY) <= 0.5f
                        && household.add(partner)) queue.add(partner);
                }
            }
            if (pinned) continue;
            float available = Math.abs(desired);
            for (String id : household) {
                LayoutSnapshot.Position member = current.positionOf(id);
                if (desired < 0f) available = Math.min(available, member.x);
                for (String otherId : current.positions.keySet()) {
                    if (household.contains(otherId)) continue;
                    LayoutSnapshot.Position other = current.positionOf(otherId);
                    if (other == null || member.y >= other.y + TreeLayoutEngine.CARD_H
                        || other.y >= member.y + TreeLayoutEngine.CARD_H) continue;
                    float gap = desired > 0f
                        ? other.x - member.x - CARD_W
                        : member.x - other.x - CARD_W;
                    if (gap >= -0.5f) available = Math.min(available, gap - GAP);
                }
            }
            float shift = Math.copySign(snapDown(Math.max(0f, available)), desired);
            if (Math.abs(shift) < GRID / 2f) continue;
            LayoutSnapshot candidate = current.shifted(household, shift, 0f);
            if (!constraints.validate(graph, candidate).isValid()
                || !preservesAncestrySides(graph, current, candidate)
                || Math.abs(offset(family, candidate))
                    >= Math.abs(offset(family, current)) - GRID / 2f) continue;
            boolean worsensOther = false;
            for (FamilyLayoutGraph.ParentFamily other : graph.parentFamilies) {
                if (other == family || other.parents.isEmpty() || other.children.isEmpty()) continue;
                if (Math.abs(offset(other, candidate))
                    > Math.abs(offset(other, current)) + GRID / 2f) {
                    worsensOther = true;
                    break;
                }
            }
            if (worsensOther) continue;
            current = candidate;
            changed = true;
        }
        return changed ? current : null;
    }

    /**
     * Treat every sibling family's descendant branches as a contour block.
     * Order the blocks by their parents, then pack them once from left to
     * right. A wide family therefore reserves its actual child width before
     * the next family is placed, instead of pushing only its last children
     * into a neighbouring branch.
     */
    private static LayoutSnapshot packFamilyBands(
        TreeState state, FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot current, float rowY
    ) {
        List<Module> modules = modulesAtRow(state, graph, current, rowY);
        if (modules.size() < 3) return null;
        Map<String, Integer> indexByPerson = new HashMap<>();
        int[] roots = new int[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            roots[i] = i;
            for (String id : modules.get(i).ids) indexByPerson.put(id, i);
        }
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>();
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            List<Integer> childIndexes = new ArrayList<>();
            boolean sameRow = true;
            for (String childId : family.children) {
                LayoutSnapshot.Position child = current.positionOf(childId);
                Integer index = indexByPerson.get(childId);
                if (child == null || index == null
                    || Math.abs(child.y - rowY) > 0.5f) {
                    sameRow = false;
                    break;
                }
                childIndexes.add(index);
            }
            if (sameRow) {
                int first = childIndexes.get(0);
                for (int index : childIndexes) {
                    roots[find(roots, index)] = find(roots, first);
                }
                families.add(family);
            }
        }
        if (families.size() < 2) return null;
        Map<Integer, Set<String>> idsByRoot = new HashMap<>();
        for (int i = 0; i < modules.size(); i++) {
            idsByRoot.computeIfAbsent(find(roots, i), ignored -> new LinkedHashSet<>())
                .addAll(modules.get(i).ids);
        }
        Map<Integer, List<FamilyLayoutGraph.ParentFamily>> familiesByRoot = new HashMap<>();
        for (FamilyLayoutGraph.ParentFamily family : families) {
            Integer first = indexByPerson.get(family.children.iterator().next());
            if (first != null) familiesByRoot.computeIfAbsent(find(roots, first),
                ignored -> new ArrayList<>()).add(family);
        }
        List<Module> bands = new ArrayList<>();
        for (Map.Entry<Integer, Set<String>> entry : idsByRoot.entrySet()) {
            Module band = new Module(entry.getValue(),
                BranchContour.from(current, entry.getValue()), rowY);
            List<FamilyLayoutGraph.ParentFamily> origins = familiesByRoot.getOrDefault(
                entry.getKey(), Collections.emptyList());
            float totalShift = 0f;
            for (FamilyLayoutGraph.ParentFamily family : origins) {
                totalShift += parentCenter(family, current)
                    - (childWidth(family, current) / 2f
                        + leftChild(family, current));
            }
            band.target = origins.isEmpty() ? 0f : snap(totalShift / origins.size());
            bands.add(band);
        }
        bands.sort(Comparator.comparingDouble((Module band) -> band.topLeft + band.target)
            .thenComparingDouble(band -> band.topLeft));
        double[] cumulative = new double[bands.size()];
        double[] transformed = new double[bands.size()];
        for (int i = 0; i < bands.size(); i++) {
            if (i > 0) cumulative[i] = cumulative[i - 1]
                + requiredRelativeShift(graph, bands.get(i - 1), bands.get(i));
            transformed[i] = bands.get(i).target - cumulative[i];
        }
        double[] fitted = isotonic(transformed);
        for (int i = 0; i < bands.size(); i++) {
            Module band = bands.get(i);
            band.shift = snap((float) (fitted[i] + cumulative[i]));
            float lowerBound = Float.NEGATIVE_INFINITY;
            for (int j = 0; j < i; j++) {
                Module before = bands.get(j);
                lowerBound = Math.max(lowerBound,
                    snapCeil(before.shift + requiredRelativeShift(graph, before, band)));
            }
            band.shift = ancestrySafeShift(graph, current, band,
                band.shift, lowerBound, Float.POSITIVE_INFINITY);
        }
        LayoutSnapshot candidate = current;
        boolean anyShift = false;
        for (Module band : bands) {
            if (Math.abs(band.shift) >= GRID / 2f) {
                anyShift = true;
                candidate = candidate.shifted(band.ids, band.shift, 0f);
            }
        }
        candidate = preserveCenteredLineages(state, graph, current,
            candidate, families);
        if (candidate == null) return null;
        if (!anyShift || !constraints.validate(graph, candidate).isValid()
            || !preservesAncestrySides(graph, current, candidate)) return null;
        float oldMax = 0f;
        float newMax = 0f;
        for (FamilyLayoutGraph.ParentFamily family : families) {
            oldMax = Math.max(oldMax, Math.abs(offset(family, current)));
            newMax = Math.max(newMax, Math.abs(offset(family, candidate)));
            if (Math.abs(offset(family, current)) < GRID / 2f
                && Math.abs(offset(family, candidate)) >= GRID / 2f) return null;
            if (childWidth(family, candidate) > childWidth(family, current) + GRID / 2f) {
                return null;
            }
        }
        return newMax < oldMax - GRID / 2f ? candidate : null;
    }

    private static float ancestrySafeShift(
        FamilyLayoutGraph graph, LayoutSnapshot snapshot, Module band,
        float desired, float lowerBound, float upperBound
    ) {
        List<float[]> forbidden = new ArrayList<>();
        for (String childId : band.ids) {
            LayoutSnapshot.Position child = snapshot.positionOf(childId);
            if (child == null) continue;
            for (String partnerId : graph.partnersByPerson.getOrDefault(
                childId, Collections.emptySet())) {
                if (!band.ids.contains(partnerId)) continue;
                // A partner with no own parent branch does not impose a
                // fixed ancestry side on this child household.  Keeping the
                // soft side would strand a wide sibling branch unnecessarily.
                if (graph.parentsByChild.getOrDefault(
                    partnerId, Collections.emptySet()).isEmpty()) continue;
                LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
                if (partner == null) continue;
                float householdLeft = Math.min(child.x, partner.x);
                float householdRight = Math.max(child.x, partner.x) + CARD_W;
                for (String parentId : graph.parentsByChild.getOrDefault(
                    childId, Collections.emptySet())) {
                    if (band.ids.contains(parentId)) continue;
                    LayoutSnapshot.Position parent = snapshot.positionOf(parentId);
                    if (parent == null) continue;
                    boolean leftSide = child.x <= partner.x
                        && parent.x + CARD_W <= householdLeft + 0.5f;
                    boolean rightSide = child.x >= partner.x
                        && parent.x >= householdRight - 0.5f;
                    if (leftSide || rightSide) forbidden.add(new float[]{
                        parent.x - householdRight,
                        parent.x + CARD_W - householdLeft
                    });
                }
            }
        }
        float minimum = Float.isFinite(lowerBound) ? snapCeil(lowerBound)
            : Float.NEGATIVE_INFINITY;
        float maximum = Float.isFinite(upperBound) ? snapDown(upperBound)
            : Float.POSITIVE_INFINITY;
        float target = snap(Math.min(maximum, Math.max(desired, minimum)));
        List<Float> options = new ArrayList<>();
        options.add(target);
        if (Float.isFinite(minimum)) options.add(minimum);
        if (Float.isFinite(maximum)) options.add(maximum);
        for (float[] interval : forbidden) {
            options.add((float) Math.floor(interval[0] / GRID) * GRID);
            options.add(snapCeil(interval[1]));
        }
        float best = Float.NaN;
        for (float option : options) {
            if (option < minimum - 0.5f || option > maximum + 0.5f) continue;
            boolean inside = false;
            for (float[] interval : forbidden) {
                if (option > interval[0] + 0.5f
                    && option < interval[1] - 0.5f) {
                    inside = true;
                    break;
                }
            }
            if (inside) continue;
            if (!Float.isFinite(best)
                || Math.abs(option - desired) < Math.abs(best - desired) - 0.5f
                || (Math.abs(Math.abs(option - desired) - Math.abs(best - desired)) <= 0.5f
                    && option < best)) best = option;
        }
        return Float.isFinite(best) ? best : target;
    }

    private static float leftChild(
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot
    ) {
        float left = Float.MAX_VALUE;
        for (String id : family.children) {
            left = Math.min(left, snapshot.positionOf(id).x);
        }
        return left;
    }

    /**
     * When a child band needs more width, carry an already centered parent
     * household and the following row branches by the same amount. This
     * creates the required gap without disconnecting that family.
     */
    private static LayoutSnapshot preserveCenteredLineages(
        TreeState state, FamilyLayoutGraph graph,
        LayoutSnapshot original, LayoutSnapshot candidate,
        List<FamilyLayoutGraph.ParentFamily> families
    ) {
        List<FamilyLayoutGraph.ParentFamily> centered = new ArrayList<>();
        for (FamilyLayoutGraph.ParentFamily family : families) {
            if (Math.abs(offset(family, original)) < GRID / 2f) centered.add(family);
        }
        centered.sort(Comparator.comparingDouble(family -> parentCenter(family, original)));
        for (FamilyLayoutGraph.ParentFamily family : centered) {
            float shift = snap(offset(family, candidate));
            if (Math.abs(shift) < GRID / 2f) continue;
            float parentY = Float.NaN;
            Set<String> household = new LinkedHashSet<>(family.parents);
            ArrayDeque<String> queue = new ArrayDeque<>(family.parents);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                LayoutSnapshot.Position point = candidate.positionOf(id);
                if (point == null) return null;
                if (Float.isNaN(parentY)) parentY = point.y;
                if (Math.abs(point.y - parentY) > 0.5f) return null;
                for (String partner : graph.partnersByPerson.getOrDefault(
                    id, Collections.emptySet())) {
                    LayoutSnapshot.Position peer = candidate.positionOf(partner);
                    if (peer != null && Math.abs(peer.y - parentY) <= 0.5f
                        && household.add(partner)) queue.add(partner);
                }
            }
            float cut = shift > 0f ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
            for (String id : household) {
                LayoutSnapshot.Position point = candidate.positionOf(id);
                if (shift > 0f) cut = Math.min(cut, point.x);
                else cut = Math.max(cut, point.x + CARD_W);
            }
            Set<String> moving = new LinkedHashSet<>(household);
            for (Person person : state.people.values()) {
                LayoutSnapshot.Position point = candidate.positionOf(person.id);
                if (point == null || Math.abs(point.y - parentY) > 0.5f
                    || household.contains(person.id)) continue;
                boolean follower = shift > 0f
                    ? point.x >= cut - 0.5f
                    : point.x + CARD_W <= cut + 0.5f;
                if (follower) moving.addAll(graph.descendantBranch(
                    Collections.singleton(person.id)));
            }
            for (String id : moving) {
                Person person = state.people.get(id);
                LayoutSnapshot.Position point = candidate.positionOf(id);
                if (person == null || person.pinned || point == null
                    || point.x + shift < -0.5f) return null;
            }
            candidate = candidate.shifted(moving, shift, 0f);
        }
        return candidate;
    }

    private static float parentCenter(
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot
    ) {
        float sum = 0f;
        for (String id : family.parents) {
            LayoutSnapshot.Position point = snapshot.positionOf(id);
            if (point == null) return Float.POSITIVE_INFINITY;
            sum += point.x + CARD_W / 2f;
        }
        return family.parents.isEmpty() ? Float.POSITIVE_INFINITY
            : sum / family.parents.size();
    }

    private static LayoutSnapshot reflowRow(
        TreeState state, FamilyLayoutGraph graph, LayoutConstraints constraints,
        LayoutSnapshot current, float rowY
    ) {
        List<Module> modules = modulesAtRow(state, graph, current, rowY);
        if (modules.size() < 2) return null;
        Map<String, Module> byPerson = new HashMap<>();
        for (Module module : modules) {
            for (String id : module.ids) byPerson.put(id, module);
        }
        List<FamilyLayoutGraph.ParentFamily> targeted = new ArrayList<>();
        Set<Module> targetedModules = new HashSet<>();
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            Set<Module> childModules = new LinkedHashSet<>();
            boolean sameRow = true;
            for (String id : family.children) {
                LayoutSnapshot.Position child = current.positionOf(id);
                if (child == null || Math.abs(child.y - rowY) > 0.5f) {
                    sameRow = false;
                    break;
                }
                left = Math.min(left, child.x);
                right = Math.max(right, child.x + CARD_W);
                Module module = byPerson.get(id);
                if (module == null) { sameRow = false; break; }
                childModules.add(module);
            }
            if (!sameRow || childModules.isEmpty()) continue;
            float parentCenter = 0f;
            for (String id : family.parents) {
                LayoutSnapshot.Position parent = current.positionOf(id);
                if (parent == null) { sameRow = false; break; }
                parentCenter += parent.x + CARD_W / 2f;
            }
            if (!sameRow) continue;
            parentCenter /= family.parents.size();
            if (parentCenter < left - GAP || parentCenter > right + GAP) {
                float shift = snap(parentCenter - (left + right) / 2f);
                for (Module module : childModules) {
                    module.targetSum += shift;
                    module.targetCount++;
                }
                targetedModules.addAll(childModules);
                targeted.add(family);
            }
        }
        if (targeted.isEmpty()) return null;
        modules = keepUntouchedFamiliesTogether(graph, current, rowY,
            modules, byPerson, targetedModules);
        byPerson.clear();
        for (Module module : modules) {
            for (String id : module.ids) byPerson.put(id, module);
        }
        for (Module module : modules) {
            module.target = module.targetCount == 0 ? 0f
                : snap(module.targetSum / module.targetCount);
        }
        modules.sort(Comparator.comparingDouble((Module module) -> module.topLeft + module.target)
            .thenComparingDouble(module -> module.topLeft));
        int n = modules.size();
        double[] cumulative = new double[n];
        double[] transformed = new double[n];
        for (int i = 0; i < n; i++) {
            Module module = modules.get(i);
            if (i > 0) cumulative[i] = cumulative[i - 1]
                + requiredRelativeShift(graph, modules.get(i - 1), module);
            transformed[i] = module.target - cumulative[i];
        }
        double[] fitted = isotonic(transformed);
        float previousShift = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            Module module = modules.get(i);
            module.shift = snap((float) (fitted[i] + cumulative[i]));
            if (i > 0) module.shift = Math.max(module.shift,
                previousShift + snapCeil(requiredRelativeShift(graph, modules.get(i - 1), module)));
            for (int j = 0; j < i - 1; j++) {
                Module earlier = modules.get(j);
                module.shift = Math.max(module.shift,
                    earlier.shift + snapCeil(requiredRelativeShift(graph, earlier, module)));
            }
            previousShift = module.shift;
        }
        LayoutSnapshot candidate = current;
        for (Module module : modules) {
            if (Math.abs(module.shift) > 0.5f) {
                candidate = candidate.shifted(module.ids, module.shift, 0f);
            }
        }
        if (!constraints.validate(graph, candidate).isValid()
            || !preservesAncestrySides(graph, current, candidate)) return null;
        float oldError = 0f;
        float newError = 0f;
        for (FamilyLayoutGraph.ParentFamily family : targeted) {
            oldError += Math.abs(offset(family, current));
            newError += Math.abs(offset(family, candidate));
        }
        if (newError > oldError - GRID + 0.5f) return null;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.children.size() < 2 || targeted.contains(family)) continue;
            boolean linkedToTarget = false;
            for (String id : family.children) {
                if (targetedModules.contains(byPerson.get(id))) linkedToTarget = true;
            }
            if (linkedToTarget) continue;
            float oldWidth = childWidth(family, current);
            float newWidth = childWidth(family, candidate);
            if (newWidth > oldWidth + GRID + 0.5f) {
                return null;
            }
        }
        return candidate;
    }

    private static boolean preservesAncestrySides(
        FamilyLayoutGraph graph, LayoutSnapshot original, LayoutSnapshot candidate
    ) {
        for (Map.Entry<String, Set<String>> entry : graph.parentsByChild.entrySet()) {
            String childId = entry.getKey();
            LayoutSnapshot.Position child = original.positionOf(childId);
            LayoutSnapshot.Position movedChild = candidate.positionOf(childId);
            if (child == null || movedChild == null) continue;
            for (String partnerId : graph.partnersByPerson.getOrDefault(
                childId, Collections.emptySet())) {
                LayoutSnapshot.Position partner = original.positionOf(partnerId);
                LayoutSnapshot.Position movedPartner = candidate.positionOf(partnerId);
                if (partner == null || movedPartner == null) continue;
                if (graph.parentsByChild.getOrDefault(
                    partnerId, Collections.emptySet()).isEmpty()) continue;
                float oldLeft = Math.min(child.x, partner.x);
                float oldRight = Math.max(child.x, partner.x) + CARD_W;
                float newLeft = Math.min(movedChild.x, movedPartner.x);
                float newRight = Math.max(movedChild.x, movedPartner.x) + CARD_W;
                for (String parentId : entry.getValue()) {
                    LayoutSnapshot.Position parent = original.positionOf(parentId);
                    LayoutSnapshot.Position movedParent = candidate.positionOf(parentId);
                    if (parent == null || movedParent == null) continue;
                    if (child.x <= partner.x
                        && parent.x + CARD_W <= oldLeft + 0.5f
                        && movedParent.x + CARD_W > newLeft + 0.5f
                        && movedParent.x < newRight - 0.5f) return false;
                    if (child.x >= partner.x
                        && parent.x >= oldRight - 0.5f
                        && movedParent.x < newRight - 0.5f
                        && movedParent.x + CARD_W > newLeft + 0.5f) return false;
                }
            }
        }
        return true;
    }


    private static List<Module> keepUntouchedFamiliesTogether(
        FamilyLayoutGraph graph, LayoutSnapshot snapshot, float rowY,
        List<Module> modules, Map<String, Module> byPerson,
        Set<Module> targeted
    ) {
        Map<Module, Integer> indexes = new HashMap<>();
        int[] parent = new int[modules.size()];
        for (int i = 0; i < modules.size(); i++) {
            indexes.put(modules.get(i), i);
            parent[i] = i;
        }
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.children.size() < 2) continue;
            List<Module> siblings = new ArrayList<>();
            boolean movable = true;
            for (String childId : family.children) {
                LayoutSnapshot.Position child = snapshot.positionOf(childId);
                Module module = byPerson.get(childId);
                if (child == null || Math.abs(child.y - rowY) > 0.5f
                    || module == null || targeted.contains(module)) {
                    movable = false;
                    break;
                }
                if (!siblings.contains(module)) siblings.add(module);
            }
            if (!movable || siblings.size() < 2) continue;
            int first = indexes.get(siblings.get(0));
            for (int i = 1; i < siblings.size(); i++) {
                parent[find(parent, indexes.get(siblings.get(i)))] = find(parent, first);
            }
        }
        Map<Integer, Set<String>> grouped = new java.util.LinkedHashMap<>();
        for (int i = 0; i < modules.size(); i++) {
            grouped.computeIfAbsent(find(parent, i), ignored -> new LinkedHashSet<>())
                .addAll(modules.get(i).ids);
        }
        List<Module> result = new ArrayList<>();
        for (Set<String> ids : grouped.values()) {
            if (ids.size() == 0) continue;
            Module original = null;
            for (Module module : modules) {
                if (module.ids.equals(ids)) { original = module; break; }
            }
            result.add(original == null
                ? new Module(ids, BranchContour.from(snapshot, ids), rowY)
                : original);
        }
        return result;
    }

    private static int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private static float childWidth(
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot
    ) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : family.children) {
            float x = snapshot.positionOf(id).x;
            left = Math.min(left, x);
            right = Math.max(right, x + CARD_W);
        }
        return right - left;
    }

    private static List<Module> modulesAtRow(
        TreeState state, FamilyLayoutGraph graph, LayoutSnapshot snapshot, float rowY
    ) {
        List<Module> modules = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (Person person : state.people.values()) {
            if (Math.abs(person.y - rowY) > 0.5f || !visited.add(person.id)) continue;
            Set<String> household = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(person.id);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                if (!household.add(id)) continue;
                for (String partner : graph.partnersByPerson.getOrDefault(
                    id, Collections.emptySet())) {
                    LayoutSnapshot.Position point = snapshot.positionOf(partner);
                    if (point != null && Math.abs(point.y - rowY) <= 0.5f) {
                        visited.add(partner);
                        queue.add(partner);
                    }
                }
            }
            Set<String> branch = graph.descendantBranch(household);
            boolean movable = true;
            for (String id : branch) {
                Person member = state.people.get(id);
                if (member == null || member.pinned || member.y < rowY - 0.5f) {
                    movable = false;
                    break;
                }
            }
            if (movable) modules.add(new Module(branch, BranchContour.from(snapshot, branch), rowY));
        }
        // At a marriage between descendant branches, the shared lower
        // household belongs to neither upper branch exclusively. Keep that
        // junction fixed, instead of disabling every independent branch in
        // the generation or moving the shared household twice.
        Map<String, Integer> owners = new HashMap<>();
        for (Module module : modules) {
            for (String id : module.ids) owners.merge(id, 1, Integer::sum);
        }
        List<Module> exclusive = new ArrayList<>();
        for (Module module : modules) {
            Set<String> ids = new LinkedHashSet<>(module.ids);
            ids.removeIf(id -> owners.get(id) > 1);
            if (ids.isEmpty()) continue;
            Module part = new Module(ids, BranchContour.from(snapshot, ids), rowY);
            if (Float.isFinite(part.topLeft)) exclusive.add(part);
        }
        return exclusive;
    }

    private static float requiredRelativeShift(FamilyLayoutGraph graph, Module left, Module right) {
        float gap = GAP;
        if (left.ids.size() == 1 && right.ids.size() == 1) {
            String leftId = left.ids.iterator().next(), rightId = right.ids.iterator().next();
            FamilyLayoutGraph.ParentFamily family = graph.parentFamilyOf(leftId);
            if (family != null && family == graph.parentFamilyOf(rightId)) gap = GRID;
        }
        float required = -Float.MAX_VALUE;
        for (Map.Entry<Integer, BranchContour.Span> entry : right.contour.rows.entrySet()) {
            BranchContour.Span occupied = left.contour.rows.get(entry.getKey());
            if (occupied != null) required = Math.max(required,
                occupied.right + gap - entry.getValue().left);
        }
        return required == -Float.MAX_VALUE ? 0f : required;
    }

    private static float offset(
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot
    ) {
        float parentCenter = 0f;
        for (String id : family.parents) parentCenter += snapshot.positionOf(id).x + CARD_W / 2f;
        parentCenter /= family.parents.size();
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : family.children) {
            float x = snapshot.positionOf(id).x;
            left = Math.min(left, x);
            right = Math.max(right, x + CARD_W);
        }
        return (left + right) / 2f - parentCenter;
    }

    private static double[] isotonic(double[] values) {
        int n = values.length;
        double[] averages = new double[n];
        double[] totals = new double[n];
        int[] starts = new int[n];
        int[] ends = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            averages[count] = values[i];
            totals[count] = 1d;
            starts[count] = ends[count] = i;
            count++;
            while (count > 1 && averages[count - 2] > averages[count - 1]) {
                int j = count - 2;
                averages[j] = (averages[j] * totals[j]
                    + averages[j + 1] * totals[j + 1]) / (totals[j] + totals[j + 1]);
                totals[j] += totals[j + 1];
                ends[j] = ends[j + 1];
                count--;
            }
        }
        double[] result = new double[n];
        for (int j = 0; j < count; j++) {
            for (int i = starts[j]; i <= ends[j]; i++) result[i] = averages[j];
        }
        return result;
    }

    private static float snap(float value) {
        return Math.round(value / GRID) * GRID;
    }

    private static float snapCeil(float value) {
        return (float) Math.ceil(value / GRID) * GRID;
    }

    private static float snapDown(float value) {
        return (float) Math.floor(value / GRID) * GRID;
    }

    private static final class Module {
        final Set<String> ids;
        final BranchContour contour;
        final float topLeft;
        float targetSum;
        int targetCount;
        float target;
        float shift;

        Module(Set<String> ids, BranchContour contour, float rowY) {
            this.ids = ids;
            this.contour = contour;
            BranchContour.Span top = contour.rows.get(Math.round(rowY / GRID));
            topLeft = top == null ? Float.NaN : top.left;
        }
    }
}
