package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PersonPathFinderTest {
    @Test
    public void choosesDirectConnectionOverLongerRoute() {
        TreeState state = new TreeState();
        add(state, "a", "b", "c");
        state.links.add(new Relation("ab", "parent", "a", "b"));
        state.links.add(new Relation("bc", "parent", "b", "c"));
        state.links.add(new Relation("ac", "partner", "a", "c"));

        assertEquals(Arrays.asList("a", "c"), PersonPathFinder.shortestPath(state, "a", "c"));
    }

    @Test
    public void traversesLinksInBothDirections() {
        TreeState state = new TreeState();
        add(state, "parent", "child", "grandchild");
        state.links.add(new Relation("one", "parent", "parent", "child"));
        state.links.add(new Relation("two", "parent", "child", "grandchild"));

        assertEquals(Arrays.asList("grandchild", "child", "parent"),
            PersonPathFinder.shortestPath(state, "grandchild", "parent"));
    }

    @Test
    public void handlesSameMissingAndDisconnectedPeople() {
        TreeState state = new TreeState();
        add(state, "a", "b");

        assertEquals(Collections.singletonList("a"), PersonPathFinder.shortestPath(state, "a", "a"));
        assertTrue(PersonPathFinder.shortestPath(state, "a", "b").isEmpty());
        assertTrue(PersonPathFinder.shortestPath(state, "a", "missing").isEmpty());
        assertTrue(PersonPathFinder.shortestPath(null, "a", "b").isEmpty());
    }

    private static void add(TreeState state, String... ids) {
        for (String id : ids) state.people.put(id, new Person(id));
    }
}
