package ru.drshapaya.androidft2;

/** Portable display preferences; independent of a person's actual life status. */
final class LifeStatusAppearance {
    static final String[][] LIVING = {
        {"heart", "Сердце"}, {"leaves", "Листья"}, {"leaf", "Лист"}, {"leaf-outline", "Контур листа"},
        {"sprout", "Росток"}, {"tree", "Дерево"}, {"sun", "Солнце"}, {"sunrise", "Восход"},
        {"pulse", "Пульс"}, {"lotus", "Лотос"}, {"infinity", "Бесконечность"}
    };
    static final String[][] DECEASED = {
        {"candle", "Свеча"}, {"ribbon", "Лента памяти"}, {"moon", "Луна"}, {"flower", "Цветок памяти"},
        {"hourglass", "Песочные часы"}, {"bare-tree", "Дерево без листьев"}, {"cross", "Крест"},
        {"headstone", "Памятник"}, {"headstone-cross", "Памятник с крестом"}, {"urn", "Урна"}
    };
    static final String[][] UNKNOWN = {{"question", "Вопрос"}, {"dash", "Тире"}, {"ellipsis", "Многоточие"}};

    String livingIcon = "heart", deceasedIcon = "candle", unknownIcon = "question";
    String livingColor = "#26836C", deceasedColor = "#4D5E76", unknownColor = "#808A97";
    String style = "solid";

    static String[][] icons(String status) {
        return PersonLifeStatus.LIVING.equals(status) ? LIVING
            : PersonLifeStatus.DECEASED.equals(status) ? DECEASED : UNKNOWN;
    }

    String icon(String status) {
        return PersonLifeStatus.LIVING.equals(status) ? livingIcon
            : PersonLifeStatus.DECEASED.equals(status) ? deceasedIcon : unknownIcon;
    }

    void setIcon(String status, String value) {
        if (!contains(icons(status), value)) return;
        if (PersonLifeStatus.LIVING.equals(status)) livingIcon = value;
        else if (PersonLifeStatus.DECEASED.equals(status)) deceasedIcon = value;
        else unknownIcon = value;
    }

    String color(String status) {
        return PersonLifeStatus.LIVING.equals(status) ? livingColor
            : PersonLifeStatus.DECEASED.equals(status) ? deceasedColor : unknownColor;
    }

    void setColor(String status, String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) return;
        value = value.toUpperCase(java.util.Locale.ROOT);
        if (PersonLifeStatus.LIVING.equals(status)) livingColor = value;
        else if (PersonLifeStatus.DECEASED.equals(status)) deceasedColor = value;
        else unknownColor = value;
    }

    String encode() {
        return livingIcon + "|" + deceasedIcon + "|" + unknownIcon + "|" + livingColor + "|"
            + deceasedColor + "|" + unknownColor + "|" + style;
    }

    LifeStatusAppearance copy() { return decode(encode()); }

    static LifeStatusAppearance decode(String value) {
        LifeStatusAppearance result = new LifeStatusAppearance();
        if (value == null) return result;
        String[] parts = value.split("\\|", -1);
        if (parts.length != 7) return result;
        result.setIcon(PersonLifeStatus.LIVING, parts[0]);
        result.setIcon(PersonLifeStatus.DECEASED, parts[1]);
        result.setIcon(PersonLifeStatus.UNKNOWN, parts[2]);
        result.setColor(PersonLifeStatus.LIVING, parts[3]);
        result.setColor(PersonLifeStatus.DECEASED, parts[4]);
        result.setColor(PersonLifeStatus.UNKNOWN, parts[5]);
        if ("solid".equals(parts[6]) || "soft".equals(parts[6]) || "plain".equals(parts[6])) result.style = parts[6];
        return result;
    }

    private static boolean contains(String[][] icons, String value) {
        for (String[] icon : icons) if (icon[0].equals(value)) return true;
        return false;
    }
}
