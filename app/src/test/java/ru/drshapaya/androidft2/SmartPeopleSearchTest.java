package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public final class SmartPeopleSearchTest {
    @Test
    public void findsPeopleWithoutPhoto() {
        TreeState state = new TreeState();
        Person anna = person("anna", "Анна Иванова", "1880");
        Person sergey = person("sergey", "Сергей Петров", "1990");
        sergey.photoMediaId = "photo_existing";
        state.people.put(anna.id, anna);
        state.people.put(sergey.id, sergey);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "У кого нет фотографии?", anna.id);

        assertTrue(matches(state, query, anna));
        assertFalse(matches(state, query, sergey));
        assertEquals("Люди → нет фотографии → показать 1 человека", SmartPeopleSearch.explanation(query, 1));
    }

    @Test
    public void filtersBirthYearBeforeThreshold() {
        TreeState state = new TreeState();
        Person older = person("older", "Старший", "1899");
        Person younger = person("younger", "Младший", "1900");
        state.people.put(older.id, older);
        state.people.put(younger.id, younger);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "родившиеся до 1900", older.id);

        assertTrue(matches(state, query, older));
        assertFalse(matches(state, query, younger));
    }

    @Test
    public void yearAloneMeansBirthYear() {
        TreeState state = new TreeState();
        Person target = person("target", "Цель", "2006");
        Person other = person("other", "Другой", "2007");
        state.people.put(target.id, target);
        state.people.put(other.id, other);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "2006", target.id);

        assertTrue(matches(state, query, target));
        assertFalse(matches(state, query, other));
    }

    @Test
    public void filtersBirthdaysByMonth() {
        TreeState state = new TreeState();
        Person august = person("august", "Август", "1990");
        august.bornDay = "12";
        august.bornMonth = "8";
        Person september = person("september", "Сентябрь", "1990");
        september.bornDay = "12";
        september.bornMonth = "9";
        state.people.put(august.id, august);
        state.people.put(september.id, september);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "дни рождения в августе", august.id);

        assertTrue(matches(state, query, august));
        assertFalse(matches(state, query, september));
    }

    @Test
    public void monthNameAloneMeansBirthdaysInThatMonth() {
        TreeState state = new TreeState();
        Person july = person("july", "Июль", "1990");
        july.bornDay = "7";
        july.bornMonth = "7";
        Person august = person("august", "Август", "1990");
        august.bornDay = "8";
        august.bornMonth = "8";
        state.people.put(july.id, july);
        state.people.put(august.id, august);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "июль", july.id);

        assertTrue(matches(state, query, july));
        assertFalse(matches(state, query, august));
    }

    @Test
    public void filtersExactBirthdayDayAndMonth() {
        TreeState state = new TreeState();
        Person target = person("target", "Седьмой", "1990");
        target.bornDay = "7";
        target.bornMonth = "7";
        Person other = person("other", "Восьмой", "1990");
        other.bornDay = "8";
        other.bornMonth = "7";
        state.people.put(target.id, target);
        state.people.put(other.id, other);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "7 июля", target.id);

        assertTrue(matches(state, query, target));
        assertFalse(matches(state, query, other));
    }

    @Test
    public void filtersBirthdayMonthAndBirthYearTogether() {
        TreeState state = new TreeState();
        Person target = person("target", "Июль 2006", "2006");
        target.bornDay = "7";
        target.bornMonth = "7";
        Person wrongYear = person("wrongYear", "Июль 2007", "2007");
        wrongYear.bornDay = "7";
        wrongYear.bornMonth = "7";
        Person wrongMonth = person("wrongMonth", "Август 2006", "2006");
        wrongMonth.bornDay = "7";
        wrongMonth.bornMonth = "8";
        state.people.put(target.id, target);
        state.people.put(wrongYear.id, wrongYear);
        state.people.put(wrongMonth.id, wrongMonth);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "июль год 2006", target.id);

        assertTrue(matches(state, query, target));
        assertFalse(matches(state, query, wrongYear));
        assertFalse(matches(state, query, wrongMonth));
    }

    @Test
    public void filtersWithPhotoAndBirthDate() {
        TreeState state = new TreeState();
        Person filled = person("filled", "Заполнен", "1990");
        filled.photoMediaId = "photo_existing";
        Person empty = person("empty", "Пустой", "");
        state.people.put(filled.id, filled);
        state.people.put(empty.id, empty);

        assertTrue(matches(state, SmartPeopleSearch.parse(state, "с фото", filled.id), filled));
        assertFalse(matches(state, SmartPeopleSearch.parse(state, "с фото", filled.id), empty));
        assertTrue(matches(state, SmartPeopleSearch.parse(state, "с датой рождения", filled.id), filled));
        assertFalse(matches(state, SmartPeopleSearch.parse(state, "с датой рождения", filled.id), empty));
    }

    @Test
    public void findsAncestorsOfNamedPerson() {
        TreeState state = new TreeState();
        Person father = person("father", "Иван Петров", "1950");
        Person child = person("child", "Сергей Петров", "1980");
        state.people.put(father.id, father);
        state.people.put(child.id, child);
        state.addRelation("parent", father.id, child.id);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "предки Сергея", child.id);

        assertTrue(matches(state, query, father));
        assertFalse(matches(state, query, child));
    }

    @Test
    public void relationCommandWithoutNameDoesNotUseSelectedPerson() {
        TreeState state = new TreeState();
        Person father = person("father", "Иван Петров", "1950");
        Person child = person("child", "Сергей Петров", "1980");
        state.people.put(father.id, father);
        state.people.put(child.id, child);
        state.addRelation("parent", father.id, child.id);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "предки ", child.id);

        assertTrue(query.scopedIds.isEmpty());
    }

    @Test
    public void recognizesKinshipQuestion() {
        TreeState state = new TreeState();
        Person anna = person("anna", "Анна Петрова", "1950");
        Person sergey = person("sergey", "Сергей Петров", "1980");
        state.people.put(anna.id, anna);
        state.people.put(sergey.id, sergey);
        state.addRelation("parent", anna.id, sergey.id);

        SmartPeopleSearch.Query query = SmartPeopleSearch.parse(state, "Как Анна связана с Сергеем?", sergey.id);

        assertEquals(anna.id, query.kinshipFirstId);
        assertEquals(sergey.id, query.kinshipSecondId);
        assertTrue(query.kinshipSummary.contains("мать"));
    }

    @Test
    public void findsByGivenNameOrPatronymicRegardlessOfNameOrder() {
        TreeState state = new TreeState();
        Person target = person("target", "Иванов Сергей Петрович", "1980");
        Person other = person("other", "Сидоров Алексей Николаевич", "1981");
        state.people.put(target.id, target);
        state.people.put(other.id, other);

        assertTrue(matches(state, SmartPeopleSearch.parse(state, "Сергей", target.id), target));
        assertTrue(matches(state, SmartPeopleSearch.parse(state, "Петрович", target.id), target));
        assertFalse(matches(state, SmartPeopleSearch.parse(state, "Петрович", target.id), other));
    }

    @Test
    public void toleratesSmallTyposButRejectsUnrelatedText() {
        TreeState state = new TreeState();
        Person target = person("target", "Иванов Сергей Петрович", "1980");
        state.people.put(target.id, target);

        assertTrue(matches(state, SmartPeopleSearch.parse(state, "Сергеи", target.id), target));
        assertTrue(matches(state, SmartPeopleSearch.parse(state, "Сиргеи", target.id), target));
        assertTrue(SmartPeopleSearch.suggestionScore(target.name, "Петровичь") > 0);
        assertFalse(matches(state, SmartPeopleSearch.parse(state, "Кузнецов", target.id), target));
    }

    private static boolean matches(TreeState state, SmartPeopleSearch.Query query, Person person) {
        return SmartPeopleSearch.matches(
            state,
            query,
            person,
            value -> value != null && (!value.photoMediaId.isEmpty() || !value.photo.isEmpty()),
            date(2026, 8, 1));
    }

    private static Person person(String id, String name, String bornYear) {
        Person person = new Person(id);
        person.name = name;
        person.bornYear = bornYear;
        person.gender = PersonGender.infer(name);
        return person;
    }

    private static Calendar date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar;
    }
}
