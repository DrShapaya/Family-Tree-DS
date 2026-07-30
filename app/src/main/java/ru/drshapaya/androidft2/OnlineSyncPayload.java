package ru.drshapaya.androidft2;

import org.json.JSONObject;

/**
 * Produces the stable part of a tree document that is shared between devices.
 * Export timestamps and device modes must never make an otherwise unchanged
 * tree look dirty.
 */
final class OnlineSyncPayload {
    private OnlineSyncPayload() {
    }

    static JSONObject fromTreeJson(JSONObject source) throws Exception {
        JSONObject result = source == null
            ? new JSONObject()
            : new JSONObject(source.toString());
        result.remove("settings");
        result.remove("selectedId");
        result.remove("exportedAt");
        result.remove("mode");
        return result;
    }

    static String fromTreeJson(String source) throws Exception {
        return fromTreeJson(new JSONObject(source == null ? "{}" : source)).toString();
    }
}
