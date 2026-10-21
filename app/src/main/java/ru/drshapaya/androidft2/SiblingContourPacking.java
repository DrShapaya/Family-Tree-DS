package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Packs independent sibling branches by their occupied rows. */
final class SiblingContourPacking {
    private static final float GRID = TreeLayoutEngine.GRID;
    private static final float CARD_W = TreeLayoutEngine.CARD_W;
    private static final float BRANCH_GAP = GRID * 5f;

    private SiblingContourPacking() {}

    static boolean improve(TreeState state) {
        if (state == null || state.people.size() < 4) return false;
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
        LayoutSnapshot ordering = current;
        families.sort(Comparator.comparingDouble(
            (FamilyLayoutGraph.ParentFamily family) -> childRow(family, ordering)).reversed());
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : families) {
            LayoutSnapshot packed = packFamily(state, graph, constraints, current, family);
            if (packed == null) continue;
            current = packed;
            changed = true;
        }
        for (FamilyLayoutGraph.ParentFamily family : families) {
            LayoutSnapshot packed = closeSiblingGaps(
                state, graph, constraints, current, family, false);
            if (packed == null) continue;
            current = packed;
            changed = true;
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    static boolean compactAvailableGaps(TreeState state) {
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        LayoutSnapshot current = LayoutSnapshot.capture(state);
        if (!constraints.validate(graph, current).isValid()) return false;
        List<FamilyLayoutGraph.ParentFamily> families = new ArrayList<>(graph.parentFamilies);
        LayoutSnapshot ordering = current;
        families.sort(Comparator.comparingDouble(
            (FamilyLayoutGraph.ParentFamily family) -> childRow(family, ordering)).reversed());
        boolean changed = false;
        for (FamilyLayoutGraph.ParentFamily family : families) {
            LayoutSnapshot next = closeSiblingGaps(state, graph, constraints, current, family, true);
            if (next != null) {current = next; changed = true;}
        }
        if (changed) current.applyTo(state);
        return changed;
    }

    /**
     * Close only the excess gap between adjacent sibling branches.  A broad
     * branch still keeps the normal branch gap, while two plain cards use the
     * one-cell sibling gap.  Moving the two sides symmetrically preserves a
     * centred family; when that would make the family worse, the better
     * one-sided move is selected instead.
     */
    private static LayoutSnapshot closeSiblingGaps(
        TreeState state,
        FamilyLayoutGraph graph,
        LayoutConstraints constraints,
        LayoutSnapshot current,
        FamilyLayoutGraph.ParentFamily family,
        boolean fitAvailable
    ) {
        if (family.parents.isEmpty() || family.children.size() < 2) return null;
        LayoutSnapshot snapshot = current;
        boolean changed = false;
        int limit = family.children.size() * family.children.size();
        for (int pass = 0; pass < limit; pass++) {
            List<Module> modules = siblingModules(graph, snapshot, family);
            if (modules.size() < 2) break;
            modules.sort(Comparator.comparingDouble(Module::placedTopLeft));
            boolean moved = false;
            for (int index = 0; index + 1 < modules.size(); index++) {
                Module left = modules.get(index);
                Module right = modules.get(index + 1);
                float actualGap = right.placedTopLeft() - left.placedTopRight();
                float wantedGap = gapBetween(left, right);
                float excess = snapDown(actualGap - wantedGap);
                if (excess < GRID) continue;
                Set<String> prefix = new HashSet<>();
                Set<String> suffix = new HashSet<>();
                for (int item = 0; item <= index; item++) prefix.addAll(modules.get(item).ids);
                for (int item = index + 1; item < modules.size(); item++) {
                    suffix.addAll(modules.get(item).ids);
                }
                boolean prefixPinned = hasPinned(state, prefix);
                boolean suffixPinned = hasPinned(state, suffix);
                if (prefixPinned && suffixPinned) continue;
                float beforeError = familyCenterError(graph, snapshot, family);
                LayoutSnapshot best = null;
                float bestError = Float.POSITIVE_INFINITY;
                float bestMovement = Float.POSITIVE_INFINITY;
                float half = snap(excess / 2f);
                float prefixShift = fitAvailable ? fittedShift(graph, snapshot, prefix, excess) : excess;
                float suffixShift = fitAvailable ? fittedShift(graph, snapshot, suffix, -excess) : -excess;
                LayoutSnapshot[] candidates = new LayoutSnapshot[]{
                    snapshot.shifted(prefix, prefixShift, 0f),
                    snapshot.shifted(suffix, suffixShift, 0f),
                    snapshot.shifted(prefix, half, 0f)
                        .shifted(suffix, -half, 0f)
                };
                float[] movements = new float[]{prefixShift, -suffixShift, half * 2f};
                for (int candidateIndex = 0; candidateIndex < candidates.length;
                    candidateIndex++) {
                    if ((candidateIndex != 1 && prefixPinned)
                        || (candidateIndex != 0 && suffixPinned)) continue;
                    LayoutSnapshot candidate = candidates[candidateIndex];
                    if (movements[candidateIndex] < GRID) continue;
                    candidate = carryParentAnchor(state, graph, snapshot, candidate, family, fitAvailable);
                    if (!constraints.validate(graph, candidate).isValid()) continue;
                    float error = familyCenterError(graph, candidate, family);
                    if (error > Math.max(beforeError, GRID * 0.5f) + .5f) continue;
                    if (!doesNotWorsenFamilies(graph, snapshot, candidate)) continue;
                    if (!doesNotWorsenChildAlignments(graph, snapshot, candidate)) continue;
                    if (fitAvailable && !preservesProperlyAlignedLeaves(graph, snapshot, candidate)) continue;
                    if (fitAvailable && opensExternalChildGap(graph, snapshot, candidate, family)) continue;
                    if (error < bestError - 0.5f
                        || (Math.abs(error - bestError) <= 0.5f
                            && movements[candidateIndex] < bestMovement)) {
                        best = candidate;
                        bestError = error;
                        bestMovement = movements[candidateIndex];
                    }
                }
                if (best == null) continue;
                snapshot = best;
                changed = true;
                moved = true;
                break;
            }
            if (!moved) break;
        }
        return changed ? snapshot : null;
    }

    /** Compress the children together with an independent upper parent branch. */
    private static LayoutSnapshot carryParentAnchor(
        TreeState state, FamilyLayoutGraph graph, LayoutSnapshot before,
        LayoutSnapshot after, FamilyLayoutGraph.ParentFamily family, boolean fitAvailable
    ) {
        // The old anchor may already be wrong. Preserve the upper branch's
        // internal geometry, while centering it over the newly compacted span.
        float delta = snap(familyCenterOffset(graph, after, family));
        if (Math.abs(delta) < GRID / 2f) return after;
        Set<String> ancestry = graph.ancestryBranch(family.parents);
        if (ancestry.contains(state.rootId) || !Collections.disjoint(ancestry, family.children)
            || hasPinned(state, ancestry)) return after;
        return after.shifted(ancestry, fitAvailable ? fittedShift(graph, after, ancestry, delta) : delta, 0f);
    }

    private static float fittedShift(FamilyLayoutGraph graph, LayoutSnapshot snapshot,
        Set<String> moving, float desired) {
        float available = Math.abs(desired);
        for (String id : moving) {
            LayoutSnapshot.Position p = snapshot.positionOf(id);
            if (desired < 0) available = Math.min(available, p.x);
            for (String other : graph.people.keySet()) {
                if (moving.contains(other)) continue;
                LayoutSnapshot.Position q = snapshot.positionOf(other);
                if (Math.abs(p.y - q.y) > .5f) continue;
                float actual = desired > 0 ? q.x - p.x - CARD_W : p.x - q.x - CARD_W;
                if (actual < -.5f) continue;
                Set<String> parents = graph.parentsByChild.getOrDefault(id, Collections.emptySet());
                boolean plainSiblings = !parents.isEmpty() && parents.equals(graph.parentsByChild.get(other))
                    && graph.partnersByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()
                    && graph.partnersByPerson.getOrDefault(other, Collections.emptySet()).isEmpty();
                float gap = plainSiblings ? GRID : BRANCH_GAP;
                available = Math.min(available, actual - Math.min(actual, gap));
            }
        }
        return Math.copySign(snapDown(available), desired);
    }

    private static boolean opensExternalChildGap(FamilyLayoutGraph graph, LayoutSnapshot before,
        LayoutSnapshot after, FamilyLayoutGraph.ParentFamily target) {
        float y = before.positionOf(target.children.iterator().next()).y;
        List<String> row = new ArrayList<>();
        for (String id : graph.people.keySet()) if (Math.abs(before.positionOf(id).y - y) <= .5f) row.add(id);
        row.sort(Comparator.comparingDouble(id -> before.positionOf(id).x));
        for (int i = 0; i + 1 < row.size(); i++) {
            String a = row.get(i), b = row.get(i + 1);
            if (graph.partnersByPerson.getOrDefault(a, Collections.emptySet()).contains(b)) continue;
            if (target.children.contains(a) && target.children.contains(b)) continue;
            float oldGap = before.positionOf(b).x - before.positionOf(a).x - CARD_W;
            float newGap = after.positionOf(b).x - after.positionOf(a).x - CARD_W;
            if (newGap > Math.max(oldGap, BRANCH_GAP) + .5f) return true;
        }
        return false;
    }

    private static boolean hasPinned(TreeState state, Set<String> ids) {
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person != null && person.pinned) return true;
        }
        return false;
    }

    private static boolean doesNotWorsenFamilies(
        FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after
    ) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            float oldError = familyCenterError(graph, before, family);
            float newError = familyCenterError(graph, after, family);
            if (newError > Math.max(oldError, GRID * 0.5f) + .5f) return false;
        }
        return true;
    }

    private static boolean doesNotWorsenChildAlignments(
        FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after
    ) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            // Multiple siblings are centered as a span, not at their old
            // individual distances from the parents of a stretched family.
            if (family.children.size() != 1) continue;
            float parentCenter = 0f;
            float newParentCenter = 0f;
            for (String id : family.parents) {
                LayoutSnapshot.Position parent = before.positionOf(id);
                LayoutSnapshot.Position movedParent = after.positionOf(id);
                if (parent == null || movedParent == null) return false;
                parentCenter += parent.x + CARD_W / 2f;
                newParentCenter += movedParent.x + CARD_W / 2f;
            }
            parentCenter /= family.parents.size();
            newParentCenter /= family.parents.size();
            for (String childId : family.children) {
                LayoutSnapshot.Position oldChild = before.positionOf(childId);
                LayoutSnapshot.Position newChild = after.positionOf(childId);
                if (oldChild == null || newChild == null) return false;
                float oldError = Math.abs(oldChild.x + CARD_W / 2f - parentCenter);
                float newError = Math.abs(newChild.x + CARD_W / 2f - newParentCenter);
                if (newError > Math.max(oldError, GRID / 2f) + .5f) return false;
            }
        }
        return true;
    }

    private static List<Module> siblingModules(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        FamilyLayoutGraph.ParentFamily family
    ) {
        List<Module> modules = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        float rowY = Float.NaN;
        for (String childId : family.children) {
            LayoutSnapshot.Position child = snapshot.positionOf(childId);
            if (child == null) return Collections.emptyList();
            if (Float.isNaN(rowY)) rowY = child.y;
            if (Math.abs(rowY - child.y) > 0.5f) return Collections.emptyList();
            Set<String> ids = graph.descendantBranch(Collections.singleton(childId));
            if (!Collections.disjoint(claimed, ids)) return Collections.emptyList();
            claimed.addAll(ids);
            Module module = new Module(childId, ids,
                BranchContour.from(snapshot, ids), rowY);
            if (!module.valid()) return Collections.emptyList();
            modules.add(module);
        }
        return modules;
    }

    static boolean preservesAlignedLeaves(
        FamilyLayoutGraph graph, LayoutSnapshot before, LayoutSnapshot after
    ) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty()) continue;
            float oldCenter = 0f, newCenter = 0f;
            for (String id : family.parents) {
                oldCenter += before.positionOf(id).x + CARD_W / 2f;
                newCenter += after.positionOf(id).x + CARD_W / 2f;
            }
            oldCenter /= family.parents.size();
            newCenter /= family.parents.size();
            for (String child : family.children) {
                if (!graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    || !graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()) continue;
                float oldError = Math.abs(before.positionOf(child).x + CARD_W / 2f - oldCenter);
                float newError = Math.abs(after.positionOf(child).x + CARD_W / 2f - newCenter);
                if (oldError < GRID / 2f && newError > GRID / 2f) return false;
            }
        }
        return true;
    }

    static boolean preservesProperlyAlignedLeaves(FamilyLayoutGraph graph,
        LayoutSnapshot before, LayoutSnapshot after) {
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            float oldParent = 0, newParent = 0, left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            Set<String> allChildren = new HashSet<>();
            for (String parent : family.parents) {
                oldParent += before.positionOf(parent).x + CARD_W / 2f;
                newParent += after.positionOf(parent).x + CARD_W / 2f;
                allChildren.addAll(graph.childrenByParent.getOrDefault(parent, Collections.emptySet()));
            }
            if (family.parents.isEmpty() || allChildren.isEmpty()) continue;
            oldParent /= family.parents.size(); newParent /= family.parents.size();
            for (String child : allChildren) {
                float x = before.positionOf(child).x + CARD_W / 2f;
                left = Math.min(left, x); right = Math.max(right, x);
                for (FamilyLayoutGraph.PartnerUnit unit : graph.partnerUnitsByPerson.getOrDefault(child, Collections.emptyList())) {
                    for (String partner : unit.people) {
                        if (before.positionOf(partner).y != before.positionOf(child).y) continue;
                        float partnerX = before.positionOf(partner).x + CARD_W / 2f;
                        left = Math.min(left, partnerX); right = Math.max(right, partnerX);
                    }
                }
            }
            if (Math.abs((left + right) / 2f - oldParent) > GRID / 2f) continue;
            for (String child : family.children) {
                if (!graph.childrenByParent.getOrDefault(child, Collections.emptySet()).isEmpty()
                    || !graph.partnersByPerson.getOrDefault(child, Collections.emptySet()).isEmpty()) continue;
                float oldError = Math.abs(before.positionOf(child).x + CARD_W / 2f - oldParent);
                float newError = Math.abs(after.positionOf(child).x + CARD_W / 2f - newParent);
                if (oldError < GRID / 2f && newError > GRID / 2f) return false;
            }
        }
        return true;
    }

    private static float familyCenterError(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        FamilyLayoutGraph.ParentFamily family
    ) {
        return Math.abs(familyCenterOffset(graph, snapshot, family));
    }

    private static float familyCenterOffset(
        FamilyLayoutGraph graph, LayoutSnapshot snapshot, FamilyLayoutGraph.ParentFamily family
    ) {
        float parentCenter = 0f;
        for (String id : family.parents) {
            LayoutSnapshot.Position parent = snapshot.positionOf(id);
            if (parent == null) return Float.POSITIVE_INFINITY;
            parentCenter += parent.x + CARD_W / 2f;
        }
        parentCenter /= family.parents.size();
        float left = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        for (String id : family.children) {
            LayoutSnapshot.Position child = snapshot.positionOf(id);
            if (child == null) return Float.POSITIVE_INFINITY;
            left = Math.min(left, child.x);
            right = Math.max(right, child.x + CARD_W);
        }
        return (left + right) / 2f - parentCenter;
    }

    private static LayoutSnapshot packFamily(
        TreeState state,
        FamilyLayoutGraph graph,
        LayoutConstraints constraints,
        LayoutSnapshot current,
        FamilyLayoutGraph.ParentFamily family
    ) {
        if (family.children.size() < 3 || family.parents.isEmpty()
            || hasExplicitSiblingOrder(graph, family.children)) return null;
        List<Module> modules = new ArrayList<>();
        Set<String> claimed = new HashSet<>();
        float rowY = Float.NaN;
        for (String childId : family.children) {
            LayoutSnapshot.Position child = current.positionOf(childId);
            if (child == null || !child.isFinite()) return null;
            if (Float.isNaN(rowY)) rowY = child.y;
            else if (Math.abs(rowY - child.y) > 0.5f) return null;
            Set<String> ids = graph.descendantBranch(Collections.singleton(childId));
            if (!Collections.disjoint(claimed, ids)) return null;
            claimed.addAll(ids);
            for (String id : ids) {
                Person person = state.people.get(id);
                if (person != null && person.pinned) return null;
            }
            BranchContour contour = BranchContour.from(current, ids);
            Module module = new Module(childId, ids, contour, rowY);
            if (!module.valid()) return null;
            modules.add(module);
        }
        List<Module> broad = new ArrayList<>();
        List<Module> shallow = new ArrayList<>();
        for (Module module : modules) {
            if (module.broad) broad.add(module);
            else shallow.add(module);
        }
        if (broad.size() < 2 || shallow.isEmpty()) return null;
        broad.sort(Comparator.comparingDouble(value -> value.topLeft));
        shallow.sort(Comparator.comparingDouble((Module value) -> -value.topWidth())
            .thenComparingDouble(value -> value.topLeft));

        // Place broad subtrees first. Their deepest occupied row determines the
        // distance; a large gap left on the child row remains available to a
        // shallower sibling rather than being treated as a solid rectangle.
        List<Module> placed = new ArrayList<>();
        for (Module module : broad) {
            if (placed.isEmpty()) {
                module.shift = 0f;
            } else {
                float needed = Float.NEGATIVE_INFINITY;
                for (Module previous : placed) {
                    needed = Math.max(needed,
                        exactRightShift(previous.placedContour(), module.contour));
                }
                if (!Float.isFinite(needed)) return null;
                module.shift = snapCeil(needed);
            }
            placed.add(module);
        }

        for (Module module : shallow) {
            Module bestBefore = null;
            float bestSlack = Float.POSITIVE_INFINITY;
            List<Module> rowOrder = new ArrayList<>(placed);
            rowOrder.sort(Comparator.comparingDouble(Module::placedTopLeft));
            for (int index = 0; index + 1 < rowOrder.size(); index++) {
                Module before = rowOrder.get(index);
                Module after = rowOrder.get(index + 1);
                float gap = after.placedTopLeft() - before.placedTopRight();
                float slack = gap - module.topWidth()
                    - gapBetween(before, module) - gapBetween(module, after);
                if (slack >= -0.5f && slack < bestSlack - 0.5f) {
                    bestBefore = before;
                    bestSlack = slack;
                }
            }
            if (bestBefore != null) {
                module.shift = snap(bestBefore.placedTopRight()
                    + gapBetween(bestBefore, module) - module.topLeft);
            } else {
                Module last = null;
                for (Module existing : placed) {
                    if (last == null || existing.placedTopRight() > last.placedTopRight()) {
                        last = existing;
                    }
                }
                module.shift = snap(last.placedTopRight()
                    + gapBetween(last, module) - module.topLeft);
            }
            placed.add(module);
        }

        float oldLeft = Float.POSITIVE_INFINITY;
        float oldRight = Float.NEGATIVE_INFINITY;
        float packedLeft = Float.POSITIVE_INFINITY;
        float packedRight = Float.NEGATIVE_INFINITY;
        float oldTopLeft = Float.POSITIVE_INFINITY;
        float oldTopRight = Float.NEGATIVE_INFINITY;
        float packedTopLeft = Float.POSITIVE_INFINITY;
        float packedTopRight = Float.NEGATIVE_INFINITY;
        for (Module module : modules) {
            oldLeft = Math.min(oldLeft, module.contour.left());
            oldRight = Math.max(oldRight, module.contour.right());
            packedLeft = Math.min(packedLeft, module.contour.left() + module.shift);
            packedRight = Math.max(packedRight, module.contour.right() + module.shift);
            oldTopLeft = Math.min(oldTopLeft, module.topLeft);
            oldTopRight = Math.max(oldTopRight, module.topRight);
            packedTopLeft = Math.min(packedTopLeft, module.placedTopLeft());
            packedTopRight = Math.max(packedTopRight, module.placedTopRight());
        }
        if (oldTopRight - oldTopLeft - (packedTopRight - packedTopLeft) < GRID - 0.5f
            || oldRight - oldLeft - (packedRight - packedLeft) < GRID - 0.5f) {
            return null;
        }

        float minTranslation = snapCeil(oldLeft - packedLeft);
        float maxTranslation = snapDown(oldRight - packedRight);
        if (minTranslation > maxTranslation + 0.5f) return null;
        float parentCenter = 0f;
        for (String id : family.parents) {
            parentCenter += current.positionOf(id).x + CARD_W / 2f;
        }
        parentCenter /= family.parents.size();
        float firstChild = Float.POSITIVE_INFINITY;
        float lastChild = Float.NEGATIVE_INFINITY;
        for (Module module : modules) {
            float childX = current.positionOf(module.childId).x + module.shift;
            firstChild = Math.min(firstChild, childX);
            lastChild = Math.max(lastChild, childX + CARD_W);
        }
        float translation = Math.max(minTranslation, Math.min(maxTranslation,
            snap(parentCenter - (firstChild + lastChild) / 2f)));

        LayoutSnapshot candidate = current;
        for (Module module : modules) {
            float shift = module.shift + translation;
            if (Math.abs(shift) >= 0.5f) candidate = candidate.shifted(module.ids, shift, 0f);
        }
        if (!constraints.validate(graph, candidate).isValid()
            || !preservesAlignedLeaves(graph, current, candidate)) return null;
        float[] before = centerErrors(graph, current);
        float[] after = centerErrors(graph, candidate);
        if (after[0] > before[0] + 0.5f || after[1] > before[1] + 0.5f) return null;
        return candidate;
    }

    private static boolean hasExplicitSiblingOrder(
        FamilyLayoutGraph graph, Collection<String> children
    ) {
        Set<String> members = new HashSet<>(children);
        for (String id : children) {
            for (String sibling : graph.siblingsByPerson.getOrDefault(
                id, Collections.emptySet())) {
                if (members.contains(sibling)) return true;
            }
        }
        return false;
    }

    private static float exactRightShift(BranchContour left, BranchContour right) {
        float needed = Float.NEGATIVE_INFINITY;
        for (java.util.Map.Entry<Integer, BranchContour.Span> entry : right.rows.entrySet()) {
            BranchContour.Span occupied = left.rows.get(entry.getKey());
            if (occupied != null) {
                needed = Math.max(needed,
                    occupied.right + BRANCH_GAP - entry.getValue().left);
            }
        }
        return needed;
    }

    private static float gapBetween(Module left, Module right) {
        return left.ids.size() == 1 && right.ids.size() == 1
            && !left.broad && !right.broad ? GRID : BRANCH_GAP;
    }

    private static float childRow(
        FamilyLayoutGraph.ParentFamily family, LayoutSnapshot snapshot
    ) {
        for (String childId : family.children) {
            LayoutSnapshot.Position point = snapshot.positionOf(childId);
            if (point != null) return point.y;
        }
        return Float.NEGATIVE_INFINITY;
    }

    private static float[] centerErrors(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        float total = 0f;
        float largest = 0f;
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (family.parents.isEmpty() || family.children.isEmpty()) continue;
            float parentCenter = 0f;
            for (String id : family.parents) {
                parentCenter += snapshot.positionOf(id).x + CARD_W / 2f;
            }
            parentCenter /= family.parents.size();
            float left = Float.POSITIVE_INFINITY;
            float right = Float.NEGATIVE_INFINITY;
            for (String id : family.children) {
                float x = snapshot.positionOf(id).x;
                left = Math.min(left, x);
                right = Math.max(right, x + CARD_W);
            }
            float error = Math.abs((left + right) / 2f - parentCenter);
            total += error;
            largest = Math.max(largest, error);
        }
        return new float[]{total, largest};
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
        final String childId;
        final Set<String> ids;
        final BranchContour contour;
        final float topLeft;
        final float topRight;
        final boolean broad;
        float shift;

        Module(String childId, Set<String> ids, BranchContour contour, float rowY) {
            this.childId = childId;
            this.ids = ids;
            this.contour = contour;
            BranchContour.Span top = contour.rows.get(Math.round(rowY / GRID));
            topLeft = top == null ? Float.NaN : top.left;
            topRight = top == null ? Float.NaN : top.right;
            boolean below = false;
            for (Integer row : contour.rows.keySet()) {
                if (row > Math.round(rowY / GRID)) below = true;
            }
            broad = below;
        }

        boolean valid() {
            return Float.isFinite(topLeft) && Float.isFinite(topRight);
        }

        float topWidth() {
            return topRight - topLeft;
        }

        float placedTopLeft() {
            return topLeft + shift;
        }

        float placedTopRight() {
            return topRight + shift;
        }

        BranchContour placedContour() {
            return contour.shifted(shift);
        }
    }
}
