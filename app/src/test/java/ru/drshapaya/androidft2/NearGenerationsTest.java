package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

public class NearGenerationsTest {
    @Test public void expandsOneRelationshipLayerAtATimeWithoutLoopingOnCycles() {
        TreeState state = new TreeState();
        for (String id : new String[]{"a", "b", "c", "d", "unrelated"}) state.people.put(id, new Person(id));
        state.links.add(new Relation("ab", "parent", "a", "b"));
        state.links.add(new Relation("bc", "parent", "b", "c"));
        state.links.add(new Relation("cd", "partner", "c", "d"));
        state.links.add(new Relation("db", "family", "d", "b"));
        assertEquals(state.nearOf("a"), state.nearOf("a", 1));
        assertEquals(2, state.nearOf("a", 1).size());
        assertEquals(4, state.nearOf("a", 2).size());
        assertEquals(4, state.nearOf("a", 99).size());
        assertFalse(state.nearOf("a", 99).contains("unrelated"));
        assertTrue(state.nearOf("missing", 2).isEmpty());
        assertEquals(state.nearOf("a", 1), state.nearOf("a", 0));
    }
}
