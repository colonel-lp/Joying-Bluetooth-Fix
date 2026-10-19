package com.joying.bluetoothsettings;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import java.util.HashMap;

/** Versioned overlay-only link for EQ & DSP; never selects an audio source. */
public final class ControlLinkService extends Service {
    static final int VERSION=1, WATCH=1, OPEN=2, STATE=3, ERROR=4, UNWATCH=5, CLOSE=6;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HashMap<IBinder,Client> clients=new HashMap<>();
    private final OverlayPresence.Listener presence=visible->{for(Client c:clients.values().toArray(new Client[0]))c.send(STATE,visible?1:0);};
    private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper(),message->{receive(message);return true;}));
    @Override public void onCreate(){super.onCreate();OverlayPresence.INSTANCE.add(presence);}
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    private boolean allowed(int uid){
        String[] packages=getPackageManager().getPackagesForUid(uid);
        if(packages!=null)for(String p:packages)if("com.eqdsp.controller".equals(p))return true;
        return false;
    }
    private void receive(Message message){
        if(!allowed(message.sendingUid)||message.arg1!=VERSION||message.replyTo==null)return;
        IBinder binder=message.replyTo.getBinder();
        Client client=clients.get(binder);
        if(message.what==WATCH){
            if(client==null){client=new Client(message.replyTo);try{binder.linkToDeath(client,0);clients.put(binder,client);}catch(RemoteException dead){return;}}
            client.send(STATE,OverlayPresence.INSTANCE.visible()?1:0);return;
        }
        if(message.what==UNWATCH){if(client!=null)client.remove();return;}
        if(client==null)return;
        if(message.what==CLOSE){
            if(!OverlayPresence.INSTANCE.visible())client.send(STATE,0);
            else if(!OverlayPresence.INSTANCE.requestClose())client.send(ERROR,2);
            return;
        }
        if(message.what!=OPEN)return;
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){client.send(ERROR,1);return;}
        if(OverlayPresence.INSTANCE.visible()){client.send(STATE,1);return;}
        try{
            Intent service=new Intent(this,FloatingBarService.class);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
        }catch(RuntimeException failure){client.send(ERROR,2);}
    }
    private final class Client implements IBinder.DeathRecipient {
        final Messenger messenger;
        Client(Messenger messenger){this.messenger=messenger;}
        void send(int kind,int value){
            Message message=Message.obtain(null,kind,VERSION,value);
            try{messenger.send(message);}catch(RemoteException dead){remove();}
        }
        void remove(){IBinder binder=messenger.getBinder();if(clients.remove(binder)!=null)binder.unlinkToDeath(this,0);}
        @Override public void binderDied(){main.post(this::remove);}
    }
    @Override public void onDestroy(){
        OverlayPresence.INSTANCE.remove(presence);
        for(Client c:clients.values().toArray(new Client[0]))c.remove();
        main.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
