package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Relationship choices used by the add-partner and add-children flows. */
final class FamilyRelationPolicy {
    private FamilyRelationPolicy() {}

    static List<String> partnerIds(TreeState state, String personId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (state == null || personId == null) return new ArrayList<>();
        for (Relation relation : state.links) {
            if (!("partner".equals(relation.type) || "family".equals(relation.type))) continue;
            if (personId.equals(relation.from) && state.people.containsKey(relation.to)) {
                result.add(relation.to);
            } else if (personId.equals(relation.to) && state.people.containsKey(relation.from)) {
                result.add(relation.from);
            }
        }
        return new ArrayList<>(result);
    }

    static List<String> childIds(TreeState state, String personId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (state == null || personId == null) return new ArrayList<>();
        for (Relation relation : state.links) {
            if ("parent".equals(relation.type)
                && personId.equals(relation.from)
                && state.people.containsKey(relation.to)) {
                result.add(relation.to);
            }
        }
        return new ArrayList<>(result);
    }

    static boolean askWhetherNewPartnerParentsExistingChildren(
        TreeState state,
        String personId
    ) {
        return !childIds(state, personId).isEmpty();
    }

    static boolean needsCoParentChoice(TreeState state, String personId) {
        return partnerIds(state, personId).size() > 1;
    }

    static String automaticCoParent(TreeState state, String personId) {
        List<String> partners = partnerIds(state, personId);
        return partners.size() == 1 ? partners.get(0) : "";
    }

    static String validChosenCoParent(
        TreeState state,
        String personId,
        String chosenId
    ) {
        if (chosenId == null || chosenId.isEmpty()) return "";
        return partnerIds(state, personId).contains(chosenId) ? chosenId : "";
    }
}
