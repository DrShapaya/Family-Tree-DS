package ru.drshapaya.androidft2;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Anonymous checks of the structural layout rules. */
public final class RealFamilyLayoutRegressionTest {
    @Test public void redundantFullSiblingLinksDoNotPreventReunitingATornLeafBand() {
        for (boolean redundantLink : new boolean[]{false, true}) {
            TreeState state = new TreeState();
            for (String id : new String[]{"a", "b", "c", "d", "otherA", "otherB", "root"}) person(state, id);
            position(state, "a", 2400, 400); position(state, "b", 2720, 400);
            position(state, "c", 800, 880); position(state, "d", 4000, 880);
            position(state, "otherA", 1600, 880); position(state, "otherB", 1920, 880);
            position(state, "root", 1760, 1360);
            parents(state, "a", "b", "c"); parents(state, "a", "b", "d");
            parents(state, "otherA", "otherB", "root"); state.rootId = "root";
            if (redundantLink) link(state, "sibling", "c", "d");
            assertTrue(TerminalSiblingBands.improve(state));
            assertEquals(2400, state.people.get("c").x, .5f);
            assertEquals(2720, state.people.get("d").x, .5f);
            assertEquals("the neighbouring household stays intact", 1600, state.people.get("otherA").x, .5f);
            valid(state);
        }
    }

    @Test public void lateSingleParentAnchorCannotSplitAnImplicitCoParentHousehold() {
        TreeState state = new TreeState();
        for (String id : new String[]{"a", "b", "c", "d", "e", "private"}) person(state, id);
        position(state, "a", 4000, 400); position(state, "b", 4320, 400);
        position(state, "c", 3840, 880); position(state, "d", 4160, 880);
        position(state, "e", 4480, 880); position(state, "private", 800, 880);
        for (String child : new String[]{"c", "d", "e"}) parents(state, "a", "b", child);
        link(state, "parent", "a", "private"); state.rootId = "d";
        FamilyAnchorProjection.improve(state);
        assertEquals(320, Math.abs(state.people.get("a").x - state.people.get("b").x), .5f);
        assertEquals(4300, FamilyAnchorProjection.average(Arrays.asList("a", "b"), LayoutSnapshot.capture(state)), .5f);
        valid(state);
    }

    @Test public void chainedUnionsKeepEveryActualPairAdjacent() {
        for (boolean reverse : new boolean[]{false, true}) {
            TreeState state = new TreeState();
            List<String> ids = new ArrayList<>(Arrays.asList("a", "b", "c", "d", "ab", "bc", "cd"));
            if (reverse) Collections.reverse(ids);
            for (String id : ids) person(state, id);
            parents(state, "a", "b", "ab"); parents(state, "b", "c", "bc"); parents(state, "c", "d", "cd");
            state.rootId = "bc";
            TreeLayoutEngine.layoutFresh(state);
            for (String[] pair : new String[][]{{"a", "b"}, {"b", "c"}, {"c", "d"}})
                assertEquals(Arrays.toString(pair), 320, Math.abs(state.people.get(pair[0]).x - state.people.get(pair[1]).x), .5f);
            valid(state);
        }
    }

    @Test public void ancestorUnionOrientationFollowsItsChildrenInsteadOfInsertionOrder() {
        TreeState state = new TreeState();
        for (String id : new String[]{"a", "shared", "b", "childA", "childB"}) person(state, id);
        position(state, "a", 800, 400); position(state, "shared", 1120, 400); position(state, "b", 1440, 400);
        position(state, "childA", 1600, 880); position(state, "childB", 800, 880);
        parents(state, "a", "shared", "childA"); parents(state, "shared", "b", "childB");
        state.rootId = "childA";
        assertTrue(HouseholdOrientation.improve(state));
        assertTrue(state.people.get("a").x > state.people.get("b").x);
        assertEquals(320, state.people.get("a").x - state.people.get("shared").x, .5f);
        assertEquals(320, state.people.get("shared").x - state.people.get("b").x, .5f);
        assertFalse(HouseholdOrientation.improve(state));
        valid(state);
    }

    @Test public void aCenteredButWidelySplitTerminalFamilyStillCompacts() {
        TreeState state = new TreeState();
        for (String id : new String[]{"a", "b", "c", "d"}) person(state, id);
        position(state, "a", 1600, 400); position(state, "b", 1920, 400);
        position(state, "c", 800, 880); position(state, "d", 2720, 880);
        parents(state, "a", "b", "c"); parents(state, "a", "b", "d"); state.rootId = "a";
        assertTrue(TerminalSiblingBands.compactFreeBands(state));
        assertEquals(1600, state.people.get("c").x, .5f);
        assertEquals(1920, state.people.get("d").x, .5f);
        assertEquals(1600, state.people.get("a").x, .5f);
        valid(state);
    }

    private static void person(TreeState state, String id) { Person p = new Person(id); p.name = id; state.people.put(id, p); }
    private static void position(TreeState state, String id, float x, float y) { state.people.get(id).x = x; state.people.get(id).y = y; }
    private static void parents(TreeState state, String a, String b, String child) { link(state, "parent", a, child); link(state, "parent", b, child); }
    private static void link(TreeState state, String type, String from, String to) { state.links.add(new Relation("r" + state.links.size(), type, from, to)); }
    private static void valid(TreeState state) { assertTrue(new LayoutConstraints().validate(FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid()); }
}
