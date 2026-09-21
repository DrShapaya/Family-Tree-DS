package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

final class RewardWallet {
    static final int REWARDED_AD_TOKENS = 10;
    static final long REWARDED_AD_COOLDOWN_MS = 8L * 1000L;

    private static final String PREFS = "androidft-rewards";
    private static final String KEY_TOKENS = "tokens";
    private static final String KEY_OWNED = "owned";
    private static final String KEY_SELECTED_PREFIX = "selected_";
    private static final String KEY_LAST_REWARD_AT = "last_reward_at";

    private final SharedPreferences preferences;

    RewardWallet(Context context) {
        preferences = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateCardEdges(preferences);
    }

    // Split the legacy fire purchase once, preserving both ownership and appearance.
    static void migrateCardEdges(SharedPreferences preferences) {
        if (preferences.getBoolean("card_edges_migrated", false)) return;
        Set<String> owned = new HashSet<>(preferences.getStringSet(KEY_OWNED, java.util.Collections.emptySet()));
        SharedPreferences.Editor edit = preferences.edit().putBoolean("card_edges_migrated", true);
        if (owned.contains("card_theme_fire")) {
            owned.add("card_edge_fire");
            edit.putStringSet(KEY_OWNED, owned);
            String edgeKey = KEY_SELECTED_PREFIX + RewardCatalog.CARD_EDGES;
            if (!preferences.contains(edgeKey) && "card_theme_fire".equals(
                    preferences.getString(KEY_SELECTED_PREFIX + RewardCatalog.CARD_THEMES, "")))
                edit.putString(edgeKey, "card_edge_fire");
        }
        edit.apply();
    }

    int balance() {
        return Math.max(0, preferences.getInt(KEY_TOKENS, 0));
    }

    long rewardedAdCooldownLeft(long now) {
        long last = Math.max(0L, preferences.getLong(KEY_LAST_REWARD_AT, 0L));
        long left = REWARDED_AD_COOLDOWN_MS - Math.max(0L, now - last);
        return Math.max(0L, left);
    }

    boolean canEarnRewardedAd(long now) {
        return rewardedAdCooldownLeft(now) == 0L;
    }

    String rewardedAdBlockedMessage(long now) {
        long seconds = (rewardedAdCooldownLeft(now) + 999L) / 1000L;
        if (seconds > 0L) return "Следующая реклама через " + seconds + " с";
        return "";
    }

    void recordRewardedAd(long now) {
        preferences.edit()
            .putInt(KEY_TOKENS, balance() + REWARDED_AD_TOKENS)
            .putLong(KEY_LAST_REWARD_AT, now)
            .apply();
    }

    void grantTokens(int amount) {
        if (amount <= 0) return;
        preferences.edit()
            .putInt(KEY_TOKENS, balance() + amount)
            .apply();
    }

    boolean spendTokens(int amount) {
        if (amount <= 0 || balance() < amount) return false;
        preferences.edit()
            .putInt(KEY_TOKENS, balance() - amount)
            .apply();
        return true;
    }

    boolean owns(String id) {
        if (RewardCatalog.STANDARD.equals(id)) return true;
        return owned().contains(id);
    }

    boolean purchase(RewardCatalog.Item item) {
        if (item == null || owns(item.id) || balance() < item.price) return false;
        Set<String> nextOwned = owned();
        nextOwned.add(item.id);
        preferences.edit()
            .putInt(KEY_TOKENS, balance() - item.price)
            .putStringSet(KEY_OWNED, nextOwned)
            .apply();
        return true;
    }

    String selected(String categoryId) {
        if (!RewardCatalog.isKnownCategory(categoryId)) return RewardCatalog.STANDARD;
        String id = preferences.getString(KEY_SELECTED_PREFIX + categoryId, RewardCatalog.STANDARD);
        if ((RewardCatalog.STANDARD.equals(id) || owns(id))
            && RewardCatalog.belongsToCategory(id, categoryId)) return id;
        return RewardCatalog.STANDARD;
    }

    boolean select(String categoryId, String itemId) {
        if (!RewardCatalog.isKnownCategory(categoryId)) return false;
        String safe = itemId == null || itemId.trim().isEmpty()
            ? RewardCatalog.STANDARD
            : itemId.trim();
        if (!RewardCatalog.belongsToCategory(safe, categoryId)) return false;
        if (!RewardCatalog.STANDARD.equals(safe) && !owns(safe)) return false;
        preferences.edit()
            .putString(KEY_SELECTED_PREFIX + categoryId, safe)
            .apply();
        return true;
    }

    private Set<String> owned() {
        return new HashSet<>(preferences.getStringSet(KEY_OWNED, java.util.Collections.emptySet()));
    }
}
