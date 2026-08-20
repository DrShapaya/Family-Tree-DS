package ru.drshapaya.androidft2;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

final class BirthdayCalculator {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    static final class Result {
        final Person person;
        final int daysUntil;
        final int occurrenceYear;
        final int age;
        final int day;
        final int month;

        Result(Person person, int daysUntil, int occurrenceYear, int age, int day, int month) {
            this.person = person;
            this.daysUntil = daysUntil;
            this.occurrenceYear = occurrenceYear;
            this.age = age;
            this.day = day;
            this.month = month;
        }
    }

    private BirthdayCalculator() {}

    static Result nearest(TreeState state, Calendar now) {
        return nearest(state, now, java.util.Collections.emptySet());
    }

    static Result nearest(TreeState state, Calendar now, Set<String> excludedPersonIds) {
        if (state == null || state.people == null || state.people.isEmpty()) return null;
        Calendar localNow = now == null ? Calendar.getInstance() : now;
        int todayYear = localNow.get(Calendar.YEAR);
        int todayMonth = localNow.get(Calendar.MONTH) + 1;
        int todayDay = localNow.get(Calendar.DAY_OF_MONTH);
        long todayUtc = utcDate(todayYear, todayMonth, todayDay).getTimeInMillis();

        Result nearest = null;
        for (Person person : state.people.values()) {
            if (person == null
                || hasDeathDate(person)
                || (excludedPersonIds != null && excludedPersonIds.contains(person.id))) continue;
            int day = parse(person.bornDay);
            int month = parse(person.bornMonth);
            if (!validBirthday(day, month)) continue;

            Calendar occurrence = occurrence(todayYear, month, day);
            if (occurrence.getTimeInMillis() < todayUtc) {
                occurrence = occurrence(todayYear + 1, month, day);
            }
            int occurrenceYear = occurrence.get(Calendar.YEAR);
            int actualMonth = occurrence.get(Calendar.MONTH) + 1;
            int actualDay = occurrence.get(Calendar.DAY_OF_MONTH);
            int days = (int) ((occurrence.getTimeInMillis() - todayUtc) / 86_400_000L);
            int bornYear = parse(person.bornYear);
            int age = bornYear > 0 && bornYear <= occurrenceYear
                ? occurrenceYear - bornYear
                : -1;
            Result candidate = new Result(
                person,
                Math.max(0, days),
                occurrenceYear,
                age,
                actualDay,
                actualMonth);
            if (nearest == null
                || candidate.daysUntil < nearest.daysUntil
                || (candidate.daysUntil == nearest.daysUntil
                    && safeName(candidate.person).compareToIgnoreCase(safeName(nearest.person)) < 0)) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    static Result nearest(TreeState state) {
        return nearest(state, Calendar.getInstance());
    }

    static Result nearest(TreeState state, Set<String> excludedPersonIds) {
        return nearest(state, Calendar.getInstance(), excludedPersonIds);
    }

    private static Calendar occurrence(int year, int month, int day) {
        int actualDay = day;
        if (month == 2 && day == 29 && !new GregorianCalendar().isLeapYear(year)) {
            actualDay = 28;
        }
        return utcDate(year, month, actualDay);
    }

    private static Calendar utcDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(UTC, Locale.ROOT);
        calendar.clear();
        calendar.setLenient(false);
        calendar.set(year, month - 1, day, 0, 0, 0);
        calendar.getTimeInMillis();
        return calendar;
    }

    private static boolean validBirthday(int day, int month) {
        if (month < 1 || month > 12 || day < 1) return false;
        try {
            utcDate(2000, month, day);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean hasDeathDate(Person person) {
        return present(person.died)
            || present(person.diedDay)
            || present(person.diedMonth)
            || present(person.diedYear);
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeName(Person person) {
        return person == null || person.name == null ? "" : person.name.trim();
    }
}
