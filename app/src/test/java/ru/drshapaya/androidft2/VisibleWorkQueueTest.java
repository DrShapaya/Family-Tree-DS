package ru.drshapaya.androidft2;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class VisibleWorkQueueTest {
    private static void await(CountDownLatch latch) {
        try { assertTrue("Worker did not complete", latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }

    @Test public void coalescesTheSamePhotoAcrossConsumersAndRunningRequests() {
        Object canvas=new Object(), gallery=new Object();
        CountDownLatch started=new CountDownLatch(1), release=new CountDownLatch(1), completed=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger();
        try (VisibleWorkQueue<String> queue=new VisibleWorkQueue<>(2,8,completed::countDown)) {
            Runnable decode=()->{calls.incrementAndGet();started.countDown();await(release);};
            assertTrue(queue.request(canvas,"photo",1,decode));await(started);
            assertTrue(queue.request(gallery,"photo",2,decode));
            assertTrue(queue.request(canvas,"photo",1,decode));
            release.countDown();await(completed);assertEquals(1,calls.get());
        } finally { release.countDown(); }
    }

    @Test public void boundedBacklogPrioritizesVisibleGalleryAndDropsCancelledConsumers() {
        Object owner=new Object(), dismissed=new Object();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(2);
        List<String> order=Collections.synchronizedList(new ArrayList<>());
        try (VisibleWorkQueue<String> queue=new VisibleWorkQueue<>(1,3,()->{})) {
            queue.request(owner,"running",1,()->{started.countDown();await(release);});await(started);
            queue.request(owner,"canvas",1,()->{order.add("canvas");done.countDown();});
            queue.request(owner,"gallery",2,()->{order.add("gallery");done.countDown();});
            queue.request(dismissed,"dismissed",3,()->order.add("dismissed"));
            assertEquals(3,queue.pendingCount());
            assertFalse(queue.request(owner,"overflow",1,()->order.add("overflow")));
            queue.cancel(dismissed);assertEquals(2,queue.pendingCount());
            release.countDown();await(done);assertEquals(Arrays.asList("gallery","canvas"),order);
        } finally { release.countDown(); }
    }

    @Test public void newerFrameDropsOffscreenRequestsButKeepsOtherConsumers() {
        Object canvas=new Object(),gallery=new Object();
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(2);
        Set<String> decoded=Collections.synchronizedSet(new HashSet<>());
        try (VisibleWorkQueue<String> queue=new VisibleWorkQueue<>(1,4,()->{})) {
            queue.request(canvas,"running",1,()->{started.countDown();await(release);});await(started);
            queue.request(canvas,"old-frame",1,()->decoded.add("old-frame"));
            queue.request(canvas,"shared",1,()->{decoded.add("shared");done.countDown();});
            queue.request(gallery,"shared",2,()->fail("Duplicate decoder"));
            queue.begin(canvas);
            queue.request(canvas,"new-frame",1,()->{decoded.add("new-frame");done.countDown();});
            release.countDown();await(done);
            assertEquals(new HashSet<>(Arrays.asList("shared","new-frame")),decoded);
        } finally { release.countDown(); }
    }
}
