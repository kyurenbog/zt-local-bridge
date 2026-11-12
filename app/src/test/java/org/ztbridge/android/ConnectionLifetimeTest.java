package org.ztbridge.android;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ConnectionLifetimeTest {
    @Test public void abandonedHalfClosedReadReturnsCapacityWithoutRestart() throws Exception {
        Semaphore capacity = new Semaphore(1); capacity.acquire();
        CountDownLatch blockedRead = new CountDownLatch(1);
        ConnectionLifetime lifetime = new ConnectionLifetime(0, blockedRead::countDown, capacity::release);
        lifetime.connected(0);
        Thread reader = new Thread(() -> {
            try { if (blockedRead.await(2, TimeUnit.SECONDS)) lifetime.finish(1, 15000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        reader.start();
        lifetime.finish(0, 0); // Client sent FIN, upstream read never responds.
        assertEquals(0, capacity.availablePermits());
        assertFalse(lifetime.reap(14999));
        assertTrue(lifetime.reap(15000));
        reader.join(2000); assertFalse(reader.isAlive());
        assertEquals(1, capacity.availablePermits());
        lifetime.cancel(); lifetime.finish(1, 16000); lifetime.reap(300000);
        assertEquals(1, capacity.availablePermits());
    }

    @Test public void halfClosedRequestStillAllowsLongStreamingResponse() {
        AtomicInteger cancellations = new AtomicInteger(), releases = new AtomicInteger();
        ConnectionLifetime lifetime = new ConnectionLifetime(0, cancellations::incrementAndGet, releases::incrementAndGet);
        lifetime.connected(0); lifetime.finish(0, 0);
        for (int i = 1; i <= 30; i++) {
            lifetime.activity(i * 10000L);
            assertFalse(lifetime.reap(i * 10000L + 9000));
        }
        assertEquals(0, cancellations.get()); assertEquals(0, releases.get());
        lifetime.finish(1, 310000);
        assertEquals(1, releases.get());
    }

    @Test public void cancelledDescriptorIsRetainedUntilBothWorkersExit() {
        AtomicInteger cancellations = new AtomicInteger(), releases = new AtomicInteger();
        ConnectionLifetime lifetime = new ConnectionLifetime(0, cancellations::incrementAndGet, releases::incrementAndGet);
        lifetime.connected(0);
        assertFalse(lifetime.reap(119999)); assertTrue(lifetime.reap(120000));
        assertEquals(1, cancellations.get()); assertEquals(0, releases.get());
        lifetime.finish(1, 120000); assertEquals(0, releases.get());
        lifetime.finish(0, 120001); assertEquals(1, releases.get());
    }

    @Test public void repeatedLoadCyclesNeverLoseOrDuplicateSlots() throws Exception {
        Semaphore slots = new Semaphore(256);
        for (int batch = 0; batch < 8; batch++) {
            ConnectionLifetime[] connections = new ConnectionLifetime[256];
            for (int i = 0; i < connections.length; i++) {
                assertTrue(slots.tryAcquire());
                connections[i] = new ConnectionLifetime(0, () -> {}, slots::release);
                connections[i].connected(0); connections[i].finish(0, 0);
            }
            assertEquals(0, slots.availablePermits());
            for (ConnectionLifetime connection : connections) {
                assertTrue(connection.reap(15000));
                connection.finish(1, 15001); connection.finish(1, 15002);
            }
            assertEquals(256, slots.availablePermits());
        }
    }

    @Test public void stuckConnectExpiresSeparatelyFromConnectedIdle() {
        AtomicInteger cancelled = new AtomicInteger();
        ConnectionLifetime pending = new ConnectionLifetime(0, cancelled::incrementAndGet, () -> {});
        assertFalse(pending.reap(19999)); assertTrue(pending.reap(20000));
        assertTrue(pending.cancelled()); assertEquals(1, cancelled.get());
    }
}
