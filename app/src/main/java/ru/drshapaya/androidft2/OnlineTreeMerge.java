package ru.drshapaya.androidft2;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Three-way merge for tree JSON. Independent edits to different people,
 * links, positions or fields survive. A local edit wins only when both sides
 * changed the exact same value since the last common snapshot.
 */
final class OnlineTreeMerge {
    private static final Object MISSING = new Object();

    private OnlineTreeMerge() {
    }

    static JSONObject merge(JSONObject base, JSONObject local, JSONObject remote) throws Exception {
        Object value = mergeValue(
            base == null ? new JSONObject() : base,
            local == null ? new JSONObject() : local,
            remote == null ? new JSONObject() : remote);
        return value instanceof JSONObject ? (JSONObject) value : new JSONObject();
    }

    private static Object mergeValue(Object base, Object local, Object remote) throws Exception {
        if (same(local, base)) return copy(remote);
        if (same(remote, base)) return copy(local);
        if (same(local, remote)) return copy(local);

        if (local instanceof JSONObject && remote instanceof JSONObject) {
            return mergeObject(
                base instanceof JSONObject ? (JSONObject) base : new JSONObject(),
                (JSONObject) local,
                (JSONObject) remote);
        }
        if (local instanceof JSONArray && remote instanceof JSONArray) {
            JSONArray baseArray = base instanceof JSONArray ? (JSONArray) base : new JSONArray();
            if (isIdArray(baseArray, (JSONArray) local, (JSONArray) remote)) {
                return mergeIdArray(baseArray, (JSONArray) local, (JSONArray) remote);
            }
        }
        return copy(local);
    }

    private static JSONObject mergeObject(
        JSONObject base,
        JSONObject local,
        JSONObject remote
    ) throws Exception {
        JSONObject result = new JSONObject();
        Set<String> keys = new LinkedHashSet<>();
        addKeys(keys, base);
        addKeys(keys, remote);
        addKeys(keys, local);
        for (String key : keys) {
            Object merged = mergeValue(
                base.has(key) ? base.opt(key) : MISSING,
                local.has(key) ? local.opt(key) : MISSING,
                remote.has(key) ? remote.opt(key) : MISSING);
            if (merged != MISSING) result.put(key, merged);
        }
        return result;
    }

    private static JSONArray mergeIdArray(
        JSONArray base,
        JSONArray local,
        JSONArray remote
    ) throws Exception {
        JSONObject baseMap = mapById(base);
        JSONObject localMap = mapById(local);
        JSONObject remoteMap = mapById(remote);
        JSONObject merged = mergeObject(baseMap, localMap, remoteMap);
        JSONArray result = new JSONArray();
        Set<String> order = new LinkedHashSet<>();
        addArrayIds(order, remote);
        addArrayIds(order, local);
        Iterator<String> keys = merged.keys();
        while (keys.hasNext()) order.add(keys.next());
        for (String id : order) {
            JSONObject item = merged.optJSONObject(id);
            if (item != null) result.put(item);
        }
        return result;
    }

    private static JSONObject mapById(JSONArray array) throws Exception {
        JSONObject result = new JSONObject();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            if (!id.isEmpty()) result.put(id, item);
        }
        return result;
    }

    private static boolean isIdArray(JSONArray... arrays) {
        boolean found = false;
        for (JSONArray array : arrays) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null || item.optString("id", "").isEmpty()) return false;
                found = true;
            }
        }
        return found;
    }

    private static void addKeys(Set<String> target, JSONObject source) {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) target.add(keys.next());
    }

    private static void addArrayIds(Set<String> target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null) target.add(item.optString("id", ""));
        }
        target.remove("");
    }

    private static boolean same(Object first, Object second) {
        if (first == MISSING || second == MISSING) return first == second;
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (first == JSONObject.NULL || second == JSONObject.NULL) {
            return first == JSONObject.NULL && second == JSONObject.NULL;
        }
        return first.toString().equals(second.toString());
    }

    private static Object copy(Object value) throws Exception {
        if (value == MISSING) return MISSING;
        if (value instanceof JSONObject) return new JSONObject(value.toString());
        if (value instanceof JSONArray) return new JSONArray(value.toString());
        return value == null ? JSONObject.NULL : value;
    }
}
