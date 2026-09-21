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
}
