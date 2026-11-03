package ru.drshapaya.androidft2;

import java.util.HashMap;
import java.util.Map;

/**
 * Genitive case for the kinship terms the calculator produces, so a relationship through a
 * spouse reads "жена дяди" rather than "жена человека, который приходится дядя".
 */
final class KinshipGenitive {
    private static final Map<String, String> NOUNS = new HashMap<>();

    static {
        String[][] pairs = {
            {"брат", "брата"}, {"сестра", "сестры"}, {"дядя", "дяди"}, {"тётя", "тёти"},
            {"племянник", "племянника"}, {"племянница", "племянницы"},
            {"дедушка", "дедушки"}, {"бабушка", "бабушки"}, {"внук", "внука"}, {"внучка", "внучки"},
            {"сын", "сына"}, {"дочь", "дочери"}, {"отец", "отца"}, {"мать", "матери"},
            {"муж", "мужа"}, {"жена", "жены"}, {"партнёр", "партнёра"}, {"партнёрша", "партнёрши"},
            {"родитель", "родителя"}, {"ребёнок", "ребёнка"}, {"предок", "предка"}, {"потомок", "потомка"},
            {"отчим", "отчима"}, {"мачеха", "мачехи"}, {"зять", "зятя"}, {"невестка", "невестки"},
            {"пасынок", "пасынка"}, {"падчерица", "падчерицы"}, {"свёкор", "свёкра"}, {"свекровь", "свекрови"},
            {"тесть", "тестя"}, {"тёща", "тёщи"}, {"деверь", "деверя"}, {"золовка", "золовки"},
            {"шурин", "шурина"}, {"свояченица", "свояченицы"}, {"супруг", "супруга"}, {"супруга", "супруги"},
            {"жених", "жениха"}, {"невеста", "невесты"},
        };
        for (String[] pair : pairs) NOUNS.put(pair[0], pair[1]);
    }

    private KinshipGenitive() {}

    /** Genitive of a kinship term, or {@code null} when a word is not one the calculator uses. */
    static String of(String term) {
        if (term == null || term.trim().isEmpty()) return null;
        String[] words = term.trim().split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String declined = "или".equals(word) ? word : declineWord(word);
            if (declined == null) return null;
            if (i > 0) out.append(' ');
            out.append(declined);
        }
        return out.toString();
    }

    private static String declineWord(String word) {
        // "прапрадедушка" -> "прапрадедушки": strip the repeated "пра" and decline the stem.
        String prefix = "";
        String stem = word;
        while (stem.startsWith("пра") && !NOUNS.containsKey(stem)) {
            prefix += "пра";
            stem = stem.substring(3);
        }
        String noun = NOUNS.get(stem);
        if (noun != null) return prefix + noun;
        if (!prefix.isEmpty()) return null;
        if (word.endsWith("ый") || word.endsWith("ой")) return word.substring(0, word.length() - 2) + "ого";
        if (word.endsWith("ий")) return word.substring(0, word.length() - 2) + "его";
        if (word.endsWith("ая")) return word.substring(0, word.length() - 2) + "ой";
        if (word.endsWith("яя")) return word.substring(0, word.length() - 2) + "ей";
        return null;
    }
}
