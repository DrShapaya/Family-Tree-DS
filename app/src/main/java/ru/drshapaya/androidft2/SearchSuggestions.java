package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/** Shared, UI-independent suggestions for the tree header and people panel. */
final class SearchSuggestions {
    static final class Suggestion {
        final String label;
        final String applyText;
        final String personId;

        Suggestion(String label, String applyText, String personId) {
            this.label = label;
            this.applyText = applyText;
            this.personId = personId == null ? "" : personId;
        }
    }

    private SearchSuggestions() {}

    static List<Suggestion> build(
        String input,
        int limit,
        Collection<Person> people,
        Supplier<List<String>> places,
        Supplier<List<String>> years,
        String unnamedPerson
    ) {
        List<Suggestion> result = new ArrayList<>();
        if (limit <= 0 || people == null) return result;
        Set<String> seen = new LinkedHashSet<>();
        String commandPrefix = relationCommandPrefix(input);
        String raw = input == null ? "" : input;
        String suggestionInput = commandPrefix.isEmpty()
            ? raw : raw.substring(Math.min(commandPrefix.length(), raw.length()));
        String normalizedInput = normalize(suggestionInput);
        if (normalizedInput.isEmpty()) return result;
        boolean trailingSpace = suggestionInput.endsWith(" ");
        String[] tokens = normalizedInput.split("\\s+");

        List<RankedPerson> rankedPeople = new ArrayList<>(people.size());
        for (Person person : people) {
            String name = displayName(person, unnamedPerson);
            int score = SmartPeopleSearch.suggestionScore(name, normalizedInput);
            if (score > 0) rankedPeople.add(new RankedPerson(person, name, score));
        }
        rankedPeople.sort((first, second) -> Integer.compare(second.score, first.score));
        for (RankedPerson ranked : rankedPeople) {
            Person person = ranked.person;
            String name = ranked.name;
            add(result, seen, normalizedInput, commandPrefix, name, name, person.id, limit);
            if (result.size() >= limit) return result;
        }

        if (commandPrefix.isEmpty() && tokens.length <= 1 && !trailingSpace) {
            for (String place : places.get()) {
                if (!normalize(place).startsWith(tokens[0])) continue;
                add(result, seen, normalizedInput, "", place, place, "", limit);
                if (result.size() >= limit) return result;
            }
            for (int month = 1; month <= 12; month++) {
                String name = monthName(month);
                if (normalize(name).startsWith(tokens[0])) {
                    add(result, seen, normalizedInput, "", name, name, "", limit);
                }
                if (result.size() >= limit) return result;
            }
            for (String year : years.get()) {
                if (year.startsWith(tokens[0])) {
                    add(result, seen, normalizedInput, "", year, year, "", limit);
                }
                if (result.size() >= limit) return result;
            }
        }
        return result;
    }

    static List<String> treePlaces(Collection<Person> people) {
        List<String> places = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Person person : people) {
            String place = person.place == null ? "" : person.place.trim();
            if (place.isEmpty()) continue;
            for (String part : place.split("[,;·/\\\\]")) {
                String clean = part.trim();
                if (clean.isEmpty()) continue;
                if (seen.add(normalize(clean))) places.add(clean);
            }
        }
        return places;
    }

    static List<String> birthYears(Collection<Person> people) {
        Set<String> uniqueYears = new LinkedHashSet<>();
        for (Person person : people) {
            String year = person.bornYear == null ? "" : person.bornYear.replaceAll("[^0-9]", "");
            if (year.length() >= 3) uniqueYears.add(year);
        }
        List<String> years = new ArrayList<>(uniqueYears);
        java.util.Collections.sort(years);
        return years;
    }

    private static void add(
        List<Suggestion> result,
        Set<String> seen,
        String normalizedInput,
        String prefix,
        String label,
        String apply,
        String personId,
        int limit
    ) {
        String key = normalize(label);
        if (result.size() >= limit || key.isEmpty()
            || key.equals(normalizedInput) && (personId == null || personId.isEmpty())
            || !seen.add(key)) return;
        result.add(new Suggestion(label, prefix + apply, personId));
    }

    private static String relationCommandPrefix(String input) {
        String raw = input == null ? "" : input;
        String normalized = normalize(raw);
        for (String command : new String[]{"братья и сестры", "предки", "потомки", "родственники", "родственников",
            "предков", "потомков", "дети", "детей", "родители", "родителей",
            "партнеры", "партнеров", "братья", "братьев", "сестры", "сестер"}) {
            if (normalized.equals(command)) return raw.endsWith(" ") ? raw : raw + " ";
            if (normalized.startsWith(command + " ")) {
                int index = raw.toLowerCase(Locale.ROOT).replace('ё', 'е').indexOf(command);
                if (index >= 0) {
                    int end = Math.min(raw.length(), index + command.length());
                    while (end < raw.length() && Character.isWhitespace(raw.charAt(end))) end++;
                    return raw.substring(0, end);
                }
            }
        }
        return "";
    }

    private static String displayName(Person person, String unnamedPerson) {
        return person == null || person.name == null || person.name.trim().isEmpty()
            ? unnamedPerson : person.name.trim();
    }

    private static final class RankedPerson {
        final Person person;
        final String name;
        final int score;
        RankedPerson(Person person, String name, int score) {
            this.person = person; this.name = name; this.score = score;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replaceAll("[^\\p{L}\\d ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String monthName(int month) {
        String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
        return months[month - 1];
    }
}
