package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TreeLayoutEngineTest {
    private static final float EPSILON = 0.01f;

    @Test
    public void nuclearFamilyUsesPartnerAndGenerationGaps() {
        TreeState state = new TreeState();
        person(state, "father", "Алексей Иванов", "1970");
        person(state, "mother", "Анна Иванова", "1972");
        person(state, "child", "Иван Иванов", "1995");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "child");
        link(state, "parent", "mother", "child");
        state.rootId = "child";

        TreeLayoutEngine.layout(state);

        Person father = state.people.get("father");
        Person mother = state.people.get("mother");
        Person child = state.people.get("child");
        assertTrue("мужчина должен быть слева", father.x < mother.x);
        assertEquals(TreeLayoutEngine.GRID, mother.x - father.x - TreeLayoutEngine.CARD_W, EPSILON);
        assertEquals(father.y, mother.y, EPSILON);
        assertEquals(TreeLayoutEngine.LEVEL_GAP, child.y - father.y, EPSILON);
        assertOnGrid(state);
    }

    @Test
    public void simpleSiblingsHaveOneCellBetweenCards() {
        TreeState state = parentsAndTwoChildren();
        TreeLayoutEngine.layout(state);

        Person first = state.people.get("childA");
        Person second = state.people.get("childB");
        if (first.x > second.x) {
            Person swap = first;
            first = second;
            second = swap;
        }
        assertEquals(TreeLayoutEngine.SIBLING_GAP, second.x - first.x - TreeLayoutEngine.CARD_W, EPSILON);
    }

    @Test
    public void siblingFamiliesHaveTwoCellsBetweenExtremeCards() {
        TreeState state = parentsAndTwoChildren();
        person(state, "partnerA", "Ольга Первая", "1995");
        person(state, "partnerB", "Мария Вторая", "1997");
        link(state, "partner", "childA", "partnerA");
        link(state, "partner", "childB", "partnerB");
        TreeLayoutEngine.layout(state);

        float firstMin = minX(state, "childA", "partnerA");
        float firstMax = maxRight(state, "childA", "partnerA");
        float secondMin = minX(state, "childB", "partnerB");
        float secondMax = maxRight(state, "childB", "partnerB");
        float gap = firstMin < secondMin ? secondMin - firstMax : firstMin - secondMax;
        assertEquals(TreeLayoutEngine.GRID * 2f, gap, EPSILON);
    }

    @Test
    public void paternalAncestryStaysLeftOfMaternalAncestry() {
        TreeState state = new TreeState();
        person(state, "father", "Алексей Иванов", "1970");
        person(state, "mother", "Анна Иванова", "1972");
        person(state, "child", "Иван Иванов", "1995");
        person(state, "paternalGrandfather", "Пётр Иванов", "1940");
        person(state, "paternalGrandmother", "Ольга Иванова", "1942");
        person(state, "maternalGrandfather", "Сергей Петров", "1941");
        person(state, "maternalGrandmother", "Мария Петрова", "1944");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "child");
        link(state, "parent", "mother", "child");
        link(state, "partner", "paternalGrandfather", "paternalGrandmother");
        link(state, "parent", "paternalGrandfather", "father");
        link(state, "parent", "paternalGrandmother", "father");
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        link(state, "parent", "maternalGrandfather", "mother");
        link(state, "parent", "maternalGrandmother", "mother");
        state.rootId = "child";

        TreeLayoutEngine.layout(state);

        float paternalRight = maxRight(state, "paternalGrandfather", "paternalGrandmother");
        float maternalLeft = minX(state, "maternalGrandfather", "maternalGrandmother");
        assertTrue("мужская ветка должна оставаться слева", paternalRight < maternalLeft);
        assertTrue(
            "между семейными ветками должно быть не меньше пяти клеток",
            maternalLeft - paternalRight >= TreeLayoutEngine.GRID * 5f);
        assertEquals(
            TreeLayoutEngine.LEVEL_GAP,
            state.people.get("father").y - state.people.get("paternalGrandfather").y,
            EPSILON);
        assertEquals(
            TreeLayoutEngine.LEVEL_GAP,
            state.people.get("child").y - state.people.get("father").y,
            EPSILON);
    }

    @Test
    public void paternalAndMaternalSideFamiliesStayOnTheirOwnSides() {
        TreeState state = new TreeState();
        person(state, "father", "Алексей Иванов", "1970");
        person(state, "mother", "Анна Иванова", "1972");
        person(state, "child", "Иван Иванов", "1995");
        person(state, "paternalGrandfather", "Пётр Иванов", "1940");
        person(state, "paternalGrandmother", "Ольга Иванова", "1942");
        person(state, "paternalUncle", "Борис Иванов", "1968");
        person(state, "paternalUnclePartner", "Елена Иванова", "1970");
        person(state, "maternalGrandfather", "Сергей Петров", "1941");
        person(state, "maternalGrandmother", "Мария Петрова", "1944");
        person(state, "maternalAunt", "Татьяна Петрова", "1975");
        person(state, "maternalAuntPartner", "Андрей Сидоров", "1973");

        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "child");
        link(state, "parent", "mother", "child");
        link(state, "partner", "paternalGrandfather", "paternalGrandmother");
        link(state, "parent", "paternalGrandfather", "father");
        link(state, "parent", "paternalGrandmother", "father");
        link(state, "parent", "paternalGrandfather", "paternalUncle");
        link(state, "parent", "paternalGrandmother", "paternalUncle");
        link(state, "partner", "paternalUncle", "paternalUnclePartner");
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        link(state, "parent", "maternalGrandfather", "mother");
        link(state, "parent", "maternalGrandmother", "mother");
        link(state, "parent", "maternalGrandfather", "maternalAunt");
        link(state, "parent", "maternalGrandmother", "maternalAunt");
        link(state, "partner", "maternalAuntPartner", "maternalAunt");
        state.rootId = "child";

        TreeLayoutEngine.layout(state);

        float paternalAncestorRight = maxRight(
            state,
            "paternalGrandfather",
            "paternalGrandmother");
        float maternalAncestorLeft = minX(
            state,
            "maternalGrandfather",
            "maternalGrandmother");
        assertTrue(paternalAncestorRight + TreeLayoutEngine.GRID * 5f <= maternalAncestorLeft);

        float paternalSideRight = maxRight(state, "paternalUncle", "paternalUnclePartner");
        float rootFamilyLeft = minX(state, "father", "mother");
        float rootFamilyRight = maxRight(state, "father", "mother");
        float maternalSideLeft = minX(state, "maternalAunt", "maternalAuntPartner");
        assertTrue(paternalSideRight + TreeLayoutEngine.GRID * 2f <= rootFamilyLeft);
        assertTrue(rootFamilyRight + TreeLayoutEngine.GRID * 2f <= maternalSideLeft);
    }

    @Test
    public void disconnectedFamiliesUseFiveCellBoundaryGap() {
        TreeState state = parentsAndTwoChildren();
        person(state, "otherFather", "Борис Сидоров", "1960");
        person(state, "otherMother", "Елена Сидорова", "1962");
        link(state, "partner", "otherFather", "otherMother");
        TreeLayoutEngine.layout(state);

        float rootFamilyRight = maxRight(state, "father", "mother", "childA", "childB");
        float otherLeft = minX(state, "otherFather", "otherMother");
        assertTrue(
            "границы независимых семей должны отстоять на пять клеток",
            otherLeft - rootFamilyRight >= TreeLayoutEngine.GRID * 5f);
    }

    @Test
    public void pinnedCardAnchorsItsFamilyWithoutBreakingRows() {
        TreeState state = new TreeState();
        person(state, "father", "Алексей Иванов", "1970");
        person(state, "mother", "Анна Иванова", "1972");
        person(state, "child", "Иван Иванов", "1995");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "child");
        link(state, "parent", "mother", "child");
        state.rootId = "child";
        Person father = state.people.get("father");
        father.x = 2000f;
        father.y = 1120f;
        father.pinned = true;

        TreeLayoutEngine.layout(state);

        Person mother = state.people.get("mother");
        Person child = state.people.get("child");
        assertEquals(2000f, father.x, EPSILON);
        assertEquals(1120f, father.y, EPSILON);
        assertEquals(father.y, mother.y, EPSILON);
        assertEquals(TreeLayoutEngine.GRID, mother.x - father.x - TreeLayoutEngine.CARD_W, EPSILON);
        assertEquals(TreeLayoutEngine.LEVEL_GAP, child.y - father.y, EPSILON);
    }

    @Test
    public void fourRowCardEndsOnTheNextGenerationGridLine() {
        TreeState state = new TreeState();
        person(state, "person", "Иван Иванов", "1980");
        state.rootId = "person";
        Guide guide = new Guide();
        guide.id = "guide";
        guide.axis = "h";
        guide.position = TreeLayoutEngine.GRID * 10f;
        state.guides.add(guide);

        TreeLayoutEngine.layout(state);

        Person person = state.people.get("person");
        assertEquals(
            guide.position + TreeLayoutEngine.LEVEL_GAP,
            person.y + TreeLayoutEngine.CARD_H,
            EPSILON);
    }

    @Test
    public void openingTreePreservesSavedCoordinatesEvenWhenRowsContradictLinks() {
        TreeState state = parentsAndTwoChildren();
        String[] ids = {"father", "mother", "childA", "childB"};
        for (int i = 0; i < ids.length; i++) {
            Person person = state.people.get(ids[i]);
            person.x = 400f + i * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID);
            person.y = 1000f;
        }

        TreeLayoutEngine.ensurePositions(state);

        for (int i = 0; i < ids.length; i++) {
            Person person = state.people.get(ids[i]);
            assertEquals(400f + i * (TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID), person.x, EPSILON);
            assertEquals(1000f, person.y, EPSILON);
        }
    }

    @Test
    public void arrangingReferenceStyleTreeKeepsItsFamilyBlocksStable() {
        TreeState state = new TreeState();
        positionedPerson(state, "father", "Отец", 9400f, 7880f);
        positionedPerson(state, "mother", "Мать", 9720f, 7880f);
        positionedPerson(state, "brother", "Брат", 9240f, 8360f);
        positionedPerson(state, "root", "я", 9560f, 8360f);
        positionedPerson(state, "sister", "Сестра", 9880f, 8360f);
        positionedPerson(state, "paternalGrandfather", "ДедО", 9000f, 7400f);
        positionedPerson(state, "paternalGrandmother", "БабаО", 9320f, 7400f);
        positionedPerson(state, "maternalGrandfather", "ДедМ", 9800f, 7400f);
        positionedPerson(state, "maternalGrandmother", "БабаМ", 10120f, 7400f);
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "brother");
        link(state, "parent", "mother", "brother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        link(state, "parent", "father", "sister");
        link(state, "parent", "mother", "sister");
        link(state, "sibling", "brother", "root");
        link(state, "sibling", "root", "sister");
        link(state, "partner", "paternalGrandfather", "paternalGrandmother");
        link(state, "parent", "paternalGrandfather", "father");
        link(state, "parent", "paternalGrandmother", "father");
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        link(state, "parent", "maternalGrandfather", "mother");
        link(state, "parent", "maternalGrandmother", "mother");
        state.rootId = "root";

        java.util.Map<String, float[]> before = new java.util.LinkedHashMap<>();
        for (Person person : state.people.values()) before.put(person.id, new float[]{person.x, person.y});

        TreeLayoutEngine.layout(state);

        for (Person person : state.people.values()) {
            float[] expected = before.get(person.id);
            assertEquals(person.id, expected[0], person.x, EPSILON);
            assertEquals(person.id, expected[1], person.y, EPSILON);
        }
    }

    @Test
    public void autoArrangeCentersNewSiblingGroupUnderParents() {
        TreeState state = new TreeState();
        positionedPerson(state, "father", "Отец", 9400f, 7880f);
        positionedPerson(state, "mother", "Мать", 9720f, 7880f);
        positionedPerson(state, "root", "я", 9400f, 8360f);
        positionedPerson(state, "brother", "Брат", 9720f, 8360f);
        positionedPerson(state, "sister", "Сестра", 10360f, 8360f);
        link(state, "partner", "father", "mother");
        for (String child : new String[]{"root", "brother", "sister"}) {
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
        }
        link(state, "sibling", "root", "brother");
        link(state, "sibling", "root", "sister");
        link(state, "sibling", "brother", "sister");
        state.rootId = "root";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("sister"),
            "root",
            "siblings");

        assertEquals(9400f, state.people.get("root").x, EPSILON);
        assertEquals(9720f, state.people.get("brother").x, EPSILON);
        assertEquals(10040f, state.people.get("sister").x, EPSILON);
        assertEquals(9560f, state.people.get("father").x, EPSILON);
        assertEquals(9880f, state.people.get("mother").x, EPSILON);
    }

    @Test
    public void autoArrangeDistributesFourChildrenAroundFamilyCenter() {
        TreeState state = new TreeState();
        positionedPerson(state, "parent", "Тетя", 7760f, 7880f);
        String[] children = {"child1", "child2", "child3", "child4"};
        for (int i = 0; i < children.length; i++) {
            positionedPerson(state, children[i], "Новый ребёнок " + (i + 1), 7000f + i * 480f, 8360f);
            link(state, "parent", "parent", children[i]);
        }
        state.rootId = "parent";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList(children),
            "parent",
            "children");

        assertEquals(7280f, state.people.get("child1").x, EPSILON);
        assertEquals(7600f, state.people.get("child2").x, EPSILON);
        assertEquals(7920f, state.people.get("child3").x, EPSILON);
        assertEquals(8240f, state.people.get("child4").x, EPSILON);
        assertEquals(7760f, state.people.get("parent").x, EPSILON);
    }

    @Test
    public void autoArrangeCentersNewParentPairAboveChild() {
        TreeState state = new TreeState();
        positionedPerson(state, "child", "я", 9560f, 8360f);
        positionedPerson(state, "parent1", "Новый родитель 1", 8600f, 7880f);
        positionedPerson(state, "parent2", "Новый родитель 2", 9000f, 7880f);
        link(state, "partner", "parent1", "parent2");
        link(state, "parent", "parent1", "child");
        link(state, "parent", "parent2", "child");
        state.rootId = "child";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("parent1", "parent2"),
            "child",
            "parents");

        assertEquals(9400f, state.people.get("parent1").x, EPSILON);
        assertEquals(9720f, state.people.get("parent2").x, EPSILON);
        assertEquals(9560f, state.people.get("child").x, EPSILON);
    }

    @Test
    public void parentsAddedAfterThreeSiblingsCenterAboveTheWholeSiblingGroup() {
        TreeState state = new TreeState();
        positionedPerson(state, "sibling1", "Брат 1", 10000f, 7000f);
        positionedPerson(state, "sibling2", "Брат 2", 10320f, 7000f);
        positionedPerson(state, "sibling3", "Брат 3", 10640f, 7000f);
        positionedPerson(state, "father", "Отец", 10160f, 6520f);
        positionedPerson(state, "mother", "Мать", 10480f, 6520f);
        link(state, "sibling", "sibling1", "sibling2");
        link(state, "sibling", "sibling2", "sibling3");
        link(state, "partner", "father", "mother");
        for (String child : new String[]{"sibling1", "sibling2", "sibling3"}) {
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
        }
        state.rootId = "sibling2";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("father", "mother"),
            "sibling2",
            "parents");

        float siblingCenter = (
            state.people.get("sibling1").x
                + state.people.get("sibling3").x
                + TreeLayoutEngine.CARD_W) / 2f;
        float parentCenter = (
            state.people.get("father").x
                + state.people.get("mother").x
                + TreeLayoutEngine.CARD_W) / 2f;
        assertEquals(siblingCenter, parentCenter, TreeLayoutEngine.GRID / 2f + EPSILON);
        assertEquals(7000f, state.people.get("sibling1").y, EPSILON);
        assertEquals(7000f, state.people.get("sibling2").y, EPSILON);
        assertEquals(7000f, state.people.get("sibling3").y, EPSILON);
    }

    @Test
    public void localArrangeInRemoteFamilyDoesNotMoveMainTree() {
        TreeState state = new TreeState();
        positionedPerson(state, "root", "я", 9560f, 8840f);
        positionedPerson(state, "father", "Отец", 9400f, 8360f);
        positionedPerson(state, "mother", "Мать", 9720f, 8360f);
        positionedPerson(state, "aunt", "Тетя", 7280f, 8360f);
        positionedPerson(state, "auntPartner", "Партнёр тети", 7600f, 8360f);
        positionedPerson(state, "cousin", "Ребёнок тети", 7440f, 8840f);
        positionedPerson(state, "oldParent1", "Старый родитель 1", 5680f, 7880f);
        positionedPerson(state, "oldParent2", "Старый родитель 2", 6000f, 7880f);
        positionedPerson(state, "newParent1", "Новый родитель 1", 6200f, 7880f);
        positionedPerson(state, "newParent2", "Новый родитель 2", 6600f, 7880f);
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        link(state, "partner", "aunt", "auntPartner");
        link(state, "parent", "aunt", "cousin");
        link(state, "parent", "auntPartner", "cousin");
        link(state, "partner", "oldParent1", "oldParent2");
        link(state, "parent", "oldParent1", "aunt");
        link(state, "parent", "oldParent2", "aunt");
        link(state, "partner", "newParent1", "newParent2");
        link(state, "parent", "newParent1", "aunt");
        link(state, "parent", "newParent2", "aunt");
        state.rootId = "root";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("newParent1", "newParent2"),
            "aunt",
            "parents");

        assertEquals(9560f, state.people.get("root").x, EPSILON);
        assertEquals(8840f, state.people.get("root").y, EPSILON);
        assertEquals(9400f, state.people.get("father").x, EPSILON);
        assertEquals(9720f, state.people.get("mother").x, EPSILON);
        assertEquals(7120f, state.people.get("newParent1").x, EPSILON);
        assertEquals(7440f, state.people.get("newParent2").x, EPSILON);
        assertEquals(7280f, state.people.get("aunt").x, EPSILON);
    }

    @Test
    public void addingChildMovesOnlyThatSiblingFamilyAndItsDescendants() {
        TreeState state = new TreeState();
        positionedPerson(state, "root", "я", 9560f, 8840f);
        positionedPerson(state, "father", "Отец", 9400f, 8360f);
        positionedPerson(state, "mother", "Мать", 9720f, 8360f);
        positionedPerson(state, "parent1", "Новый родитель 1", 7120f, 7880f);
        positionedPerson(state, "parent2", "Новый родитель 2", 7440f, 7880f);
        positionedPerson(state, "aunt", "Тетя", 7280f, 8360f);
        positionedPerson(state, "auntPartner", "Партнёр тети", 7600f, 8360f);
        positionedPerson(state, "cousin", "Ребёнок тети", 7440f, 8840f);
        positionedPerson(state, "newChild", "Новый ребёнок", 8240f, 8360f);
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        link(state, "partner", "parent1", "parent2");
        link(state, "parent", "parent1", "aunt");
        link(state, "parent", "parent2", "aunt");
        link(state, "partner", "aunt", "auntPartner");
        link(state, "parent", "aunt", "cousin");
        link(state, "parent", "auntPartner", "cousin");
        link(state, "parent", "parent1", "newChild");
        link(state, "parent", "parent2", "newChild");
        state.rootId = "root";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newChild"),
            "parent1",
            "children");

        assertEquals(9560f, state.people.get("root").x, EPSILON);
        assertEquals(9400f, state.people.get("father").x, EPSILON);
        assertEquals(9720f, state.people.get("mother").x, EPSILON);
        assertEquals(7520f, state.people.get("parent1").x, EPSILON);
        assertEquals(7840f, state.people.get("parent2").x, EPSILON);
        assertEquals(8360f, state.people.get("aunt").y, EPSILON);
        assertEquals(8840f, state.people.get("cousin").y, EPSILON);
        assertEquals(8360f, state.people.get("newChild").y, EPSILON);
    }

    @Test
    public void newBrotherUsesSiblingRowAndRecentersParents() {
        TreeState state = new TreeState();
        positionedPerson(state, "parent1", "Новый родитель 1", 6960f, 6920f);
        positionedPerson(state, "parent2", "Новый родитель 2", 7280f, 6920f);
        positionedPerson(state, "existingChild", "Новый родитель 1", 7200f, 7400f);
        positionedPerson(state, "existingPartner", "Новый родитель 2", 7520f, 7400f);
        positionedPerson(state, "farDescendant", "Нижний потомок", 10000f, 7880f);
        positionedPerson(state, "newBrother", "Новый ребёнок", 6360f, 7400f);
        link(state, "partner", "parent1", "parent2");
        link(state, "parent", "parent1", "existingChild");
        link(state, "parent", "parent2", "existingChild");
        link(state, "partner", "existingChild", "existingPartner");
        link(state, "parent", "existingChild", "farDescendant");
        link(state, "parent", "existingPartner", "farDescendant");
        link(state, "parent", "parent1", "newBrother");
        link(state, "parent", "parent2", "newBrother");
        link(state, "sibling", "existingChild", "newBrother");
        state.rootId = "existingChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newBrother"),
            "parent1",
            "children");

        assertEquals(6720f, state.people.get("newBrother").x, EPSILON);
        assertEquals(7200f, state.people.get("existingChild").x, EPSILON);
        assertEquals(7520f, state.people.get("existingPartner").x, EPSILON);
        assertEquals(10000f, state.people.get("farDescendant").x, EPSILON);
        assertEquals(6800f, state.people.get("parent1").x, EPSILON);
        assertEquals(7120f, state.people.get("parent2").x, EPSILON);
    }

    @Test
    public void siblingsOfLeftPartnerAllGrowOutwardToTheLeft() {
        TreeState state = new TreeState();
        positionedPerson(state, "leftPartner", "Левый партнёр", 9880f, 6200f);
        positionedPerson(state, "rightPartner", "Правый партнёр", 10200f, 6200f);
        positionedPerson(state, "child", "Ребёнок", 10040f, 6680f);
        positionedPerson(state, "sibling1", "Брат 1", 10680f, 6200f);
        positionedPerson(state, "sibling2", "Брат 2", 9400f, 6200f);
        link(state, "partner", "leftPartner", "rightPartner");
        link(state, "parent", "leftPartner", "child");
        link(state, "parent", "rightPartner", "child");
        link(state, "sibling", "leftPartner", "sibling1");
        link(state, "sibling", "leftPartner", "sibling2");
        link(state, "sibling", "sibling1", "sibling2");
        state.rootId = "child";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("sibling1", "sibling2"),
            "leftPartner",
            "siblings");

        assertEquals(9080f, state.people.get("sibling1").x, EPSILON);
        assertEquals(9400f, state.people.get("sibling2").x, EPSILON);
        assertEquals(9880f, state.people.get("leftPartner").x, EPSILON);
        assertEquals(10200f, state.people.get("rightPartner").x, EPSILON);
    }

    @Test
    public void secondParentFamilyRedistributesBothParentBranches() {
        TreeState state = new TreeState();
        positionedPerson(state, "leftChild", "Левый партнёр", 10040f, 6680f);
        positionedPerson(state, "rightChild", "Правый партнёр", 10360f, 6680f);
        positionedPerson(state, "leftParent1", "Левый родитель 1", 9880f, 6200f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 10200f, 6200f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 10520f, 6200f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 10840f, 6200f);
        link(state, "partner", "leftChild", "rightChild");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftChild");
        link(state, "parent", "leftParent2", "leftChild");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "rightChild");
        link(state, "parent", "rightParent2", "rightChild");
        state.rootId = "leftChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("rightParent1", "rightParent2"),
            "rightChild",
            "parents");

        assertEquals(9640f, state.people.get("leftParent1").x, EPSILON);
        assertEquals(9960f, state.people.get("leftParent2").x, EPSILON);
        assertEquals(10440f, state.people.get("rightParent1").x, EPSILON);
        assertEquals(10760f, state.people.get("rightParent2").x, EPSILON);
        assertEquals(10040f, state.people.get("leftChild").x, EPSILON);
        assertEquals(10360f, state.people.get("rightChild").x, EPSILON);
    }

    @Test
    public void secondParentPairReturnsToParentRowAndSeparatesBothFamilies() {
        TreeState state = new TreeState();
        positionedPerson(state, "leftChild", "Левый партнёр", 11920f, 7760f);
        positionedPerson(state, "rightChild", "Правый партнёр", 12240f, 7760f);
        positionedPerson(state, "leftParent1", "Левый родитель 1", 11760f, 7280f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 12080f, 7280f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 12080f, 7480f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 12400f, 7280f);
        link(state, "partner", "leftChild", "rightChild");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftChild");
        link(state, "parent", "leftParent2", "leftChild");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "rightChild");
        link(state, "parent", "rightParent2", "rightChild");
        state.rootId = "rightChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("rightParent1", "rightParent2"),
            "rightChild",
            "parents");

        assertEquals(11520f, state.people.get("leftParent1").x, EPSILON);
        assertEquals(11840f, state.people.get("leftParent2").x, EPSILON);
        assertEquals(12320f, state.people.get("rightParent1").x, EPSILON);
        assertEquals(12640f, state.people.get("rightParent2").x, EPSILON);
        assertEquals(7280f, state.people.get("leftParent1").y, EPSILON);
        assertEquals(7280f, state.people.get("leftParent2").y, EPSILON);
        assertEquals(7280f, state.people.get("rightParent1").y, EPSILON);
        assertEquals(7280f, state.people.get("rightParent2").y, EPSILON);
    }

    @Test
    public void firstDescendantWidensOnlyItsSiblingRowAndRecentersParents() {
        TreeState state = new TreeState();
        positionedPerson(state, "parent1", "Родитель 1", 9080f, 6200f);
        positionedPerson(state, "parent2", "Родитель 2", 9400f, 6200f);
        positionedPerson(state, "leftChild", "Ребёнок слева", 8920f, 6680f);
        positionedPerson(state, "middleChild", "Ребёнок в центре", 9240f, 6680f);
        positionedPerson(state, "rightChild", "Ребёнок справа", 9560f, 6680f);
        positionedPerson(state, "neighborBranch", "Соседняя семья", 10040f, 6680f);
        positionedPerson(state, "grandchild", "Внук", 9080f, 7160f);
        link(state, "partner", "parent1", "parent2");
        for (String child : new String[]{"leftChild", "middleChild", "rightChild"}) {
            link(state, "parent", "parent1", child);
            link(state, "parent", "parent2", child);
        }
        link(state, "parent", "middleChild", "grandchild");
        state.rootId = "middleChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("grandchild"),
            "middleChild",
            "children");

        assertEquals(8600f, state.people.get("leftChild").x, EPSILON);
        assertEquals(9080f, state.people.get("middleChild").x, EPSILON);
        assertEquals(9560f, state.people.get("rightChild").x, EPSILON);
        assertEquals(8920f, state.people.get("parent1").x, EPSILON);
        assertEquals(9240f, state.people.get("parent2").x, EPSILON);
        assertEquals(9080f, state.people.get("grandchild").x, EPSILON);
        assertEquals(10040f, state.people.get("neighborBranch").x, EPSILON);
    }

    @Test
    public void firstPartnerWidensTheSameBranchWithoutUsingDescendantWidth() {
        TreeState state = new TreeState();
        positionedPerson(state, "parent1", "Родитель 1", 8920f, 6200f);
        positionedPerson(state, "parent2", "Родитель 2", 9240f, 6200f);
        positionedPerson(state, "leftChild", "Ребёнок слева", 8600f, 6680f);
        positionedPerson(state, "middleChild", "Ребёнок в центре", 9080f, 6680f);
        positionedPerson(state, "rightChild", "Ребёнок справа", 9560f, 6680f);
        positionedPerson(state, "neighborBranch", "Соседняя семья", 10040f, 6680f);
        positionedPerson(state, "grandchild", "Внук", 9080f, 7160f);
        positionedPerson(state, "newPartner", "Новый партнёр", 8440f, 6680f);
        link(state, "partner", "parent1", "parent2");
        for (String child : new String[]{"leftChild", "middleChild", "rightChild"}) {
            link(state, "parent", "parent1", child);
            link(state, "parent", "parent2", child);
        }
        link(state, "parent", "middleChild", "grandchild");
        link(state, "partner", "middleChild", "newPartner");
        state.rootId = "middleChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newPartner"),
            "middleChild",
            "partner");

        assertEquals(8280f, state.people.get("leftChild").x, EPSILON);
        assertEquals(8760f, state.people.get("newPartner").x, EPSILON);
        assertEquals(9080f, state.people.get("middleChild").x, EPSILON);
        assertEquals(9560f, state.people.get("rightChild").x, EPSILON);
        assertEquals(8760f, state.people.get("parent1").x, EPSILON);
        assertEquals(9080f, state.people.get("parent2").x, EPSILON);
        assertEquals(9080f, state.people.get("grandchild").x, EPSILON);
        assertEquals(10040f, state.people.get("neighborBranch").x, EPSILON);
    }

    @Test
    public void addingRightSiblingsRecentersIndependentLeftParentsWhenSpaceExists() {
        TreeState state = partnerBranchesWithThreeRightSiblings();
        positionedPerson(state, "leftParent1", "Левый родитель 1", 8280f, 6080f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 8600f, 6080f);
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftPartner");
        link(state, "parent", "leftParent2", "leftPartner");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("sibling3"),
            "mainChild",
            "siblings");

        assertEquals(8520f, state.people.get("leftParent1").x, EPSILON);
        assertEquals(8840f, state.people.get("leftParent2").x, EPSILON);
        assertEquals(9400f, state.people.get("rightParent1").x, EPSILON);
        assertEquals(9720f, state.people.get("rightParent2").x, EPSILON);
        assertEquals(8680f, state.people.get("leftPartner").x, EPSILON);
        assertEquals(9000f, state.people.get("mainChild").x, EPSILON);
    }

    @Test
    public void firstChildExpandsRightSiblingsWithoutTranslatingWholeBranch() {
        TreeState state = partnerBranchesWithThreeRightSiblings();
        positionedPerson(state, "leftParent1", "Левый родитель 1", 8520f, 6080f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 8840f, 6080f);
        positionedPerson(state, "childOfSibling1", "Ребёнок брата 1", 9480f, 7040f);
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftPartner");
        link(state, "parent", "leftParent2", "leftPartner");
        link(state, "parent", "sibling1", "childOfSibling1");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("childOfSibling1"),
            "sibling1",
            "children");

        assertEquals(8680f, state.people.get("leftPartner").x, EPSILON);
        assertEquals(9000f, state.people.get("mainChild").x, EPSILON);
        assertEquals(9480f, state.people.get("sibling1").x, EPSILON);
        assertEquals(9960f, state.people.get("sibling2").x, EPSILON);
        assertEquals(10280f, state.people.get("sibling3").x, EPSILON);
        assertEquals(9480f, state.people.get("childOfSibling1").x, EPSILON);
        assertEquals(9480f, state.people.get("rightParent1").x, EPSILON);
        assertEquals(9800f, state.people.get("rightParent2").x, EPSILON);
        assertEquals(8520f, state.people.get("leftParent1").x, EPSILON);
    }

    @Test
    public void newWideSiblingBranchMovesOutwardAndKeepsChildrenCentered() {
        TreeState state = partnerBranchesWithThreeRightSiblings();
        positionedPerson(state, "leftParent1", "Левый родитель 1", 8520f, 6080f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 8840f, 6080f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 9480f, 6080f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 9800f, 6080f);
        state.people.get("sibling2").x = 9960f;
        state.people.get("sibling3").x = 10280f;
        positionedPerson(state, "childOfSibling1", "Ребёнок брата 1", 9480f, 7040f);
        positionedPerson(state, "child1", "Ребёнок 1", 10520f, 7040f);
        positionedPerson(state, "child2", "Ребёнок 2", 10840f, 7040f);
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftPartner");
        link(state, "parent", "leftParent2", "leftPartner");
        link(state, "parent", "sibling1", "childOfSibling1");
        link(state, "parent", "sibling2", "child1");
        link(state, "parent", "sibling2", "child2");
        link(state, "sibling", "child1", "child2");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("child1", "child2"),
            "sibling2",
            "children");

        assertEquals(8680f, state.people.get("leftPartner").x, EPSILON);
        assertEquals(9000f, state.people.get("mainChild").x, EPSILON);
        assertEquals(9480f, state.people.get("sibling1").x, EPSILON);
        assertEquals(9960f, state.people.get("sibling3").x, EPSILON);
        assertEquals(10440f, state.people.get("sibling2").x, EPSILON);
        assertEquals(10280f, state.people.get("child1").x, EPSILON);
        assertEquals(10600f, state.people.get("child2").x, EPSILON);
        assertEquals(9560f, state.people.get("rightParent1").x, EPSILON);
        assertEquals(9880f, state.people.get("rightParent2").x, EPSILON);
        assertEquals(8520f, state.people.get("leftParent1").x, EPSILON);
    }

    @Test
    public void newChildrenStayCenteredWhileNeighborBranchMovesOutward() {
        TreeState state = problemSevenFamily();
        positionedPerson(state, "newChild1", "Новый ребёнок 1", 11440f, 8000f);
        positionedPerson(state, "newChild2", "Новый ребёнок 2", 11760f, 8000f);
        link(state, "parent", "sibling2", "newChild1");
        link(state, "parent", "sibling2", "newChild2");
        link(state, "sibling", "newChild1", "newChild2");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("newChild1", "newChild2"),
            "sibling2",
            "children");

        assertEquals(11280f, state.people.get("sibling2").x, EPSILON);
        assertEquals(11120f, state.people.get("newChild1").x, EPSILON);
        assertEquals(11440f, state.people.get("newChild2").x, EPSILON);
        assertEquals(10480f, state.people.get("sibling1").x, EPSILON);
        assertEquals(10320f, state.people.get("sibling1Child1").x, EPSILON);
        assertEquals(10640f, state.people.get("sibling1Child2").x, EPSILON);
        assertEquals(10960f, state.people.get("grandparent1").x, EPSILON);
        assertEquals(11280f, state.people.get("grandparent2").x, EPSILON);
    }

    @Test
    public void addedPartnerCentersAllExistingChildrenUnderParentPair() {
        TreeState state = problemSevenFamily();
        setProblemSevenSolvedPositions(state);
        positionedPerson(state, "child1", "Ребёнок 1", 11120f, 8000f);
        positionedPerson(state, "child2", "Ребёнок 2", 11440f, 8000f);
        positionedPerson(state, "newPartner", "Новый партнёр", 10960f, 7520f);
        link(state, "parent", "sibling2", "child1");
        link(state, "parent", "sibling2", "child2");
        link(state, "partner", "sibling2", "newPartner");
        link(state, "parent", "newPartner", "child1");
        link(state, "parent", "newPartner", "child2");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newPartner"),
            "sibling2",
            "partner");

        assertEquals(10960f, state.people.get("newPartner").x, EPSILON);
        assertEquals(11280f, state.people.get("sibling2").x, EPSILON);
        assertEquals(10960f, state.people.get("child1").x, EPSILON);
        assertEquals(11280f, state.people.get("child2").x, EPSILON);
        assertEquals(10480f, state.people.get("sibling1").x, EPSILON);
        assertEquals(11760f, state.people.get("familyPartner").x, EPSILON);
    }

    @Test
    public void secondParentJoinsExistingFamilyWithoutPullingParentFromSiblingRow() {
        TreeState state = problemSevenFamily();
        setProblemSevenSolvedPositions(state);
        positionedPerson(state, "child1", "Ребёнок 1", 11120f, 8000f);
        positionedPerson(state, "child2", "Ребёнок 2", 11440f, 8000f);
        positionedPerson(state, "newParent", "Новый родитель", 10960f, 7520f);
        link(state, "parent", "sibling2", "child1");
        link(state, "parent", "sibling2", "child2");
        link(state, "partner", "sibling2", "newParent");
        link(state, "parent", "newParent", "child1");
        link(state, "parent", "newParent", "child2");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newParent"),
            "child1",
            "parents");

        assertEquals(10960f, state.people.get("newParent").x, EPSILON);
        assertEquals(11280f, state.people.get("sibling2").x, EPSILON);
        assertEquals(10960f, state.people.get("child1").x, EPSILON);
        assertEquals(11280f, state.people.get("child2").x, EPSILON);
        assertEquals(10480f, state.people.get("sibling1").x, EPSILON);
        assertEquals(11760f, state.people.get("familyPartner").x, EPSILON);
    }

    @Test
    public void childFamiliesKeepFiveGridCellsBetweenTheirExtremeCards() {
        TreeState state = problemNineBaseFamily();
        positionedPerson(state, "unclePartner", "Партнёр дяди", 12000f, 7040f);
        positionedPerson(state, "uncleChild1", "Ребёнок дяди 1", 12000f, 7520f);
        positionedPerson(state, "uncleChild2", "Ребёнок дяди 2", 12320f, 7520f);
        link(state, "partner", "uncle", "unclePartner");
        for (String child : new String[]{"uncleChild1", "uncleChild2"}) {
            link(state, "parent", "uncle", child);
            link(state, "parent", "unclePartner", child);
        }

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("uncleChild1", "uncleChild2"),
            "uncle",
            "children");

        assertEquals(12160f, state.people.get("unclePartner").x, EPSILON);
        assertEquals(12480f, state.people.get("uncle").x, EPSILON);
        assertEquals(12160f, state.people.get("uncleChild1").x, EPSILON);
        assertEquals(12480f, state.people.get("uncleChild2").x, EPSILON);
        assertEquals(
            TreeLayoutEngine.GRID * 5f,
            state.people.get("uncleChild1").x
                - state.people.get("maternalSibling2").x
                - TreeLayoutEngine.CARD_W,
            EPSILON);
    }

    @Test
    public void allSiblingsOfPartnerStayOnOuterSideAndAncestryDoesNotCrossPartner() {
        TreeState state = problemNineBaseFamily();
        state.people.get("unclePartner").x = 12160f;
        state.people.get("uncle").x = 12480f;
        state.people.get("uncleChild1").x = 12160f;
        state.people.get("uncleChild2").x = 12480f;
        positionedPerson(state, "greatGrandparent1", "Прадед", 11600f, 6080f);
        positionedPerson(state, "greatGrandparent2", "Прабабушка", 11920f, 6080f);
        positionedPerson(state, "grandfatherBrother1", "Брат деда 1", 10960f, 6560f);
        positionedPerson(state, "grandfatherBrother2", "Брат деда 2", 11280f, 6560f);
        positionedPerson(state, "grandfatherBrother3", "Брат деда 3", 12560f, 6560f);
        link(state, "partner", "greatGrandparent1", "greatGrandparent2");
        for (String child : new String[]{
            "maternalGrandfather",
            "grandfatherBrother1",
            "grandfatherBrother2",
            "grandfatherBrother3"
        }) {
            link(state, "parent", "greatGrandparent1", child);
            link(state, "parent", "greatGrandparent2", child);
        }

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList(
                "grandfatherBrother1",
                "grandfatherBrother2",
                "grandfatherBrother3"),
            "maternalGrandfather",
            "siblings");

        assertEquals(11520f, state.people.get("grandfatherBrother1").x, EPSILON);
        assertEquals(11840f, state.people.get("grandfatherBrother2").x, EPSILON);
        assertEquals(12160f, state.people.get("grandfatherBrother3").x, EPSILON);
        assertEquals(12640f, state.people.get("maternalGrandfather").x, EPSILON);
        assertEquals(12960f, state.people.get("maternalGrandmother").x, EPSILON);
        assertEquals(11920f, state.people.get("greatGrandparent1").x, EPSILON);
        assertEquals(12240f, state.people.get("greatGrandparent2").x, EPSILON);
        assertTrue(
            state.people.get("grandfatherBrother1").x
                >= state.people.get("mother").x);
        assertEquals(12480f, state.people.get("unclePartner").x, EPSILON);
        assertEquals(12800f, state.people.get("uncle").x, EPSILON);
        assertEquals(12480f, state.people.get("uncleChild1").x, EPSILON);
        assertEquals(12800f, state.people.get("uncleChild2").x, EPSILON);
    }

    @Test
    public void addingPartnerAtLeftWorkspaceEdgeDoesNotTeleportMainBranchPastMaternalBranch() {
        TreeState state = new TreeState();
        positionedPerson(state, "paternalGrandfather", "Дед по отцу", 120f, 440f);
        positionedPerson(state, "paternalGrandmother", "Бабушка по отцу", 440f, 440f);
        positionedPerson(state, "aunt", "Тётя", 40f, 920f);
        positionedPerson(state, "newPartner", "Партнёр тёти", -280f, 920f);
        positionedPerson(state, "father", "Отец", 520f, 920f);
        positionedPerson(state, "mother", "Мать", 840f, 920f);
        positionedPerson(state, "rootLeftSibling", "Сестра", 360f, 1400f);
        positionedPerson(state, "root", "Я", 680f, 1400f);
        positionedPerson(state, "rootRightSibling", "Брат", 1000f, 1400f);
        positionedPerson(state, "maternalGrandfather", "Дед по матери", 1160f, 440f);
        positionedPerson(state, "maternalGrandmother", "Бабушка по матери", 1480f, 440f);
        positionedPerson(state, "unclePartner", "Партнёр дяди", 1480f, 920f);
        positionedPerson(state, "uncle", "Дядя", 1800f, 920f);
        positionedPerson(state, "cousin1", "Двоюродный брат", 1480f, 1400f);
        positionedPerson(state, "cousin2", "Двоюродная сестра", 1800f, 1400f);
        link(state, "partner", "paternalGrandfather", "paternalGrandmother");
        link(state, "parent", "paternalGrandfather", "aunt");
        link(state, "parent", "paternalGrandmother", "aunt");
        link(state, "parent", "paternalGrandfather", "father");
        link(state, "parent", "paternalGrandmother", "father");
        link(state, "partner", "newPartner", "aunt");
        link(state, "partner", "father", "mother");
        for (String child : new String[]{"rootLeftSibling", "root", "rootRightSibling"}) {
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
        }
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        link(state, "parent", "maternalGrandfather", "mother");
        link(state, "parent", "maternalGrandmother", "mother");
        link(state, "parent", "maternalGrandfather", "uncle");
        link(state, "parent", "maternalGrandmother", "uncle");
        link(state, "partner", "unclePartner", "uncle");
        for (String child : new String[]{"cousin1", "cousin2"}) {
            link(state, "parent", "unclePartner", child);
            link(state, "parent", "uncle", child);
        }
        state.rootId = "root";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newPartner"),
            "aunt",
            "partner");

        assertTrue("основной ствол не должен улетать на тысячи пикселей", state.people.get("root").x <= 1000f);
        assertTrue("материнская ветка должна остаться правее матери", state.people.get("uncle").x > state.people.get("mother").x);
        assertEquals(920f, state.people.get("father").y, EPSILON);
        assertEquals(1400f, state.people.get("root").y, EPSILON);
    }

    @Test
    public void wideningMiddleSiblingWithoutParentsReordersBeforeSimpleOuterSibling() {
        TreeState state = new TreeState();
        positionedPerson(state, "simpleSibling", "Брат 1", 1960f, 440f);
        positionedPerson(state, "changedPartner", "Партнёр брата 2", 2440f, 440f);
        positionedPerson(state, "changedSibling", "Брат 2", 2760f, 440f);
        positionedPerson(state, "lineageSibling", "Дед", 3240f, 440f);
        positionedPerson(state, "lineagePartner", "Бабушка", 3560f, 440f);
        positionedPerson(state, "newChild1", "Ребёнок 1", 2440f, 920f);
        positionedPerson(state, "newChild2", "Ребёнок 2", 2760f, 920f);
        positionedPerson(state, "father", "Отец", 3880f, 920f);
        positionedPerson(state, "mother", "Мать", 4200f, 920f);
        positionedPerson(state, "root", "Я", 4040f, 1400f);
        link(state, "sibling", "simpleSibling", "changedSibling");
        link(state, "sibling", "simpleSibling", "lineageSibling");
        link(state, "sibling", "changedSibling", "lineageSibling");
        link(state, "partner", "changedPartner", "changedSibling");
        link(state, "parent", "changedPartner", "newChild1");
        link(state, "parent", "changedSibling", "newChild1");
        link(state, "parent", "changedPartner", "newChild2");
        link(state, "parent", "changedSibling", "newChild2");
        link(state, "partner", "lineageSibling", "lineagePartner");
        link(state, "parent", "lineageSibling", "father");
        link(state, "parent", "lineagePartner", "father");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        state.rootId = "root";

        float rootX = state.people.get("root").x;
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("newChild1", "newChild2"),
            "changedSibling",
            "children");

        assertTrue(
            "широкая ветка должна уйти наружу перед простым братом",
            state.people.get("changedSibling").x < state.people.get("simpleSibling").x);
        assertEquals("основной ствол должен остаться на месте", rootX, state.people.get("root").x, EPSILON);
        assertEquals(
            (state.people.get("changedPartner").x + state.people.get("changedSibling").x) / 2f,
            (state.people.get("newChild1").x + state.people.get("newChild2").x) / 2f,
            EPSILON);
    }

    @Test
    public void localAdditionNeverChangesExistingGenerationRows() {
        TreeState state = problemNineBaseFamily();
        positionedPerson(state, "remoteGrandparent", "Чужой прадед", 9000f, 6080f);
        positionedPerson(state, "newParent1", "Новый родитель 1", 11200f, 6080f);
        positionedPerson(state, "newParent2", "Новый родитель 2", 11520f, 6080f);
        link(state, "partner", "newParent1", "newParent2");
        link(state, "parent", "newParent1", "maternalGrandfather");
        link(state, "parent", "newParent2", "maternalGrandfather");
        java.util.Map<String, Float> rows = new java.util.LinkedHashMap<>();
        for (Person person : state.people.values()) {
            if (!person.id.equals("newParent1") && !person.id.equals("newParent2")) {
                rows.put(person.id, person.y);
            }
        }

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("newParent1", "newParent2"),
            "maternalGrandfather",
            "parents");

        for (java.util.Map.Entry<String, Float> row : rows.entrySet()) {
            assertEquals(
                "локальное добавление не должно менять поколение " + row.getKey(),
                row.getValue(),
                state.people.get(row.getKey()).y,
                EPSILON);
        }
    }

    @Test
    public void collidingRightParentFamilyDoesNotDragDistantLeftAncestors() {
        TreeState state = new TreeState();
        positionedPerson(state, "commonParent1", "Общий родитель 1", 10240f, 6480f);
        positionedPerson(state, "commonParent2", "Общий родитель 2", 10560f, 6480f);
        positionedPerson(state, "newPgmParent1", "Новый родитель PGM 1", 11400f, 6480f);
        positionedPerson(state, "newPgmParent2", "Новый родитель PGM 2", 11720f, 6480f);
        positionedPerson(state, "mgfParent1", "Родитель MGF 1", 12200f, 6480f);
        positionedPerson(state, "mgfParent2", "Родитель MGF 2", 12520f, 6480f);
        positionedPerson(state, "pgfBrother2Partner", "Партнёр брата PGF 2", 9320f, 6960f);
        positionedPerson(state, "pgfBrother2", "Брат PGF 2", 9640f, 6960f);
        positionedPerson(state, "pgfBrother1Partner", "Партнёр брата PGF 1", 10120f, 6960f);
        positionedPerson(state, "pgfBrother1", "Брат PGF 1", 10440f, 6960f);
        positionedPerson(state, "pgf", "PGF", 11320f, 6960f);
        positionedPerson(state, "pgm", "PGM", 11640f, 6960f);
        positionedPerson(state, "mgf", "MGF", 12360f, 6960f);
        positionedPerson(state, "mgm", "MGM", 12680f, 6960f);
        positionedPerson(state, "brother2Child1", "Ребёнок брата PGF 2", 9320f, 7440f);
        positionedPerson(state, "brother2Child2", "Ребёнок брата PGF 2", 9640f, 7440f);
        positionedPerson(state, "brother1Child", "Ребёнок брата PGF 1", 10280f, 7440f);
        positionedPerson(state, "auntPartner", "Партнёр тёти", 10920f, 7440f);
        positionedPerson(state, "aunt", "Тётя", 11240f, 7440f);
        positionedPerson(state, "father", "Отец", 11720f, 7440f);
        positionedPerson(state, "mother", "Мать", 12040f, 7440f);
        positionedPerson(state, "root", "Я", 11880f, 7920f);
        link(state, "partner", "commonParent1", "commonParent2");
        for (String child : new String[]{"pgfBrother2", "pgfBrother1", "pgf"}) {
            link(state, "parent", "commonParent1", child);
            link(state, "parent", "commonParent2", child);
        }
        link(state, "sibling", "pgfBrother2", "pgfBrother1");
        link(state, "sibling", "pgfBrother1", "pgf");
        link(state, "partner", "pgfBrother2Partner", "pgfBrother2");
        for (String child : new String[]{"brother2Child1", "brother2Child2"}) {
            link(state, "parent", "pgfBrother2Partner", child);
            link(state, "parent", "pgfBrother2", child);
        }
        link(state, "partner", "pgfBrother1Partner", "pgfBrother1");
        link(state, "parent", "pgfBrother1Partner", "brother1Child");
        link(state, "parent", "pgfBrother1", "brother1Child");
        link(state, "partner", "pgf", "pgm");
        for (String child : new String[]{"aunt", "father"}) {
            link(state, "parent", "pgf", child);
            link(state, "parent", "pgm", child);
        }
        link(state, "partner", "auntPartner", "aunt");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "root");
        link(state, "parent", "mother", "root");
        link(state, "partner", "newPgmParent1", "newPgmParent2");
        link(state, "parent", "newPgmParent1", "pgm");
        link(state, "parent", "newPgmParent2", "pgm");
        link(state, "partner", "mgf", "mgm");
        link(state, "partner", "mgfParent1", "mgfParent2");
        link(state, "parent", "mgfParent1", "mgf");
        link(state, "parent", "mgfParent2", "mgf");
        state.rootId = "root";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("newPgmParent1", "newPgmParent2"),
            "pgm",
            "parents");

        assertEquals(
            "common=" + state.people.get("commonParent1").x + ","
                + state.people.get("commonParent2").x
                + " b2=" + state.people.get("pgfBrother2Partner").x + ","
                + state.people.get("pgfBrother2").x
                + " b1=" + state.people.get("pgfBrother1Partner").x + ","
                + state.people.get("pgfBrother1").x
                + " pg=" + state.people.get("pgf").x + "," + state.people.get("pgm").x
                + " aunt=" + state.people.get("auntPartner").x + ","
                + state.people.get("aunt").x,
            10320f,
            state.people.get("commonParent1").x,
            EPSILON);
        assertEquals(10640f, state.people.get("commonParent2").x, EPSILON);
        assertEquals(11400f, state.people.get("newPgmParent1").x, EPSILON);
        assertEquals(11720f, state.people.get("newPgmParent2").x, EPSILON);
        assertEquals(12200f, state.people.get("mgfParent1").x, EPSILON);
        assertEquals(12520f, state.people.get("mgfParent2").x, EPSILON);
        assertEquals(9400f, state.people.get("pgfBrother2Partner").x, EPSILON);
        assertEquals(9720f, state.people.get("pgfBrother2").x, EPSILON);
        assertEquals(10200f, state.people.get("pgfBrother1Partner").x, EPSILON);
        assertEquals(10520f, state.people.get("pgfBrother1").x, EPSILON);
        assertEquals(11240f, state.people.get("pgf").x, EPSILON);
        assertEquals(11560f, state.people.get("pgm").x, EPSILON);
        assertEquals(10840f, state.people.get("auntPartner").x, EPSILON);
        assertEquals(11160f, state.people.get("aunt").x, EPSILON);
        assertEquals(11720f, state.people.get("father").x, EPSILON);
        assertEquals(12040f, state.people.get("mother").x, EPSILON);
        assertEquals(11880f, state.people.get("root").x, EPSILON);
        assertEquals(
            (state.people.get("pgfBrother2").x + state.people.get("pgf").x
                + TreeLayoutEngine.CARD_W) / 2f,
            (state.people.get("commonParent1").x + state.people.get("commonParent2").x
                + TreeLayoutEngine.CARD_W) / 2f,
            EPSILON);

        positionedPerson(state, "pgmSibling1", "Брат или сестра PGM 1", 11640f, 6960f);
        positionedPerson(state, "pgmSibling2", "Брат или сестра PGM 2", 11960f, 6960f);
        for (String sibling : new String[]{"pgmSibling1", "pgmSibling2"}) {
            link(state, "parent", "newPgmParent1", sibling);
            link(state, "parent", "newPgmParent2", sibling);
            link(state, "sibling", "pgm", sibling);
        }
        link(state, "sibling", "pgmSibling1", "pgmSibling2");

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("pgmSibling1", "pgmSibling2"),
            "pgm",
            "siblings");

        float partnerRowRight = maxRight(state, "pgf", "pgm");
        assertTrue(state.people.get("pgmSibling1").x >= partnerRowRight + TreeLayoutEngine.GRID * 5f);
        assertEquals(
            TreeLayoutEngine.SIBLING_GAP,
            state.people.get("pgmSibling2").x
                - state.people.get("pgmSibling1").x
                - TreeLayoutEngine.CARD_W,
            EPSILON);
        assertEquals(11880f, state.people.get("root").x, EPSILON);
        assertEquals(7920f, state.people.get("root").y, EPSILON);
        assertNoOverlaps(state);
    }

    @Test
    public void nextAutomaticAdditionRepairsChildDraggedOntoAncestorRow() {
        TreeState state = new TreeState();
        positionedPerson(state, "unclePartner", "Партнёр", 12680f, 7440f);
        positionedPerson(state, "uncle", "Дядя", 13000f, 7440f);
        positionedPerson(state, "draggedChild", "Случайно сдвинутый ребёнок", 12680f, 6200f);
        positionedPerson(state, "existingChild", "Ребёнок 2", 13000f, 7920f);
        positionedPerson(state, "newChild", "Новый ребёнок", 13320f, 0f);
        link(state, "partner", "unclePartner", "uncle");
        for (String child : new String[]{"draggedChild", "existingChild", "newChild"}) {
            link(state, "parent", "unclePartner", child);
            link(state, "parent", "uncle", child);
        }
        link(state, "sibling", "draggedChild", "existingChild");
        link(state, "sibling", "existingChild", "newChild");
        state.rootId = "existingChild";

        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newChild"),
            "uncle",
            "children");

        assertEquals(7920f, state.people.get("draggedChild").y, EPSILON);
        assertEquals(7920f, state.people.get("existingChild").y, EPSILON);
        assertEquals(7920f, state.people.get("newChild").y, EPSILON);
    }

    @Test
    public void problemElevenPartnerPushesOnlyMaternalAncestorSiblingGroupOutward() {
        TreeState state = problemElevenBase();
        positionedPerson(state, "sidePartner", "Партнёр 1", 11040f, 6560f);
        link(state, "partner", "sidePartner", "side1");

        float rootX = state.people.get("root").x;
        float fatherX = state.people.get("father").x;
        float motherX = state.people.get("mother").x;
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("sidePartner"),
            "side1",
            "partner");

        assertEquals("основной ствол остаётся на месте", rootX, state.people.get("root").x, EPSILON);
        assertEquals(fatherX, state.people.get("father").x, EPSILON);
        assertEquals(motherX, state.people.get("mother").x, EPSILON);
        assertTrue(
            "вся боковая материнская группа должна начинаться правее матери",
            state.people.get("sidePartner").x
                >= state.people.get("mother").x + TreeLayoutEngine.GRID * 2f);
        assertEquals(
            "общие родители должны остаться по центру крайних детей",
            (state.people.get("lineageGrandfather").x + state.people.get("side1").x
                + TreeLayoutEngine.CARD_W) / 2f,
            (state.people.get("commonParent1").x + state.people.get("commonParent2").x
                + TreeLayoutEngine.CARD_W) / 2f,
            EPSILON);
    }

    @Test
    public void problemElevenSecondChildMakesRoomWithoutMovingMainDescendants() {
        TreeState state = problemElevenBase();
        positionedPerson(state, "sidePartner", "Партнёр 1", 11600f, 6560f);
        link(state, "partner", "sidePartner", "side1");
        for (String id : new String[]{"lineageGrandfather", "lineageGrandmother", "side1",
            "side2", "side3", "commonParent1", "commonParent2"}) {
            state.people.get(id).x += 560f;
        }
        positionedPerson(state, "upperParent1", "Родитель 3", 10640f, 6080f);
        positionedPerson(state, "upperParent2", "3", 10960f, 6080f);
        link(state, "partner", "upperParent1", "upperParent2");
        link(state, "parent", "upperParent1", "maternalGrandmother");
        link(state, "parent", "upperParent2", "maternalGrandmother");
        positionedPerson(state, "newSon", "Новый ребёнок", 10560f, 6560f);
        link(state, "parent", "upperParent1", "newSon");
        link(state, "parent", "upperParent2", "newSon");
        link(state, "sibling", "maternalGrandmother", "newSon");

        float rootX = state.people.get("root").x;
        float fatherX = state.people.get("father").x;
        float motherX = state.people.get("mother").x;
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Collections.singletonList("newSon"),
            "upperParent2",
            "children");

        Person existingSon = state.people.get("maternalGrandmother");
        Person newSon = state.people.get("newSon");
        assertTrue(
            "новый сын должен стоять справа с отступом семейной ветки",
            newSon.x >= existingSon.x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID * 5f);
        assertEquals(
            (existingSon.x + newSon.x + TreeLayoutEngine.CARD_W) / 2f,
            (state.people.get("upperParent1").x + state.people.get("upperParent2").x
                + TreeLayoutEngine.CARD_W) / 2f,
            EPSILON);
        assertEquals(rootX, state.people.get("root").x, EPSILON);
        assertEquals(fatherX, state.people.get("father").x, EPSILON);
        assertEquals(motherX, state.people.get("mother").x, EPSILON);
        assertNoOverlaps(state);
    }

    @Test
    public void mirroredParentBranchesStaySymmetricAfterAddingLastGrandparents() {
        TreeState state = new TreeState();
        positionedPerson(state, "leftChild", "Партнёр", 11560f, 7920f);
        positionedPerson(state, "root", "Основная карточка", 11880f, 7920f);
        positionedPerson(state, "leftParent1", "Левый родитель 1", 11160f, 7440f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 11480f, 7440f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 11960f, 7440f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 12280f, 7440f);
        positionedPerson(state, "leftGrandparent1", "Левый прародитель 1", 11320f, 6960f);
        positionedPerson(state, "leftGrandparent2", "Левый прародитель 2", 11640f, 6960f);
        positionedPerson(state, "rightGrandparent1", "Правый прародитель 1", 12120f, 6960f);
        positionedPerson(state, "rightGrandparent2", "Правый прародитель 2", 12440f, 6960f);
        link(state, "partner", "leftChild", "root");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftChild");
        link(state, "parent", "leftParent2", "leftChild");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "root");
        link(state, "parent", "rightParent2", "root");
        link(state, "partner", "leftGrandparent1", "leftGrandparent2");
        link(state, "parent", "leftGrandparent1", "leftParent2");
        link(state, "parent", "leftGrandparent2", "leftParent2");
        link(state, "partner", "rightGrandparent1", "rightGrandparent2");
        link(state, "parent", "rightGrandparent1", "rightParent1");
        link(state, "parent", "rightGrandparent2", "rightParent1");
        state.rootId = "root";

        float rootX = state.people.get("root").x;
        float leftChildX = state.people.get("leftChild").x;
        TreeLayoutEngine.layoutAfterAddition(
            state,
            java.util.Arrays.asList("rightGrandparent1", "rightGrandparent2"),
            "rightParent1",
            "parents");

        float axis = (state.people.get("leftChild").x + state.people.get("root").x
            + TreeLayoutEngine.CARD_W) / 2f;
        float leftParentsCenter = (state.people.get("leftParent1").x
            + state.people.get("leftParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightParentsCenter = (state.people.get("rightParent1").x
            + state.people.get("rightParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float leftGrandparentsCenter = (state.people.get("leftGrandparent1").x
            + state.people.get("leftGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightGrandparentsCenter = (state.people.get("rightGrandparent1").x
            + state.people.get("rightGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        assertEquals(axis, (leftParentsCenter + rightParentsCenter) / 2f, EPSILON);
        assertEquals(axis, (leftGrandparentsCenter + rightGrandparentsCenter) / 2f, EPSILON);
        assertEquals(rootX, state.people.get("root").x, EPSILON);
        assertEquals(leftChildX, state.people.get("leftChild").x, EPSILON);
        assertNoOverlaps(state);
    }

    @Test
    public void stepwiseRebuildIgnoresOldCoordinatesAndIsDeterministic() {
        TreeState first = problemElevenBase();
        TreeState second = TreeStateCopier.copy(first);
        int index = 0;
        for (Person person : first.people.values()) {
            person.x = 400f + (index % 3) * 40f;
            person.y = 600f + (index % 2) * 40f;
            index++;
        }
        index = 0;
        for (Person person : second.people.values()) {
            person.x = 18000f - index * 120f;
            person.y = 12000f - (index % 4) * 80f;
            index++;
        }

        TreeLayoutEngine.rebuildStepwise(first);
        TreeLayoutEngine.rebuildStepwise(second);

        for (String id : first.people.keySet()) {
            assertEquals(first.people.get(id).x, second.people.get(id).x, EPSILON);
            assertEquals(first.people.get(id).y, second.people.get(id).y, EPSILON);
        }
        assertEquals(11880f, first.people.get("root").x, EPSILON);
        assertEquals(7920f, first.people.get("root").y, EPSILON);
        assertNoOverlaps(first);
        assertOnGrid(first);
    }

    @Test
    public void stepwiseRebuildKeepsMirroredAncestryAroundRootCoupleAxis() {
        TreeState state = new TreeState();
        positionedPerson(state, "leftChild", "Партнёр", 700f, 900f);
        positionedPerson(state, "root", "Основная карточка", 100f, 500f);
        positionedPerson(state, "leftParent1", "Левый родитель 1", 120f, 200f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 160f, 200f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 200f, 200f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 240f, 200f);
        positionedPerson(state, "leftGrandparent1", "Левый прародитель 1", 280f, 200f);
        positionedPerson(state, "leftGrandparent2", "Левый прародитель 2", 320f, 200f);
        positionedPerson(state, "rightGrandparent1", "Правый прародитель 1", 360f, 200f);
        positionedPerson(state, "rightGrandparent2", "Правый прародитель 2", 400f, 200f);
        link(state, "partner", "leftChild", "root");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "leftChild");
        link(state, "parent", "leftParent2", "leftChild");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "root");
        link(state, "parent", "rightParent2", "root");
        link(state, "partner", "leftGrandparent1", "leftGrandparent2");
        link(state, "parent", "leftGrandparent1", "leftParent2");
        link(state, "parent", "leftGrandparent2", "leftParent2");
        link(state, "partner", "rightGrandparent1", "rightGrandparent2");
        link(state, "parent", "rightGrandparent1", "rightParent1");
        link(state, "parent", "rightGrandparent2", "rightParent1");
        state.rootId = "root";

        TreeLayoutEngine.rebuildStepwise(state);

        float axis = (state.people.get("leftChild").x + state.people.get("root").x
            + TreeLayoutEngine.CARD_W) / 2f;
        float leftParentsCenter = (state.people.get("leftParent1").x
            + state.people.get("leftParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightParentsCenter = (state.people.get("rightParent1").x
            + state.people.get("rightParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float leftGrandparentsCenter = (state.people.get("leftGrandparent1").x
            + state.people.get("leftGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightGrandparentsCenter = (state.people.get("rightGrandparent1").x
            + state.people.get("rightGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        assertEquals(axis, (leftParentsCenter + rightParentsCenter) / 2f, EPSILON);
        assertEquals(axis, (leftGrandparentsCenter + rightGrandparentsCenter) / 2f, EPSILON);
        assertNoOverlaps(state);
    }

    @Test
    public void manualArrangeReplaysLocalStepsAndIgnoresScrambledCoordinates() {
        TreeState first = mirroredThreeGenerationTree();
        TreeState second = TreeStateCopier.copy(first);
        int index = 0;
        for (Person person : first.people.values()) {
            person.x = 400f + (index % 3) * 40f;
            person.y = 400f + (index % 2) * 40f;
            index++;
        }
        index = 0;
        for (Person person : second.people.values()) {
            person.x = 18000f - index * 360f;
            person.y = 1200f + (index % 4) * 520f;
            index++;
        }

        TreeLayoutEngine.rebuildStepwise(first);
        TreeLayoutEngine.rebuildStepwise(second);

        assertEquals(11880f, first.people.get("root").x, EPSILON);
        assertEquals(7920f, first.people.get("root").y, EPSILON);
        for (String id : first.people.keySet()) {
            assertEquals(first.people.get(id).x, second.people.get(id).x, EPSILON);
            assertEquals(first.people.get(id).y, second.people.get(id).y, EPSILON);
        }
        float axis = (first.people.get("partner").x + first.people.get("root").x
            + TreeLayoutEngine.CARD_W) / 2f;
        float leftParents = (first.people.get("leftParent1").x
            + first.people.get("leftParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightParents = (first.people.get("rightParent1").x
            + first.people.get("rightParent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float leftGrandparents = (first.people.get("leftGrandparent1").x
            + first.people.get("leftGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        float rightGrandparents = (first.people.get("rightGrandparent1").x
            + first.people.get("rightGrandparent2").x + TreeLayoutEngine.CARD_W) / 2f;
        assertEquals(axis, (leftParents + rightParents) / 2f, EPSILON);
        assertEquals(axis, (leftGrandparents + rightGrandparents) / 2f, EPSILON);
        assertNoOverlaps(first);
    }

    @Test(timeout = 3000L)
    public void manualArrangeBuildsLargeTreeQuicklyWithoutOverlaps() {
        TreeState state = mirroredThreeGenerationTree();
        for (int family = 0; family < 12; family++) {
            String parent = family % 2 == 0 ? "leftParent1" : "rightParent2";
            String sibling = "side" + family;
            String partner = "sidePartner" + family;
            positionedPerson(state, sibling, "Боковой родственник " + family, 0f, 0f);
            positionedPerson(state, partner, "Партнёр " + family, 0f, 0f);
            link(state, "sibling", parent, sibling);
            link(state, "partner", partner, sibling);
            for (int childIndex = 0; childIndex < 3; childIndex++) {
                String child = "sideChild" + family + "_" + childIndex;
                positionedPerson(state, child, "Ребёнок", 0f, 0f);
                link(state, "parent", partner, child);
                link(state, "parent", sibling, child);
            }
        }

        TreeLayoutEngine.rebuildStepwise(state);

        assertEquals(70, state.people.size());
        assertNoOverlaps(state);
        assertOnGrid(state);
    }

    private TreeState mirroredThreeGenerationTree() {
        TreeState state = new TreeState();
        positionedPerson(state, "root", "Основная карточка", 0f, 0f);
        positionedPerson(state, "partner", "Партнёр", 0f, 0f);
        positionedPerson(state, "leftParent1", "Левый родитель 1", 0f, 0f);
        positionedPerson(state, "leftParent2", "Левый родитель 2", 0f, 0f);
        positionedPerson(state, "rightParent1", "Правый родитель 1", 0f, 0f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 0f, 0f);
        positionedPerson(state, "leftGrandparent1", "Левый прародитель 1", 0f, 0f);
        positionedPerson(state, "leftGrandparent2", "Левый прародитель 2", 0f, 0f);
        positionedPerson(state, "rightGrandparent1", "Правый прародитель 1", 0f, 0f);
        positionedPerson(state, "rightGrandparent2", "Правый прародитель 2", 0f, 0f);
        link(state, "partner", "partner", "root");
        link(state, "partner", "leftParent1", "leftParent2");
        link(state, "parent", "leftParent1", "partner");
        link(state, "parent", "leftParent2", "partner");
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "parent", "rightParent1", "root");
        link(state, "parent", "rightParent2", "root");
        link(state, "partner", "leftGrandparent1", "leftGrandparent2");
        link(state, "parent", "leftGrandparent1", "leftParent2");
        link(state, "parent", "leftGrandparent2", "leftParent2");
        link(state, "partner", "rightGrandparent1", "rightGrandparent2");
        link(state, "parent", "rightGrandparent1", "rightParent1");
        link(state, "parent", "rightGrandparent2", "rightParent1");
        state.rootId = "root";
        return state;
    }

    private TreeState problemElevenBase() {
        TreeState state = new TreeState();
        positionedPerson(state, "root", "Пустая карточка", 11040f, 7520f);
        positionedPerson(state, "father", "2", 11200f, 7040f);
        positionedPerson(state, "mother", "Новый родитель 2", 11520f, 7040f);
        positionedPerson(state, "rootSibling1", "Новый ребёнок 1", 11360f, 7520f);
        positionedPerson(state, "rootSibling2", "Новый ребёнок 2", 11680f, 7520f);
        positionedPerson(state, "fatherParent1", "Новый родитель 1", 10000f, 6560f);
        positionedPerson(state, "maternalGrandmother", "Новый родитель 2", 10320f, 6560f);
        positionedPerson(state, "lineageGrandfather", "Новый родитель 1", 12640f, 6560f);
        positionedPerson(state, "lineageGrandmother", "Новый родитель 2", 12960f, 6560f);
        positionedPerson(state, "side1", "1", 11360f, 6560f);
        positionedPerson(state, "side2", "Новый ребёнок 2", 11840f, 6560f);
        positionedPerson(state, "side3", "Новый ребёнок 3", 12160f, 6560f);
        positionedPerson(state, "commonParent1", "Новый родитель 1", 11840f, 6080f);
        positionedPerson(state, "commonParent2", "Новый родитель 2", 12160f, 6080f);

        link(state, "partner", "father", "mother");
        for (String child : new String[]{"root", "rootSibling1", "rootSibling2"}) {
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
        }
        link(state, "partner", "fatherParent1", "maternalGrandmother");
        link(state, "parent", "fatherParent1", "father");
        link(state, "parent", "maternalGrandmother", "father");
        link(state, "partner", "lineageGrandfather", "lineageGrandmother");
        link(state, "parent", "lineageGrandfather", "mother");
        link(state, "parent", "lineageGrandmother", "mother");
        link(state, "partner", "commonParent1", "commonParent2");
        for (String child : new String[]{"lineageGrandfather", "side1", "side2", "side3"}) {
            link(state, "parent", "commonParent1", child);
            link(state, "parent", "commonParent2", child);
        }
        link(state, "sibling", "lineageGrandfather", "side1");
        link(state, "sibling", "side1", "side2");
        link(state, "sibling", "side2", "side3");
        state.rootId = "root";
        return state;
    }

    private TreeState problemNineBaseFamily() {
        TreeState state = new TreeState();
        positionedPerson(state, "father", "Отец", 11200f, 7040f);
        positionedPerson(state, "mother", "Мать", 11520f, 7040f);
        positionedPerson(state, "main", "Основная карточка", 11040f, 7520f);
        positionedPerson(state, "maternalSibling1", "Брат или сестра 1", 11360f, 7520f);
        positionedPerson(state, "maternalSibling2", "Брат или сестра 2", 11680f, 7520f);
        positionedPerson(state, "maternalGrandfather", "Дед", 11760f, 6560f);
        positionedPerson(state, "maternalGrandmother", "Бабушка", 12080f, 6560f);
        positionedPerson(state, "uncle", "Дядя", 12320f, 7040f);
        positionedPerson(state, "unclePartner", "Партнёр дяди", 12000f, 7040f);
        positionedPerson(state, "uncleChild1", "Ребёнок дяди 1", 12000f, 7520f);
        positionedPerson(state, "uncleChild2", "Ребёнок дяди 2", 12320f, 7520f);
        link(state, "partner", "father", "mother");
        for (String child : new String[]{"main", "maternalSibling1", "maternalSibling2"}) {
            link(state, "parent", "father", child);
            link(state, "parent", "mother", child);
        }
        link(state, "partner", "maternalGrandfather", "maternalGrandmother");
        for (String child : new String[]{"mother", "uncle"}) {
            link(state, "parent", "maternalGrandfather", child);
            link(state, "parent", "maternalGrandmother", child);
        }
        link(state, "partner", "uncle", "unclePartner");
        for (String child : new String[]{"uncleChild1", "uncleChild2"}) {
            link(state, "parent", "uncle", child);
            link(state, "parent", "unclePartner", child);
        }
        state.rootId = "main";
        return state;
    }

    private TreeState problemSevenFamily() {
        TreeState state = new TreeState();
        positionedPerson(state, "grandparent1", "Общий родитель 1", 11120f, 7040f);
        positionedPerson(state, "grandparent2", "Общий родитель 2", 11440f, 7040f);
        positionedPerson(state, "sibling1", "Брат 1", 10800f, 7520f);
        positionedPerson(state, "sibling2", "Брат 2", 11280f, 7520f);
        positionedPerson(state, "familyPartner", "Партнёр", 11760f, 7520f);
        positionedPerson(state, "main", "Основная карточка", 12080f, 7520f);
        positionedPerson(state, "sibling1Child1", "Ребёнок брата 1", 10640f, 8000f);
        positionedPerson(state, "sibling1Child2", "Ребёнок брата 2", 10960f, 8000f);
        link(state, "partner", "grandparent1", "grandparent2");
        link(state, "partner", "familyPartner", "main");
        for (String child : new String[]{"sibling1", "sibling2", "familyPartner"}) {
            link(state, "parent", "grandparent1", child);
            link(state, "parent", "grandparent2", child);
        }
        link(state, "parent", "sibling1", "sibling1Child1");
        link(state, "parent", "sibling1", "sibling1Child2");
        state.rootId = "main";
        return state;
    }

    private void setProblemSevenSolvedPositions(TreeState state) {
        state.people.get("grandparent1").x = 10960f;
        state.people.get("grandparent2").x = 11280f;
        state.people.get("sibling1").x = 10480f;
        state.people.get("sibling1Child1").x = 10480f;
        state.deletePerson("sibling1Child2");
    }

    private TreeState partnerBranchesWithThreeRightSiblings() {
        TreeState state = new TreeState();
        positionedPerson(state, "rightParent1", "Правый родитель 1", 9400f, 6080f);
        positionedPerson(state, "rightParent2", "Правый родитель 2", 9720f, 6080f);
        positionedPerson(state, "leftPartner", "Левый партнёр", 8680f, 6560f);
        positionedPerson(state, "mainChild", "Основная карточка", 9000f, 6560f);
        positionedPerson(state, "sibling1", "Брат 1", 9480f, 6560f);
        positionedPerson(state, "sibling2", "Брат 2", 9800f, 6560f);
        positionedPerson(state, "sibling3", "Брат 3", 10120f, 6560f);
        link(state, "partner", "rightParent1", "rightParent2");
        link(state, "partner", "mainChild", "leftPartner");
        for (String child : new String[]{"mainChild", "sibling1", "sibling2", "sibling3"}) {
            link(state, "parent", "rightParent1", child);
            link(state, "parent", "rightParent2", child);
        }
        link(state, "sibling", "mainChild", "sibling1");
        link(state, "sibling", "mainChild", "sibling2");
        link(state, "sibling", "mainChild", "sibling3");
        link(state, "sibling", "sibling1", "sibling2");
        link(state, "sibling", "sibling1", "sibling3");
        link(state, "sibling", "sibling2", "sibling3");
        state.rootId = "mainChild";
        return state;
    }

    @Test
    public void overlappingHeapDoesNotBecomeFakeGenerations() {
        TreeState state = parentsAndTwoChildren();
        String[] ids = {"father", "mother", "childA", "childB"};
        for (int i = 0; i < ids.length; i++) {
            Person person = state.people.get(ids[i]);
            person.x = 1000f + i * 80f;
            person.y = 1000f + i * 120f;
        }

        TreeLayoutEngine.layout(state);

        Person father = state.people.get("father");
        Person first = state.people.get("childA");
        Person second = state.people.get("childB");
        assertEquals(TreeLayoutEngine.LEVEL_GAP, first.y - father.y, EPSILON);
        assertEquals(first.y, second.y, EPSILON);
        assertNoOverlaps(state);
    }

    @Test
    public void wideGenerationExpandsWorkspaceInsteadOfClampingCardsTogether() {
        TreeState state = new TreeState();
        state.workspaceWidth = TreeLayoutEngine.MIN_SURFACE_W;
        person(state, "person0", "Человек 0", "1980");
        state.rootId = "person0";
        for (int i = 1; i < 20; i++) {
            String id = "person" + i;
            person(state, id, "Человек " + i, Integer.toString(1980 + i));
            link(state, "sibling", "person0", id);
        }

        TreeLayoutEngine.layout(state);

        assertTrue(state.workspaceWidth > TreeLayoutEngine.MIN_SURFACE_W);
        float right = maxRight(state, state.people.keySet().toArray(new String[0]));
        assertTrue(right + TreeLayoutEngine.GRID * 4f <= state.workspaceWidth);
        assertNoOverlaps(state);
    }

    private static TreeState parentsAndTwoChildren() {
        TreeState state = new TreeState();
        person(state, "father", "Алексей Иванов", "1970");
        person(state, "mother", "Анна Иванова", "1972");
        person(state, "childA", "Иван Иванов", "1995");
        person(state, "childB", "Павел Иванов", "1998");
        link(state, "partner", "father", "mother");
        link(state, "parent", "father", "childA");
        link(state, "parent", "mother", "childA");
        link(state, "parent", "father", "childB");
        link(state, "parent", "mother", "childB");
        link(state, "sibling", "childA", "childB");
        state.rootId = "childA";
        return state;
    }

    private static void person(TreeState state, String id, String name, String year) {
        Person person = new Person(id);
        person.name = name;
        person.bornYear = year;
        state.people.put(id, person);
    }

    private static void positionedPerson(
        TreeState state,
        String id,
        String name,
        float x,
        float y
    ) {
        person(state, id, name, "");
        state.people.get(id).x = x;
        state.people.get(id).y = y;
    }

    private static void link(TreeState state, String type, String from, String to) {
        state.links.add(new Relation(type + "_" + from + "_" + to, type, from, to));
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

    private static void assertOnGrid(TreeState state) {
        List<Person> people = new ArrayList<>(state.people.values());
        people.sort(Comparator.comparing(person -> person.id));
        for (Person person : people) {
            assertEquals(0f, person.x % TreeLayoutEngine.GRID, EPSILON);
            assertEquals(0f, person.y % TreeLayoutEngine.GRID, EPSILON);
        }
    }

    private static void assertNoOverlaps(TreeState state) {
        List<Person> people = new ArrayList<>(state.people.values());
        for (int i = 0; i < people.size(); i++) {
            Person first = people.get(i);
            for (int j = i + 1; j < people.size(); j++) {
                Person second = people.get(j);
                boolean horizontalOverlap = Math.abs(first.x - second.x) < TreeLayoutEngine.CARD_W;
                boolean verticalOverlap = Math.abs(first.y - second.y) < TreeLayoutEngine.CARD_H;
                assertTrue(
                    "карточки не должны пересекаться: " + first.id + " и " + second.id,
                    !horizontalOverlap || !verticalOverlap);
            }
        }
    }
}
