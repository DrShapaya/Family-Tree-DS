package ru.drshapaya.androidft2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

public final class TreePhotoAlbumsTest {
    @Test
    public void stateCopyKeepsPhotoAlbumsIndependent() {
        TreeState source = new TreeState();
        Person first = new Person("p_first");
        first.name = "Иванов Иван";
        source.people.put(first.id, first);
        source.photoAlbums.put("Семейный праздник", new ArrayList<>(Arrays.asList(first.id)));
        source.photoAlbumMedia.put("Семейный праздник", new ArrayList<>(Arrays.asList("photo_album.jpg")));
        source.familyAlbumMedia.put("иванов", new ArrayList<>(Arrays.asList("photo_family.jpg")));
        source.personAlbumMedia.put(first.id, new ArrayList<>(Arrays.asList("photo_person.jpg")));
        PhotoAlbumFolder folder = new PhotoAlbumFolder("Детство");
        folder.personIds.add(first.id);
        folder.photoMediaIds.add("photo_folder.jpg");
        source.photoAlbumFolders.put("Семейный праздник", new ArrayList<>(Arrays.asList(folder)));
        source.familyAlbums.add("иванов");

        TreeState copied = TreeStateCopier.copy(source);

        assertEquals(Arrays.asList(first.id), copied.photoAlbums.get("Семейный праздник"));
        assertEquals(Arrays.asList("photo_album.jpg"), copied.photoAlbumMedia.get("Семейный праздник"));
        assertEquals(Arrays.asList("photo_family.jpg"), copied.familyAlbumMedia.get("иванов"));
        assertEquals(Arrays.asList("photo_person.jpg"), copied.personAlbumMedia.get(first.id));
        assertEquals("Детство", copied.photoAlbumFolders.get("Семейный праздник").get(0).name);
        assertEquals(Arrays.asList("photo_folder.jpg"), copied.photoAlbumFolders.get("Семейный праздник").get(0).photoMediaIds);
        assertTrue(copied.familyAlbums.contains("иванов"));
        copied.photoAlbums.get("Семейный праздник").clear();
        copied.photoAlbumMedia.get("Семейный праздник").clear();
        copied.photoAlbumFolders.get("Семейный праздник").get(0).photoMediaIds.clear();
        assertEquals(1, source.photoAlbums.get("Семейный праздник").size());
        assertEquals(1, source.photoAlbumMedia.get("Семейный праздник").size());
        assertEquals(1, source.photoAlbumFolders.get("Семейный праздник").get(0).photoMediaIds.size());
    }

    @Test
    public void deletingPersonRemovesAlbumReferencesButKeepsAlbum() {
        TreeState state = new TreeState();
        Person person = new Person("p_first");
        state.people.put(person.id, person);
        state.photoAlbums.put("Альбом", new ArrayList<>(Arrays.asList(person.id)));
        state.personAlbumMedia.put(person.id, new ArrayList<>(Arrays.asList("photo_person.jpg")));
        PhotoAlbumFolder folder = new PhotoAlbumFolder("Папка");
        folder.personIds.add(person.id);
        state.photoAlbumFolders.put("Альбом", new ArrayList<>(Arrays.asList(folder)));

        state.deletePerson(person.id);

        assertTrue(state.photoAlbums.containsKey("Альбом"));
        assertTrue(state.photoAlbums.get("Альбом").isEmpty());
        assertTrue(state.photoAlbumFolders.get("Альбом").get(0).personIds.isEmpty());
        assertTrue(!state.personAlbumMedia.containsKey(person.id));
    }
}
