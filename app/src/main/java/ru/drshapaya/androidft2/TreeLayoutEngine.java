package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
        float requiredRight = surfaceWidth(state);
        float requiredBottom = surfaceHeight(state);
        for (Person person : state.people.values()) {
            if (!isValidPosition(person)) continue;
            requiredRight = Math.max(requiredRight, person.x + CARD_W + MARGIN_X);
            requiredBottom = Math.max(requiredBottom, person.y + CARD_H + MARGIN_Y);
        }
        state.workspaceWidth = normalizeSurfaceWidth((int) Math.ceil(requiredRight));
        state.workspaceHeight = normalizeSurfaceHeight((int) Math.ceil(requiredBottom));
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
            }
            index++;
        }
    }

    static boolean hasSavedPositions(TreeState state) {
        if (state == null) return false;
        for (Person person : state.people.values()) {
            if (isValidPosition(person)) return true;
        }
        return false;
    }

    static void layout(TreeState state) {
        layout(state, Collections.emptySet());
    }

    /**
     * Rebuilds the saved tree by replaying deterministic relationship additions.
     * This is the manual "Arrange" operation: it deliberately ignores old card
     * coordinates and uses the same local rules as auto-arrange-on-add.
     */
    static void rebuildStepwise(TreeState state) {
        if (state == null || state.people.isEmpty()) return;

        TreeState source = TreeStateCopier.copy(state);
        Relations finalRelations = buildRelations(source);
        Map<String, Integer> sourceOrder = new LinkedHashMap<>();
        int order = 0;
        for (String id : source.people.keySet()) sourceOrder.put(id, order++);
        String rootId = source.people.containsKey(source.rootId)
            ? source.rootId
            : source.people.keySet().iterator().next();
        Map<String, Integer> distances = relationshipDistances(rootId, finalRelations, source);
        Map<String, Integer> generations = relationshipGenerations(rootId, finalRelations, source);

        TreeState working = new TreeState();
        TreeStateCopier.copyMetadata(source, working);
        // A full rebuild must not inherit dimensions produced by an earlier rebuild:
        // doing so moves the center on every press and breaks idempotence. Start from
        // the same canonical field and expand it only if the new layout requires it.
        working.workspaceWidth = (int) SURFACE_W;
        working.workspaceHeight = (int) SURFACE_H;
        working.rootId = rootId;
        working.selectedId = rootId;
        Person root = TreeStateCopier.copyPerson(source.people.get(rootId));
        root.x = snap(surfaceWidth(working) / 2f - CARD_W / 2f);
        root.y = snap(surfaceHeight(working) / 2f - CARD_H / 2f);
        working.people.put(root.id, root);
        syncAvailableRelations(source, working);

        while (working.people.size() < source.people.size()) {
            StepAction next = nextRebuildAction(
                source,
                working,
                finalRelations,
                distances,
                generations,
                sourceOrder);
            if (next == null) {
                String seedId = firstMissingPerson(source, working, sourceOrder);
                if (seedId.isEmpty()) break;
                Person seed = TreeStateCopier.copyPerson(source.people.get(seedId));
                seed.x = snap(maxRight(working) + BRANCH_GAP);
                seed.y = root.y;
                working.people.put(seed.id, seed);
                syncAvailableRelations(source, working);
                continue;
            }
            addRebuildStep(source, working, next, sourceOrder);
        }

        Person arrangedRoot = working.people.get(rootId);
        float rootTargetX = snap(surfaceWidth(working) / 2f - CARD_W / 2f);
        if (arrangedRoot != null) {
            shiftPeople(
                working,
                working.people.keySet(),
                snap(rootTargetX - arrangedRoot.x));
        }
        SmartLayoutSolver.compactRebuiltTree(working);
        translateTreeInsideLeftBoundary(working);
        expandWorkspaceToFit(working);

        for (Person person : state.people.values()) {
            Person arranged = working.people.get(person.id);
            if (arranged == null) continue;
            person.x = arranged.x;
            person.y = arranged.y;
        }
        state.workspaceWidth = working.workspaceWidth;
        state.workspaceHeight = working.workspaceHeight;
    }

    private static void addRebuildStep(
        TreeState source,
        TreeState working,
        StepAction action,
        Map<String, Integer> sourceOrder
    ) {
        Person anchor = working.people.get(action.anchorId);
        float baseX = anchor == null ? surfaceWidth(working) / 2f : anchor.x;
        float baseY = anchor == null ? surfaceHeight(working) / 2f : anchor.y;
        List<String> ids = new ArrayList<>(action.addedIds);
        ids.sort(Comparator.comparingInt(id -> sourceOrder.getOrDefault(id, Integer.MAX_VALUE)));
        LinkedHashSet<String> added = new LinkedHashSet<>();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            Person original = source.people.get(id);
            if (original == null || working.people.containsKey(id)) continue;
            Person person = TreeStateCopier.copyPerson(original);
            person.x = snap(baseX + index * (CARD_W + GRID));
            person.y = snap(baseY);
            working.people.put(id, person);
            added.add(id);
        }
        if (added.isEmpty()) return;
        syncAvailableRelations(source, working);
        layoutAfterAddition(working, added, action.anchorId, action.action, true);
    }

    private static void syncAvailableRelations(TreeState source, TreeState working) {
        Set<String> existing = new HashSet<>();
        for (Relation relation : working.links) existing.add(relation.id);
        for (Relation relation : source.links) {
            if (existing.contains(relation.id)
                || !working.people.containsKey(relation.from)
                || !working.people.containsKey(relation.to)) continue;
            working.links.add(TreeStateCopier.copyRelation(relation));
            existing.add(relation.id);
        }
    }

    private static StepAction nextRebuildAction(
        TreeState source,
        TreeState working,
        Relations relations,
        Map<String, Integer> distances,
        Map<String, Integer> generations,
        Map<String, Integer> sourceOrder
    ) {
        List<StepAction> candidates = new ArrayList<>();
        Set<String> known = working.people.keySet();

        for (String anchorId : known) {
            for (String partnerId : relations.partnersByPerson.getOrDefault(
                anchorId,
                Collections.emptySet())) {
                if (!known.contains(partnerId)) {
                    candidates.add(new StepAction(
                        "partner",
                        anchorId,
                        Collections.singleton(partnerId),
                        0,
                        rebuildDistance(Collections.singleton(partnerId), distances),
                        sourceOrder));
                }
            }
        }

        for (String childId : known) {
            LinkedHashSet<String> missingParents = new LinkedHashSet<>(
                relations.parentsByChild.getOrDefault(childId, Collections.emptySet()));
            missingParents.removeAll(known);
            if (!missingParents.isEmpty()) {
                candidates.add(new StepAction(
                    "parents",
                    childId,
                    missingParents,
                    2,
                    rebuildDistance(missingParents, distances),
                    sourceOrder));
            }
        }

        Map<String, Set<String>> childrenByFamily = new LinkedHashMap<>();
        Map<String, Set<String>> parentsByFamily = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            List<String> sortedParents = new ArrayList<>(entry.getValue());
            Collections.sort(sortedParents);
            String key = String.join("|", sortedParents);
            childrenByFamily.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(entry.getKey());
            parentsByFamily.putIfAbsent(key, new LinkedHashSet<>(entry.getValue()));
        }
        for (String key : childrenByFamily.keySet()) {
            Set<String> parentIds = parentsByFamily.get(key);
            if (!known.containsAll(parentIds)) continue;
            LinkedHashSet<String> missingChildren = new LinkedHashSet<>(childrenByFamily.get(key));
            missingChildren.removeAll(known);
            if (missingChildren.isEmpty()) continue;
            List<String> knownChildren = new ArrayList<>(childrenByFamily.get(key));
            knownChildren.removeIf(id -> !known.contains(id));
            knownChildren.sort(Comparator.comparingInt(
                id -> distances.getOrDefault(id, Integer.MAX_VALUE)));
            int parentGeneration = Integer.MAX_VALUE;
            for (String parentId : parentIds) {
                parentGeneration = Math.min(
                    parentGeneration,
                    generations.getOrDefault(parentId, 0));
            }
            boolean collateral = parentGeneration < 0 && !knownChildren.isEmpty();
            String anchorId = collateral
                ? knownChildren.get(0)
                : firstBySourceOrder(parentIds, sourceOrder);
            candidates.add(new StepAction(
                collateral ? "siblings" : "children",
                anchorId,
                missingChildren,
                collateral ? 1 : 3,
                rebuildDistance(missingChildren, distances),
                sourceOrder));
        }

        for (String anchorId : known) {
            LinkedHashSet<String> explicit = new LinkedHashSet<>(
                relations.siblingsByPerson.getOrDefault(anchorId, Collections.emptySet()));
            explicit.removeAll(known);
            explicit.removeIf(id -> !relations.parentsByChild.getOrDefault(
                id,
                Collections.emptySet()).isEmpty());
            if (!explicit.isEmpty()) {
                candidates.add(new StepAction(
                    "siblings",
                    anchorId,
                    explicit,
                    1,
                    rebuildDistance(explicit, distances),
                    sourceOrder));
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator
            .comparingInt((StepAction action) -> action.distance)
            .thenComparingInt(action -> action.priority)
            .thenComparingInt(action -> sourceOrder.getOrDefault(
                action.anchorId,
                Integer.MAX_VALUE))
            .thenComparingInt(action -> action.firstOrder));
        return candidates.get(0);
    }

    private static int rebuildDistance(Collection<String> ids, Map<String, Integer> distances) {
        int result = Integer.MAX_VALUE;
        for (String id : ids) result = Math.min(result, distances.getOrDefault(id, Integer.MAX_VALUE));
        return result;
    }

    private static String firstBySourceOrder(
        Collection<String> ids,
        Map<String, Integer> sourceOrder
    ) {
        String result = "";
        int best = Integer.MAX_VALUE;
        for (String id : ids) {
            int order = sourceOrder.getOrDefault(id, Integer.MAX_VALUE);
            if (order < best) {
                result = id;
                best = order;
            }
        }
        return result;
    }

    private static String firstMissingPerson(
        TreeState source,
        TreeState working,
        Map<String, Integer> sourceOrder
    ) {
        List<String> ids = new ArrayList<>(source.people.keySet());
        ids.sort(Comparator.comparingInt(id -> sourceOrder.getOrDefault(id, Integer.MAX_VALUE)));
        for (String id : ids) if (!working.people.containsKey(id)) return id;
        return "";
    }

    private static float maxRight(TreeState state) {
        float right = 0f;
        for (Person person : state.people.values()) right = Math.max(right, person.x + CARD_W);
        return right;
    }

    private static Map<String, Integer> relationshipDistances(
        String rootId,
        Relations relations,
        TreeState state
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (state.people.containsKey(rootId)) {
            result.put(rootId, 0);
            queue.add(rootId);
        }
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            int nextDistance = result.get(id) + 1;
            LinkedHashSet<String> neighbors = new LinkedHashSet<>();
            neighbors.addAll(relations.parentsByChild.getOrDefault(id, Collections.emptySet()));
            neighbors.addAll(relations.childrenByParent.getOrDefault(id, Collections.emptySet()));
            neighbors.addAll(relations.partnersByPerson.getOrDefault(id, Collections.emptySet()));
            neighbors.addAll(relations.siblingsByPerson.getOrDefault(id, Collections.emptySet()));
            for (String neighbor : neighbors) {
                if (!state.people.containsKey(neighbor) || result.containsKey(neighbor)) continue;
                result.put(neighbor, nextDistance);
                queue.addLast(neighbor);
            }
        }
        return result;
    }

    private static Map<String, Integer> relationshipGenerations(
        String rootId,
        Relations relations,
        TreeState state
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (state.people.containsKey(rootId)) {
            result.put(rootId, 0);
            queue.add(rootId);
        }
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            int generation = result.get(id);
            offerGeneration(
                state,
                result,
                queue,
                relations.partnersByPerson.getOrDefault(id, Collections.emptySet()),
                generation);
            offerGeneration(
                state,
                result,
                queue,
                relations.siblingsByPerson.getOrDefault(id, Collections.emptySet()),
                generation);
            offerGeneration(
                state,
                result,
                queue,
                relations.parentsByChild.getOrDefault(id, Collections.emptySet()),
                generation - 1);
            offerGeneration(
                state,
                result,
                queue,
                relations.childrenByParent.getOrDefault(id, Collections.emptySet()),
                generation + 1);
        }
        return result;
    }

    private static void offerGeneration(
        TreeState state,
        Map<String, Integer> generations,
        ArrayDeque<String> queue,
        Collection<String> ids,
        int generation
    ) {
        for (String id : ids) {
            if (!state.people.containsKey(id) || generations.containsKey(id)) continue;
            generations.put(id, generation);
            queue.addLast(id);
        }
    }

    static void layoutAfterAddition(TreeState state, Collection<String> addedIds) {
        layoutAfterAddition(state, addedIds, "", "");
    }

    static void layoutAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId,
        String action
    ) {
        layoutAfterAddition(state, addedIds, anchorId, action, false);
    }

    private static void layoutAfterAddition(
        TreeState state,
        Collection<String> addedIds,
        String anchorId,
        String action,
        boolean rebuildStep
    ) {
        Set<String> existingAddedIds = new LinkedHashSet<>();
        if (state != null && addedIds != null) {
            for (String id : addedIds) {
                if (state.people.containsKey(id)) existingAddedIds.add(id);
            }
        }
        if (state == null || existingAddedIds.isEmpty()) return;

        Map<String, Float> preservedRows = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            if (!existingAddedIds.contains(person.id)) preservedRows.put(person.id, person.y);
        }

        Relations relations = buildRelations(state);
        String localAction = normalizeLocalAction(action);
        if (localAction.isEmpty()) {
            localAction = inferLocalAction(existingAddedIds, relations);
        }
        String localAnchorId = state.people.containsKey(anchorId)
            ? anchorId
            : inferLocalAnchor(existingAddedIds, relations);

        alignAddedRow(state, existingAddedIds, localAnchorId, localAction);
        if ("children".equals(localAction) || "siblings".equals(localAction)) {
            boolean firstChildren = "children".equals(localAction)
                && relations.childrenByParent.getOrDefault(
                    localAnchorId,
                    Collections.emptySet()).size() <= existingAddedIds.size();
            if (firstChildren) {
                reflowContainingSiblingGroup(state, relations, localAnchorId);
            }
            arrangeSiblingFamily(
                state,
                relations,
                existingAddedIds,
                localAnchorId,
                "children".equals(localAction));
            if ("children".equals(localAction)) {
                reflowContainingSiblingGroup(state, relations, localAnchorId);
            } else {
                for (String partnerId : partnerRow(localAnchorId, relations, state)) {
                    if (!partnerId.equals(localAnchorId)) {
                        reflowContainingSiblingGroup(state, relations, partnerId);
                    }
                }
                reflowContainingSiblingGroup(state, relations, localAnchorId);
                reflowParentFamiliesForPartnerRow(state, relations, localAnchorId);
            }
        } else if ("parents".equals(localAction)) {
            Set<String> currentParents = relations.parentsByChild.getOrDefault(
                localAnchorId,
                Collections.emptySet());
            String existingParent = "";
            for (String parentId : currentParents) {
                if (!existingAddedIds.contains(parentId)) {
                    existingParent = parentId;
                    break;
                }
            }
            if (existingParent.isEmpty() || existingAddedIds.size() != 1) {
                arrangeParentBranch(state, relations, existingAddedIds, localAnchorId);
                centerLineageMemberUnderParents(state, relations, localAnchorId, currentParents);
                for (String partnerId : partnerRow(localAnchorId, relations, state)) {
                    reflowContainingSiblingGroup(state, relations, partnerId);
                }
                reflowContainingSiblingGroup(state, relations, localAnchorId);
            } else {
                arrangePartner(state, relations, existingAddedIds, existingParent);
                reflowChildrenOfFamily(state, relations, existingParent);
                reflowContainingSiblingGroup(state, relations, existingParent);
                reflowContainingSiblingGroup(state, relations, localAnchorId);
            }
            reflowLineageParentFamiliesTowardRoot(state, relations, localAnchorId);
        } else if ("partner".equals(localAction)) {
            arrangePartner(state, relations, existingAddedIds, localAnchorId);
            reflowChildrenOfFamily(state, relations, localAnchorId);
            reflowContainingSiblingGroup(state, relations, localAnchorId);
            keepAncestrySiblingGroupOutsidePartner(state, relations, localAnchorId);
        }
        for (Map.Entry<String, Float> entry : preservedRows.entrySet()) {
            Person person = state.people.get(entry.getKey());
            if (person != null) person.y = entry.getValue();
        }
        repairConsensusGenerationRows(state, relations);
        translateTreeInsideLeftBoundary(state);
        if (rebuildStep) {
            SmartLayoutSolver.improveRebuildStep(
                state,
                existingAddedIds,
                localAnchorId);
        } else if (state.autoArrangeOnAdd) {
            SmartLayoutSolver.improveAfterAddition(
                state,
                existingAddedIds,
                localAnchorId);
        }
        expandWorkspaceToFit(state);
    }

    /**
     * Repairs only an unambiguous, isolated generation-row error. A person is moved
     * when at least two independent close relatives agree on the same row and no
     * competing row has the same support. This keeps manually arranged valid rows
     * untouched, while preventing an accidentally dragged child from remaining on
     * the grandparents' row after the next automatic local layout.
     */
    private static void repairConsensusGenerationRows(TreeState state, Relations relations) {
        Map<String, Float> repairedRows = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            Map<Integer, Integer> votes = new LinkedHashMap<>();
            for (String parentId : relations.parentsByChild.getOrDefault(
                person.id,
                Collections.emptySet())) {
                Person parent = state.people.get(parentId);
                if (parent != null) addRowVote(votes, parent.y + LEVEL_GAP);
            }
            for (String childId : relations.childrenByParent.getOrDefault(
                person.id,
                Collections.emptySet())) {
                Person child = state.people.get(childId);
                if (child != null) addRowVote(votes, child.y - LEVEL_GAP);
            }
            for (String partnerId : relations.partnersByPerson.getOrDefault(
                person.id,
                Collections.emptySet())) {
                Person partner = state.people.get(partnerId);
                if (partner != null) addRowVote(votes, partner.y);
            }
            for (String siblingId : relations.siblingsByPerson.getOrDefault(
                person.id,
                Collections.emptySet())) {
                Person sibling = state.people.get(siblingId);
                if (sibling != null) addRowVote(votes, sibling.y);
            }

            int bestRow = 0;
            int bestVotes = 0;
            int secondVotes = 0;
            for (Map.Entry<Integer, Integer> vote : votes.entrySet()) {
                if (vote.getValue() > bestVotes) {
                    secondVotes = bestVotes;
                    bestVotes = vote.getValue();
                    bestRow = vote.getKey();
                } else if (vote.getValue() > secondVotes) {
                    secondVotes = vote.getValue();
                }
            }
            if (bestVotes >= 2 && bestVotes > secondVotes
                && Math.abs(person.y - bestRow * GRID) > GRID / 2f) {
                repairedRows.put(person.id, Math.max(0f, bestRow * GRID));
            }
        }
        for (Map.Entry<String, Float> repair : repairedRows.entrySet()) {
            Person person = state.people.get(repair.getKey());
            if (person != null) person.y = repair.getValue();
        }
    }

    private static void addRowVote(Map<Integer, Integer> votes, float row) {
        if (!Float.isFinite(row) || row < 0f) return;
        int rowIndex = Math.round(row / GRID);
        votes.put(rowIndex, votes.getOrDefault(rowIndex, 0) + 1);
    }

    private static String normalizeLocalAction(String action) {
        if (action == null) return "";
        String normalized = action.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("parent")) return "parents";
        if (normalized.contains("child") || normalized.contains("children")) return "children";
        if (normalized.contains("sibling")) return "siblings";
        if (normalized.contains("partner")) return "partner";
        return "";
    }

    private static String inferLocalAction(Set<String> addedIds, Relations relations) {
        for (String id : addedIds) {
            if (!relations.parentsByChild.getOrDefault(id, Collections.emptySet()).isEmpty()) {
                return "children";
            }
        }
        for (String id : addedIds) {
            if (!relations.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) {
                return "parents";
            }
        }
        for (String id : addedIds) {
            for (String partner : relations.partnersByPerson.getOrDefault(id, Collections.emptySet())) {
                if (!addedIds.contains(partner)) return "partner";
            }
            if (!relations.siblingsByPerson.getOrDefault(id, Collections.emptySet()).isEmpty()) {
                return "siblings";
            }
        }
        return "";
    }

    private static String inferLocalAnchor(Set<String> addedIds, Relations relations) {
        for (String id : addedIds) {
            Set<String> parents = relations.parentsByChild.getOrDefault(id, Collections.emptySet());
            if (!parents.isEmpty()) return parents.iterator().next();
            Set<String> children = relations.childrenByParent.getOrDefault(id, Collections.emptySet());
            if (!children.isEmpty()) return children.iterator().next();
            Set<String> partners = relations.partnersByPerson.getOrDefault(id, Collections.emptySet());
            for (String partner : partners) if (!addedIds.contains(partner)) return partner;
            Set<String> siblings = relations.siblingsByPerson.getOrDefault(id, Collections.emptySet());
            for (String sibling : siblings) if (!addedIds.contains(sibling)) return sibling;
        }
        return "";
    }

    private static void alignAddedRow(
        TreeState state,
        Set<String> addedIds,
        String anchorId,
        String action
    ) {
        Person anchor = state.people.get(anchorId);
        if (anchor == null || !Float.isFinite(anchor.y)) return;
        float targetY = anchor.y;
        if ("parents".equals(action)) targetY -= LEVEL_GAP;
        else if ("children".equals(action)) targetY += LEVEL_GAP;
        targetY = Math.max(0f, snap(targetY));
        for (String id : addedIds) {
            Person person = state.people.get(id);
            if (person != null) person.y = targetY;
        }
    }

    private static void arrangeSiblingFamily(
        TreeState state,
        Relations relations,
        Set<String> addedIds,
        String anchorId,
        boolean childAction
    ) {
        LinkedHashSet<String> siblingRoots = new LinkedHashSet<>();
        Set<String> familyParents = Collections.emptySet();
        for (String addedId : addedIds) {
            Set<String> parents = relations.parentsByChild.getOrDefault(
                addedId,
                Collections.emptySet());
            if (!parents.isEmpty()) {
                familyParents = new LinkedHashSet<>(parents);
                break;
            }
        }
        if (!familyParents.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
                if (entry.getValue().containsAll(familyParents)) siblingRoots.add(entry.getKey());
            }
        } else {
            if (state.people.containsKey(anchorId)) siblingRoots.add(anchorId);
            siblingRoots.addAll(addedIds);
            ArrayDeque<String> queue = new ArrayDeque<>(siblingRoots);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                for (String sibling : relations.siblingsByPerson.getOrDefault(
                    id,
                    Collections.emptySet())) {
                    if (siblingRoots.add(sibling)) queue.addLast(sibling);
                }
            }
        }
        siblingRoots.removeIf(id -> !state.people.containsKey(id));
        if (siblingRoots.isEmpty()) return;

        List<String> orderedRoots = new ArrayList<>(siblingRoots);
        orderedRoots.sort(Comparator.comparingDouble(id -> state.people.get(id).x));
        Set<String> rootStops = new HashSet<>(orderedRoots);
        Set<String> claimed = new HashSet<>();
        List<LocalBlock> blocks = new ArrayList<>();
        Map<String, Integer> addedOrder = new HashMap<>();
        int nextAddedOrder = 0;
        for (String id : addedIds) addedOrder.put(id, nextAddedOrder++);
        for (String rootId : orderedRoots) {
            Set<String> people = downwardBranch(rootId, rootStops, relations, state);
            people.removeAll(claimed);
            if (people.isEmpty()) people.add(rootId);
            claimed.addAll(people);
            blocks.add(new LocalBlock(
                rootId,
                people,
                partnerRow(rootId, relations, state),
                addedIds.contains(rootId),
                addedOrder.getOrDefault(rootId, Integer.MAX_VALUE),
                relations,
                state));
        }
        blocks.sort(Comparator.comparingDouble(block -> block.rootCenter));

        float anchor = familyAnchor(state, familyParents, anchorId, childAction);
        placeNewSiblingBlocks(state, blocks, anchorId, anchor, childAction);
        if (!childAction) {
            enforceAncestryOutsidePartner(state, relations, blocks, anchorId);
        }
        if (!familyParents.isEmpty()) {
            centerParentBranchOverChildren(state, relations, familyParents, blocks);
        }
    }

    private static Set<String> partnerRow(
        String rootId,
        Relations relations,
        TreeState state
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Person root = state.people.get(rootId);
        if (root == null) return result;
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            Person person = state.people.get(id);
            if (person == null || Math.abs(person.y - root.y) > GRID || !result.add(id)) continue;
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                queue.addLast(partner);
            }
        }
        return result;
    }

    private static Set<String> downwardBranch(
        String startId,
        Set<String> siblingStops,
        Relations relations,
        TreeState state
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!state.people.containsKey(id) || !result.add(id)) continue;
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                if (!siblingStops.contains(partner) || partner.equals(startId)) queue.addLast(partner);
            }
            for (String child : relations.childrenByParent.getOrDefault(
                id,
                Collections.emptySet())) {
                if (!siblingStops.contains(child) || child.equals(startId)) queue.addLast(child);
            }
        }
        return result;
    }

    private static float familyAnchor(
        TreeState state,
        Set<String> parentIds,
        String fallbackId,
        boolean childAction
    ) {
        float sum = 0f;
        int count = 0;
        for (String parentId : parentIds) {
            Person parent = state.people.get(parentId);
            if (parent == null) continue;
            sum += parent.x + CARD_W / 2f;
            count++;
        }
        if (count > 0) return sum / count;
        Person fallback = state.people.get(fallbackId);
        if (fallback != null) return fallback.x + CARD_W / 2f;
        if (childAction && !state.people.isEmpty()) {
            Person first = state.people.values().iterator().next();
            return first.x + CARD_W / 2f;
        }
        return surfaceWidth(state) / 2f;
    }

    private static void placeNewSiblingBlocks(
        TreeState state,
        List<LocalBlock> blocks,
        String anchorId,
        float anchor,
        boolean childAction
    ) {
        if (blocks.isEmpty()) return;
        int pivot = findSiblingPivot(blocks, anchorId, anchor, childAction);
        if (pivot < 0) {
            packLocalBlocks(state, blocks, anchor, !childAction);
            return;
        }
        if (!childAction && blocks.get(pivot).width() > CARD_W + GRID) {
            placeSiblingsOutsidePartner(state, blocks, pivot);
            return;
        }
        Set<String> moving = new LinkedHashSet<>();
        Set<String> localGroup = new LinkedHashSet<>();
        for (LocalBlock block : blocks) localGroup.addAll(block.people);
        for (LocalBlock block : blocks) if (block.added) moving.addAll(block.people);

        LocalBlock right = blocks.get(pivot);
        for (int i = pivot - 1; i >= 0; i--) {
            LocalBlock block = blocks.get(i);
            float desiredRight = right.left - localGap(block, right);
            if (block.added || block.right > desiredRight) {
                float delta = snap(desiredRight - block.right);
                shiftPeople(state, block.people, delta);
                block.shift(delta);
                moving.addAll(block.people);
            }
            right = block;
        }

        LocalBlock left = blocks.get(pivot);
        for (int i = pivot + 1; i < blocks.size(); i++) {
            LocalBlock block = blocks.get(i);
            float desiredLeft = left.right + localGap(left, block);
            if (block.added || block.left < desiredLeft) {
                float delta = snap(desiredLeft - block.left);
                shiftPeople(state, block.people, delta);
                block.shift(delta);
                moving.addAll(block.people);
            }
            left = block;
        }
        float commonShift = avoidStationaryCollisions(state, moving, localGroup);
        if (Math.abs(commonShift) >= 0.5f) {
            for (LocalBlock block : blocks) {
                if (moving.contains(block.rootId)) block.shift(commonShift);
            }
        }
    }

    private static void placeSiblingsOutsidePartner(
        TreeState state,
        List<LocalBlock> blocks,
        int pivot
    ) {
        LocalBlock anchor = blocks.get(pivot);
        boolean placeLeft = anchor.rootCenter <= (anchor.left + anchor.right) / 2f;
        List<LocalBlock> added = new ArrayList<>();
        for (LocalBlock block : blocks) if (block.added) added.add(block);
        added.sort(Comparator.comparingInt(block -> block.addedOrder));
        if (added.isEmpty()) return;

        Set<String> moving = new LinkedHashSet<>();
        Set<String> localGroup = new LinkedHashSet<>();
        for (LocalBlock block : blocks) localGroup.addAll(block.people);
        LocalBlock reference = anchor;
        if (placeLeft) {
            for (LocalBlock block : blocks) {
                if (!block.added && block.rootCenter < reference.rootCenter) reference = block;
            }
            float boundary = reference.left;
            for (int addedIndex = added.size() - 1; addedIndex >= 0; addedIndex--) {
                LocalBlock block = added.get(addedIndex);
                float desiredRight = boundary - localGap(block, reference);
                float delta = snap(desiredRight - block.right);
                shiftPeople(state, block.people, delta);
                block.shift(delta);
                moving.addAll(block.people);
                boundary = block.left;
                reference = block;
            }
        } else {
            for (LocalBlock block : blocks) {
                if (!block.added && block.rootCenter > reference.rootCenter) reference = block;
            }
            float boundary = reference.right;
            for (LocalBlock block : added) {
                float desiredLeft = boundary + localGap(reference, block);
                float delta = snap(desiredLeft - block.left);
                shiftPeople(state, block.people, delta);
                block.shift(delta);
                moving.addAll(block.people);
                boundary = block.right;
                reference = block;
            }
        }
        // Do not squeeze the new siblings back across their partner merely because
        // an adjacent ancestry branch occupies their first candidate positions.
        // enforceAncestryOutsidePartner moves the complete local ancestry group to
        // its genealogical side; the containing-group reflow then makes room in the
        // neighboring branches without destroying the sibling order.
    }

    private static void enforceAncestryOutsidePartner(
        TreeState state,
        Relations relations,
        List<LocalBlock> siblingBlocks,
        String anchorId
    ) {
        LocalBlock anchorBlock = null;
        for (LocalBlock block : siblingBlocks) {
            if (block.rootId.equals(anchorId)) {
                anchorBlock = block;
                break;
            }
        }
        if (anchorBlock == null
            || partnerRow(anchorId, relations, state).size() <= 1
            || !state.people.containsKey(state.rootId)) return;

        String lineageChildId = "";
        for (String childId : relations.childrenByParent.getOrDefault(
            anchorId,
            Collections.emptySet())) {
            Set<String> branch = downwardBranch(
                childId,
                Collections.emptySet(),
                relations,
                state);
            if (branch.contains(state.rootId)) {
                lineageChildId = childId;
                break;
            }
        }
        Person lineageChild = state.people.get(lineageChildId);
        if (lineageChild == null) return;
        String outsidePartnerId = "";
        for (String partnerId : relations.partnersByPerson.getOrDefault(
            lineageChildId,
            Collections.emptySet())) {
            outsidePartnerId = partnerId;
            break;
        }
        Person outsidePartner = state.people.get(outsidePartnerId);
        if (outsidePartner == null) return;

        boolean rightSide = lineageChild.x > outsidePartner.x;
        float extreme = rightSide ? Float.MAX_VALUE : -Float.MAX_VALUE;
        for (LocalBlock block : siblingBlocks) {
            Collection<String> inspected = block == anchorBlock
                ? partnerRow(anchorId, relations, state)
                : block.people;
            for (String id : inspected) {
                Person person = state.people.get(id);
                if (person == null) continue;
                extreme = rightSide
                    ? Math.min(extreme, person.x)
                    : Math.max(extreme, person.x);
            }
        }
        if (!Float.isFinite(extreme)) return;
        float delta = rightSide
            ? Math.max(0f, lineageChild.x - extreme)
            : Math.min(0f, lineageChild.x - extreme);
        delta = snap(delta);
        if (Math.abs(delta) < 0.5f) return;

        for (LocalBlock block : siblingBlocks) {
            Collection<String> moving = block == anchorBlock
                ? partnerRow(anchorId, relations, state)
                : block.people;
            shiftPeople(state, moving, delta);
            block.shift(delta);
        }
        alignOuterChildBranches(
            state,
            relations,
            anchorId,
            lineageChildId,
            rightSide);
    }

    private static void alignOuterChildBranches(
        TreeState state,
        Relations relations,
        String parentId,
        String lineageChildId,
        boolean rightSide
    ) {
        LinkedHashSet<String> childRoots = new LinkedHashSet<>(
            relations.childrenByParent.getOrDefault(parentId, Collections.emptySet()));
        childRoots.remove(lineageChildId);
        childRoots.removeIf(id -> !state.people.containsKey(id));
        if (childRoots.isEmpty()) return;

        Set<String> stops = new HashSet<>(childRoots);
        stops.add(lineageChildId);
        List<LocalBlock> blocks = new ArrayList<>();
        for (String childId : childRoots) {
            Set<String> branch = downwardBranch(childId, stops, relations, state);
            blocks.add(new LocalBlock(
                childId,
                branch,
                partnerRow(childId, relations, state),
                false,
                Integer.MAX_VALUE,
                relations,
                state));
        }
        blocks.sort(Comparator.comparingDouble(block -> block.rootCenter));
        if (!rightSide) Collections.reverse(blocks);

        Set<String> parentRow = partnerRow(parentId, relations, state);
        float parentCenter = 0f;
        int parentCount = 0;
        for (String id : parentRow) {
            Person person = state.people.get(id);
            if (person == null) continue;
            parentCenter += person.x + CARD_W / 2f;
            parentCount++;
        }
        if (parentCount == 0) return;
        parentCenter /= parentCount;

        LocalBlock previous = null;
        for (LocalBlock block : blocks) {
            float delta;
            if (previous == null) {
                delta = snap(parentCenter - block.rootCenter);
                if (rightSide) delta = Math.max(0f, delta);
                else delta = Math.min(0f, delta);
            } else if (rightSide) {
                float rowDelta = previous.right + localGap(previous, block) - block.left;
                float branchDelta = rightBranchClearanceDelta(
                    state,
                    previous.people,
                    block.people,
                    localGap(previous, block));
                delta = snap(Math.max(0f, Math.max(rowDelta, branchDelta)));
            } else {
                float rowDelta = previous.left - localGap(block, previous) - block.right;
                float branchDelta = leftBranchClearanceDelta(
                    state,
                    block.people,
                    previous.people,
                    localGap(block, previous));
                delta = snap(Math.min(0f, Math.min(rowDelta, branchDelta)));
            }
            shiftPeople(state, block.people, delta);
            block.shift(delta);
            previous = block;
        }
    }

    private static int findSiblingPivot(
        List<LocalBlock> blocks,
        String anchorId,
        float anchor,
        boolean childAction
    ) {
        if (!childAction) {
            for (int i = 0; i < blocks.size(); i++) {
                if (!blocks.get(i).added && blocks.get(i).rootId.equals(anchorId)) return i;
            }
        }
        int best = -1;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < blocks.size(); i++) {
            LocalBlock block = blocks.get(i);
            if (block.added) continue;
            float next = Math.abs(block.rootCenter - anchor);
            if (next < distance) {
                best = i;
                distance = next;
            }
        }
        return best;
    }

    private static void packLocalBlocks(TreeState state, List<LocalBlock> blocks, float anchor) {
        packLocalBlocks(state, blocks, anchor, true);
    }

    private static void packLocalBlocks(
        TreeState state,
        List<LocalBlock> blocks,
        float anchor,
        boolean avoidExternalCollisions
    ) {
        if (blocks.isEmpty()) return;
        float totalWidth = 0f;
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) totalWidth += localGap(blocks.get(i - 1), blocks.get(i));
            totalWidth += blocks.get(i).width();
        }
        float cursor = snap(anchor - totalWidth / 2f);
        Set<String> moving = new LinkedHashSet<>();
        for (LocalBlock block : blocks) moving.addAll(block.people);
        for (int i = 0; i < blocks.size(); i++) {
            LocalBlock block = blocks.get(i);
            if (i > 0) cursor += localGap(blocks.get(i - 1), block);
            float delta = snap(cursor - block.left);
            shiftPeople(state, block.people, delta);
            block.shift(delta);
            cursor = block.right;
        }
        float commonShift = avoidExternalCollisions
            ? avoidStationaryCollisions(state, moving)
            : 0f;
        if (Math.abs(commonShift) >= 0.5f) {
            for (LocalBlock block : blocks) block.shift(commonShift);
        }
    }

    private static void reflowChildrenOfFamily(
        TreeState state,
        Relations relations,
        String parentId
    ) {
        if (!state.people.containsKey(parentId)) return;
        LinkedHashSet<String> childRoots = new LinkedHashSet<>(
            relations.childrenByParent.getOrDefault(parentId, Collections.emptySet()));
        childRoots.removeIf(id -> !state.people.containsKey(id));
        if (childRoots.isEmpty()) return;

        List<String> orderedRoots = new ArrayList<>(childRoots);
        orderedRoots.sort(Comparator.comparingDouble(id -> state.people.get(id).x));
        Set<String> stops = new HashSet<>(orderedRoots);
        Set<String> claimed = new HashSet<>();
        List<LocalBlock> blocks = new ArrayList<>();
        for (String rootId : orderedRoots) {
            Set<String> branch = downwardBranch(rootId, stops, relations, state);
            branch.removeAll(claimed);
            if (branch.isEmpty()) branch.add(rootId);
            claimed.addAll(branch);
            blocks.add(new LocalBlock(
                rootId,
                branch,
                partnerRow(rootId, relations, state),
                false,
                Integer.MAX_VALUE,
                relations,
                state));
        }

        LinkedHashSet<String> parentFamily = new LinkedHashSet<>();
        parentFamily.add(parentId);
        for (String partnerId : relations.partnersByPerson.getOrDefault(
            parentId,
            Collections.emptySet())) {
            Set<String> partnerChildren = relations.childrenByParent.getOrDefault(
                partnerId,
                Collections.emptySet());
            if (!Collections.disjoint(childRoots, partnerChildren)) parentFamily.add(partnerId);
        }
        float anchor = familyAnchor(state, parentFamily, parentId, false);
        packLocalBlocks(state, blocks, anchor, false);
    }

    private static void centerLineageMemberUnderParents(
        TreeState state,
        Relations relations,
        String childId,
        Collection<String> parentIds
    ) {
        Person child = state.people.get(childId);
        if (child == null || parentIds == null || parentIds.isEmpty()) return;
        float parentCenter = 0f;
        int parentCount = 0;
        for (String parentId : parentIds) {
            Person parent = state.people.get(parentId);
            if (parent == null) continue;
            parentCenter += parent.x + CARD_W / 2f;
            parentCount++;
        }
        if (parentCount == 0) return;
        parentCenter /= parentCount;
        float delta = snap(parentCenter - (child.x + CARD_W / 2f));
        if (Math.abs(delta) < 0.5f) return;

        Set<String> partnerFamily = partnerRow(childId, relations, state);
        if (partnerFamily.contains(state.rootId)) return;
        LinkedHashSet<String> moving = new LinkedHashSet<>(partnerFamily);
        LinkedHashSet<String> childRoots = new LinkedHashSet<>();
        for (String familyMember : partnerFamily) {
            childRoots.addAll(relations.childrenByParent.getOrDefault(
                familyMember,
                Collections.emptySet()));
        }
        childRoots.removeIf(id -> !state.people.containsKey(id));
        Set<String> stops = new HashSet<>(childRoots);
        boolean keepsMainTrunkFixed = false;
        for (String childRoot : childRoots) {
            Set<String> branch = downwardBranch(childRoot, stops, relations, state);
            // Keep the main trunk and camera anchor fixed. Only the side family follows
            // the lineage couple when the couple has to move under its actual parents.
            if (branch.contains(state.rootId)) keepsMainTrunkFixed = true;
            else moving.addAll(branch);
        }
        if (!keepsMainTrunkFixed) return;
        if (!hasCollisionWithStationary(state, moving, moving, delta)) {
            shiftPeople(state, moving, delta);
        }
    }

    private static void centerParentBranchOverChildren(
        TreeState state,
        Relations relations,
        Set<String> parentIds,
        List<LocalBlock> children
    ) {
        if (parentIds.isEmpty() || children.isEmpty()) return;
        float minChildCenter = Float.MAX_VALUE;
        float maxChildCenter = -Float.MAX_VALUE;
        for (LocalBlock child : children) {
            minChildCenter = Math.min(minChildCenter, child.rootCenter);
            maxChildCenter = Math.max(maxChildCenter, child.rootCenter);
        }
        float target = (minChildCenter + maxChildCenter) / 2f;
        float current = 0f;
        int parentCount = 0;
        for (String parentId : parentIds) {
            Person parent = state.people.get(parentId);
            if (parent == null) continue;
            current += parent.x + CARD_W / 2f;
            parentCount++;
        }
        if (parentCount == 0) return;
        current /= parentCount;
        float delta = snap(target - current);
        if (Math.abs(delta) < 0.5f) return;

        LinkedHashSet<String> branch = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(parentIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!state.people.containsKey(id) || !branch.add(id)) continue;
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                queue.addLast(partner);
            }
            for (String parent : relations.parentsByChild.getOrDefault(
                id,
                Collections.emptySet())) {
                queue.addLast(parent);
            }
        }
        shiftPeople(state, branch, delta);
        avoidStationaryCollisions(state, branch);
    }

    private static float localGap(LocalBlock first, LocalBlock second) {
        return first.simple && second.simple ? SIBLING_GAP : BRANCH_GAP;
    }

    private static void reflowContainingSiblingGroup(
        TreeState state,
        Relations relations,
        String memberId
    ) {
        if (!state.people.containsKey(memberId)) return;
        Set<String> parentIds = new LinkedHashSet<>(relations.parentsByChild.getOrDefault(
            memberId,
            Collections.emptySet()));
        LinkedHashSet<String> siblingRoots = new LinkedHashSet<>();
        if (!parentIds.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
                if (entry.getValue().containsAll(parentIds)) siblingRoots.add(entry.getKey());
            }
        } else {
            siblingRoots.add(memberId);
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(memberId);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                for (String sibling : relations.siblingsByPerson.getOrDefault(
                    id,
                    Collections.emptySet())) {
                    if (siblingRoots.add(sibling)) queue.addLast(sibling);
                }
            }
        }
        siblingRoots.removeIf(id -> !state.people.containsKey(id));
        if (siblingRoots.size() <= 1) return;

        List<String> orderedRoots = new ArrayList<>(siblingRoots);
        orderedRoots.sort(Comparator.comparingDouble(id -> state.people.get(id).x));
        Set<String> stops = new HashSet<>(orderedRoots);
        Set<String> claimed = new HashSet<>();
        List<LocalBlock> blocks = new ArrayList<>();
        for (String rootId : orderedRoots) {
            Set<String> branch = downwardBranch(rootId, stops, relations, state);
            branch.removeAll(claimed);
            if (branch.isEmpty()) branch.add(rootId);
            claimed.addAll(branch);
            blocks.add(new LocalBlock(
                rootId,
                branch,
                partnerRow(rootId, relations, state),
                false,
                Integer.MAX_VALUE,
                relations,
                state));
        }
        blocks.sort(Comparator.comparingDouble(block -> block.rootCenter));
        float anchor = familyAnchor(state, parentIds, memberId, false);
        if (parentIds.isEmpty()) {
            for (LocalBlock block : blocks) {
                if (block.people.contains(state.rootId)) {
                    anchor = block.rootCenter;
                    break;
                }
            }
        }
        String stableRootId = memberId;
        if (!canReorderChangedBlock(blocks, memberId, anchor)) {
            for (LocalBlock block : blocks) {
                if (block.people.contains(state.rootId)) {
                    stableRootId = block.rootId;
                    break;
                }
            }
        }
        reflowExistingSiblingBlocks(state, blocks, stableRootId, anchor);
        if (!parentIds.isEmpty()) {
            centerParentBranchOverChildren(state, relations, parentIds, blocks);
        }
    }

    /**
     * Keeps a collateral ancestry family on the same side as the lineage member.
     *
     * <p>When a partner is added to an aunt/uncle in an ancestor generation, the
     * new two-card row can grow back across the opposite parent below it. Moving
     * the complete sibling branch would also drag the root and the whole visible
     * tree. Instead, move the collateral families and only the lineage card/partner
     * row, then re-center their common parents. Descendants on the main trunk stay
     * fixed.</p>
     */
    private static void keepAncestrySiblingGroupOutsidePartner(
        TreeState state,
        Relations relations,
        String changedSiblingId
    ) {
        Set<String> parentIds = new LinkedHashSet<>(relations.parentsByChild.getOrDefault(
            changedSiblingId,
            Collections.emptySet()));
        if (parentIds.isEmpty() || !state.people.containsKey(state.rootId)) return;

        LinkedHashSet<String> siblingRoots = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
            if (entry.getValue().containsAll(parentIds)) siblingRoots.add(entry.getKey());
        }
        siblingRoots.removeIf(id -> !state.people.containsKey(id));
        if (siblingRoots.size() <= 1) return;

        Set<String> stops = new HashSet<>(siblingRoots);
        List<LocalBlock> blocks = new ArrayList<>();
        LocalBlock lineageBlock = null;
        for (String siblingId : siblingRoots) {
            Set<String> branch = downwardBranch(siblingId, stops, relations, state);
            LocalBlock block = new LocalBlock(
                siblingId,
                branch,
                partnerRow(siblingId, relations, state),
                false,
                Integer.MAX_VALUE,
                relations,
                state);
            blocks.add(block);
            if (branch.contains(state.rootId)) lineageBlock = block;
        }
        if (lineageBlock == null || lineageBlock.rootId.equals(changedSiblingId)) return;

        String lineageChildId = "";
        for (String childId : relations.childrenByParent.getOrDefault(
            lineageBlock.rootId,
            Collections.emptySet())) {
            if (downwardBranch(childId, Collections.emptySet(), relations, state)
                .contains(state.rootId)) {
                lineageChildId = childId;
                break;
            }
        }
        Person lineageChild = state.people.get(lineageChildId);
        if (lineageChild == null) return;

        Person oppositePartner = null;
        for (String partnerId : relations.partnersByPerson.getOrDefault(
            lineageChildId,
            Collections.emptySet())) {
            Person candidate = state.people.get(partnerId);
            if (candidate != null) {
                oppositePartner = candidate;
                break;
            }
        }
        if (oppositePartner == null) return;

        boolean rightSide = lineageChild.x > oppositePartner.x;
        float extreme = rightSide ? Float.MAX_VALUE : -Float.MAX_VALUE;
        for (LocalBlock block : blocks) {
            for (String rowId : partnerRow(block.rootId, relations, state)) {
                Person person = state.people.get(rowId);
                if (person == null) continue;
                extreme = rightSide
                    ? Math.min(extreme, person.x)
                    : Math.max(extreme, person.x);
            }
        }
        if (!Float.isFinite(extreme)) return;
        float boundary = lineageChild.x
            + (rightSide ? SIBLING_FAMILY_GAP : -SIBLING_FAMILY_GAP);
        float delta = rightSide
            ? Math.max(0f, boundary - extreme)
            : Math.min(0f, boundary - extreme);
        delta = snap(delta);
        if (Math.abs(delta) < 0.5f) return;

        LinkedHashSet<String> moving = new LinkedHashSet<>();
        for (LocalBlock block : blocks) {
            if (block == lineageBlock) moving.addAll(partnerRow(block.rootId, relations, state));
            else moving.addAll(block.people);
            block.shift(delta);
        }
        shiftPeople(state, moving, delta);
        centerParentBranchOverChildren(state, relations, parentIds, blocks);
    }

    private static boolean canReorderChangedBlock(
        List<LocalBlock> blocks,
        String changedRootId,
        float anchor
    ) {
        int changedIndex = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).rootId.equals(changedRootId)) {
                changedIndex = i;
                break;
            }
        }
        if (changedIndex < 0) return false;
        LocalBlock changed = blocks.get(changedIndex);
        if (changed.simple) return false;
        if (changed.rootCenter > anchor + GRID / 2f) {
            return changedIndex + 1 < blocks.size() && blocks.get(changedIndex + 1).simple;
        }
        if (changed.rootCenter < anchor - GRID / 2f) {
            return changedIndex > 0 && blocks.get(changedIndex - 1).simple;
        }
        return false;
    }

    private static void reflowExistingSiblingBlocks(
        TreeState state,
        List<LocalBlock> blocks,
        String changedRootId,
        float anchor
    ) {
        int pivot = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).rootId.equals(changedRootId)) {
                pivot = i;
                break;
            }
        }
        if (pivot < 0) return;

        LocalBlock changed = blocks.get(pivot);
        boolean reordered = false;
        if (!changed.simple && changed.rootCenter > anchor + GRID / 2f) {
            int lastSimple = pivot;
            while (lastSimple + 1 < blocks.size() && blocks.get(lastSimple + 1).simple) {
                lastSimple++;
            }
            if (lastSimple > pivot) {
                blocks.remove(pivot);
                blocks.add(lastSimple, changed);
                int stable = pivot - 1;
                LocalBlock left = stable >= 0 ? blocks.get(stable) : null;
                for (int i = pivot; i <= lastSimple; i++) {
                    LocalBlock block = blocks.get(i);
                    float desiredLeft = left == null
                        ? block.left
                        : left.right + localGap(left, block);
                    float delta = snap(desiredLeft - block.left);
                    shiftPeople(state, block.people, delta);
                    block.shift(delta);
                    left = block;
                }
                reordered = true;
            }
        } else if (!changed.simple && changed.rootCenter < anchor - GRID / 2f) {
            int firstSimple = pivot;
            while (firstSimple - 1 >= 0 && blocks.get(firstSimple - 1).simple) {
                firstSimple--;
            }
            if (firstSimple < pivot) {
                blocks.remove(pivot);
                blocks.add(firstSimple, changed);
                int stable = pivot + 1;
                LocalBlock right = stable < blocks.size() ? blocks.get(stable) : null;
                for (int i = pivot; i >= firstSimple; i--) {
                    LocalBlock block = blocks.get(i);
                    float desiredRight = right == null
                        ? block.right
                        : right.left - localGap(block, right);
                    float delta = snap(desiredRight - block.right);
                    shiftPeople(state, block.people, delta);
                    block.shift(delta);
                    right = block;
                }
                reordered = true;
            }
        }

        if (!reordered) {
            pivot = blocks.indexOf(changed);
            LocalBlock right = changed;
            for (int i = pivot - 1; i >= 0; i--) {
                LocalBlock block = blocks.get(i);
                float desiredRight = right.left - localGap(block, right);
                float rowDelta = desiredRight - block.right;
                float branchDelta = leftBranchClearanceDelta(
                    state,
                    block.people,
                    right.people,
                    localGap(block, right));
                float requiredDelta = Math.min(rowDelta, branchDelta);
                if (Math.abs(requiredDelta) > 0.5f) {
                    float delta = snap(requiredDelta);
                    shiftPeople(state, block.people, delta);
                    block.shift(delta);
                }
                right = block;
            }
            LocalBlock left = changed;
            for (int i = pivot + 1; i < blocks.size(); i++) {
                LocalBlock block = blocks.get(i);
                float desiredLeft = left.right + localGap(left, block);
                float rowDelta = desiredLeft - block.left;
                float branchDelta = rightBranchClearanceDelta(
                    state,
                    left.people,
                    block.people,
                    localGap(left, block));
                float requiredDelta = Math.max(rowDelta, branchDelta);
                if (Math.abs(requiredDelta) > 0.5f) {
                    float delta = snap(requiredDelta);
                    shiftPeople(state, block.people, delta);
                    block.shift(delta);
                }
                left = block;
            }
        }

        Set<String> localGroup = new LinkedHashSet<>();
        boolean crossesLeftBoundary = false;
        for (LocalBlock block : blocks) {
            localGroup.addAll(block.people);
            if (block.left < 0f) crossesLeftBoundary = true;
        }
        // A centered group still needs the ordinary collision correction used by the
        // local-layout tests. At the left workspace edge, however, every negative trial
        // is rejected and the old search could only jump the complete root branch past
        // an unrelated family. In that one case the final normalization below performs
        // only the smallest common translation needed to bring x back to zero.
        if (!crossesLeftBoundary) {
            float commonShift = avoidStationaryCollisions(state, localGroup);
            if (Math.abs(commonShift) >= 0.5f) {
                for (LocalBlock block : blocks) block.shift(commonShift);
            }
        }
    }

    private static float leftBranchClearanceDelta(
        TreeState state,
        Collection<String> leftIds,
        Collection<String> rightIds,
        float gap
    ) {
        float delta = Float.MAX_VALUE;
        for (String leftId : leftIds) {
            Person left = state.people.get(leftId);
            if (left == null) continue;
            for (String rightId : rightIds) {
                Person right = state.people.get(rightId);
                if (right == null || !rowsOverlap(left, right)) continue;
                delta = Math.min(
                    delta,
                    right.x - CARD_W - gap - left.x);
            }
        }
        return delta == Float.MAX_VALUE ? 0f : delta;
    }

    private static float rightBranchClearanceDelta(
        TreeState state,
        Collection<String> leftIds,
        Collection<String> rightIds,
        float gap
    ) {
        float delta = -Float.MAX_VALUE;
        for (String leftId : leftIds) {
            Person left = state.people.get(leftId);
            if (left == null) continue;
            for (String rightId : rightIds) {
                Person right = state.people.get(rightId);
                if (right == null || !rowsOverlap(left, right)) continue;
                delta = Math.max(
                    delta,
                    left.x + CARD_W + gap - right.x);
            }
        }
        return delta == -Float.MAX_VALUE ? 0f : delta;
    }

    private static boolean rowsOverlap(Person first, Person second) {
        return first.y < second.y + CARD_H + GRID
            && second.y < first.y + CARD_H + GRID;
    }

    private static void arrangeParentBranch(
        TreeState state,
        Relations relations,
        Set<String> addedIds,
        String childId
    ) {
        Person child = state.people.get(childId);
        if (child == null) return;
        LinkedHashSet<String> parentFamily = new LinkedHashSet<>(addedIds);
        ArrayDeque<String> queue = new ArrayDeque<>(addedIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                if (state.people.containsKey(partner) && parentFamily.add(partner)) {
                    queue.addLast(partner);
                }
            }
        }
        List<String> ordered = new ArrayList<>(parentFamily);
        ordered.sort(Comparator.comparingDouble(id -> state.people.get(id).x));
        float width = ordered.size() * CARD_W + Math.max(0, ordered.size() - 1) * PARTNER_GAP;
        float cursor = snap(child.x + CARD_W / 2f - width / 2f);
        Set<String> moved = new LinkedHashSet<>();
        Set<String> stops = new HashSet<>(parentFamily);
        for (String parentId : ordered) {
            Person parent = state.people.get(parentId);
            Set<String> branch = upwardBranch(parentId, stops, relations, state);
            branch.removeAll(moved);
            float delta = snap(cursor - parent.x);
            shiftPeople(state, branch, delta);
            moved.addAll(branch);
            cursor += CARD_W + PARTNER_GAP;
        }
        avoidStationaryCollisions(state, moved);
        reflowParentFamiliesForPartnerRow(state, relations, childId);
    }

    private static Set<String> upwardBranch(
        String startId,
        Set<String> familyStops,
        Relations relations,
        TreeState state
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!state.people.containsKey(id) || !result.add(id)) continue;
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                if (!familyStops.contains(partner) || partner.equals(startId)) queue.addLast(partner);
            }
            for (String parent : relations.parentsByChild.getOrDefault(
                id,
                Collections.emptySet())) {
                if (!familyStops.contains(parent) || parent.equals(startId)) queue.addLast(parent);
            }
        }
        return result;
    }

    private static void reflowParentFamiliesForPartnerRow(
        TreeState state,
        Relations relations,
        String childId
    ) {
        Set<String> lowerRow = partnerRow(childId, relations, state);
        if (lowerRow.size() <= 1) return;
        Map<String, ParentFamilyBlock> groups = new LinkedHashMap<>();
        for (String lowerId : lowerRow) {
            Set<String> parents = relations.parentsByChild.getOrDefault(
                lowerId,
                Collections.emptySet());
            if (parents.isEmpty()) continue;
            List<String> sortedParents = new ArrayList<>(parents);
            Collections.sort(sortedParents);
            String key = String.join("|", sortedParents);
            ParentFamilyBlock block = groups.get(key);
            if (block == null) {
                block = new ParentFamilyBlock(sortedParents, state);
                groups.put(key, block);
            }
        }
        if (groups.size() <= 1) return;

        List<ParentFamilyBlock> blocks = new ArrayList<>(groups.values());
        for (ParentFamilyBlock block : blocks) {
            Set<String> parentSet = new HashSet<>(block.parents);
            for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
                if (!entry.getValue().containsAll(parentSet)) continue;
                Person child = state.people.get(entry.getKey());
                if (child != null) block.childCenters.add(child.x + CARD_W / 2f);
            }
        }
        blocks.sort(Comparator.comparingDouble(ParentFamilyBlock::childCenter));
        Set<String> allMoving = new LinkedHashSet<>();
        for (ParentFamilyBlock block : blocks) {
            block.branch.addAll(upwardFamilyBranch(block.parents, relations, state));
            allMoving.addAll(block.branch);
            float idealLeft = snap(block.childCenter() - block.width() / 2f);
            float delta = snap(idealLeft - block.left);
            shiftPeople(state, block.branch, delta);
            block.shift(delta);
        }
        boolean mirroredPair = blocks.size() == 2
            && haveMatchingBranchContours(state, blocks.get(0), blocks.get(1));
        for (int boundary = 0; boundary + 1 < blocks.size(); boundary++) {
            ParentFamilyBlock left = blocks.get(boundary);
            ParentFamilyBlock right = blocks.get(boundary + 1);
            float deficit = mirroredPair
                ? rightBranchClearanceDelta(
                    state,
                    left.branch,
                    right.branch,
                    BRANCH_GAP)
                : left.right + BRANCH_GAP - right.left;
            if (deficit <= 0f) continue;
            int steps = Math.max(1, Math.round(deficit / GRID));
            float moveLeft = ((steps + 1) / 2) * GRID;
            float moveRight = (steps / 2) * GRID;
            for (int i = 0; i <= boundary; i++) {
                ParentFamilyBlock block = blocks.get(i);
                shiftPeople(state, block.branch, -moveLeft);
                block.shift(-moveLeft);
            }
            for (int i = boundary + 1; i < blocks.size(); i++) {
                ParentFamilyBlock block = blocks.get(i);
                shiftPeople(state, block.branch, moveRight);
                block.shift(moveRight);
            }
        }
        // Resolve a collision for the family that actually has it. Treating all
        // parent families as one rigid moving block made the right family hitting
        // an ancestor branch drag an unrelated far-left family with it, reopening
        // a gap that had just been compacted.
        for (ParentFamilyBlock block : blocks) {
            float correction = avoidStationaryCollisions(state, block.branch, allMoving);
            block.shift(correction);
        }
    }

    private static void reflowLineageParentFamiliesTowardRoot(
        TreeState state,
        Relations relations,
        String startId
    ) {
        String currentId = startId;
        Set<String> visited = new HashSet<>();
        while (state.people.containsKey(currentId) && visited.add(currentId)) {
            reflowParentFamiliesForPartnerRow(state, relations, currentId);
            if (partnerRow(currentId, relations, state).contains(state.rootId)) return;

            String nextId = "";
            for (String rowId : partnerRow(currentId, relations, state)) {
                for (String childId : relations.childrenByParent.getOrDefault(
                    rowId,
                    Collections.emptySet())) {
                    if (downwardBranch(childId, Collections.emptySet(), relations, state)
                        .contains(state.rootId)) {
                        nextId = childId;
                        break;
                    }
                }
                if (!nextId.isEmpty()) break;
            }
            if (nextId.isEmpty()) return;
            currentId = nextId;
        }
    }

    private static boolean haveMatchingBranchContours(
        TreeState state,
        ParentFamilyBlock first,
        ParentFamilyBlock second
    ) {
        return first.childCenters.size() == second.childCenters.size()
            && branchContour(state, first.branch).equals(branchContour(state, second.branch));
    }

    private static List<String> branchContour(TreeState state, Collection<String> ids) {
        Map<Integer, List<Person>> rows = new TreeMap<>();
        int bottomRow = Integer.MIN_VALUE;
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person == null) continue;
            int row = Math.round(person.y / GRID);
            rows.computeIfAbsent(row, ignored -> new ArrayList<>()).add(person);
            bottomRow = Math.max(bottomRow, row);
        }
        List<String> contour = new ArrayList<>();
        if (bottomRow == Integer.MIN_VALUE) return contour;
        for (Map.Entry<Integer, List<Person>> entry : rows.entrySet()) {
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            for (Person person : entry.getValue()) {
                left = Math.min(left, person.x);
                right = Math.max(right, person.x + CARD_W);
            }
            contour.add(
                (entry.getKey() - bottomRow) + ":" + entry.getValue().size() + ":"
                    + Math.round((right - left) / GRID));
        }
        return contour;
    }

    private static Set<String> upwardFamilyBranch(
        Collection<String> startIds,
        Relations relations,
        TreeState state
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(startIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!state.people.containsKey(id) || !result.add(id)) continue;
            for (String partner : relations.partnersByPerson.getOrDefault(
                id,
                Collections.emptySet())) {
                queue.addLast(partner);
            }
            for (String parent : relations.parentsByChild.getOrDefault(
                id,
                Collections.emptySet())) {
                queue.addLast(parent);
            }
        }
        return result;
    }

    private static void arrangePartner(
        TreeState state,
        Relations relations,
        Set<String> addedIds,
        String anchorId
    ) {
        Person anchor = state.people.get(anchorId);
        if (anchor == null) return;
        boolean siblingRowCanReflow = hasContainingSiblings(anchorId, relations);
        float cursor = anchor.x - CARD_W - PARTNER_GAP;
        Set<String> existingPartners = new LinkedHashSet<>(
            relations.partnersByPerson.getOrDefault(anchorId, Collections.emptySet()));
        existingPartners.removeAll(addedIds);
        List<String> ordered = new ArrayList<>(addedIds);
        ordered.sort(Comparator.comparingDouble(id -> state.people.get(id).x));
        Collections.reverse(ordered);
        Set<String> ignore = new HashSet<>(addedIds);
        for (String id : ordered) {
            Person added = state.people.get(id);
            if (added == null) continue;
            float target = existingPartners.isEmpty()
                ? snap(cursor)
                : nearestOpenPartnerSlot(
                    state,
                    anchorId,
                    id,
                    existingPartners,
                    ignore);
            if (Float.isFinite(target)
                && (siblingRowCanReflow
                    || cardSpotIsOpen(state, id, target, anchor.y, ignore)
                    || !existingPartners.isEmpty())) {
                added.x = target;
                added.y = anchor.y;
            }
            ignore.remove(id);
            existingPartners.add(id);
            cursor -= CARD_W + PARTNER_GAP;
        }
    }

    /** Places additional partners around their shared person instead of stacking them. */
    private static float nearestOpenPartnerSlot(
        TreeState state,
        String anchorId,
        String movingId,
        Collection<String> existingPartners,
        Set<String> ignoredIds
    ) {
        Person anchor = state.people.get(anchorId);
        if (anchor == null) return Float.NaN;
        int leftCount = 0;
        int rightCount = 0;
        for (String partnerId : existingPartners) {
            Person partner = state.people.get(partnerId);
            if (partner == null) continue;
            if (partner.x < anchor.x) leftCount++;
            else rightCount++;
        }
        boolean preferLeft = leftCount <= rightCount;
        int limit = Math.max(4, state.people.size() + 1);
        for (int distance = 1; distance <= limit; distance++) {
            float offset = distance * (CARD_W + PARTNER_GAP);
            float first = snap(anchor.x + (preferLeft ? -offset : offset));
            float second = snap(anchor.x + (preferLeft ? offset : -offset));
            if (cardSpotIsOpen(state, movingId, first, anchor.y, ignoredIds)) return first;
            if (cardSpotIsOpen(state, movingId, second, anchor.y, ignoredIds)) return second;
        }
        return Float.NaN;
    }

    private static boolean hasContainingSiblings(String personId, Relations relations) {
        if (!relations.siblingsByPerson.getOrDefault(
            personId,
            Collections.emptySet()).isEmpty()) return true;
        Set<String> parents = relations.parentsByChild.getOrDefault(
            personId,
            Collections.emptySet());
        if (parents.isEmpty()) return false;
        int count = 0;
        for (Set<String> candidateParents : relations.parentsByChild.values()) {
            if (candidateParents.containsAll(parents) && ++count > 1) return true;
        }
        return false;
    }

    private static boolean cardSpotIsOpen(
        TreeState state,
        String movingId,
        float x,
        float y,
        Set<String> ignoredIds
    ) {
        for (Person other : state.people.values()) {
            if (other.id.equals(movingId) || ignoredIds.contains(other.id)) continue;
            if (cardsCollide(x, y, other.x, other.y)) return false;
        }
        return true;
    }

    private static float avoidStationaryCollisions(TreeState state, Set<String> movingIds) {
        return avoidStationaryCollisions(state, movingIds, movingIds);
    }

    private static float avoidStationaryCollisions(
        TreeState state,
        Set<String> movingIds,
        Set<String> ignoredStationaryIds
    ) {
        if (movingIds.isEmpty()
            || !hasCollisionWithStationary(state, movingIds, ignoredStationaryIds, 0f)) return 0f;
        float limit = Math.max(surfaceWidth(state), 8000f);
        for (float distance = GRID; distance <= limit; distance += GRID) {
            if (!hasCollisionWithStationary(
                state,
                movingIds,
                ignoredStationaryIds,
                -distance)) {
                shiftPeople(state, movingIds, -distance);
                return -distance;
            }
            if (!hasCollisionWithStationary(
                state,
                movingIds,
                ignoredStationaryIds,
                distance)) {
                shiftPeople(state, movingIds, distance);
                return distance;
            }
        }
        return 0f;
    }

    private static boolean hasCollisionWithStationary(
        TreeState state,
        Set<String> movingIds,
        Set<String> ignoredStationaryIds,
        float dx
    ) {
        for (String movingId : movingIds) {
            Person moving = state.people.get(movingId);
            if (moving == null || moving.x + dx < 0f) return true;
            for (Person stationary : state.people.values()) {
                if (ignoredStationaryIds.contains(stationary.id)) continue;
                if (separateBranchesTooClose(
                    moving.x + dx,
                    moving.y,
                    stationary.x,
                    stationary.y)) return true;
            }
        }
        return false;
    }

    private static boolean cardsCollide(float ax, float ay, float bx, float by) {
        return ax < bx + CARD_W + GRID
            && bx < ax + CARD_W + GRID
            && ay < by + CARD_H + GRID
            && by < ay + CARD_H + GRID;
    }

    private static boolean separateBranchesTooClose(float ax, float ay, float bx, float by) {
        return ax < bx + CARD_W + BRANCH_GAP
            && bx < ax + CARD_W + BRANCH_GAP
            && ay < by + CARD_H + GRID
            && by < ay + CARD_H + GRID;
    }

    private static void shiftPeople(TreeState state, Collection<String> ids, float delta) {
        if (Math.abs(delta) < 0.5f) return;
        float snapped = snap(delta);
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person != null) person.x += snapped;
        }
    }

    private static void translateTreeInsideLeftBoundary(TreeState state) {
        float minX = Float.MAX_VALUE;
        for (Person person : state.people.values()) {
            if (Float.isFinite(person.x)) minX = Math.min(minX, person.x);
        }
        if (!Float.isFinite(minX) || minX >= 0f) return;
        float delta = snap(-minX);
        for (Person person : state.people.values()) person.x += delta;
    }

    private static void expandWorkspaceToFit(TreeState state) {
        float requiredRight = surfaceWidth(state);
        float requiredBottom = surfaceHeight(state);
        for (Person person : state.people.values()) {
            requiredRight = Math.max(requiredRight, person.x + CARD_W + MARGIN_X);
            requiredBottom = Math.max(requiredBottom, person.y + CARD_H + MARGIN_Y);
        }
        state.workspaceWidth = normalizeSurfaceWidth((int) Math.ceil(requiredRight));
        state.workspaceHeight = normalizeSurfaceHeight((int) Math.ceil(requiredBottom));
    }

    private static void layout(TreeState state, Set<String> addedIds) {
        if (state == null || state.people.isEmpty()) return;

        Relations relations = buildRelations(state);
        UnitGraph graph = makeUnits(state, relations);
        if (graph.units.isEmpty()) return;
        boolean preserveSavedOrder = hasUsableSavedLayout(state, graph, relations);

        String rootId = state.people.containsKey(state.rootId)
            ? state.rootId
            : state.people.keySet().iterator().next();
        state.rootId = rootId;
        Unit root = graph.personToUnit.get(rootId);
        if (root == null) root = graph.units.get(0);

        LayoutAnchor anchor = preserveSavedOrder ? savedAnchor(state, root) : null;
        assignLevels(state, graph, relations, root);
        if (preserveSavedOrder) assignOrderHintsFromSaved(graph);
        else assignOrderHints(state, graph, relations, root);
        Map<Integer, List<Unit>> rows = makeRows(state, graph);
        seedRows(rows, root, preserveSavedOrder);
        if (preserveSavedOrder && addedIds != null && !addedIds.isEmpty()) {
            centerFamiliesTouchedByAddition(state, graph, relations, rows, root, addedIds);
            refreshHorizontalAnchor(anchor, graph);
        }
        if (!preserveSavedOrder) {
            alignRows(rows, graph, relations, root.level);
        }
        separateFamilyComponents(graph, relations, root);
        restoreSavedHorizontalAnchor(graph, anchor);
        applyPositions(state, graph, anchor, root.level);
    }

    private static void centerFamiliesTouchedByAddition(
        TreeState state,
        UnitGraph graph,
        Relations relations,
        Map<Integer, List<Unit>> rows,
        Unit root,
        Set<String> addedIds
    ) {
        Set<Integer> touchedRows = new LinkedHashSet<>();
        Set<String> centeredFamilies = new HashSet<>();

        for (String addedId : addedIds) {
            Set<String> parentIds = relations.parentsByChild.getOrDefault(
                addedId,
                Collections.emptySet());
            if (!parentIds.isEmpty()) {
                String familyKey = String.join("|", new java.util.TreeSet<>(parentIds));
                if (centeredFamilies.add(familyKey)) {
                    List<Unit> children = childUnitsWithParents(graph, relations, parentIds);
                    Unit parent = graph.personToUnit.get(parentIds.iterator().next());
                    if (parent != null && !children.isEmpty()) {
                        reorderRootSiblingGroup(state, rows, root, children, parentIds, relations);
                        float anchor = parentAnchor(parent, parentIds);
                        placeSiblingGroup(children, rows.get(children.get(0).level), anchor);
                        touchedRows.add(children.get(0).level);
                    }
                }
            } else {
                Unit added = graph.personToUnit.get(addedId);
                List<Unit> siblings = explicitSiblingGroup(added, graph, relations);
                if (siblings.size() > 1) {
                    Float savedRootCenter = siblings.contains(root) ? savedUnitCenter(root) : null;
                    float anchor = savedRootCenter == null ? Float.NaN : savedRootCenter;
                    if (!Float.isFinite(anchor)) anchor = groupCenter(siblings);
                    placeSiblingGroup(siblings, rows.get(added.level), anchor);
                    touchedRows.add(added.level);
                }
            }

            Set<String> children = relations.childrenByParent.getOrDefault(
                addedId,
                Collections.emptySet());
            if (!children.isEmpty()) {
                Unit parentUnit = graph.personToUnit.get(addedId);
                if (parentUnit != null) {
                    List<Float> anchors = new ArrayList<>();
                    for (String childId : children) {
                        Unit childUnit = graph.personToUnit.get(childId);
                        if (childUnit == null) continue;
                        int childIndex = childUnit.people.indexOf(childId);
                        if (childIndex >= 0) anchors.add(personCenter(childUnit, childIndex));
                    }
                    if (!anchors.isEmpty()) {
                        Collections.sort(anchors);
                        float target = anchors.get(anchors.size() / 2);
                        if ((anchors.size() & 1) == 0) {
                            target = (anchors.get(anchors.size() / 2 - 1) + target) / 2f;
                        }
                        parentUnit.center = snap(target - parentUnit.width / 2f)
                            + parentUnit.width / 2f;
                        touchedRows.add(parentUnit.level);
                    }
                }
            }
        }

        for (Integer level : touchedRows) {
            List<Unit> row = rows.get(level);
            if (row == null || row.isEmpty()) continue;
            Map<String, Float> desired = new HashMap<>();
            for (Unit unit : row) desired.put(unit.id, unit.center);
            packRow(row, desired);
        }
    }

    private static List<Unit> childUnitsWithParents(
        UnitGraph graph,
        Relations relations,
        Set<String> parentIds
    ) {
        LinkedHashSet<Unit> result = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : relations.parentsByChild.entrySet()) {
            if (!entry.getValue().equals(parentIds)) continue;
            Unit child = graph.personToUnit.get(entry.getKey());
            if (child != null) result.add(child);
        }
        List<Unit> ordered = new ArrayList<>(result);
        ordered.sort(Comparator.comparingDouble(unit -> unit.center));
        return ordered;
    }

    private static List<Unit> explicitSiblingGroup(
        Unit added,
        UnitGraph graph,
        Relations relations
    ) {
        if (added == null) return Collections.emptyList();
        LinkedHashSet<Unit> result = new LinkedHashSet<>();
        result.add(added);
        for (String siblingId : siblingUnitIds(added, relations, graph.personToUnit)) {
            Unit sibling = graph.unitById.get(siblingId);
            if (sibling != null && sibling.level == added.level) result.add(sibling);
        }
        List<Unit> ordered = new ArrayList<>(result);
        ordered.sort(Comparator.comparingDouble(unit -> unit.center));
        return ordered;
    }

    private static void reorderRootSiblingGroup(
        TreeState state,
        Map<Integer, List<Unit>> rows,
        Unit root,
        List<Unit> children,
        Set<String> parentIds,
        Relations relations
    ) {
        if (root == null || !children.contains(root)) return;
        List<Unit> row = rows.get(root.level);
        if (row == null) return;
        float rootCenter = root.center;
        children.sort((first, second) -> {
            int firstRank = rootSiblingRank(state, first, root, rootCenter, parentIds, relations);
            int secondRank = rootSiblingRank(state, second, root, rootCenter, parentIds, relations);
            if (firstRank != secondRank) return Integer.compare(firstRank, secondRank);
            return Float.compare(first.center, second.center);
        });
        int insertAt = row.size();
        for (Unit child : children) {
            int index = row.indexOf(child);
            if (index >= 0) insertAt = Math.min(insertAt, index);
        }
        row.removeAll(children);
        row.addAll(Math.min(insertAt, row.size()), children);
    }

    private static int rootSiblingRank(
        TreeState state,
        Unit unit,
        Unit root,
        float rootCenter,
        Set<String> parentIds,
        Relations relations
    ) {
        if (unit == root) return 1;
        String childPerson = "";
        for (String id : unit.people) {
            if (relations.parentsByChild.getOrDefault(id, Collections.emptySet()).equals(parentIds)) {
                childPerson = id;
                break;
            }
        }
        String gender = inferGender(state, childPerson);
        if (PersonGender.MALE.equals(gender)) return 0;
        if (PersonGender.FEMALE.equals(gender)) return 2;
        return unit.center < rootCenter ? 0 : 2;
    }

    private static float parentAnchor(Unit parent, Set<String> parentIds) {
        float sum = 0f;
        int count = 0;
        for (String parentId : parentIds) {
            int index = parent.people.indexOf(parentId);
            if (index < 0) continue;
            sum += personCenter(parent, index);
            count++;
        }
        return count == 0 ? parent.center : sum / count;
    }

    private static void placeSiblingGroup(
        List<Unit> siblings,
        List<Unit> row,
        float anchor
    ) {
        if (siblings == null || siblings.isEmpty() || row == null || !Float.isFinite(anchor)) return;
        siblings.sort(Comparator.comparingInt(row::indexOf));
        boolean allSimple = true;
        for (Unit unit : siblings) {
            if (unit.people.size() != 1 || !unit.children.isEmpty()) {
                allSimple = false;
                break;
            }
        }
        if (allSimple) {
            float siblingStep = siblings.size() <= 3 ? CARD_W + SIBLING_GAP : 340f;
            float start = -((siblings.size() - 1) * siblingStep) / 2f;
            float anchorLeft = anchor - CARD_W / 2f;
            for (int i = 0; i < siblings.size(); i++) {
                float left = snap(anchorLeft + start + i * siblingStep);
                siblings.get(i).center = left + CARD_W / 2f;
            }
            return;
        }

        float width = 0f;
        for (int i = 0; i < siblings.size(); i++) {
            if (i > 0) width += gapBetween(siblings.get(i - 1), siblings.get(i));
            width += siblings.get(i).width;
        }
        float cursor = anchor - width / 2f;
        for (int i = 0; i < siblings.size(); i++) {
            Unit unit = siblings.get(i);
            if (i > 0) cursor += gapBetween(siblings.get(i - 1), unit);
            float left = snap(cursor);
            unit.center = left + unit.width / 2f;
            cursor = left + unit.width;
        }
    }

    private static float groupCenter(List<Unit> units) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Unit unit : units) {
            min = Math.min(min, unit.left());
            max = Math.max(max, unit.right());
        }
        return min == Float.MAX_VALUE ? Float.NaN : (min + max) / 2f;
    }

    private static boolean hasUsableSavedLayout(
        TreeState state,
        UnitGraph graph,
        Relations relations
    ) {
        return hasSavedPositions(state)
            && !graph.units.isEmpty()
            && assignLevelsFromSavedRows(state, graph, relations);
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
            people.sort((a, b) -> comparePeopleBySavedPosition(state, relations, a, b));
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

    private static void assignOrderHintsFromSaved(UnitGraph graph) {
        for (Unit unit : graph.units) {
            Float center = savedUnitCenter(unit);
            unit.orderHint = center == null ? 0d : center / ORDER_SCALE;
            unit.orderSamples = 1;
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

    private static void seedRows(
        Map<Integer, List<Unit>> rows,
        Unit root,
        boolean preserveSavedOrder
    ) {
        for (List<Unit> row : rows.values()) {
            Map<String, Float> desired = new HashMap<>();
            for (Unit unit : row) {
                Float saved = preserveSavedOrder ? savedUnitCenter(unit) : null;
                float center = saved == null
                    ? (float) (unit.orderHint * ORDER_SCALE)
                    : saved;
                Float pinned = pinnedCenter(unit);
                desired.put(unit.id, pinned == null ? center : pinned);
            }
            if (!preserveSavedOrder && row.contains(root) && pinnedCenter(root) == null) {
                desired.put(root.id, 0f);
            }
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
                if (row != null) packAlignedRow(
                    row,
                    desiredCenters(row, graph, relations, true));
            }
            for (int level = rootLevel + 1; level <= maxLevel; level++) {
                List<Unit> row = rows.get(level);
                if (row != null) packAlignedRow(
                    row,
                    desiredCenters(row, graph, relations, false));
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
    private static void packAlignedRow(
        List<Unit> row,
        Map<String, Float> desired
    ) {
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

    private static LayoutAnchor savedAnchor(TreeState state, Unit root) {
        if (state == null || root == null) return null;
        String personId = state.people.containsKey(state.rootId)
            ? state.rootId
            : root.people.isEmpty() ? "" : root.people.get(0);
        Person person = state.people.get(personId);
        if (!isValidPosition(person)) return null;
        return new LayoutAnchor(personId, person.x, person.y);
    }

    private static void restoreSavedHorizontalAnchor(UnitGraph graph, LayoutAnchor anchor) {
        if (graph == null || anchor == null) return;
        for (Unit unit : graph.units) {
            if (pinnedCenter(unit) != null) return;
        }
        Unit root = graph.personToUnit.get(anchor.personId);
        if (root == null) return;
        int index = root.people.indexOf(anchor.personId);
        if (index < 0) return;
        float currentLeft = root.left() + index * (CARD_W + PARTNER_GAP);
        float shift = snap(anchor.x - currentLeft);
        float minAfterShift = Float.MAX_VALUE;
        for (Unit unit : graph.units) minAfterShift = Math.min(minAfterShift, unit.left() + shift);
        if (minAfterShift < 0f) shift += snap(-minAfterShift + MARGIN_X);
        for (Unit unit : graph.units) unit.center += shift;
    }

    private static void refreshHorizontalAnchor(LayoutAnchor anchor, UnitGraph graph) {
        if (anchor == null || graph == null) return;
        Unit unit = graph.personToUnit.get(anchor.personId);
        if (unit == null) return;
        int index = unit.people.indexOf(anchor.personId);
        if (index < 0) return;
        anchor.x = unit.left() + index * (CARD_W + PARTNER_GAP);
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

    private static void applyPositions(
        TreeState state,
        UnitGraph graph,
        LayoutAnchor anchor,
        int rootLevel
    ) {
        int maxLevel = 0;
        for (Unit unit : graph.units) maxLevel = Math.max(maxLevel, unit.level);
        Float savedFirstRow = anchor == null
            ? null
            : snap(anchor.y) - rootLevel * LEVEL_GAP;
        List<Float> rowTops = generationRows(state, graph, maxLevel, savedFirstRow);

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

    private static List<Float> generationRows(
        TreeState state,
        UnitGraph graph,
        int maxLevel,
        Float savedFirstRow
    ) {
        float first = savedFirstRow == null ? MARGIN_Y : Math.max(0f, savedFirstRow);
        List<Float> guideRows = new ArrayList<>();
        for (Guide guide : state.guides) {
            if ("h".equals(guide.axis) && Float.isFinite(guide.position)) {
                guideRows.add(guide.position + GUIDE_CARD_OFFSET);
            }
        }
        if (savedFirstRow == null && !guideRows.isEmpty()) {
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

    private static int comparePeopleBySavedPosition(
        TreeState state,
        Relations relations,
        String first,
        String second
    ) {
        Person firstPerson = state.people.get(first);
        Person secondPerson = state.people.get(second);
        if (isValidPosition(firstPerson)
            && isValidPosition(secondPerson)
            && Math.abs(firstPerson.y - secondPerson.y) <= GRID * 2f) {
            int saved = Float.compare(firstPerson.x, secondPerson.x);
            if (saved != 0) return saved;
        }
        return comparePeople(state, relations, first, second);
    }

    private static Float savedUnitCenter(Unit unit) {
        if (unit == null || unit.people.isEmpty()) return null;
        float leftSum = 0f;
        int count = 0;
        for (int i = 0; i < unit.personRefs.size(); i++) {
            Person person = unit.personRefs.get(i);
            if (!isValidPosition(person)) continue;
            leftSum += person.x - i * (CARD_W + PARTNER_GAP);
            count++;
        }
        return count == 0 ? null : leftSum / count + unit.width / 2f;
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

    private static final class StepAction {
        final String action;
        final String anchorId;
        final LinkedHashSet<String> addedIds;
        final int priority;
        final int distance;
        final int firstOrder;

        StepAction(
            String action,
            String anchorId,
            Collection<String> addedIds,
            int priority,
            int distance,
            Map<String, Integer> sourceOrder
        ) {
            this.action = action;
            this.anchorId = anchorId;
            this.addedIds = new LinkedHashSet<>(addedIds);
            this.priority = priority;
            this.distance = distance;
            int order = Integer.MAX_VALUE;
            for (String id : addedIds) {
                order = Math.min(order, sourceOrder.getOrDefault(id, Integer.MAX_VALUE));
            }
            firstOrder = order;
        }
    }

    private static final class LocalBlock {
        final String rootId;
        final Set<String> people;
        final boolean simple;
        float left;
        float right;
        float rootCenter;
        final boolean added;
        final int addedOrder;

        LocalBlock(
            String rootId,
            Set<String> people,
            Set<String> rowPeople,
            boolean added,
            int addedOrder,
            Relations relations,
            TreeState state
        ) {
            this.rootId = rootId;
            this.people = new LinkedHashSet<>(people);
            this.added = added;
            this.addedOrder = addedOrder;
            Person root = state.people.get(rootId);
            rootCenter = root == null ? 0f : root.x + CARD_W / 2f;
            left = Float.MAX_VALUE;
            right = -Float.MAX_VALUE;
            for (String id : rowPeople) {
                Person person = state.people.get(id);
                if (person == null) continue;
                left = Math.min(left, person.x);
                right = Math.max(right, person.x + CARD_W);
            }
            if (left == Float.MAX_VALUE) {
                left = rootCenter - CARD_W / 2f;
                right = rootCenter + CARD_W / 2f;
            }
            boolean hasChildren = false;
            for (String id : rowPeople) {
                if (!relations.childrenByParent.getOrDefault(id, Collections.emptySet()).isEmpty()) {
                    hasChildren = true;
                    break;
                }
            }
            simple = rowPeople.size() == 1 && !hasChildren;
        }

        float width() {
            return right - left;
        }

        void shift(float delta) {
            left += delta;
            right += delta;
            rootCenter += delta;
        }
    }

    private static final class ParentFamilyBlock {
        final List<String> parents;
        final Set<String> branch = new LinkedHashSet<>();
        final List<Float> childCenters = new ArrayList<>();
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;

        ParentFamilyBlock(List<String> parents, TreeState state) {
            this.parents = new ArrayList<>(parents);
            for (String id : parents) {
                Person person = state.people.get(id);
                if (person == null) continue;
                branch.add(id);
                left = Math.min(left, person.x);
                right = Math.max(right, person.x + CARD_W);
            }
            if (left == Float.MAX_VALUE) {
                left = 0f;
                right = CARD_W;
            }
        }

        float width() {
            return right - left;
        }

        float childCenter() {
            if (childCenters.isEmpty()) return (left + right) / 2f;
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (Float center : childCenters) {
                min = Math.min(min, center);
                max = Math.max(max, center);
            }
            return (min + max) / 2f;
        }

        void shift(float delta) {
            left += delta;
            right += delta;
        }
    }

    private static final class LayoutAnchor {
        final String personId;
        float x;
        final float y;

        LayoutAnchor(String personId, float x, float y) {
            this.personId = personId;
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
