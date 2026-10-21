package ru.drshapaya.androidft2;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.test.InstrumentationTestCase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real media imports and listener delivery with all network responses supplied locally. */
public final class OnlineMediaOptimizationTest extends InstrumentationTestCase {
    public void testSuccessfulDownloadNotifiesGalleryAndDoesNotRewriteUnusedMediaPreferences() throws Exception {
        verifyBatch(false);
    }

    public void testPartialDownloadStillNotifiesGalleryWhenNextFileFails() throws Exception {
        verifyBatch(true);
    }

    private void verifyBatch(boolean failSecond) throws Exception {
        Context base = getInstrumentation().getTargetContext();
        String namespace = "online-media-test-" + UUID.randomUUID();
        File directory = new File(base.getCacheDir(), namespace);
        assertTrue(directory.mkdirs());
        Context context = new ContextWrapper(base) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return base.getSharedPreferences(namespace + "-" + name, mode);
            }
        };
        TreeStore store = new TreeStore(context);
        Map<String, byte[]> media = new LinkedHashMap<>();
        for (int color : new int[]{Color.RED, Color.BLUE}) {
            Bitmap image = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            image.eraseColor(color);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
            image.recycle();
            byte[] bytes = output.toByteArray();
            StringBuilder digest = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                digest.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            }
            media.put("photo_" + digest + ".png", bytes);
        }
        JSONArray assets = new JSONArray();
        Map<Long, byte[]> downloads = new LinkedHashMap<>();
        long id = 11;
        for (Map.Entry<String, byte[]> item : media.entrySet()) {
            assets.put(new JSONObject().put("id", id).put("name", item.getKey())
                .put("size", item.getValue().length).put("state", "uploaded"));
            downloads.put(id++, item.getValue());
        }
        int[] attempts = {0};
        GitHubApi transport = new GitHubApi(null, uri -> {
            String path = uri.getPath();
            if (path.contains("/releases/tags/")) return response(uri, 200, "{\"id\":7}".getBytes());
            if (path.endsWith("/releases/7/assets")) return response(uri, 200, assets.toString().getBytes());
            int separator = path.lastIndexOf('/');
            if (path.contains("/releases/assets/")) {
                byte[] bytes = downloads.get(Long.parseLong(path.substring(separator + 1)));
                assertNotNull(bytes);
                if (++attempts[0] == 2 && failSecond) {
                    return response(uri, 403, "{\"message\":\"Fixture denied second file\"}".getBytes());
                }
                return response(uri, 200, bytes);
            }
            throw new AssertionError("Unexpected network request: " + uri);
        });
        CountDownLatch notified = new CountDownLatch(1);
        OnlineTreeManager manager = new OnlineTreeManager(context, store, new OnlineTreeManager.Listener() {
            @Override public void onRemoteTree(TreeState state, String message) { }
            @Override public void onMediaChanged() { notified.countDown(); }
            @Override public void onEditingPermissionChanged(boolean allowed) { }
            @Override public void onStatusChanged() { }
            @Override public void onMessage(String message) { }
        }, transport);
        try {
            TreeState tree = new TreeState();
            for (String mediaId : media.keySet()) tree.addPerson(mediaId, 0, 0).photoMediaId = mediaId;
            Method sync = OnlineTreeManager.class.getDeclaredMethod("syncMediaDown", TreeState.class,
                String.class, String.class, String.class);
            sync.setAccessible(true);
            try {
                assertEquals(2, sync.invoke(manager, tree, "fixture-owner", "fixture-repo", "fixture-tree-123"));
                assertFalse("Expected second download to fail", failSecond);
            } catch (InvocationTargetException error) {
                if (!failSecond) throw error;
                assertTrue(error.getCause() instanceof GitHubApi.ApiException);
                assertEquals(403, ((GitHubApi.ApiException) error.getCause()).status);
            }
            assertTrue("Downloaded photos never refreshed the gallery", notified.await(5, TimeUnit.SECONDS));
            int present = 0;
            for (String mediaId : media.keySet()) if (store.mediaStore().exists(mediaId)) present++;
            assertEquals(failSecond ? 1 : 2, present);
            assertFalse(context.getSharedPreferences("androidft-online-v1", 0).contains("remoteMediaIds"));
        } finally { manager.close(); }
    }

    private static HttpURLConnection response(URI uri, int status, byte[] bytes) throws Exception {
        return new HttpURLConnection(uri.toURL()) {
            @Override public void connect() { }
            @Override public void disconnect() { }
            @Override public boolean usingProxy() { return false; }
            @Override public int getResponseCode() { return status; }
            @Override public long getContentLengthLong() { return bytes.length; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
            @Override public InputStream getErrorStream() { return new ByteArrayInputStream(bytes); }
        };
    }
}
