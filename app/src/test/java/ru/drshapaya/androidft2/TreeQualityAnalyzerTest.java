package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public final class TreeQualityAnalyzerTest {
    @Test
    public void incompatibleLifeDatesAreErrors() {
        TreeState state = new TreeState();
        Person person = person("p1", "Иванов Иван", "1980");
        person.diedYear = "1979";
        state.people.put(person.id, person);
        state.rootId = person.id;

        TreeQualityAnalyzer.PersonReport report = TreeQualityAnalyzer.analyze(state).person(person.id);

        assertTrue(report.errors() > 0);
        assertTrue(report.countCategory(TreeQualityAnalyzer.CATEGORY_DATES) > 0);
    }

    @Test
    public void futureBirthYearIsAnImmediateDateError() {
        TreeState state = new TreeState();
        String futureYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR) + 40);
        Person person = person("p1", "Иванов Иван", futureYear);
        state.people.put(person.id, person);
        state.rootId = person.id;

        TreeQualityAnalyzer.PersonReport report = TreeQualityAnalyzer.analyze(state).person(person.id);

        assertTrue(report.errors() > 0);
        assertTrue(report.countCategory(TreeQualityAnalyzer.CATEGORY_DATES) > 0);
    }

    @Test
    public void unusualParentAgeIsWarning() {
        TreeState state = new TreeState();
        Person parent = person("parent", "Иванов Пётр", "1950");
        Person child = person("child", "Иванов Иван", "2018");
        state.people.put(parent.id, parent);
        state.people.put(child.id, child);
        state.rootId = child.id;
        state.links.add(new Relation("link", "parent", parent.id, child.id));

        TreeQualityAnalyzer.PersonReport report = TreeQualityAnalyzer.analyze(state).person(child.id);

        assertEquals(1, report.countCategory(TreeQualityAnalyzer.CATEGORY_PARENT_AGE));
        assertTrue(report.warnings() > 0);
    }

    @Test
    public void completenessRewardsFilledCard() {
        TreeState state = new TreeState();
        Person person = person("p1", "Иванов Иван Петрович", "1980");
        person.bornDay = "12";
        person.bornMonth = "5";
        person.gender = PersonGender.MALE;
        person.place = "Красноярск";
        person.notes = "Биография";
        person.photoMediaId = "photo.jpg";
        person.memories.add(new Memory());
        state.people.put(person.id, person);
        state.rootId = person.id;

        TreeQualityAnalyzer.PersonReport report = TreeQualityAnalyzer.analyze(state).person(person.id);

        assertTrue(report.completeness >= 90);
    }

    private static Person person(String id, String name, String bornYear) {
        Person person = new Person(id);
        person.name = name;
        person.bornYear = bornYear;
        return person;
    }
}
