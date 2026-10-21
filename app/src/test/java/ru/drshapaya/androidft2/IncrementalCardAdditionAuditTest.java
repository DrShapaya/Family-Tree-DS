package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Exercises the real one-card-at-a-time production layout path. */
public final class IncrementalCardAdditionAuditTest {
    private static final float EPSILON = 0.5f;

    @Test(timeout = 30_000L)
    public void complexTreeRemainsValidAfterEverySingleCardAddition() {
        Builder b = new Builder();
        b.root("root", PersonGender.MALE);
        b.partner("root_wife", PersonGender.FEMALE, "root");
        b.child("child_a", PersonGender.MALE, "root", "root_wife");
        b.child("child_b", PersonGender.FEMALE, "root", "root_wife");
        b.parent("root_father", PersonGender.MALE, "root");
        b.parent("root_mother", PersonGender.FEMALE, "root");
        b.parent("wife_father", PersonGender.MALE, "root_wife");
        b.parent("wife_mother", PersonGender.FEMALE, "root_wife");
        b.sibling("root_brother", PersonGender.MALE, "root");
        b.partner("brother_wife", PersonGender.FEMALE, "root_brother");
        b.child("brother_child_a", PersonGender.MALE, "root_brother", "brother_wife");
        b.child("brother_child_b", PersonGender.FEMALE, "root_brother", "brother_wife");
        b.partner("root_second_wife", PersonGender.FEMALE, "root");
        b.child("half_child_a", PersonGender.MALE, "root", "root_second_wife");
        b.child("half_child_b", PersonGender.FEMALE, "root", "root_second_wife");
        b.parent("second_wife_father", PersonGender.MALE, "root_second_wife");
        b.parent("second_wife_mother", PersonGender.FEMALE, "root_second_wife");
        b.sibling("second_wife_sister", PersonGender.FEMALE, "root_second_wife");
        b.partner("second_wife_sister_husband", PersonGender.MALE, "second_wife_sister");
        b.child("second_wife_sister_child", PersonGender.MALE,
            "second_wife_sister", "second_wife_sister_husband");
        b.partner("child_a_wife", PersonGender.FEMALE, "child_a");
        b.child("grandchild_a", PersonGender.FEMALE, "child_a", "child_a_wife");
        b.child("grandchild_b", PersonGender.MALE, "child_a", "child_a_wife");
        b.partner("child_b_husband", PersonGender.MALE, "child_b");
        b.child("grandchild_c", PersonGender.MALE, "child_b_husband", "child_b");
        b.partner("half_child_a_wife", PersonGender.FEMALE, "half_child_a");
        b.child("half_grandchild", PersonGender.FEMALE,
            "half_child_a", "half_child_a_wife");
        b.parent("child_a_wife_father", PersonGender.MALE, "child_a_wife");
        b.parent("child_a_wife_mother", PersonGender.FEMALE, "child_a_wife");
        b.sibling("child_a_wife_sister", PersonGender.FEMALE, "child_a_wife");
        b.partner("child_a_wife_sister_husband", PersonGender.MALE,
            "child_a_wife_sister");
        b.child("child_a_wife_sister_child", PersonGender.MALE,
            "child_a_wife_sister_husband", "child_a_wife_sister");
        b.partner("grandchild_a_husband", PersonGender.MALE, "grandchild_a");
        b.child("great_grandchild", PersonGender.FEMALE,
            "grandchild_a_husband", "grandchild_a");
        assertEquals(34, b.state.people.size());
    }

