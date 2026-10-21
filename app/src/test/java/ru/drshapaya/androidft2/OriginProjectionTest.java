package ru.drshapaya.androidft2;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Role-based graphs, reflected and relabelled, exercise rules without GEDCOM numbers. */
public final class OriginProjectionTest {
    @Test public void ancestorOrderCanChangeAcrossAnAtomicTerminalBand() {
        for (boolean mirror : new boolean[]{false, true}) {
            TreeState s = ancestors();
            if (mirror) mirror(s);
            LayoutSnapshot before = LayoutSnapshot.capture(s);
            assertTrue(OriginOrderProjection.improve(s));
            float direction = mirror ? -1 : 1;
            assertTrue(direction * (center(s, "elm", "elmMate") - center(s, "oak", "oakMate")) > 0);
            assertEquals(320, Math.abs(s.people.get("leafA").x - s.people.get("leafB").x), .5f);
            assertEquals(320, Math.abs(s.people.get("leafB").x - s.people.get("leafC").x), .5f);
            assertEquals(before.positionOf("oakChild").x, s.people.get("oakChild").x, .5f);
            assertTrue(Math.abs(center(s, "oak", "oakMate") - s.people.get("oakChild").x - 140) < 400);
            assertTrue(Math.abs(center(s, "elm", "elmMate") - s.people.get("elmChild").x - 140) < 400);
            valid(s);
        }
    }

    @Test public void ancestorProjectionHonorsPinnedCards() {
        TreeState s = ancestors();
        s.people.get("oak").pinned = true;
        float x = s.people.get("oak").x;
        OriginOrderProjection.improve(s);
        assertEquals(x, s.people.get("oak").x, .5f);
        valid(s);
    }

    @Test public void connectorUsesTheFacingEdgeAndLeavesSharedDescendantsFixed() {
        for (boolean mirror : new boolean[]{false, true}) {
            TreeState s = connector();
            if (mirror) mirror(s);
            float shared = s.people.get("junction").x;
            float origin = center(s, "origin", "originMate");
            assertTrue(ContinuingOriginProjection.improve(s));
            float direction = mirror ? -1 : 1;
            assertTrue(direction * (s.people.get("connector").x - s.people.get("leafC").x) >= 480);
            assertTrue(direction * (center(s, "other", "otherMate") - s.people.get("spouse").x - 140) > 0);
            assertEquals(shared, s.people.get("junction").x, .5f);
            assertEquals(origin, center(s, "origin", "originMate"), .5f);
            valid(s);
        }
    }

    @Test public void connectorDoesNotMovePinnedAncestorOrPinnedDescendant() {
        for (String pinned : new String[]{"other", "freeChild"}) {
            TreeState s = connector(); s.people.get(pinned).pinned = true;
            LayoutSnapshot before = LayoutSnapshot.capture(s);
            assertFalse(ContinuingOriginProjection.improve(s));
            for (Person p : s.people.values()) assertEquals(before.positionOf(p.id).x, p.x, .5f);
            valid(s);
        }
    }

    @Test public void relabelledAndReversedInputKeepsTheSameGeometricResult() {
        TreeState a = connector(), b = connector();
        Map<String, String> ids = new LinkedHashMap<>();
        List<Person> reversed = new ArrayList<>(b.people.values()); Collections.reverse(reversed);
        b.people.clear(); int index = 0;
        for (Person p : reversed) {
            String old = p.id, next = "anonymous-" + index++; ids.put(old, next); p.id = next; b.people.put(next, p);
        }
        for (Relation r : b.links) { r.from = ids.get(r.from); r.to = ids.get(r.to); }
        Collections.reverse(b.links); b.rootId = ids.get(b.rootId);
        assertTrue(ContinuingOriginProjection.improve(a));
        assertTrue(ContinuingOriginProjection.improve(b));
        for (String id : a.people.keySet()) assertEquals(id, a.people.get(id).x, b.people.get(ids.get(id)).x, .5f);
        valid(b);
    }

    private static TreeState ancestors() {
        TreeState s = new TreeState();
        pair(s, "oak", "oakMate", 4000, 880);
        pair(s, "elm", "elmMate", 1200, 880);
        person(s, "oakChild", 1600, 1360); person(s, "elmChild", 4400, 1360);
        child(s, "oak", "oakMate", "oakChild"); child(s, "elm", "elmMate", "elmChild");
        pair(s, "bandParent", "bandMate", 2160, 400);
        for (int i = 0; i < 3; i++) {
            String id = new String[]{"leafA", "leafB", "leafC"}[i];
            person(s, id, 2000 + i * 320, 880); child(s, "bandParent", "bandMate", id);
        }
        s.rootId = "oakChild"; return s;
    }

    private static TreeState connector() {
        TreeState s = new TreeState();
        pair(s, "origin", "originMate", 3200, 880);
        pair(s, "other", "otherMate", 4160, 880);
        pair(s, "connector", "spouse", 1600, 1360);
        child(s, "origin", "originMate", "connector"); child(s, "other", "otherMate", "spouse");
        for (int i = 0; i < 3; i++) {
            String id = new String[]{"leafA", "leafB", "leafC"}[i];
            person(s, id, 3040 + i * 320, 1360); child(s, "origin", "originMate", id);
        }
        person(s, "otherLeaf", 2400, 1360); child(s, "other", "otherMate", "otherLeaf");
        pair(s, "alien", "alienMate", 6400, 1360);
        pair(s, "junction", "junctionMate", 800, 1840);
        child(s, "connector", "spouse", "junction"); child(s, "alien", "alienMate", "junctionMate");
        person(s, "freeChild", 1600, 1840); person(s, "freeChildTwo", 1920, 1840);
        child(s, "connector", "spouse", "freeChild"); child(s, "connector", "spouse", "freeChildTwo");
        s.rootId = "junction"; return s;
    }

    private static void mirror(TreeState s) { for (Person p : s.people.values()) p.x = 10000 - p.x - 280; }
    private static void person(TreeState s, String id, float x, float y) {
        Person p = new Person(id); p.name = "Anonymous"; p.x = x; p.y = y; s.people.put(id, p);
    }
    private static void pair(TreeState s, String a, String b, float x, float y) {
        person(s, a, x, y); person(s, b, x + 320, y);
        s.links.add(new Relation("r" + s.links.size(), "partner", a, b, "right"));
    }
    private static void child(TreeState s, String a, String b, String c) {
        for (String parent : new String[]{a, b}) s.links.add(new Relation("r" + s.links.size(), "parent", parent, c, "right"));
    }
    private static float center(TreeState s, String a, String b) { return (s.people.get(a).x + s.people.get(b).x + 280) / 2; }
    private static void valid(TreeState s) {
        assertTrue(new LayoutConstraints().validate(FamilyLayoutGraph.from(s), LayoutSnapshot.capture(s)).isValid());
    }
}
