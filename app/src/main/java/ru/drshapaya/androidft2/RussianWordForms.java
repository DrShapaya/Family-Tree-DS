package ru.drshapaya.androidft2;

/** Selects the Russian singular, paucal or plural form for a count. */
final class RussianWordForms {
    private RussianWordForms() {}

    static String forCount(int count, String one, String few, String many) {
        int absolute = Math.abs(count);
        int mod100 = absolute % 100;
        int mod10 = absolute % 10;
        if (mod100 >= 11 && mod100 <= 14) return many;
        if (mod10 == 1) return one;
        if (mod10 >= 2 && mod10 <= 4) return few;
        return many;
    }

    /** Formats "count word" in the interface language, e.g. "4 человека" or "1 person". */
    static String count(
        boolean english,
        int count,
        String one,
        String few,
        String many,
        String englishOne,
        String englishMany) {
        String word = english
            ? (Math.abs(count) == 1 ? englishOne : englishMany)
            : forCount(count, one, few, many);
        return count + " " + word;
    }

    static String peopleAndLinks(boolean english, int people, int links) {
        return count(english, people, "человек", "человека", "человек", "person", "people")
            + ", "
            + count(english, links, "связь", "связи", "связей", "connection", "connections");
    }
}
