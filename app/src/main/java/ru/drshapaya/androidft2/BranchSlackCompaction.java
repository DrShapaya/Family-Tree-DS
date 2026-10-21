package ru.drshapaya.androidft2;

import java.util.*;

/** Removes empty horizontal strips by translating complete household contours. */
final class BranchSlackCompaction {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float WIDTH = TreeLayoutEngine.CARD_W;

    static boolean improve(TreeState state) {
        if (state == null || state.people.size() < 3) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        List<Set<String>> households = households(graph);
        boolean changed = false;
        for (int pass = 0; pass < households.size(); pass++) {
            Map<Float, List<Household>> rows = rows(households, current);
            boolean moved = false;
            // Lower contours determine the space required by upper branches.
            List<Float> levels = new ArrayList<>(rows.keySet());
            levels.sort(Comparator.reverseOrder());
            search:
            for (float y : levels) {
                List<Household> row = rows.get(y);
                for (int i = 0; i + 1 < row.size(); i++) {
                    Household left = row.get(i), right = row.get(i + 1);
                    float excess = down(right.left - left.right - gap(graph, left, right));
                    if (excess < GRID) continue;
                    float cut = (left.right + right.left) / 2f;
                    // First move the whole side. Upper/lower contours also fit
                    // around a shared family whose other branch stays put.
                    for (int scope = 0; scope < 4; scope++) {
                        for (int side = 0; side < 2; side++) {
                            Set<String> moving = new LinkedHashSet<>();
                            boolean pinned = false;
                            if (scope == 3) {
                                moving.addAll(exclusiveDescendants(graph, row, side == 0 ? left : right));
                            } else for (List<Household> otherRow : rows.values()) {
                                for (Household h : otherRow) {
                                    if ((scope == 1 && h.y > y) || (scope == 2 && h.y < y)) continue;
                                    boolean belongs = side == 0 ? h.right <= cut : h.left >= cut;
                                    if (!belongs) continue;
                                    moving.addAll(h.ids);
                                    for (String id : h.ids) pinned |= state.people.get(id).pinned;
                                }
                            }
                            if (scope == 2) {
                                for (Household h : row) if (moving.containsAll(h.ids))
                                    moving.addAll(graph.descendantBranch(h.ids));
                            }
                            carryAttachedFamilies(graph, moving);
                            for (String id : moving) pinned |= state.people.get(id).pinned;
                            if (moving.isEmpty() || pinned || moving.contains(state.rootId)) continue;
                            float available = excess;
                            for (List<Household> otherRow : rows.values()) {
                                for (int k = 0; k + 1 < otherRow.size(); k++) {
                                    Household a = otherRow.get(k), b = otherRow.get(k + 1);
                                    boolean ma = moving.contains(a.ids.iterator().next());
                                    boolean mb = moving.contains(b.ids.iterator().next());
                                    if (ma == mb || (side == 0 ? !ma : !mb)) continue;
                                    float actual = b.left - a.right;
                                    available = Math.min(available, actual - Math.min(actual, gap(graph, a, b)));
                                }
                            }
                            float shift = down(available) * (side == 0 ? 1 : -1);
                            if (Math.abs(shift) < GRID) continue;
                            LayoutSnapshot candidate = current.shifted(moving, shift, 0);

                            if (!preservesAttachments(graph, current, candidate)
                                || opensRowGaps(graph, rows, current, candidate)
                                || !SiblingContourPacking.preservesAlignedLeaves(graph, current, candidate)
                                || !constraints.validate(graph, candidate).isValid()) continue;
                            current = candidate;
                            moved = changed = true;
                            break search;
                        }
                    }
                }
            }
            if (!moved) break;
        }
        if (changed) current.applyTo(state);
        return changed;
    }



    private static Set<String> exclusiveDescendants(FamilyLayoutGraph graph,
        List<Household> row, Household selected) {
        Map<String, Integer> owners = new HashMap<>();
        Set<String> branch = null;
        for (Household h : row) {
            Set<String> descendants = graph.descendantBranch(h.ids);
            for (String id : descendants) owners.merge(id, 1, Integer::sum);
            if (h == selected) branch = descendants;
        }
        branch.removeIf(id -> !selected.ids.contains(id) && owners.getOrDefault(id, 0) > 1);
        return branch;
    }

