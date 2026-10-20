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
import android.content.pm.PackageManager;
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
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingBarService extends Service {
    static final String CLOSE = "com.joying.bluetoothsettings.CLOSE";
    static final String EXIT = "com.joying.bluetoothsettings.EXIT";
    static final String PREVIOUS = "com.joying.bluetoothsettings.PREVIOUS";
    static final String PLAY_PAUSE = "com.joying.bluetoothsettings.PLAY_PAUSE";
    static final String STOP = "com.joying.bluetoothsettings.STOP";
    static final String NEXT = "com.joying.bluetoothsettings.NEXT";
    static final String AUDIO = "com.joying.bluetoothsettings.AUDIO";
    static final String BLUETOOTH = "com.joying.bluetoothsettings.BLUETOOTH";
    static final String SETTINGS = "com.joying.bluetoothsettings.SETTINGS";
    private static final String[] NOTIFICATION_ACTIONS = {PREVIOUS, PLAY_PAUSE, STOP, NEXT, AUDIO, BLUETOOTH, SETTINGS, EXIT};
    private static final int[] NOTIFICATION_BUTTONS = {R.id.notification_previous, R.id.notification_play,
            R.id.notification_stop, R.id.notification_next, R.id.notification_audio, R.id.notification_bluetooth,
            R.id.notification_settings, R.id.notification_exit};
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private WindowManager.LayoutParams layout;
    private LinearLayout bar;
    private TextView status;
    private Button previous, play, stop, next, sourceButton, bluetoothButton, settingsButton;
    private final SettingsWindow.Listener settingsListener = this::render;
    private SharedPreferences preferences;
    private BluetoothAdapter adapter;
    private JoyingSource wire;
    private SourceController source;
    private NativePlayback playback;
    private ReceiverDevice receiverDevice;
    private AudioFocusControl audioFocus;
    private AudioSession audioSession;
    private AdapterController adapterControl;
    private boolean receiverRegistered, closing, destroyed, disabling, attached, hidden, stopping;
    private boolean exitRequested, exitSourceDone, exitAdapterRequested;
    private String notificationKey, shutdownError;
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
            SettingsWindow.INSTANCE.add(settingsListener);
            receiverDevice = new ReceiverDevice(this, adapter, this::render);
            receiverDevice.start();
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
        if (intent != null && EXIT.equals(intent.getAction())) turnOffAndExit();
        else if (intent != null && CLOSE.equals(intent.getAction())) closeBar();
        else if (intent != null && notificationAction(intent.getAction())) {
            if (!attached) hidden = true;
            if (canControl(intent.getAction())) {
                switch (intent.getAction()) {
                    case PREVIOUS: playback.send(PlaybackState.ACTION_SKIP_TO_PREVIOUS); break;
                    case PLAY_PAUSE: playOrPause(); break;
                    case STOP: playback.send(PlaybackState.ACTION_STOP); break;
                    case NEXT: playback.send(PlaybackState.ACTION_SKIP_TO_NEXT); break;
                    case AUDIO: toggleAudio(); break;
                    case BLUETOOTH: toggleBluetooth(); break;
                    case SETTINGS: toggleSettings(); break;
                }
            }
            render();
        } else showBar();
        return START_NOT_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void showNotification() {
        int state = adapterFullState();
        String bt = state == AdapterController.ON ? "ON"
                : state == AdapterController.OFF || state == AdapterController.BLE_ON ? "OFF"
                : state == BluetoothAdapter.ERROR ? "?" : "waiting";
        if (disabling || (adapterControl != null && adapterControl.busy())) bt = "waiting";
        String audio = source == null ? "?" : source.busy() ? "waiting"
                : (source.active() || (audioSession != null && audioSession.requested())) ? "ON"
                : source.current() < 0 ? "?" : "OFF";
        String text = "BT " + bt + " · Audio " + audio;
        if (exitRequested) text = "Turning both off · " + text;
        else if (shutdownError != null) text = shutdownError + " · " + text;
        String sender = receiverDevice == null ? "" : receiverDevice.name();
        String summary = playback == null ? "" : playback.summary();
        boolean playing = playback != null && playback.playing();
        boolean audioOn = source != null && source.active();
        boolean btOn = adapterState() == BluetoothAdapter.STATE_ON;
        String key = text + "\n" + sender + "\n" + summary + "\n" + playing + audioOn + btOn
                + SettingsWindow.INSTANCE.open();
        for (String action : NOTIFICATION_ACTIONS) key += ":" + canControl(action);
        if (key.equals(notificationKey)) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel("controls", "Bluetooth controls", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, "controls");
        } else builder = new Notification.Builder(this);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, SettingsShortcut.class), flags);
        RemoteViews controls = new RemoteViews(getPackageName(), R.layout.notification_controls);
        String detail = text + (sender.isEmpty() ? "" : " · " + sender) + (summary.isEmpty() ? "" : " · " + summary);
        controls.setTextViewText(R.id.notification_status, detail);
        controls.setOnClickPendingIntent(R.id.notification_status, open);
        String[] labels = {"|◀", playing ? "Ⅱ" : "▶", "■", "▶|", "Audio " + audio, "BT " + bt, "BT settings", "×"};
        String[] descriptions = {"Previous track", playing ? "Pause remote music" : "Play remote music",
                "Stop remote music", "Next track", "Toggle Bluetooth audio source: " + audio,
                "Toggle Android Bluetooth: " + bt, "Open or close BT settings", "Turn Bluetooth and Audio off and exit"};
        for (int i = 0; i < NOTIFICATION_ACTIONS.length; i++) {
            String action = NOTIFICATION_ACTIONS[i];
            int id = NOTIFICATION_BUTTONS[i];
            boolean enabled = canControl(action);
            boolean active = AUDIO.equals(action) ? audioOn : BLUETOOTH.equals(action) ? btOn
                    : SETTINGS.equals(action) && SettingsWindow.INSTANCE.open();
            controls.setTextViewText(id, labels[i]);
            controls.setTextColor(id, active ? 0xFF87E5B0 : enabled ? Color.WHITE : 0xFF8C96A4);
            controls.setBoolean(id, "setEnabled", enabled);
            controls.setContentDescription(id, descriptions[i]);
            controls.setOnClickPendingIntent(id, PendingIntent.getService(this, 20 + i,
                    new Intent(this, FloatingBarService.class).setAction(action), flags));
        }
        builder.setSmallIcon(R.drawable.bluetooth_icon).setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(open);
        if (Build.VERSION.SDK_INT >= 24) {
            builder.setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(controls).setCustomBigContentView(controls);
        } else builder.setContent(controls);
        Notification notification = builder.build();
        if (notificationKey == null) startForeground(12, notification);
        else manager.notify(12, notification);
        notificationKey = key;
    }
    private boolean notificationAction(String action) {
        for (String candidate : NOTIFICATION_ACTIONS) if (candidate.equals(action)) return true;
        return false;
    }
    private boolean canControl(String action) {
        if (destroyed || stopping || closing) return false;
        if (EXIT.equals(action)) return true;
        if (source == null || playback == null || audioSession == null || adapterControl == null) return false;
        int state = adapterState();
        boolean on = state == BluetoothAdapter.STATE_ON;
        boolean changing = disabling || adapterControl.busy();
        switch (action) {
            case BLUETOOTH: return !changing && (on || state == BluetoothAdapter.STATE_OFF);
            case AUDIO: return !changing && !source.busy() && source.current() >= 0 && (on || source.active());
            case SETTINGS: return !SettingsWindow.INSTANCE.busy();
            case PREVIOUS: return on && playback.supports(PlaybackState.ACTION_SKIP_TO_PREVIOUS);
            case PLAY_PAUSE: return on && playback.supports(playback.playing() ? PlaybackState.ACTION_PAUSE : PlaybackState.ACTION_PLAY);
            case STOP: return on && playback.supports(PlaybackState.ACTION_STOP);
            case NEXT: return on && playback.supports(PlaybackState.ACTION_SKIP_TO_NEXT);
            default: return false;
        }
    }

    private void showBar() {
        if (destroyed || stopping) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            error("Allow Bluetooth Controls to display over other apps");
            hidden = true; render(); return;
        }
        try {
            hidden = false;
            if (bar == null) createBar();
            else attachBar();
        } catch (RuntimeException failure) {
            Log.e("JoyingBluetooth", "Floating bar unavailable", failure);
            error("Floating bar unavailable; check display-over-other-apps permission");
            hidden = true;
        }
        render();
    }
    private void attachBar() {
        if (attached) return;
        windows.addView(bar, layout);
        attached = true;
        OverlayPresence.INSTANCE.attached(this::closeBar);
    }
    private boolean hideBar() {
        if (!attached) return true;
        try { windows.removeViewImmediate(bar); }
        catch (RuntimeException failure) {
            Log.w("JoyingBluetooth", "Floating bar removal failed", failure);
            error("Floating controls could not be hidden"); return false;
        }
        attached = false;
        OverlayPresence.INSTANCE.set(false);
        return true;
    }
    private void createBar() {
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setPadding(dp(5), dp(3), dp(5), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF2222933); background.setCornerRadius(dp(9));
        background.setStroke(dp(1), 0xFF7C899B); bar.setBackground(background);
        LinearLayout title = new LinearLayout(this);
        bar.addView(title, new LinearLayout.LayoutParams(-1, dp(27)));
        status = new TextView(this);
        status.setTextColor(Color.WHITE); status.setTextSize(12);
        status.setSingleLine(true); status.setEllipsize(TextUtils.TruncateAt.END);
        status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(7), 0, dp(7), 0);
        status.setContentDescription("Drag here to move Bluetooth controls. Long press to reconnect services.");
        title.addView(status, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView version = new TextView(this);
        version.setText(versionLabel()); version.setTextSize(12); version.setTextColor(0xFFBAC6D6);
        version.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        version.setPadding(dp(7), 0, dp(7), 0);
        title.addView(version, new LinearLayout.LayoutParams(-2, -1));
        LinearLayout row = new LinearLayout(this);
        bar.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
        previous = button(row, "|◀", 1, () -> playback.send(PlaybackState.ACTION_SKIP_TO_PREVIOUS));
        play = button(row, "▶", 1, this::playOrPause);
        stop = button(row, "■", 1, () -> playback.send(PlaybackState.ACTION_STOP));
        next = button(row, "▶|", 1, () -> playback.send(PlaybackState.ACTION_SKIP_TO_NEXT));
        sourceButton = button(row, "Audio ?", 1.9f, this::toggleAudio);
        bluetoothButton = button(row, "BT ?", 1.6f, this::toggleBluetooth);
        settingsButton = button(row, "BT Settings", 2.1f, this::toggleSettings);
        settingsButton.setContentDescription("Open or close the Bluetooth settings window opened by these controls");
        Button close = button(row, "×", .8f, this::closeBar);
        close.setContentDescription("Hide floating controls; exit when Bluetooth and Audio are off");
        close.setOnClickListener(v -> closeBar());
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
        View.OnTouchListener drag = new View.OnTouchListener() {
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
        };
        status.setOnTouchListener(drag);
        version.setOnTouchListener(drag);
        attachBar();
    }
    private String versionLabel() {
        try { return "v" + getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException unavailable) { return ""; }
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
        if (attached) try { windows.updateViewLayout(bar, layout); } catch (RuntimeException ignored) {}
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
    private void toggleSettings() {
        SettingsWindow window = SettingsWindow.INSTANCE;
        if (window.busy()) return;
        if (window.open()) {
            try { window.requestClose(); }
            catch (RuntimeException failure) { error("Bluetooth settings could not close"); }
            return;
        }
        final long token = window.beginOpen();
        if (token == 0) return;
        try {
            startActivity(new Intent(this, BtSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            main.postDelayed(() -> {
                if (window.expire(token)) error("Bluetooth settings did not open; try again");
            }, 5000);
        } catch (RuntimeException failure) {
            window.launchFailed();
            error("Bluetooth settings unavailable");
        }
    }
    private void openActivity(Intent intent) {
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (RuntimeException failure) { error("Requested Android screen is unavailable"); }
    }
    private void closeBar() {
        if (destroyed || stopping || !hideBar()) return;
        hidden = true;
        render();
    }
    private boolean bothOff() {
        int state = adapterFullState();
        return adapter != null && (state == AdapterController.OFF || state == AdapterController.BLE_ON)
                && source != null && source.current() >= 0 && !source.active() && !source.busy()
                && audioSession != null && !audioSession.requested()
                && adapterControl != null && !adapterControl.busy() && !disabling;
    }
    private void turnOffAndExit() {
        if (destroyed || stopping || exitRequested) return;
        if (!attached) hidden = true;
        exitRequested = closing = true;
        exitSourceDone = exitAdapterRequested = false;
        shutdownError = null;
        audioSession.stop();
        source.releaseActive(() -> {
            if (destroyed || stopping) return;
            exitSourceDone = true;
            render();
        });
        render();
    }
    private void advanceExit() {
        if (!exitRequested || !exitSourceDone || source.busy() || adapterControl.busy() || disabling) return;
        if (!exitAdapterRequested) {
            exitAdapterRequested = true;
            adapterControl.request(false);
            return;
        }
        if (bothOff()) finishService();
        else {
            exitRequested = closing = false;
            shutdownError = "Shutdown not confirmed";
            error("Bluetooth or Audio is still on or unavailable; press notification × to retry");
            showNotification();
        }
    }
    private void finishService() {
        if (stopping) return;
        stopping = closing = true;
        audioSession.close();
        stopSelf();
    }
    private void render() {
        if (source == null || playback == null || destroyed || stopping) return;
        if (bothOff()) shutdownError = null;
        showNotification();
        if (hidden && !exitRequested && bothOff()) { finishService(); return; }
        advanceExit();
        if (status == null || stopping) return;
        int state = adapterState();
        boolean on = state == BluetoothAdapter.STATE_ON;
        boolean changing = disabling || (adapterControl != null && adapterControl.busy());
        String bt = changing ? "waiting" : on ? "ON" : state == BluetoothAdapter.STATE_OFF ? "OFF" : "waiting";
        bluetoothButton.setText("BT " + bt);
        bluetoothButton.setTextColor(on ? 0xFF87E5B0 : Color.WHITE);
        bluetoothButton.setEnabled(canControl(BLUETOOTH));
        bluetoothButton.setAlpha(bluetoothButton.isEnabled() ? 1f : .4f);
        sourceButton.setText(source.busy() ? "Audio …" : source.current() < 0 ? "Audio ?" : source.active() ? "Audio ON" : "Audio OFF");
        sourceButton.setTextColor(source.active() ? 0xFF87E5B0 : Color.WHITE);
        sourceButton.setEnabled(canControl(AUDIO));
        sourceButton.setAlpha(sourceButton.isEnabled() ? 1f : .4f);
        settingsButton.setTextColor(SettingsWindow.INSTANCE.open() ? 0xFF87E5B0 : Color.WHITE);
        settingsButton.setEnabled(canControl(SETTINGS));
        settingsButton.setAlpha(settingsButton.isEnabled() ? 1f : .4f);
        play.setText(playback.playing() ? "Ⅱ" : "▶");
        enable(previous, PREVIOUS);
        enable(play, PLAY_PAUSE);
        enable(stop, STOP);
        enable(next, NEXT);
        status.setText(closing ? "Turning Bluetooth and Audio off…" : "⋮⋮  Bluetooth " + bt + (receiverDevice == null || receiverDevice.name().isEmpty()
                ? "" : " · " + receiverDevice.name()) + " · " + playback.summary());
    }
    private void enable(Button button, String action) {
        button.setEnabled(canControl(action));
        button.setAlpha(button.isEnabled() ? 1f : .35f);
    }
    private void error(String text) {
        Log.w("JoyingBluetooth", text);
        if (!destroyed) Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        SettingsWindow.INSTANCE.remove(settingsListener);
        SettingsWindow.INSTANCE.launchFailed();
        if (audioSession != null) audioSession.close();
        if (adapterControl != null) adapterControl.cancel();
        if (receiverRegistered) unregisterReceiver(receiver);
        if (receiverDevice != null) receiverDevice.close();
        if (playback != null) playback.close();
        if (wire != null) wire.close();
        if (attached && windows != null) { try { windows.removeViewImmediate(bar); } catch (RuntimeException ignored) {} }
        attached = false;
        OverlayPresence.INSTANCE.set(false);
        stopForeground(true);
        super.onDestroy();
    }
}
