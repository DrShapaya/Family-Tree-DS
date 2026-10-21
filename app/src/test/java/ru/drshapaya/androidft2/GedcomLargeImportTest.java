package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GedcomLargeImportTest {
    @Test(timeout = 30_000L)
    public void importedUserSampleHasFourHundredPeople() throws Exception {
        String gedcom;
        try (java.io.InputStream input = GedcomLargeImportTest.class.getResourceAsStream(
            "/fixtures/connected_family_tree_400.ged")) {
            assertTrue("fixture must exist", input != null);
            gedcom = TreeStore.readAll(input);
        }
        assertEquals("sample must retain its UTF-8 BOM", '\uFEFF', gedcom.charAt(0));
        String normalized = TreeStore.normalizeImportedText(gedcom);
        assertTrue("the real import path must recognize GEDCOM after its BOM",
            TreeStore.looksLikeGedcom(normalized));
        long started = System.nanoTime();
        TreeState state = TreeStore.parseGedcom(normalized);
        assertTrue("sample import should finish promptly",
            (System.nanoTime() - started) / 1_000_000L < 10_000L);
        assertEquals(400, state.people.size());
        assertEquals(468, state.links.size());
    }
    @Test(timeout = 15_000L)
    public void importsLargeGedcomWithoutStalling() throws Exception {
        int peopleCount = 1_000;
        String gedcom = largeGedcom(peopleCount);
        int expectedLinks = expectedLinkCount(peopleCount);

        long startedAt = System.nanoTime();
        TreeState state = TreeStore.parseGedcom(gedcom);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(peopleCount, state.people.size());
        assertEquals(expectedLinks, state.links.size());
        assertTrue("GEDCOM import took " + elapsedMillis + " ms", elapsedMillis < 10_000L);
        assertTrue("deep lineage must expand past the legacy canvas limit",
            state.workspaceHeight > 100_000);
        java.util.List<Person> ordered = new java.util.ArrayList<>(state.people.values());
        float finalQuarterDrift = ordered.get(peopleCount * 3 / 4).x
            - ordered.get(peopleCount - 4).x;
        assertTrue("the branch stopped progressing near its tail: " + finalQuarterDrift,
            Math.abs(finalQuarterDrift) > 2_000f);
        java.util.Set<String> occupied = new java.util.HashSet<>();
        for (Person person : state.people.values()) {
            assertTrue(Float.isFinite(person.x));
            assertTrue(Float.isFinite(person.y));
            assertTrue(
                "overlapping imported cards at " + person.x + "," + person.y,
                occupied.add(person.x + ":" + person.y));
        }
    }

    @Test(timeout = 15_000L)
    public void identicalImportsKeepSameGeometryDespiteGeneratedIds() throws Exception {
        String gedcom = largeGedcom(240);
        TreeState first = TreeStore.parseGedcom(gedcom);
        TreeState second = TreeStore.parseGedcom(gedcom);
        java.util.Iterator<Person> firstPeople = first.people.values().iterator();
        java.util.Iterator<Person> secondPeople = second.people.values().iterator();
        while (firstPeople.hasNext() && secondPeople.hasNext()) {
            Person firstPerson = firstPeople.next();
            Person secondPerson = secondPeople.next();
            assertEquals(firstPerson.x, secondPerson.x, 0.01f);
            assertEquals(firstPerson.y, secondPerson.y, 0.01f);
        }
        assertEquals(firstPeople.hasNext(), secondPeople.hasNext());
    }

    private static String largeGedcom(int peopleCount) {
        StringBuilder gedcom = new StringBuilder("0 HEAD\n1 CHAR UTF-8\n");
        for (int index = 0; index < peopleCount; index++) {
            gedcom.append("0 @I").append(index).append("@ INDI\n")
                .append("1 NAME Person ").append(index).append(" /Test/\n");
        }
        for (int family = 0; family * 3 + 1 < peopleCount; family++) {
            int firstParent = family * 3;
            int secondParent = firstParent + 1;
            gedcom.append("0 @F").append(family).append("@ FAM\n")
                .append("1 HUSB @I").append(firstParent).append("@\n")
                .append("1 WIFE @I").append(secondParent).append("@\n");
            for (int child = firstParent + 2;
                 child <= firstParent + 4 && child < peopleCount;
                 child++) {
                gedcom.append("1 CHIL @I").append(child).append("@\n");
            }
        }
        return gedcom.append("0 TRLR\n").toString();
    }

    private static int expectedLinkCount(int peopleCount) {
        int result = 0;
        for (int family = 0; family * 3 + 1 < peopleCount; family++) {
            int firstParent = family * 3;
            result++;
            for (int child = firstParent + 2;
                 child <= firstParent + 4 && child < peopleCount;
                 child++) result += 2;
        }
        return result;
    }
}
