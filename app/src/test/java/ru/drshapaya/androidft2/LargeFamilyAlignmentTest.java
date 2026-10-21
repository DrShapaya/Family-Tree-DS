package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Several married siblings bring independent parent branches into the same rows. */
public final class LargeFamilyAlignmentTest {
    @Test
    public void spouseAncestryFollowsEachMarriedChildAcrossWideGenerations() {
        TreeState state = new TreeState();
        person(state, "rootFather");
        person(state, "rootMother");
        partner(state, "rootFather", "rootMother");
        for (int index = 0; index < 8; index++) {
            String child = "child" + index;
            String spouse = "spouse" + index;
            String father = "spouseFather" + index;
            String mother = "spouseMother" + index;
            person(state, father);
            person(state, mother);
            person(state, spouse);
            person(state, child);
            parents(state, father, mother, spouse);
            parents(state, "rootFather", "rootMother", child);
            partner(state, child, spouse);
            for (int grandchild = 0; grandchild < 2; grandchild++) {
                String id = "grandchild" + index + "_" + grandchild;
                person(state, id);
                parents(state, child, spouse, id);
            }
        }
        state.rootId = "rootFather";

        TreeLayoutEngine.layoutFresh(state);

        assertEquals(50, state.people.size());
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
        for (int index = 0; index < 8; index++) {
            float parentsCenter = center(state, "spouseFather" + index,
                "spouseMother" + index);
            float spouseCenter = center(state, "spouse" + index);
            assertTrue("spouse ancestry " + index + " is too far from its child: "
                + parentsCenter + " vs " + spouseCenter,
                Math.abs(parentsCenter - spouseCenter) <= TreeLayoutEngine.GRID * 3f);
        }
    }

    private static void person(TreeState state, String id) {
        Person person = new Person(id);
        person.name = id;
        person.gender = id.endsWith("Father") || id.contains("Father")
            ? PersonGender.MALE : id.endsWith("Mother") || id.contains("Mother")
                ? PersonGender.FEMALE : PersonGender.UNKNOWN;
        state.people.put(id, person);
    }

    private static void partner(TreeState state, String first, String second) {
        state.links.add(new Relation("partner:" + first + ":" + second,
            "partner", first, second, "right"));
    }

    private static void parents(TreeState state, String first, String second, String child) {
        state.links.add(new Relation("parent:" + first + ":" + child,
            "parent", first, child));
        state.links.add(new Relation("parent:" + second + ":" + child,
            "parent", second, child));
    }

    private static float center(TreeState state, String... ids) {
        float sum = 0f;
        for (String id : ids) sum += state.people.get(id).x + TreeLayoutEngine.CARD_W / 2f;
        return sum / ids.length;
    }
}
