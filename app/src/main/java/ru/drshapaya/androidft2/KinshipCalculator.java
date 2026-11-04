package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Determines how two people are related using the links already stored in the tree.
 * The calculation is read-only and is safe to use while editing is locked.
 */
final class KinshipCalculator {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final int MAX_GENERATIONS = 24;
    private static final int MAX_PATH_LENGTH = 16;
    static final String LINE_FATHER = "по отцовской линии";
    static final String LINE_MOTHER = "по материнской линии";
    static final String LINE_BOTH = "по обеим линиям";

    static final class Result {
        final boolean found;
        final String firstToSecond;
        final String secondToFirst;
        /** Side of the second person's family the first one is on, or "". */
        final String firstToSecondLineage;
        final String secondToFirstLineage;
        /** Classic name when it differs from the label ("двоюродная бабушка"), or "". */
        final String firstToSecondTraditional;
        final String secondToFirstTraditional;
        /** People from first to second along the relationship, both ends included; empty when unknown. */
        final List<String> pathIds;
        final String detail;

        Result(boolean found, String firstToSecond, String secondToFirst, String detail) {
            this(found, firstToSecond, secondToFirst, "", "", "", "", Collections.emptyList(), detail);
        }

        Result(boolean found, String firstToSecond, String secondToFirst,
               String firstToSecondLineage, String secondToFirstLineage,
               String firstToSecondTraditional, String secondToFirstTraditional,
               List<String> pathIds, String detail) {
            this.found = found;
            this.firstToSecond = firstToSecond;
            this.secondToFirst = secondToFirst;
            this.firstToSecondLineage = firstToSecondLineage;
            this.secondToFirstLineage = secondToFirstLineage;
            this.firstToSecondTraditional = firstToSecondTraditional;
            this.secondToFirstTraditional = secondToFirstTraditional;
            this.pathIds = Collections.unmodifiableList(new ArrayList<>(pathIds));
            this.detail = detail;
        }

        /** One line for menus and search: "сестра бабушки, по материнской линии". */
        String firstToSecondSummary() {
            return summary(firstToSecond, firstToSecondLineage);
        }

        String secondToFirstSummary() {
            return summary(secondToFirst, secondToFirstLineage);
        }

        private static String summary(String label, String lineage) {
            return lineage == null || lineage.isEmpty() ? label : label + ", " + lineage;
        }
    }

    private static final class Description {
        final boolean found;
        final String label;
        final String lineage;
        final String traditional;
        /** From subject to reference; null when only a graph search can tell. */
        final List<String> pathIds;

        Description(boolean found, String label, String lineage, String traditional, List<String> pathIds) {
            this.found = found;
            this.label = label;
            this.lineage = lineage;
            this.traditional = traditional;
            this.pathIds = pathIds;
        }
    }

    /** One tree revision: its relations index and the ancestor walks already made in it. */
    private static final class Context {
        final TreeRelationsIndex index;
        final Map<String, Map<String, AncestorStep>> ancestorSteps = new HashMap<>();

        Context(TreeState state) {
            index = new TreeRelationsIndex(state, false);
        }
    }

    private static final class CommonAncestor {
        String id = "";
        int subjectDepth = Integer.MAX_VALUE;
        int referenceDepth = Integer.MAX_VALUE;
    }

    private static final class SiblingAncestors {
        String subjectAncestorId = "";
        String referenceAncestorId = "";
        int subjectDepth = Integer.MAX_VALUE;
        int referenceDepth = Integer.MAX_VALUE;
    }

    private static final class AncestorStep {
        final int depth;
        final String childId;

        AncestorStep(int depth, String childId) {
            this.depth = depth;
            this.childId = childId;
        }
    }

    private KinshipCalculator() {
    }

    static Result calculate(TreeState state, String firstId, String secondId) {
        return calculate(state, firstId, secondId, false);
    }

