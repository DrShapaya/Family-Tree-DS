package ru.drshapaya.androidft2;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One thumbnail budget and bounded decode queue shared by canvas and photo screens. */
final class ThumbnailLoader {
    static final ThumbnailLoader SHARED = new ThumbnailLoader();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> inlineTokens = new WeakHashMap<>();
    private long inlineSequence;
    private final AtomicBoolean notificationPending = new AtomicBoolean();
    private final Map<Object, WeakReference<Runnable>> listeners = new WeakHashMap<>();
    private final LruCache<Key, Bitmap> cache = new LruCache<>(
        (int)Math.max(8L << 20, Math.min(32L << 20, Runtime.getRuntime().maxMemory() / 8))) {
        @Override protected int sizeOf(Key key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Set<Key> failures = Collections.synchronizedSet(new HashSet<>());
    private final VisibleWorkQueue<Key> queue = new VisibleWorkQueue<>(2, 128, this::notifyConsumers);

    void begin(Object owner) { queue.begin(owner); }

    /** Retains lookup keys, never the bitmap: eviction and newly arrived media remain observable. */
    static final class Request {
        private String namespace,media,inline;
        private int size;
        private Key key,failureKey;
    }

    Bitmap get(Object owner, Runnable ready, TreeMediaStore store, String mediaId,
               String inline, int targetSize, int priority) {
        return get(owner,ready,store,mediaId,inline,targetSize,priority,null);
    }

    Bitmap get(Object owner, Runnable ready, TreeMediaStore store, String mediaId,
               String inline, int targetSize, int priority,Request request) {
        String media = mediaId == null ? "" : mediaId;
        String legacy = inline == null ? "" : inline;
        if (media.isEmpty() && legacy.isEmpty()) return null;
        String namespace = media.isEmpty() ? "inline" : store == null ? "missing" : store.thumbnailNamespace();
        int size = Math.max(16, targetSize);
        Key key,failureKey;
        if(request!=null &&request.key!=null &&request.size==size &&namespace.equals(request.namespace)
                &&media.equals(request.media) &&legacy.equals(request.inline)) {
            key=request.key;failureKey=request.failureKey;
        } else {
        String source = media;
        if (media.isEmpty()) {
            synchronized (inlineTokens) {
                source=inlineTokens.get(legacy);
                if (source==null) { source=Long.toString(++inlineSequence); inlineTokens.put(legacy,source); }
            }
        }
        key = new Key(namespace, source, size);
        failureKey = new Key(namespace, source, 0);
        if(request!=null) {
            request.namespace=namespace;request.media=media;request.inline=legacy;request.size=size;
            request.key=key;request.failureKey=failureKey;
        }
        }
        Bitmap cached = cache.get(key);
        if (cached != null) return cached;
        synchronized (listeners) {
            WeakReference<Runnable> previous=listeners.get(owner);
            if(previous==null ||previous.get()!=ready)listeners.put(owner,new WeakReference<>(ready));
        }
        if (failures.contains(failureKey) || failures.contains(key)) return null;
        queue.request(owner, key, priority, () -> {
            if (cache.get(key) != null) return;
            Bitmap bitmap = null;
            try {
                if (!media.isEmpty() && store != null) bitmap = store.decodeBitmap(media, size);
                else {
                    int comma = legacy.indexOf(',');
                    String encoded = comma >= 0 ? legacy.substring(comma + 1) : legacy;
                    bitmap = MainActivity.decodeBitmapBytes(Base64.decode(encoded, Base64.DEFAULT), size);
                }
                if (bitmap != null) { cache.put(key, bitmap); failures.remove(failureKey); }
                else failures.add(failureKey);
            } catch (OutOfMemoryError error) {
                cache.trimToSize(cache.maxSize()/2);
                failures.add(key);
            } catch (RuntimeException error) {
                // No retry storm for broken/missing files. Media arrival explicitly clears failures.
                failures.add(failureKey);
            }
        });
        return null;
    }

    void cancel(Object owner) {
        queue.cancel(owner);
        synchronized (listeners) { listeners.remove(owner); }
    }

    void clearFailures() { failures.clear(); notifyConsumers(); }

    void trim(boolean all) {
        if (all) { cache.evictAll(); failures.clear(); } else cache.trimToSize(cache.maxSize() / 2);
    }

    private void notifyConsumers() {
        if (!notificationPending.compareAndSet(false, true)) return;
        main.postDelayed(() -> {
            notificationPending.set(false);
            List<Runnable> callbacks = new ArrayList<>();
            synchronized (listeners) {
                listeners.values().removeIf(reference -> reference.get() == null);
                for (WeakReference<Runnable> reference : listeners.values()) {
                    Runnable callback = reference.get();
                    if (callback != null) callbacks.add(callback);
                }
            }
            // A consumer refused by the bounded queue retries on the next frame.
            for (Runnable callback : callbacks) callback.run();
        }, 16L);
    }
    private static final class Key {
        final String namespace, source;
        final int size, hash;
        Key(String namespace, String source, int size) {
            this.namespace=namespace; this.source=source; this.size=size;
            hash=31*(31*namespace.hashCode()+source.hashCode())+size;
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object object) {
            if (!(object instanceof Key)) return false;
            Key other=(Key)object;
            return size==other.size && namespace.equals(other.namespace) && source.equals(other.source);
        }
    }

}
