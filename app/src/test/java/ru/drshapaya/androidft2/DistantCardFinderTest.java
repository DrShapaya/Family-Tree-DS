package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class DistantCardFinderTest {
    @Test
    public void ignoresConnectedAndNearbyCards() {
        TreeState state = new TreeState();
        add(state, "root", 0, 0);
        add(state, "connected", 320, 0);
        add(state, "near", 600, 0);
        add(state, "far", 2000, 0);
        state.rootId = "root";
        state.links.add(new Relation("link", "parent", "root", "connected"));

        assertEquals(Collections.singletonList("far"), DistantCardFinder.findIds(state));
    }

    @Test
    public void rootComponentWinsOverLargerDisconnectedComponent() {
        TreeState state = new TreeState();
        add(state, "root", 0, 0);
        add(state, "far1", 2000, 0);
        add(state, "far2", 2320, 0);
        state.rootId = "root";
        state.links.add(new Relation("link", "sibling", "far1", "far2"));

        assertEquals(Arrays.asList("far1", "far2"), DistantCardFinder.findIds(state));
    }

    @Test
    public void boundsExcludeCardsBeingMoved() {
        TreeState state = new TreeState();
        add(state, "root", 100, 200);
        add(state, "far", 2000, 0);

        DistantCardFinder.Bounds bounds = DistantCardFinder.boundsExcluding(
            state, Collections.singletonList("far"));

        assertEquals(100f, bounds.left, 0f);
        assertEquals(200f, bounds.top, 0f);
        assertEquals(100f + TreeLayoutEngine.CARD_W, bounds.right, 0f);
        assertEquals(200f + TreeLayoutEngine.CARD_H, bounds.bottom, 0f);
        assertTrue(DistantCardFinder.findIds(null).isEmpty());
    }

    private static void add(TreeState state, String id, float x, float y) {
        Person person = new Person(id);
        person.x = x;
        person.y = y;
        state.people.put(id, person);
    }
}
