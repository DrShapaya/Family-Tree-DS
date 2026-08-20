package ru.drshapaya.androidft2;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BirthdayCalculatorTest {
    @Test
    public void selectsNearestBirthdayAcrossNewYear() {
        TreeState state = new TreeState();
        state.people.put("later", person("later", "Позже", "20", "12", "1990"));
        state.people.put("nearest", person("nearest", "Скоро", "3", "1", "2000"));

        BirthdayCalculator.Result result = BirthdayCalculator.nearest(
            state,
            date(2026, 12, 29));

        assertNotNull(result);
        assertEquals("nearest", result.person.id);
        assertEquals(5, result.daysUntil);
        assertEquals(27, result.age);
    }

    @Test
    public void birthdayTodayHasZeroDays() {
        TreeState state = new TreeState();
        state.people.put("today", person("today", "Сегодня", "14", "8", "1980"));

        BirthdayCalculator.Result result = BirthdayCalculator.nearest(
            state,
            date(2026, 8, 14));

        assertNotNull(result);
        assertEquals(0, result.daysUntil);
        assertEquals(46, result.age);
    }

    @Test
    public void leapBirthdayUsesFebruaryTwentyEighthInCommonYear() {
        TreeState state = new TreeState();
        state.people.put("leap", person("leap", "Високосный", "29", "2", "2000"));

        BirthdayCalculator.Result result = BirthdayCalculator.nearest(
            state,
            date(2027, 2, 27));

        assertNotNull(result);
        assertEquals(1, result.daysUntil);
        assertEquals(28, result.day);
        assertEquals(2, result.month);
    }

    @Test
    public void ignoresDeceasedAndIncompleteDates() {
        TreeState state = new TreeState();
        Person deceased = person("deceased", "Умер", "15", "8", "1940");
        deceased.diedYear = "2020";
        state.people.put(deceased.id, deceased);
        state.people.put("partial", person("partial", "Только год", "", "", "1990"));

        assertNull(BirthdayCalculator.nearest(state, date(2026, 8, 14)));
    }

    @Test
    public void skipsPeopleExcludedFromWidget() {
        TreeState state = new TreeState();
        state.people.put("nearest", person("nearest", "Ближайший", "15", "8", "1990"));
        state.people.put("allowed", person("allowed", "Разрешён", "20", "8", "1995"));

        BirthdayCalculator.Result result = BirthdayCalculator.nearest(
            state,
            date(2026, 8, 14),
            Collections.singleton("nearest"));

        assertNotNull(result);
        assertEquals("allowed", result.person.id);
        assertEquals(6, result.daysUntil);
    }

    private static Person person(
        String id,
        String name,
        String day,
        String month,
        String year
    ) {
        Person person = new Person(id);
        person.name = name;
        person.bornDay = day;
        person.bornMonth = month;
        person.bornYear = year;
        return person;
    }

    private static Calendar date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar;
    }
}
