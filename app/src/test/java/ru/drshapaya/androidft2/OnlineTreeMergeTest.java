package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OnlineTreeMergeTest {
    @Test
    public void independentPersonFieldsSurviveConcurrentEdits() throws Exception {
        JSONObject base = state(person("p1", "Анна", "", ""));
        JSONObject local = state(person("p1", "Анна Мария", "", ""));
        JSONObject remote = state(person("p1", "Анна", "Красноярск", ""));

        JSONObject merged = OnlineTreeMerge.merge(base, local, remote);
        JSONObject person = merged.getJSONObject("people").getJSONObject("p1");

        assertEquals("Анна Мария", person.getString("name"));
        assertEquals("Красноярск", person.getString("place"));
    }

    @Test
    public void independentlyAddedIdItemsAreBothKept() throws Exception {
        JSONObject base = new JSONObject().put("links", new JSONArray());
        JSONObject local = new JSONObject().put(
            "links",
            new JSONArray().put(new JSONObject().put("id", "local").put("type", "partner")));
        JSONObject remote = new JSONObject().put(
            "links",
            new JSONArray().put(new JSONObject().put("id", "remote").put("type", "parent")));

        JSONArray links = OnlineTreeMerge.merge(base, local, remote).getJSONArray("links");

        assertEquals(2, links.length());
    }

    @Test
    public void localDeletionWinsAConflictWithoutResurrectingItem() throws Exception {
        JSONObject base = state(person("p1", "Анна", "", ""));
        JSONObject local = new JSONObject().put("people", new JSONObject());
        JSONObject remote = state(person("p1", "Анна", "", "Удалённая заметка"));

        JSONObject merged = OnlineTreeMerge.merge(base, local, remote);

        assertFalse(merged.getJSONObject("people").has("p1"));
    }

    private static JSONObject state(JSONObject person) throws Exception {
        return new JSONObject().put(
            "people",
            new JSONObject().put(person.getString("id"), person));
    }

    private static JSONObject person(String id, String name, String place, String notes)
        throws Exception {
        return new JSONObject()
            .put("id", id)
            .put("name", name)
            .put("place", place)
            .put("notes", notes);
    }
}
