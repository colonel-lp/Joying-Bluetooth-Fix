package com.joying.bluetoothsettings;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingBarService extends Service {
    static final String CLOSE = "com.joying.bluetoothsettings.CLOSE";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private WindowManager.LayoutParams layout;
    private LinearLayout bar;
    private TextView status;
    private Button previous, play, stop, next, sourceButton, bluetoothButton;
    private SharedPreferences preferences;
    private BluetoothAdapter adapter;
    private JoyingSource wire;
    private SourceController source;
    private NativePlayback playback;
    private AudioFocusControl audioFocus;
    private AudioSession audioSession;
    private AdapterController adapterControl;
    private boolean receiverRegistered, closing, destroyed, disabling;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int state = adapterState();
            if (state == BluetoothAdapter.STATE_OFF) {
                disabling = false;
                if (audioSession != null && audioSession.requested()) audioSession.stop();
                source.releaseOwned(() -> {});
            }
            if (adapterControl != null) adapterControl.refresh();
            playback.setEnabled(state == BluetoothAdapter.STATE_ON);
            render();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("floatingBar", MODE_PRIVATE);
        showNotification(); // Meet Android 8 foreground-service deadline immediately.
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            error("Allow Bluetooth Controls to display over other apps"); stopSelf(); return;
        }
        adapter = BluetoothAdapter.getDefaultAdapter();
        source = new SourceController(new SourceController.Port() {
            @Override public boolean select(int value) { return wire != null && wire.select(value); }
            @Override public void savePrevious(int value) {
                // Preserve the return source if the process is killed during acquisition.
                preferences.edit().putInt("previousSource", value).commit();
            }
            @Override public void later(Runnable task, long delay) { main.postDelayed(task, delay); }
            @Override public void changed() {
                if (audioSession != null) audioSession.routeChanged(source.active(), source.busy());
                render();
            }
            @Override public void error(String text) { FloatingBarService.this.error(text); }
        }, preferences.getInt("previousSource", -1));
        wire = new JoyingSource(this, main, new JoyingSource.Listener() {
            @Override public void source(int value) {
                source.update(value);
                if (adapterState() == BluetoothAdapter.STATE_OFF && !source.busy()) source.releaseOwned(() -> {});
            }
            @Override public void disconnected() { source.disconnected(); }
            @Override public void error(String text) { FloatingBarService.this.error(text); }
        });
        playback = new NativePlayback(this, main, new NativePlayback.Listener() {
            @Override public void changed() { render(); }
            @Override public void error(String text) { FloatingBarService.this.error(text); }
        });
        audioFocus = new AudioFocusControl(this, main, () -> {
            if (destroyed || audioSession == null || !audioSession.requested()) return;
            audioSession.stop();
            source.releaseActive(() -> {});
        });
        audioSession = new AudioSession(new AudioSession.Port() {
            @Override public boolean acquireFocus() { return audioFocus.acquire(); }
            @Override public void releaseFocus() { audioFocus.release(); }
            @Override public void pauseBluetooth() { playback.pauseForRelease(); }
            @Override public void error(String text) { FloatingBarService.this.error(text); }
        });
        adapterControl = new AdapterController(new AdapterController.Port() {
            @Override public int state() { return adapterFullState(); }
            @Override public boolean setEnabled(boolean enabled) {
                return adapter != null && (enabled ? adapter.enable() : adapter.disable());
            }
            @Override public long now() { return SystemClock.uptimeMillis(); }
            @Override public void later(Runnable task, long delay) { main.postDelayed(task, delay); }
            @Override public void changed() {
                playback.setEnabled(adapterState() == BluetoothAdapter.STATE_ON);
                render();
            }
            @Override public void error(String text) { FloatingBarService.this.error(text); }
            @Override public void enablePermissionRequired() {
                openActivity(new Intent(FloatingBarService.this, SettingsShortcut.class).setAction(SettingsShortcut.ENABLE));
            }
        });
        try {
            createBar();
            registerReceiver(receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            receiverRegistered = true;
            wire.connect();
            playback.setEnabled(adapterState() == BluetoothAdapter.STATE_ON);
            render();
        } catch (RuntimeException failure) {
            Log.e("JoyingBluetooth", "Floating bar startup failed", failure);
            error("Floating bar unavailable; check display-over-other-apps permission");
            stopSelf();
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CLOSE.equals(intent.getAction())) closeBar();
        else render();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void showNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel("controls", "Floating Bluetooth controls", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, "controls");
        } else builder = new Notification.Builder(this);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, SettingsShortcut.class), flags);
        PendingIntent close = PendingIntent.getService(this, 1, new Intent(this, FloatingBarService.class).setAction(CLOSE), flags);
        startForeground(12, builder.setSmallIcon(com.joying.bluetoothsettings.R.drawable.bluetooth_icon)
                .setContentTitle("Bluetooth Controls").setContentText("Floating controls are running")
                .setOngoing(true).setContentIntent(open)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", close).build());
    }
    private void createBar() {
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(5), dp(3), dp(5), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF2222933); background.setCornerRadius(dp(9));
        background.setStroke(dp(1), 0xFF7C899B); bar.setBackground(background);
        status = new TextView(this);
        status.setTextColor(Color.WHITE); status.setTextSize(12);
        status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(7), 0, dp(7), 0);
        status.setContentDescription("Drag here to move Bluetooth controls. Long press to reconnect services.");
        bar.addView(status, new LinearLayout.LayoutParams(-1, dp(27)));
        LinearLayout row = new LinearLayout(this);
        bar.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
        previous = button(row, "|◀", 1, () -> playback.send(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        play = button(row, "▶", 1, this::playOrPause);
        stop = button(row, "■", 1, () -> playback.send(PlaybackState.ACTION_STOP));
        next = button(row, "▶|", 1, () -> playback.send(PlaybackState.ACTION_SKIP_TO_NEXT));
        sourceButton = button(row, "Audio ?", 1.9f, this::toggleAudio);
        bluetoothButton = button(row, "BT ?", 1.6f, this::toggleBluetooth);
        button(row, "BT Settings", 2.1f, () -> openActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        button(row, "×", .8f, this::closeBar).setContentDescription("Close floating controls and release the source selected by this app");
        previous.setContentDescription("Previous track"); play.setContentDescription("Play or pause remote music");
        stop.setContentDescription("Stop remote music, when supported by sender"); next.setContentDescription("Next track");
        sourceButton.setContentDescription("Toggle Joying Bluetooth audio source");
        bluetoothButton.setContentDescription("Turn Android Bluetooth on or off");
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        layout = new WindowManager.LayoutParams(barWidth(), -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.LEFT;
        layout.x = preferences.getInt("x", dp(10)); layout.y = preferences.getInt("y", dp(50));
        clampPosition();
        status.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY; int originalX, originalY; boolean dragged; long downAt;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    startX = event.getRawX(); startY = event.getRawY();
                    originalX = layout.x; originalY = layout.y; dragged = false; downAt = event.getEventTime(); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) dragged = true;
                    if (dragged) { layout.x = originalX + Math.round(dx); layout.y = originalY + Math.round(dy); updatePosition(); }
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    preferences.edit().putInt("x", layout.x).putInt("y", layout.y).apply();
                    if (!dragged && event.getActionMasked() == MotionEvent.ACTION_UP && event.getEventTime() - downAt > 650 && !closing) {
                        wire.reconnect(); playback.reconnect(); error("Reconnecting source and playback services");
                    }
                    view.performClick(); return true;
                }
                return false;
            }
        });
        windows.addView(bar, layout);
        OverlayPresence.INSTANCE.attached(this::closeBar);
    }
    private Button button(LinearLayout row, String label, float weight, Runnable action) {
        Button button = new Button(this);
        button.setText(label); button.setTextSize(12); button.setAllCaps(false);
        button.setTextColor(Color.WHITE); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0); button.setPadding(dp(2), 0, dp(2), 0);
        button.setSingleLine(true);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFF3B4655); background.setCornerRadius(dp(4));
        button.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, weight);
        params.setMargins(dp(1), 0, dp(1), 0); row.addView(button, params);
        button.setOnClickListener(v -> { if (!closing) action.run(); });
        return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int barWidth() { return Math.min(dp(530), Math.max(dp(200), getResources().getDisplayMetrics().widthPixels - dp(12))); }
    private void clampPosition() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        layout.x = Math.max(0, Math.min(layout.x, Math.max(0, screen.widthPixels - layout.width)));
        layout.y = Math.max(0, Math.min(layout.y, Math.max(0, screen.heightPixels - dp(100))));
    }
    private void updatePosition() {
        clampPosition();
        try { windows.updateViewLayout(bar, layout); } catch (RuntimeException ignored) {}
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (bar != null && layout != null) { layout.width = barWidth(); updatePosition(); }
    }
    private int adapterState() {
        try { return adapter == null ? BluetoothAdapter.ERROR : adapter.getState(); }
        catch (RuntimeException failure) { return BluetoothAdapter.ERROR; }
    }
    private int adapterFullState() {
        if (adapter != null && Build.VERSION.SDK_INT <= 28) {
            try { return (Integer) BluetoothAdapter.class.getMethod("getLeState").invoke(adapter); }
            catch (ReflectiveOperationException | RuntimeException ignored) {}
        }
        return adapterState();
    }
    private void toggleBluetooth() {
        if (adapterControl == null || adapterControl.busy() || disabling) return;
        int state = adapterState();
        if (state == BluetoothAdapter.STATE_ON && !disabling) {
            disabling = true; render();
            audioSession.stop();
            source.releaseActive(() -> {
                if (destroyed) return;
                disabling = false;
                adapterControl.request(false);
            });
        } else if (state == BluetoothAdapter.STATE_OFF) {
            adapterControl.request(true);
        } else if (state == BluetoothAdapter.ERROR) error("Bluetooth adapter unavailable");
    }
    private void toggleAudio() {
        if (source.busy() || source.current() < 0) return;
        if (source.active()) {
            audioSession.stop();
            source.toggle();
        } else if (adapterState() != BluetoothAdapter.STATE_ON) {
            error("Enable Bluetooth before selecting its source");
        } else if (audioSession.begin()) source.toggle();
    }
    private void playOrPause() {
        if (playback.playing()) playback.send(PlaybackState.ACTION_PAUSE);
        else {
            if (source.active() && !audioSession.begin()) return;
            playback.send(PlaybackState.ACTION_PLAY);
        }
    }
    private void openActivity(Intent intent) {
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException failure) { error("Requested Android screen is unavailable"); }
    }
    private void closeBar() {
        if (closing) return;
        closing = true; render();
        if (audioSession != null) audioSession.close();
        if (source == null) stopSelf();
        else source.releaseOwned(this::stopSelf);
    }
    private void render() {
        if (status == null || source == null || playback == null || destroyed) return;
        int state = adapterState();
        boolean on = state == BluetoothAdapter.STATE_ON;
        boolean changing = disabling || (adapterControl != null && adapterControl.busy());
        String bt = changing ? "waiting" : on ? "ON" : state == BluetoothAdapter.STATE_OFF ? "OFF" : "waiting";
        bluetoothButton.setText("BT " + bt);
        bluetoothButton.setTextColor(on ? 0xFF87E5B0 : Color.WHITE);
        bluetoothButton.setEnabled(!closing && !changing && (on || state == BluetoothAdapter.STATE_OFF));
        bluetoothButton.setAlpha(bluetoothButton.isEnabled() ? 1f : .4f);
        sourceButton.setText(source.busy() ? "Audio …" : source.current() < 0 ? "Audio ?" : source.active() ? "Audio ON" : "Audio OFF");
        sourceButton.setTextColor(source.active() ? 0xFF87E5B0 : Color.WHITE);
        sourceButton.setEnabled(!closing && !changing && !source.busy() && source.current() >= 0 && (on || source.active()));
        sourceButton.setAlpha(sourceButton.isEnabled() ? 1f : .4f);
        play.setText(playback.playing() ? "Ⅱ" : "▶");
        enable(previous, PlaybackState.ACTION_SKIP_TO_PREVIOUS, on);
        enable(play, playback.playing() ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY, on);
        enable(stop, PlaybackState.ACTION_STOP, on);
        enable(next, PlaybackState.ACTION_SKIP_TO_NEXT, on);
        status.setText(closing ? "Closing · restoring previous source…" : "⋮⋮  Bluetooth " + bt + " · " + playback.summary());
    }
    private void enable(Button button, long action, boolean on) {
        button.setEnabled(!closing && on && playback.supports(action));
        button.setAlpha(button.isEnabled() ? 1f : .35f);
    }
    private void error(String text) {
        Log.w("JoyingBluetooth", text);
        if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (audioSession != null) audioSession.close();
        if (adapterControl != null) adapterControl.cancel();
        if (receiverRegistered) unregisterReceiver(receiver);
        if (playback != null) playback.close();
        if (wire != null) wire.close();
        if (bar != null && windows != null) { try { windows.removeView(bar); } catch (RuntimeException ignored) {} }
        OverlayPresence.INSTANCE.set(false);
        stopForeground(true);
        super.onDestroy();
    }
}
