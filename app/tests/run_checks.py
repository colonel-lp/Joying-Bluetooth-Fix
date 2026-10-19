"""Compile and exercise the real Activity with host Android stand-ins; no APK/SDK."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET


APP = Path(__file__).resolve().parents[1]
SOURCE = APP / 'src/main/java/com/joying/bluetoothsettings/SettingsShortcut.java'
STUBS = {
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/content/Intent.java': '''package android.content;
public class Intent {
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public final String action; public int flags;
    public Intent(String action) { this.action = action; }
    public Intent addFlags(int value) { flags |= value; return this; }
}''',
    'android/os/Bundle.java': '''package android.os;
public class Bundle {
    private final java.util.Map<String, Boolean> values = new java.util.HashMap<>();
    public void putBoolean(String key, boolean value) { values.put(key, value); }
    public boolean getBoolean(String key, boolean fallback) {
        return values.containsKey(key) ? values.get(key) : fallback;
    }
}''',
    'android/app/Activity.java': '''package android.app;
import android.content.*; import android.os.Bundle;
public class Activity extends Context {
    public static final int RESULT_OK = -1, RESULT_CANCELED = 0;
    public boolean finished, failStart, failRequest;
    public int starts, requests, requestCode;
    public Intent started, requested;
    protected void onCreate(Bundle state) {}
    protected void onSaveInstanceState(Bundle state) {}
    protected void onActivityResult(int request, int result, Intent data) {}
    public void startActivity(Intent intent) {
        if (failStart) throw new RuntimeException("settings unavailable");
        starts++; started = intent;
    }
    public void startActivityForResult(Intent intent, int code) {
        if (failRequest) throw new RuntimeException("request unavailable");
        requests++; requested = intent; requestCode = code;
    }
    public void finish() { finished = true; }
}''',
    'android/bluetooth/BluetoothAdapter.java': '''package android.bluetooth;
public class BluetoothAdapter {
    public static final String ACTION_REQUEST_ENABLE = "android.bluetooth.adapter.action.REQUEST_ENABLE";
    public static BluetoothAdapter current;
    public boolean enabled, failRead;
    public static BluetoothAdapter getDefaultAdapter() { return current; }
    public boolean isEnabled() {
        if (failRead) throw new SecurityException("state unavailable");
        return enabled;
    }
}''',
    'android/widget/Toast.java': '''package android.widget;
import android.content.Context;
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static String last;
    public static Toast makeText(Context context, CharSequence text, int duration) {
        last = text.toString(); return new Toast();
    }
    public void show() {}
}''',
    'com/joying/bluetoothsettings/LauncherChecks.java': '''package com.joying.bluetoothsettings;
import android.bluetooth.BluetoothAdapter;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class LauncherChecks {
    private static int scenarios;
    private static SettingsShortcut create(boolean enabled) {
        BluetoothAdapter.current = new BluetoothAdapter();
        BluetoothAdapter.current.enabled = enabled;
        Toast.last = null;
        return new SettingsShortcut();
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void settingsOpened(SettingsShortcut app) {
        require(app.finished && app.starts == 1, "settings opened once and launcher closed");
        require("android.settings.BLUETOOTH_SETTINGS".equals(app.started.action), "settings action");
        require((app.started.flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0, "settings task flag");
    }
    private static void failed(SettingsShortcut app) {
        require(app.finished && app.starts == 0 && Toast.last != null, "visible error and exit");
    }
    public static void main(String[] args) {
        SettingsShortcut app = create(true);
        app.onCreate(null); settingsOpened(app);
        require(app.requests == 0, "already ON does not ask to enable"); scenarios++;

        app = create(false); app.onCreate(null);
        require(app.requests == 1 && !app.finished && app.starts == 0, "wait for enable result");
        require(BluetoothAdapter.ACTION_REQUEST_ENABLE.equals(app.requested.action), "public enable action");
        BluetoothAdapter.current.enabled = true;
        app.onActivityResult(app.requestCode, Activity.RESULT_OK, null); settingsOpened(app); scenarios++;

        app = create(false); app.onCreate(null);
        app.onActivityResult(app.requestCode, Activity.RESULT_CANCELED, null);
        require(app.finished && app.starts == 0 && app.requests == 1, "cancel exits without retry"); scenarios++;

        app = create(false); app.onCreate(null);
        app.onActivityResult(app.requestCode, Activity.RESULT_OK, null); failed(app); scenarios++;

        app = create(false); BluetoothAdapter.current = null;
        app.onCreate(null); failed(app); scenarios++;

        app = create(false); app.failRequest = true;
        app.onCreate(null); failed(app); scenarios++;

        app = create(false); BluetoothAdapter.current.failRead = true;
        app.onCreate(null); failed(app); scenarios++;

        app = create(true); app.failStart = true;
        app.onCreate(null); failed(app); scenarios++;

        app = create(false); app.onCreate(null);
        BluetoothAdapter.current.failRead = true;
        app.onActivityResult(app.requestCode, Activity.RESULT_OK, null); failed(app); scenarios++;

        app = create(false); app.onCreate(null);
        Bundle state = new Bundle(); app.onSaveInstanceState(state);
        SettingsShortcut recreated = new SettingsShortcut(); recreated.onCreate(state);
        require(recreated.requests == 0 && !recreated.finished, "recreation preserves request without duplicate");
        BluetoothAdapter.current.enabled = true;
        recreated.onActivityResult(app.requestCode, Activity.RESULT_OK, null); settingsOpened(recreated); scenarios++;

        recreated.onActivityResult(app.requestCode, Activity.RESULT_OK, null);
        require(recreated.starts == 1, "duplicate callback ignored"); scenarios++;

        app = create(false); app.onCreate(null);
        app.onActivityResult(999, Activity.RESULT_OK, null);
        require(!app.finished && app.starts == 0, "unrelated callback does not consume request");
        BluetoothAdapter.current.enabled = true;
        app.onActivityResult(app.requestCode, Activity.RESULT_OK, null); settingsOpened(app); scenarios++;

        app = create(false); app.onCreate(null);
        app.onActivityResult(app.requestCode, Activity.RESULT_CANCELED, null);
        app = create(false); app.onCreate(null);
        require(app.requests == 1 && !app.finished, "new launch can request after previous cancellation"); scenarios++;
        System.out.println(scenarios + " Activity flow scenarios passed (host stand-ins).");
    }
}''',
}


def main():
    ns = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(APP / 'src/main/AndroidManifest.xml').getroot()
    permissions = [item.get(ns + 'name') for item in manifest.findall('uses-permission')]
    assert permissions == ['android.permission.BLUETOOTH']
    activity = manifest.find('application/activity')
    assert activity.get(ns + 'noHistory') != 'true'
    assert activity.get(ns + 'theme') == '@android:style/Theme.Translucent.NoTitleBar'
    with tempfile.TemporaryDirectory(prefix='joying-bt-checks-') as temporary:
        root = Path(temporary)
        sources = [str(SOURCE)]
        for name, source in STUBS.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
            sources.append(str(path))
        classes = root / 'classes'
        classes.mkdir()
        subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '8',
                        '-d', str(classes), *sources], check=True)
        subprocess.run(['java', '-cp', str(classes),
                        'com.joying.bluetoothsettings.LauncherChecks'], check=True)
    print('Manifest permission and lifecycle checks passed. No Android SDK/APK build performed.')


if __name__ == '__main__':
    main()
