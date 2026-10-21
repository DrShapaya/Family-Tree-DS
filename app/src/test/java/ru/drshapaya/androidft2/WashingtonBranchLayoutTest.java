package ru.drshapaya.androidft2;

import org.junit.Test;
import java.util.regex.Pattern;
import static org.junit.Assert.*;

/** Reported terminal bands, tested through real GEDCOM import and a full rebuild. */
public final class WashingtonBranchLayoutTest {
    @Test public void importedAndRebuiltWashingtonKeepAllFiveReportedBandsTogether() throws Exception {
        TreeState state = TreeStore.parseGedcom(ImportedLayoutAuditTest.resource("washington-numbered.ged"));
        checkReportedBands(state);
        ImportedLayoutAuditTest.save(state, "washington-branch-fix");
        LayoutSnapshot expected = LayoutSnapshot.capture(state);
        for (Person p : state.people.values()) { p.x = 12000; p.y = 8000; }
        state.workspaceWidth = 90000;
        TreeLayoutEngine.layoutFresh(state);
        checkReportedBands(state);
        for (Person p : state.people.values()) {
            assertEquals(p.name, expected.positionOf(p.id).x, p.x, .5f);
            assertEquals(p.name, expected.positionOf(p.id).y, p.y, .5f);
        }
    }

    private static void checkReportedBands(TreeState state) {
        assertBand(state, 206, 215);
        assertEquals("204/205 children use the available space under the parents",
            center(state, 204, 205), center(state, 206, 215), TreeLayoutEngine.GRID / 2);
        assertBand(state, 266, 268);
        assertTrue("264's children stay beyond the preceding family",
            person(state, 266).x >= person(state, 263).x + TreeLayoutEngine.CARD_W + TreeLayoutEngine.GRID);
        assertBand(state, 362, 368);
        assertTrue("360's children remain on their side of the marriage",
            person(state, 368).x < person(state, 333).x);
        assertBand(state, 222, 232);
        assertTrue("the Dymoke parents belong above their terminal band",
            person(state, 220).x >= person(state, 225).x && person(state, 221).x <= person(state, 230).x);
        assertTrue("the upper Talboys pair follows the same branch",
            person(state, 273).x >= person(state, 224).x && person(state, 274).x <= person(state, 230).x);
        assertBand(state, 283, 294);
        assertEquals("281/282 children do not inherit their broad sister's spacing",
            center(state, 281, 282), center(state, 283, 294), TreeLayoutEngine.GRID / 2);
        LayoutConstraints.ValidationResult result = new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state));
        assertTrue("layout constraints: " + result.violations, result.isValid());
    }

    private static void assertBand(TreeState state, int first, int last) {
        for (int n = first; n < last; n++) {
            assertEquals("compact siblings " + n + "/" + (n + 1), TreeLayoutEngine.GRID,
                person(state, n + 1).x - person(state, n).x - TreeLayoutEngine.CARD_W, .5f);
            assertEquals(person(state, n).y, person(state, n + 1).y, .5f);
        }
    }

    private static float center(TreeState state, int a, int b) {
        return (person(state, a).x + person(state, b).x + TreeLayoutEngine.CARD_W) / 2;
    }

    private static Person person(TreeState state, int n) {
        Pattern pattern = Pattern.compile("\\b0*" + n + "\\b");
        for (Person person : state.people.values()) if (pattern.matcher(person.name).find()) return person;
        throw new AssertionError("Missing fixture person " + n);
    }
}
