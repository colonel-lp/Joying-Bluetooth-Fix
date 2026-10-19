"""Exercise the production foreground service with host platform stand-ins."""
from pathlib import Path
import subprocess
import tempfile
from run_checks import JAVA, compile_java

STUBS = {
'android/content/Context.java': '''package android.content;
public class Context {
 public static final String NOTIFICATION_SERVICE="notification",WINDOW_SERVICE="window";public static final int MODE_PRIVATE=0;
 public final android.app.NotificationManager notifications=new android.app.NotificationManager();public final android.view.WindowManager windowManager=new android.view.WindowManager();
 public final SharedPreferences prefs=new SharedPreferences();public BroadcastReceiver receiver;
 public Object getSystemService(String s){return NOTIFICATION_SERVICE.equals(s)?notifications:windowManager;}
 public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){receiver=r;return null;}public void unregisterReceiver(BroadcastReceiver r){receiver=null;}
 public Intent lastActivity;public void startActivity(Intent i){lastActivity=i;}public String getPackageName(){return "com.joying.bluetoothsettings";}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public void broadcast(){if(receiver!=null)receiver.onReceive(this,new Intent());}
}''',
'android/content/Intent.java': '''package android.content;public class Intent {public String action;public Class<?> type;public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_CLEAR_TASK=2;public int flags;
 public Intent(){}public Intent(String a){action=a;}public Intent(Context c,Class<?> t){type=t;}public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}public Intent addFlags(int f){flags|=f;return this;}}
''',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public static class NameNotFoundException extends Exception {}public PackageInfo getPackageInfo(String p,int f)throws NameNotFoundException{return new PackageInfo();}}',
'android/content/pm/PackageInfo.java':'package android.content.pm;public class PackageInfo {public String versionName="1.8";}',
'com/joying/bluetoothsettings/BtSettingsActivity.java':'package com.joying.bluetoothsettings;public class BtSettingsActivity {}',
'com/joying/bluetoothsettings/ReceiverDevice.java':'package com.joying.bluetoothsettings;class ReceiverDevice {static String sender="";ReceiverDevice(android.content.Context c,android.bluetooth.BluetoothAdapter b,Runnable r){}void start(){}String name(){return sender;}void close(){}}',
'android/content/IntentFilter.java':'package android.content;public class IntentFilter {public IntentFilter(String s){}}',
'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/content/SharedPreferences.java':'''package android.content;public class SharedPreferences {private java.util.Map<String,Integer> values=new java.util.HashMap<>();public int getInt(String k,int d){return values.getOrDefault(k,d);}public Editor edit(){return new Editor();}public class Editor {public Editor putInt(String k,int v){values.put(k,v);return this;}public boolean commit(){return true;}public void apply(){}}}''',
'android/content/res/Configuration.java':'package android.content.res;public class Configuration {}',
'android/content/res/Resources.java':'package android.content.res;public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/util/DisplayMetrics.java':'package android.util;public class DisplayMetrics {public float density=1;public int widthPixels=1024,heightPixels=600;}',
'android/util/Log.java':'package android.util;public class Log {public static int w(String a,String b){return 0;}public static int w(String a,String b,Throwable t){return 0;}public static int e(String a,String b,Throwable t){return 0;}}',
'android/app/Service.java':'''package android.app;import android.content.*;public class Service extends Context {public static final int START_NOT_STICKY=2;public boolean stopped,foreground;public int foregroundCalls;public void onCreate(){}public int onStartCommand(Intent i,int f,int id){return 2;}public android.os.IBinder onBind(Intent i){return null;}public void onDestroy(){}public void onConfigurationChanged(android.content.res.Configuration c){}public void startForeground(int id,Notification n){foreground=true;foregroundCalls++;notifications.notify(id,n);}public void stopForeground(boolean remove){foreground=false;if(remove)notifications.last=null;}public void stopSelf(){stopped=true;}}''',
'android/app/PendingIntent.java':'''package android.app;import android.content.*;public class PendingIntent {public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;public Intent intent;public boolean activity;static PendingIntent make(Intent i,boolean a){PendingIntent p=new PendingIntent();p.intent=i;p.activity=a;return p;}public static PendingIntent getActivity(Context c,int n,Intent i,int f){return make(i,true);}public static PendingIntent getService(Context c,int n,Intent i,int f){return make(i,false);}}''',
'android/app/NotificationChannel.java':'package android.app;public class NotificationChannel {public NotificationChannel(String a,String b,int i){}}',
'android/app/NotificationManager.java':'package android.app;public class NotificationManager {public static final int IMPORTANCE_LOW=2;public Notification last;public int updates;public void createNotificationChannel(NotificationChannel c){}public void notify(int id,Notification n){last=n;updates++;}}',
'android/app/Notification.java':'''package android.app;import android.content.Context;public class Notification {public static final int PRIORITY_LOW=-1;public String text;public boolean ongoing,onlyOnce;public PendingIntent content;public java.util.Map<String,PendingIntent> actions=new java.util.LinkedHashMap<>();public static class Builder {Notification n=new Notification();public Builder(Context c){}public Builder(Context c,String s){}public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String s){return this;}public Builder setContentText(String s){n.text=s;return this;}public Builder setOngoing(boolean b){n.ongoing=b;return this;}public Builder setOnlyAlertOnce(boolean b){n.onlyOnce=b;return this;}public Builder setShowWhen(boolean b){return this;}public Builder setPriority(int i){return this;}public Builder setContentIntent(PendingIntent p){n.content=p;return this;}public Builder addAction(int i,String s,PendingIntent p){n.actions.put(s,p);return this;}public Notification build(){return n;}}}''',
'android/os/IBinder.java':'package android.os;public interface IBinder {}',
'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=27;}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}',
'android/os/Handler.java':'''package android.os;public class Handler {public final java.util.List<Runnable> tasks=new java.util.ArrayList<>();public Handler(Looper l){}public void postDelayed(Runnable r,long d){tasks.add(r);}public void removeCallbacksAndMessages(Object o){tasks.clear();}}''',
'android/bluetooth/BluetoothAdapter.java':'''package android.bluetooth;public class BluetoothAdapter {public static final int ERROR=-1,STATE_OFF=10,STATE_TURNING_ON=11,STATE_ON=12,STATE_TURNING_OFF=13;public static final String ACTION_STATE_CHANGED="state";public static BluetoothAdapter instance;public int state=12,enables,disables;public boolean accept=true;public static BluetoothAdapter getDefaultAdapter(){return instance;}public int getState(){return state>=14?10:state;}public int getLeState(){return state;}public boolean enable(){enables++;if(accept)state=11;return accept;}public boolean disable(){disables++;if(accept)state=13;return accept;}}''',
'android/provider/Settings.java':'package android.provider;public class Settings {public static final String ACTION_BLUETOOTH_SETTINGS="bt";public static boolean allowed=true;public static boolean canDrawOverlays(android.content.Context c){return allowed;}}',
'android/graphics/Color.java':'package android.graphics;public class Color {public static final int WHITE=-1;}',
'android/graphics/PixelFormat.java':'package android.graphics;public class PixelFormat {public static final int TRANSLUCENT=-3;}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable {public void setColor(int c){}public void setCornerRadius(int r){}public void setStroke(int w,int c){}}',
'android/text/TextUtils.java':'package android.text;public class TextUtils {public enum TruncateAt {END}}',
'android/view/Gravity.java':'package android.view;public class Gravity {public static final int TOP=1,LEFT=2,CENTER_VERTICAL=4,RIGHT=8;}',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent {public static final int ACTION_DOWN=0,ACTION_MOVE=1,ACTION_UP=2,ACTION_CANCEL=3;public int getActionMasked(){return 0;}public float getRawX(){return 0;}public float getRawY(){return 0;}public long getEventTime(){return 0;}}',
'android/view/View.java':'''package android.view;public class View {public interface OnClickListener {void onClick(View v);}public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}public OnClickListener click;public boolean enabled=true;public void setOnClickListener(OnClickListener c){click=c;}public void setOnTouchListener(OnTouchListener t){}public boolean performClick(){if(click!=null)click.onClick(this);return true;}public void setPadding(int a,int b,int c,int d){}public void setContentDescription(String s){}public void setBackground(android.graphics.drawable.GradientDrawable b){}public void setEnabled(boolean b){enabled=b;}public boolean isEnabled(){return enabled;}public void setAlpha(float f){}}''',
'android/view/WindowManager.java':'''package android.view;public class WindowManager {public int adds,removes;public boolean failRemove;public View current;public void addView(View v,LayoutParams p){if(current!=null)throw new IllegalStateException("duplicate window");current=v;adds++;}public void removeViewImmediate(View v){if(failRemove)throw new IllegalStateException("remove");if(current!=v)throw new IllegalStateException("not attached");current=null;removes++;}public void updateViewLayout(View v,LayoutParams p){}public static class LayoutParams {public static final int TYPE_APPLICATION_OVERLAY=1,TYPE_PHONE=2,FLAG_NOT_FOCUSABLE=4,FLAG_NOT_TOUCH_MODAL=8;public int width,x,y,gravity;public LayoutParams(int w,int h,int t,int f,int p){width=w;}}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View {public String text;public int color;public TextView(android.content.Context c){}public void setTextColor(int c){color=c;}public void setTextSize(int s){}public void setSingleLine(boolean b){}public void setEllipsize(android.text.TextUtils.TruncateAt t){}public void setGravity(int g){}public void setText(String s){text=s;}}''',
'android/widget/Button.java':'package android.widget;public class Button extends TextView {public Button(android.content.Context c){super(c);}public void setAllCaps(boolean b){}public void setMinWidth(int v){}public void setMinimumWidth(int v){}public void setMinHeight(int v){}public void setMinimumHeight(int v){}}',
'android/widget/LinearLayout.java':'''package android.widget;public class LinearLayout extends android.view.View {public static final int VERTICAL=1;public java.util.List<android.view.View> children=new java.util.ArrayList<>();public LinearLayout(android.content.Context c){}public void setOrientation(int n){}public void addView(android.view.View v,LayoutParams p){children.add(v);}public static class LayoutParams {public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float f){}public void setMargins(int a,int b,int c,int d){}}}''',
'android/widget/Toast.java':'package android.widget;public class Toast {public static final int LENGTH_LONG=1;public static String last;public static Toast makeText(android.content.Context c,String s,int n){last=s;return new Toast();}public void show(){}}',
'android/media/session/PlaybackState.java':'package android.media.session;public class PlaybackState {public static final long ACTION_PLAY=4,ACTION_PAUSE=2,ACTION_STOP=1,ACTION_SKIP_TO_PREVIOUS=16,ACTION_SKIP_TO_NEXT=32;}',
'android/R.java':'package android;public class R {public static class drawable {public static final int ic_menu_view=1,ic_menu_close_clear_cancel=2;}}',
'com/joying/bluetoothsettings/R.java':'package com.joying.bluetoothsettings;public class R {public static class drawable {public static final int bluetooth_icon=1;}}',
'com/joying/bluetoothsettings/SettingsShortcut.java':'package com.joying.bluetoothsettings;public class SettingsShortcut {static final String ENABLE="enable";}',
'com/joying/bluetoothsettings/AudioFocusControl.java':'''package com.joying.bluetoothsettings;class AudioFocusControl {interface Listener {void lost();}static AudioFocusControl last;Listener listener;int acquired,released;boolean held;AudioFocusControl(android.content.Context c,android.os.Handler h,Listener l){last=this;listener=l;}boolean acquire(){acquired++;held=true;return true;}void release(){released++;held=false;}void lose(){listener.lost();}}''',
'com/joying/bluetoothsettings/NativePlayback.java':'''package com.joying.bluetoothsettings;class NativePlayback {interface Listener {void changed();void error(String s);}static NativePlayback last;int pauses;boolean enabled;Listener listener;NativePlayback(android.content.Context c,android.os.Handler h,Listener l){last=this;listener=l;}void setEnabled(boolean b){enabled=b;listener.changed();}void send(long a){}void pauseForRelease(){pauses++;}void reconnect(){}boolean playing(){return false;}boolean supports(long a){return enabled;}String summary(){return "Ready";}void close(){}}''',
'com/joying/bluetoothsettings/JoyingSource.java':'''package com.joying.bluetoothsettings;class JoyingSource {interface Listener {void source(int s);void disconnected();void error(String s);}static int initial=0;static JoyingSource last;Listener listener;boolean accept=true;java.util.List<Integer> selections=new java.util.ArrayList<>();JoyingSource(android.content.Context c,android.os.Handler h,Listener l){last=this;listener=l;}void connect(){if(initial>=0)listener.source(initial);}void update(int s){listener.source(s);}boolean select(int s){selections.add(s);return accept;}void reconnect(){listener.disconnected();connect();}void close(){}}''',
}

