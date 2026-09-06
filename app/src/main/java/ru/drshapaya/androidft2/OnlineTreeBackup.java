package ru.drshapaya.androidft2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class OnlineTreeBackup {
    static final long MIN_INTERVAL_MS = 60L * 60L * 1000L;

    private OnlineTreeBackup() {
    }

    static JSONObject create(
        String treeId,
        String previousState,
        String currentState,
        long revision,
        String mainSha,
        String author
    ) throws Exception {
        JSONObject previous = parse(previousState);
        JSONObject current = parse(currentState);
        Stats stats = new Stats();
        JSONObject changes = diffObject(previous, current, stats);
        Set<String> previousMedia = mediaIds(previous);
        Set<String> currentMedia = mediaIds(current);
        JSONArray mediaAdded = difference(currentMedia, previousMedia);
        JSONArray mediaRemoved = difference(previousMedia, currentMedia);
        stats.mediaAdded = mediaAdded.length();
        stats.mediaRemoved = mediaRemoved.length();
        int sameLeaves = countEqualLeaves(previous, current);
        int changedLeaves = Math.max(1, stats.scalarChanges + stats.addedItems + stats.removedItems);
        int similarity = Math.max(0, Math.min(100, Math.round(
            sameLeaves * 100f / Math.max(1, sameLeaves + changedLeaves))));

        return new JSONObject()
            .put("format", "ru.drshapaya.androidft.online-backup")
            .put("protocol", 1)
            .put("treeId", safe(treeId))
            .put("revision", Math.max(1L, revision))
            .put("createdAt", System.currentTimeMillis())
            .put("createdBy", safe(author))
            .put("mainPath", "androidft/tree.json")
            .put("mainSha", safe(mainSha))
            .put("baseHash", hashState(previous.toString()))
            .put("currentHash", hashState(current.toString()))
            .put("similarity", similarity)
            .put("summary", stats.toJson())
            .put("media", new JSONObject()
                .put("added", mediaAdded)
                .put("removed", mediaRemoved))
            .put("changes", changes);
    }

    static boolean shouldStore(JSONObject backup, long lastBackupAt, String lastBackupHash, long now) {
        if (backup == null) return false;
        String currentHash = backup.optString("currentHash", "");
        if (!currentHash.isEmpty() && currentHash.equals(lastBackupHash)) return false;
        if (lastBackupAt <= 0L || now - lastBackupAt >= MIN_INTERVAL_MS) return true;
        JSONObject summary = backup.optJSONObject("summary");
        JSONObject media = backup.optJSONObject("media");
        int mediaChanges = arrayLength(media, "added") + arrayLength(media, "removed");
        int changedItems = summary == null ? 0 : summary.optInt("changedItems", 0);
        int addedItems = summary == null ? 0 : summary.optInt("addedItems", 0);
        int removedItems = summary == null ? 0 : summary.optInt("removedItems", 0);
        return mediaChanges > 0 || addedItems + removedItems > 0 || changedItems >= 10;
    }

    static String hashState(String stateJson) throws Exception {
        JSONObject object = parse(stateJson);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(object.toString().getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format(Locale.US, "%02x", value & 0xff));
        return hex.toString();
    }

    private static JSONObject diffObject(JSONObject previous, JSONObject current, Stats stats) throws Exception {
        JSONObject result = new JSONObject();
        for (String key : unionKeys(previous, current)) {
            boolean beforeHas = previous.has(key);
            boolean afterHas = current.has(key);
            if (!beforeHas) {
                stats.addedItems++;
                result.put(key, new JSONObject().put("added", compact(current.opt(key))));
                continue;
            }
            if (!afterHas) {
                stats.removedItems++;
                result.put(key, new JSONObject().put("removed", compact(previous.opt(key))));
                continue;
            }
            Object before = previous.opt(key);
            Object after = current.opt(key);
            if (jsonEquals(before, after)) continue;
            result.put(key, diffValue(before, after, stats));
        }
        return result;
    }

    private static JSONObject diffValue(Object before, Object after, Stats stats) throws Exception {
        if (before instanceof JSONObject && after instanceof JSONObject) {
            JSONObject beforeObject = (JSONObject) before;
            JSONObject afterObject = (JSONObject) after;
            if (looksLikeIdMap(beforeObject) || looksLikeIdMap(afterObject)) {
                return diffIdMap(beforeObject, afterObject, stats);
            }
            JSONObject fields = diffObject(beforeObject, afterObject, stats);
            return fields.length() == 0 ? new JSONObject() : new JSONObject().put("fields", fields);
        }
        if (before instanceof JSONArray && after instanceof JSONArray) {
            return diffArray((JSONArray) before, (JSONArray) after, stats);
        }
        stats.scalarChanges++;
        return new JSONObject()
            .put("before", compact(before))
            .put("after", compact(after));
    }

    private static JSONObject diffIdMap(JSONObject previous, JSONObject current, Stats stats) throws Exception {
        JSONArray added = new JSONArray();
        JSONArray removed = new JSONArray();
        JSONObject changed = new JSONObject();
        for (String key : unionKeys(previous, current)) {
            boolean beforeHas = previous.has(key);
            boolean afterHas = current.has(key);
            if (!beforeHas) {
                stats.addedItems++;
                added.put(compact(current.opt(key)));
            } else if (!afterHas) {
                stats.removedItems++;
                removed.put(compact(previous.opt(key)));
            } else if (!jsonEquals(previous.opt(key), current.opt(key))) {
                stats.changedItems++;
                changed.put(key, diffValue(previous.opt(key), current.opt(key), stats));
            }
        }
        JSONObject result = new JSONObject();
        if (added.length() > 0) result.put("added", added);
        if (removed.length() > 0) result.put("removed", removed);
        if (changed.length() > 0) result.put("changed", changed);
        return result;
    }

    private static JSONObject diffArray(JSONArray previous, JSONArray current, Stats stats) throws Exception {
        JSONObject previousMap = arrayById(previous);
        JSONObject currentMap = arrayById(current);
        if (previousMap.length() > 0 || currentMap.length() > 0) {
            return diffIdMap(previousMap, currentMap, stats);
        }
        stats.scalarChanges++;
        return new JSONObject()
            .put("beforeHash", hashValue(previous))
            .put("afterHash", hashValue(current))
            .put("beforeCount", previous == null ? 0 : previous.length())
            .put("afterCount", current == null ? 0 : current.length());
    }

    private static JSONObject arrayById(JSONArray array) throws Exception {
        JSONObject result = new JSONObject();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) return new JSONObject();
            String id = item.optString("id", "");
            if (id.isEmpty()) return new JSONObject();
            result.put(id, item);
        }
        return result;
    }

    private static Object compact(Object value) throws Exception {
        if (value instanceof JSONObject || value instanceof JSONArray) return value;
        if (!(value instanceof String)) return value;
        String text = (String) value;
        if (text.length() <= 220) return text;
        return new JSONObject()
            .put("hash", hashValue(text))
            .put("length", text.length());
    }

    private static Set<String> mediaIds(Object value) {
        Set<String> result = new LinkedHashSet<>();
        collectMediaIds(value, result);
        return result;
    }

    private static void collectMediaIds(Object value, Set<String> result) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) collectMediaIds(object.opt(keys.next()), result);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) collectMediaIds(array.opt(i), result);
        } else if (value instanceof String) {
            String text = (String) value;
            if (text.matches("(photo|attachment)_[A-Za-z0-9._-]{8,170}")) result.add(text);
        }
    }

    private static int countEqualLeaves(Object before, Object after) {
        if (before instanceof JSONObject && after instanceof JSONObject) {
            int count = 0;
            JSONObject first = (JSONObject) before;
            JSONObject second = (JSONObject) after;
            for (String key : unionKeys(first, second)) {
                if (first.has(key) && second.has(key)) count += countEqualLeaves(first.opt(key), second.opt(key));
            }
            return count;
        }
        if (before instanceof JSONArray && after instanceof JSONArray) {
            JSONArray first = (JSONArray) before;
            JSONArray second = (JSONArray) after;
            int count = 0;
            for (int i = 0; i < Math.min(first.length(), second.length()); i++) {
                count += countEqualLeaves(first.opt(i), second.opt(i));
            }
            return count;
        }
        return jsonEquals(before, after) ? 1 : 0;
    }

    private static JSONArray difference(Set<String> first, Set<String> second) {
        JSONArray result = new JSONArray();
        for (String value : first) if (!second.contains(value)) result.put(value);
        return result;
    }

    private static boolean looksLikeIdMap(JSONObject object) {
        if (object == null || object.length() == 0) return false;
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            Object value = object.opt(keys.next());
            if (!(value instanceof JSONObject)) return false;
        }
        return true;
    }

    private static List<String> unionKeys(JSONObject first, JSONObject second) {
        List<String> keys = new ArrayList<>();
        addKeys(keys, first);
        addKeys(keys, second);
        Collections.sort(keys);
        return keys;
    }

    private static void addKeys(List<String> keys, JSONObject object) {
        if (object == null) return;
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (!keys.contains(key)) keys.add(key);
        }
    }

    private static boolean jsonEquals(Object before, Object after) {
        if (before == null || before == JSONObject.NULL) return after == null || after == JSONObject.NULL;
        if (after == null || after == JSONObject.NULL) return false;
        return before.toString().equals(after.toString());
    }

    private static String hashValue(Object value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(String.valueOf(value).getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte item : bytes) hex.append(String.format(Locale.US, "%02x", item & 0xff));
        return hex.toString();
    }

    private static int arrayLength(JSONObject object, String key) {
        JSONArray array = object == null ? null : object.optJSONArray(key);
        return array == null ? 0 : array.length();
    }

    private static JSONObject parse(String json) throws Exception {
        String value = json == null || json.trim().isEmpty() ? "{}" : json;
        return new JSONObject(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Stats {
        int addedItems;
        int removedItems;
        int changedItems;
        int scalarChanges;
        int mediaAdded;
        int mediaRemoved;

        JSONObject toJson() throws Exception {
            return new JSONObject()
                .put("addedItems", addedItems)
                .put("removedItems", removedItems)
                .put("changedItems", changedItems)
                .put("fieldChanges", scalarChanges)
                .put("mediaAdded", mediaAdded)
                .put("mediaRemoved", mediaRemoved);
        }
    }
}
