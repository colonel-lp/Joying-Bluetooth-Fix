package com.joying.bluetoothsettings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Root of a separate task containing the native Bluetooth settings screen. */
public final class BtSettingsActivity extends Activity {
    private static final int SETTINGS = 20;
    private boolean launched, closing;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        launched = saved != null && saved.getBoolean("launched", false);
        try {
            if (!launched) {
                launched = true;
                startActivityForResult(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), SETTINGS);
            }
            SettingsWindow.INSTANCE.attached(this, this::closeTask);
        } catch (RuntimeException failure) {
            SettingsWindow.INSTANCE.launchFailed();
            Toast.makeText(this, "Bluetooth settings unavailable", Toast.LENGTH_LONG).show();
            finish();
        }
    }
    private void closeTask() {
        if (closing) return;
        closing = true;
        try {
            if (isTaskRoot()) finishAndRemoveTask();
            else { finishActivity(SETTINGS); finish(); }
        } catch (RuntimeException failure) {
            closing = false;
            throw failure;
        }
    }
    @Override protected void onSaveInstanceState(Bundle saved) {
        saved.putBoolean("launched", launched);
        super.onSaveInstanceState(saved);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SETTINGS) return;
        SettingsWindow.INSTANCE.detached(this);
        try { closeTask(); } catch (RuntimeException failure) { finish(); }
    }
    @Override protected void onDestroy() {
        SettingsWindow.INSTANCE.detached(this);
        super.onDestroy();
    }
}
