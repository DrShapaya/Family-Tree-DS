package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.*;
import java.io.File;
import android.content.Context;
import android.content.ContextWrapper;

public class PersistenceRegressionTest {
    private TreeStore store() {
        return new TreeStore(new ContextWrapper(null) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return new File(System.getProperty("java.io.tmpdir"), "androidft-unit"); }
        });
    }

    @Test public void roundTripKeepsLargeArchivesAndAlbums() throws Exception {
        TreeState tree = new TreeState();
        Person person = tree.addPerson("Человек", 0, 0);
        for (int i=0; i<105; i++) {
            Memory memory = new Memory(); memory.id="m_"+i; memory.text="История".repeat(1200);
            person.memories.add(memory);
            tree.photoAlbums.put("Альбом "+i, new ArrayList<>(Collections.singleton(person.id)));
        }
        TreeState loaded=store().parse(store().toJson(tree).toString());
        assertEquals(105, loaded.people.get(person.id).memories.size());
        assertEquals(person.memories.get(0).text, loaded.people.get(person.id).memories.get(0).text);
        assertEquals(tree.photoAlbums, loaded.photoAlbums);
    }

    @Test public void undoAndRedoRestoreAlbumReferencesWithoutAliasing() {
        TreeState tree = new TreeState(); Person p=tree.addPerson("Первый",0,0);
        tree.addPerson("Второй",320,0);
        tree.photoAlbums.put("Альбом",new ArrayList<>(Collections.singleton(p.id)));
        tree.personAlbumMedia.put(p.id,new ArrayList<>(Collections.singleton("photo_12345678.jpg")));
        PhotoAlbumFolder folder = new PhotoAlbumFolder("Детство"); folder.personIds.add(p.id);
        tree.photoAlbumFolders.put("Альбом",new ArrayList<>(Collections.singleton(folder)));
        TreeState before=TreeStateCopier.copy(tree);
        tree.deletePerson(p.id);
        TreeDeltaCommand command=TreeDeltaCommand.between(before,tree,"Delete");
        command.undo(tree); assertTrue(TreeAlbumSnapshot.same(before,tree));
        tree.photoAlbumFolders.get("Альбом").get(0).personIds.clear();
        command.redo(tree); assertFalse(tree.people.containsKey(p.id));
        command.undo(tree); assertTrue(TreeAlbumSnapshot.same(before,tree));
    }

    @Test public void importsIsoAndDmyDatesWithoutClampingYearIntoDay() throws Exception {
        for (String date: Arrays.asList("1980-12-25","25.12.1980")) {
            Person p=store().parse("{\"people\":{\"p\":{\"born\":\""+date+"\"}}}").people.get("p");
            assertEquals("25",p.bornDay); assertEquals("12",p.bornMonth); assertEquals("1980",p.bornYear);
        }
        Person invalid=store().parse("{\"people\":{\"p\":{\"born\":\"1980-02-31\"}}}").people.get("p");
        assertEquals("",invalid.bornDay); assertEquals("1980",invalid.bornYear);
    }

    @Test(expected=java.io.IOException.class) public void rejectsMissingPeopleStructure() throws Exception { store().parse("{}"); }
    @Test public void acceptsExplicitlyEmptyTree() throws Exception { assertTrue(store().parse("{\"people\":{}}").people.isEmpty()); }
}
