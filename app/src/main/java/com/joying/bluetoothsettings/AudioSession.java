package com.joying.bluetoothsettings;

/** Focus ownership for an explicitly selected Bluetooth audio route. */
final class AudioSession {
    interface Port {
        boolean acquireFocus();
        void releaseFocus();
        void pauseBluetooth();
        void error(String message);
    }
    private final Port port;
    private boolean requested;
    AudioSession(Port port) { this.port = port; }
    boolean requested() { return requested; }
    boolean begin() {
        if (requested) return true;
        if (!port.acquireFocus()) {
            port.error("Audio focus unavailable; Bluetooth audio was not selected");
            return false;
        }
        requested = true;
        return true;
    }
    void routeChanged(boolean selected, boolean pending) {
        if (requested && !selected && !pending) stop();
    }
    void stop() {
        requested = false;
        port.pauseBluetooth();
        port.releaseFocus();
    }
    void close() {
        if (requested) stop();
        else port.releaseFocus();
    }
}
