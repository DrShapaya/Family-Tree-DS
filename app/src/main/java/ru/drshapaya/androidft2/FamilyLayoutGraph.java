package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Semantic, coordinate-independent representation consumed by the future solver. */
final class FamilyLayoutGraph {
    private static final ThreadLocal<ReuseScope> REUSE = new ThreadLocal<>();

    /** Only for coordinate-only pipelines; topology is frozen until this scope closes. */
    static ReuseScope reuseTopology(TreeState state) {
        ReuseScope scope = new ReuseScope(state, REUSE.get()); REUSE.set(scope); return scope;
    }

    static final class ReuseScope implements AutoCloseable {
        final TreeState state;
        final ReuseScope previous;
        FamilyLayoutGraph graph;
        ReuseScope(TreeState state, ReuseScope previous) { this.state=state; this.previous=previous; }
        @Override public void close() { if (previous==null) REUSE.remove(); else REUSE.set(previous); }
    }
    GenerationPlan generationPlan;
    final String rootId;
    final Map<String, PersonNode> people = new LinkedHashMap<>();
    final List<PartnerUnit> partnerUnits = new ArrayList<>();
    final List<ParentFamily> parentFamilies = new ArrayList<>();
    final List<SiblingGroup> siblingGroups = new ArrayList<>();
    final List<Set<String>> components = new ArrayList<>();
    final List<GenerationConflict> generationConflicts = new ArrayList<>();
    final Map<String, Set<String>> parentsByChild = new LinkedHashMap<>();
    final Map<String, Set<String>> childrenByParent = new LinkedHashMap<>();
    final Map<String, Set<String>> partnersByPerson = new LinkedHashMap<>();
    final Map<String, Set<String>> siblingsByPerson = new LinkedHashMap<>();
    final Map<String, Integer> generationByPerson = new LinkedHashMap<>();
    final Map<String, Integer> componentByPerson = new LinkedHashMap<>();
    final Map<String, ParentFamily> parentFamilyByChild = new LinkedHashMap<>();
    final Map<String, List<PartnerUnit>> partnerUnitsByPerson = new LinkedHashMap<>();
    final Map<String, SiblingGroup> siblingGroupByPerson = new LinkedHashMap<>();

    private FamilyLayoutGraph(String rootId) {
        this.rootId = rootId == null ? "" : rootId;
    }

    static FamilyLayoutGraph from(TreeState state) {
        ReuseScope scope = REUSE.get();
        if (scope != null && scope.state == state) {
            if (scope.graph == null) scope.graph = build(state);
            return scope.graph;
        }
        return build(state);
    }

    private static FamilyLayoutGraph build(TreeState state) {
        String root = state == null ? "" : state.rootId;
        if (state != null && !state.people.containsKey(root) && !state.people.isEmpty()) {
            root = state.people.keySet().iterator().next();
        }
        FamilyLayoutGraph graph = new FamilyLayoutGraph(root);
        if (state == null) return graph;
        for (String id : state.people.keySet()) {
            graph.people.put(id, new PersonNode(id, PersonGender.resolve(state.people.get(id))));
        }
        for (Relation relation : state.links) {
            if (relation == null
                || !graph.people.containsKey(relation.from)
                || !graph.people.containsKey(relation.to)
                || relation.from.equals(relation.to)) continue;
            if ("parent".equals(relation.type)) {
                add(graph.parentsByChild, relation.to, relation.from);
                add(graph.childrenByParent, relation.from, relation.to);
            } else if ("partner".equals(relation.type) || "family".equals(relation.type)) {
                add(graph.partnersByPerson, relation.from, relation.to);
                add(graph.partnersByPerson, relation.to, relation.from);
            } else if ("sibling".equals(relation.type)) {
                add(graph.siblingsByPerson, relation.from, relation.to);
                add(graph.siblingsByPerson, relation.to, relation.from);
            }
        }
        graph.buildParentFamilies();
        graph.buildPartnerUnits();
        graph.buildSiblingGroups();
        graph.buildComponents();
        graph.generationPlan = GenerationPlan.from(state);
        if (graph.generationPlan.relaxed && !graph.generationPlan.cyclic) {
            for (Set<String> component : graph.components) {
                String anchor = component.contains(root) ? root : component.iterator().next();
                int offset = graph.generationPlan.levels.get(anchor);
                for (String id : component) {
                    graph.generationByPerson.put(id, graph.generationPlan.levels.get(id) - offset);
                }
            }
        } else {
            graph.assignGenerations();
        }
        return graph;
    }

