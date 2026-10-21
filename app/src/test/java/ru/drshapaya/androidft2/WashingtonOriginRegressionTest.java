package ru.drshapaya.androidft2;

import org.junit.Test;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Continuing households and their ancestor chains must follow the origin order. */
public final class WashingtonOriginRegressionTest {
    @Test public void ancestorChainsAndContinuingHouseholdUseTheirOwnCorridors() throws Exception {
        TreeState state = TreeStore.parseGedcom(ImportedLayoutAuditTest.resource("washington-numbered.ged"));
        ImportedLayoutAuditTest.save(state, "washington-origins-current");
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

    private static void check(TreeState state) {
        before(state, 232, 476);
        before(state, 477, 472);
        before(state, 473, 474);
        assertTrue("132's ancestors must remain near their child",
            Math.abs(center(state, 472, 473) - center(state, 132, 132)) <= 1600);
        before(state, 113, 48);
        before(state, 49, 460);
        assertTrue(center(state, 102, 103) < center(state, 48, 48));
        assertTrue(center(state, 458, 459) > center(state, 49, 49));
        assertTrue(new LayoutConstraints().validate(FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state)).isValid());
    }
    private static void before(TreeState state, int a, int b) {
        assertTrue(a + " must precede " + b, p(state, a).x + 480 <= p(state, b).x + .5f);
    }
    private static float center(TreeState state, int a, int b) {
        return (p(state, a).x + p(state, b).x + 280) / 2;
    }
    private static Person p(TreeState state, int number) {
        Pattern pattern = Pattern.compile("\\b0*" + number + "\\b");
        for (Person p : state.people.values()) if (pattern.matcher(p.name).find()) return p;
        throw new AssertionError("Missing " + number);
    }
}
