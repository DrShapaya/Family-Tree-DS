package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OnlineSyncPayloadTest {
    @Test
    public void volatileDeviceFieldsDoNotMakeTreeDirty() throws Exception {
        JSONObject first = tree("2026-07-30T10:00:00Z", "copy", "person-a");
        JSONObject second = tree("2026-07-30T10:00:20Z", "view", "person-b");

        String firstPayload = OnlineSyncPayload.fromTreeJson(first).toString();
        String secondPayload = OnlineSyncPayload.fromTreeJson(second).toString();

        assertEquals(firstPayload, secondPayload);
        assertFalse(new JSONObject(firstPayload).has("exportedAt"));
        assertFalse(new JSONObject(firstPayload).has("mode"));
        assertFalse(new JSONObject(firstPayload).has("settings"));
        assertFalse(new JSONObject(firstPayload).has("selectedId"));
        assertTrue(new JSONObject(firstPayload).has("history"));
    }

    private static JSONObject tree(String exportedAt, String mode, String selectedId)
        throws Exception {
        return new JSONObject()
            .put("format", "ru.drshapaya.familytree.ftree")
            .put("version", 2)
            .put("exportedAt", exportedAt)
            .put("mode", mode)
            .put("selectedId", selectedId)
            .put("settings", new JSONObject().put("theme", "dark"))
            .put("people", new JSONObject().put(
                "person-a",
                new JSONObject().put("id", "person-a").put("name", "Анна")))
            .put("history", new JSONArray().put(
                new JSONObject().put("id", "history-a").put("label", "Создание")));
    }
}
