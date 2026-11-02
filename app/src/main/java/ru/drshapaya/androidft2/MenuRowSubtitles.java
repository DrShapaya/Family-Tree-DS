package ru.drshapaya.androidft2;

import java.util.HashMap;
import java.util.Map;

/**
 * One-line grey subtitles for menu and settings rows. The long help text stays
 * available on long press; the subtitle only says what the row is for.
 */
final class MenuRowSubtitles {
    private static final int MAX_FALLBACK_LENGTH = 44;
    private static final Map<String, String> SUBTITLES = new HashMap<>();

    static {
        // Card menu
        SUBTITLES.put("Цвет по ФИО", "Свой цвет для выбранной карточки");
        SUBTITLES.put("Цвет по фамилии", "Один цвет для всей фамилии");
        SUBTITLES.put("Удалить карточку", "Удалить выбранного человека");
        // Files
        SUBTITLES.put("Сохранить на телефон", "Файл дерева в папку на телефоне");
        SUBTITLES.put("Сохранённые версии", "10 последних копий, можно вернуть");
        SUBTITLES.put("Импорт", ".ftree, GEDCOM, JSON, ZIP с фото");
        SUBTITLES.put("Отправить в другом формате", "GEDCOM, PDF, HTML и другие");
        // Connections
        SUBTITLES.put("Партнёрская связь", "Выберите две карточки");
        SUBTITLES.put("Кто кому приходится", "Родство между двумя людьми");
        SUBTITLES.put("Убрать связь", "Выберите две связанные карточки");
        // Tree
        SUBTITLES.put("Рамка", "Выделить карточки прямоугольником");
        SUBTITLES.put("Лассо", "Выделить карточки обводкой");
        SUBTITLES.put("Отображение ветки", "Предки, потомки или близкие");
        SUBTITLES.put("Быстрый старт", "Новое дерево за несколько шагов");
        SUBTITLES.put("Всё дерево", "Все карточки и связи");
        SUBTITLES.put("Предки", "Выбранный человек и его предки");
        SUBTITLES.put("Потомки", "Выбранный человек и его потомки");
        SUBTITLES.put("Близкие", "Ближайшие связи выбранного");
        // Settings
        SUBTITLES.put("Обучение", "Пошаговые подсказки по кнопкам");
        SUBTITLES.put("Степень родства", "Как называть дальнюю родню");
        SUBTITLES.put("История действий", "Панель последних действий");
        SUBTITLES.put("Защита правок", "Защита от случайных изменений");
        SUBTITLES.put("Только просмотр", "Глава дерева отключил правки");
        SUBTITLES.put("Значок качества карточки", "Ошибки и полнота данных");
        SUBTITLES.put("Значок статуса жизни", "Жив, умер или неизвестно");
        SUBTITLES.put("Иконки и цвета статуса жизни", "Символы и цвета значка");
        SUBTITLES.put("Линии поколений", "Горизонтальные линии уровней");
        SUBTITLES.put("Ровные связи", "Прямые углы вместо кривых");
        SUBTITLES.put("От середины пары", "Линия к детям от центра пары");
        SUBTITLES.put("Скрыть детали карточек", "Меньше текста на карточках");
        SUBTITLES.put("Компактные карточки", "Размер 3×5 вместо 4×7");
        SUBTITLES.put("Фокус на дереве", "Больше места для дерева");
        SUBTITLES.put("Удалить всё дерево", "Можно отменить через историю");
        SUBTITLES.put("Язык", "Русский, английский или как в телефоне");
        SUBTITLES.put("Language", "Русский, английский или как в телефоне");
    }

    private MenuRowSubtitles() {}

    static String forRow(String label, String help) {
        String key = label == null ? "" : label.trim();
        int colon = key.indexOf(':');
        String known = SUBTITLES.get(key);
        if (known == null && colon > 0) known = SUBTITLES.get(key.substring(0, colon).trim());
        if (known != null) return known;
        // Short help texts (developer menu, dynamic states) already read as subtitles.
        String fallback = help == null ? "" : help.trim();
        if (fallback.endsWith(".")) fallback = fallback.substring(0, fallback.length() - 1);
        return fallback.length() <= MAX_FALLBACK_LENGTH ? fallback : "";
    }
}
