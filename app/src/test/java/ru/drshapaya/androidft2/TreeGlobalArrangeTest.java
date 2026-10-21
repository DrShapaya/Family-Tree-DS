package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TreeGlobalArrangeTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void fullArrangeIgnoresPreviousPileAndIsRepeatable() {
        TreeState state = branchedTree();
        TreeLayoutEngine.layout(state);

        Map<String, Position> expected = positions(state);
        int expectedWidth = state.workspaceWidth;
        int expectedHeight = state.workspaceHeight;

        int index = 0;
        for (Person person : state.people.values()) {
            // Deliberately emulate both common bad inputs: a pile and a very
            // wide row. Neither is allowed to affect the structural result.
            person.x = index % 2 == 0 ? 80f : 200_000f + index * 320f;
            person.y = index % 3 == 0 ? 80f : 120f;
            index++;
        }
        state.workspaceWidth = TreeLayoutEngine.MAX_SURFACE_SIZE;
        state.workspaceHeight = TreeLayoutEngine.MAX_SURFACE_SIZE;

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));
        assertGeometryEquals(expected, expectedWidth, expectedHeight, state);
        assertNoOverlaps(state);

        Map<String, Position> firstArrange = positions(state);
        assertTrue(TreeLayoutEngine.layoutFresh(state, null));
        assertGeometryEquals(firstArrange, expectedWidth, expectedHeight, state);
    }

    @Test
    public void fullArrangeKeepsExplicitlyPinnedCard() {
        TreeState state = branchedTree();
        Person root = state.people.get("child");
        root.x = 3_200f;
        root.y = 4_000f;
        root.pinned = true;

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));

        assertEquals(3_200f, root.x, EPSILON);
        assertEquals(4_000f, root.y, EPSILON);
    }

    @Test(timeout = 30_000L)
    public void fullArrangeOfTwelveHundredPeopleMatchesImportGeometry() throws Exception {
        TreeState state = TreeStore.parseGedcom(largeConnectedGedcom(1_200));
        Map<String, Position> imported = positions(state);
        int importedWidth = state.workspaceWidth;
        int importedHeight = state.workspaceHeight;

        int index = 0;
        for (Person person : state.people.values()) {
            person.x = index++ * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID);
            person.y = 0f;
        }
        state.workspaceWidth = TreeLayoutEngine.MAX_SURFACE_SIZE;
        state.workspaceHeight = (int) TreeLayoutEngine.SURFACE_H;

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));

        assertGeometryEquals(imported, importedWidth, importedHeight, state);
        assertNoOverlaps(state);
    }

    @Test
    public void unknownPartnersKeepAncestryAndSiblingBranchesOnTheirFamilySide() {
        TreeState state = ancestrySideTree();

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));

        assertTrue("the partner with the larger ancestry must stay left",
            state.people.get("eight").x < state.people.get("seven").x);
        assertTrue("eight's parent branch must stay left of seven's branch",
            maxRight(state, "eightParentA", "eightParentB")
                < minX(state, "sevenParentA", "sevenParentB"));
        assertTrue("left partner's parents must stay outside the couple",
            maxRight(state, "eightParentA", "eightParentB")
                <= state.people.get("eight").x);
        assertTrue("right partner's parents must stay outside the couple",
            minX(state, "sevenParentA", "sevenParentB")
                >= state.people.get("seven").x + TreeLayoutEngine.CARD_W);

        assertTrue("the ancestor-bearing partner must stay left",
            state.people.get("eightParentA").x < state.people.get("eightParentB").x);
        assertTrue("left partner's siblings must remain outside on the left",
            maxRight(state, "leftSibling1", "leftSibling2", "leftSibling3")
                < state.people.get("eightParentA").x);
        assertTrue("right partner's siblings must remain outside on the right",
            minX(state, "rightSibling1", "rightSibling2")
                > state.people.get("eightParentB").x + TreeLayoutEngine.CARD_W);

        float childLeft = minX(state, "child1", "child2", "child3");
        float childRight = maxRight(state, "child1", "child2", "child3");
        assertTrue("siblings from one family must form one compact block",
            childRight - childLeft <= 3f * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 5f));
        assertNoOverlaps(state);
    }

    @Test
    public void childFamilyBlocksFollowTheirParentFamilyOrder() {
        TreeState state = twoNeighbouringChildFamilies();

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));

        float firstParents = center(state, "firstFather", "firstMother");
        float secondParents = center(state, "secondFather", "secondMother");
        float firstChildren = center(state, "child1", "child2", "child3");
        float secondChildren = center(state, "child9", "child10");
        assertTrue("child-family order must match parent-family order",
            (firstParents - secondParents) * (firstChildren - secondChildren) > 0f);
        if (firstParents < secondParents) {
            assertTrue(maxRight(state, "child1", "child2", "child3")
                < minX(state, "child9", "child10"));
        } else {
            assertTrue(maxRight(state, "child9", "child10")
                < minX(state, "child1", "child2", "child3"));
        }
        assertNoOverlaps(state);
    }

    @Test
    public void reportedNestedCouplesKeepParentAndChildGroupsAligned() {
        TreeState state = reportedNestedCouples();
        assertEquals(58, state.links.size());

        assertTrue(TreeLayoutEngine.layoutFresh(state, null));

        assertTrue(state.people.get("n18").x < state.people.get("n19").x);
        assertEquals("parents of 18 must sit over their own child",
            center(state, "n18"), center(state, "n10", "n11"), EPSILON);
        assertEquals("the three children must center below their parents",
            center(state, "n16", "n17"), center(state, "n23", "n24", "n25"), EPSILON);
        assertEquals("collateral relatives need the normal branch gap",
            TreeLayoutEngine.GRID * 5f,
            state.people.get("n8").x - state.people.get("n7").x - TreeLayoutEngine.CARD_W,
            EPSILON);
        assertEquals("the new sibling needs the normal branch gap",
            TreeLayoutEngine.GRID * 5f,
            state.people.get("n14").x - state.people.get("n26").x - TreeLayoutEngine.CARD_W,
            EPSILON);
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (Person person : state.people.values()) {
            left = Math.min(left, person.x);
            right = Math.max(right, person.x + TreeLayoutEngine.CARD_W);
        }
        assertTrue("side constraints must not stretch this family into a long row: "
            + (right - left), right - left <= 6_000f);
        assertNoOverlaps(state);
    }

    private static TreeState branchedTree() {
        TreeState state = new TreeState();
        person(state, "paternalGrandfather");
        person(state, "paternalGrandmother");
        person(state, "maternalGrandfather");
        person(state, "maternalGrandmother");
        person(state, "father");
        person(state, "mother");
        person(state, "paternalUncle");
        person(state, "maternalAunt");
        person(state, "child");
        person(state, "sibling");
        person(state, "childPartner");
        person(state, "grandchildA");
        person(state, "grandchildB");

        partner(state, "paternalGrandfather", "paternalGrandmother");
        parents(state, "paternalGrandfather", "paternalGrandmother", "father");
        parents(state, "paternalGrandfather", "paternalGrandmother", "paternalUncle");
        partner(state, "maternalGrandfather", "maternalGrandmother");
        parents(state, "maternalGrandfather", "maternalGrandmother", "mother");
        parents(state, "maternalGrandfather", "maternalGrandmother", "maternalAunt");
        partner(state, "father", "mother");
        parents(state, "father", "mother", "child");
        parents(state, "father", "mother", "sibling");
        partner(state, "child", "childPartner");
        parents(state, "child", "childPartner", "grandchildA");
        parents(state, "child", "childPartner", "grandchildB");
        state.rootId = "child";
        return state;
    }

    private static TreeState ancestrySideTree() {
        TreeState state = new TreeState();
        String[] ids = {
            "seven", "eight", "child1", "child2", "child3",
            "sevenParentA", "sevenParentB", "sevenSibling",
            "eightParentA", "eightParentB",
            "grandparentA", "grandparentB",
            "leftSibling1", "leftSibling2", "leftSibling3",
            "rightSibling1", "rightSibling2"
        };
        for (String id : ids) person(state, id);

        partner(state, "seven", "eight");
        parents(state, "seven", "eight", "child1");
        parents(state, "seven", "eight", "child2");
        parents(state, "seven", "eight", "child3");
        sibling(state, "child1", "child2");
        sibling(state, "child1", "child3");
        sibling(state, "child2", "child3");

        partner(state, "sevenParentA", "sevenParentB");
        parents(state, "sevenParentA", "sevenParentB", "seven");
        parents(state, "sevenParentA", "sevenParentB", "sevenSibling");
        sibling(state, "seven", "sevenSibling");

        partner(state, "eightParentA", "eightParentB");
        parents(state, "eightParentA", "eightParentB", "eight");
        partner(state, "grandparentA", "grandparentB");
        parents(state, "grandparentA", "grandparentB", "eightParentA");
        parents(state, "grandparentA", "grandparentB", "leftSibling1");
        parents(state, "grandparentA", "grandparentB", "leftSibling2");
        parents(state, "grandparentA", "grandparentB", "leftSibling3");
        sibling(state, "eightParentA", "leftSibling1");
        sibling(state, "eightParentA", "leftSibling2");
        sibling(state, "eightParentA", "leftSibling3");
        sibling(state, "leftSibling1", "leftSibling2");
        sibling(state, "leftSibling2", "leftSibling3");
        sibling(state, "eightParentB", "rightSibling1");
        sibling(state, "eightParentB", "rightSibling2");
        sibling(state, "rightSibling1", "rightSibling2");

        state.rootId = "child2";
        return state;
    }

    private static TreeState twoNeighbouringChildFamilies() {
        TreeState state = new TreeState();
        String[] ids = {
            "firstFather", "firstMother", "secondFather", "secondMother",
            "child9", "child10", "child1", "child2", "child3"
        };
        for (String id : ids) person(state, id);
        state.people.get("firstFather").gender = PersonGender.MALE;
        state.people.get("firstMother").gender = PersonGender.FEMALE;
        state.people.get("secondFather").gender = PersonGender.MALE;
        state.people.get("secondMother").gender = PersonGender.FEMALE;

        partner(state, "firstFather", "firstMother");
        partner(state, "secondFather", "secondMother");
        sibling(state, "firstFather", "secondFather");

        // Add the second family's children first to ensure insertion order
        // cannot decide which parent pair they appear below.
        parents(state, "secondFather", "secondMother", "child9");
        parents(state, "secondFather", "secondMother", "child10");
        sibling(state, "child9", "child10");
        parents(state, "firstFather", "firstMother", "child1");
        parents(state, "firstFather", "firstMother", "child2");
        parents(state, "firstFather", "firstMother", "child3");
        sibling(state, "child1", "child2");
        sibling(state, "child1", "child3");
        sibling(state, "child2", "child3");
        state.rootId = "child2";
        return state;
    }

    private static TreeState reportedNestedCouples() {
        TreeState state = new TreeState();
        for (int number = 1; number <= 26; number++) {
            person(state, "n" + number);
        }
        state.people.get("n19").gender = PersonGender.FEMALE;
        state.people.get("n19").genderManual = true;

        partner(state, "n16", "n17");
        parents(state, "n16", "n17", "n23");
        parents(state, "n16", "n17", "n24");
        parents(state, "n16", "n17", "n25");
        partner(state, "n12", "n13");
        parents(state, "n12", "n13", "n17");
        parents(state, "n12", "n13", "n19");
        parents(state, "n12", "n13", "n20");
        partner(state, "n6", "n7");
        parents(state, "n6", "n7", "n16");
        parents(state, "n6", "n7", "n14");
        parents(state, "n6", "n7", "n26");
        sibling(state, "n16", "n14");
        sibling(state, "n16", "n26");
        sibling(state, "n14", "n26");
        partner(state, "n14", "n15");
        parents(state, "n14", "n15", "n21");
        parents(state, "n14", "n15", "n22");
        partner(state, "n1", "n2");
        parents(state, "n1", "n2", "n6");
        parents(state, "n1", "n2", "n3");
        parents(state, "n1", "n2", "n4");
        parents(state, "n1", "n2", "n5");
        partner(state, "n18", "n19");
        partner(state, "n10", "n11");
        parents(state, "n10", "n11", "n18");
        sibling(state, "n6", "n3");
        sibling(state, "n6", "n4");
        sibling(state, "n3", "n4");
        sibling(state, "n3", "n5");
        sibling(state, "n4", "n5");
        sibling(state, "n5", "n6");
        sibling(state, "n7", "n8");
        sibling(state, "n7", "n9");
        sibling(state, "n8", "n9");
        sibling(state, "n17", "n19");
        sibling(state, "n17", "n20");
        sibling(state, "n19", "n20");
        sibling(state, "n21", "n22");
        sibling(state, "n23", "n24");
        sibling(state, "n23", "n25");
        sibling(state, "n24", "n25");
        state.rootId = "n23";
        return state;
    }

    private static String largeConnectedGedcom(int peopleCount) {
        StringBuilder gedcom = new StringBuilder("0 HEAD\n1 CHAR UTF-8\n");
        for (int index = 0; index < peopleCount; index++) {
            gedcom.append("0 @I").append(index).append("@ INDI\n")
                .append("1 NAME Person ").append(index).append(" /Test/\n");
        }
        for (int family = 0; family * 3 + 1 < peopleCount; family++) {
            int firstParent = family * 3;
            int secondParent = firstParent + 1;
            gedcom.append("0 @F").append(family).append("@ FAM\n")
                .append("1 HUSB @I").append(firstParent).append("@\n")
                .append("1 WIFE @I").append(secondParent).append("@\n");
            for (int child = firstParent + 2;
                 child <= firstParent + 4 && child < peopleCount;
                 child++) {
                gedcom.append("1 CHIL @I").append(child).append("@\n");
            }
        }
        return gedcom.append("0 TRLR\n").toString();
    }

    private static void person(TreeState state, String id) {
        Person person = new Person(id);
        person.name = id;
        state.people.put(id, person);
    }

    private static void partner(TreeState state, String first, String second) {
        link(state, "partner", first, second);
    }

    private static void sibling(TreeState state, String first, String second) {
        link(state, "sibling", first, second);
    }

    private static void parents(TreeState state, String first, String second, String child) {
        link(state, "parent", first, child);
        link(state, "parent", second, child);
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
    }

    private static Map<String, Position> positions(TreeState state) {
        Map<String, Position> result = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            result.put(person.id, new Position(person.x, person.y));
        }
        return result;
    }

    private static float minX(TreeState state, String... ids) {
        float result = Float.MAX_VALUE;
        for (String id : ids) result = Math.min(result, state.people.get(id).x);
        return result;
    }

    private static float maxRight(TreeState state, String... ids) {
        float result = -Float.MAX_VALUE;
        for (String id : ids) {
            result = Math.max(result, state.people.get(id).x + TreeLayoutEngine.CARD_W);
        }
        return result;
    }

    private static float center(TreeState state, String... ids) {
        float total = 0f;
        for (String id : ids) {
            total += state.people.get(id).x + TreeLayoutEngine.CARD_W / 2f;
        }
        return total / ids.length;
    }

    private static void assertGeometryEquals(
        Map<String, Position> expected,
        int expectedWidth,
        int expectedHeight,
        TreeState actual
    ) {
        assertEquals(expectedWidth, actual.workspaceWidth);
        assertEquals(expectedHeight, actual.workspaceHeight);
        assertEquals(expected.size(), actual.people.size());
        for (Map.Entry<String, Position> entry : expected.entrySet()) {
            Person person = actual.people.get(entry.getKey());
            assertFalse(Float.isNaN(person.x));
            assertFalse(Float.isNaN(person.y));
            assertEquals(entry.getValue().x, person.x, EPSILON);
            assertEquals(entry.getValue().y, person.y, EPSILON);
        }
    }

    private static void assertNoOverlaps(TreeState state) {
        Person[] people = state.people.values().toArray(new Person[0]);
        for (int i = 0; i < people.length; i++) {
            for (int j = i + 1; j < people.length; j++) {
                boolean horizontal = Math.abs(people[i].x - people[j].x) < TreeLayoutEngine.CARD_W;
                boolean vertical = Math.abs(people[i].y - people[j].y) < TreeLayoutEngine.CARD_H;
                assertFalse("overlap: " + people[i].id + " / " + people[j].id, horizontal && vertical);
            }
        }
    }

    private static final class Position {
        final float x;
        final float y;

        Position(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
