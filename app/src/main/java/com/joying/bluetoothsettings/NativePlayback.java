package com.joying.bluetoothsettings;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.util.Log;

/** Controls only the native receiver's session, never global media buttons. */
final class NativePlayback {
    interface Listener { void changed(); void error(String text); }
    private final Context context;
    private final Handler main;
    private final Listener listener;
    private MediaBrowser browser;
    private MediaController controller;
    private PlaybackState state;
    private boolean enabled, closed;
    private long generation;
    private String title = "";
    private final Runnable retry = () -> { if (enabled && !closed) connect(); };
    private MediaController.Callback callback;
    NativePlayback(Context context, Handler main, Listener listener) {
        this.context = context; this.main = main; this.listener = listener;
    }
    void setEnabled(boolean value) {
        enabled = value;
        if (!value) { main.removeCallbacks(retry); disconnect(); }
        else if (browser == null) connect();
        listener.changed();
    }
    private void connect() {
        if (browser != null || !enabled || closed) return;
        final long token = ++generation;
        final MediaBrowser candidate = new MediaBrowser(context, new ComponentName(
                "com.android.bluetooth", "com.android.bluetooth.a2dpsink.mbs.A2dpMediaBrowserService"),
                new MediaBrowser.ConnectionCallback() {
                    @Override public void onConnected() {
                        if (generation != token || browser == null || closed || !enabled) return;
                        try {
                            controller = new MediaController(context, browser.getSessionToken());
                            callback = new MediaController.Callback() {
                                @Override public void onPlaybackStateChanged(PlaybackState value) {
                                    if (generation != token) return;
                                    state = value; listener.changed();
                                }
                                @Override public void onMetadataChanged(MediaMetadata value) {
                                    if (generation != token) return;
                                    readTitle(value); listener.changed();
                                }
                                @Override public void onSessionDestroyed() {
                                    if (generation != token) return;
                                    disconnect(); scheduleRetry(); listener.changed();
                                }
                            };
                            controller.registerCallback(callback, main);
                            state = controller.getPlaybackState();
                            readTitle(controller.getMetadata());
                            listener.changed();
                        } catch (RuntimeException failure) { failed("Native playback session unavailable", failure); }
                    }
                    @Override public void onConnectionSuspended() { if (generation == token) failed("Native playback disconnected", null); }
                    @Override public void onConnectionFailed() { if (generation == token) failed("Native playback connection unavailable", null); }
                }, null);
        browser = candidate;
        try { candidate.connect(); }
        catch (RuntimeException failure) { failed("Native playback binding denied", failure); }
    }
    private void failed(String message, Exception failure) {
        if (failure != null) Log.w("JoyingBluetooth", message, failure);
        disconnect(); scheduleRetry(); listener.changed();
        // Retry silently; the status line remains explicit while no session is ready.
    }
    private void scheduleRetry() {
        main.removeCallbacks(retry);
        if (enabled && !closed) main.postDelayed(retry, 3000);
    }
    private void readTitle(MediaMetadata metadata) {
        CharSequence text = metadata == null ? null : metadata.getDescription().getTitle();
        title = text == null ? "" : text.toString();
    }
    boolean supports(long action) { return ready() && (state.getActions() & action) != 0; }
    boolean playing() { return state != null && state.getState() == PlaybackState.STATE_PLAYING; }
    boolean ready() { return controller != null && state != null && state.getState() != PlaybackState.STATE_NONE && state.getState() != PlaybackState.STATE_ERROR; }
    String summary() {
        if (!ready()) return "Remote unavailable";
        String label = playing() ? "Playing" : state.getState() == PlaybackState.STATE_PAUSED ? "Paused"
                : state.getState() == PlaybackState.STATE_STOPPED ? "Stopped" : "Remote ready";
        return label + (title.isEmpty() ? "" : " · " + title);
    }
    void send(long action) {
        if (!supports(action)) { listener.error("This remote control is unavailable"); return; }
        try {
            MediaController.TransportControls controls = controller.getTransportControls();
            if (action == PlaybackState.ACTION_PLAY) controls.play();
            else if (action == PlaybackState.ACTION_PAUSE) controls.pause();
            else if (action == PlaybackState.ACTION_STOP) controls.stop();
            else if (action == PlaybackState.ACTION_SKIP_TO_NEXT) controls.skipToNext();
            else if (action == PlaybackState.ACTION_SKIP_TO_PREVIOUS) controls.skipToPrevious();
        } catch (RuntimeException failure) { listener.error("Native playback command failed"); }
    }
    void reconnect() { disconnect(); main.removeCallbacks(retry); if (enabled && !closed) connect(); }
    private void disconnect() {
        generation++;
        if (controller != null && callback != null) { try { controller.unregisterCallback(callback); } catch (RuntimeException ignored) {} }
        callback = null;
        controller = null; state = null; title = "";
        if (browser != null) { try { browser.disconnect(); } catch (RuntimeException ignored) {} }
        browser = null;
    }
    void close() { closed = true; main.removeCallbacks(retry); disconnect(); }
}
