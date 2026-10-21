package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Repairs a reversed father/mother pair without changing its generation. */
final class ParentRoleCandidateGenerator implements LayoutCandidateGenerator {
    @Override
    public List<LayoutOperation> generate(FamilyLayoutGraph graph, LayoutSnapshot snapshot) {
        if (graph == null || snapshot == null) return Collections.emptyList();
        List<LayoutOperation> result = new ArrayList<>();
        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            String fatherId = uniqueParent(graph, family, PersonGender.MALE);
            String motherId = uniqueParent(graph, family, PersonGender.FEMALE);
            if (fatherId.isEmpty() || motherId.isEmpty()) continue;
            // A repeated partnership needs the dedicated multi-union operation;
            // swapping the shared card for one pair would reverse another pair.
            if (graph.partnerUnitsOf(fatherId).size() > 1
                || graph.partnerUnitsOf(motherId).size() > 1) continue;
            LayoutSnapshot.Position father = snapshot.positionOf(fatherId);
            LayoutSnapshot.Position mother = snapshot.positionOf(motherId);
            if (father == null || mother == null || father.x < mother.x) continue;
            result.add(new CompositeLayoutOperation(Arrays.asList(
                new ShiftLayoutOperation(
                    Collections.singleton(fatherId),
                    mother.x - father.x,
                    0f,
                    "father-to-left"),
                new ShiftLayoutOperation(
                    Collections.singleton(motherId),
                    father.x - mother.x,
                    0f,
                    "mother-to-right")),
                "parent-role-order:" + family.id));
        }
        return result;
    }

    private static String uniqueParent(
        FamilyLayoutGraph graph,
        FamilyLayoutGraph.ParentFamily family,
        String gender
    ) {
        String result = "";
        for (String id : family.parents) {
            FamilyLayoutGraph.PersonNode node = graph.people.get(id);
            if (node == null || !gender.equals(node.gender)) continue;
            if (!result.isEmpty()) return "";
            result = id;
        }
        return result;
    }
}
