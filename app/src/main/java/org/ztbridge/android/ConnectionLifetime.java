package org.ztbridge.android;

/** Preserve TCP half-close, while bounding abandoned or stalled connections. */
final class ConnectionLifetime {
    static final long IDLE_MS = 120_000, HALF_CLOSED_IDLE_MS = 15_000, CONNECT_MS = 20_000;
    private final Runnable cancelIo, release;
    private final boolean[] finished = new boolean[2];
    private long lastActivity;
    private boolean connected, cancelled, released;
    ConnectionLifetime(long now, Runnable cancelIo, Runnable release) {
        lastActivity = now; this.cancelIo = cancelIo; this.release = release;
    }
    synchronized void connected(long now) { connected = true; lastActivity = now; }
    synchronized void activity(long now) { lastActivity = now; }
    synchronized boolean cancelled() { return cancelled; }
    void finish(int direction, long now) {
        boolean dispose = false;
        synchronized (this) {
            if (finished[direction]) return;
            finished[direction] = true; lastActivity = now;
            if (finished[0] && finished[1] && !released) { released = true; dispose = true; }
        }
        if (dispose) release.run();
    }
    void cancel() {
        synchronized (this) {
            if (cancelled || released) return;
            cancelled = true;
        }
        cancelIo.run();
    }
    boolean reap(long now) {
        synchronized (this) {
            long timeout = !connected ? CONNECT_MS : finished[0] || finished[1] ? HALF_CLOSED_IDLE_MS : IDLE_MS;
            if (released || cancelled || now - lastActivity < timeout) return false;
        }
        cancel(); return true;
    }
}