    @Test(timeout = 60_000L)
    public void multilevelTreeRemainsValidWhileCardsAreAddedOneAtATime() {
        Builder b = new Builder(false);
        b.root("wide_root", PersonGender.MALE);
        b.partner("wide_root_partner", PersonGender.FEMALE, "wide_root");
        List<Couple> generation = new ArrayList<>();
        generation.add(new Couple("wide_root", "wide_root_partner"));

        int serial = 0;
        for (int level = 1; level <= 4; level++) {
            List<Couple> next = new ArrayList<>();
            for (Couple parents : generation) {
                for (int childIndex = 0; childIndex < 2; childIndex++) {
                    String childId = "level_" + level + "_person_" + serial;
                    String partnerId = "level_" + level + "_partner_" + serial;
                    String childGender = (serial & 1) == 0
                        ? PersonGender.MALE
                        : PersonGender.FEMALE;
                    String partnerGender = PersonGender.MALE.equals(childGender)
                        ? PersonGender.FEMALE
                        : PersonGender.MALE;
                    b.child(childId, childGender, parents.first, parents.second);
                    b.partner(partnerId, partnerGender, childId);
                    next.add(new Couple(childId, partnerId));
                    serial++;
                }
            }
            generation = next;
        }

        assertEquals(62, b.state.people.size());

        TreeLayoutEngine.rebuildStepwise(b.state);
        audit(++b.step, "manual-arrange", b.state, false);
        LayoutSnapshot arrangedOnce = LayoutSnapshot.capture(b.state);
        TreeLayoutEngine.rebuildStepwise(b.state);
        for (String id : arrangedOnce.positions.keySet()) {
            assertEquals(arrangedOnce.positionOf(id).x, b.state.people.get(id).x, EPSILON);
            assertEquals(arrangedOnce.positionOf(id).y, b.state.people.get(id).y, EPSILON);
        }
    }

    private static final class Builder {
        final TreeState state = new TreeState();
        final boolean strictGeometry;
        int step;

        Builder() {
            this(true);
        }

        Builder(boolean strictGeometry) {
            this.strictGeometry = strictGeometry;
            state.autoArrangeOnAdd = true;
        }

        void root(String id, String gender) {
            add(id, gender, 11880f, 7920f);
            state.rootId = id;
            state.selectedId = id;
            audit(++step, "root:" + id, state, strictGeometry);
        }

        void partner(String id, String gender, String anchorId) {
            Person anchor = state.people.get(anchorId);
            add(id, gender, anchor.x + 320f, anchor.y);
            state.addRelation("partner", anchorId, id, "right");
            arrange(id, anchorId, "partner");
        }

        void child(String id, String gender, String firstParentId, String secondParentId) {
            Person anchor = state.people.get(firstParentId);
            add(id, gender, anchor.x, anchor.y + TreeLayoutEngine.LEVEL_GAP);
            state.addRelation("parent", firstParentId, id);
            if (secondParentId != null && !secondParentId.isEmpty()) {
                state.addRelation("parent", secondParentId, id);
            }
            for (String siblingId : siblingsWithSameParents(id)) {
                state.addRelation("sibling", siblingId, id);
            }
            arrange(id, firstParentId, "children");
        }

        void parent(String id, String gender, String childId) {
            Person child = state.people.get(childId);
            float side = PersonGender.MALE.equals(gender) ? -320f : 320f;
            add(id, gender, child.x + side, child.y - TreeLayoutEngine.LEVEL_GAP);
            Set<String> existing = parentsOf(childId);
            state.addRelation("parent", id, childId);
            if (existing.size() == 1) {
                state.addRelation("partner", existing.iterator().next(), id, "right");
            }
            arrange(id, childId, "parents");
        }

        void sibling(String id, String gender, String anchorId) {
            Person anchor = state.people.get(anchorId);
            add(id, gender, anchor.x + 320f, anchor.y);
            Set<String> parents = parentsOf(anchorId);
            for (String parentId : parents) state.addRelation("parent", parentId, id);
            state.addRelation("sibling", anchorId, id);
            arrange(id, anchorId, "siblings");
        }

        private void arrange(String id, String anchorId, String action) {
            TreeLayoutEngine.layoutAfterAddition(
                state,
                Collections.singleton(id),
                anchorId,
                action);
            audit(++step, action + ":" + id + "->" + anchorId, state, strictGeometry);
        }

        private void add(String id, String gender, float x, float y) {
            Person person = new Person(id);
            person.name = id;
            person.gender = gender;
            person.genderManual = true;
            person.x = snap(x);
            person.y = snap(y);
            state.people.put(id, person);
            state.selectedId = id;
        }

