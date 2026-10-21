package ru.drshapaya.androidft2;
import org.junit.Test;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Seven additional reports: ordering, exact gaps and anchors, not just collisions. */
public final class WashingtonAnchorRegressionTest {
    @Test public void auditAnchors() throws Exception {
        TreeState state = TreeStore.parseGedcom(ImportedLayoutAuditTest.resource("washington-numbered.ged"));
        ImportedLayoutAuditTest.save(state, "washington-anchors-current");
        check(state);
        LayoutSnapshot expected = LayoutSnapshot.capture(state);
        for (Person p : state.people.values()) { p.x = 12000; p.y = 8000; }
        TreeLayoutEngine.layoutFresh(state);
        check(state);
        for (Person p : state.people.values()) {
            assertEquals(p.name, expected.positionOf(p.id).x, p.x, .5f);
            assertEquals(p.name, expected.positionOf(p.id).y, p.y, .5f);
        }
    }

    private static void check(TreeState s) {
        gap(s, 249, 301, 5);
        gap(s, 254, 204, 5);
        center(s, 247, 247, 202, 202, 20);
        assertTrue("233/234 must remain outside the Bullock terminal band",
            p(s, 234).x + 480 <= p(s, 283).x + .5f);
        center(s, 469, 469, 103, 471, 20);
        gap(s, 187, 188, 5);
        assertTrue("188 must precede the unrelated Martiau branch", p(s, 188).x + 280 < p(s, 136).x);
        center(s, 216, 217, 170, 170, 20);
        center(s, 180, 181, 115, 115, 20);
        center(s, 264, 265, 216, 268, 20);
        // Two grandparent couples (600 px each) above adjacent partners
        // (320 px apart) necessarily share a 480 px outward displacement.
        center(s, 302, 303, 264, 264, 240);
        center(s, 304, 305, 265, 265, 240);
        assertTrue(new LayoutConstraints().validate(FamilyLayoutGraph.from(s), LayoutSnapshot.capture(s)).isValid());
    }

    private static void gap(TreeState s, int a, int b, int cells) {
        assertEquals("gap " + a + "/" + b, cells * 40, p(s, b).x - p(s, a).x - 280, .5f);
    }
    private static void center(TreeState s, int a, int b, int c, int d, float tolerance) {
        assertEquals("anchor " + a + "/" + b, (p(s, c).x + p(s, d).x + 280) / 2,
            (p(s, a).x + p(s, b).x + 280) / 2, tolerance + .5f);
    }
    private static Person p(TreeState s, int n) {
        Pattern pattern = Pattern.compile("\\b0*" + n + "\\b");
        for (Person p : s.people.values()) if (pattern.matcher(p.name).find()) return p;
        throw new AssertionError("Missing " + n);
    }
}
