package ru.drshapaya.androidft2;

import android.webkit.MimeTypeMap;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Streaming reader/writer for the portable AndroidFT .ftree container.
 *
 * <p>The package is a regular ZIP file containing manifest.json, tree.json and
 * content-addressed media files. The tree remains small because binary data is
 * never embedded into JSON.</p>
 */
final class TreePackageIO {
    static final String MIME_TYPE = "application/vnd.drshapaya.familytree+zip";
    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String TREE_ENTRY = "tree.json";
    private static final String PHOTO_PREFIX = "media/photos/";
    private static final String ATTACHMENT_PREFIX = "media/attachments/";
    private static final long MAX_TREE_BYTES = 25L * 1024L * 1024L;
    private static final long MAX_PACKAGE_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_PACKAGE_MEDIA_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 640;
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private TreePackageIO() {
    }

    static void write(
        TreeState state,
        TreeStore store,
        OutputStream destination,
        String mode
    ) throws Exception {
        if (state == null) throw new IOException("Дерево не загружено");
        Set<String> mediaIds = referencedMedia(state);
        JSONObject manifest = new JSONObject()
            .put("format", "ru.drshapaya.familytree")
            .put("containerVersion", 2)
            .put("appVersion", MainActivity.VERSION_NAME)
            .put("mode", mode == null || mode.isEmpty() ? "copy" : mode)
            .put("createdAt", System.currentTimeMillis())
            .put("tree", TREE_ENTRY)
            .put("mediaCount", mediaIds.size());

        try (ZipOutputStream zip = new ZipOutputStream(destination)) {
            zip.setLevel(1);
            writeTextEntry(zip, MANIFEST_ENTRY, manifest.toString());
            writeTextEntry(zip, TREE_ENTRY, store.exportText(state));
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            for (String mediaId : mediaIds) {
                if (!store.mediaStore().exists(mediaId)) continue;
                String prefix = mediaId.startsWith("photo_") ? PHOTO_PREFIX : ATTACHMENT_PREFIX;
                zip.putNextEntry(new ZipEntry(prefix + mediaId));
                try (InputStream input = store.mediaStore().open(mediaId)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    static TreeState read(InputStream source, TreeStore store) throws Exception {
        if (source == null) throw new IOException("Файл не открыт");
        File stagedPackage = store.mediaStore().createStagingFile("tree-import", ".ftree");
        try {
            stagePackage(source, stagedPackage, store.mediaStore());
            try (InputStream stagedInput = new FileInputStream(stagedPackage)) {
                return readZip(stagedInput, store);
            }
        } finally {
            stagedPackage.delete();
        }
    }

    private static TreeState readZip(InputStream source, TreeStore store) throws Exception {
        String treeJson = null;
        boolean manifestSeen = false;
        int entryCount = 0;
        long mediaBytes = 0L;
        Map<String, String> importedIds = new LinkedHashMap<>();

        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) throw new IOException("Слишком много файлов внутри .ftree");
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                String name = safeEntryName(entry.getName());
                if (MANIFEST_ENTRY.equals(name)) {
                    JSONObject manifest = new JSONObject(readUtf8(zip, 256L * 1024L));
                    if (!"ru.drshapaya.familytree".equals(manifest.optString("format"))) {
                        throw new IOException("Неизвестный формат .ftree");
                    }
                    int version = manifest.optInt("containerVersion", 0);
                    if (version < 1 || version > 2) {
                        throw new IOException("Версия .ftree не поддерживается");
                    }
                    manifestSeen = true;
                } else if (TREE_ENTRY.equals(name)) {
                    treeJson = readUtf8(zip, MAX_TREE_BYTES);
                } else if (name.startsWith(PHOTO_PREFIX) || name.startsWith(ATTACHMENT_PREFIX)) {
                    boolean photo = name.startsWith(PHOTO_PREFIX);
                    String originalId = name.substring(
                        photo ? PHOTO_PREFIX.length() : ATTACHMENT_PREFIX.length());
                    if (!isMediaId(originalId, photo)) {
                        throw new IOException("Недопустимое имя медиафайла");
                    }
                    String mime = mimeFor(originalId);
                    TreeMediaStore.StoredMedia stored = photo
                        ? store.mediaStore().importPhoto(zip, originalId, mime)
                        : store.mediaStore().importAttachment(zip, originalId, mime);
                    mediaBytes += stored.size;
                    if (mediaBytes > MAX_PACKAGE_MEDIA_BYTES) {
                        throw new IOException("Суммарный размер медиа в .ftree превышает 1 ГБ");
                    }
                    importedIds.put(originalId, stored.id);
                } else {
                    drain(zip, 2L * 1024L * 1024L);
                }
                zip.closeEntry();
            }
        }
        if (!manifestSeen) throw new IOException("В .ftree отсутствует manifest.json");
        if (treeJson == null || treeJson.trim().isEmpty()) {
            throw new IOException("В .ftree отсутствует tree.json");
        }
        TreeState state = store.parse(treeJson);
        remapMedia(state, importedIds);
        return state;
    }

    private static void stagePackage(
        InputStream source,
        File destination,
        TreeMediaStore mediaStore
    ) throws Exception {
        long total = 0L;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        try (FileOutputStream output = new FileOutputStream(destination)) {
            int read;
            while ((read = source.read(buffer)) != -1) {
                total += read;
                if (total > MAX_PACKAGE_BYTES) {
                    throw new IOException("Размер .ftree превышает 1 ГБ");
                }
                output.write(buffer, 0, read);
                if ((total & ((8L * 1024L * 1024L) - 1L)) < read) {
                    mediaStore.ensureFreeSpace(8L * 1024L * 1024L);
                }
            }
            output.getFD().sync();
        } catch (Exception error) {
            destination.delete();
            throw error;
        }
    }

    static boolean hasZipSignature(byte[] header, int count) {
        return count >= 4
            && header[0] == 'P'
            && header[1] == 'K'
            && ((header[2] == 3 && header[3] == 4)
                || (header[2] == 5 && header[3] == 6)
                || (header[2] == 7 && header[3] == 8));
    }

    private static Set<String> referencedMedia(TreeState state) {
        Set<String> ids = new LinkedHashSet<>();
        for (Person person : state.people.values()) {
            if (person == null) continue;
            if (isMediaId(person.photoMediaId, true)) ids.add(person.photoMediaId);
            for (Memory memory : person.memories) {
                if (memory == null) continue;
                for (MemoryAttachment attachment : memory.attachments) {
                    if (attachment != null && isMediaId(attachment.mediaId, false)) {
                        ids.add(attachment.mediaId);
                    }
                }
            }
        }
        return ids;
    }

    private static void remapMedia(TreeState state, Map<String, String> importedIds) {
        if (state == null || importedIds.isEmpty()) return;
        for (Person person : state.people.values()) {
            if (person == null) continue;
            String remappedPhoto = importedIds.get(person.photoMediaId);
            if (remappedPhoto != null) person.photoMediaId = remappedPhoto;
            for (Memory memory : person.memories) {
                if (memory == null) continue;
                for (MemoryAttachment attachment : memory.attachments) {
                    if (attachment == null) continue;
                    String remappedAttachment = importedIds.get(attachment.mediaId);
                    if (remappedAttachment != null) {
                        attachment.mediaId = remappedAttachment;
                    }
                }
            }
        }
    }

    private static void writeTextEntry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String readUtf8(InputStream input, long limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copyLimited(input, output, limit);
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private static void drain(InputStream input, long limit) throws Exception {
        copyLimited(input, null, limit);
    }

    private static long copyLimited(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Файл внутри .ftree слишком большой");
            if (output != null) output.write(buffer, 0, read);
        }
        return total;
    }

    private static String safeEntryName(String value) throws IOException {
        String name = value == null ? "" : value.replace('\\', '/');
        if (name.isEmpty()
            || name.startsWith("/")
            || name.contains("../")
            || name.contains("/..")
            || name.indexOf('\0') >= 0) {
            throw new IOException("Недопустимый путь внутри .ftree");
        }
        return name;
    }

    private static boolean isMediaId(String value, boolean photo) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,180}")) return false;
        return value.startsWith(photo ? "photo_" : "attachment_");
    }

    private static String mimeFor(String filename) {
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        String mime = extension.isEmpty()
            ? null
            : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }
}
