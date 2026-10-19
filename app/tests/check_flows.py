"""Exercise real launcher/playback classes with host stand-ins, not a radio emulator."""
from pathlib import Path
import subprocess
import tempfile
from run_checks import APP, JAVA, compile_java

STUBS = {
'android/content/Context.java': '''package android.content; public class Context {}''',
'android/content/ComponentName.java': '''package android.content; public class ComponentName { public final String pkg,cls; public ComponentName(String p,String c){pkg=p;cls=c;} }''',
'android/content/Intent.java': '''package android.content;
public class Intent {
 public String action; public Class<?> type;
 public Intent(){} public Intent(String a){action=a;} public Intent(String a,android.net.Uri u){action=a;}
 public Intent(Context c,Class<?> t){type=t;} public String getAction(){return action;}
 public Intent setAction(String a){action=a;return this;}
}''',
'android/net/Uri.java': '''package android.net; public class Uri { public static Uri parse(String s){return new Uri();} }''',
'android/os/Bundle.java': '''package android.os; public class Bundle { private int n; public int getInt(String k,int d){return n;} public void putInt(String k,int v){n=v;} }''',
'android/os/Build.java': '''package android.os; public class Build { public static class VERSION { public static int SDK_INT=27;} }''',
'android/os/Handler.java': '''package android.os; public class Handler { public Runnable pending; public void removeCallbacks(Runnable r){if(pending==r)pending=null;} public void postDelayed(Runnable r,long d){pending=r;} }''',
'android/provider/Settings.java': '''package android.provider; import android.content.Context; public class Settings { public static final String ACTION_MANAGE_OVERLAY_PERMISSION="overlay"; public static boolean allowed=true; public static boolean canDrawOverlays(Context c){return allowed;} }''',
'android/bluetooth/BluetoothAdapter.java': '''package android.bluetooth; public class BluetoothAdapter { public static final String ACTION_REQUEST_ENABLE="enable"; public static BluetoothAdapter instance=new BluetoothAdapter(); public boolean enabled; public static BluetoothAdapter getDefaultAdapter(){return instance;} public boolean isEnabled(){return enabled;} }''',
'android/app/Activity.java': '''package android.app; import android.content.*; import android.os.Bundle;
public class Activity extends Context {
 public static final int RESULT_OK=-1,RESULT_CANCELED=0; public boolean finished; public int requests,starts,lastCode; public Intent intent=new Intent();
 protected void onCreate(Bundle b){} protected void onSaveInstanceState(Bundle b){} protected void onActivityResult(int r,int c,Intent i){}
 public Intent getIntent(){return intent;} public String getPackageName(){return "com.joying.bluetoothsettings";}
 public void startActivityForResult(Intent i,int r){requests++;lastCode=r;} public void startForegroundService(Intent i){starts++;} public void startService(Intent i){starts++;} public void finish(){finished=true;}
}''',
'android/widget/Toast.java': '''package android.widget; import android.content.Context; public class Toast { public static final int LENGTH_LONG=1; public static Toast makeText(Context c,String s,int n){return new Toast();} public void show(){} }''',
'android/util/Log.java': '''package android.util; public class Log { public static int w(String t,String m,Throwable e){return 0;} }''',
'android/media/MediaDescription.java': '''package android.media; public class MediaDescription { public CharSequence getTitle(){return "Track";} }''',
'android/media/MediaMetadata.java': '''package android.media; public class MediaMetadata { public MediaDescription getDescription(){return new MediaDescription();} }''',
'android/media/session/PlaybackState.java': '''package android.media.session; public class PlaybackState {
 public static final long ACTION_STOP=1,ACTION_PAUSE=2,ACTION_PLAY=4,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SKIP_TO_NEXT=32;
 public static final int STATE_NONE=0,STATE_STOPPED=1,STATE_PAUSED=2,STATE_PLAYING=3,STATE_ERROR=7; public long actions=54; public int state=3;
 public long getActions(){return actions;} public int getState(){return state;}
}''',
'android/media/session/MediaController.java': '''package android.media.session;
import android.content.Context; import android.media.MediaMetadata; import android.os.Handler;
public class MediaController {
 public static MediaController last; public Callback callback; public String command=""; public PlaybackState state=new PlaybackState();
 public MediaController(Context c,Object token){last=this;} public void registerCallback(Callback c,Handler h){callback=c;} public void unregisterCallback(Callback c){}
 public PlaybackState getPlaybackState(){return state;} public MediaMetadata getMetadata(){return new MediaMetadata();}
 public TransportControls getTransportControls(){return new TransportControls();}
 public class TransportControls { public void play(){command="play";} public void pause(){command="pause";} public void stop(){command="stop";} public void skipToNext(){command="next";} public void skipToPrevious(){command="previous";} }
 public static class Callback { public void onPlaybackStateChanged(PlaybackState s){} public void onMetadataChanged(MediaMetadata m){} public void onSessionDestroyed(){} }
}''',
'android/media/browse/MediaBrowser.java': '''package android.media.browse; import android.content.*; import android.os.Bundle;
public class MediaBrowser {
 public static MediaBrowser last; public ComponentName component; public ConnectionCallback callback; public boolean disconnected;
 public MediaBrowser(Context c,ComponentName n,ConnectionCallback b,Bundle x){last=this;component=n;callback=b;}
 public void connect(){} public void disconnect(){disconnected=true;} public Object getSessionToken(){return new Object();}
 public static class ConnectionCallback { public void onConnected(){} public void onConnectionSuspended(){} public void onConnectionFailed(){} }
}''',
'com/joying/bluetoothsettings/FloatingBarService.java': '''package com.joying.bluetoothsettings; public class FloatingBarService {}''',
'com/joying/bluetoothsettings/FlowChecks.java': '''package com.joying.bluetoothsettings;
import android.content.*; import android.os.*; import android.bluetooth.BluetoothAdapter; import android.provider.Settings;
import android.media.browse.MediaBrowser; import android.media.session.*;
public class FlowChecks {
 static int cases; static void check(boolean x,String s){if(!x)throw new AssertionError(s);}
 public static void main(String[] args){
  Settings.allowed=true; BluetoothAdapter.instance.enabled=false;
  SettingsShortcut a=new SettingsShortcut(); a.onCreate(null);
  check(a.starts==1&&a.requests==0&&a.finished,"launcher shows bar without silently enabling adapter");cases++;
  Settings.allowed=false; a=new SettingsShortcut();a.onCreate(null);
  check(a.requests==1&&a.starts==0&&!a.finished,"overlay permission waits");
  Settings.allowed=true;a.onActivityResult(a.lastCode,0,null);check(a.starts==1&&a.finished,"overlay grant starts bar");cases++;
  Settings.allowed=false;a=new SettingsShortcut();a.onCreate(null);a.onActivityResult(a.lastCode,0,null);
  check(a.finished&&a.starts==0&&a.requests==1,"denied overlay never loops");cases++;
  a=new SettingsShortcut();a.intent.setAction(SettingsShortcut.ENABLE);a.onCreate(null);
  check(a.requests==1&&!a.finished,"enable button awaits Android confirmation");
  Bundle saved=new Bundle();a.onSaveInstanceState(saved);SettingsShortcut recreated=new SettingsShortcut();recreated.intent.setAction(SettingsShortcut.ENABLE);recreated.onCreate(saved);
  check(recreated.requests==0&&!recreated.finished,"recreation preserves one pending prompt");
  recreated.onActivityResult(a.lastCode,0,null);check(recreated.finished&&recreated.requests==0,"cancel exits");cases++;
  BluetoothAdapter.instance.enabled=true;a=new SettingsShortcut();a.intent.setAction(SettingsShortcut.ENABLE);a.onCreate(null);
  check(a.finished&&a.requests==0,"already enabled does not prompt");cases++;
  Handler handler=new Handler();final int[] errors={0};
  NativePlayback p=new NativePlayback(new Context(),handler,new NativePlayback.Listener(){public void changed(){}public void error(String s){errors[0]++;}});
  p.setEnabled(true);MediaBrowser first=MediaBrowser.last;
  check(first.component.pkg.equals("com.android.bluetooth")&&first.component.cls.endsWith(".a2dpsink.mbs.A2dpMediaBrowserService"),"explicit native receiver component");cases++;
  first.callback.onConnected();MediaController initial=MediaController.last;
  check(p.ready()&&p.playing()&&p.supports(PlaybackState.ACTION_STOP),"native Stop handler is available despite omitted action bit");cases++;
  p.send(PlaybackState.ACTION_PAUSE);check(initial.command.equals("pause"),"pause uses native token");
  p.send(PlaybackState.ACTION_SKIP_TO_NEXT);check(initial.command.equals("next"),"next uses native token");cases++;
  p.send(PlaybackState.ACTION_STOP);check(errors[0]==0&&initial.command.equals("stop"),"Stop uses the native handler");
  initial.state.actions=4; p.send(PlaybackState.ACTION_SKIP_TO_NEXT);check(errors[0]==1&&initial.command.equals("stop"),"other unavailable actions stay blocked"); initial.state.actions=54;cases++;
  p.reconnect();MediaBrowser replacement=MediaBrowser.last;replacement.callback.onConnected();MediaController second=MediaController.last;
  first.callback.onConnectionFailed();initial.callback.onSessionDestroyed();check(p.ready()&&MediaController.last==second&&!replacement.disconnected,"stale callback cannot disconnect replacement");cases++;
  p.setEnabled(false);check(!p.ready()&&replacement.disconnected&&handler.pending==null,"off drops session and retry");cases++;
  p.setEnabled(true);MediaBrowser.last.callback.onConnectionFailed();check(handler.pending!=null,"failed binding schedules bounded retry");
  p.close();check(handler.pending==null&&!p.ready(),"close cancels reconnect");cases++;
  System.out.println(cases+" launcher/native-playback scenarios passed (host stand-ins).");
 }
}''',
}

def main():
    with tempfile.TemporaryDirectory(prefix='joying-bt-flows-') as temp:
        root = Path(temp)
        files = [JAVA / 'NativePlayback.java', JAVA / 'SettingsShortcut.java']
        for name, text in STUBS.items():
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
            files.append(p)
        classes = root / 'classes'
        classes.mkdir()
        compile_java(files, classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.FlowChecks'], check=True)

if __name__ == '__main__':
    main()