    int generationStep(String from, String to, int ordinaryStep) {
        return generationPlan != null && generationPlan.relaxed && !generationPlan.cyclic
            ? generationPlan.difference(from, to) : ordinaryStep;
    }

    ParentFamily parentFamilyOf(String childId) {
        return parentFamilyByChild.get(childId);
    }

    Set<String> partnerHousehold(Collection<String> start) {
        Set<String> result = new LinkedHashSet<>(); ArrayDeque<String> queue = new ArrayDeque<>(start);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst(); if (!result.add(id)) continue;
            for (PartnerUnit pair : partnerUnitsOf(id)) queue.addAll(pair.people);
        }
        return result;
    }

    /** A redundant full-sibling edge carries no extra horizontal constraint. */
    boolean hasIndependentSibling(String id) {
        Set<String> origin = parentsByChild.getOrDefault(id, Collections.emptySet());
        for (String peer : siblingsByPerson.getOrDefault(id, Collections.emptySet()))
            if (origin.isEmpty() || !origin.equals(parentsByChild.getOrDefault(peer, Collections.emptySet()))) return true;
        return false;
    }

    List<PartnerUnit> partnerUnitsOf(String personId) {
        return Collections.unmodifiableList(new ArrayList<>(
            partnerUnitsByPerson.getOrDefault(personId, Collections.emptyList())));
    }

    String ancestrySignature(String personId) {
        return ancestrySignature(personId, new HashSet<>(), new HashMap<>());
    }

    private String ancestrySignature(
        String personId,
        Set<String> path,
        Map<String, String> cache
    ) {
        String cached = cache.get(personId);
        if (cached != null) return cached;
        if (!people.containsKey(personId)) return "missing";
        if (!path.add(personId)) return "cycle";
        List<String> parentSignatures = new ArrayList<>();
        for (String parentId : parentsByChild.getOrDefault(personId, Collections.emptySet())) {
            parentSignatures.add(ancestrySignature(parentId, path, cache));
        }
        Collections.sort(parentSignatures);
        path.remove(personId);
        String signature = "person(partners="
            + partnersByPerson.getOrDefault(personId, Collections.emptySet()).size()
            + ",parents=" + parentSignatures + ")";
        cache.put(personId, signature);
        return signature;
    }

    boolean structurallyMirrored(String firstId, String secondId) {
        return ancestrySignature(firstId).equals(ancestrySignature(secondId));
    }

    Set<String> ancestryBranch(Collection<String> startIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (startIds != null) queue.addAll(startIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!people.containsKey(id) || !result.add(id)) continue;
            queue.addAll(partnersByPerson.getOrDefault(id, Collections.emptySet()));
            queue.addAll(parentsByChild.getOrDefault(id, Collections.emptySet()));
        }
        return result;
    }

    /**
     * Full outward lineage: ancestors plus collateral sibling families of every ancestor.
     * It intentionally never follows the direct descendant path back toward the root.
     */
    Set<String> lineageBranch(Collection<String> startIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (startIds != null) queue.addAll(startIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!people.containsKey(id) || !result.add(id)) continue;
            queue.addAll(partnersByPerson.getOrDefault(id, Collections.emptySet()));
            queue.addAll(parentsByChild.getOrDefault(id, Collections.emptySet()));
            SiblingGroup group = siblingGroupByPerson.get(id);
            if (group == null) continue;
            for (String siblingId : group.people) {
                if (!siblingId.equals(id)) {
                    result.addAll(descendantBranch(Collections.singleton(siblingId)));
                }
            }
        }
        return result;
    }

    /**
     * Returns a rigid family branch below the supplied people. Partners stay in the same
     * block and every descendant household is included, while parents and siblings are not.
     */
    Set<String> descendantBranch(Collection<String> startIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (startIds != null) queue.addAll(startIds);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!people.containsKey(id) || !result.add(id)) continue;
            queue.addAll(partnersByPerson.getOrDefault(id, Collections.emptySet()));
            queue.addAll(childrenByParent.getOrDefault(id, Collections.emptySet()));
        }
        return result;
    }

    /** Selects the branch direction away from the root generation. */
    Set<String> outwardBranch(String personId) {
        Integer generation = generationByPerson.get(personId);
        if (generation != null && generation < 0) {
            return lineageBranch(Collections.singleton(personId));
        }
        return descendantBranch(Collections.singleton(personId));
    }

    private void buildPartnerUnits() {
        Map<String, LinkedHashSet<String>> pairs = new LinkedHashMap<>();
        for (String personId : people.keySet()) {
            for (String partnerId : partnersByPerson.getOrDefault(
                personId,
                Collections.emptySet())) {
                addPartnerPair(pairs, personId, partnerId);
            }
        }
        // A co-parent family is still a distinct layout union even if the user did
        // not add an explicit current partner link (for example after a divorce).
        for (ParentFamily family : parentFamilies) {
            if (family.parents.size() == 2) {
                addPartnerPair(pairs, family.parents.get(0), family.parents.get(1));
            }
        }

        LinkedHashSet<String> assigned = new LinkedHashSet<>();
        for (Map.Entry<String, LinkedHashSet<String>> entry : pairs.entrySet()) {
            PartnerUnit unit = new PartnerUnit("union:" + entry.getKey(), entry.getValue());
            for (ParentFamily family : parentFamilies) {
                if (samePeople(unit.people, family.parents)) unit.children.addAll(family.children);
            }
            partnerUnits.add(unit);
            for (String id : unit.people) {
                assigned.add(id);
                partnerUnitsByPerson.computeIfAbsent(id, ignored -> new ArrayList<>()).add(unit);
            }
        }
        for (String id : people.keySet()) {
            if (assigned.contains(id)) continue;
            PartnerUnit unit = new PartnerUnit(
                "single:" + id,
                Collections.singleton(id));
            partnerUnits.add(unit);
            partnerUnitsByPerson.computeIfAbsent(id, ignored -> new ArrayList<>()).add(unit);
        }
    }

    private static void addPartnerPair(
        Map<String, LinkedHashSet<String>> pairs,
        String first,
        String second
    ) {
        if (first == null || second == null || first.equals(second)) return;
        String left = first.compareTo(second) <= 0 ? first : second;
        String right = first.compareTo(second) <= 0 ? second : first;
        String key = left + "|" + right;
        pairs.putIfAbsent(key, new LinkedHashSet<>(java.util.Arrays.asList(left, right)));
    }

    private static boolean samePeople(Collection<String> first, Collection<String> second) {
        return first.size() == second.size()
            && new LinkedHashSet<>(first).equals(new LinkedHashSet<>(second));
    }

    private void buildParentFamilies() {
        Map<String, ParentFamily> byKey = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : parentsByChild.entrySet()) {
            List<String> sortedParents = new ArrayList<>(entry.getValue());
            Collections.sort(sortedParents);
            String key = String.join("|", sortedParents);
            ParentFamily family = byKey.computeIfAbsent(
                key,
                ignored -> new ParentFamily("family:" + key, sortedParents));
            family.children.add(entry.getKey());
            parentFamilyByChild.put(entry.getKey(), family);
        }
        parentFamilies.addAll(byKey.values());
    }

    private void buildSiblingGroups() {
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : siblingsByPerson.entrySet()) {
            for (String siblingId : entry.getValue()) {
                add(adjacency, entry.getKey(), siblingId);
                add(adjacency, siblingId, entry.getKey());
            }
        }
        // Children sharing at least one parent belong to the same semantic sibling
        // group. Exact parent sets remain separate ParentFamily instances, so half
        // siblings are related without merging their two family unions.
        for (Set<String> childrenSet : childrenByParent.values()) {
            if (childrenSet.size() < 2) continue;
            List<String> children = new ArrayList<>(childrenSet);
            String first = children.get(0);
            for (int index = 1; index < children.size(); index++) {
                add(adjacency, first, children.get(index));
                add(adjacency, children.get(index), first);
            }
        }

        Set<String> visited = new HashSet<>();
        for (String startId : people.keySet()) {
            if (visited.contains(startId) || !adjacency.containsKey(startId)) continue;
            LinkedHashSet<String> members = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(startId);
            visited.add(startId);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                members.add(id);
                for (String siblingId : adjacency.getOrDefault(id, Collections.emptySet())) {
                    if (visited.add(siblingId)) queue.addLast(siblingId);
                }
            }
            if (members.size() > 1) {
                SiblingGroup group = new SiblingGroup(
                    "siblings:" + String.join("|", members),
                    members);
                siblingGroups.add(group);
                for (String id : members) siblingGroupByPerson.put(id, group);
            }
        }
    }

    private void buildComponents() {
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        List<String> seeds = new ArrayList<>(people.keySet());
        if (people.containsKey(rootId)) {
            seeds.remove(rootId);
            seeds.add(0, rootId);
        }
        for (String seed : seeds) {
            if (!visited.add(seed)) continue;
            LinkedHashSet<String> component = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                component.add(id);
                for (String neighbor : neighborsOf(id)) {
                    if (people.containsKey(neighbor) && visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            int componentIndex = components.size();
            components.add(Collections.unmodifiableSet(component));
            for (String id : component) componentByPerson.put(id, componentIndex);
        }
    }

    private Set<String> neighborsOf(String id) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(partnersByPerson.getOrDefault(id, Collections.emptySet()));
        result.addAll(siblingsByPerson.getOrDefault(id, Collections.emptySet()));
        result.addAll(parentsByChild.getOrDefault(id, Collections.emptySet()));
        result.addAll(childrenByParent.getOrDefault(id, Collections.emptySet()));
        return result;
    }

    private void assignGenerations() {
        LinkedHashSet<String> seeds = new LinkedHashSet<>();
        if (people.containsKey(rootId)) seeds.add(rootId);
        seeds.addAll(people.keySet());
        for (String seed : seeds) {
            if (generationByPerson.containsKey(seed)) continue;
            assignComponentGenerations(seed);
        }
    }

    private void assignComponentGenerations(String seed) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        generationByPerson.put(seed, 0);
        queue.add(seed);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            int generation = generationByPerson.get(id);
            offerGeneration(id, "partner", partnersByPerson.getOrDefault(
                id, Collections.emptySet()), generation, queue);
            offerGeneration(id, "sibling", siblingsByPerson.getOrDefault(
                id, Collections.emptySet()), generation, queue);
            offerGeneration(id, "parent", parentsByChild.getOrDefault(
                id, Collections.emptySet()), generation - 1, queue);
            offerGeneration(id, "child", childrenByParent.getOrDefault(
                id, Collections.emptySet()), generation + 1, queue);
        }
    }

    private void offerGeneration(
        String sourceId,
        String relation,
        Collection<String> ids,
        int generation,
        ArrayDeque<String> queue
    ) {
        for (String id : ids) {
            if (!people.containsKey(id)) continue;
            Integer assigned = generationByPerson.get(id);
            if (assigned != null) {
                if (assigned != generation) {
                    addGenerationConflict(sourceId, id, relation, generation, assigned);
                }
                continue;
            }
            generationByPerson.put(id, generation);
            queue.addLast(id);
        }
    }

    private void addGenerationConflict(
        String sourceId,
        String targetId,
        String relation,
        int expected,
        int actual
    ) {
        for (GenerationConflict conflict : generationConflicts) {
            if (conflict.matches(sourceId, targetId, relation, expected, actual)) return;
        }
        generationConflicts.add(new GenerationConflict(
            sourceId,
            targetId,
            relation,
            expected,
            actual));
    }

    private static void add(Map<String, Set<String>> map, String key, String value) {
        map.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(value);
    }

    static final class PersonNode {
        final String id;
        final String gender;

        PersonNode(String id, String gender) {
            this.id = id;
            this.gender = PersonGender.normalize(gender);
        }
    }

    static final class PartnerUnit {
        final String id;
        final LinkedHashSet<String> people;
        final LinkedHashSet<String> children = new LinkedHashSet<>();

        PartnerUnit(String id, Collection<String> people) {
            this.id = id;
            this.people = new LinkedHashSet<>(people);
        }
    }

    static final class ParentFamily {
        final String id;
        final List<String> parents;
        final LinkedHashSet<String> children = new LinkedHashSet<>();

        ParentFamily(String id, Collection<String> parents) {
            this.id = id;
            this.parents = new ArrayList<>(parents);
        }
    }

    static final class SiblingGroup {
        final String id;
        final LinkedHashSet<String> people;

        SiblingGroup(String id, Collection<String> people) {
            this.id = id;
            this.people = new LinkedHashSet<>(people);
        }
    }

    static final class GenerationConflict {
        final String sourceId;
        final String targetId;
        final String relation;
        final int expectedGeneration;
        final int actualGeneration;

        GenerationConflict(
            String sourceId,
            String targetId,
            String relation,
            int expectedGeneration,
            int actualGeneration
        ) {
            this.sourceId = sourceId;
            this.targetId = targetId;
            this.relation = relation;
            this.expectedGeneration = expectedGeneration;
            this.actualGeneration = actualGeneration;
        }

        boolean matches(
            String sourceId,
            String targetId,
            String relation,
            int expectedGeneration,
            int actualGeneration
        ) {
            return this.sourceId.equals(sourceId)
                && this.targetId.equals(targetId)
                && this.relation.equals(relation)
                && this.expectedGeneration == expectedGeneration
                && this.actualGeneration == actualGeneration;
        }
    }
}
