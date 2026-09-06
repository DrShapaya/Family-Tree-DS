package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OnlineTreeBackupTest {
    @Test
    public void backupStoresDeltaAndMediaReferences() throws Exception {
        JSONObject previous = state(person("p1", "Анна", "photo_old12345678.jpg", "Красноярск"));
        JSONObject current = state(person("p1", "Анна", "photo_new12345678.jpg", "Москва"));

        JSONObject backup = OnlineTreeBackup.create(
            "tree",
            previous.toString(),
            current.toString(),
            7,
            "sha",
            "isaev");

        assertEquals("ru.drshapaya.androidft.online-backup", backup.getString("format"));
        assertEquals(7, backup.getLong("revision"));
        assertTrue(backup.getInt("similarity") < 100);
        assertEquals("photo_new12345678.jpg", backup.getJSONObject("media").getJSONArray("added").getString(0));
        assertEquals("photo_old12345678.jpg", backup.getJSONObject("media").getJSONArray("removed").getString(0));

        JSONObject personChanges = backup
            .getJSONObject("changes")
            .getJSONObject("people")
            .getJSONObject("changed")
            .getJSONObject("p1")
            .getJSONObject("fields");
        assertEquals("Красноярск", personChanges.getJSONObject("place").getString("before"));
        assertEquals("Москва", personChanges.getJSONObject("place").getString("after"));
    }

    @Test
    public void hourlyOrImportantChangesCreateBackup() throws Exception {
        JSONObject previous = state(person("p1", "Анна", "photo_old12345678.jpg", ""));
        JSONObject current = state(person("p1", "Анна", "photo_new12345678.jpg", ""));
        JSONObject backup = OnlineTreeBackup.create("tree", previous.toString(), current.toString(), 2, "sha", "isaev");
        String hash = backup.getString("currentHash");

        assertFalse(OnlineTreeBackup.shouldStore(backup, 10_000L, hash, 10_500L));
        assertTrue(OnlineTreeBackup.shouldStore(backup, 10_000L, "", 10_500L));
        JSONObject small = OnlineTreeBackup.create("tree", current.toString(), current.toString(), 3, "sha", "isaev");
        assertTrue(OnlineTreeBackup.shouldStore(small, 10_000L, "", 10_000L + OnlineTreeBackup.MIN_INTERVAL_MS));
    }

    private static JSONObject state(JSONObject person) throws Exception {
        return new JSONObject()
            .put("people", new JSONObject().put(person.getString("id"), person))
            .put("links", new JSONArray());
    }

    private static JSONObject person(String id, String name, String photoId, String place)
        throws Exception {
        return new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("photoId", photoId)
            .put("place", place);
    }
}
