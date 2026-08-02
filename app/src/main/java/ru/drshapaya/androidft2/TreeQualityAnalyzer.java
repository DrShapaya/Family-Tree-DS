package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only quality checks shared by the canvas, card menu and tree report. */
final class TreeQualityAnalyzer {
    static final int RECOMMENDATION = 1;
    static final int WARNING = 2;
    static final int ERROR = 3;

    static final String CATEGORY_PARENT_AGE = "parent_age";
    static final String CATEGORY_DATES = "dates";
    static final String CATEGORY_RELATIONS = "relations";
    static final String CATEGORY_MISSING = "missing";

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
    }

    private static void checkRelations(TreeState state, Map<String, Map<String, Issue>> unique) {
        Map<String, Integer> parentCounts = new LinkedHashMap<>();
        for (Relation link : state.links) {
            Person from = state.people.get(link.from);
            Person to = state.people.get(link.to);
            if (from == null || to == null) continue;
            if (link.from.equals(link.to)) {
                add(unique.get(link.from), link.from, ERROR, CATEGORY_RELATIONS,
                    "Связь карточки с самой собой", "Удалите подозрительную связь.");
                continue;
            }
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
        for (Map.Entry<String, Integer> item : parentCounts.entrySet()) {
            if (item.getValue() <= 2) continue;
            add(unique.get(item.getKey()), item.getKey(), WARNING, CATEGORY_RELATIONS,
                "Указано больше двух родителей", "Проверьте родительские связи: " + item.getValue() + ".");
        }
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
