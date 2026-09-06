package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only quality checks shared by the canvas, card menu and tree report. */
final class TreeQualityAnalyzer {
    static final int RECOMMENDATION = 1;
    static final int WARNING = 2;
    static final int ERROR = 3;

    static final String CATEGORY_PARENT_AGE = "parent_age";
    static final String CATEGORY_DATES = "dates";
    static final String CATEGORY_RELATIONS = "relations";
    static final String CATEGORY_MISSING = "missing";
    static final String CATEGORY_DUPLICATES = "duplicates";
    static final String CATEGORY_STRUCTURE = "structure";
    static final String CATEGORY_MEMORY = "memory";

    static final class Issue {
        final String personId;
        final int severity;
        final String category;
        final String title;
        final String detail;

        Issue(String personId, int severity, String category, String title, String detail) {
            this.personId = personId == null ? "" : personId;
            this.severity = severity;
            this.category = category == null ? "" : category;
            this.title = title == null ? "" : title;
            this.detail = detail == null ? "" : detail;
        }
    }

    static final class PersonReport {
        final String personId;
        int completeness;
        final List<Issue> issues = new ArrayList<>();

        PersonReport(String personId) {
            this.personId = personId;
        }

        int errors() { return countSeverity(ERROR); }
        int warnings() { return countSeverity(WARNING); }
        int recommendations() { return countSeverity(RECOMMENDATION); }

        int countCategory(String category) {
            int count = 0;
            for (Issue issue : issues) if (category.equals(issue.category)) count++;
            return count;
        }

        int topSeverity() {
            int result = 0;
            for (Issue issue : issues) result = Math.max(result, issue.severity);
            return result;
        }

        private int countSeverity(int severity) {
            int count = 0;
            for (Issue issue : issues) if (issue.severity == severity) count++;
            return count;
        }
    }

    static final class TreeReport {
        int score;
        final Map<String, PersonReport> people = new LinkedHashMap<>();
        final List<Issue> issues = new ArrayList<>();

        PersonReport person(String id) {
            PersonReport report = people.get(id);
            return report == null ? new PersonReport(id) : report;
        }

        int errors() { return countSeverity(ERROR); }
        int warnings() { return countSeverity(WARNING); }
        int recommendations() { return countSeverity(RECOMMENDATION); }

        private int countSeverity(int severity) {
            int count = 0;
            for (Issue issue : issues) if (issue.severity == severity) count++;
            return count;
        }
    }

    private TreeQualityAnalyzer() {}

    static TreeReport analyze(TreeState state) {
        TreeReport tree = new TreeReport();
        if (state == null || state.people.isEmpty()) {
            tree.score = 0;
            return tree;
        }

        Map<String, Map<String, Issue>> unique = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            PersonReport report = new PersonReport(person.id);
            report.completeness = completeness(state, person);
            tree.people.put(person.id, report);
            unique.put(person.id, new LinkedHashMap<>());
            checkPerson(state, person, unique.get(person.id));
        }
        checkRelations(state, unique);
        checkDuplicatePeople(state, unique);
        checkDisconnectedComponents(state, unique);

