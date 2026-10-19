package com.joying.bluetoothsettings;

/** Adapter requests with state confirmation and one enable retry. */
final class AdapterController {
    static final int OFF = 10, TURNING_ON = 11, ON = 12, TURNING_OFF = 13;
    static final int BLE_TURNING_ON = 14, BLE_ON = 15, BLE_TURNING_OFF = 16;
    static final long TIMEOUT_MS = 12000, RETRY_MS = 3000, POLL_MS = 250;
    interface Port {
        int state();
        boolean setEnabled(boolean enabled);
        long now();
        void later(Runnable task, long delay);
        void changed();
        void error(String message);
        void enablePermissionRequired();
    }
    private final Port port;
    private int target = -1, attempts;
    private long generation, started, lastRequest;
    private boolean scheduled;
    AdapterController(Port port) { this.port = port; }
    boolean busy() { return target != -1; }
    void request(boolean enabled) {
        if (busy()) return;
        target = enabled ? ON : OFF;
        attempts = 0;
        started = port.now();
        scheduled = false;
        generation++;
        tick();
    }
    void refresh() { if (busy()) tick(); else port.changed(); }
    void cancel() { target = -1; scheduled = false; generation++; }
    private void finish() { cancel(); port.changed(); }
    private void tick() {
        if (!busy()) return;
        int state = port.state();
        if (state < OFF || state > BLE_TURNING_OFF) {
            port.error("Bluetooth state unavailable"); finish(); return;
        }
        if ((target == ON && state == ON) || (target == OFF && (state == OFF || state == BLE_ON))) {
            finish(); return;
        }
        long now = port.now();
        if (now - started >= TIMEOUT_MS) {
            port.error(target == ON ? "Bluetooth enable not confirmed; open Settings" : "Bluetooth shutdown not confirmed; open Settings");
            finish(); return;
        }
        boolean enable = target == ON;
        boolean stable = enable ? state == OFF || state == BLE_ON : state == ON;
        if (stable && (attempts == 0 || (enable && attempts == 1 && now - lastRequest >= RETRY_MS))) {
            attempts++;
            lastRequest = now;
            try {
                if (!port.setEnabled(enable)) {
                    port.error(enable ? "Bluetooth enable request rejected" : "Bluetooth off request rejected");
                    finish(); return;
                }
            } catch (SecurityException denied) {
                finish();
                if (enable) port.enablePermissionRequired();
                else port.error("Bluetooth off permission unavailable");
                return;
            } catch (RuntimeException failure) {
                port.error("Bluetooth request failed"); finish(); return;
            }
        }
        port.changed();
        if (scheduled) return;
        scheduled = true;
        final long token = generation;
        port.later(() -> {
            if (token != generation || !busy()) return;
            scheduled = false;
            tick();
        }, POLL_MS);
    }
}
