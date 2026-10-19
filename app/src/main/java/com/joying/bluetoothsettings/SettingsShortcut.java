package com.joying.bluetoothsettings;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Requests Bluetooth enablement when needed, then opens the existing settings screen. */
public class SettingsShortcut extends Activity {
    private static final int REQUEST_ENABLE = 1;
    private static final String WAITING_FOR_ENABLE = "waitingForEnable";
    private boolean waitingForEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        waitingForEnable = savedInstanceState != null
                && savedInstanceState.getBoolean(WAITING_FOR_ENABLE, false);
        // Android retains the outstanding activity result across recreation.
        if (waitingForEnable) {
            return;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                fail("Bluetooth is unavailable.");
            } else if (adapter.isEnabled()) {
                openSettings();
            } else {
                // isEnabled() is false in the Joying's stuck BLE_ON state too.
                waitingForEnable = true;
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                        REQUEST_ENABLE);
            }
        } catch (RuntimeException unavailable) {
            waitingForEnable = false;
            fail("Bluetooth enable request is unavailable.");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(WAITING_FOR_ENABLE, waitingForEnable);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_ENABLE || !waitingForEnable) {
            return;
        }
        waitingForEnable = false;
        if (resultCode != RESULT_OK) {
            finish();
            return;
        }
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                openSettings();
            } else {
                fail("Bluetooth did not finish enabling. Reopen this app to try again.");
            }
        } catch (RuntimeException unavailable) {
            fail("Bluetooth state is unavailable.");
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            fail("Bluetooth Settings is unavailable.");
            return;
        }
        finish();
    }

    private void fail(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