    private static boolean opensRowGaps(FamilyLayoutGraph graph, Map<Float, List<Household>> rows,
        LayoutSnapshot before, LayoutSnapshot after) {
        for (List<Household> row : rows.values()) {
            for (int i = 0; i + 1 < row.size(); i++) {
                Household a = row.get(i), b = row.get(i + 1);
                float da = after.positionOf(a.ids.iterator().next()).x - before.positionOf(a.ids.iterator().next()).x;
                float db = after.positionOf(b.ids.iterator().next()).x - before.positionOf(b.ids.iterator().next()).x;
                float old = b.left - a.right;
                if (old + db - da > Math.max(old, gap(graph, a, b)) + .5f) return true;
            }
        }
        return false;
    }

    private static boolean preservesAttachments(FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot after) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            float[] old = attachment(family, before), next = attachment(family, after);
            // Internal rigid families retain their exact geometry. At a shared
            // boundary the sibling span may shrink, while its parents remain
            // inside that span; freezing the old midpoint would forbid this.
            if (next[0] > old[0] + .5f || next[1] > old[1] + .5f) return false;
            if (family.children.size() <= 2 && old[2] <= GRID / 2f && next[2] > GRID / 2f) return false;
        }
        return true;
    }

    private static void carryAttachedFamilies(FamilyLayoutGraph graph, Set<String> moving) {
        boolean changed;
        do {
            changed = false;
            for (String id : new ArrayList<>(moving))
                changed |= moving.addAll(graph.partnersByPerson.getOrDefault(id, Collections.emptySet()));
            for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
                if (family.children.size() == 1 && moving.containsAll(family.parents))
                    changed |= moving.addAll(family.children);
                if (family.children.size() != 1 || !moving.containsAll(family.children)) continue;
                boolean complete = true;
                for (String parent : family.parents)
                    complete &= moving.containsAll(graph.childrenByParent.getOrDefault(parent, Collections.emptySet()));
                if (complete) changed |= moving.addAll(family.parents);
            }
        } while (changed);
    }

    private static float[] attachment(FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot) {
        float parent = 0, left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
        for (String id : family.parents) parent += snapshot.positionOf(id).x + WIDTH / 2;
        parent /= family.parents.size();
        for (String id : family.children) {
            float center = snapshot.positionOf(id).x + WIDTH / 2;
            left = Math.min(left, center); right = Math.max(right, center);
        }
        return new float[]{Math.max(0, Math.max(left - parent, parent - right)), right - left,
            Math.abs((left + right) / 2f - parent)};
    }

    private static List<Set<String>> households(FamilyLayoutGraph graph) {
        List<Set<String>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : graph.people.keySet()) {
            if (!seen.add(id)) continue;
            Set<String> unit = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>(); queue.add(id);
            while (!queue.isEmpty()) {
                String next = queue.remove(); unit.add(next);
                for (String partner : graph.partnersByPerson.getOrDefault(next, Collections.emptySet()))
                    if (seen.add(partner)) queue.add(partner);
            }
            result.add(unit);
        }
        return result;
    }

    private static Map<Float, List<Household>> rows(List<Set<String>> households, LayoutSnapshot snapshot) {
        Map<Float, List<Household>> rows = new TreeMap<>();
        for (Set<String> ids : households) {
            Household h = new Household(ids, snapshot);
            rows.computeIfAbsent(h.y, ignored -> new ArrayList<>()).add(h);
        }
        for (List<Household> row : rows.values()) row.sort(Comparator.comparingDouble(h -> h.left));
        return rows;
    }

    private static float gap(FamilyLayoutGraph graph, Household a, Household b) {
        if (a.ids.size() == 1 && b.ids.size() == 1) {
            String first = a.ids.iterator().next(), second = b.ids.iterator().next();
            Set<String> parents = graph.parentsByChild.getOrDefault(first, Collections.emptySet());
            if ((!parents.isEmpty() && parents.equals(graph.parentsByChild.get(second)))
                || graph.siblingsByPerson.getOrDefault(first, Collections.emptySet()).contains(second)) return GRID;
        }
        return 5 * GRID;
    }

    private static float down(float x) { return (float)Math.floor((x + .5f) / GRID) * GRID; }
    private static final class Household {
        final Set<String> ids;
        final float y, left, right;
        Household(Set<String> ids, LayoutSnapshot snapshot) {
            this.ids = ids;
            y = snapshot.positionOf(ids.iterator().next()).y;
            float l = Float.POSITIVE_INFINITY, r = Float.NEGATIVE_INFINITY;
            for (String id : ids) { float x = snapshot.positionOf(id).x; l = Math.min(l, x); r = Math.max(r, x + WIDTH); }
            left = l; right = r;
        }
    }
}