        private Set<String> parentsOf(String childId) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            for (Relation relation : state.links) {
                if ("parent".equals(relation.type) && childId.equals(relation.to)) {
                    result.add(relation.from);
                }
            }
            return result;
        }

        private List<String> siblingsWithSameParents(String childId) {
            Set<String> parents = parentsOf(childId);
            List<String> result = new ArrayList<>();
            for (String candidate : state.people.keySet()) {
                if (!candidate.equals(childId) && parents.equals(parentsOf(candidate))) {
                    result.add(candidate);
                }
            }
            return result;
        }
    }

    private static final class Couple {
        final String first;
        final String second;

        Couple(String first, String second) {
            this.first = first;
            this.second = second;
        }
    }

    private static void audit(int step, String label, TreeState state) {
        audit(step, label, state, true);
    }

    private static void audit(
        int step,
        String label,
        TreeState state,
        boolean strictGeometry
    ) {
        String prefix = "step " + step + " [" + label + "]: ";
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot snapshot = LayoutSnapshot.capture(state);
        LayoutConstraints.ValidationResult validation =
            new LayoutConstraints().validate(graph, snapshot);
        assertTrue(prefix + violations(validation), validation.isValid());
        assertEquals(prefix + "father/mother side", 0d,
            LayoutScorer.parentRoleSideError(graph, snapshot), EPSILON);

        for (FamilyLayoutGraph.ParentFamily family : graph.parentFamilies) {
            if (strictGeometry) {
                assertFamilyDirectionOrCenter(prefix, family, graph, snapshot);
            }
            assertCollateralSiblingsStayOutsidePartner(prefix, family, graph, snapshot);
        }

        FamilyLineRouter.Plan plan = FamilyLineRouter.build(
            state,
            TreeLayoutEngine.CARD_W,
            TreeLayoutEngine.CARD_H,
            TreeLayoutEngine.GRID);
        assertEquals(prefix + "unrouted parent families",
            graph.parentFamilies.size(), plan.routes.size());
        for (FamilyLineRouter.FamilyRoute route : plan.routes) {
            for (FamilyLineRouter.Segment segment : route.segments) {
                for (Person person : state.people.values()) {
                    assertTrue(prefix + route.id + " crosses card " + person.id,
                        !segmentCrossesCard(segment, person));
                }
            }
        }
    }

    private static void assertFamilyDirectionOrCenter(
        String prefix,
        FamilyLayoutGraph.ParentFamily family,
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        if (hasRepeatedParentUnion(family, graph)) return;
        BranchContour ancestry = BranchContour.from(
            snapshot,
            graph.lineageBranch(family.parents));
        boolean hasIndependentPartnerAncestry = false;
        for (String childId : family.children) {
            LayoutSnapshot.Position child = snapshot.positionOf(childId);
            if (child == null) continue;
            for (String partnerId : graph.partnersByPerson.getOrDefault(
                childId,
                Collections.emptySet())) {
                FamilyLayoutGraph.ParentFamily partnerFamily = graph.parentFamilyOf(partnerId);
                LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
                if (partnerFamily == null || partnerFamily == family || partner == null) continue;
                hasIndependentPartnerAncestry = true;
                if (family.parents.size() == 1 || family.children.size() > 1) continue;
                double outwardError = child.x < partner.x
                    ? AncestrySideRules.leftError(ancestry, child)
                    : AncestrySideRules.rightError(ancestry, child);
                assertEquals(prefix + family.id + " points into partner ancestry",
                    0d, outwardError, EPSILON);
            }
        }
        if (hasIndependentPartnerAncestry) return;

        double parentCenter = averageCenter(family.parents, snapshot);
        double childCenter = outerCenter(family.children, snapshot);
        assertTrue(prefix + family.id + " is off-center by "
                + Math.abs(parentCenter - childCenter)
                + " (parents=" + family.parents + " @ " + parentCenter
                + ", children=" + family.children + " @ " + childCenter + ")",
            Math.abs(parentCenter - childCenter) <= TreeLayoutEngine.CARD_W + EPSILON);
    }

    private static boolean hasRepeatedParentUnion(
        FamilyLayoutGraph.ParentFamily family,
        FamilyLayoutGraph graph
    ) {
        for (String parentId : family.parents) {
            int unionCount = 0;
            for (FamilyLayoutGraph.ParentFamily candidate : graph.parentFamilies) {
                if (candidate.parents.contains(parentId) && ++unionCount > 1) return true;
            }
        }
        return false;
    }

    private static void assertCollateralSiblingsStayOutsidePartner(
        String prefix,
        FamilyLayoutGraph.ParentFamily family,
        FamilyLayoutGraph graph,
        LayoutSnapshot snapshot
    ) {
        if (family.children.size() < 2) return;
        for (String childId : family.children) {
            if (!childId.equals(graph.rootId)) continue;
            LayoutSnapshot.Position child = snapshot.positionOf(childId);
            if (child == null) continue;
            for (String partnerId : graph.partnersByPerson.getOrDefault(
                childId,
                Collections.emptySet())) {
                FamilyLayoutGraph.ParentFamily partnerFamily = graph.parentFamilyOf(partnerId);
                LayoutSnapshot.Position partner = snapshot.positionOf(partnerId);
                if (partnerFamily == null || partnerFamily == family || partner == null) continue;
                for (String siblingId : family.children) {
                    if (siblingId.equals(childId)) continue;
                    LayoutSnapshot.Position sibling = snapshot.positionOf(siblingId);
                    if (sibling == null) continue;
                    if (child.x < partner.x) {
                        assertTrue(prefix + siblingId + " crossed behind partner " + partnerId,
                            sibling.x <= child.x + EPSILON);
                    } else {
                        assertTrue(prefix + siblingId + " crossed behind partner " + partnerId,
                            sibling.x + EPSILON >= child.x);
                    }
                }
            }
        }
    }

    private static boolean segmentCrossesCard(
        FamilyLineRouter.Segment segment,
        Person person
    ) {
        float left = person.x;
        float right = person.x + TreeLayoutEngine.CARD_W;
        float top = person.y;
        float bottom = person.y + TreeLayoutEngine.CARD_H;
        if (Math.abs(segment.y1 - segment.y2) < EPSILON) {
            return segment.y1 > top + EPSILON && segment.y1 < bottom - EPSILON
                && Math.max(segment.x1, segment.x2) > left + EPSILON
                && Math.min(segment.x1, segment.x2) < right - EPSILON;
        }
        if (Math.abs(segment.x1 - segment.x2) < EPSILON) {
            return segment.x1 > left + EPSILON && segment.x1 < right - EPSILON
                && Math.max(segment.y1, segment.y2) > top + EPSILON
                && Math.min(segment.y1, segment.y2) < bottom - EPSILON;
        }
        return false;
    }

    private static String violations(LayoutConstraints.ValidationResult result) {
        StringBuilder value = new StringBuilder();
        for (LayoutConstraints.Violation violation : result.violations) {
            if (value.length() > 0) value.append(", ");
            value.append(violation.code).append(':')
                .append(violation.firstId).append('/').append(violation.secondId);
        }
        return value.toString();
    }

    private static double averageCenter(
        Iterable<String> ids,
        LayoutSnapshot snapshot
    ) {
        double total = 0d;
        int count = 0;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null) continue;
            total += position.x + TreeLayoutEngine.CARD_W / 2d;
            count++;
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private static double outerCenter(
        Iterable<String> ids,
        LayoutSnapshot snapshot
    ) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (String id : ids) {
            LayoutSnapshot.Position position = snapshot.positionOf(id);
            if (position == null) continue;
            left = Math.min(left, position.x);
            right = Math.max(right, position.x + TreeLayoutEngine.CARD_W);
        }
        return left == Float.MAX_VALUE ? Double.NaN : (left + right) / 2d;
    }

    private static float snap(float value) {
        return Math.round(value / TreeLayoutEngine.GRID) * TreeLayoutEngine.GRID;
    }
}
