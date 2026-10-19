package com.joying.bluetoothsettings;

import java.util.ArrayList;

/** Process-local window presence; observing never creates a window or changes audio. */
final class OverlayPresence {
    interface Listener { void changed(boolean visible); }
    static final OverlayPresence INSTANCE=new OverlayPresence();
    private final ArrayList<Listener> listeners=new ArrayList<>();
    private boolean visible;
    boolean visible(){return visible;}
    void add(Listener listener){if(!listeners.contains(listener))listeners.add(listener);listener.changed(visible);}
    void remove(Listener listener){listeners.remove(listener);}
    void set(boolean value){if(visible==value)return;visible=value;for(Listener l:new ArrayList<>(listeners))l.changed(value);}
}