        int completenessTotal = 0;
        for (PersonReport report : tree.people.values()) {
            Map<String, Issue> issues = unique.get(report.personId);
            if (issues != null) report.issues.addAll(issues.values());
            tree.issues.addAll(report.issues);
            completenessTotal += report.completeness;
        }
        int average = completenessTotal / Math.max(1, tree.people.size());
        int penalty = Math.round((tree.errors() * 12f
            + tree.warnings() * 4.5f
            + tree.recommendations()) / Math.max(1, tree.people.size()));
        tree.score = clamp(average - penalty, 0, 100);
        return tree;
    }

    private static int completeness(TreeState state, Person person) {
        int score = 0;
        if (!isPlaceholderName(person.name)) score += 20;
        if (positiveYear(person.bornYear) > 0) score += 15;
        if (positive(person.bornDay) && positive(person.bornMonth)) score += 5;
        if (!PersonGender.UNKNOWN.equals(PersonGender.resolve(person))) score += 10;
        if (present(person.place)) score += 10;
        if (present(person.notes)) score += 10;
        if (present(person.photoMediaId) || present(person.photo)) score += 15;
        if (!person.memories.isEmpty()) score += 10;
        if (hasRelation(state, person.id)) score += 5;
        return clamp(score, 0, 100);
    }

    private static void checkPerson(TreeState state, Person person, Map<String, Issue> issues) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int born = positiveYear(person.bornYear);
        int died = positiveYear(person.diedYear);

        if (isPlaceholderName(person.name)) {
            add(issues, person.id, RECOMMENDATION, CATEGORY_MISSING,
                "Не указано полное имя", "Добавьте фамилию, имя и отчество.");
        }
        if (born == 0) {
            add(issues, person.id, RECOMMENDATION, CATEGORY_MISSING,
                "Нет года рождения", "Год рождения улучшит проверку дат и родства.");
        }
        if (!present(person.place)) {
            add(issues, person.id, RECOMMENDATION, CATEGORY_MISSING,
                "Не указано место", "Добавьте место рождения или проживания.");
        }
        if (!present(person.photoMediaId) && !present(person.photo)) {
            add(issues, person.id, RECOMMENDATION, CATEGORY_MISSING,
                "Нет фотографии", "Добавьте фотографию для более полной карточки.");
        }
        if (PersonGender.UNKNOWN.equals(PersonGender.resolve(person))) {
            add(issues, person.id, RECOMMENDATION, CATEGORY_MISSING,
                "Не указан пол", "Пол нужен для точных названий родства.");
        }
        if (!hasRelation(state, person.id) && state.people.size() > 1) {
            add(issues, person.id, WARNING, CATEGORY_RELATIONS,
                "Карточка не связана с деревом", "Добавьте хотя бы одну семейную связь.");
        }
        if (born > currentYear) {
            add(issues, person.id, ERROR, CATEGORY_DATES,
                "Дата рождения в будущем", "Проверьте год рождения: " + born + ".");
        }
        if (died > currentYear) {
            add(issues, person.id, ERROR, CATEGORY_DATES,
                "Дата смерти в будущем", "Проверьте год смерти: " + died + ".");
        }
        if (born > 0 && died > 0 && died < born) {
            add(issues, person.id, ERROR, CATEGORY_DATES,
                "Несовместимые даты жизни", "Год смерти раньше года рождения.");
        }
        if (born > 0 && died > 0 && died - born > 125) {
            add(issues, person.id, WARNING, CATEGORY_DATES,
                "Необычно большой возраст", "Продолжительность жизни превышает 125 лет.");
        }
        checkDateParts(person, issues, true);
        checkDateParts(person, issues, false);
        checkMemories(person, issues);
    }

    private static void checkRelations(TreeState state, Map<String, Map<String, Issue>> unique) {
        Map<String, Integer> parentCounts = new LinkedHashMap<>();
        Map<String, Integer> relationCounts = new LinkedHashMap<>();
        Map<String, Set<String>> pairRoles = new LinkedHashMap<>();
        for (Relation link : state.links) {
            Person from = state.people.get(link.from);
            Person to = state.people.get(link.to);
            if (from == null || to == null) continue;
            if (link.from.equals(link.to)) {
                add(unique.get(link.from), link.from, ERROR, CATEGORY_RELATIONS,
                    "Связь карточки с самой собой", "Удалите подозрительную связь.");
                continue;
            }
            String normalizedPair = normalizedPair(link.from, link.to);
            String relationKey = link.type + "|" + ("parent".equals(link.type)
                ? link.from + ">" + link.to
                : normalizedPair);
            int relationCount = relationCounts.getOrDefault(relationKey, 0) + 1;
            relationCounts.put(relationKey, relationCount);
            if (relationCount == 2) {
                addPair(unique, link.from, link.to, WARNING, CATEGORY_RELATIONS,
                    "Повторяется одинаковая связь", "Удалите дубликат связи между карточками.");
            }
            pairRoles.computeIfAbsent(normalizedPair, ignored -> new LinkedHashSet<>()).add(link.type);
            if (!"parent".equals(link.type)) continue;
            parentCounts.put(link.to, parentCounts.getOrDefault(link.to, 0) + 1);
            checkParentAge(from, to, unique.get(to.id));

            if (state.descendantsOf(to.id).contains(from.id)) {
                add(unique.get(to.id), to.id, ERROR, CATEGORY_RELATIONS,
                    "Циклическая родительская связь", "Человек не может быть собственным предком.");
            }
            for (Relation other : state.links) {
                if (!"partner".equals(other.type) && !"sibling".equals(other.type)) continue;
                boolean samePair = (link.from.equals(other.from) && link.to.equals(other.to))
                    || (link.from.equals(other.to) && link.to.equals(other.from));
                if (samePair) {
                    add(unique.get(to.id), to.id, ERROR, CATEGORY_RELATIONS,
                        "Несовместимые типы связи", "Между двумя карточками одновременно указаны разные роли.");
                }
            }
        }
        for (Map.Entry<String, Set<String>> entry : pairRoles.entrySet()) {
            if (entry.getValue().size() <= 1) continue;
            String[] ids = entry.getKey().split("\\|", 2);
            if (ids.length != 2) continue;
            addPair(unique, ids[0], ids[1], ERROR, CATEGORY_RELATIONS,
                "Несовместимые типы связи", "Между двумя карточками одновременно указаны разные роли.");
        }
        for (Map.Entry<String, Integer> item : parentCounts.entrySet()) {
            if (item.getValue() <= 2) continue;
            add(unique.get(item.getKey()), item.getKey(), WARNING, CATEGORY_RELATIONS,
                "Указано больше двух родителей", "Проверьте родительские связи: " + item.getValue() + ".");
        }
    }

    private static void checkDuplicatePeople(
        TreeState state,
        Map<String, Map<String, Issue>> unique
    ) {
        Map<String, List<Person>> byNameAndBirth = new LinkedHashMap<>();
        Map<String, List<Person>> byNameAndPlace = new LinkedHashMap<>();
        for (Person person : state.people.values()) {
            String name = normalizedName(person.name);
            if (name.isEmpty() || isPlaceholderName(person.name)) continue;
            int born = positiveYear(person.bornYear);
            if (born > 0) {
                byNameAndBirth.computeIfAbsent(name + "|born:" + born, ignored -> new ArrayList<>())
                    .add(person);
            }
            String place = normalize(person.place);
            if (born == 0 && !place.isEmpty()) {
                byNameAndPlace.computeIfAbsent(name + "|place:" + place, ignored -> new ArrayList<>())
                    .add(person);
            }
        }
        reportDuplicates(unique, byNameAndBirth, "Совпадают ФИО и год рождения");
        reportDuplicates(unique, byNameAndPlace, "Совпадают ФИО и место");
    }

    private static void reportDuplicates(
        Map<String, Map<String, Issue>> unique,
        Map<String, List<Person>> groups,
        String reason
    ) {
        for (List<Person> group : groups.values()) {
            if (group.size() < 2) continue;
            String names = duplicateNames(group);
            for (Person person : group) {
                add(unique.get(person.id), person.id, WARNING, CATEGORY_DUPLICATES,
                    "Возможный дубль карточки", reason + ": " + names + ".");
            }
        }
    }

    private static void checkDisconnectedComponents(
        TreeState state,
        Map<String, Map<String, Issue>> unique
    ) {
        if (state.people.size() < 2) return;
        String root = state.people.containsKey(state.rootId)
            ? state.rootId
            : state.people.keySet().iterator().next();
        Set<String> rootComponent = relatedComponent(state, root);
        if (rootComponent.size() == state.people.size()) return;
        Set<String> reported = new LinkedHashSet<>();
        for (String id : state.people.keySet()) {
            if (rootComponent.contains(id) || !reported.add(id)) continue;
            Set<String> component = relatedComponent(state, id);
            reported.addAll(component);
            if (component.size() == 1 && !hasRelation(state, id)) continue;
            Person person = state.people.get(id);
            add(unique.get(id), id, WARNING, CATEGORY_STRUCTURE,
                "Отдельная ветка не связана с корнем",
                "Ветка начинается с " + displayName(person) + " и содержит "
                    + component.size() + " " + word(component.size(), "карточку", "карточки", "карточек") + ".");
        }
    }

    private static Set<String> relatedComponent(TreeState state, String startId) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        if (state.people.containsKey(startId)) queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.removeFirst();
            if (!result.add(id)) continue;
            for (Relation link : state.links) {
                if (id.equals(link.from) && state.people.containsKey(link.to)) queue.add(link.to);
                else if (id.equals(link.to) && state.people.containsKey(link.from)) queue.add(link.from);
            }
        }
        return result;
    }

    private static void checkParentAge(Person parent, Person child, Map<String, Issue> issues) {
        int parentBorn = positiveYear(parent.bornYear);
        int childBorn = positiveYear(child.bornYear);
        int parentDied = positiveYear(parent.diedYear);
        if (parentBorn > 0 && childBorn > 0) {
            int age = childBorn - parentBorn;
            String parentName = displayName(parent);
            if (age <= 0) {
                add(issues, child.id, ERROR, CATEGORY_PARENT_AGE,
                    "Родитель младше ребёнка", parentName + ": возраст при рождении ребёнка " + age + ".");
            } else if (age < 14 || age > 80) {
                add(issues, child.id, ERROR, CATEGORY_PARENT_AGE,
                    "Недопустимый возраст родителя", parentName + ": " + age + " лет при рождении ребёнка.");
            } else if (age < 16 || age > 65) {
                add(issues, child.id, WARNING, CATEGORY_PARENT_AGE,
                    "Необычный возраст родителя", parentName + ": " + age + " лет при рождении ребёнка.");
            }
        }
        if (parentDied > 0 && childBorn > parentDied + 1) {
            add(issues, child.id, ERROR, CATEGORY_DATES,
                "Ребёнок родился после смерти родителя",
                displayName(parent) + ": смерть " + parentDied + ", рождение ребёнка " + childBorn + ".");
        }
    }

    private static void checkDateParts(
        Person person,
        Map<String, Issue> issues,
        boolean birth
    ) {
        String label = birth ? "рождения" : "смерти";
        int day = positiveYear(birth ? person.bornDay : person.diedDay);
        int month = positiveYear(birth ? person.bornMonth : person.diedMonth);
        int year = positiveYear(birth ? person.bornYear : person.diedYear);
        if (day > 0 && month == 0) {
            add(issues, person.id, WARNING, CATEGORY_DATES,
                "День указан без месяца", "Проверьте дату " + label + ".");
        }
        if (month > 12) {
            add(issues, person.id, ERROR, CATEGORY_DATES,
                "Некорректный месяц", "Месяц даты " + label + " больше 12.");
        }
        if (day > 0 && month > 0 && day > maxDay(year <= 0 ? 2000 : year, month)) {
            add(issues, person.id, ERROR, CATEGORY_DATES,
                "Некорректный день месяца", "Проверьте дату " + label + ": " + day + "." + month + ".");
        }
    }

    private static void checkMemories(Person person, Map<String, Issue> issues) {
        for (Memory memory : person.memories) {
            if (memory == null) continue;
            boolean hasTitle = present(memory.title) && !"Воспоминание".equals(memory.title.trim());
            boolean hasText = present(memory.text);
            boolean hasAttachments = memory.attachments != null && !memory.attachments.isEmpty();
            if (!hasTitle && !hasText && !hasAttachments) {
                add(issues, person.id, RECOMMENDATION, CATEGORY_MEMORY,
                    "Пустая запись архива", "Добавьте текст, название или вложение к воспоминанию.");
            }
            if (memory.attachments == null) continue;
            for (MemoryAttachment attachment : memory.attachments) {
                if (attachment == null) continue;
                if (!present(attachment.mediaId) && !present(attachment.data)) {
                    add(issues, person.id, WARNING, CATEGORY_MEMORY,
                        "Пустое вложение", "Удалите вложение без файла или прикрепите файл заново.");
                }
            }
        }
    }

    private static void addPair(
        Map<String, Map<String, Issue>> unique,
        String firstId,
        String secondId,
        int severity,
        String category,
        String title,
        String detail
    ) {
        add(unique.get(firstId), firstId, severity, category, title, detail);
        add(unique.get(secondId), secondId, severity, category, title, detail);
    }

    private static void add(
        Map<String, Issue> issues,
        String personId,
        int severity,
        String category,
        String title,
        String detail
    ) {
        if (issues == null) return;
        String key = severity + "|" + category + "|" + title + "|" + detail;
        issues.putIfAbsent(key, new Issue(personId, severity, category, title, detail));
    }

    private static boolean hasRelation(TreeState state, String id) {
        for (Relation link : state.links) if (id.equals(link.from) || id.equals(link.to)) return true;
        return false;
    }

    private static String normalizedPair(String first, String second) {
        String a = first == null ? "" : first;
        String b = second == null ? "" : second;
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private static String duplicateNames(List<Person> people) {
        StringBuilder builder = new StringBuilder();
        for (Person person : people) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(displayName(person));
        }
        return builder.toString();
    }

    private static String normalizedName(String value) {
        String normalized = normalize(value);
        if (normalized.equals("без имени") || normalized.equals("пустая карточка")) return "";
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replaceAll("[^\\p{L}\\d ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean isPlaceholderName(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return name.isEmpty()
            || name.equals("без имени")
            || name.equals("пустая карточка")
            || name.startsWith("новый ")
            || name.equals("брат или сестра");
    }

    private static String displayName(Person person) {
        return person == null || !present(person.name) ? "Без имени" : person.name.trim();
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean positive(String value) {
        return positiveYear(value) > 0;
    }

    private static int positiveYear(String value) {
        try {
            int parsed = value == null || value.trim().isEmpty() ? 0 : Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static int maxDay(int year, int month) {
        if (month < 1 || month > 12) return 31;
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(Math.max(1, year), month - 1, 1);
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    private static String word(int count, String one, String few, String many) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) return many;
        if (mod10 == 1) return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
