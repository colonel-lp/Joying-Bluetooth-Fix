"""Check the owned settings activity and its state without a window manager."""
from pathlib import Path
import subprocess
import tempfile
from run_checks import JAVA, compile_java

STUBS = {
'android/content/Intent.java': 'package android.content;public class Intent {public final String action;public Intent(String s){action=s;}}',
'android/os/Bundle.java': 'package android.os;public class Bundle {boolean value;public boolean getBoolean(String s,boolean d){return value;}public void putBoolean(String s,boolean b){value=b;}}',
'android/provider/Settings.java': 'package android.provider;public class Settings {public static final String ACTION_BLUETOOTH_SETTINGS="bt-settings";}',
'android/widget/Toast.java': 'package android.widget;public class Toast {public static final int LENGTH_LONG=1;public static Toast makeText(Object a,String s,int n){return new Toast();}public void show(){}}',
'android/app/Activity.java': '''package android.app;import android.os.Bundle;import android.content.Intent;
public class Activity {public boolean root=true,fail,finished;public int starts,removed,finishedChild,lastRequest;public Intent child;
 protected void onCreate(Bundle b){}protected void onSaveInstanceState(Bundle b){}protected void onActivityResult(int r,int c,Intent i){}protected void onDestroy(){}
 public void startActivityForResult(Intent i,int r){if(fail)throw new IllegalStateException();starts++;child=i;lastRequest=r;}public boolean isTaskRoot(){return root;}
 public void finishAndRemoveTask(){removed++;}public void finishActivity(int r){finishedChild=r;}public void finish(){finished=true;}}
''',
}

CHECKS = '''package com.joying.bluetoothsettings;
import android.os.Bundle;
public class SettingsChecks {
 static int cases;static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  SettingsWindow state=new SettingsWindow();long token=state.beginOpen();check(token!=0&&state.busy()&&!state.open()&&state.beginOpen()==0,"pending launch prevents duplicate windows");cases++;
  Object first=new Object();final int[] closes={0};state.attached(first,()->closes[0]++);check(state.open()&&!state.busy()&&!state.expire(token),"attachment confirms open and invalidates timeout");cases++;
  check(state.requestClose()&&closes[0]==1&&state.open()&&state.busy()&&!state.requestClose(),"close does not claim removal early or duplicate");state.detached(first);check(!state.open()&&!state.busy(),"removal resets close state");cases++;
  Object second=new Object();state.attached(first,()->{});state.attached(second,()->{});state.detached(first);check(state.open(),"old activity destruction cannot clear replacement");state.detached(second);cases++;
  token=state.beginOpen();check(state.expire(token)&&!state.busy(),"failed delivery allows retry");long newer=state.beginOpen();check(!state.expire(token)&&state.busy(),"old timeout cannot cancel new launch");state.launchFailed();check(!state.busy(),"launch exception permits retry");cases++;
  state.attached(first,()->{throw new IllegalStateException();});try{state.requestClose();throw new AssertionError("expected failure");}catch(IllegalStateException expected){}check(state.open()&&!state.busy(),"failed close retains open indication and permits retry");state.detached(first);cases++;
  BtSettingsActivity a=new BtSettingsActivity();a.onCreate(null);check(a.starts==1&&a.child.action.equals("bt-settings")&&SettingsWindow.INSTANCE.open(),"host launches native settings for a result");SettingsWindow.INSTANCE.requestClose();check(a.removed==1&&SettingsWindow.INSTANCE.open(),"button close removes the root task and waits for destruction");a.onDestroy();check(!SettingsWindow.INSTANCE.open(),"destroy confirms settings OFF");cases++;
  a=new BtSettingsActivity();a.onCreate(null);a.onActivityResult(99,0,null);check(SettingsWindow.INSTANCE.open(),"unrelated result ignored");a.onActivityResult(a.lastRequest,0,null);check(a.removed==1&&!SettingsWindow.INSTANCE.open(),"Back clears state and removes empty host task");a.onDestroy();cases++;
  a=new BtSettingsActivity();a.onCreate(null);Bundle saved=new Bundle();a.onSaveInstanceState(saved);BtSettingsActivity replacement=new BtSettingsActivity();replacement.onCreate(saved);a.onDestroy();check(replacement.starts==0&&SettingsWindow.INSTANCE.open(),"recreation retains child without duplicate launch or stale detach");replacement.onActivityResult(20,0,null);replacement.onDestroy();cases++;
  SettingsWindow.INSTANCE.beginOpen();a=new BtSettingsActivity();a.fail=true;a.onCreate(null);check(a.finished&&!SettingsWindow.INSTANCE.open()&&!SettingsWindow.INSTANCE.busy(),"missing native screen leaves button OFF and retryable");a.onDestroy();cases++;
  a=new BtSettingsActivity();a.root=false;a.onCreate(null);SettingsWindow.INSTANCE.requestClose();check(a.removed==0&&a.finishedChild==20&&a.finished,"non-root fallback closes only its own child, never another app task");a.onDestroy();cases++;
  System.out.println(cases+" settings-window scenarios passed (host stand-ins).");
 }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix='joying-settings-') as temp:
        root = Path(temp)
        files = [JAVA / 'SettingsWindow.java', JAVA / 'BtSettingsActivity.java']
        stubs = dict(STUBS)
        stubs['com/joying/bluetoothsettings/SettingsChecks.java'] = CHECKS
        for name, text in stubs.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            files.append(path)
        classes = root / 'classes'
        classes.mkdir()
        compile_java(files, classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.SettingsChecks'], check=True)


if __name__ == '__main__':
    main()
