package com.joying.bluetoothsettings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Opens the existing Android Bluetooth screen and closes this launcher activity. */
public class SettingsShortcut extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException unavailable) {
            Toast.makeText(this, "Bluetooth Settings is unavailable.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
