package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Progressive, semantic scope for one local layout action. */
final class LayoutImpactRegion {
    private static final int MAX_EXPANSION_LEVEL = 5;

    final LinkedHashSet<String> addedIds;
    final LinkedHashSet<String> activeIds;
    final LinkedHashSet<String> lockedIds;
    final int expansionLevel;

    private LayoutImpactRegion(
        Collection<String> addedIds,
        Collection<String> activeIds,
        Collection<String> lockedIds,
        int expansionLevel
    ) {
        this.addedIds = validSet(addedIds);
        this.activeIds = validSet(activeIds);
        this.lockedIds = validSet(lockedIds);
        this.expansionLevel = expansionLevel;
    }

    static LayoutImpactRegion initial(
        FamilyLayoutGraph graph,
        Collection<String> addedIds,
        String anchorId
    ) {
        LinkedHashSet<String> added = validPeople(graph, addedIds);
        LinkedHashSet<String> active = new LinkedHashSet<>(added);
        if (graph != null && graph.people.containsKey(anchorId)) active.add(anchorId);
        LinkedHashSet<String> locked = new LinkedHashSet<>();
        if (graph != null
            && graph.people.containsKey(graph.rootId)
            && !added.contains(graph.rootId)) locked.add(graph.rootId);
        return new LayoutImpactRegion(added, active, locked, 0);
    }

    LayoutImpactRegion expand(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || !canExpand()) return this;
        LinkedHashSet<String> expanded = new LinkedHashSet<>(activeIds);
        int nextLevel = expansionLevel + 1;
        if (nextLevel == 1) addHouseholds(graph, activeIds, expanded);
        else if (nextLevel == 2) addSiblingFamilies(graph, activeIds, expanded);
        else if (nextLevel == 3) addParentFamilies(graph, activeIds, expanded);
        else if (nextLevel == 4) addContourNeighbors(graph, snapshot, expanded);
        else addAncestry(graph, activeIds, expanded);
        expanded.retainAll(graph.people.keySet());
        return new LayoutImpactRegion(addedIds, expanded, lockedIds, nextLevel);
    }

    boolean canExpand() {
        return expansionLevel < MAX_EXPANSION_LEVEL;
    }

    Set<String> movableIds() {
        LinkedHashSet<String> movable = new LinkedHashSet<>(activeIds);
        movable.removeAll(lockedIds);
        return Collections.unmodifiableSet(movable);
    }

    boolean allows(LayoutOperation operation) {
        if (operation == null || operation.affectedIds().isEmpty()) return false;
        return Collections.disjoint(operation.affectedIds(), lockedIds)
            && activeIds.containsAll(operation.affectedIds());
    }

    private static void addHouseholds(
        FamilyLayoutGraph graph,
        Collection<String> source,
        Set<String> target
    ) {
        for (String id : new ArrayList<>(source)) {
            target.addAll(graph.partnersByPerson.getOrDefault(id, Collections.emptySet()));
            target.addAll(graph.parentsByChild.getOrDefault(id, Collections.emptySet()));
            target.addAll(graph.childrenByParent.getOrDefault(id, Collections.emptySet()));
        }
    }

    private static void addSiblingFamilies(
        FamilyLayoutGraph graph,
        Collection<String> source,
        Set<String> target
    ) {
        for (String id : new ArrayList<>(source)) {
            LinkedHashSet<String> siblings = new LinkedHashSet<>(
                graph.siblingsByPerson.getOrDefault(id, Collections.emptySet()));
            FamilyLayoutGraph.ParentFamily family = graph.parentFamilyOf(id);
            if (family != null) siblings.addAll(family.children);
            for (String siblingId : siblings) {
                target.addAll(graph.descendantBranch(Collections.singleton(siblingId)));
            }
        }
    }

    private static void addParentFamilies(
        FamilyLayoutGraph graph,
        Collection<String> source,
        Set<String> target
    ) {
        for (String id : new ArrayList<>(source)) {
            Set<String> parents = graph.parentsByChild.getOrDefault(id, Collections.emptySet());
            target.addAll(parents);
            for (String parentId : parents) {
                target.addAll(graph.partnersByPerson.getOrDefault(
                    parentId,
                    Collections.emptySet()));
            }
            target.addAll(graph.partnersByPerson.getOrDefault(id, Collections.emptySet()));
        }
    }

    private static void addContourNeighbors(
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot,
        Set<String> target
    ) {
        BranchContour activeContour = BranchContour.from(snapshot, target);
        LinkedHashSet<String> neighbors = new LinkedHashSet<>();
        for (String id : graph.people.keySet()) {
            if (target.contains(id)) continue;
            Set<String> branch = graph.outwardBranch(id);
            if (branch.isEmpty() || !Collections.disjoint(branch, target)) continue;
            BranchContour contour = BranchContour.from(snapshot, branch);
            if (contoursAreNear(activeContour, contour, TreeLayoutEngine.GRID * 5f)) {
                neighbors.addAll(branch);
            }
        }
        target.addAll(neighbors);
    }

    private static void addAncestry(
        FamilyLayoutGraph graph,
        Collection<String> source,
        Set<String> target
    ) {
        target.addAll(graph.ancestryBranch(source));
    }

    private static boolean contoursAreNear(
        BranchContour first,
        BranchContour second,
        float gap
    ) {
        for (Map.Entry<Integer, BranchContour.Span> entry : first.rows.entrySet()) {
            BranchContour.Span other = second.rows.get(entry.getKey());
            if (other == null) continue;
            BranchContour.Span span = entry.getValue();
            if (span.left <= other.right + gap && other.left <= span.right + gap) return true;
        }
        return false;
    }

    private static LinkedHashSet<String> validPeople(
        FamilyLayoutGraph graph,
        Collection<String> ids
    ) {
        LinkedHashSet<String> result = validSet(ids);
        if (graph == null) result.clear();
        else result.retainAll(graph.people.keySet());
        return result;
    }

    private static LinkedHashSet<String> validSet(Collection<String> ids) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) if (id != null && !id.isEmpty()) result.add(id);
        }
        return result;
    }
}
