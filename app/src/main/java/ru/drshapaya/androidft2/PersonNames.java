package ru.drshapaya.androidft2;

/** Full names remain compatible with old files; display preferences never overwrite them. */
final class PersonNames {
    private static final java.util.regex.Pattern LEGACY_MAIDEN = java.util.regex.Pattern.compile("^([^\\s()]+)\\s*\\(([^()]+)\\)(?:\\s+(.*))?$");
    static String clean(String text) { return text == null ? "" : text.trim().replaceAll("\\s+", " "); }

    static String[] parts(Person p) {
        if (p.namePartsSet) return new String[]{p.surname, p.givenName, p.patronymic};
        // Legacy names are stored surname-first. Keep every word, including compound names.
        String name = clean(p.name);
        java.util.regex.Matcher legacy=LEGACY_MAIDEN.matcher(name);
        if(legacy.matches() && validMaiden(legacy.group(2))) {
            String[] rest=clean(legacy.group(3)).split(" ",2);
            return new String[]{legacy.group(1),rest[0],rest.length>1 ? rest[1] : ""};
        }
        String[] words = name.split(" ", 3);
        if (words.length < 2) return new String[]{"", name, ""};
        return new String[]{words[0], words[1], words.length > 2 ? words[2] : ""};
    }

    /** Handles both old raw names and names already split by an earlier version. */
    static boolean migrateLegacy(Person p) {
        java.util.regex.Matcher legacy=LEGACY_MAIDEN.matcher(clean(p.name));
        if(!legacy.matches() || !validMaiden(legacy.group(2))) return false;
        String maiden=clean(legacy.group(2));
        String[] rest=clean(legacy.group(3)).split(" ",2);
        String given=rest[0], patronymic=rest.length>1 ? rest[1] : "";
        if(p.namePartsSet && !clean(p.givenName).startsWith("(")) { given=p.givenName; patronymic=p.patronymic; }
        set(p,legacy.group(1),given,patronymic);
        if(clean(p.maidenName).isEmpty()) { p.maidenName=maiden; p.maidenNameEnabled=true; }
        return true;
    }

    private static boolean validMaiden(String value) {
        String name=clean(value);
        return !"копия".equalsIgnoreCase(name) && !"copy".equalsIgnoreCase(name)
            && name.matches("[\\p{L}\\p{M}][\\p{L}\\p{M} .’'\\-]*");
    }

    static void set(Person p, String surname, String given, String patronymic) {
        p.surname = clean(surname); p.givenName = clean(given); p.patronymic = clean(patronymic);
        p.namePartsSet = true;
        p.name = join(p.surname, p.givenName, p.patronymic);
    }

    static String display(Person p, boolean maidenByDefault) {
        boolean maiden = "maiden".equals(p.surnameDisplay)
            || ("inherit".equals(p.surnameDisplay) && maidenByDefault);
        if (!maiden || !p.maidenNameEnabled || clean(p.maidenName).isEmpty()) return clean(p.name);
        String[] parts = parts(p);
        return join(p.maidenName, parts[1], parts[2]);
    }

    static String alternate(Person p) {
        if (clean(p.maidenName).isEmpty()) return "";
        String[] parts = parts(p);
        return join(p.maidenName, parts[1], parts[2]);
    }
    static String search(Person p) { return clean(p.name) + " " + alternate(p); }
    static String mode(String value) { return "maiden".equals(value) || "current".equals(value) ? value : "inherit"; }
    static String join(String surname, String given, String patronymic) { return clean(clean(surname) + " " + clean(given) + " " + clean(patronymic)); }
    static void copy(Person from, Person to) {
        to.surname = from.surname; to.givenName = from.givenName; to.patronymic = from.patronymic;
        to.maidenName = from.maidenName; to.namePartsSet = from.namePartsSet;
        to.maidenNameEnabled = from.maidenNameEnabled; to.surnameDisplay = from.surnameDisplay;
    }
    static boolean equal(Person a, Person b) {
        return java.util.Objects.equals(a.surname,b.surname) && java.util.Objects.equals(a.givenName,b.givenName)
            && java.util.Objects.equals(a.patronymic,b.patronymic) && java.util.Objects.equals(a.maidenName,b.maidenName)
            && a.namePartsSet == b.namePartsSet && a.maidenNameEnabled == b.maidenNameEnabled
            && java.util.Objects.equals(a.surnameDisplay,b.surnameDisplay);
    }
}
