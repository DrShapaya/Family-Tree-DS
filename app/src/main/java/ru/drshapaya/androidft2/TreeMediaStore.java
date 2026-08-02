package ru.drshapaya.androidft2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Base64InputStream;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * Content-addressed storage for original photos and memory attachments.
 *
 * <p>The tree JSON stores only the returned media id. Files are shared between
 * current state, undo commands and local versions, so replacing the JSON never
 * copies the original binary payload.</p>
 */
final class TreeMediaStore {
    static final long MAX_PHOTO_BYTES = 30L * 1024L * 1024L;
    static final long MAX_ATTACHMENT_BYTES = 100L * 1024L * 1024L;
    static final long MAX_VIDEO_BYTES = 500L * 1024L * 1024L;
    static final long MIN_FREE_SPACE_BYTES = 64L * 1024L * 1024L;

    private static final String ROOT_DIRECTORY = "androidft2-media";
    private static final String PHOTOS_DIRECTORY = "photos";
    private static final String ATTACHMENTS_DIRECTORY = "attachments";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private final File root;
    private final File photos;
    private final File attachments;
    private final File staging;

    TreeMediaStore(Context context) {
        root = new File(context.getFilesDir(), ROOT_DIRECTORY);
        photos = new File(root, PHOTOS_DIRECTORY);
        attachments = new File(root, ATTACHMENTS_DIRECTORY);
        staging = new File(root, "staging");
    }

    StoredMedia importPhoto(InputStream input, String filename, String mimeType) throws Exception {
        return importStream(input, true, filename, mimeType, MAX_PHOTO_BYTES);
    }

    StoredMedia importAttachment(
        InputStream input,
        String filename,
        String mimeType
    ) throws Exception {
        long limit = mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")
            ? MAX_VIDEO_BYTES
            : MAX_ATTACHMENT_BYTES;
        return importStream(input, false, filename, mimeType, limit);
    }

    StoredMedia importDataUrl(String dataUrl, boolean photo, String filename) throws Exception {
        if (dataUrl == null) throw new IOException("Пустые данные медиа");
        int comma = dataUrl.indexOf(',');
        if (comma < 0 || comma + 1 >= dataUrl.length()) {
            throw new IOException("Повреждённый Data URL");
        }
        String header = dataUrl.substring(0, comma).trim();
        String lower = header.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        int semicolon = lower.indexOf(';');
        if (colon != 4 || semicolon <= colon + 1 || !lower.contains(";base64")) {
            throw new IOException("Неподдерживаемый Data URL");
        }
        String mimeType = header.substring(colon + 1, semicolon).trim();
        long limit = photo
            ? MAX_PHOTO_BYTES
            : mimeType.toLowerCase(Locale.ROOT).startsWith("video/")
                ? MAX_VIDEO_BYTES
                : MAX_ATTACHMENT_BYTES;
        try (InputStream encoded = new StringAsciiInputStream(dataUrl, comma + 1);
             InputStream decoded = new Base64InputStream(encoded, Base64.DEFAULT)) {
            return importStream(decoded, photo, filename, mimeType, limit);
        }
    }

    InputStream open(String mediaId) throws IOException {
        File file = resolve(mediaId);
        if (file == null || !file.isFile()) throw new IOException("Медиафайл не найден");
        return new FileInputStream(file);
    }

    File file(String mediaId) {
        return resolve(mediaId);
    }

    boolean exists(String mediaId) {
        File file = resolve(mediaId);
        return file != null && file.isFile();
    }

    long size(String mediaId) {
        File file = resolve(mediaId);
        return file == null || !file.isFile() ? 0L : file.length();
    }

