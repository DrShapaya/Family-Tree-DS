package ru.drshapaya.androidft2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class PhotoAlbumFolder {
    String id = "folder_" + UUID.randomUUID().toString().replace("-", "");
    String name = "Новая папка";
    final List<String> personIds = new ArrayList<>();
    final List<String> photoMediaIds = new ArrayList<>();

    PhotoAlbumFolder() {
    }

    PhotoAlbumFolder(String name) {
        if (name != null && !name.trim().isEmpty()) this.name = name.trim();
    }
}
