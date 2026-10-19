package com.joying.bluetoothsettings;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

/** Permanent media focus: earlier players are not automatically resumed. */
final class AudioFocusControl {
    interface Listener { void lost(); }
    private final AudioManager manager;
    private final Handler main;
    private final Listener listener;
    private AudioManager.OnAudioFocusChangeListener callback;
    private Object request;
    private boolean held;
    private long generation;

    AudioFocusControl(Context context, Handler main, Listener listener) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.main = main;
        this.listener = listener;
    }
    boolean acquire() {
        if (held) return true;
        if (manager == null) return false;
        final long token = ++generation;
        callback = change -> {
            if (token == generation && held && change < 0) listener.lost();
        };
        try {
            int result;
            if (Build.VERSION.SDK_INT >= 26) {
                request = Api26.create(callback, main);
                result = Api26.acquire(manager, request);
            } else {
                result = manager.requestAudioFocus(callback, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            }
            held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if (!held) release();
            return held;
        } catch (RuntimeException failure) {
            Log.w("JoyingBluetooth", "Audio focus request failed", failure);
            release();
            return false;
        }
    }
    void release() {
        generation++;
        held = false;
        try {
            if (manager != null) {
                if (Build.VERSION.SDK_INT >= 26 && request != null) Api26.release(manager, request);
                else if (callback != null) manager.abandonAudioFocus(callback);
            }
        } catch (RuntimeException failure) {
            Log.w("JoyingBluetooth", "Audio focus release failed", failure);
        } finally {
            request = null;
            callback = null;
        }
    }
    private static final class Api26 {
        static Object create(AudioManager.OnAudioFocusChangeListener callback, Handler main) {
            return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(callback, main)
                    .build();
        }
        static int acquire(AudioManager manager, Object request) {
            return manager.requestAudioFocus((AudioFocusRequest) request);
        }
        static void release(AudioManager manager, Object request) {
            manager.abandonAudioFocusRequest((AudioFocusRequest) request);
        }
    }
}
