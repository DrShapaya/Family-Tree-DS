package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FamilyLineRouterTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void parentPairUsesOneUnionAndOneChildBus() {
        TreeState state = new TreeState();
        Person father = person(state, "father", PersonGender.MALE, 400f, 400f);
        Person mother = person(state, "mother", PersonGender.FEMALE, 720f, 400f);
        person(state, "childA", PersonGender.UNKNOWN, 400f, 880f);
        person(state, "childB", PersonGender.UNKNOWN, 720f, 880f);
        relation(state, "couple", "partner", father.id, mother.id);
        relation(state, "fa", "parent", father.id, "childA");
        relation(state, "ma", "parent", mother.id, "childA");
        relation(state, "fb", "parent", father.id, "childB");
        relation(state, "mb", "parent", mother.id, "childB");

        FamilyLineRouter.Plan plan = FamilyLineRouter.build(
            state,
            TreeLayoutEngine.CARD_W,
            TreeLayoutEngine.CARD_H,
            TreeLayoutEngine.GRID);

        assertEquals(1, plan.routes.size());
        FamilyLineRouter.FamilyRoute route = plan.routes.get(0);
        assertEquals("father", route.parentIds.get(0));
        assertEquals("mother", route.parentIds.get(1));
        assertEquals(700f, route.unionX, EPSILON);
        assertEquals(720f, route.busY, EPSILON);
        assertEquals(4, route.relationIds.size());
        assertTrue(plan.consumes(state.links.get(0)));
        assertEquals(6, route.segments.size());

        FamilyLineRouter.Segment fatherSide = route.segments.get(0);
        FamilyLineRouter.Segment motherSide = route.segments.get(1);
        assertTrue(fatherSide.x1 < route.unionX);
        assertTrue(motherSide.x1 > route.unionX);
        assertEquals(route.unionX, fatherSide.x2, EPSILON);
        assertEquals(route.unionX, motherSide.x2, EPSILON);
    }

    @Test
    public void repeatedPartnershipsUseSeparateAnchorsAndBusLanes() {
        TreeState state = new TreeState();
        Person father = person(state, "father", PersonGender.MALE, 720f, 400f);
        Person firstMother = person(state, "motherA", PersonGender.FEMALE, 1040f, 400f);
        Person secondMother = person(state, "motherB", PersonGender.FEMALE, 1360f, 400f);
        person(state, "childA", PersonGender.UNKNOWN, 880f, 880f);
        person(state, "childA2", PersonGender.UNKNOWN, 1040f, 880f);
        person(state, "childB", PersonGender.UNKNOWN, 1360f, 880f);
        relation(state, "pa", "partner", father.id, firstMother.id);
        relation(state, "pb", "partner", father.id, secondMother.id);
        relation(state, "fa", "parent", father.id, "childA");
        relation(state, "ma", "parent", firstMother.id, "childA");
        relation(state, "fa2", "parent", father.id, "childA2");
        relation(state, "ma2", "parent", firstMother.id, "childA2");
        relation(state, "fb", "parent", father.id, "childB");
        relation(state, "mb", "parent", secondMother.id, "childB");

        FamilyLineRouter.Plan plan = FamilyLineRouter.build(
            state,
            TreeLayoutEngine.CARD_W,
            TreeLayoutEngine.CARD_H,
            TreeLayoutEngine.GRID);

        assertEquals(2, plan.routes.size());
        assertFalse(Math.abs(plan.routes.get(0).unionX - plan.routes.get(1).unionX) < EPSILON);
        assertFalse(Math.abs(plan.routes.get(0).busY - plan.routes.get(1).busY) < EPSILON);
        FamilyLineRouter.FamilyRoute distantUnion = plan.routes.stream()
            .filter(route -> route.parentIds.contains("motherB"))
            .findFirst()
            .orElseThrow();
        for (FamilyLineRouter.Segment segment : distantUnion.segments) {
            assertFalse(segmentCrossesCard(segment, firstMother));
        }
    }

    @Test
    public void candidateRepairsReversedFatherAndMother() {
        TreeState state = new TreeState();
        Person father = person(state, "father", PersonGender.MALE, 720f, 400f);
        Person mother = person(state, "mother", PersonGender.FEMALE, 400f, 400f);
        person(state, "child", PersonGender.UNKNOWN, 560f, 880f);
        relation(state, "f", "parent", father.id, "child");
        relation(state, "m", "parent", mother.id, "child");

        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutSnapshot initial = LayoutSnapshot.capture(state);
        LayoutScorer.Score before = new LayoutScorer(new LayoutConstraints()).score(
            graph,
            initial,
            initial,
            LayoutWeights.defaults());
        assertTrue(before.wrongSideError > 0d);
        LayoutOperation repair = new ParentRoleCandidateGenerator().generate(
            graph,
            initial).get(0);
        LayoutSnapshot repaired = repair.apply(initial);
        assertTrue(repaired.positionOf("father").x < repaired.positionOf("mother").x);
        assertTrue(new LayoutConstraints().validate(
            graph,
            repaired).isValid());
    }

    private static Person person(
        TreeState state,
        String id,
        String gender,
        float x,
        float y
    ) {
        Person person = new Person(id);
        person.name = id;
        person.gender = gender;
        person.genderManual = true;
        person.x = x;
        person.y = y;
        state.people.put(id, person);
        return person;
    }

    @Test
    public void movingChildToParentsRowOrAboveKeepsSharedRouteAndRelations() {
        for (float childY : new float[]{400f, -80f, 600f}) {
            for (float childX : new float[]{400f, 560f, 1040f}) {
                TreeState state = new TreeState();
                person(state, "father", PersonGender.MALE, 400f, 400f);
                person(state, "mother", PersonGender.FEMALE, 720f, 400f);
                Person child = person(state, "child", PersonGender.UNKNOWN, childX, childY);
                person(state, "sibling", PersonGender.UNKNOWN, 1040f, 880f);
                relation(state, "pair", "partner", "father", "mother");
                relation(state, "f", "parent", "father", "child");
                relation(state, "m", "parent", "mother", "child");
                relation(state, "fs", "parent", "father", "sibling");
                relation(state, "ms", "parent", "mother", "sibling");
                FamilyLineRouter.Plan plan = FamilyLineRouter.build(state,
                    TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID);
                assertEquals(1, plan.routes.size());
                assertTrue(plan.routes.get(0).routedChildIds.contains("child"));
                assertEquals(4, plan.routes.get(0).relationIds.size());
                for (Relation link : state.links) assertTrue(plan.consumes(link));
                for (FamilyLineRouter.Segment segment : plan.routes.get(0).segments) {
                    assertTrue(Math.abs(segment.x1 - segment.x2) < EPSILON
                        || Math.abs(segment.y1 - segment.y2) < EPSILON);
                }
                // Even when the child was moved directly above a parent,
                // its drop must go around that parent's card.
                if (childY != 400f || childX == 1040f) {
                    for (FamilyLineRouter.Segment segment : plan.routes.get(0).segments) {
                        for (Person card : state.people.values()) {
                            if ("child".equals(segment.personId)) {
                                assertFalse("Moved branch cuts " + card.id, segmentCrossesCard(segment, card));
                            }
                        }
                    }
                }
                assertEquals("centered", state.familyLineMode);
                assertEquals(5, state.links.size());
                child.y = 880f;
                FamilyLineRouter.Plan restored = FamilyLineRouter.build(state,
                    TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID);
                assertEquals(1, restored.routes.size());
                assertTrue(restored.routes.get(0).routedChildIds.isEmpty());
            }
        }
    }

    @Test
    public void movingOneBrotherChangesOnlyHisAttachment() {
        TreeState state = new TreeState();
        person(state, "father", PersonGender.MALE, 400f, 400f);
        person(state, "mother", PersonGender.FEMALE, 720f, 400f);
        Person brother = person(state, "brother", PersonGender.UNKNOWN, 80f, 880f);
        person(state, "sister", PersonGender.UNKNOWN, 1040f, 880f);
        person(state, "sibling", PersonGender.UNKNOWN, 1360f, 880f);
        relation(state, "pair", "partner", "father", "mother");
        for (String child : new String[]{"brother", "sister", "sibling"}) {
            relation(state, "f-" + child, "parent", "father", child);
            relation(state, "m-" + child, "parent", "mother", child);
        }
        FamilyLineRouter.FamilyRoute before = FamilyLineRouter.build(state,
            TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID).routes.get(0);
        for (float y : new float[]{600f, 400f, -80f, 880f}) {
            brother.y = y;
            FamilyLineRouter.FamilyRoute after = FamilyLineRouter.build(state,
                TreeLayoutEngine.CARD_W, TreeLayoutEngine.CARD_H, TreeLayoutEngine.GRID).routes.get(0);
            assertEquals(before.unionX, after.unionX, EPSILON);
            assertEquals(before.unionY, after.unionY, EPSILON);
            assertEquals(before.busY, after.busY, EPSILON);
            assertEquals(unchangedSegments(before, "brother"), unchangedSegments(after, "brother"));
            if (y < 880f) {
                FamilyLineRouter.Segment last = after.segments.get(after.segments.size() - 1);
                // The moved brother joins the existing common centre.
                for (FamilyLineRouter.Segment segment : after.segments) {
                    if ("brother".equals(segment.personId)) last = segment;
                }
                assertEquals(after.unionX, last.x2, EPSILON);
                assertEquals(after.busY, last.y2, EPSILON);
            }
        }
    }

    private static java.util.List<String> unchangedSegments(FamilyLineRouter.FamilyRoute route, String movedId) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (FamilyLineRouter.Segment segment : route.segments) {
            if (!movedId.equals(segment.personId)) {
                result.add(segment.personId + ":" + segment.x1 + "," + segment.y1 + ":" + segment.x2 + "," + segment.y2);
            }
        }
        return result;
    }

    private static void relation(
        TreeState state,
        String id,
        String type,
        String from,
        String to
    ) {
        state.links.add(new Relation(id, type, from, to));
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
            return segment.y1 > top && segment.y1 < bottom
                && Math.max(segment.x1, segment.x2) > left
                && Math.min(segment.x1, segment.x2) < right;
        }
        if (Math.abs(segment.x1 - segment.x2) < EPSILON) {
            return segment.x1 > left && segment.x1 < right
                && Math.max(segment.y1, segment.y2) > top
                && Math.min(segment.y1, segment.y2) < bottom;
        }
        return false;
    }
}
