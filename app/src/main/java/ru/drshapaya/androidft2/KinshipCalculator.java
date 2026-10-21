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
    private static final ThreadLocal<TreeRelationsIndex> ACTIVE_INDEX = new ThreadLocal<>();
    private static final int MAX_GENERATIONS = 24;
    private static final int MAX_PATH_LENGTH = 16;
    static final class Result {
        final boolean found;
        final String firstToSecond;
        final String secondToFirst;
        final String detail;

        Result(boolean found, String firstToSecond, String secondToFirst, String detail) {
            this.found = found;
            this.firstToSecond = firstToSecond;
            this.secondToFirst = secondToFirst;
            this.detail = detail;
        }
    }

    private static final class Description {
        final boolean found;
        final String label;

        Description(boolean found, String label) {
            this.found = found;
            this.label = label;
        }
    }

    private static final class CommonAncestor {
        String id = "";
        int subjectDepth = Integer.MAX_VALUE;
        int referenceDepth = Integer.MAX_VALUE;
    }

    private static final class SiblingAncestors {
        int subjectDepth = Integer.MAX_VALUE;
        int referenceDepth = Integer.MAX_VALUE;
    }

    private KinshipCalculator() {
    }

    static Result calculate(TreeState state, String firstId, String secondId) {
        return calculate(state, firstId, secondId, false);
    }

    static Result calculate(TreeState state, String firstId, String secondId, boolean terminological) {
        TreeRelationsIndex previous=ACTIVE_INDEX.get();
        if (state!=null) ACTIVE_INDEX.set(new TreeRelationsIndex(state,false));
        try { return calculateIndexed(state,firstId,secondId,terminological); }
        finally { if (previous==null) ACTIVE_INDEX.remove(); else ACTIVE_INDEX.set(previous); }
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
        String path = buildPathDescription(state, firstId, secondId);
        if (!found) {
            return new Result(false, "родство не найдено", "родство не найдено", path);
        }
        return new Result(
            true,
            first.found ? first.label : "родство не определено",
            second.found ? second.label : "родство не определено",
            path);
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
        if (arePartners(state, subjectId, referenceId)) {
            return found(gendered(subject, "муж", "жена", "партнёр"));
        }
        if (isParent(state, subjectId, referenceId)) {
            return found(gendered(subject, "отец", "мать", "родитель"));
        }
        if (isParent(state, referenceId, subjectId)) {
            return found(gendered(subject, "сын", "дочь", "ребёнок"));
        }
        if (areSiblings(state, subjectId, referenceId)) {
            return found(siblingLabel(state, subject, subjectId, referenceId));
        }

        Map<String, Integer> subjectAncestors = ancestorDepths(state, subjectId);
        Map<String, Integer> referenceAncestors = ancestorDepths(state, referenceId);
        Integer referenceDepthFromSubject = referenceAncestors.get(subjectId);
        if (referenceDepthFromSubject != null && referenceDepthFromSubject > 0) {
            return found(ancestorLabel(subject, referenceDepthFromSubject));
        }
        Integer subjectDepthFromReference = subjectAncestors.get(referenceId);
        if (subjectDepthFromReference != null && subjectDepthFromReference > 0) {
            return found(descendantLabel(subject, subjectDepthFromReference));
        }

        CommonAncestor common = nearestCommonAncestor(subjectAncestors, referenceAncestors);
        if (!common.id.isEmpty() && common.subjectDepth > 0 && common.referenceDepth > 0) {
            return found(collateralLabel(subject, common.subjectDepth - 1,
                common.referenceDepth - 1, terminological));
        }

        SiblingAncestors siblings = nearestSiblingAncestors(state, subjectAncestors, referenceAncestors);
        if (siblings.subjectDepth != Integer.MAX_VALUE) {
            return found(collateralLabel(subject, siblings.subjectDepth, siblings.referenceDepth, terminological));
        }

        if (allowPartnerFallback) {
            Description viaPartner = describeThroughPartner(state, subjectId, referenceId, terminological);
            if (viaPartner.found) return viaPartner;
        }
        return notFound();
    }

    private static Description describeThroughPartner(TreeState state, String subjectId, String referenceId, boolean terminological) {
        Person subject = state.people.get(subjectId);
        if (subject == null) return notFound();

        for (String partnerId : partnersOf(state, subjectId)) {
            Description partnerRelation = describe(state, partnerId, referenceId, false, terminological);
            if (!partnerRelation.found) continue;
            if (isParent(state, partnerId, referenceId)) {
                return found(gendered(subject, "отчим", "мачеха", "супруг или супруга родителя"));
            }
            if (isParent(state, referenceId, partnerId)) {
                return found(gendered(subject, "зять", "невестка", "супруг или супруга ребёнка"));
            }
            if (areSiblings(state, partnerId, referenceId)) {
                return found(gendered(
                    subject,
                    "муж сестры или брата",
                    "жена брата или сестры",
                    "супруг или супруга брата или сестры"));
            }
            return found(gendered(subject, "муж", "жена", "партнёр") + " человека, который приходится "
                + partnerRelation.label);
        }

        for (String referencePartnerId : partnersOf(state, referenceId)) {
            Description relationToPartner = describe(state, subjectId, referencePartnerId, false, terminological);
            if (!relationToPartner.found) continue;
            if (isParent(state, subjectId, referencePartnerId)) {
                String partnerGender = PersonGender.resolve(state.people.get(referencePartnerId));
                if (PersonGender.MALE.equals(partnerGender)) {
                    return found(gendered(subject, "свёкор", "свекровь", "родитель мужа"));
                }
                if (PersonGender.FEMALE.equals(partnerGender)) {
                    return found(gendered(subject, "тесть", "тёща", "родитель жены"));
                }
                return found(gendered(subject, "отец супруга или супруги", "мать супруга или супруги", "родитель супруга или супруги"));
            }
            if (isParent(state, referencePartnerId, subjectId)) {
                return found(gendered(subject, "пасынок", "падчерица", "ребёнок супруга или супруги"));
            }
            if (areSiblings(state, subjectId, referencePartnerId)) {
                String partnerGender = PersonGender.resolve(state.people.get(referencePartnerId));
                if (PersonGender.MALE.equals(partnerGender)) {
                    return found(gendered(subject, "деверь", "золовка", "брат или сестра мужа"));
                }
                if (PersonGender.FEMALE.equals(partnerGender)) {
                    return found(gendered(subject, "шурин", "свояченица", "брат или сестра жены"));
                }
                return found(gendered(subject, "брат супруга или супруги", "сестра супруга или супруги", "брат или сестра супруга или супруги"));
            }
            return found(relationToPartner.label + " супруга или супруги");
        }
        return notFound();
    }

    private static Map<String, Integer> ancestorDepths(TreeState state, String startId) {
        Map<String, Integer> depths = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        depths.put(startId, 0);
        queue.add(startId);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            int depth = depths.get(current);
            if (depth >= MAX_GENERATIONS) continue;
            for (String parent : ACTIVE_INDEX.get().parents.getOrDefault(current,Collections.emptySet())) {
                if (depths.containsKey(parent)) continue;
                depths.put(parent,depth+1); queue.add(parent);
            }
        }
        return depths;
    }

    private static CommonAncestor nearestCommonAncestor(
        Map<String, Integer> subjectAncestors,
        Map<String, Integer> referenceAncestors
    ) {
        CommonAncestor best = new CommonAncestor();
        int bestTotal = Integer.MAX_VALUE;
        int bestMax = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> entry : subjectAncestors.entrySet()) {
            Integer referenceDepth = referenceAncestors.get(entry.getKey());
            if (referenceDepth == null) continue;
            int subjectDepth = entry.getValue();
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

    private static SiblingAncestors nearestSiblingAncestors(
        TreeState state,
        Map<String, Integer> subjectAncestors,
        Map<String, Integer> referenceAncestors
    ) {
        SiblingAncestors best = new SiblingAncestors();
        int bestTotal = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> subject : subjectAncestors.entrySet()) {
            for (Map.Entry<String, Integer> reference : referenceAncestors.entrySet()) {
                if (!areExplicitSiblings(state, subject.getKey(), reference.getKey())) continue;
                int total = subject.getValue() + reference.getValue();
                if (total < bestTotal) {
                    best.subjectDepth = subject.getValue();
                    best.referenceDepth = reference.getValue();
                    bestTotal = total;
                }
            }
        }
        return best;
    }

    private static boolean isParent(TreeState state, String parentId, String childId) {
        for (Relation relation : state.links) {
            if ("parent".equals(relation.type)
                && parentId.equals(relation.from)
                && childId.equals(relation.to)) return true;
        }
        return false;
    }

    private static boolean arePartners(TreeState state, String firstId, String secondId) {
        for (Relation relation : state.links) {
            if (!"partner".equals(relation.type) && !"family".equals(relation.type)) continue;
            if (connects(relation, firstId, secondId)) return true;
        }
        return false;
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
        for (Relation relation : state.links) {
            if ("sibling".equals(relation.type) && connects(relation, firstId, secondId)) return true;
        }
        return false;
    }

    private static boolean connects(Relation relation, String firstId, String secondId) {
        return (firstId.equals(relation.from) && secondId.equals(relation.to))
            || (firstId.equals(relation.to) && secondId.equals(relation.from));
    }

    private static Set<String> parentsOf(TreeState state, String childId) {
        return new LinkedHashSet<>(ACTIVE_INDEX.get().parents.getOrDefault(childId, Collections.emptySet()));
    }

    private static Set<String> partnersOf(TreeState state, String personId) {
        return new LinkedHashSet<>(ACTIVE_INDEX.get().partners.getOrDefault(personId, Collections.emptySet()));
    }

    private static String ancestorLabel(Person subject, int generations) {
        if (generations <= 1) return gendered(subject, "отец", "мать", "родитель");
        if (generations == 2) return gendered(subject, "дедушка", "бабушка", "дедушка или бабушка");
        if (generations == 3) return gendered(subject, "прадедушка", "прабабушка", "прадедушка или прабабушка");
        return gendered(subject, "предок", "предок", "предок") + " в " + generations + "-м поколении";
    }

    private static String descendantLabel(Person subject, int generations) {
        if (generations <= 1) return gendered(subject, "сын", "дочь", "ребёнок");
        if (generations == 2) return gendered(subject, "внук", "внучка", "внук или внучка");
        if (generations == 3) return gendered(subject, "правнук", "правнучка", "правнук или правнучка");
        return gendered(subject, "потомок", "потомок", "потомок") + " в " + generations + "-м поколении";
    }

    /** Depths measured from a pair of siblings, including the siblings themselves at zero. */
    private static String collateralLabel(Person subject, int subjectDepth, int referenceDepth, boolean terminology) {
        int degree = Math.min(subjectDepth, referenceDepth);
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
        // Explicit descriptions preserve the number of generations without ambiguous rare terms.
        if (younger) {
            String relatives = degree == 0 ? "брата или сестры"
                : stem + "ого брата или " + stem + "ой сестры";
            return descendantLabel(subject, difference) + " " + relatives;
        }
        String ancestor = difference == 2 ? "дедушки или бабушки"
            : difference == 3 ? "прадедушки или прабабушки" : "предка в " + difference + "-м поколении";
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

    private static String buildPathDescription(TreeState state, String startId, String endId) {
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
            for (String neighbor : neighborsOf(state, current)) {
                if (depth.containsKey(neighbor)) continue;
                depth.put(neighbor, currentDepth + 1);
                previous.put(neighbor, current);
                queue.add(neighbor);
            }
        }
        if (!depth.containsKey(endId)) return "";
        List<String> ids = new ArrayList<>();
        String current = endId;
        ids.add(current);
        while (!current.equals(startId)) {
            current = previous.get(current);
            if (current == null) return "";
            ids.add(current);
        }
        Collections.reverse(ids);
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            Person person = state.people.get(id);
            names.add(person == null || person.name == null || person.name.trim().isEmpty()
                ? "Без имени"
                : person.name.trim());
        }
        return "Цепочка в дереве: " + String.join(" → ", names);
    }

    private static Set<String> neighborsOf(TreeState state, String personId) {
        return new LinkedHashSet<>(ACTIVE_INDEX.get().adjacent.getOrDefault(personId, Collections.emptySet()));
    }

    private static Description found(String label) {
        return new Description(true, label);
    }

    private static Description notFound() {
        return new Description(false, "родство не найдено");
    }
}
