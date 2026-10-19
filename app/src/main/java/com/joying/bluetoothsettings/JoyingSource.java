package com.joying.bluetoothsettings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

/** Small wire client for the existing main-module source command and update 0. */
final class JoyingSource {
    interface Listener { void source(int value); void disconnected(); void error(String message); }
    private static final String MODULE = "com.syu.ipc.IRemoteModule";
    private static final String CALLBACK = "com.syu.ipc.IModuleCallback";
    private final Context context;
    private final Handler main;
    private final Listener listener;
    private IBinder remote;
    private boolean bound, closed;
    private final Binder callback = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) reply.writeString(CALLBACK);
                return true;
            }
            if (code != 1) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(CALLBACK);
            int update = data.readInt();
            int[] values = data.createIntArray();
            data.createFloatArray();
            data.createStringArray();
            if (update == 0 && values != null && values.length > 0) {
                int source = values[0];
                main.post(() -> { if (!closed && remote != null) listener.source(source); });
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (closed) return;
            remote = binder;
            if (!subscribe(true)) listener.error("Joying source readback unavailable");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            remote = null;
            listener.disconnected();
            listener.error("Joying service disconnected");
        }
        @Override public void onBindingDied(ComponentName name) { reconnect(); }
        @Override public void onNullBinding(ComponentName name) {
            listener.error("Joying source service returned no connection");
        }
    };
    JoyingSource(Context context, Handler main, Listener listener) {
        this.context = context; this.main = main; this.listener = listener;
    }
    void connect() {
        if (bound || closed) return;
        Intent intent = new Intent("com.syu.ms.main")
                .setComponent(new ComponentName("com.syu.ms", "app.ModuleService"));
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) listener.error("Joying source service unavailable");
        } catch (RuntimeException failure) { listener.error("Joying source binding denied"); }
    }
    void reconnect() {
        detach();
        listener.disconnected();
        connect();
    }
    private boolean subscribe(boolean register) {
        if (remote == null) return false;
        Parcel request = Parcel.obtain();
        try {
            request.writeInterfaceToken(MODULE);
            request.writeStrongBinder(callback);
            request.writeInt(0);
            if (register) request.writeInt(1); // flush current source immediately
            return remote.transact(register ? 3 : 4, request, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException | RuntimeException failure) {
            Log.w("JoyingBluetooth", "Source subscription failed", failure);
            return false;
        } finally { request.recycle(); }
    }
    boolean select(int source) {
        if (remote == null) return false;
        Parcel request = Parcel.obtain();
        try {
            request.writeInterfaceToken(MODULE);
            request.writeInt(0);
            request.writeIntArray(new int[]{source});
            request.writeFloatArray(null);
            request.writeStringArray(null);
            return remote.transact(1, request, null, IBinder.FLAG_ONEWAY);
        } catch (RemoteException | RuntimeException failure) {
            Log.w("JoyingBluetooth", "Source command failed", failure);
            return false;
        } finally { request.recycle(); }
    }
    private void detach() {
        subscribe(false);
        remote = null;
        if (bound) { try { context.unbindService(connection); } catch (RuntimeException ignored) {} }
        bound = false;
    }
    void close() { closed = true; detach(); }
}