    Bitmap decodeBitmap(String mediaId, int targetSize) {
        File file = resolve(mediaId);
        if (file == null || !file.isFile()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sampleSize(
            bounds.outWidth,
            bounds.outHeight,
            Math.max(64, targetSize));
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    void copyTo(String mediaId, OutputStream output) throws Exception {
        try (InputStream input = open(mediaId)) {
            copy(input, output, Long.MAX_VALUE);
        }
    }

    File createStagingFile(String prefix, String extension) throws IOException {
        ensureDirectory(staging);
        ensureFreeSpace(MIN_FREE_SPACE_BYTES);
        String safePrefix = prefix == null ? "stage" : prefix.replaceAll("[^A-Za-z0-9_-]", "");
        if (safePrefix.isEmpty()) safePrefix = "stage";
        String safeExtension = extension == null ? "" : extension.replaceAll("[^A-Za-z0-9.]", "");
        return new File(
            staging,
            safePrefix + "-" + UUID.randomUUID().toString().replace("-", "") + safeExtension);
    }

    void ensureFreeSpace(long additionalBytes) throws IOException {
        File probe = staging.exists() ? staging : root;
        long usable = probe.getUsableSpace();
        long required = Math.max(0L, additionalBytes) + MIN_FREE_SPACE_BYTES;
        if (usable > 0L && usable < required) {
            throw new IOException(
                "Недостаточно свободного места: требуется ещё "
                    + humanSize(required));
        }
    }

    private StoredMedia importStream(
        InputStream input,
        boolean photo,
        String filename,
        String mimeType,
        long maxBytes
    ) throws Exception {
        if (input == null) throw new IOException("Файл не открыт");
        File destinationDirectory = photo ? photos : attachments;
        ensureDirectory(destinationDirectory);
        ensureDirectory(staging);
        ensureFreeSpace(Math.min(maxBytes, 8L * 1024L * 1024L));
        File temporary = new File(
            staging,
            "media-" + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0L;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > maxBytes) {
                    throw new IOException(
                        "Размер файла превышает допустимые " + humanSize(maxBytes));
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                if ((size & ((8L * 1024L * 1024L) - 1L)) < read) {
                    ensureFreeSpace(8L * 1024L * 1024L);
                }
            }
            output.getFD().sync();
        } catch (Exception error) {
            temporary.delete();
            throw error;
        }

        String extension = safeExtension(filename, mimeType);
        String mediaId = (photo ? "photo_" : "attachment_")
            + hex(digest.digest())
            + (extension.isEmpty() ? "" : "." + extension);
        File destination = new File(destinationDirectory, mediaId);
        if (destination.exists()) {
            temporary.delete();
        } else if (!temporary.renameTo(destination)) {
            try (InputStream source = new FileInputStream(temporary);
                 FileOutputStream output = new FileOutputStream(destination)) {
                copy(source, output, maxBytes);
                output.getFD().sync();
            } catch (Exception error) {
                destination.delete();
                throw error;
            } finally {
                temporary.delete();
            }
        }
        return new StoredMedia(mediaId, size, normalizeMime(mimeType, extension));
    }

    private File resolve(String mediaId) {
        if (mediaId == null || !mediaId.matches("[A-Za-z0-9._-]{1,180}")) return null;
        File directory;
        if (mediaId.startsWith("photo_")) directory = photos;
        else if (mediaId.startsWith("attachment_")) directory = attachments;
        else return null;
        File file = new File(directory, mediaId);
        try {
            String parent = directory.getCanonicalPath();
            return parent.equals(file.getCanonicalFile().getParent()) ? file : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static long copy(InputStream input, OutputStream output, long maxBytes) throws Exception {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Файл превышает допустимый размер");
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Не удалось создать каталог медиа");
        }
    }

    private static int sampleSize(int width, int height, int targetSize) {
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= targetSize) sample *= 2;
        return Math.max(1, sample);
    }

    private static String safeExtension(String filename, String mimeType) {
        String extension = "";
        String name = filename == null ? "" : filename.trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < name.length()) {
            extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        extension = extension.replaceAll("[^a-z0-9]", "");
        if (extension.length() > 10) extension = "";
        if (extension.isEmpty() && mimeType != null) {
            String mapped = MimeTypeMap.getSingleton().getExtensionFromMimeType(
                mimeType.toLowerCase(Locale.ROOT));
            if (mapped != null) extension = mapped.replaceAll("[^a-z0-9]", "");
        }
        return extension;
    }

    private static String normalizeMime(String mimeType, String extension) {
        String value = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        if (!value.isEmpty()) return value;
        String mapped = extension == null || extension.isEmpty()
            ? null
            : MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mapped == null ? "application/octet-stream" : mapped;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02x", value & 0xff));
        return out.toString();
    }

    static String humanSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.1f GB", bytes / (1024d * 1024d * 1024d));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.getDefault(), "%.0f MB", bytes / (1024d * 1024d));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.getDefault(), "%.0f KB", bytes / 1024d);
        }
        return bytes + " B";
    }

    static final class StoredMedia {
        final String id;
        final long size;
        final String mimeType;

        StoredMedia(String id, long size, String mimeType) {
            this.id = id;
            this.size = size;
            this.mimeType = mimeType;
        }
    }

    private static final class StringAsciiInputStream extends InputStream {
        private final String value;
        private int index;

        StringAsciiInputStream(String value, int start) {
            this.value = value == null ? "" : value;
            index = Math.max(0, start);
        }

        @Override
        public int read() {
            if (index >= value.length()) return -1;
            return value.charAt(index++) & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (index >= value.length()) return -1;
            int count = Math.min(length, value.length() - index);
            for (int i = 0; i < count; i++) {
                buffer[offset + i] = (byte) (value.charAt(index + i) & 0xff);
            }
            index += count;
            return count;
        }
    }
}
