package com.joying.bluetoothsettings;

import java.util.ArrayList;

/** Lifetime of the native settings task opened by these controls. */
final class SettingsWindow {
    interface Listener { void changed(); }
    static final SettingsWindow INSTANCE = new SettingsWindow();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private Object owner;
    private Runnable close;
    private boolean pending, closing;
    private long generation;

    boolean open() { return owner != null; }
    boolean busy() { return pending || closing; }
    long beginOpen() {
        if (open() || busy()) return 0;
        pending = true;
        long token = ++generation;
        changed();
        return token;
    }
    boolean expire(long token) {
        if (!pending || token != generation) return false;
        pending = false;
        changed();
        return true;
    }
    void launchFailed() {
        if (!pending) return;
        pending = false;
        generation++;
        changed();
    }
    void attached(Object activity, Runnable action) {
        owner = activity;
        close = action;
        pending = closing = false;
        generation++;
        changed();
    }
    boolean requestClose() {
        if (!open() || busy()) return false;
        Object current = owner;
        closing = true;
        changed();
        try { close.run(); }
        catch (RuntimeException failure) {
            if (owner == current) { closing = false; changed(); }
            throw failure;
        }
        return true;
    }
    void detached(Object activity) {
        if (owner != activity) return;
        owner = close = null;
        closing = false;
        generation++;
        changed();
    }
    void add(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        listener.changed();
    }
    void remove(Listener listener) { listeners.remove(listener); }
    private void changed() { for (Listener listener : new ArrayList<>(listeners)) listener.changed(); }
}
