package com.joying.bluetoothsettings;

/** Serial source ownership and readback; independent of Android for host checks. */
final class SourceController {
    static final int BLUETOOTH = 3, ANDROID = 0, UNKNOWN = -1;
    static final long TIMEOUT_MS = 1500;
    interface Port {
        boolean select(int value);
        void savePrevious(int value);
        void later(Runnable task, long delay);
        void changed();
        void error(String message);
    }
    private final Port port;
    private int source = UNKNOWN, target = UNKNOWN, previous;
    private long generation;
    private Runnable releaseCompletion;
    private boolean releasing;

    SourceController(Port port, int savedPrevious) {
        this.port = port;
        previous = savedPrevious >= 0 && savedPrevious != BLUETOOTH ? savedPrevious : UNKNOWN;
    }
    int current() { return source; }
    boolean busy() { return target != UNKNOWN; }
    boolean active() { return source == BLUETOOTH; }
    private void save(int value) { previous = value; port.savePrevious(value); }

    void disconnected() {
        source = target = UNKNOWN;
        generation++;
        port.changed();
        drainRelease();
    }
    void update(int value) {
        if (value < 0 || value >= 20) return;
        source = value;
        if (busy() && (target == value || (target != BLUETOOTH && value != BLUETOOTH))) {
            target = UNKNOWN;
            generation++;
        }
        if (!busy() && value != BLUETOOTH && previous != UNKNOWN) save(UNKNOWN);
        port.changed();
        drainRelease();
    }
    void toggle() {
        if (busy() || releaseCompletion != null) return;
        if (source == UNKNOWN) { port.error("Waiting for Joying source readback"); return; }
        if (active()) request(previous == UNKNOWN ? ANDROID : previous);
        else { save(source); request(BLUETOOTH); }
    }
    private void request(int value) {
        target = value;
        final long token = ++generation;
        if (!port.select(value)) {
            target = UNKNOWN;
            port.error("Joying source request failed");
            port.changed();
            drainRelease();
            return;
        }
        port.changed();
        port.later(() -> {
            if (generation != token || !busy()) return;
            target = UNKNOWN;
            generation++;
            port.error("Joying source change not confirmed");
            port.changed();
            drainRelease();
        }, TIMEOUT_MS);
    }
    /** Wait for an in-flight request, then restore only a source acquired by us. */
    void releaseOwned(Runnable complete) {
        if (releaseCompletion != null) {
            Runnable first = releaseCompletion;
            releaseCompletion = () -> { first.run(); complete.run(); };
            return;
        }
        releaseCompletion = complete;
        releasing = false;
        drainRelease();
    }
    void releaseActive(Runnable complete) {
        if (active() && previous == UNKNOWN) save(ANDROID);
        releaseOwned(complete);
    }
    private void drainRelease() {
        if (releaseCompletion == null || busy()) return;
        if (!releasing && active() && previous != UNKNOWN) {
            releasing = true;
            request(previous);
            return;
        }
        if (source == UNKNOWN && previous != UNKNOWN) port.error("Previous source could not be restored; reopen controls to retry");
        Runnable done = releaseCompletion;
        releaseCompletion = null;
        releasing = false;
        done.run();
    }
}
