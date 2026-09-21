package ru.drshapaya.androidft2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class SupporterCatalog {
    static final class Fundraiser {
        final String title;
        final String description;
        final double goal;
        final double raised;
        final String currency;

        Fundraiser(String title, String description, double goal, double raised, String currency) {
            this.title = title == null || title.trim().isEmpty() ? "Сбор на сервер" : title.trim();
            this.description = description == null ? "" : description.trim();
            this.goal = Double.isFinite(goal) && goal > 0d ? goal : 30000d;
            this.raised = Double.isFinite(raised) && raised > 0d ? Math.min(raised, this.goal) : 0d;
            this.currency = currency == null || currency.trim().isEmpty()
                ? "RUB"
                : currency.trim().toUpperCase(Locale.ROOT);
        }
    }

    static final class RemoteData {
        final Fundraiser fundraiser;
        final List<Entry> supporters;
        final int displayLimit;

        RemoteData(Fundraiser fundraiser, List<Entry> supporters, int displayLimit) {
            this.fundraiser = fundraiser;
            this.supporters = supporters == null ? Collections.emptyList() : supporters;
            this.displayLimit = displayLimit;
        }
    }

    static final class Entry {
        final String name;
        final double amount;
        final String currency;
        final String color;
        final boolean showHeart;
        final int order;

        Entry(
            String name,
            double amount,
            String currency,
            String color,
            boolean showHeart,
            int order
        ) {
            this.name = name == null || name.trim().isEmpty() ? "Аноним" : name.trim();
            this.amount = amount;
            this.currency = currency == null || currency.trim().isEmpty()
                ? "RUB"
                : currency.trim().toUpperCase(Locale.ROOT);
            this.color = normalizeColor(color);
            this.showHeart = showHeart;
            this.order = order;
        }
    }

    private SupporterCatalog() {}

    static List<Entry> parse(String json) {
        return parseData(json).supporters;
    }

    static RemoteData parseData(String json) {
        Fundraiser fundraiser = new Fundraiser("Сбор на сервер", "", 30000d, 0d, "RUB");
        int displayLimit = 5;
        if (json == null || json.trim().isEmpty()) return new RemoteData(fundraiser, Collections.emptyList(), displayLimit);
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(json);
            displayLimit = clampDisplayLimit(root.optInt("displayLimit", 5));
            JSONObject fundraiserJson = root.optJSONObject("fundraiser");
            if (fundraiserJson != null) {
                fundraiser = new Fundraiser(
                    fundraiserJson.optString("title", "Сбор на сервер"),
                    fundraiserJson.optString("description", ""),
                    fundraiserJson.optDouble("goal", 30000d),
                    fundraiserJson.optDouble("raised", 0d),
                    fundraiserJson.optString("currency", "RUB"));
            }
            JSONArray values = root.optJSONArray("supporters");
            if (values == null) return new RemoteData(fundraiser, entries, displayLimit);
            boolean hasExplicitOrder = false;
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                double amount = value.optDouble("amount", 0d);
                if (!Double.isFinite(amount) || amount <= 0d) continue;
                boolean hasOrder = value.has("order");
                hasExplicitOrder = hasExplicitOrder || hasOrder;
                entries.add(new Entry(
                    value.optString("name", "Аноним"),
                    amount,
                    value.optString("currency", "RUB"),
                    value.optString("color", "#087A73"),
                    value.optBoolean("showHeart", value.optBoolean("heart", false)),
                    hasOrder ? value.optInt("order", index) : index));
            }
            if (hasExplicitOrder) {
                entries.sort(Comparator.comparingInt((Entry entry) -> entry.order));
            } else {
                entries.sort(Comparator.comparingDouble((Entry entry) -> entry.amount).reversed());
            }
            fundraiser = withSupporterTotal(fundraiser, entries);
        } catch (Exception ignored) {
            return new RemoteData(fundraiser, Collections.emptyList(), displayLimit);
        }
        return new RemoteData(fundraiser, entries, displayLimit);
    }

    private static Fundraiser withSupporterTotal(Fundraiser fundraiser, List<Entry> entries) {
        double supporterTotal = 0d;
        for (Entry entry : entries) {
            if (fundraiser.currency.equals(entry.currency)) {
                supporterTotal += entry.amount;
            }
        }
        return new Fundraiser(
            fundraiser.title,
            fundraiser.description,
            fundraiser.goal,
            Math.max(fundraiser.raised, supporterTotal),
            fundraiser.currency);
    }

    private static int clampDisplayLimit(int value) {
        return Math.max(1, Math.min(20, value));
    }

    private static String normalizeColor(String value) {
        String color = value == null ? "" : value.trim();
        return color.matches("(?i)^#[0-9a-f]{6}$") ? color.toUpperCase(Locale.ROOT) : "#087A73";
    }

    static long amountToBeat(Entry leader) {
        if (leader == null || !Double.isFinite(leader.amount) || leader.amount <= 0d) return 0L;
        return Math.max(1L, (long) Math.ceil(leader.amount * 1.10d));
    }
}
