package ru.drshapaya.androidft2;

import java.util.*;

/** Bounded, coalescing work for the latest visible frame of each consumer. */
final class VisibleWorkQueue<K> implements AutoCloseable {
    private final int capacity;
    private final Map<K, Work<K>> pending = new LinkedHashMap<>();
    private final Set<K> running = new HashSet<>();
    private final Map<Object, Long> generations = new WeakHashMap<>();
    private final List<Thread> workers = new ArrayList<>();
    private final Runnable completed;
    private long sequence;
    private boolean closed;
    private boolean pruneNeeded;

    VisibleWorkQueue(int threads, int capacity, Runnable completed) {
        this.capacity = Math.max(1, capacity);
        this.completed = completed;
        for (int i = 0; i < Math.max(1, threads); i++) {
            Thread worker = new Thread(this::run, "visible-thumbnail-" + i);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }

    synchronized void begin(Object owner) {
        generations.put(owner, generations.getOrDefault(owner, 0L) + 1L);
        pruneNeeded = true;
    }

    synchronized boolean request(Object owner, K key, int priority, Runnable job) {
        if (closed) return false;
        long generation = generations.computeIfAbsent(owner, ignored -> 1L);
        if (running.contains(key)) return true;
        Work<K> work = pending.get(key);
        if (work == null) {
            if (pruneNeeded) {
                pending.values().removeIf(item -> !current(item));
                pruneNeeded=false;
            }
            if (pending.size() >= capacity) return false;
            work = new Work<>(key, job);
            pending.put(key, work);
        }
        work.owners.put(owner, generation);
        work.priority = Math.max(work.priority, priority);
        work.sequence = ++sequence;
        notifyAll();
        return true;
    }

    synchronized void cancel(Object owner) {
        generations.remove(owner);
        pending.values().removeIf(item -> !current(item));
    }

    synchronized int pendingCount() { return pending.size(); }

    private boolean current(Work<K> work) {
        for (Map.Entry<Object, Long> entry : work.owners.entrySet()) {
            if (entry.getValue().equals(generations.get(entry.getKey()))) return true;
        }
        return false;
    }

    private synchronized Work<K> take() throws InterruptedException {
        while (!closed) {
            pending.values().removeIf(item -> !current(item));
            Work<K> best = null;
            for (Work<K> candidate : pending.values()) {
                if (best == null || candidate.priority > best.priority
                    || candidate.priority == best.priority && candidate.sequence > best.sequence) best = candidate;
            }
            if (best != null) {
                pending.remove(best.key);
                running.add(best.key);
                return best;
            }
            wait();
        }
        return null;
    }

    private void run() {
        while (true) {
            Work<K> work;
            try { work = take(); }
            catch (InterruptedException interrupted) { return; }
            if (work == null) return;
            try { work.job.run(); }
            finally {
                synchronized (this) { running.remove(work.key); }
                if (completed != null) completed.run();
            }
        }
    }

    @Override public synchronized void close() {
        closed = true;
        pending.clear(); generations.clear();
        for (Thread worker : workers) worker.interrupt();
        notifyAll();
    }

    private static final class Work<K> {
        final K key;
        final Runnable job;
        final Map<Object, Long> owners = new WeakHashMap<>();
        int priority;
        long sequence;
        Work(K key, Runnable job) { this.key = key; this.job = job; }
    }
}
