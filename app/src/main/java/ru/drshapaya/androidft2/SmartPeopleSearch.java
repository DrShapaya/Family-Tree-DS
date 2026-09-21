package ru.drshapaya.androidft2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SmartPeopleSearch {
    interface PhotoResolver {
        boolean hasPhoto(Person person);
    }

    static final class Query {
        final String raw;
        final String normalized;
        final List<String> parts = new ArrayList<>();
        boolean active;
        String freeText = "";
        boolean withoutPhoto;
        boolean withPhoto;
        boolean withoutBirthDate;
        boolean withBirthDate;
        String life = "";
        int bornBefore;
        int bornAfter;
        int rangeStart;
        int rangeEnd;
        int exactBirthYear;
        boolean birthdays;
        int birthdayMonth;
        int birthdayDay;
        int upcomingBirthdayDays;
        int ageOlderThan;
        String diedPlace = "";
        String scope = "";
        String anchorId = "";
        String anchorName = "";
        Set<String> scopedIds = Collections.emptySet();
        String kinshipFirstId = "";
        String kinshipSecondId = "";
        String kinshipSummary = "";

        Query(String raw) {
            this.raw = raw == null ? "" : raw.trim();
            this.normalized = normalize(this.raw);
            this.active = !this.normalized.isEmpty();
        }
    }

    private static final Pattern YEAR = Pattern.compile("(\\d{3,4})");
    private static final Pattern RANGE = Pattern.compile("(\\d{3,4})\\s*(?:-|–|—|по|до)\\s*(\\d{3,4})");
    private static final Pattern OLDER_THAN = Pattern.compile("старше\\s+(\\d{1,3})");
    private static final Pattern BEFORE = Pattern.compile("(?:до|раньше)\\s+(\\d{3,4})");
    private static final Pattern AFTER = Pattern.compile("(?:после|позже)\\s+(\\d{3,4})");

    private SmartPeopleSearch() {
    }

    static Query parse(TreeState state, String raw, String selectedId) {
        Query query = new Query(raw);
        if (!query.active) return query;
        String n = query.normalized;

        parseKinship(state, query);
        if (!query.kinshipFirstId.isEmpty() && !query.kinshipSecondId.isEmpty()) return query;

        if (containsAny(n, "без фото", "без фотографии", "нет фото", "нет фотографии")) {
            query.withoutPhoto = true;
            query.parts.add("нет фотографии");
        }
        if (!query.withoutPhoto && containsAny(n, "с фото", "с фотографией", "есть фото", "есть фотография")) {
            query.withPhoto = true;
            query.parts.add("с фотографией");
        }
        if (containsAny(n, "без даты рождения", "нет даты рождения", "дата рождения не указана")) {
            query.withoutBirthDate = true;
            query.parts.add("нет даты рождения");
        }
        if (!query.withoutBirthDate && containsAny(n, "с датой рождения", "есть дата рождения")) {
            query.withBirthDate = true;
            query.parts.add("с датой рождения");
        }
        if (containsAny(n, "живые", "живущие")) {
            query.life = "living";
            query.parts.add("живые");
        }
        if (containsAny(n, "умерш", "покойн")) {
            query.life = "dead";
            query.parts.add("умершие");
            String place = tailAfter(n, " в ");
            if (!place.isEmpty()) {
                query.diedPlace = cleanFreeText(place);
                if (!query.diedPlace.isEmpty()) query.parts.add("место: " + query.diedPlace);
            }
        }

        parseDateFilters(query);
        parseRelationScope(state, query, selectedId);

        String free = cleanFreeText(n);
        if (query.parts.isEmpty() || (!hasStructuredFilters(query) && looksLikePlainSearch(n))) {
            query.freeText = free;
            if (!query.freeText.isEmpty()) query.parts.add("поиск: " + query.freeText);
        } else if (query.diedPlace.isEmpty() && !query.scope.isEmpty()) {
            String candidate = removeKnownWords(free);
            if (!candidate.isEmpty() && findPerson(state, candidate) == null) {
                query.freeText = candidate;
                query.parts.add("поиск: " + query.freeText);
            }
        }
        return query;
    }

    private static boolean hasStructuredFilters(Query query) {
        return query.withoutPhoto
            || query.withPhoto
            || query.withoutBirthDate
            || query.withBirthDate
            || !query.life.isEmpty()
            || query.bornBefore > 0
            || query.bornAfter > 0
            || query.rangeStart > 0
            || query.rangeEnd > 0
            || query.exactBirthYear > 0
            || query.birthdays
            || query.ageOlderThan > 0
            || !query.diedPlace.isEmpty()
            || !query.scope.isEmpty();
    }

    static boolean matches(TreeState state, Query query, Person person, PhotoResolver photoResolver, Calendar now) {
        if (query == null || !query.active) return true;
        if (person == null) return false;
        if (!query.scopedIds.isEmpty() && !query.scopedIds.contains(person.id)) return false;
        if (query.withoutPhoto && photoResolver != null && photoResolver.hasPhoto(person)) return false;
        if (query.withPhoto && (photoResolver == null || !photoResolver.hasPhoto(person))) return false;
        if (query.withoutBirthDate && hasBirthDate(person)) return false;
        if (query.withBirthDate && !hasBirthDate(person)) return false;
        boolean dead = hasDeathDate(person);
        if ("living".equals(query.life) && dead) return false;
        if ("dead".equals(query.life) && !dead) return false;
        int born = year(person.bornYear);
        if (query.bornBefore > 0 && (born <= 0 || born >= query.bornBefore)) return false;
        if (query.bornAfter > 0 && (born <= query.bornAfter)) return false;
        if (query.rangeStart > 0 && query.rangeEnd > 0 && (born < query.rangeStart || born > query.rangeEnd)) return false;
        if (query.exactBirthYear > 0 && born != query.exactBirthYear) return false;
        if (query.birthdays && !matchesBirthday(query, person, now)) return false;
        if (query.ageOlderThan > 0 && !isOlderThan(person, query.ageOlderThan, now)) return false;
        if (!query.diedPlace.isEmpty() && !containsNormalized(person.place, query.diedPlace)) return false;
        if (!query.freeText.isEmpty() && !matchesFreeText(person, query.freeText)) return false;
        return true;
    }

    static String explanation(Query query, int count) {
        if (query == null || !query.active) return "";
        List<String> parts = new ArrayList<>();
        if (!query.kinshipSummary.isEmpty()) {
            parts.add("Родство");
            parts.add(query.kinshipSummary);
        } else {
            parts.add("Люди");
            parts.addAll(query.parts);
        }
        parts.add("показать " + count + " " + peopleWord(count));
        return join(parts, " → ");
    }

    static Person findPerson(TreeState state, String fragment) {
        if (state == null || fragment == null || fragment.trim().isEmpty()) return null;
        String needle = normalizePersonKey(fragment);
        Person best = null;
        int bestScore = -1;
        for (Person person : state.people.values()) {
            String name = normalizePersonKey(person.name);
            int score = personMatchScore(name, needle);
            if (score <= 0) score = fuzzyTokenScore(name, needle);
            if (score > bestScore) {
                bestScore = score;
                best = person;
            }
        }
        return bestScore <= 0 ? null : best;
    }

    /** Scores a name or other short label for autocomplete, including small typos. */
    static int suggestionScore(String candidate, String query) {
        return fuzzyTokenScore(normalize(candidate), normalize(query));
    }

    private static void parseKinship(TreeState state, Query query) {
        Matcher matcher = Pattern.compile("как\\s+(.+?)\\s+связан\\S*\\s+с\\s+(.+)").matcher(query.normalized);
        if (!matcher.find()) {
            matcher = Pattern.compile("кто\\s+(.+?)\\s+для\\s+(.+)").matcher(query.normalized);
            if (!matcher.find()) return;
        }
        Person first = findPerson(state, matcher.group(1));
        Person second = findPerson(state, matcher.group(2));
        if (first == null || second == null) return;
        KinshipCalculator.Result result = KinshipCalculator.calculate(state, first.id, second.id);
        query.kinshipFirstId = first.id;
        query.kinshipSecondId = second.id;
        query.kinshipSummary = displayName(first) + " и " + displayName(second) + ": "
            + (result == null ? "родство не определено" : result.firstToSecond);
        query.parts.add(query.kinshipSummary);
    }

    private static void parseDateFilters(Query query) {
        String n = query.normalized;
        Matcher range = RANGE.matcher(n);
        if (n.matches("\\d{3,4}")) {
            query.exactBirthYear = intValue(n);
            query.parts.add("год рождения " + query.exactBirthYear);
        } else if (containsAny(n, "родивш", "рожд", "год") && range.find()) {
            query.rangeStart = Math.min(intValue(range.group(1)), intValue(range.group(2)));
            query.rangeEnd = Math.max(intValue(range.group(1)), intValue(range.group(2)));
            query.parts.add("рождение " + query.rangeStart + "-" + query.rangeEnd);
        } else if (containsAny(n, "родивш", "рожд", "до ", "раньше")) {
            Matcher before = BEFORE.matcher(n);
            if (before.find()) {
                query.bornBefore = intValue(before.group(1));
                query.parts.add("родились до " + query.bornBefore);
            }
        }
        if (containsAny(n, "родивш", "рожд", "после ", "позже")) {
            Matcher after = AFTER.matcher(n);
            if (after.find()) {
                query.bornAfter = intValue(after.group(1));
                query.parts.add("родились после " + query.bornAfter);
            }
        }
        if (query.rangeStart == 0 && query.bornBefore == 0 && query.bornAfter == 0
            && containsAny(n, "родивш", "рожд", "год")) {
            Matcher year = YEAR.matcher(n);
            if (year.find()) {
                query.exactBirthYear = intValue(year.group(1));
                query.parts.add("год рождения " + query.exactBirthYear);
            }
        }
        int parsedMonth = monthNumber(n);
        int parsedDay = birthdayDayNumber(n, parsedMonth);
        if (containsAny(n, "день рождения", "дни рождения", "днем рождения", "скоро день")
            || looksLikeMonthOnly(n, parsedMonth)
            || (parsedMonth > 0 && query.exactBirthYear > 0)
            || (parsedDay > 0 && parsedMonth > 0)) {
            query.birthdays = true;
            query.upcomingBirthdayDays = containsAny(n, "скоро", "ближайш") ? 31 : 0;
            query.birthdayMonth = parsedMonth;
            query.birthdayDay = parsedDay;
            if (query.birthdayDay > 0 && query.birthdayMonth > 0) {
                query.parts.add("день рождения: " + query.birthdayDay + " " + monthName(query.birthdayMonth));
            } else if (query.birthdayMonth > 0) query.parts.add("дни рождения: " + monthName(query.birthdayMonth));
            else query.parts.add(query.upcomingBirthdayDays > 0 ? "ближайшие дни рождения" : "дни рождения");
        }
        Matcher older = OLDER_THAN.matcher(n);
        if (older.find()) {
            query.ageOlderThan = intValue(older.group(1));
            query.parts.add("старше " + query.ageOlderThan + " лет");
        }
    }

    private static void parseRelationScope(TreeState state, Query query, String selectedId) {
        String n = query.normalized;
        if (containsAny(n, "по линии матери", "материнск")) {
            Person anchor = state == null ? null : state.people.get(selectedId);
            Set<String> ids = maternalLine(state, anchor == null ? "" : anchor.id);
            if (!ids.isEmpty()) {
                query.scope = "maternal";
                query.anchorId = anchor.id;
                query.anchorName = displayName(anchor);
                query.scopedIds = ids;
                query.parts.add("предки по линии матери");
            }
            return;
        }
        String scope = "";
        String tail = "";
        if (containsAny(n, "предки", "предков")) {
            scope = "ancestors";
            tail = tailAfterAny(n, "предки", "предков");
        } else if (containsAny(n, "потомки", "потомков")) {
            scope = "descendants";
            tail = tailAfterAny(n, "потомки", "потомков");
        } else if (containsAny(n, "родственники", "родственников", "родня")) {
            scope = "relatives";
            tail = tailAfterAny(n, "родственники", "родственников", "родня");
        }
        if (scope.isEmpty()) return;
        Person anchor = findPerson(state, cleanFreeText(tail));
        if (anchor == null) return;
        Set<String> ids;
        if ("ancestors".equals(scope)) ids = new LinkedHashSet<>(state.ancestorsOf(anchor.id));
        else if ("descendants".equals(scope)) ids = new LinkedHashSet<>(state.descendantsOf(anchor.id));
        else ids = connectedRelatives(state, anchor.id);
        ids.remove(anchor.id);
        query.scope = scope;
        query.anchorId = anchor.id;
        query.anchorName = displayName(anchor);
        query.scopedIds = ids;
        query.parts.add(scopeLabel(scope) + ": " + query.anchorName);
    }

    private static Set<String> connectedRelatives(TreeState state, String anchorId) {
        if (state == null || anchorId == null || anchorId.isEmpty()) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(anchorId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!result.add(id)) continue;
            for (Relation relation : state.links) {
                if (id.equals(relation.from) && state.people.containsKey(relation.to)) queue.add(relation.to);
                if (id.equals(relation.to) && state.people.containsKey(relation.from)) queue.add(relation.from);
            }
        }
        return result;
    }

    private static Set<String> maternalLine(TreeState state, String selectedId) {
        if (state == null || selectedId == null || selectedId.isEmpty()) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        for (Relation relation : state.links) {
            if (!"parent".equals(relation.type) || !selectedId.equals(relation.to)) continue;
            Person parent = state.people.get(relation.from);
            if (parent == null) continue;
            if (PersonGender.FEMALE.equals(parent.gender) || result.isEmpty()) {
                result.add(parent.id);
                result.addAll(state.ancestorsOf(parent.id));
            }
        }
        result.remove(selectedId);
        return result;
    }

    private static boolean matchesBirthday(Query query, Person person, Calendar now) {
        int day = intValue(person.bornDay);
        int month = intValue(person.bornMonth);
        if (day <= 0 || month <= 0 || month > 12) return false;
        if (query.birthdayDay > 0 && day != query.birthdayDay) return false;
        if (query.birthdayMonth > 0) return month == query.birthdayMonth;
        if (query.upcomingBirthdayDays <= 0) return true;
        if (hasDeathDate(person)) return false;
        Calendar today = now == null ? Calendar.getInstance() : now;
        Calendar next = Calendar.getInstance();
        next.clear();
        next.set(today.get(Calendar.YEAR), month - 1, Math.min(day, maxDay(today.get(Calendar.YEAR), month)));
        Calendar start = Calendar.getInstance();
        start.clear();
        start.set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        if (next.before(start)) {
            next.set(today.get(Calendar.YEAR) + 1, month - 1, Math.min(day, maxDay(today.get(Calendar.YEAR) + 1, month)));
        }
        long days = (next.getTimeInMillis() - start.getTimeInMillis()) / 86_400_000L;
        return days >= 0 && days <= query.upcomingBirthdayDays;
    }

    private static boolean isOlderThan(Person person, int age, Calendar now) {
        int born = year(person.bornYear);
        if (born <= 0) return false;
        int end = year(person.diedYear);
        if (end <= 0) end = now == null ? Calendar.getInstance().get(Calendar.YEAR) : now.get(Calendar.YEAR);
        return end - born > age;
    }

    private static boolean matchesFreeText(Person person, String freeText) {
        String normalized = normalize(value(person.name) + " " + value(person.place) + " " + value(person.notes)
            + " " + value(person.bornYear) + " " + value(person.diedYear));
        if (containsNormalized(normalized, freeText)) return true;
        String surnameNeedle = TreeState.surnameOf(freeText);
        return surnameNeedle.length() > 2 && TreeState.surnameOf(person.name).contains(surnameNeedle);
    }

    private static boolean hasBirthDate(Person person) {
        return present(person.born) || present(person.bornDay) || present(person.bornMonth) || present(person.bornYear);
    }

    private static boolean hasDeathDate(Person person) {
        return present(person.died) || present(person.diedDay) || present(person.diedMonth) || present(person.diedYear);
    }

    private static String cleanFreeText(String value) {
        return removeKnownWords(filterStopWords(normalize(value), true));
    }

    private static String removeKnownWords(String value) {
        return filterStopWords(normalize(value), false);
    }

    private static String filterStopWords(String value, boolean includeCommon) {
        StringBuilder builder = new StringBuilder();
        for (String token : normalize(value).split("\\s+")) {
            if (token.isEmpty() || token.matches("\\d{1,4}")) continue;
            if (isKnownStopWord(token) || (includeCommon && isCommonStopWord(token))) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(token);
        }
        return builder.toString().trim();
    }

    private static boolean isCommonStopWord(String token) {
        switch (token) {
            case "покажи":
            case "найди":
            case "показать":
            case "все":
            case "всех":
            case "люди":
            case "человек":
            case "людей":
            case "у":
            case "кого":
            case "есть":
            case "нет":
            case "с":
            case "со":
            case "родившиеся":
            case "родившихся":
            case "родился":
            case "родилась":
            case "рождения":
            case "дата":
            case "даты":
                return true;
            default:
                return false;
        }
    }

    private static boolean isKnownStopWord(String token) {
        switch (token) {
            case "без":
            case "фото":
            case "фотография":
            case "фотографии":
            case "фотографий":
            case "живые":
            case "живущие":
            case "умершие":
            case "умерший":
            case "умершая":
            case "предки":
            case "предков":
            case "потомки":
            case "потомков":
            case "родственники":
            case "родственников":
            case "родня":
            case "день":
            case "дни":
            case "рождения":
            case "скоро":
            case "ближайшие":
            case "ближайший":
            case "старше":
            case "лет":
            case "года":
            case "год":
            case "до":
            case "после":
            case "в":
                return true;
            default:
                return false;
        }
    }

    private static boolean looksLikePlainSearch(String normalized) {
        return !containsAny(normalized,
            "без фото", "без фотографии", "без даты", "нет даты", "живые", "умерш",
            "родивш", "рожд", "предки", "потомки", "родствен", "день рождения", "дни рождения", "старше");
    }

    private static String tailAfterAny(String source, String... markers) {
        for (String marker : markers) {
            String tail = tailAfter(source, marker);
            if (!tail.isEmpty()) return tail;
        }
        return "";
    }

    private static String tailAfter(String source, String marker) {
        int index = source.indexOf(marker);
        if (index < 0) return "";
        return source.substring(index + marker.length()).trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static boolean containsNormalized(String haystack, String needle) {
        String normalizedHaystack = normalize(haystack);
        String normalizedNeedle = normalize(needle);
        if (normalizedHaystack.contains(normalizedNeedle)) return true;
        String compactHaystack = normalizePersonKey(normalizedHaystack);
        String compactNeedle = normalizePersonKey(normalizedNeedle);
        if (personMatchScore(compactHaystack, compactNeedle) > 0) return true;
        return fuzzyTokenScore(normalizedHaystack, normalizedNeedle) > 0;
    }

    private static int fuzzyTokenScore(String haystack, String needle) {
        if (haystack.isEmpty() || needle.isEmpty()) return 0;
        if (haystack.equals(needle)) return 1200;
        if (haystack.contains(needle)) return 900 + needle.length();
        String[] candidates = haystack.split("\\s+");
        String[] requested = needle.split("\\s+");
        int total = 0;
        for (String wanted : requested) {
            if (wanted.isEmpty()) continue;
            int best = 0;
            for (String candidate : candidates) {
                if (candidate.isEmpty()) continue;
                if (candidate.equals(wanted)) {
                    best = Math.max(best, 180 + wanted.length());
                    continue;
                }
                if (candidate.startsWith(wanted) || wanted.startsWith(candidate)) {
                    int common = Math.min(candidate.length(), wanted.length());
                    if (common >= 2) best = Math.max(best, 130 + common);
                    continue;
                }
                int longest = Math.max(candidate.length(), wanted.length());
                int tolerance = longest >= 10 ? 3 : longest >= 6 ? 2 : longest >= 4 ? 1 : 0;
                if (tolerance == 0) continue;
                int distance = editDistance(candidate, wanted, tolerance);
                if (distance <= tolerance) {
                    best = Math.max(best, 95 + Math.min(candidate.length(), wanted.length()) - distance * 12);
                }
            }
            if (best == 0) return 0;
            total += best;
        }
        return total;
    }

    private static int editDistance(String first, String second, int limit) {
        if (Math.abs(first.length() - second.length()) > limit) return limit + 1;
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) previous[j] = j;
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                    Math.min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > limit) return limit + 1;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[second.length()];
    }

    private static int personMatchScore(String name, String needle) {
        if (name.isEmpty() || needle.isEmpty()) return 0;
        if (name.equals(needle)) return 1000;
        if (name.contains(needle)) return 700 + needle.length();
        Set<String> nameTokens = new HashSet<>();
        Collections.addAll(nameTokens, name.split(" "));
        int score = 0;
        for (String token : needle.split(" ")) {
            if (token.isEmpty()) continue;
            boolean found = false;
            for (String nameToken : nameTokens) {
                if (nameToken.startsWith(token) || token.startsWith(nameToken)) {
                    found = true;
                    score += Math.min(token.length(), nameToken.length());
                    break;
                }
            }
            if (!found) return 0;
        }
        return score;
    }

    private static String normalizePersonKey(String value) {
        String[] tokens = normalize(value).replaceAll("[^\\p{L}\\d ]", " ").split("\\s+");
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            String stem = stem(token);
            if (!stem.isEmpty()) result.add(stem);
        }
        return join(result, " ");
    }

    private static String stem(String token) {
        String value = token == null ? "" : token.trim();
        if (value.length() <= 3) return value;
        String[] endings = {"ого", "его", "ому", "ему", "ами", "ями", "ой", "ей", "ую", "юю", "ая", "яя", "ым", "им", "ых", "их", "а", "я", "ы", "и", "е", "у", "ю"};
        for (String ending : endings) {
            if (value.length() - ending.length() >= 3 && value.endsWith(ending)) {
                return value.substring(0, value.length() - ending.length());
            }
        }
        return value;
    }

    private static int monthNumber(String value) {
        String n = normalize(value);
        String[] months = {"январ", "феврал", "март", "апрел", "ма", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"};
        for (int i = 0; i < months.length; i++) if (n.contains(months[i])) return i + 1;
        return 0;
    }

    private static int birthdayDayNumber(String value, int month) {
        if (month <= 0) return 0;
        Matcher matcher = Pattern.compile("\\b(\\d{1,2})\\b").matcher(normalize(value));
        while (matcher.find()) {
            int day = intValue(matcher.group(1));
            if (day >= 1 && day <= 31) return day;
        }
        return 0;
    }

    private static boolean looksLikeMonthOnly(String value, int month) {
        if (month <= 0) return false;
        int meaningful = 0;
        for (String token : normalize(value).split("\\s+")) {
            if (token.isEmpty() || "в".equals(token) || "на".equals(token) || "за".equals(token)) continue;
            meaningful++;
        }
        return meaningful == 1;
    }

    private static String monthName(int month) {
        String[] months = {"январь", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь"};
        return month >= 1 && month <= 12 ? months[month - 1] : "";
    }

    private static int maxDay(int year, int month) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static String scopeLabel(String scope) {
        if ("ancestors".equals(scope)) return "предки";
        if ("descendants".equals(scope)) return "потомки";
        return "родственники";
    }

    private static String peopleWord(int count) {
        int mod10 = Math.abs(count) % 10;
        int mod100 = Math.abs(count) % 100;
        if (mod10 == 1 && mod100 != 11) return "человека";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "человека";
        return "человек";
    }

    private static String displayName(Person person) {
        return person == null || value(person.name).isEmpty() ? "Без имени" : person.name.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('ё', 'е')
            .replace('–', '-')
            .replace('—', '-')
            .replaceAll("[,!?;:()\\[\\]\"']", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static int year(String value) {
        return intValue(value == null ? "" : value.replaceAll("[^0-9]", ""));
    }

    private static int intValue(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
