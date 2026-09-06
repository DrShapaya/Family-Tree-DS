package ru.drshapaya.androidft2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class SupporterCatalog {
    static final class Entry {
        final String name;
        final double amount;
        final String currency;

        Entry(String name, double amount, String currency) {
            this.name = name == null || name.trim().isEmpty() ? "Аноним" : name.trim();
            this.amount = amount;
            this.currency = currency == null || currency.trim().isEmpty()
                ? "RUB"
                : currency.trim().toUpperCase(Locale.ROOT);
        }
    }

    private SupporterCatalog() {}

    static List<Entry> parse(String json) {
        if (json == null || json.trim().isEmpty()) return Collections.emptyList();
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            JSONArray values = new JSONObject(json).optJSONArray("supporters");
            if (values == null) return entries;
            for (int index = 0; index < values.length(); index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) continue;
                double amount = value.optDouble("amount", 0d);
                if (!Double.isFinite(amount) || amount <= 0d) continue;
                entries.add(new Entry(
                    value.optString("name", "Аноним"),
                    amount,
                    value.optString("currency", "RUB")));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        entries.sort(Comparator.comparingDouble((Entry entry) -> entry.amount).reversed());
        return entries;
    }

    static long amountToBeat(Entry leader) {
        if (leader == null || !Double.isFinite(leader.amount) || leader.amount <= 0d) return 0L;
        return Math.max(1L, (long) Math.ceil(leader.amount * 1.10d));
    }
}
