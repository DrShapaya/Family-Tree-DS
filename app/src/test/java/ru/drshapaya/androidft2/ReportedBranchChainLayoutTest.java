package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regression for a chain of sibling families connected by marriages. */
public final class ReportedBranchChainLayoutTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void twoLeafChildrenCanOccupyGapBesideTheirUncleFamily() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-10-01-two-children.json");

        TreeLayoutEngine.layoutFresh(state);

        Person sibling = state.people.get(idByName(state, "8. 4"));
        Person parent = state.people.get(idByName(state, "9. 6"));
        Person partner = state.people.get(idByName(state, "27"));
        Person firstChild = state.people.get(idByName(state, "28"));
        Person secondChild = state.people.get(idByName(state, "Брат или сестра"));
        Person leftCouple = state.people.get(idByName(state, "15. Новый партнёр"));
        Person rightCouple = state.people.get(idByName(state, "16. 8"));

        assertTrue("the free leaf sibling should use the open side of the couple",
            parent.x < partner.x && partner.x < sibling.x);
        assertTrue("both children should fit between the neighboring family branches",
            leftCouple.x < firstChild.x && firstChild.x < secondChild.x
                && secondChild.x < rightCouple.x);
        assertEquals("the two children should remain centered below their parents",
            directCenter(state, parent.id, partner.id),
            directCenter(state, firstChild.id, secondChild.id),
            TreeLayoutEngine.GRID + EPSILON);
        assertTrue("the ancestor branch should stay near its descendants: "
                + state.people.get(idByName(state, "6. Новый родитель 1")).x
                + ", " + state.people.get(idByName(state, "7. Новый родитель 2")).x
                + "; children=" + state.people.get(idByName(state, "Новый ребёнок")).x
                + ", " + rightCouple.x + "; couple=" + parent.x + ", "
                + partner.x + "; sibling=" + sibling.x + "; leaf=" + firstChild.x
                + ", " + secondChild.x,
            Math.abs(directCenter(state,
                idByName(state, "6. Новый родитель 1"),
                idByName(state, "7. Новый родитель 2"))
                - directCenter(state,
                    idByName(state, "Новый ребёнок"), rightCouple.id))
                <= TreeLayoutEngine.GRID * 12f + EPSILON);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void newChildStaysBetweenSiblingFamiliesInsteadOfFarBranch() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-10-01-child-layout.json");

        TreeLayoutEngine.layoutFresh(state);

        Person siblingBefore = state.people.get(idByName(state, "8. 4"));
        Person firstParent = state.people.get(idByName(state, "9. 6"));
        Person partner = state.people.get(idByName(state, "27"));
        Person siblingAfter = state.people.get(idByName(state, "26"));
        Person child = state.people.get(idByName(state, "28"));
        Person leftChild = state.people.get(idByName(state, "15. Новый партнёр"));
        Person rightChild = state.people.get(idByName(state, "16. 8"));

        assertTrue("the couple should fit between its siblings: "
                + siblingBefore.x + ", " + firstParent.x + ", " + partner.x
                + ", " + siblingAfter.x + "; child=" + child.x
                + ", gap=" + leftChild.x + ".." + rightChild.x,
            siblingBefore.x < firstParent.x && firstParent.x < partner.x
                && partner.x < siblingAfter.x);
        assertEquals("the couple should use the nearby open slot",
            TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 5f,
            firstParent.x - siblingBefore.x, EPSILON);
        assertEquals("the following sibling should stay nearby",
            TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 5f,
            siblingAfter.x - partner.x, EPSILON);
        assertTrue("the new child should stay in the gap between child branches: "
                + leftChild.x + ", " + child.x + ", " + rightChild.x
                + "; parents=" + firstParent.x + ", " + partner.x,
            leftChild.x + TreeLayoutEngine.CARD_W < child.x
                && child.x + TreeLayoutEngine.CARD_W < rightChild.x);
        assertEquals("the child should sit below the couple",
            directCenter(state, firstParent.id, partner.id),
            child.x + TreeLayoutEngine.CARD_W / 2f,
            TreeLayoutEngine.GRID + EPSILON);
        assertTrue("the ancestor branch should remain close to its children",
            Math.abs(directCenter(state,
                idByName(state, "6. Новый родитель 1"),
                idByName(state, "7. Новый родитель 2"))
                - directCenter(state,
                    idByName(state, "Новый ребёнок"), rightChild.id))
                <= TreeLayoutEngine.GRID * 22f + EPSILON);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void fullArrangeCentersParentPairOverAllDirectChildrenInReportedTree() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-09-30-parent-center.json");
        float previousParentX = state.people.get(idByName(state, "6. Новый родитель 1")).x;
        float previousSiblingX = state.people.get(idByName(state, "9. 6")).x;
        TreeLayoutEngine.layoutFresh(state);

        float parentCenter = directCenter(state,
            idByName(state, "6. Новый родитель 1"),
            idByName(state, "7. Новый родитель 2"));
        float childrenCenter = directCenter(state,
            idByName(state, "Новый ребёнок"),
            idByName(state, "16. 8"));
        assertEquals("parents 6 and 7 should center over their outermost children",
            childrenCenter, parentCenter, TreeLayoutEngine.GRID * 2f + EPSILON);
        assertTrue("the whole upper branch should move toward the children",
            state.people.get(idByName(state, "6. Новый родитель 1")).x - previousParentX
                >= TreeLayoutEngine.GRID * 12f);
        assertEquals("the adjacent sibling must follow the parent branch",
            state.people.get(idByName(state, "6. Новый родитель 1")).x - previousParentX,
            state.people.get(idByName(state, "9. 6")).x - previousSiblingX,
            EPSILON);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void addingPartnerToPerson4MakesSpaceWithoutRelocatingMainBranches() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-09-28-before-person4-partner.json");
        String p4 = idByName(state, "Person4 Tree");
        String p5 = idByName(state, "Person5 Tree");
        Person partner = new Person("new-partner-of-4");
        partner.name = "Новый партнёр";
        partner.gender = PersonGender.UNKNOWN;
        partner.x = state.people.get(p4).x - 320f;
        partner.y = state.people.get(p4).y;
        state.people.put(partner.id, partner);
        state.links.add(new Relation("p4-partner", "partner", p4, partner.id, "left"));
        state.links.add(new Relation("p4-child-parent", "parent", partner.id, p5, "right"));
        state.autoArrangeOnAdd = true;

        LayoutSnapshot before = LayoutSnapshot.capture(state);
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton(partner.id),
            p4,
            "add-partner-with-children");

        for (String name : Arrays.asList("Person1 Tree", "Person2 Tree", "Person4 Tree",
            "Person5 Tree", "Person6 Tree", "Person7 Tree", "Person8 Tree",
            "Person33 Tree", "Person34 Tree", "Person35 Tree", "Person36 Tree")) {
            String id = idByName(state, name);
            assertEquals(name + " should remain in its established branch",
                before.positionOf(id).x, state.people.get(id).x, EPSILON);
        }
        float siblingShift = state.people.get(idByName(state, "Person3 Tree")).x
            - before.positionOf(idByName(state, "Person3 Tree")).x;
        assertTrue("the sibling family must make space", siblingShift < 0f);
        for (String name : Arrays.asList("Person3 Tree", "Person9 Tree", "Person10 Tree",
            "Person11 Tree", "Person12 Tree", "Брат или сестра")) {
            String id = idByName(state, name);
            assertEquals(name + " should follow its sibling branch", siblingShift,
                state.people.get(id).x - before.positionOf(id).x, EPSILON);
        }
        float leftShift = state.people.get(idByName(state, "Person20 Tree")).x
            - before.positionOf(idByName(state, "Person20 Tree")).x;
        assertTrue("left ancestry should move outward", leftShift < siblingShift);
        for (String name : Arrays.asList("Person17 Tree", "Person18 Tree", "Person19 Tree",
            "Person20 Tree", "Person29 Tree", "Person30 Tree", "Person31 Tree",
            "Person32 Tree")) {
            String id = idByName(state, name);
            assertEquals(name + " should follow its ancestry", leftShift,
                state.people.get(id).x - before.positionOf(id).x, EPSILON);
        }
        float rightShift = state.people.get(idByName(state, "Person28 Tree")).x
            - before.positionOf(idByName(state, "Person28 Tree")).x;
        assertTrue("right ancestry should move outward", rightShift > 0f);
        for (String name : Arrays.asList("Person25 Tree", "Person26 Tree", "Person27 Tree",
            "Person28 Tree", "Person21 Tree", "Person22 Tree", "Person23 Tree",
            "Person24 Tree", "Person13 Tree", "Person14 Tree", "Person15 Tree",
            "Person16 Tree")) {
            String id = idByName(state, name);
            assertEquals(name + " should follow its ancestry", rightShift,
                state.people.get(id).x - before.positionOf(id).x, EPSILON);
        }
        assertCardGap(state, idByName(state, "Person3 Tree"), partner.id, 5);
        assertCardGap(state, p4, idByName(state, "Person28 Tree"), 5);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void addingPartnerToPerson3KeepsNeighbouringBranchesSeparated() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-09-28-before-partner.json");
        String p3 = idByName(state, "Person3 Tree");
        String p10 = idByName(state, "Person10 Tree");
        Person partner = new Person("new-partner-of-3");
        partner.name = "Новый партнёр";
        partner.gender = PersonGender.UNKNOWN;
        partner.x = state.people.get(p3).x - 320f;
        partner.y = state.people.get(p3).y;
        state.people.put(partner.id, partner);
        state.links.add(new Relation("new-partner-relation", "partner", p3, partner.id, "left"));
        state.links.add(new Relation("new-parent-relation", "parent", partner.id, p10, "right"));
        state.autoArrangeOnAdd = true;

        LayoutSnapshot before = LayoutSnapshot.capture(state);
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton(partner.id),
            p3,
            "add-partner-with-children");
        for (String name : Arrays.asList("Person3 Tree", "Person4 Tree", "Person9 Tree",
            "Person10 Tree", "Person33 Tree", "Person35 Tree", "Брат или сестра")) {
            String id = idByName(state, name);
            assertEquals(name + " must not be dragged toward another branch",
                before.positionOf(id).x, state.people.get(id).x, EPSILON);
        }
        float leftBranchShift = state.people.get(idByName(state, "Person20 Tree")).x
            - before.positionOf(idByName(state, "Person20 Tree")).x;
        assertTrue("The obstructing ancestry branch must move outward", leftBranchShift < 0f);
        for (String name : Arrays.asList("Person17 Tree", "Person18 Tree", "Person19 Tree",
            "Person20 Tree", "Person29 Tree", "Person30 Tree", "Person31 Tree",
            "Person32 Tree")) {
            String id = idByName(state, name);
            assertEquals(name + " must follow its connected ancestry branch",
                leftBranchShift,
                state.people.get(id).x - before.positionOf(id).x,
                EPSILON);
        }
        assertEquals(state.people.get(p3).x - 320f, partner.x, EPSILON);
        assertCardGap(state, idByName(state, "Person20 Tree"), partner.id, 5);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void partnerOnRightPushesOnlyTouchedAncestryAndItsNextNeighbour() {
        TreeState state = new TreeState();
        for (String id : Arrays.asList("anchor", "mate", "blocker", "blockerSibling",
            "father", "mother", "next", "nextMate")) {
            person(state, id, "mate".equals(id) || "mother".equals(id)
                || "nextMate".equals(id) ? PersonGender.FEMALE : PersonGender.MALE);
        }
        setPosition(state, "anchor", 1000f, 1000f);
        setPosition(state, "mate", 1320f, 1000f);
        setPosition(state, "blocker", 1480f, 1000f);
        setPosition(state, "blockerSibling", 1960f, 1000f);
        setPosition(state, "father", 1480f, 520f);
        setPosition(state, "mother", 1800f, 520f);
        setPosition(state, "next", 2440f, 1000f);
        setPosition(state, "nextMate", 2760f, 1000f);
        link(state, "partner", "anchor", "mate");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "blocker");
        link(state, "parent", "mother", "blocker");
        link(state, "parent", "father", "blockerSibling");
        link(state, "parent", "mother", "blockerSibling");
        link(state, "sibling", "blocker", "blockerSibling");
        link(state, "partner", "next", "nextMate");
        state.rootId = "anchor";
        state.autoArrangeOnAdd = true;

        LayoutSnapshot before = LayoutSnapshot.capture(state);
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton("mate"),
            "anchor",
            "add-partner-only");

        assertEquals(before.positionOf("anchor").x, state.people.get("anchor").x, EPSILON);
        assertEquals(1320f, state.people.get("mate").x, EPSILON);
        for (String id : Arrays.asList("blocker", "blockerSibling", "father", "mother",
            "next", "nextMate")) {
            assertEquals(id, 320f, state.people.get(id).x - before.positionOf(id).x, EPSILON);
        }
        assertCardGap(state, "mate", "blocker", 5);
        assertCardGap(state, "blockerSibling", "next", 5);
    }


    @Test
    public void rebuildingActualReportedPackageRestoresStructuralOrder() throws Exception {
        TreeState state = readFixture("/fixtures/family-tree-2026-09-27-6.json");

        TreeLayoutEngine.rebuildStepwise(state);

        String p1 = idByName(state, "Person1 Tree");
        String p2 = idByName(state, "Person2 Tree");
        String p3 = idByName(state, "Person3 Tree");
        String p4 = idByName(state, "Person4 Tree");
        String p5 = idByName(state, "Person5 Tree");
        String p6 = idByName(state, "Person6 Tree");
        String p8 = idByName(state, "Person8 Tree");
        String p15 = idByName(state, "Person15 Tree");
        String p16 = idByName(state, "Person16 Tree");
        String p28 = idByName(state, "Person28 Tree");
        String p33 = idByName(state, "Person33 Tree");
        String p34 = idByName(state, "Person34 Tree");
        String p35 = idByName(state, "Person35 Tree");

        assertTrue("Person35 must remain left of Person8",
            state.people.get(p35).x < state.people.get(p8).x);
        assertTrue("Person35 ancestry must be on the outside left",
            directCenter(state, p33, p34) < cardCenter(state, p35));
        assertTrue("Person8 ancestry must be on the outside right",
            directCenter(state, p5, p6) > cardCenter(state, p8));
        assertTrue("Person15 must be right of Person16",
            state.people.get(p15).x > state.people.get(p16).x);
        assertEquals(
            directCenter(state, p1, p2),
            directCenter(state, p3, p4),
            EPSILON);
        assertCardGap(state, p4, p28, 5);

        LayoutSnapshot first = LayoutSnapshot.capture(state);
        TreeLayoutEngine.rebuildStepwise(state);
        for (String id : first.positions.keySet()) {
            assertEquals(id + " X", first.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(id + " Y", first.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
    }

    @Test
    public void rebuildCentersEveryFamilyAndUsesOneBranchGap() {
        TreeState state = connectedFamilyChain();

        TreeLayoutEngine.rebuildStepwise(state);

        assertFamilyCentered(state, "p1", "p2", "p3", "p4");
        assertFamilyCentered(state, "p13", "p14", "p15", "p16");
        assertCardGap(state, "p20", "p3", 5);
        assertCardGap(state, "p24", "p23", 5);
        assertCardGap(state, "p8", "p7", 5);
        assertCardGap(state, "p34", "p5", 5);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            LayoutSnapshot.capture(state)).isValid());

        LayoutSnapshot first = LayoutSnapshot.capture(state);
        TreeLayoutEngine.rebuildStepwise(state);
        for (String id : first.positions.keySet()) {
            assertEquals(first.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(first.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
    }

    @Test
    public void addingPartnerDoesNotCompactDistantMarriedBranch() {
        TreeState state = connectedFamilyChain();
        TreeLayoutEngine.rebuildStepwise(state);
        state.autoArrangeOnAdd = true;

        addPartner(state, "p9", "partnerOfP9");
        link(state, "parent", "partnerOfP9", "p11");
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton("partnerOfP9"),
            "p9",
            "add-partner");

        LayoutSnapshot before = LayoutSnapshot.capture(state);
        addPartner(state, "p12", "partnerOfP12");
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton("partnerOfP12"),
            "p12",
            "add-partner");

        for (String id : Arrays.asList("p33", "p34", "p35", "p36")) {
            assertEquals(before.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(before.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
    }

    @Test
    public void addingPartnerMovesAdjacentFamilyBranchWithFullGap() {
        TreeState state = new TreeState();
        person(state, "father", PersonGender.MALE);
        person(state, "mother", PersonGender.FEMALE);
        person(state, "left", PersonGender.MALE);
        person(state, "right", PersonGender.MALE);
        person(state, "rightMate", PersonGender.FEMALE);
        person(state, "rightChild", PersonGender.MALE);
        person(state, "newMate", PersonGender.FEMALE);
        setPosition(state, "father", 1240f, 1000f);
        setPosition(state, "mother", 1560f, 1000f);
        setPosition(state, "left", 1160f, 1480f);
        setPosition(state, "right", 1640f, 1480f);
        setPosition(state, "rightMate", 1960f, 1480f);
        setPosition(state, "rightChild", 1800f, 1960f);
        setPosition(state, "newMate", 1480f, 1480f);
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "left");
        link(state, "parent", "mother", "left");
        link(state, "parent", "father", "right");
        link(state, "parent", "mother", "right");
        link(state, "sibling", "left", "right");
        link(state, "partner", "right", "rightMate");
        link(state, "parent", "right", "rightChild");
        link(state, "parent", "rightMate", "rightChild");
        link(state, "partner", "left", "newMate");
        state.rootId = "left";
        state.autoArrangeOnAdd = true;

        float beforeRight = state.people.get("right").x;
        float beforeMate = state.people.get("rightMate").x;
        float beforeChild = state.people.get("rightChild").x;
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singleton("newMate"),
            "left",
            "partner");

        float branchShift = state.people.get("right").x - beforeRight;
        assertTrue("adjacent family should move to make room", branchShift > 0f);
        assertEquals(branchShift, state.people.get("rightMate").x - beforeMate, EPSILON);
        assertEquals(branchShift, state.people.get("rightChild").x - beforeChild, EPSILON);
        assertTrue("branches must keep five grid cells",
            state.people.get("right").x
                - state.people.get("newMate").x
                - TreeLayoutEngine.CARD_W >= TreeLayoutEngine.GRID * 5f);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void selectedConnectedBranchKeepsItsStructureAndOutsideCardsFixed() {
        TreeState state = connectedFamilyChain();
        TreeLayoutEngine.rebuildStepwise(state);
        Set<String> selected = new LinkedHashSet<>(Arrays.asList(
            "p5", "p6", "p7", "p8", "p33", "p34", "p35", "p36"));
        LayoutSnapshot before = LayoutSnapshot.capture(state);
        Person anchor = state.people.get("p5");
        int index = 0;
        for (String id : selected) {
            if (id.equals("p5")) continue;
            Person person = state.people.get(id);
            person.x = anchor.x + (index++ % 2) * TreeLayoutEngine.GRID;
        }

        assertTrue(TreeLayoutEngine.rebuildSelected(state, selected, "p5", null));

        for (String id : before.positions.keySet()) {
            if (selected.contains(id)) continue;
            assertEquals(before.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(before.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state),
            LayoutSnapshot.capture(state)).isValid());
    }

    @Test
    public void arrangingAlreadyStraightTreeWithSecondUnionIsIdempotent() {
        TreeState state = alreadyStraightTreeWithSecondUnion();
        LayoutSnapshot before = LayoutSnapshot.capture(state);

        TreeLayoutEngine.rebuildStepwise(state);

        for (String id : before.positions.keySet()) {
            assertEquals(before.positionOf(id).x, state.people.get(id).x, EPSILON);
            assertEquals(before.positionOf(id).y, state.people.get(id).y, EPSILON);
        }
    }

    @Test
    public void arrangingReportedBrokenSecondLayoutRestoresStraightGeometry() {
        TreeState broken = alreadyStraightTreeWithSecondUnion();
        move(broken, "p5", 12280f);
        move(broken, "p6", 12600f);
        move(broken, "p7", 12160f);
        move(broken, "p8", 12960f);
        move(broken, "p11", 11040f);
        move(broken, "p12", 11360f);
        move(broken, "p15", 12760f);
        move(broken, "p28", 14840f);
        move(broken, "p33", 13080f);
        move(broken, "p34", 13400f);
        move(broken, "p35", 12640f);
        move(broken, "p36", 13440f);
        move(broken, "extra", 11680f);

        TreeLayoutEngine.rebuildStepwise(broken);

        assertGenderedPartnerAncestryFacesOutward(broken);
        assertFamilyCentered(broken, "p1", "p2", "p3", "p4");
        assertFamilyCentered(broken, "p13", "p14", "p15", "p16");
        assertTrue(broken.people.get("p16").x < broken.people.get("p15").x);
        assertTrue(broken.people.get("p35").x < broken.people.get("p8").x);
        assertTrue(new LayoutConstraints().validate(
            FamilyLayoutGraph.from(broken),
            LayoutSnapshot.capture(broken)).isValid());
        LayoutSnapshot first = LayoutSnapshot.capture(broken);
        TreeLayoutEngine.rebuildStepwise(broken);
        for (String id : first.positions.keySet()) {
            assertEquals(id + " X", first.positionOf(id).x, broken.people.get(id).x, EPSILON);
            assertEquals(id + " Y", first.positionOf(id).y, broken.people.get(id).y, EPSILON);
        }
    }

    private static TreeState connectedFamilyChain() {
        TreeState state = new TreeState();
        for (int number = 1; number <= 36; number++) {
            String gender = (number & 1) == 1 ? PersonGender.MALE : PersonGender.FEMALE;
            person(state, "p" + number, gender);
        }
        person(state, "extra", PersonGender.UNKNOWN);

        family(state, 29, 30, 31, 32);
        family(state, 25, 26, 27, 28);
        family(state, 33, 34, 35, 36);
        family(state, 5, 6, 7, 8);
        family(state, 1, 2, 3, 4);
        family(state, 13, 14, 15, 16);
        family(state, 9, 10, 11, 12);
        family(state, 21, 22, 23, 24);
        family(state, 17, 18, 19, 20);
        link(state, "sibling", "p32", "p31");
        link(state, "sibling", "p28", "p27");
        link(state, "sibling", "p36", "p35");
        link(state, "sibling", "p8", "p7");
        link(state, "sibling", "p3", "p4");
        link(state, "sibling", "p16", "p15");
        link(state, "sibling", "p11", "p12");
        link(state, "sibling", "p24", "p23");
        link(state, "sibling", "p19", "p20");

        link(state, "parent", "p4", "p5");
        link(state, "parent", "p3", "p10");
        link(state, "parent", "p15", "p6");
        link(state, "parent", "p20", "p9");
        link(state, "family", "p23", "p16");
        link(state, "family", "p27", "p24");
        link(state, "family", "p31", "p19");
        link(state, "family", "p35", "p8");
        link(state, "parent", "p9", "extra");
        link(state, "parent", "p10", "extra");
        link(state, "sibling", "p11", "extra");
        link(state, "sibling", "p12", "extra");
        state.rootId = "p1";
        return state;
    }

    private static TreeState alreadyStraightTreeWithSecondUnion() {
        TreeState state = connectedFamilyChain();
        person(state, "partnerOfP9", PersonGender.UNKNOWN);
        state.links.add(new Relation(
            "partner_p9_partnerOfP9", "partner", "p9", "partnerOfP9", "left"));
        link(state, "parent", "partnerOfP9", "p11");
        float[] x = {
            11880, 12200, 11800, 12280, 13320, 13640, 13720, 13240,
            11480, 11800, 11160, 11480, 14440, 14760, 14840, 14360,
            10920, 11240, 10840, 11320, 13640, 13960, 14040, 13560,
            12840, 13160, 13240, 12760, 10120, 10440, 10520, 10040,
            12520, 12840, 12920, 12440
        };
        for (int number = 1; number <= 36; number++) {
            Person person = state.people.get("p" + number);
            person.x = x[number - 1];
            if (number <= 2 || (number >= 13 && number <= 14)
                || (number >= 17 && number <= 18)
                || (number >= 21 && number <= 22)
                || (number >= 25 && number <= 26)
                || (number >= 29 && number <= 30)) {
                person.y = 7920;
            } else if ((number >= 3 && number <= 4)
                || (number >= 15 && number <= 16)
                || (number >= 19 && number <= 20)
                || (number >= 23 && number <= 24)
                || (number >= 27 && number <= 28)
                || (number >= 31 && number <= 32)) {
                person.y = 8400;
            } else if ((number >= 5 && number <= 6)
                || (number >= 9 && number <= 10)
                || (number >= 33 && number <= 34)) {
                person.y = 8880;
            } else {
                person.y = 9360;
            }
        }
        state.people.get("extra").x = 11800;
        state.people.get("extra").y = 9360;
        state.people.get("partnerOfP9").x = 11160;
        state.people.get("partnerOfP9").y = 8880;
        return state;
    }

    private static void family(
        TreeState state,
        int firstParent,
        int secondParent,
        int firstChild,
        int secondChild
    ) {
        String first = "p" + firstParent;
        String second = "p" + secondParent;
        link(state, "partner", first, second);
        for (int childNumber : new int[]{firstChild, secondChild}) {
            String child = "p" + childNumber;
            link(state, "parent", first, child);
            link(state, "parent", second, child);
        }
    }

    private static void assertFamilyCentered(
        TreeState state,
        String firstParent,
        String secondParent,
        String firstChild,
        String secondChild
    ) {
        assertEquals(
            directCenter(state, firstParent, secondParent),
            directCenter(state, firstChild, secondChild),
            EPSILON);
    }

    private static float directCenter(TreeState state, String firstId, String secondId) {
        Person first = state.people.get(firstId);
        Person second = state.people.get(secondId);
        float left = Math.min(first.x, second.x);
        float right = Math.max(first.x, second.x) + TreeLayoutEngine.CARD_W;
        return (left + right) / 2f;
    }

    private static float cardCenter(TreeState state, String id) {
        return state.people.get(id).x + TreeLayoutEngine.CARD_W / 2f;
    }

    private static String idByName(TreeState state, String name) {
        for (Person person : state.people.values()) {
            if (name.equals(person.name)) return person.id;
        }
        throw new AssertionError("Missing person " + name);
    }

    private static TreeState readFixture(String resourceName) throws Exception {
        InputStream resource = ReportedBranchChainLayoutTest.class.getResourceAsStream(resourceName);
        if (resource == null) throw new AssertionError("Missing fixture " + resourceName);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = resource) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
        JSONObject document = new JSONObject(
            new String(output.toByteArray(), StandardCharsets.UTF_8));
        JSONObject root = document.optJSONObject("state");
        if (root == null) root = document;
        JSONObject people = root.getJSONObject("people");
        JSONObject positions = root.optJSONObject("positions");
        TreeState state = new TreeState();
        Iterator<String> keys = people.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = people.getJSONObject(key);
            Person person = new Person(item.optString("id", key));
            person.name = item.optString("name", "");
            person.gender = PersonGender.normalize(item.optString("gender", ""));
            person.genderManual = item.optBoolean("genderManual", item.has("gender"));
            person.pinned = item.optBoolean("pinned", false);
            JSONObject position = positions == null ? null : positions.optJSONObject(key);
            person.x = (float) (position == null
                ? item.optDouble("x", Float.NaN)
                : position.optDouble("x", Float.NaN));
            person.y = (float) (position == null
                ? item.optDouble("y", Float.NaN)
                : position.optDouble("y", Float.NaN));
            state.people.put(person.id, person);
        }
        JSONArray links = root.optJSONArray("links");
        if (links != null) for (int index = 0; index < links.length(); index++) {
            JSONObject item = links.getJSONObject(index);
            state.links.add(new Relation(
                item.optString("id", "link-" + index),
                item.optString("type", "family"),
                item.optString("from", ""),
                item.optString("to", ""),
                item.optString("side", "right")));
        }
        state.rootId = root.optString("rootId", "");
        state.selectedId = root.optString("selectedId", state.rootId);
        return state;
    }

    private static void move(TreeState state, String id, float x) {
        state.people.get(id).x = x;
    }

    private static void setPosition(TreeState state, String id, float x, float y) {
        Person person = state.people.get(id);
        person.x = x;
        person.y = y;
    }

    private static void assertGenderedPartnerAncestryFacesOutward(TreeState state) {
        Map<String, List<String>> parents = new LinkedHashMap<>();
        for (Relation relation : state.links) {
            if (!"parent".equals(relation.type)) continue;
            parents.computeIfAbsent(relation.to, ignored -> new ArrayList<>())
                .add(relation.from);
        }
        for (Relation relation : state.links) {
            if (!("partner".equals(relation.type) || "family".equals(relation.type))) continue;
            Person first = state.people.get(relation.from);
            Person second = state.people.get(relation.to);
            if (first == null || second == null) continue;
            Person man;
            Person woman;
            if (PersonGender.MALE.equals(PersonGender.resolve(first))
                && PersonGender.FEMALE.equals(PersonGender.resolve(second))) {
                man = first;
                woman = second;
            } else if (PersonGender.FEMALE.equals(PersonGender.resolve(first))
                && PersonGender.MALE.equals(PersonGender.resolve(second))) {
                man = second;
                woman = first;
            } else {
                continue;
            }
            assertTrue(man.id + " must be left of " + woman.id, man.x < woman.x);
            if (parents.containsKey(man.id)) {
                assertTrue(man.id + " ancestry must continue left",
                    averageCenter(state, parents.get(man.id)) <= man.x + TreeLayoutEngine.CARD_W / 2f);
            }
            if (parents.containsKey(woman.id)) {
                assertTrue(woman.id + " ancestry must continue right",
                    averageCenter(state, parents.get(woman.id)) >= woman.x + TreeLayoutEngine.CARD_W / 2f);
            }
        }
    }

    private static float averageCenter(TreeState state, List<String> ids) {
        float total = 0f;
        int count = 0;
        for (String id : ids) {
            Person person = state.people.get(id);
            if (person == null) continue;
            total += person.x + TreeLayoutEngine.CARD_W / 2f;
            count++;
        }
        return count == 0 ? 0f : total / count;
    }

    private static void assertCardGap(
        TreeState state,
        String leftId,
        String rightId,
        int gridCells
    ) {
        Person left = state.people.get(leftId);
        Person right = state.people.get(rightId);
        assertEquals(
            gridCells * TreeLayoutEngine.GRID,
            right.x - left.x - TreeLayoutEngine.CARD_W,
            EPSILON);
    }

    private static void person(TreeState state, String id, String gender) {
        Person person = new Person(id);
        person.name = id;
        person.gender = gender;
        person.genderManual = true;
        state.people.put(id, person);
    }

    private static void addPartner(TreeState state, String anchorId, String partnerId) {
        Person anchor = state.people.get(anchorId);
        person(state, partnerId, PersonGender.UNKNOWN);
        Person partner = state.people.get(partnerId);
        partner.x = anchor.x - TreeLayoutEngine.CARD_W - TreeLayoutEngine.GRID;
        partner.y = anchor.y;
        link(state, "partner", anchorId, partnerId);
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
    }
}