CHECKS = '''package com.joying.bluetoothsettings;
import android.bluetooth.BluetoothAdapter;import android.app.*;import android.content.Intent;import android.os.*;import android.provider.Settings;
public class ServiceChecks {
 static int cases;static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static FloatingBarService start(int bt,int src,boolean show){BluetoothAdapter.instance=new BluetoothAdapter();BluetoothAdapter.instance.state=bt;JoyingSource.initial=src;Settings.allowed=true;SystemClock.now=0;FloatingBarService s=new FloatingBarService();s.onCreate();if(show)s.onStartCommand(new Intent(),0,1);return s;}
 static void call(Object o,String method)throws Exception {java.lang.reflect.Method m=o.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(o);}
 static Handler handler(FloatingBarService s)throws Exception {java.lang.reflect.Field f=s.getClass().getDeclaredField("main");f.setAccessible(true);return (Handler)f.get(s);}
 static void action(FloatingBarService s,String name){PendingIntent p=s.notifications.last.actions.get(name);check(p!=null,"notification action exists: "+name);s.onStartCommand(p.intent,0,2);}
 static void hide(FloatingBarService s){s.onStartCommand(new Intent().setAction(FloatingBarService.CLOSE),0,3);}
 static void done(FloatingBarService s){s.onDestroy();check(!OverlayPresence.INSTANCE.visible()&&!s.foreground,"destroy removes window presence and notification");}
 public static void main(String[] args)throws Exception {
  FloatingBarService s=start(12,0,true);
  check(s.foreground&&s.foregroundCalls==1&&s.windowManager.adds==1&&OverlayPresence.INSTANCE.visible(),"foreground notification precedes one attached window");
  check(s.notifications.last.ongoing&&s.notifications.last.onlyOnce&&s.notifications.last.text.equals("BT ON · Audio OFF"),"ongoing quiet notification shows both states");cases++;
  int releases=AudioFocusControl.last.released;hide(s);
  check(!s.stopped&&!OverlayPresence.INSTANCE.visible()&&s.windowManager.current==null&&NativePlayback.last.pauses==0&&AudioFocusControl.last.released==releases&&BluetoothAdapter.instance.disables==0,"hide with BT ON changes no audio or radio state");cases++;
  s.onStartCommand(new Intent(),0,4);s.onStartCommand(new Intent(),0,5);
  check(s.windowManager.adds==2&&OverlayPresence.INSTANCE.visible(),"reopen and repeated launch reuse a single window");
  check(s.notifications.last.content.activity&&s.notifications.last.actions.get("Show controls").activity,"notification tap and Show use permission trampoline");done(s);cases++;
  s=start(12,0,true);call(s,"toggleAudio");JoyingSource.last.update(3);int writes=JoyingSource.last.selections.size();
  hide(s);check(AudioFocusControl.last.held&&NativePlayback.last.pauses==0&&JoyingSource.last.selections.size()==writes&&s.notifications.last.text.equals("BT ON · Audio ON"),"hiding active audio retains focus and source");
  check(!OverlayPresence.INSTANCE.visible(),"companion presence is OFF while service stays active");done(s);cases++;
  s=start(10,0,true);hide(s);check(s.stopped&&s.windowManager.removes==1,"both OFF exits when hidden");done(s);cases++;
  s=start(12,0,true);hide(s);BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"hidden service exits after external BT shutdown when Audio is OFF");done(s);cases++;
  s=start(12,0,true);call(s,"toggleAudio");hide(s);
  check(!s.stopped&&AudioFocusControl.last.held,"hide during source acquisition retains the request");JoyingSource.last.update(3);
  check(!s.stopped&&s.notifications.last.text.equals("BT ON · Audio ON"),"hidden acquisition confirms normally");done(s);cases++;
  s=start(12,0,true);call(s,"toggleAudio");JoyingSource.last.update(3);hide(s);JoyingSource wire=JoyingSource.last;action(s,"Turn both off & exit");
  check(NativePlayback.last.pauses==1&&!AudioFocusControl.last.held&&wire.selections.get(1)==0&&BluetoothAdapter.instance.disables==0&&!s.stopped,"exit pauses/releases and waits for source restoration before radio off");
  wire.update(0);check(BluetoothAdapter.instance.disables==1&&!s.stopped&&s.notifications.last.text.contains("waiting"),"exit waits for confirmed radio shutdown");
  BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"confirmed both OFF completes exit");done(s);cases++;
  s=start(12,0,false);action(s,"Turn both off & exit");check(s.windowManager.adds==0&&BluetoothAdapter.instance.disables==1,"cold exit action creates no overlay");BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"cold exit confirms shutdown");done(s);cases++;
  s=start(12,0,true);hide(s);BluetoothAdapter.instance.accept=false;action(s,"Turn both off & exit");
  check(!s.stopped&&s.foreground&&s.notifications.last.text.contains("Shutdown not confirmed")&&s.notifications.last.text.contains("BT ON"),"rejected radio shutdown keeps truthful notification and retry action");
  BluetoothAdapter.instance.accept=true;action(s,"Turn both off & exit");BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"retry can complete failed shutdown");done(s);cases++;
  s=start(12,0,true);hide(s);action(s,"Turn both off & exit");BluetoothAdapter.instance.state=12;SystemClock.now=13000;handler(s).tasks.get(0).run();
  check(!s.stopped&&s.notifications.last.text.contains("Shutdown not confirmed"),"timed-out shutdown retains notification");done(s);cases++;
  s=start(12,0,true);BluetoothAdapter.instance.state=10;JoyingSource.last.listener.disconnected();hide(s);
  check(!s.stopped&&s.notifications.last.text.contains("Audio ?"),"unknown source is not reported as confirmed OFF");JoyingSource.last.update(0);check(s.stopped,"later confirmed OFF permits hidden exit");done(s);cases++;
  s=start(12,0,true);s.windowManager.failRemove=true;hide(s);check(OverlayPresence.INSTANCE.visible()&&s.windowManager.current!=null&&!s.stopped,"failed removal does not falsely report hidden");s.windowManager.failRemove=false;done(s);cases++;
  s=start(12,0,true);call(s,"toggleAudio");JoyingSource.last.update(3);hide(s);AudioFocusControl.last.lose();
  check(NativePlayback.last.pauses==1&&!AudioFocusControl.last.held&&JoyingSource.last.selections.get(1)==0,"hidden service still handles focus loss");JoyingSource.last.update(0);check(!s.stopped,"BT ON keeps notification after focus loss");done(s);cases++;
  s=start(12,0,false);Settings.allowed=false;action(s,"Turn both off & exit");BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped&&s.windowManager.adds==0,"shutdown does not require overlay permission");done(s);cases++;
  s=start(10,0,true);call(s,"toggleBluetooth");hide(s);check(!s.stopped,"pending enable prevents premature hidden exit");BluetoothAdapter.instance.state=12;s.broadcast();check(!s.stopped&&s.notifications.last.text.contains("BT ON"),"hidden enable completion retains notification");done(s);cases++;
  s=start(10,0,true);call(s,"toggleBluetooth");hide(s);action(s,"Turn both off & exit");check(BluetoothAdapter.instance.disables==0,"exit waits for existing adapter transition");BluetoothAdapter.instance.state=12;s.broadcast();check(BluetoothAdapter.instance.disables==1,"exit disables adapter after in-flight enable");BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"exit after in-flight enable completes");done(s);cases++;
  s=start(12,3,true);hide(s);check(!s.stopped,"externally selected Audio ON remains active");action(s,"Turn both off & exit");check(JoyingSource.last.selections.get(0)==0,"explicit exit releases an externally selected route");JoyingSource.last.update(0);BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"external route shuts down through confirmed fallback");done(s);cases++;
  s=start(12,3,false);JoyingSource.last.accept=false;action(s,"Turn both off & exit");BluetoothAdapter.instance.state=10;s.broadcast();check(!s.stopped&&s.notifications.last.text.contains("Shutdown not confirmed"),"failed source restoration cannot claim Audio OFF");JoyingSource.last.update(0);check(s.stopped,"late confirmation finishes hidden cold exit");done(s);cases++;
  s=start(12,0,true);hide(s);int updates=s.notifications.updates;call(s,"render");call(s,"render");check(s.notifications.updates==updates,"unchanged state does not repost notifications");done(s);cases++;
  s=start(12,0,true);check(OverlayPresence.INSTANCE.requestClose()&&!OverlayPresence.INSTANCE.visible()&&!s.stopped,"companion CLOSE hides the bar while BT stays active");s.onStartCommand(new Intent(),0,4);check(OverlayPresence.INSTANCE.visible(),"companion reopen restores hidden window");done(s);cases++;
  s=start(14,0,true);hide(s);check(!s.stopped&&s.notifications.last.text.contains("BT waiting"),"BLE startup hidden behind public OFF is not treated as settled shutdown");BluetoothAdapter.instance.state=10;s.broadcast();check(s.stopped,"stable OFF ends hidden BLE startup wait");done(s);cases++;
  s=start(12,0,true);android.widget.LinearLayout root=(android.widget.LinearLayout)s.windowManager.current;android.widget.LinearLayout title=(android.widget.LinearLayout)root.children.get(0);check(((android.widget.TextView)title.children.get(1)).text.equals("v1.8"),"installed version is a separate right-hand title label");done(s);cases++;
  s=start(12,0,true);call(s,"toggleSettings");check(s.lastActivity.type==BtSettingsActivity.class&&(s.lastActivity.flags&Intent.FLAG_ACTIVITY_CLEAR_TASK)!=0,"settings opens only the app-owned separate task");Intent firstLaunch=s.lastActivity;call(s,"toggleSettings");check(s.lastActivity==firstLaunch,"pending settings launch is not duplicated");Object owner=new Object();final int[] closes={0};SettingsWindow.INSTANCE.attached(owner,()->closes[0]++);java.lang.reflect.Field button=s.getClass().getDeclaredField("settingsButton");button.setAccessible(true);android.widget.Button settings=(android.widget.Button)button.get(s);check(settings.color==0xFF87E5B0,"settings themes ON when the owned window attaches");call(s,"toggleSettings");check(closes[0]==1&&settings.color==0xFF87E5B0&&!settings.enabled,"close waits for confirmed owner removal");SettingsWindow.INSTANCE.detached(owner);check(settings.color==-1&&settings.enabled,"settings themes OFF after close or Back");done(s);cases++;
  ReceiverDevice.sender="Laptop";s=start(12,0,true);root=(android.widget.LinearLayout)s.windowManager.current;title=(android.widget.LinearLayout)root.children.get(0);check(((android.widget.TextView)title.children.get(0)).text.contains("Bluetooth ON · Laptop ·"),"receiver name follows Bluetooth title");ReceiverDevice.sender="";done(s);cases++;
  System.out.println(cases+" foreground-service scenarios passed (host stand-ins).");
 }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix='joying-service-') as temp:
        root = Path(temp)
        files = [JAVA / name for name in ('FloatingBarService.java', 'SourceController.java',
                 'AdapterController.java', 'AudioSession.java', 'OverlayPresence.java', 'SettingsWindow.java')]
        stubs = dict(STUBS)
        stubs['com/joying/bluetoothsettings/ServiceChecks.java'] = CHECKS
        for name, text in stubs.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            files.append(path)
        classes = root / 'classes'
        classes.mkdir()
        compile_java(files, classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.ServiceChecks'], check=True)


if __name__ == '__main__':
    main()
