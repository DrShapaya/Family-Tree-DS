package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GedcomNameImportTest {
    @Test
    public void importsSurnameBeforeGivenNameAndPatronymic() {
        assertImportedName("Иван Петрович /Иванов/", "Иванов Иван Петрович");
        assertImportedName("Иван /Иванов/ Петрович", "Иванов Иван Петрович");
        assertImportedName("/Иванов/ Иван Петрович", "Иванов Иван Петрович");
    }

    @Test
    public void preservesCompoundNamesAndSuffixes() {
        assertImportedName("Анна Мария /Салтыкова-Щедрина/", "Салтыкова-Щедрина Анна Мария");
        assertImportedName("Ludwig /van Beethoven/", "van Beethoven Ludwig");
        assertImportedName("John /Smith/ Jr.", "Smith John Jr.");
    }

    @Test
    public void normalizesSpacingAroundSurnameMarkers() {
        assertImportedName("Иван   Петрович/ Иванов /", "Иванов Иван Петрович");
    }

    @Test
    public void preservesNamesWithoutSurnameMarkersIncludingOwnExports() {
        assertImportedName("Иванов Иван Петрович", "Иванов Иван Петрович");
        assertImportedName("Иван Петрович Иванов", "Иван Петрович Иванов");
        assertImportedName("Иван", "Иван");
    }

    @Test
    public void acceptsMissingNameParts() {
        assertImportedName("/Иванов/", "Иванов");
        assertImportedName("Иван Петрович //", "Иван Петрович");
        assertImportedName("//", "Без имени");
        assertImportedName("", "Без имени");
    }

    @Test
    public void toleratesUnpairedSurnameMarkerWithoutLosingNameParts() {
        assertImportedName("Иван /Иванов", "Иван Иванов");
    }

    @Test
    public void surnameColorUsesImportedSurnameAndFullNameColorUsesWholeName() {
        TreeState state = TreeStore.parseGedcom("0 HEAD\n1 CHAR UTF-8\n"
            + "0 @I1@ INDI\n1 NAME Иван Петрович /Иванов/\n"
            + "0 @I2@ INDI\n1 NAME Анна Сергеевна /Иванова/\n0 TRLR\n");
        assertEquals(2, state.people.size());
        int index = 0;
        for (Person person : state.people.values()) {
            assertEquals(TreeState.colorFor(person.name, index), person.color);
            assertEquals("иванов", TreeState.surnameOf(person.name));
            person.colorMode = "auto-surname";
            assertEquals(TreeState.colorFor("иванов", index), TreeState.displayColor(person, index));
            index++;
        }
    }

    private static void assertImportedName(String gedcomName, String expected) {
        TreeState state = TreeStore.parseGedcom("0 HEAD\n1 CHAR UTF-8\n"
            + "0 @I1@ INDI\n1 NAME " + gedcomName + "\n0 TRLR\n");
        assertEquals(1, state.people.size());
        assertEquals(expected, state.people.values().iterator().next().name);
    }
}
