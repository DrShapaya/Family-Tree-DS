package ru.drshapaya.androidft2;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Anonymous graphs ensure that band rules depend on relationships, not fixture labels. */
public final class TerminalSiblingBandsTest {
    @Test public void scatteredSiblingsUseTheirOwnOriginCorridorForDifferentIds() {
        Fixture first = scattered(false), second = scattered(true);
        LayoutSnapshot before = LayoutSnapshot.capture(first.state);
        assertTrue(TerminalSiblingBands.improve(first.state));
        assertTrue(TerminalSiblingBands.improve(second.state));
        for (String key : first.ids.keySet())
            assertEquals(key, first.person(key).x, second.person(key).x, .5f);
        assertTrue(first.person("a").x > first.person("foreignMate").x + 280);
        assertTrue(first.person("d").x < first.person("main").x);
        assertEquals(first.person("foreign").x, before.positionOf(first.id("foreign")).x, .5f);
        for (String[] pair : new String[][]{{"a", "b"}, {"b", "c"}, {"c", "d"}})
            assertEquals(320, first.person(pair[1]).x - first.person(pair[0]).x, .5f);
        LayoutSnapshot settled = LayoutSnapshot.capture(first.state);
        assertFalse(TerminalSiblingBands.improve(first.state));
        for (Person p : first.state.people.values()) assertEquals(settled.positionOf(p.id).x, p.x, .5f);
        valid(first.state);
    }

    @Test public void pinnedChildAndForeignHouseholdRemainFixed() {
        Fixture f = scattered(false);
        f.person("b").pinned = true;
        f.person("foreign").pinned = true;
        LayoutSnapshot before = LayoutSnapshot.capture(f.state);
        TerminalSiblingBands.improve(f.state);
        for (String key : new String[]{"b", "foreign"})
            assertEquals(before.positionOf(f.id(key)).x, f.person(key).x, .5f);
        valid(f.state);
    }

    @Test public void neighbouringTerminalFamiliesKeepARealBandGap() {
        Fixture f = new Fixture(false);
        f.pair("leftParent", "leftMate", 800, 400);
        f.pair("rightParent", "rightMate", 2000, 400);
        f.person("leftChild", 800, 880);
        f.person("rightChild", 1120, 880);
        f.child("leftParent", "leftMate", "leftChild");
        f.child("rightParent", "rightMate", "rightChild");
        f.state.rootId = f.id("leftParent");
        valid(f.state);
        assertTrue(TerminalRowSpacing.improve(f.state));
        assertTrue(f.person("rightChild").x - f.person("leftChild").x - 280 >= 200);
        valid(f.state);
    }

    @Test public void alreadyCenteredSpanMayStraddleItsOwnChildlessHousehold() {
        Fixture f = new Fixture(false);
        f.pair("parent", "mate", 1600, 400);
        f.person("left", 800, 880);
        f.pair("child", "childMate", 1600, 880);
        f.person("right", 2720, 880);
        for (String child : new String[]{"left", "child", "right"}) f.child("parent", "mate", child);
        f.state.rootId = f.id("parent");
        LayoutSnapshot before = LayoutSnapshot.capture(f.state);
        assertFalse(TerminalSiblingBands.improve(f.state));
        for (Person p : f.state.people.values()) assertEquals(before.positionOf(p.id).x, p.x, .5f);
        valid(f.state);
    }

    private static Fixture scattered(boolean reverseIds) {
        Fixture f = new Fixture(reverseIds);
        f.pair("parent", "mate", 3200, 400);
        f.pair("foreignParent", "foreignParentMate", 800, 400);
        f.pair("foreign", "foreignMate", 1440, 880);
        f.pair("main", "mainMate", 4800, 880);
        f.person("mainChild", 4960, 1360);
        f.person("a", 480, 880); f.person("b", 800, 880);
        f.person("c", 2240, 880); f.person("d", 3840, 880);
        for (String child : new String[]{"main", "a", "b", "c", "d"}) f.child("parent", "mate", child);
        f.child("foreignParent", "foreignParentMate", "foreign");
        f.child("main", "mainMate", "mainChild");
        f.state.rootId = f.id("mainChild");
        valid(f.state);
        return f;
    }

    private static void valid(TreeState state) {
        LayoutConstraints.ValidationResult result = new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state));
        assertTrue(result.violations.toString(), result.isValid());
    }

    private static final class Fixture {
        final TreeState state = new TreeState();
        final Map<String, String> ids = new LinkedHashMap<>();
        final boolean reverse;
        Fixture(boolean reverse) { this.reverse = reverse; }
        String id(String key) { return ids.get(key); }
        Person person(String key) { return state.people.get(id(key)); }
        void person(String key, float x, float y) {
            String id = ids.computeIfAbsent(key, ignored -> "id-" + (reverse ? 100 - ids.size() : ids.size()));
            Person p = new Person(id); p.name = "Anonymous"; p.x = x; p.y = y; state.people.put(id, p);
        }
        void pair(String a, String b, float x, float y) {
            person(a, x, y); person(b, x + 320, y);
            state.links.add(new Relation("r" + state.links.size(), "partner", id(a), id(b), "right"));
        }
        void child(String a, String b, String c) {
            for (String parent : new String[]{a, b})
                state.links.add(new Relation("r" + state.links.size(), "parent", id(parent), id(c), "right"));
        }
    }
}
