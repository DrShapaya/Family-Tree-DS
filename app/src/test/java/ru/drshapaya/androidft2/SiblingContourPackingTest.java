package ru.drshapaya.androidft2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A shallow sibling may use the opening above two wider descendant branches. */
public final class SiblingContourPackingTest {
    @Test
    public void placesSingleChildInGapBetweenBroadBranches() {
        TreeState state = new TreeState();
        person(state, "parentA", 1680, 400);
        person(state, "parentB", 2000, 400);
        person(state, "branchA", 400, 880);
        person(state, "mateA", 720, 880);
        person(state, "single", 2000, 880);
        person(state, "branchB", 3000, 880);
        person(state, "mateB", 3320, 880);
        link(state, "partner", "parentA", "parentB");
        link(state, "partner", "branchA", "mateA");
        link(state, "partner", "branchB", "mateB");
        for (String child : new String[]{"branchA", "single", "branchB"}) {
            link(state, "parent", "parentA", child);
            link(state, "parent", "parentB", child);
        }
        for (int index = 0; index < 4; index++) {
            String a = "a" + index;
            String b = "b" + index;
            person(state, a, 400 + index * 320, 1360);
            person(state, b, 3000 + index * 320, 1360);
            link(state, "parent", "branchA", a);
            link(state, "parent", "mateA", a);
            link(state, "parent", "branchB", b);
            link(state, "parent", "mateB", b);
        }
        state.rootId = "parentA";
        FamilyLayoutGraph graph = FamilyLayoutGraph.from(state);
        LayoutConstraints constraints = new LayoutConstraints();
        assertTrue(constraints.validate(graph, LayoutSnapshot.capture(state)).isValid());

        float oldSpan = state.people.get("b3").x - state.people.get("a0").x;
        assertTrue(SiblingContourPacking.improve(state));

        assertTrue(constraints.validate(graph, LayoutSnapshot.capture(state)).isValid());
        assertTrue(state.people.get("mateA").x < state.people.get("single").x);
        assertTrue(state.people.get("single").x < state.people.get("branchB").x);
        assertTrue(state.people.get("b3").x - state.people.get("a0").x < oldSpan);
    }

    private static void person(TreeState state, String id, float x, float y) {
        Person person = new Person(id);
        person.name = id;
        person.x = x;
        person.y = y;
        state.people.put(id, person);
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + from + to, type, from, to));
    }
}
