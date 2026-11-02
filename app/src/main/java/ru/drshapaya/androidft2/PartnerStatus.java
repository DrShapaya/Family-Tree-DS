package ru.drshapaya.androidft2;

/**
 * Kind of relationship on a partner link. An empty value means plain "partners",
 * which is how every link created before this setting existed is read.
 */
final class PartnerStatus {
    static final String PARTNERS = "";
    static final String MARRIED = "married";
    static final String ENGAGED = "engaged";
    static final String DIVORCED = "divorced";
    static final String SEPARATED = "separated";

    /** Order shown in the picker. */
    static final String[] ALL = {MARRIED, ENGAGED, PARTNERS, DIVORCED, SEPARATED};

    private PartnerStatus() {}

    static String normalize(String value) {
        if (MARRIED.equals(value) || ENGAGED.equals(value)
            || DIVORCED.equals(value) || SEPARATED.equals(value)) return value;
        return PARTNERS;
    }

    static String label(String status) {
        switch (normalize(status)) {
            case MARRIED: return "В браке";
            case ENGAGED: return "Помолвлены";
            case DIVORCED: return "Бывшие супруги";
            case SEPARATED: return "Не в отношениях";
            default: return "Партнёры";
        }
    }

    /** Relationships that have ended are drawn with a dotted line. */
    static boolean ended(String status) {
        String value = normalize(status);
        return DIVORCED.equals(value) || SEPARATED.equals(value);
    }
}
