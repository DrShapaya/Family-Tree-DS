package ru.drshapaya.androidft2;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
    private volatile long savedRevision = 0L;
    private volatile long failedRevision = -1L;
    private final AtomicReference<PendingSave> pending = new AtomicReference<>();
    private final AtomicBoolean writing = new AtomicBoolean();
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
        if (closed || requestedRevision <= savedRevision) return;
        if (scheduled != null) main.removeCallbacks(scheduled);
        dispatch(requestedRevision);
    }

    void close() {
        if (closed) return;
        flush();
        closed = true;
        writer.execute(writer::shutdown);
    }

    private void dispatch(long revision) {
        if (closed || revision < requestedRevision || revision <= savedRevision
                || (revision <= dispatchedRevision && writing.get() && revision > failedRevision)) return;
        scheduled = null;
        beforeSnapshot.run();
        TreeState source = stateProvider.get();
        if (source == null) return;
        TreeState snapshot = TreeStateCopier.copy(source);
        dispatchedRevision = revision;
        pending.set(new PendingSave(revision, snapshot));
        startWriter();
    }

    private void startWriter() {
        if (!writing.compareAndSet(false, true)) return;
        writer.execute(() -> {
            try {
                PendingSave next;
                while ((next = pending.getAndSet(null)) != null) {
                    if (store.save(next.snapshot)) {
                        savedRevision = next.revision;
                        if (listener != null) listener.onSaved(next.snapshot);
                    } else {
                        failedRevision = next.revision;
                        if (listener != null) main.post(listener::onSaveError);
                    }
                }
            } finally {
                writing.set(false);
                if (pending.get() != null) startWriter();
            }
        });
    }

    private static final class PendingSave {
        final long revision;
        final TreeState snapshot;
        PendingSave(long revision, TreeState snapshot) { this.revision = revision; this.snapshot = snapshot; }
    }

    void saveThen(java.util.function.Consumer<Boolean> completion) {
        if (closed) { completion.accept(false); return; }
        requestImmediate(); flush();
        long revision=requestedRevision;
        writer.execute(new Runnable() {
            @Override public void run() {
                if (writing.get() || pending.get()!=null) { writer.execute(this); return; }
                boolean saved=savedRevision>=revision;
                main.post(() -> { if (!closed) completion.accept(saved); });
            }
        });
    }
}
