package ru.drshapaya.androidft2;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class PersonGender {
    static final String MALE = "male";
    static final String FEMALE = "female";
    static final String UNKNOWN = "unknown";

    private static final Set<String> MALE_NAMES = new HashSet<>(Arrays.asList(
        "александр", "алексей", "анатолий", "андрей", "антон", "артем", "артём",
        "борис", "вадим", "валерий", "василий", "виктор", "виталий", "владимир",
        "вячеслав", "геннадий", "георгий", "григорий", "даниил", "денис", "дмитрий",
        "евгений", "иван", "игорь", "илья", "кирилл", "константин", "лев", "максим",
        "михаил", "никита", "николай", "олег", "павел", "петр", "пётр", "радомир",
        "роман", "сергей", "станислав", "федор", "фёдор", "юрий", "ярослав"
    ));
    private static final Set<String> FEMALE_NAMES = new HashSet<>(Arrays.asList(
        "алена", "алёна", "александра", "анастасия", "анна", "антонида", "валентина",
        "валерия", "варвара", "вера", "вероника", "виолетта", "галина", "дарья",
        "диана", "дина", "евгения", "екатерина", "елена", "елизавета", "зинаида",
        "инна", "ирина", "ксения", "лариса", "любовь", "людмила", "маргарита",
        "марина", "мария", "надежда", "наталья", "нина", "олеся", "ольга", "полина",
        "светлана", "софья", "таисия", "тамара", "татьяна", "юлия"
    ));

    private PersonGender() {
    }

    static String normalize(String value) {
        if (MALE.equals(value)) return MALE;
        if (FEMALE.equals(value)) return FEMALE;
        return UNKNOWN;
    }

    static String resolve(Person person) {
        if (person == null) return UNKNOWN;
        String stored = normalize(person.gender);
        if (MALE.equals(stored) || FEMALE.equals(stored) || person.genderManual) return stored;
        return infer(person.name);
    }

    static String infer(String name) {
        if (name == null) return UNKNOWN;
        String text = name.toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replace("(", " ")
            .replace(")", " ");
        String[] tokens = text.split("\\s+");
        for (String token : tokens) {
            if (token.endsWith("вич") || token.endsWith("ьич")) return MALE;
        }
        for (String token : tokens) {
            if (token.endsWith("вна") || token.endsWith("ична")) return FEMALE;
        }
        for (String token : tokens) {
            if (MALE_NAMES.contains(token)) return MALE;
            if (FEMALE_NAMES.contains(token)) return FEMALE;
        }
        boolean maleKinship = text.matches(".*(отец|папа|дед|дяд|муж|сын|брат).*");
        boolean femaleKinship = text.matches(".*(мать|мама|баб|тет|жена|дочь|сестр).*");
        if (maleKinship != femaleKinship) return maleKinship ? MALE : FEMALE;
        for (String token : tokens) {
            if (token.matches(".*(ова|ева|ина|ына|ская|цкая|ая)$")) return FEMALE;
        }
        return UNKNOWN;
    }

    static String shortLabel(Person person) {
        String gender = resolve(person);
        if (MALE.equals(gender)) return "М";
        if (FEMALE.equals(gender)) return "Ж";
        return "—";
    }
}