    static Result calculate(TreeState state, String firstId, String secondId, boolean terminological) {
        Context previous=ACTIVE.get();
        if (state!=null) ACTIVE.set(new Context(state));
        try { return calculateIndexed(state,firstId,secondId,terminological); }
        finally { if (previous==null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }

    private static TreeRelationsIndex index() {
        return ACTIVE.get().index;
    }

    /**
     * Who every person is to {@code referenceId}, for kinship tags on cards. One relations
     * index and one set of ancestor walks serve the whole tree; people without a known
     * relationship are left out.
     */
    static java.util.Map<String, String> labelsRelativeTo(TreeState state, String referenceId, boolean terminological) {
        java.util.Map<String, String> labels = new java.util.HashMap<>();
        if (state == null || referenceId == null || !state.people.containsKey(referenceId)) return labels;
        Context previous=ACTIVE.get();
        ACTIVE.set(new Context(state));
        try {
            for (String personId : new java.util.ArrayList<>(state.people.keySet())) {
                if (personId.equals(referenceId)) continue;
                Description description = describe(state, personId, referenceId, true, terminological);
                if (description.found && description.label != null && !description.label.isEmpty()) {
                    labels.put(personId, withPartnerStatus(state, personId, referenceId, description.label));
                }
            }
        } finally {
            if (previous==null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
        return labels;
    }

    private static Result calculateIndexed(TreeState state, String firstId, String secondId, boolean terminological) {
        if (state == null || firstId == null || secondId == null
            || !state.people.containsKey(firstId) || !state.people.containsKey(secondId)) {
            return new Result(false, "родство не найдено", "родство не найдено", "");
        }
        if (firstId.equals(secondId)) {
            return new Result(true, "это один и тот же человек", "это один и тот же человек", "");
        }

        Description first = describe(state, firstId, secondId, true, terminological);
        Description second = describe(state, secondId, firstId, true, terminological);
        boolean found = first.found || second.found;
        // The chain shown is the one the relationship was worked out along, not just the shortest
        // link path: a marriage shortcut must not hide the blood line it is named after.
        List<String> pathIds = first.found && first.pathIds != null ? first.pathIds : null;
        if (pathIds == null && second.found && second.pathIds != null) {
            pathIds = new ArrayList<>(second.pathIds);
            Collections.reverse(pathIds);
        }
        if (pathIds == null) pathIds = graphPath(firstId, secondId);
        String path = describePath(state, pathIds);
        if (!found) {
            return new Result(false, "родство не найдено", "родство не найдено", "", "", "", "", pathIds, path);
        }
        return new Result(
            true,
            first.found ? withPartnerStatus(state, firstId, secondId, first.label) : "родство не определено",
            second.found ? withPartnerStatus(state, secondId, firstId, second.label) : "родство не определено",
            first.found ? first.lineage : "",
            second.found ? second.lineage : "",
            first.found ? first.traditional : "",
            second.found ? second.traditional : "",
            pathIds,
            path);
    }

    /**
     * Spouse terms follow the couple's relationship kind: an engaged wife is a bride,
     * a divorced one a former wife. Plain "partners" keeps the existing wording.
     */
    static String withPartnerStatus(TreeState state, String subjectId, String referenceId, String label) {
        if (state == null || label == null) return label;
        boolean wife = "жена".equals(label);
        boolean husband = "муж".equals(label);
        // Gender unknown: the calculator says "партнёр".
        boolean spouse = wife || husband || "партнёр".equals(label);
        // Card tags call this for everyone; only spouses need the scan over all links.
        if (!spouse) return label;
        String status = PartnerStatus.PARTNERS;
        for (Relation link : state.links) {
            if (!"partner".equals(link.type)) continue;
            if (subjectId.equals(link.from) && referenceId.equals(link.to)
                || referenceId.equals(link.from) && subjectId.equals(link.to)) {
                status = PartnerStatus.normalize(link.status);
                break;
            }
        }
        switch (status) {
            case PartnerStatus.ENGAGED: return wife ? "невеста" : husband ? "жених" : "жених или невеста";
            case PartnerStatus.DIVORCED: return wife ? "бывшая жена" : husband ? "бывший муж" : "бывший супруг";
            case PartnerStatus.SEPARATED: return wife ? "бывшая партнёрша" : husband ? "бывший партнёр" : "бывший партнёр";
            default: return label;
        }
    }

    /**
     * Returns who {@code subjectId} is to {@code referenceId}.
     */
    private static Description describe(
        TreeState state,
        String subjectId,
        String referenceId,
        boolean allowPartnerFallback,
        boolean terminological
    ) {
        Person subject = state.people.get(subjectId);
        if (subject == null || !state.people.containsKey(referenceId)) {
            return notFound();
        }
        List<String> direct = java.util.Arrays.asList(subjectId, referenceId);
        if (arePartners(state, subjectId, referenceId)) {
            return found(gendered(subject, "муж", "жена", "партнёр"), "", direct);
        }
        if (isParent(state, subjectId, referenceId)) {
            return found(gendered(subject, "отец", "мать", "родитель"), "", direct);
        }
        if (isParent(state, referenceId, subjectId)) {
            return found(gendered(subject, "сын", "дочь", "ребёнок"), "", direct);
        }
        if (areSiblings(state, subjectId, referenceId)) {
            return found(siblingLabel(state, subject, subjectId, referenceId), "", siblingPath(subjectId, referenceId));
        }

        Map<String, AncestorStep> subjectSteps = ancestorSteps(state, subjectId);
        Map<String, AncestorStep> referenceSteps = ancestorSteps(state, referenceId);
        AncestorStep referenceStepFromSubject = referenceSteps.get(subjectId);
        if (referenceStepFromSubject != null && referenceStepFromSubject.depth > 0) {
            List<String> path = pathToAncestor(referenceSteps, subjectId);
            Collections.reverse(path);
            return found(ancestorLabel(subject, referenceStepFromSubject.depth),
                lineage(state, referenceId, subjectId, referenceStepFromSubject.depth), path);
        }
        AncestorStep subjectStepFromReference = subjectSteps.get(referenceId);
        if (subjectStepFromReference != null && subjectStepFromReference.depth > 0) {
            return found(descendantLabel(subject, subjectStepFromReference.depth), "",
                pathToAncestor(subjectSteps, referenceId));
        }

        CommonAncestor common = nearestCommonAncestor(subjectSteps, referenceSteps);
        if (!common.id.isEmpty() && common.subjectDepth > 0 && common.referenceDepth > 0) {
            List<String> subjectPath = pathToAncestor(subjectSteps, common.id);
            List<String> referencePath = pathToAncestor(referenceSteps, common.id);
            return collateral(state, subject, referenceId, subjectPath, referencePath, false, terminological);
        }

        SiblingAncestors siblings = nearestSiblingAncestors(subjectSteps, referenceSteps);
        if (siblings.subjectDepth != Integer.MAX_VALUE) {
            List<String> subjectPath = pathToAncestor(subjectSteps, siblings.subjectAncestorId);
            List<String> referencePath = pathToAncestor(referenceSteps, siblings.referenceAncestorId);
            return collateral(state, subject, referenceId, subjectPath, referencePath, true, terminological);
        }

        if (allowPartnerFallback) {
            Description viaPartner = describeThroughPartner(state, subjectId, referenceId, terminological);
            if (viaPartner.found) return viaPartner;
        }
        return notFound();
    }

    /**
     * A collateral relative: the label, the side of the reference's family and the chain
     * subject → … → common ancestor (or a pair of explicit siblings) → … → reference.
     */
    private static Description collateral(
        TreeState state,
        Person subject,
        String referenceId,
        List<String> subjectPath,
        List<String> referencePath,
        boolean explicitSiblings,
        boolean terminology
    ) {
        String label = collateralLabel(state, subject, subjectPath, referencePath, explicitSiblings, terminology);
        List<String> path = new ArrayList<>(subjectPath);
        List<String> down = new ArrayList<>(referencePath);
        Collections.reverse(down);
        path.addAll(explicitSiblings ? down : down.subList(1, down.size()));
        // A younger relative descends from the reference's sibling or cousin: "внучка сестры"
        // already names the branch, and the reference's own parents say nothing about it.
        boolean younger = subjectPath.size() > referencePath.size();
        int referenceDepth = referencePath.size() - 1;
        String side = younger || referenceDepth < 1
            ? ""
            : lineage(state, referenceId, referencePath.get(referenceDepth), referenceDepth);
        int subjectDepth = subjectPath.size() - 1;
        int degree = explicitSiblings
            ? Math.min(subjectDepth, referenceDepth)
            : Math.min(subjectDepth - 1, referenceDepth - 1);
        String traditional = traditionalTerm(subject, degree, Math.abs(subjectDepth - referenceDepth), younger);
        return new Description(true, label, side, traditional.equals(label) ? "" : traditional, path);
    }

    /**
     * The classic Russian name, shown next to the descriptive label in the kinship panel:
     * a grandparent's sister is "двоюродная бабушка", her cousin "троюродная" — the adjective
     * is one step further than the cousin degree. Empty when there is no settled term or the
     * subject's gender is unknown.
     */
    static String traditionalTerm(Person subject, int degree, int difference, boolean younger) {
        String gender = PersonGender.resolve(subject);
        boolean male = PersonGender.MALE.equals(gender);
        if (!male && !PersonGender.FEMALE.equals(gender)) return "";
        if (difference == 0) return cousinLabel(subject, degree);
        if (difference == 1) {
            String adjective = degree == 0 ? "" : cousinStem(degree) + (male ? "ый " : "ая ");
            return adjective + (younger ? (male ? "племянник" : "племянница") : (male ? "дядя" : "тётя"));
        }
        if (!younger) {
            return cousinStem(degree + 1) + (male ? "ый " : "ая ") + ancestorLabel(subject, difference);
        }
        if (difference > 3) return "";
        String adjective = degree == 0 ? "" : cousinStem(degree) + (male ? "ый " : "ая ");
        String kind = difference == 2 ? "внучат" : "правнучат";
        return adjective + kind + (male ? "ый племянник" : "ая племянница");
    }

    /**
     * A shorter form of a label for the tags above cards, where a whole phrase does not fit:
     * "четвероюродная сестра с разницей в 2 поколения (младшее поколение)" becomes
     * "4-юродная сестра ↓2 пок.", "сестра дедушки или бабушки" becomes "сестра дедушки/бабушки".
     */
    static String compactLabel(String label) {
        if (label == null) return null;
        String result = label;
        java.util.regex.Matcher parametric = java.util.regex.Pattern
            .compile("^(.+) с разницей в (\\d+) \\S+ \\((младшее|старшее) поколение\\)$")
            .matcher(result);
        if (parametric.matches()) {
            result = parametric.group(1) + ("младшее".equals(parametric.group(3)) ? " ↓" : " ↑")
                + parametric.group(2) + " пок.";
        }
        String[][] stems = {
            {"четвероюродн", "4-юродн"}, {"пятиюродн", "5-юродн"}, {"шестиюродн", "6-юродн"},
            {"семиюродн", "7-юродн"}, {"восьмиюродн", "8-юродн"}, {"девятиюродн", "9-юродн"},
            {"десятиюродн", "10-юродн"},
        };
        for (String[] stem : stems) result = result.replace(stem[0], stem[1]);
        result = result.replace("супруг или супруга", "супруг(а)");
        // "4-юродного брата или 4-юродной сестры" -> "4-юродного брата/сестры".
        result = result.replaceAll("(\\S+)ого брата или \\1ой сестры", "$1ого брата/сестры");
        result = result.replace(" или ", "/");
        return result;
    }

    /**
     * Which of the reference's parents leads to {@code ancestorId} in {@code depth} generations.
     * With related parents both can. At depth 1 the ancestor is that parent: an aunt linked
     * to the father as an explicit sibling is on the father's side.
     */
    private static String lineage(TreeState state, String referenceId, String ancestorId, int depth) {
        if (depth < 1) return "";
        List<String> through = new ArrayList<>();
        for (String parentId : sorted(index().parents.getOrDefault(referenceId, Collections.emptySet()))) {
            AncestorStep step = ancestorSteps(state, parentId).get(ancestorId);
            if (step != null && step.depth == depth - 1) through.add(parentId);
        }
        if (through.size() > 1) return LINE_BOTH;
        if (through.isEmpty()) return "";
        return gendered(state.people.get(through.get(0)), LINE_FATHER, LINE_MOTHER, "");
    }

    private static List<String> siblingPath(String subjectId, String referenceId) {
        if (index().siblings.getOrDefault(subjectId, Collections.emptySet()).contains(referenceId)) {
            return java.util.Arrays.asList(subjectId, referenceId);
        }
        Set<String> referenceParents = index().parents.getOrDefault(referenceId, Collections.emptySet());
        for (String parentId : sorted(index().parents.getOrDefault(subjectId, Collections.emptySet()))) {
            if (referenceParents.contains(parentId)) return java.util.Arrays.asList(subjectId, parentId, referenceId);
        }
        return java.util.Arrays.asList(subjectId, referenceId);
    }

    private static List<String> sorted(Set<String> ids) {
        List<String> list = new ArrayList<>(ids);
        Collections.sort(list);
        return list;
    }

    private static Description describeThroughPartner(TreeState state, String subjectId, String referenceId, boolean terminological) {
        Person subject = state.people.get(subjectId);
        if (subject == null) return notFound();

        for (String partnerId : partnersOf(state, subjectId)) {
            Description partnerRelation = describe(state, partnerId, referenceId, false, terminological);
            if (!partnerRelation.found) continue;
            List<String> path = null;
            if (partnerRelation.pathIds != null) {
                path = new ArrayList<>();
                path.add(subjectId);
                path.addAll(partnerRelation.pathIds);
            }
            if (isParent(state, partnerId, referenceId)) {
                return found(gendered(subject, "отчим", "мачеха", "супруг или супруга родителя"), "", path);
            }
            if (isParent(state, referenceId, partnerId)) {
                return found(gendered(subject, "зять", "невестка", "супруг или супруга ребёнка"), "", path);
            }
            if (areSiblings(state, partnerId, referenceId)) {
                Person partner = state.people.get(partnerId);
                String partnerGender = PersonGender.resolve(partner);
                if (PersonGender.MALE.equals(partnerGender)) {
                    return found(gendered(subject, "муж брата", "жена брата", "супруг или супруга брата"), "", path);
                }
                if (PersonGender.FEMALE.equals(partnerGender)) {
                    return found(gendered(subject, "муж сестры", "жена сестры", "супруг или супруга сестры"), "", path);
                }
                return found(gendered(
                    subject,
                    "муж сестры или брата",
                    "жена брата или сестры",
                    "супруг или супруга брата или сестры"), "", path);
            }
            String spouse = gendered(subject, "муж", "жена", "партнёр");
            String relative = KinshipGenitive.of(partnerRelation.label);
            // "жена дяди"; a term the declension does not know keeps the plain form in brackets.
            // The uncle's side of the family is his wife's side too.
            return found(relative != null
                ? spouse + " " + relative
                : spouse + " (" + partnerRelation.label + ")", partnerRelation.lineage, path);
        }

        for (String referencePartnerId : partnersOf(state, referenceId)) {
            Description relationToPartner = describe(state, subjectId, referencePartnerId, false, terminological);
            if (!relationToPartner.found) continue;
            List<String> path = null;
            if (relationToPartner.pathIds != null) {
                path = new ArrayList<>(relationToPartner.pathIds);
                path.add(referenceId);
            }
            if (isParent(state, subjectId, referencePartnerId)) {
                String partnerGender = PersonGender.resolve(state.people.get(referencePartnerId));
                if (PersonGender.MALE.equals(partnerGender)) {
                    return found(gendered(subject, "свёкор", "свекровь", "родитель мужа"), "", path);
                }
                if (PersonGender.FEMALE.equals(partnerGender)) {
                    return found(gendered(subject, "тесть", "тёща", "родитель жены"), "", path);
                }
                return found(gendered(subject, "отец супруга или супруги", "мать супруга или супруги", "родитель супруга или супруги"), "", path);
            }
            if (isParent(state, referencePartnerId, subjectId)) {
                return found(gendered(subject, "пасынок", "падчерица", "ребёнок супруга или супруги"), "", path);
            }
            if (areSiblings(state, subjectId, referencePartnerId)) {
                String partnerGender = PersonGender.resolve(state.people.get(referencePartnerId));
                if (PersonGender.MALE.equals(partnerGender)) {
                    return found(gendered(subject, "деверь", "золовка", "брат или сестра мужа"), "", path);
                }
                if (PersonGender.FEMALE.equals(partnerGender)) {
                    return found(gendered(subject, "шурин", "свояченица", "брат или сестра жены"), "", path);
                }
                return found(gendered(subject, "брат супруга или супруги", "сестра супруга или супруги", "брат или сестра супруга или супруги"), "", path);
            }
            String partnerGender = PersonGender.resolve(state.people.get(referencePartnerId));
            return found(relationToPartner.label + (PersonGender.MALE.equals(partnerGender) ? " мужа"
                : PersonGender.FEMALE.equals(partnerGender) ? " жены" : " супруга или супруги"), "", path);
        }
        return notFound();
    }

    /**
     * Breadth-first walk up the tree. Parents are visited in id order, so when an ancestor is
     * reachable along two equally short lines the same one is chosen whatever the link order.
     * Walks are kept for the whole calculation; callers must not change the map.
     */
    private static Map<String, AncestorStep> ancestorSteps(TreeState state, String startId) {
        Map<String, Map<String, AncestorStep>> cache = ACTIVE.get().ancestorSteps;
        Map<String, AncestorStep> cached = cache.get(startId);
        if (cached != null) return cached;
        Map<String, AncestorStep> steps = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        steps.put(startId, new AncestorStep(0, null));
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            int depth = steps.get(current).depth;
            if (depth >= MAX_GENERATIONS) continue;
            for (String parent : sorted(index().parents.getOrDefault(current, Collections.emptySet()))) {
                if (steps.containsKey(parent)) continue;
                steps.put(parent, new AncestorStep(depth + 1, current));
                queue.add(parent);
            }
        }
        cache.put(startId, steps);
        return steps;
    }

    private static List<String> pathToAncestor(Map<String, AncestorStep> steps, String ancestorId) {
        List<String> path = new ArrayList<>();
        String current = ancestorId;
        while (current != null) {
            path.add(current);
            AncestorStep step = steps.get(current);
            current = (step != null) ? step.childId : null;
        }
        Collections.reverse(path);
        return path;
    }

    private static CommonAncestor nearestCommonAncestor(
        Map<String, AncestorStep> subjectSteps,
        Map<String, AncestorStep> referenceSteps
    ) {
        CommonAncestor best = new CommonAncestor();
        int bestTotal = Integer.MAX_VALUE;
        int bestMax = Integer.MAX_VALUE;
        for (Map.Entry<String, AncestorStep> entry : subjectSteps.entrySet()) {
            AncestorStep referenceStep = referenceSteps.get(entry.getKey());
            if (referenceStep == null) continue;
            int subjectDepth = entry.getValue().depth;
            int referenceDepth = referenceStep.depth;
            int total = subjectDepth + referenceDepth;
            int max = Math.max(subjectDepth, referenceDepth);
            if (total < bestTotal || (total == bestTotal && max < bestMax)) {
                best.id = entry.getKey();
                best.subjectDepth = subjectDepth;
                best.referenceDepth = referenceDepth;
                bestTotal = total;
                bestMax = max;
            }
        }
        return best;
    }

    /** Looks up each subject ancestor's explicit siblings among the reference's ancestors. */
    private static SiblingAncestors nearestSiblingAncestors(
        Map<String, AncestorStep> subjectSteps,
        Map<String, AncestorStep> referenceSteps
    ) {
        SiblingAncestors best = new SiblingAncestors();
        int bestTotal = Integer.MAX_VALUE;
        for (Map.Entry<String, AncestorStep> subject : subjectSteps.entrySet()) {
            Set<String> siblings = index().siblings.getOrDefault(subject.getKey(), Collections.emptySet());
            if (siblings.isEmpty()) continue;
            for (String siblingId : sorted(siblings)) {
                AncestorStep reference = referenceSteps.get(siblingId);
                if (reference == null) continue;
                int total = subject.getValue().depth + reference.depth;
                if (total < bestTotal) {
                    best.subjectAncestorId = subject.getKey();
                    best.referenceAncestorId = siblingId;
                    best.subjectDepth = subject.getValue().depth;
                    best.referenceDepth = reference.depth;
                    bestTotal = total;
                }
            }
        }
        return best;
    }

    private static boolean isParent(TreeState state, String parentId, String childId) {
        return index().parents.getOrDefault(childId, Collections.emptySet()).contains(parentId);
    }

    private static boolean arePartners(TreeState state, String firstId, String secondId) {
        return index().partners.getOrDefault(firstId, Collections.emptySet()).contains(secondId);
    }

    private static boolean areSiblings(TreeState state, String firstId, String secondId) {
        if (areExplicitSiblings(state, firstId, secondId)) return true;
        Set<String> firstParents = parentsOf(state, firstId);
        if (firstParents.isEmpty()) return false;
        for (String parentId : parentsOf(state, secondId)) {
            if (firstParents.contains(parentId)) return true;
        }
        return false;
    }

    private static boolean areExplicitSiblings(TreeState state, String firstId, String secondId) {
        return index().siblings.getOrDefault(firstId, Collections.emptySet()).contains(secondId);
    }

    private static Set<String> parentsOf(TreeState state, String childId) {
        return new LinkedHashSet<>(index().parents.getOrDefault(childId, Collections.emptySet()));
    }

    private static Set<String> partnersOf(TreeState state, String personId) {
        return new LinkedHashSet<>(sorted(index().partners.getOrDefault(personId, Collections.emptySet())));
    }

    private static String ancestorLabel(Person subject, int generations) {
        if (generations <= 1) return gendered(subject, "отец", "мать", "родитель");
        if (generations == 2) return gendered(subject, "дедушка", "бабушка", "дедушка или бабушка");
        if (generations == 3) return gendered(subject, "прадедушка", "прабабушка", "прадедушка или прабабушка");
        if (generations == 4) return gendered(subject, "прапрадедушка", "прапрабабушка", "прапрадедушка или прапрабабушка");
        if (generations == 5) return gendered(subject, "прапрапрадедушка", "прапрапрабабушка", "прапрапрадедушка или прапрапрабабушка");
        return gendered(subject,
            (generations - 2) + "×прадедушка",
            (generations - 2) + "×прабабушка",
            "предок в " + generations + "-м поколении");
    }

    private static String descendantLabel(Person subject, int generations) {
        if (generations <= 1) return gendered(subject, "сын", "дочь", "ребёнок");
        if (generations == 2) return gendered(subject, "внук", "внучка", "внук или внучка");
        if (generations == 3) return gendered(subject, "правнук", "правнучка", "правнук или правнучка");
        if (generations == 4) return gendered(subject, "праправнук", "праправнучка", "праправнук или праправнучка");
        if (generations == 5) return gendered(subject, "прапраправнук", "прапраправнучка", "прапраправнук или прапраправнучка");
        return gendered(subject,
            (generations - 2) + "×правнук",
            (generations - 2) + "×правнучка",
            "потомок в " + generations + "-м поколении");
    }

    private static String ancestorGenitive(Person ancestor, int difference) {
        if (difference == 1) return gendered(ancestor, "отца", "матери", "родителя");
        if (difference == 2) return gendered(ancestor, "дедушки", "бабушки", "дедушки или бабушки");
        if (difference == 3) return gendered(ancestor, "прадедушки", "прабабушки", "прадедушки или прабабушки");
        if (difference == 4) return gendered(ancestor, "прапрадедушки", "прапрабабушки", "прапрадедушки или прапрабабушки");
        if (difference == 5) return gendered(ancestor, "прапрапрадедушки", "прапрапрабабушки", "прапрапрадедушки или прапрапрабабушки");
        return gendered(ancestor,
            (difference - 2) + "×прадедушки",
            (difference - 2) + "×прабабушки",
            "предка в " + difference + "-м поколении");
    }

    /** Depths measured from a pair of siblings or common ancestor. */
    private static String collateralLabel(
        TreeState state,
        Person subject,
        List<String> subjectPath,
        List<String> referencePath,
        boolean explicitSiblings,
        boolean terminology
    ) {
        int subjectDepth = subjectPath.size() - 1;
        int referenceDepth = referencePath.size() - 1;
        int degree = explicitSiblings
            ? Math.min(subjectDepth, referenceDepth)
            : Math.min(subjectDepth - 1, referenceDepth - 1);
        int difference = Math.abs(subjectDepth - referenceDepth);
        boolean younger = subjectDepth > referenceDepth;
        String cousin = cousinLabel(subject, degree);
        if (difference == 0) return cousin;
        if (!terminology && degree > 0) {
            return cousin + " с разницей в " + difference + " " + generationWord(difference)
                + (younger ? " (младшее поколение)" : " (старшее поколение)");
        }
        String stem = cousinStem(degree);
        if (difference == 1) {
            String male = degree == 0 ? "" : stem + "ый ";
            String female = degree == 0 ? "" : stem + "ая ";
            return gendered(subject,
                male + (younger ? "племянник" : "дядя"),
                female + (younger ? "племянница" : "тётя"),
                male + (younger ? "племянник или племянница" : "дядя или тётя"));
        }
        if (younger) {
            Person linkingRelative = null;
            if (difference < subjectPath.size()) {
                linkingRelative = state.people.get(subjectPath.get(difference));
            }
            String relatives;
            if (degree == 0) {
                relatives = gendered(linkingRelative, "брата", "сестры", "брата или сестры");
            } else {
                relatives = gendered(linkingRelative,
                    stem + "ого брата",
                    stem + "ой сестры",
                    stem + "ого брата или " + stem + "ой сестры");
            }
            return descendantLabel(subject, difference) + " " + relatives;
        }
        Person linkingAncestor = null;
        if (difference < referencePath.size()) {
            linkingAncestor = state.people.get(referencePath.get(difference));
        }
        String ancestor = ancestorGenitive(linkingAncestor, difference);
        return cousin + " " + ancestor;
    }

    private static String cousinLabel(Person subject, int degree) {
        if (degree == 0) return gendered(subject, "брат", "сестра", "брат или сестра");
        String stem = cousinStem(degree);
        return gendered(subject, stem + "ый брат", stem + "ая сестра", stem + "ый брат или сестра");
    }

    private static String cousinStem(int degree) {
        switch (degree) {
            case 1: return "двоюродн";
            case 2: return "троюродн";
            case 3: return "четвероюродн";
            case 4: return "пятиюродн";
            case 5: return "шестиюродн";
            case 6: return "семиюродн";
            case 7: return "восьмиюродн";
            case 8: return "девятиюродн";
            case 9: return "десятиюродн";
            default: return (degree + 1) + "-юродн";
        }
    }

    private static String generationWord(int value) {
        int lastTwo = Math.abs(value) % 100;
        int last = lastTwo % 10;
        if (lastTwo >= 11 && lastTwo <= 14) return "поколений";
        if (last == 1) return "поколение";
        if (last >= 2 && last <= 4) return "поколения";
        return "поколений";
    }

    private static String gendered(Person person, String male, String female, String unknown) {
        String gender = PersonGender.resolve(person);
        if (PersonGender.MALE.equals(gender)) return male;
        if (PersonGender.FEMALE.equals(gender)) return female;
        return unknown;
    }

    private static String siblingLabel(TreeState state, Person subject, String subjectId, String referenceId) {
        Set<String> subjectParents = parentsOf(state, subjectId);
        Set<String> referenceParents = parentsOf(state, referenceId);
        Set<String> commonParents = new LinkedHashSet<>(subjectParents);
        commonParents.retainAll(referenceParents);
        // A missing second parent is not evidence that siblings have different parents.
        if (commonParents.size() != 1 || subjectParents.size() < 2 || referenceParents.size() < 2) {
            return gendered(subject, "брат", "сестра", "брат или сестра");
        }
        Person commonParent = state.people.get(commonParents.iterator().next());
        String parentGender = PersonGender.resolve(commonParent);
        if (PersonGender.MALE.equals(parentGender)) {
            return gendered(subject, "единокровный брат", "единокровная сестра", "единокровный брат или сестра");
        }
        if (PersonGender.FEMALE.equals(parentGender)) {
            return gendered(subject, "единоутробный брат", "единоутробная сестра", "единоутробный брат или сестра");
        }
        return gendered(subject, "неполнородный брат", "неполнородная сестра", "неполнородный брат или сестра");
    }

    /** Shortest chain over any links; the fallback when no relationship path is known. */
    private static List<String> graphPath(String startId, String endId) {
        Map<String, String> previous = new HashMap<>();
        Map<String, Integer> depth = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startId);
        depth.put(startId, 0);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (current.equals(endId)) break;
            int currentDepth = depth.get(current);
            if (currentDepth >= MAX_PATH_LENGTH) continue;
            for (String neighbor : index().adjacent.getOrDefault(current, Collections.emptySet())) {
                if (depth.containsKey(neighbor)) continue;
                depth.put(neighbor, currentDepth + 1);
                previous.put(neighbor, current);
                queue.add(neighbor);
            }
        }
        if (!depth.containsKey(endId)) return Collections.emptyList();
        List<String> ids = new ArrayList<>();
        String current = endId;
        ids.add(current);
        while (!current.equals(startId)) {
            current = previous.get(current);
            if (current == null) return Collections.emptyList();
            ids.add(current);
        }
        Collections.reverse(ids);
        return ids;
    }

    private static String describePath(TreeState state, List<String> ids) {
        if (ids.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Person person = state.people.get(id);
            names.add(person == null || person.name == null || person.name.trim().isEmpty()
                ? "Без имени"
                : person.name.trim());
        }
        return "Цепочка в дереве: " + String.join(" → ", names);
    }

    private static Description found(String label, String lineage, List<String> pathIds) {
        return new Description(true, label, lineage, "", pathIds);
    }

    private static Description notFound() {
        return new Description(false, "родство не найдено", "", "", null);
    }
}
