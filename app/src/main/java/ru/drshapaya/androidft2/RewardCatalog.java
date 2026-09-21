package ru.drshapaya.androidft2;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class RewardCatalog {
    static final String CARD_EDGES = "card_edges";
    static final String CARD_THEMES = "card_themes";
    static final String CANVAS_BACKGROUNDS = "canvas_backgrounds";
    static final String LINK_STYLES = "link_styles";
    static final String WIDGET_FRAMES = "widget_frames";
    static final String EXPORT_FRAMES = "export_frames";
    static final String STANDARD = "standard";
    static final String RARITY_COMMON = "common";
    static final String RARITY_RARE = "rare";
    static final String RARITY_EPIC = "epic";
    static final int PRICE_COMMON = 10;
    static final int PRICE_RARE = 15;
    static final int PRICE_EPIC = 20;

    static final class Category {
        final String id;
        final String title;
        final String standardTitle;
        final String standardDetail;

        Category(String id, String title, String standardTitle, String standardDetail) {
            this.id = id;
            this.title = title;
            this.standardTitle = standardTitle;
            this.standardDetail = standardDetail;
        }
    }

    static final class Item {
        final String id;
        final String categoryId;
        final String title;
        final String detail;
        final String rarity;
        final int price;

        Item(String id, String categoryId, String title, String detail, String rarity) {
            this.id = id;
            this.categoryId = categoryId;
            this.title = title;
            this.detail = detail;
            this.rarity = normalizeRarity(rarity);
            this.price = priceForRarity(this.rarity);
        }
    }

    private static final List<Category> CATEGORIES = Collections.unmodifiableList(Arrays.asList(
        new Category(
            CARD_THEMES,
            "Темы карточек",
            "Стандартные карточки",
            "Привычные цветные карточки Family Tree DS."),
        new Category(CARD_EDGES, "Края карточек", "Стандартные края",
            "Без декоративной обводки. Сочетаются с любой темой карточек."),
        new Category(
            CANVAS_BACKGROUNDS,
            "Фоны полотна",
            "Стандартное полотно",
            "Спокойный светлый фон с аккуратной сеткой."),
        new Category(
            LINK_STYLES,
            "Стили линий",
            "Стандартные линии",
            "Обычные семейные связи и пунктир для братьев и сестёр."),
        new Category(
            WIDGET_FRAMES,
            "Оформления виджетов",
            "Стандартные виджеты",
            "Текущее оформление виджетов дней рождения."),
        new Category(
            EXPORT_FRAMES,
            "Рамки экспорта",
            "Стандартный экспорт",
            "Экспорт без дополнительной декоративной рамки.")
    ));

    private static final List<Item> ITEMS = Collections.unmodifiableList(Arrays.asList(
        new Item(
            "card_theme_archive",
            CARD_THEMES,
            "Архивная карточка",
            "Тёплая бумага, тонкая рамка и мягкие подписи.",
            RARITY_EPIC),
        new Item(
            "card_theme_midnight",
            CARD_THEMES,
            "Полуночная карточка",
            "Тёмная карточка с цветовым акцентом человека.",
            RARITY_RARE),
        new Item(
            "card_theme_glass",
            CARD_THEMES,
            "Стеклянная карточка",
            "Полупрозрачное жидкое стекло с бликами.",
            RARITY_RARE),
        new Item(
            "card_theme_fire",
            CARD_THEMES,
            "Огненная карточка",
            "Угольный градиент и золотистые подписи. Пламя выбирается в разделе «Края карточек».",
            RARITY_EPIC),
        new Item("card_theme_smoke", CARD_THEMES, "Дымная карточка",
            "Графитовая заливка с мягкими дымными переливами и светлыми подписями.", RARITY_RARE),
        new Item("card_theme_botanical", CARD_THEMES, "Растительная карточка",
            "Светлая зелёная карточка с мягкими листьями и природными оттенками.", RARITY_RARE),
        new Item("card_edge_fire", CARD_EDGES, "Огненные края",
            "Живое золотистое пламя и поднимающиеся искры вокруг карточки.", RARITY_EPIC),
        new Item("card_edge_smoke", CARD_EDGES, "Дымные края",
            "Плавные клубы дыма и серебристая тонкая рамка.", RARITY_EPIC),
        new Item("card_edge_botanical", CARD_EDGES, "Растительные края",
            "Зелёные побеги, листья с прожилками и маленькие белые цветы.", RARITY_RARE),
        new Item("link_style_botanical", LINK_STYLES, "Растительные связи",
            "Тонкие вьющиеся стебли с листьями. Родственные пунктиры сохраняются.", RARITY_RARE),
        new Item(
            "canvas_background_paper",
            CANVAS_BACKGROUNDS,
            "Бумажное полотно",
            "Светлый бумажный рельеф с обычной сеткой.",
            RARITY_RARE),
        new Item(
            "canvas_background_blueprint",
            CANVAS_BACKGROUNDS,
            "Чертёжное полотно",
            "Светлый чертёж без мелкой ряби при отдалении.",
            RARITY_COMMON),
        new Item(
            "canvas_background_deep_blueprint",
            CANVAS_BACKGROUNDS,
            "Синий чертёж",
            "Игровой синий blueprint с крупной сеткой и техническими линиями.",
            RARITY_EPIC),
        new Item(
            "canvas_background_garden",
            CANVAS_BACKGROUNDS,
            "Салатовое полотно",
            "Светлый салатовый градиент с мягким мятным оттенком.",
            RARITY_COMMON),
        new Item(
            "canvas_background_botanical",
            CANVAS_BACKGROUNDS,
            "Растительное полотно",
            "Состаренная бумага, бледные ветви и насыщенная зелень по краям.",
            RARITY_EPIC),
        new Item(
            "link_style_ink",
            LINK_STYLES,
            "Чернильные связи",
            "Более выразительные линии родства и партнёрства.",
            RARITY_COMMON),
        new Item(
            "link_style_ribbon",
            LINK_STYLES,
            "Ленточные связи",
            "Две тонкие переплетённые ленты вдоль связи.",
            RARITY_RARE),
        new Item(
            "link_style_blueprint",
            LINK_STYLES,
            "Синие связи",
            "Чёткие синие линии с мягким светлым контуром и узлами.",
            RARITY_COMMON),
        new Item(
            "link_style_fire",
            LINK_STYLES,
            "Огненные связи",
            "Тонкое пламя с тёплым свечением и небольшими искрами.",
            RARITY_EPIC),
        new Item(
            "widget_frame_family",
            WIDGET_FRAMES,
            "Семейная рамка",
            "Новая рамка для виджетов ближайших дней рождения.",
            RARITY_COMMON),
        new Item(
            "widget_frame_botanical",
            WIDGET_FRAMES,
            "Растительная рамка",
            "Листья, прожилки и маленькие цветы по краям виджета.",
            RARITY_RARE),
        new Item(
            "widget_frame_fire",
            WIDGET_FRAMES,
            "Огненная рамка",
            "Золотой жар и языки огня по краям виджета.",
            RARITY_EPIC),
        new Item(
            "export_frame_classic",
            EXPORT_FRAMES,
            "Классическая рамка",
            "Тонкая рамка для PNG/PDF-экспорта дерева.",
            RARITY_RARE),
        new Item(
            "export_frame_fire",
            EXPORT_FRAMES,
            "Огненная рамка",
            "Эпическая рамка с золотым жаром и языками пламени для PNG и PDF.",
            RARITY_EPIC),
        new Item(
            "export_frame_botanical",
            EXPORT_FRAMES,
            "Растительная рамка",
            "Зелёные побеги, листья и цветы по краям PNG и PDF.",
            RARITY_RARE)
    ));

    private RewardCatalog() {}

    static List<Item> items() {
        return ITEMS;
    }

    static List<Category> categories() {
        return CATEGORIES;
    }

    static List<Item> itemsFor(String categoryId) {
        java.util.ArrayList<Item> result = new java.util.ArrayList<>();
        for (Item item : ITEMS) {
            if (item.categoryId.equals(categoryId)) result.add(item);
        }
        result.sort((left, right) -> {
            int rarity = Integer.compare(rarityRank(left.rarity), rarityRank(right.rarity));
            return rarity != 0 ? rarity : left.title.compareToIgnoreCase(right.title);
        });
        return result;
    }

    static String rarityLabel(String rarity) {
        if (RARITY_EPIC.equals(rarity)) return "Эпическое";
        if (RARITY_RARE.equals(rarity)) return "Редкое";
        return "Обычное";
    }

    private static int priceForRarity(String rarity) {
        if (RARITY_EPIC.equals(rarity)) return PRICE_EPIC;
        if (RARITY_RARE.equals(rarity)) return PRICE_RARE;
        return PRICE_COMMON;
    }

    private static String normalizeRarity(String rarity) {
        if (RARITY_EPIC.equals(rarity)) return RARITY_EPIC;
        if (RARITY_RARE.equals(rarity)) return RARITY_RARE;
        return RARITY_COMMON;
    }

    private static int rarityRank(String rarity) {
        if (RARITY_EPIC.equals(rarity)) return 2;
        if (RARITY_RARE.equals(rarity)) return 1;
        return 0;
    }

    static boolean isKnownCategory(String categoryId) {
        for (Category category : CATEGORIES) {
            if (category.id.equals(categoryId)) return true;
        }
        return false;
    }

    static boolean belongsToCategory(String itemId, String categoryId) {
        if (STANDARD.equals(itemId)) return isKnownCategory(categoryId);
        for (Item item : ITEMS) {
            if (item.id.equals(itemId)) return item.categoryId.equals(categoryId);
        }
        return false;
    }
}
