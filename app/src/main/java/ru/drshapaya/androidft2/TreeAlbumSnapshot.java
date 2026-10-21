package ru.drshapaya.androidft2;

import java.util.*;

/** Immutable album metadata for undo; originals remain in the content-addressed store. */
final class TreeAlbumSnapshot {
    private final TreeState albums = new TreeState();

    TreeAlbumSnapshot(TreeState source) { copy(source, albums); }

    void apply(TreeState target) { copy(albums, target); }

    int estimatedBytes() {
        long size = 128;
        for (Map<String, List<String>> map : Arrays.asList(albums.photoAlbums, albums.photoAlbumMedia,
                albums.personAlbumMedia, albums.familyAlbumMedia)) {
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                size += 96L + entry.getKey().length() * 2L;
                for (String value : entry.getValue()) size += 40L + value.length() * 2L;
            }
        }
        for (Map.Entry<String, List<PhotoAlbumFolder>> entry : albums.photoAlbumFolders.entrySet()) {
            size += 96L + entry.getKey().length() * 2L;
            for (PhotoAlbumFolder folder : entry.getValue()) {
                size += 128L + 2L * (folder.id.length() + folder.name.length());
                for (String id : folder.personIds) size += 40L + id.length() * 2L;
                for (String id : folder.photoMediaIds) size += 40L + id.length() * 2L;
            }
        }
        for (String value : albums.familyAlbums) size += 40L + value.length() * 2L;
        return (int) Math.min(Integer.MAX_VALUE, size);
    }

    static boolean same(TreeState a, TreeState b) {
        if (!a.photoAlbums.equals(b.photoAlbums) || !a.photoAlbumMedia.equals(b.photoAlbumMedia)
                || !a.personAlbumMedia.equals(b.personAlbumMedia) || !a.familyAlbumMedia.equals(b.familyAlbumMedia)
                || !a.familyAlbums.equals(b.familyAlbums)
                || !a.photoAlbumFolders.keySet().equals(b.photoAlbumFolders.keySet())) return false;
        for (String key : a.photoAlbumFolders.keySet()) {
            List<PhotoAlbumFolder> first = a.photoAlbumFolders.get(key), second = b.photoAlbumFolders.get(key);
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++) {
                PhotoAlbumFolder x = first.get(i), y = second.get(i);
                if (!Objects.equals(x.id, y.id) || !Objects.equals(x.name, y.name)
                        || !x.personIds.equals(y.personIds) || !x.photoMediaIds.equals(y.photoMediaIds)) return false;
            }
        }
        return true;
    }

    private static void copy(TreeState source, TreeState target) {
        copyMap(source.photoAlbums, target.photoAlbums);
        copyMap(source.photoAlbumMedia, target.photoAlbumMedia);
        copyMap(source.personAlbumMedia, target.personAlbumMedia);
        copyMap(source.familyAlbumMedia, target.familyAlbumMedia);
        target.familyAlbums.clear(); target.familyAlbums.addAll(source.familyAlbums);
        target.photoAlbumFolders.clear();
        for (Map.Entry<String, List<PhotoAlbumFolder>> entry : source.photoAlbumFolders.entrySet()) {
            List<PhotoAlbumFolder> folders = new ArrayList<>();
            for (PhotoAlbumFolder original : entry.getValue()) {
                PhotoAlbumFolder folder = new PhotoAlbumFolder();
                folder.id = original.id; folder.name = original.name;
                folder.personIds.addAll(original.personIds); folder.photoMediaIds.addAll(original.photoMediaIds);
                folders.add(folder);
            }
            target.photoAlbumFolders.put(entry.getKey(), folders);
        }
    }

    private static void copyMap(Map<String, List<String>> source, Map<String, List<String>> target) {
        target.clear();
        for (Map.Entry<String, List<String>> entry : source.entrySet())
            target.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
}
