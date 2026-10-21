package ru.drshapaya.androidft2;

import org.junit.Test;
import static org.junit.Assert.*;

/** Small anonymous graphs exercise anchors independently of the Washington fixture. */
public final class FamilyAnchorProjectionTest {
    @Test public void lateChildMovementCarriesTheExclusiveUpperChain() {
        TreeState s = new TreeState();
        pair(s, "upper", "upperMate", 3800, 400);
        pair(s, "parent", "mate", 4000, 880);
        pair(s, "child", "childMate", 5200, 1360);
        child(s, "upper", "upperMate", "parent");
        child(s, "parent", "mate", "child");
        s.rootId = "child";
        float oldUpper = s.people.get("upper").x;
        float oldParent = s.people.get("parent").x;
        assertTrue(FamilyAnchorProjection.improve(s));
        assertEquals(s.people.get("child").x + 140, center(s, "parent", "mate"), .5f);
        assertEquals(s.people.get("parent").x - oldParent, s.people.get("upper").x - oldUpper, .5f);
        valid(s);
    }

    @Test public void competingGrandparentCouplesShareTheNecessaryOutwardOffset() {
        TreeState s = competingOrigins();
        assertTrue(FamilyAnchorProjection.balanceCompetingOrigins(s));
        assertEquals(240, s.people.get("leftChild").x + 140 - center(s, "left", "leftMate"), .5f);
        assertEquals(240, center(s, "right", "rightMate") - s.people.get("rightChild").x - 140, .5f);
        assertEquals(200, s.people.get("right").x - s.people.get("leftMate").x - 280, .5f);
        valid(s);
    }

    @Test public void pinnedAncestorPreventsBalancingItsHousehold() {
        TreeState s = competingOrigins();
        s.people.get("left").pinned = true;
        LayoutSnapshot before = LayoutSnapshot.capture(s);
        assertFalse(FamilyAnchorProjection.balanceCompetingOrigins(s));
        for (Person p : s.people.values()) assertEquals(before.positionOf(p.id).x, p.x, .5f);
        valid(s);
    }

    @Test public void loneSiblingUsesTheOuterSideButAProperlyCenteredLeafStaysPut() {
        for (boolean centered : new boolean[]{false, true}) {
            TreeState s = new TreeState();
            pair(s, "parent", "mate", 1600, 400);
            pair(s, "inlaw", "daughter", 2400, 880);
            person(s, "sibling", centered ? 1760 : 1200, 880);
            child(s, "parent", "mate", "daughter");
            child(s, "parent", "mate", "sibling");
            s.rootId = "daughter";
            SiblingBoundaryProjection.improve(s);
            if (centered) assertEquals(1760, s.people.get("sibling").x, .5f);
            else assertEquals(200, s.people.get("sibling").x - s.people.get("daughter").x - 280, .5f);
            valid(s);
        }
    }

    private static TreeState competingOrigins() {
        TreeState s = new TreeState();
        pair(s, "left", "leftMate", 1200, 400);
        pair(s, "right", "rightMate", 2000, 400);
        pair(s, "leftChild", "rightChild", 2400, 880);
        child(s, "left", "leftMate", "leftChild");
        child(s, "right", "rightMate", "rightChild");
        s.rootId = "leftChild";
        return s;
    }
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
    private static float center(TreeState s, String a, String b) {
        return (s.people.get(a).x + s.people.get(b).x + 280) / 2;
    }
    private static void valid(TreeState s) {
        assertTrue(new LayoutConstraints().validate(FamilyLayoutGraph.from(s), LayoutSnapshot.capture(s)).isValid());
    }
}
