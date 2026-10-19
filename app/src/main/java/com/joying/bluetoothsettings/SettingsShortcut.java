package com.joying.bluetoothsettings;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Permission/recovery trampoline for the foreground floating bar. */
public class SettingsShortcut extends Activity {
    static final String ENABLE = "com.joying.bluetoothsettings.ENABLE";
    private static final int OVERLAY = 10, BLUETOOTH = 11;
    private int waiting;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        waiting = saved == null ? 0 : saved.getInt("waiting", 0);
        if (waiting != 0) return;
        if (ENABLE.equals(getIntent().getAction())) requestBluetooth();
        else launchBar();
    }
    private void launchBar() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                waiting = OVERLAY;
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), OVERLAY);
            } catch (RuntimeException failure) { fail("Open Android Settings and allow display over other apps for Bluetooth Controls"); }
            return;
        }
        try {
            Intent service = new Intent(this, FloatingBarService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
            finish();
        } catch (RuntimeException failure) { fail("Floating controls could not start"); }
    }
    private void requestBluetooth() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) fail("Bluetooth adapter unavailable");
            else if (adapter.isEnabled()) finish();
            else {
                waiting = BLUETOOTH;
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BLUETOOTH);
            }
        } catch (RuntimeException failure) { fail("Bluetooth enable request unavailable"); }
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("waiting", waiting); super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != waiting || waiting == 0) return;
        waiting = 0;
        if (request == OVERLAY) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) launchBar();
            else fail("Display-over-other-apps permission was not granted");
        } else {
            if (result == RESULT_OK) {
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    if (adapter == null || !adapter.isEnabled()) Toast.makeText(this,
                            "Bluetooth is still enabling; check the bar status", Toast.LENGTH_LONG).show();
                } catch (RuntimeException failure) { fail("Bluetooth state unavailable"); return; }
            }
            finish(); // Cancel never loops back to the enable prompt.
        }
    }
    private void fail(String text) { waiting = 0; Toast.makeText(this, text, Toast.LENGTH_LONG).show(); finish(); }
}
