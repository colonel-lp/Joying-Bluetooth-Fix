"""Check production focus calls and listener lifetime using Android stand-ins."""
import subprocess
import tempfile
from pathlib import Path
from run_checks import JAVA, compile_java

STUBS = {
'android/content/Context.java': '''package android.content; public class Context { public static final String AUDIO_SERVICE="audio"; public Object getSystemService(String s){return android.media.AudioManager.instance;} }''',
'android/os/Handler.java': '''package android.os; public class Handler {}''',
'android/os/Build.java': '''package android.os; public class Build { public static class VERSION { public static int SDK_INT=27;} }''',
'android/util/Log.java': '''package android.util; public class Log { public static int w(String a,String b,Throwable c){return 0;} }''',
'android/media/AudioAttributes.java': '''package android.media; public class AudioAttributes {
 public static final int USAGE_MEDIA=1,CONTENT_TYPE_MUSIC=2; public int usage,content;
 public static class Builder { private AudioAttributes a=new AudioAttributes(); public Builder setUsage(int u){a.usage=u;return this;} public Builder setContentType(int c){a.content=c;return this;} public AudioAttributes build(){return a;} }
}''',
'android/media/AudioFocusRequest.java': '''package android.media; import android.os.Handler;
public class AudioFocusRequest { public int gain; public boolean pauseWhenDucked; public AudioAttributes attributes; public AudioManager.OnAudioFocusChangeListener listener;
 public static class Builder { private AudioFocusRequest r=new AudioFocusRequest(); public Builder(int g){r.gain=g;} public Builder setAudioAttributes(AudioAttributes a){r.attributes=a;return this;} public Builder setWillPauseWhenDucked(boolean pause){r.pauseWhenDucked=pause;return this;} public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener l,Handler h){r.listener=l;return this;} public AudioFocusRequest build(){return r;} }
}''',
'android/media/AudioManager.java': '''package android.media; public class AudioManager {
 public static final int AUDIOFOCUS_GAIN=1,STREAM_MUSIC=3,AUDIOFOCUS_REQUEST_GRANTED=1; public static AudioManager instance;
 public interface OnAudioFocusChangeListener { void onAudioFocusChange(int change); }
 public int result=1,requests,abandoned,legacyGain; public AudioFocusRequest requested,released; public OnAudioFocusChangeListener legacy;
 public int requestAudioFocus(AudioFocusRequest r){requests++;requested=r;return result;}
 public int requestAudioFocus(OnAudioFocusChangeListener l,int stream,int gain){requests++;legacy=l;legacyGain=gain;return result;}
 public int abandonAudioFocusRequest(AudioFocusRequest r){abandoned++;released=r;return 1;}
 public int abandonAudioFocus(OnAudioFocusChangeListener l){abandoned++;return 1;}
}''',
'com/joying/bluetoothsettings/AudioFocusChecks.java': '''package com.joying.bluetoothsettings;
import android.media.*;import android.content.Context;import android.os.*;
public class AudioFocusChecks {
 static int cases;static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  AudioManager.instance=new AudioManager();final int[] losses={0};
  AudioFocusControl f=new AudioFocusControl(new Context(),new Handler(),()->losses[0]++);
  check(f.acquire(),"granted focus"); AudioFocusRequest first=AudioManager.instance.requested;
  check(first.gain==AudioManager.AUDIOFOCUS_GAIN&&first.attributes.usage==AudioAttributes.USAGE_MEDIA,"permanent media takeover prevents previous-player automatic regain");cases++;
  check(first.pauseWhenDucked,"request a callback because native audio belongs to another process");cases++;
  f.acquire();check(AudioManager.instance.requests==1,"no duplicate focus acquisition");cases++;
  first.listener.onAudioFocusChange(1);check(losses[0]==0,"focus gain never starts playback");cases++;
  first.listener.onAudioFocusChange(-1);check(losses[0]==1,"focus loss delegates pause/release");cases++;
  f.release();check(AudioManager.instance.released==first,"release exact request identity");first.listener.onAudioFocusChange(-1);check(losses[0]==1,"ignore stale callback after release");cases++;
  f.acquire();AudioFocusRequest second=AudioManager.instance.requested;first.listener.onAudioFocusChange(-2);
  check(losses[0]==1&&second!=first,"old callback cannot release a new session");cases++;
  second.listener.onAudioFocusChange(-2);check(losses[0]==2,"temporary interruption is handled without auto resume"); f.release();cases++;
  f.acquire();AudioFocusRequest third=AudioManager.instance.requested;third.listener.onAudioFocusChange(-3);check(losses[0]==3,"ducking interruption delegates pause rather than relying on local volume");f.release();cases++;
  AudioManager.instance=new AudioManager();AudioManager.instance.result=0;f=new AudioFocusControl(new Context(),new Handler(),()->{});
  check(!f.acquire()&&AudioManager.instance.abandoned==1,"failed request is cleaned up");cases++;
  Build.VERSION.SDK_INT=25;AudioManager.instance=new AudioManager();f=new AudioFocusControl(new Context(),new Handler(),()->losses[0]++);
  check(f.acquire()&&AudioManager.instance.legacyGain==AudioManager.AUDIOFOCUS_GAIN,"legacy permanent-focus fallback");f.release();check(AudioManager.instance.abandoned==1,"legacy release");cases++;
  System.out.println(cases+" focus API scenarios passed (host stand-ins).");
 }
}''',
}

def main():
    with tempfile.TemporaryDirectory(prefix='joying-focus-') as temporary:
        root = Path(temporary)
        files = [JAVA / 'AudioFocusControl.java']
        for name, text in STUBS.items():
            p = root / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(text)
            files.append(p)
        classes = root / 'classes'
        classes.mkdir()
        compile_java(files, classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.AudioFocusChecks'], check=True)

if __name__ == '__main__':
    main()
