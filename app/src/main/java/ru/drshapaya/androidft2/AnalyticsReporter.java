package ru.drshapaya.androidft2;

import android.app.Application;

import java.util.HashMap;
import java.util.Map;

import io.appmetrica.analytics.AppMetrica;
import io.appmetrica.analytics.AppMetricaConfig;

final class AnalyticsReporter {
    private static boolean active;
    private static long lastTreeSavedAt;
    private static int lastSavedPeopleCount = -1;
    private static int lastSavedLinkCount = -1;

    private AnalyticsReporter() {
    }

    static void initialize(Application application) {
        String apiKey = BuildConfig.APPMETRICA_API_KEY == null
            ? ""
            : BuildConfig.APPMETRICA_API_KEY.trim();
        if (application == null || apiKey.isEmpty()) {
            DiagnosticsLogger.breadcrumb(application, "analytics.disabled");
            return;
        }
        try {
            AppMetricaConfig config = AppMetricaConfig.newConfigBuilder(apiKey).build();
            AppMetrica.activate(application, config);
            AppMetrica.enableActivityAutoTracking(application);
            active = true;
            event("app_start", "build_type", BuildConfig.DEBUG ? "debug" : "release");
        } catch (RuntimeException error) {
            DiagnosticsLogger.handled(application, "analytics.init", error);
        }
    }

    static void event(String name) {
        event(name, null);
    }

    static void event(String name, String key, String value) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(key, safe(value));
        event(name, attributes);
    }

    static void event(String name, String key, int value) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(key, value);
        event(name, attributes);
    }

    static void event(String name, Map<String, Object> attributes) {
        if (!active) return;
        String safeName = safeEventName(name);
        if (safeName.isEmpty()) return;
        try {
            if (attributes == null || attributes.isEmpty()) {
                AppMetrica.reportEvent(safeName);
            } else {
                AppMetrica.reportEvent(safeName, attributes);
            }
        } catch (RuntimeException ignored) {
            // Analytics must never affect the app flow.
        }
    }

    static void rewardedAdRequested(boolean ready, boolean loading) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("ready", ready);
        attributes.put("loading", loading);
        event("rewarded_ad_requested", attributes);
    }

    static void rewardedAdEarned(int tokens, int balance) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("tokens", tokens);
        attributes.put("balance", balance);
        event("rewarded_ad_earned", attributes);
    }

    static void workshopItemPurchased(RewardCatalog.Item item, int balance) {
        if (item == null) return;
        Map<String, Object> attributes = workshopItemAttributes(item.id, item.categoryId);
        attributes.put("price", item.price);
        attributes.put("rarity", safe(item.rarity));
        attributes.put("balance", balance);
        event("workshop_item_purchased", attributes);
    }

    static void workshopItemSelected(String categoryId, String itemId) {
        event("workshop_item_selected", workshopItemAttributes(itemId, categoryId));
    }

    static void treeLoaded(TreeState state) {
        event("tree_loaded", treeAttributes(state));
    }

    static void treeSaved(TreeState state) {
        if (state == null) return;
        int people = state.people.size();
        int links = state.links.size();
        long now = System.currentTimeMillis();
        if (people == lastSavedPeopleCount
            && links == lastSavedLinkCount
            && now - lastTreeSavedAt < 60_000L) {
            return;
        }
        lastSavedPeopleCount = people;
        lastSavedLinkCount = links;
        lastTreeSavedAt = now;
        event("tree_saved", treeAttributes(state));
    }

    static void personAdded(String source, int count, boolean firstTreeCard, TreeState state) {
        Map<String, Object> attributes = treeAttributes(state);
        attributes.put("source", safe(source));
        attributes.put("count", Math.max(1, count));
        attributes.put("first_tree_card", firstTreeCard);
        event("person_added", attributes);
        if (firstTreeCard) {
            event("tree_created", attributes);
        }
    }

    static void personDeleted(int count, boolean wholeTree, int peopleBefore, int linksBefore) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("count", Math.max(1, count));
        attributes.put("whole_tree", wholeTree);
        attributes.put("people_before", Math.max(0, peopleBefore));
        attributes.put("links_before", Math.max(0, linksBefore));
        event("person_deleted", attributes);
        if (wholeTree) event("tree_deleted", attributes);
    }

    static void relationCreated(String type, TreeState state) {
        Map<String, Object> attributes = treeAttributes(state);
        attributes.put("relation_type", safe(type));
        event("relation_created", attributes);
    }

    static void relationDeleted(TreeState state) {
        event("relation_deleted", treeAttributes(state));
    }

    static void treeImported(TreeState state) {
        event("tree_imported", treeAttributes(state));
    }

    static void treeExported(String format, TreeState state) {
        Map<String, Object> attributes = treeAttributes(state);
        attributes.put("format", safe(format));
        event("tree_exported", attributes);
    }

    static void onlineAction(String name, TreeState state) {
        event(name, treeAttributes(state));
    }

    private static Map<String, Object> treeAttributes(TreeState state) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("people_count", state == null ? 0 : state.people.size());
        attributes.put("links_count", state == null ? 0 : state.links.size());
        attributes.put("guides_count", state == null ? 0 : state.guides.size());
        attributes.put("has_tree", state != null && !state.people.isEmpty());
        return attributes;
    }

    private static Map<String, Object> workshopItemAttributes(String itemId, String categoryId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("item_id", safe(itemId));
        attributes.put("category_id", safe(categoryId));
        return attributes;
    }

    private static String safeEventName(String value) {
        String safe = value == null ? "" : value.trim().replaceAll("[^a-zA-Z0-9_]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
