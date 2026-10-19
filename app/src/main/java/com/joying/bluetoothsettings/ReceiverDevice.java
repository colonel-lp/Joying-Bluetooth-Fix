package com.joying.bluetoothsettings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import java.util.List;

/** Name of the native receiver's single connected sender. */
final class ReceiverDevice {
    private static final int SINK = 11; // A2DP_SINK on Android 8.1.
    private static final String CONNECTION = "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED";
    private final Context context;
    private final BluetoothAdapter adapter;
    private final Runnable changed;
    private BluetoothProfile proxy;
    private boolean registered, requested, connected, closed;
    private String name = "";
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (closed) return;
            connect();
            refresh();
        }
    };
    private final BluetoothProfile.ServiceListener listener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile service) {
            if (profile != SINK) return;
            if (closed) {
                try { adapter.closeProfileProxy(SINK, service); } catch (RuntimeException ignored) {}
                return;
            }
            proxy = service;
            requested = connected = true;
            refresh();
        }
        @Override public void onServiceDisconnected(int profile) {
            if (profile != SINK || closed) return;
            connected = false;
            setName("");
        }
    };
    ReceiverDevice(Context context, BluetoothAdapter adapter, Runnable changed) {
        this.context = context;
        this.adapter = adapter;
        this.changed = changed;
    }
    void start() {
        if (closed || registered || adapter == null) return;
        IntentFilter filter = new IntentFilter(CONNECTION);
        filter.addAction(BluetoothDevice.ACTION_NAME_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        try {
            context.registerReceiver(receiver, filter);
            registered = true;
            connect();
        } catch (RuntimeException failure) { Log.w("JoyingBluetooth", "Receiver name unavailable", failure); }
    }
    private void connect() {
        if (closed || requested || adapter == null) return;
        try {
            if (adapter.getState() == BluetoothAdapter.STATE_ON)
                requested = adapter.getProfileProxy(context, listener, SINK);
        }
        catch (RuntimeException failure) { requested = false; }
    }
    private void refresh() {
        String value = "";
        try {
            if (adapter != null && adapter.getState() == BluetoothAdapter.STATE_ON && connected && proxy != null) {
                List<BluetoothDevice> devices = proxy.getConnectedDevices();
                if (devices != null && devices.size() == 1 && devices.get(0) != null) {
                    String deviceName = devices.get(0).getName();
                    if (deviceName != null) value = deviceName.replace('\n', ' ').replace('\r', ' ').trim();
                }
            }
        } catch (RuntimeException failure) { Log.w("JoyingBluetooth", "Receiver name unavailable", failure); }
        setName(value);
    }
    private void setName(String value) {
        if (name.equals(value)) return;
        name = value;
        changed.run();
    }
    String name() { return name; }
    void close() {
        closed = true;
        if (registered) {
            try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) {}
            registered = false;
        }
        if (adapter != null && connected && proxy != null) {
            try { adapter.closeProfileProxy(SINK, proxy); } catch (RuntimeException ignored) {}
        }
        proxy = null;
        name = "";
    }
}
