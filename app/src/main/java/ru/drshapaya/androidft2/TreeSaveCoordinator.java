package ru.drshapaya.androidft2;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Serializes and writes immutable tree copies outside the UI thread.
 */
final class TreeSaveCoordinator {
    interface Listener {
        void onSaveError();
        default void onSaved(TreeState snapshot) {}
    }

    private static final long DEBOUNCE_MILLIS = 500L;

    private final TreeStore store;
    private final Supplier<TreeState> stateProvider;
    private final Runnable beforeSnapshot;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tree-current-writer");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private long requestedRevision = 0L;
    private long dispatchedRevision = 0L;
    private Runnable scheduled;
    private boolean closed = false;

    TreeSaveCoordinator(
        TreeStore store,
        Supplier<TreeState> stateProvider,
        Runnable beforeSnapshot,
        Listener listener
    ) {
        this.store = store;
        this.stateProvider = stateProvider;
        this.beforeSnapshot = beforeSnapshot;
        this.listener = listener;
    }

    void requestDebounced() {
        request(DEBOUNCE_MILLIS);
    }

    void requestImmediate() {
        request(0L);
    }

    private void request(long delayMillis) {
        if (closed) return;
        requestedRevision++;
        if (scheduled != null) main.removeCallbacks(scheduled);
        long revision = requestedRevision;
        scheduled = () -> dispatch(revision);
        main.postDelayed(scheduled, Math.max(0L, delayMillis));
    }

    void flush() {
        if (closed || requestedRevision <= dispatchedRevision) return;
        if (scheduled != null) main.removeCallbacks(scheduled);
        dispatch(requestedRevision);
    }

    void close() {
        if (closed) return;
        flush();
        closed = true;
        writer.shutdown();
    }

    private void dispatch(long revision) {
        if (closed || revision < requestedRevision || revision <= dispatchedRevision) return;
        scheduled = null;
        beforeSnapshot.run();
        TreeState source = stateProvider.get();
        if (source == null) return;
        TreeState snapshot = TreeStateCopier.copy(source);
        dispatchedRevision = revision;
        writer.execute(() -> {
            boolean saved = store.save(snapshot);
            if (listener == null) return;
            if (!saved) main.post(listener::onSaveError);
            else listener.onSaved(snapshot);
        });
    }
}
