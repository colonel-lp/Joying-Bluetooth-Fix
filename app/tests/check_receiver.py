"""Check sender-name lookup through the native receiving profile."""
from pathlib import Path
import subprocess
import tempfile
from run_checks import JAVA, compile_java

STUBS = {
'android/content/Context.java': 'package android.content;public class Context {public BroadcastReceiver receiver;public void registerReceiver(BroadcastReceiver r,IntentFilter f){receiver=r;}public void unregisterReceiver(BroadcastReceiver r){receiver=null;}public void changed(){if(receiver!=null)receiver.onReceive(this,new Intent());}}',
'android/content/Intent.java': 'package android.content;public class Intent {}',
'android/content/IntentFilter.java': 'package android.content;public class IntentFilter {public IntentFilter(String s){}public void addAction(String s){}}',
'android/content/BroadcastReceiver.java': 'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/util/Log.java': 'package android.util;public class Log {public static int w(String a,String b,Throwable t){return 0;}}',
'android/bluetooth/BluetoothDevice.java': 'package android.bluetooth;public class BluetoothDevice {public static final String ACTION_NAME_CHANGED="name";public String name;public BluetoothDevice(String n){name=n;}public String getName(){return name;}}',
'android/bluetooth/BluetoothProfile.java': 'package android.bluetooth;public interface BluetoothProfile {java.util.List<BluetoothDevice> getConnectedDevices();interface ServiceListener {void onServiceConnected(int p,BluetoothProfile s);void onServiceDisconnected(int p);}}',
'android/bluetooth/BluetoothAdapter.java': '''package android.bluetooth;public class BluetoothAdapter {public static final int STATE_ON=12;public static final String ACTION_STATE_CHANGED="state";public int state=10,requests,closes,profile;public boolean accept=true;public BluetoothProfile.ServiceListener listener;public int getState(){return state;}public boolean getProfileProxy(android.content.Context c,BluetoothProfile.ServiceListener l,int p){requests++;profile=p;listener=l;return accept;}public void closeProfileProxy(int p,BluetoothProfile s){closes++;}}''',
}

CHECKS = '''package com.joying.bluetoothsettings;
import android.bluetooth.*;import android.content.Context;import java.util.*;
public class ReceiverChecks {
 static int cases;static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static class Proxy implements BluetoothProfile {List<BluetoothDevice> devices=new ArrayList<>();boolean denied;public List<BluetoothDevice> getConnectedDevices(){if(denied)throw new SecurityException();return devices;}}
 public static void main(String[] args){
  Context context=new Context();BluetoothAdapter adapter=new BluetoothAdapter();int[] changes={0};ReceiverDevice device=new ReceiverDevice(context,adapter,()->changes[0]++);device.start();check(adapter.requests==0,"adapter OFF defers profile allocation");cases++;
  adapter.state=12;context.changed();check(adapter.requests==1&&adapter.profile==11,"only A2DP receiver profile is queried, not paired HID devices");cases++;
  Proxy proxy=new Proxy();proxy.devices.add(new BluetoothDevice("Laptop"));adapter.listener.onServiceConnected(11,proxy);check(device.name().equals("Laptop")&&changes[0]==1,"connected receiver exposes sender name");cases++;
  context.changed();check(changes[0]==1&&adapter.requests==1,"unchanged broadcasts do not duplicate profile or redraw");proxy.devices.get(0).name="New\\nname";context.changed();check(device.name().equals("New name"),"rename refreshes and sanitizes title");cases++;
  proxy.devices.add(new BluetoothDevice("Other"));context.changed();check(device.name().isEmpty(),"ambiguous senders are not guessed");proxy.devices.remove(1);proxy.devices.get(0).name=null;context.changed();check(device.name().isEmpty(),"missing name stays absent rather than exposing an address");cases++;
  proxy.devices.get(0).name="Laptop";context.changed();proxy.denied=true;context.changed();check(device.name().isEmpty(),"permission or service failure clears stale name");proxy.denied=false;cases++;
  context.changed();adapter.listener.onServiceDisconnected(11);context.changed();check(device.name().isEmpty()&&adapter.requests==1,"service disconnect waits for existing proxy rebind rather than leaking proxies");adapter.listener.onServiceConnected(11,proxy);check(device.name().equals("Laptop"),"profile reconnection restores sender");cases++;
  adapter.state=10;context.changed();check(device.name().isEmpty(),"radio OFF clears sender");device.close();check(context.receiver==null&&adapter.closes==1,"close unregisters and releases retained proxy");adapter.listener.onServiceConnected(11,proxy);check(adapter.closes==2&&device.name().isEmpty(),"late connection after close is released");cases++;
  context=new Context();adapter=new BluetoothAdapter();adapter.state=12;adapter.accept=false;device=new ReceiverDevice(context,adapter,()->{});device.start();adapter.accept=true;context.changed();check(adapter.requests==2,"unsupported or rejected initial profile request can recover on state change");device.close();cases++;
  device=new ReceiverDevice(new Context(),null,()->{});device.start();device.close();check(device.name().isEmpty(),"missing adapter is harmless");cases++;
  System.out.println(cases+" receiver-name scenarios passed (host stand-ins).");
 }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix='joying-receiver-') as temp:
        root = Path(temp)
        files = [JAVA / 'ReceiverDevice.java']
        stubs = dict(STUBS)
        stubs['com/joying/bluetoothsettings/ReceiverChecks.java'] = CHECKS
        for name, text in stubs.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
            files.append(path)
        classes = root / 'classes'
        classes.mkdir()
        compile_java(files, classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.ReceiverChecks'], check=True)


if __name__ == '__main__':
    main()
