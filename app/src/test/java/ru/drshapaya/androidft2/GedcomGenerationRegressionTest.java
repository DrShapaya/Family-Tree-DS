package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;

public final class GedcomGenerationRegressionTest {
    @Test(timeout = 30000) public void bourbonPreservesAllLinksAndKeepsParentsAboveChildren() throws Exception {
        TreeState state = fixture("bourbon");
        assertEquals(303, state.people.size());
        assertEquals(357, state.links.stream().filter(r -> "parent".equals(r.type)).count());
        assertEquals(113, state.links.stream().filter(r -> "partner".equals(r.type)).count());
        assertParentOrder(state, false);
        Person henri = person(state, "CAPET Henri IV");
        int count = 0;
        for (Relation link : state.links) {
            if ("parent".equals(link.type) && henri.id.equals(link.from)) {
                assertEquals(TreeLayoutEngine.LEVEL_GAP, state.people.get(link.to).y - henri.y, 0.1f);
                count++;
            }
        }
        assertEquals(6, count);
        Person marguerite = person(state, "de HUNTINGDON Marguerite");
        assertEquals(TreeLayoutEngine.LEVEL_GAP,
            marguerite.y - person(state, "de NORTHUMBERLAND Henri").y, 0.1f);
        assertEquals(marguerite.y, person(state, "NORTHUMBERLAND Malcom").y, 0.1f);
        assertEquals(marguerite.y, person(state, "NORTHUMBERLAND Guillaume").y, 0.1f);
        assertEquals("1643", person(state, "CAPET Louis XIII").diedYear);
        assertEquals("1601", person(state, "CAPET Louis XIII").bornYear);
        assertNoOverlaps(state);
        assertValid(state);
        Map<String, float[]> before = new LinkedHashMap<>();
        for (Person p : state.people.values()) before.put(p.id, new float[]{p.x, p.y});
        TreeLayoutEngine.layoutFresh(state);
        assertParentOrder(state, false);
        assertValid(state);
        for (Person p : state.people.values()) {
            assertEquals(p.name + " x", before.get(p.id)[0], p.x, 0.1f);
            assertEquals(p.name + " y", before.get(p.id)[1], p.y, 0.1f);
        }
    }

    @Test(timeout = 30000) public void kennedyRetainsAdjacentGenerations() throws Exception {
        TreeState state = fixture("kennedy");
        assertEquals(208, state.people.size());
        assertEquals(254, state.links.stream().filter(r -> "parent".equals(r.type)).count());
        assertParentOrder(state, true);
        assertNoOverlaps(state);
    }

    @Test public void datesAndBirthPlaceStayWithinTheirEvent() {
        TreeState state = TreeStore.parseGedcom("0 HEAD\n0 @I1@ INDI\n1 NAME Test\n"
            + "1 BIRT\n2 DATE 27 SEP 1601\n2 PLAC Birth town\n"
            + "1 RESI\n2 DATE 1620\n2 PLAC Residence\n"
            + "1 DEAT\n2 DATE 14 MAY 1643\n"
            + "1 EVEN\n2 DATE 1610\n1 CHAN\n2 DATE 4 FEB 2018\n0 TRLR\n");
        Person p = state.people.values().iterator().next();
        assertEquals("1601", p.bornYear);
        assertEquals("27", p.bornDay);
        assertEquals("09", p.bornMonth);
        assertEquals("Birth town", p.place);
        assertEquals("1643", p.diedYear);
        assertEquals("14", p.diedDay);
        assertEquals("05", p.diedMonth);
    }

    @Test public void aChangeDateDoesNotInventBirthForAnUndatedBirthEvent() {
        TreeState state = TreeStore.parseGedcom("0 HEAD\n0 @I1@ INDI\n1 BIRT\n"
            + "1 CHAN\n2 DATE 11 MAY 2020\n0 TRLR\n");
        assertEquals("", state.people.values().iterator().next().bornYear);
    }

    private static TreeState fixture(String name) throws Exception {
        try (InputStream input = GedcomGenerationRegressionTest.class.getResourceAsStream("/fixtures/" + name + ".ged")) {
            assertNotNull(input);
            return TreeStore.parseGedcom(TreeStore.normalizeImportedText(TreeStore.readAll(input)));
        }
    }

    private static Person person(TreeState state, String name) {
        return state.people.values().stream().filter(p -> name.equals(p.name)).findFirst()
            .orElseThrow(() -> new AssertionError("Missing " + name));
    }

    private static void assertParentOrder(TreeState state, boolean exact) {
        for (Relation r : state.links) {
            if (!"parent".equals(r.type)) continue;
            Person parent = state.people.get(r.from), child = state.people.get(r.to);
            float delta = child.y - parent.y;
            assertTrue(parent.name + " -> " + child.name + ": " + delta, delta >= TreeLayoutEngine.LEVEL_GAP - 0.1f);
            if (exact) assertEquals(TreeLayoutEngine.LEVEL_GAP, delta, 0.1f);
        }
    }

    private static void assertNoOverlaps(TreeState state) {
        List<Person> people = new ArrayList<>(state.people.values());
        for (int i = 0; i < people.size(); i++) {
            Person a = people.get(i);
            assertTrue(Float.isFinite(a.x) && Float.isFinite(a.y));
            for (int j = i + 1; j < people.size(); j++) {
                Person b = people.get(j);
                assertFalse(a.name + " overlaps " + b.name,
                    Math.abs(a.x - b.x) < TreeLayoutEngine.CARD_W - 0.1f
                    && Math.abs(a.y - b.y) < TreeLayoutEngine.CARD_H - 0.1f);
            }
        }
    }

    private static void assertValid(TreeState state) {
        LayoutConstraints.ValidationResult result = new LayoutConstraints().validate(
            FamilyLayoutGraph.from(state), LayoutSnapshot.capture(state));
        assertTrue(result.violations.stream().map(v -> v.code).collect(java.util.stream.Collectors.joining(", ")),
            result.isValid());
    }
}
