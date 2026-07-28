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
}
