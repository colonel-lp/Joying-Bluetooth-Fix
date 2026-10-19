"""Source-state checks; optional full Java compilation against an Android SDK jar. No APK."""
from pathlib import Path
import argparse
import subprocess
import tempfile
import xml.etree.ElementTree as ET

APP = Path(__file__).resolve().parents[1]
JAVA = APP / 'src/main/java/com/joying/bluetoothsettings'

def compile_java(files, output, classpath=None):
    command = ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '8', '-d', str(output)]
    if classpath:
        command += ['-classpath', str(classpath)]
    subprocess.run(command + [str(p) for p in files], check=True)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--android-jar', type=Path)
    args = parser.parse_args()
    ns = '{http://schemas.android.com/apk/res/android}'
    manifest = ET.parse(APP / 'src/main/AndroidManifest.xml').getroot()
    permissions = {p.get(ns + 'name') for p in manifest.findall('uses-permission')}
    assert permissions == {'android.permission.BLUETOOTH', 'android.permission.BLUETOOTH_ADMIN',
                           'android.permission.SYSTEM_ALERT_WINDOW', 'android.permission.FOREGROUND_SERVICE'}
    activity = manifest.find('application/activity')
    assert activity.get(ns + 'noHistory') != 'true'
    assert manifest.find('application/service').get(ns + 'exported') == 'false'
    settings = manifest.find("application/activity[@{"+ns[1:-1]+"}name='.BtSettingsActivity']")
    assert settings is not None and settings.get(ns + 'exported') == 'false'
    assert settings.get(ns + 'taskAffinity') == 'com.joying.bluetoothsettings.btsettings'
    assert settings.get(ns + 'noHistory') != 'true'
    with tempfile.TemporaryDirectory(prefix='joying-bt-check-') as temp:
        root = Path(temp)
        classes = root / 'checks'
        classes.mkdir()
        compile_java([JAVA / 'SourceController.java', JAVA / 'AdapterController.java',
                      APP / 'tests/SourceControllerChecks.java', APP / 'tests/AdapterControllerChecks.java',
                      JAVA / 'OverlayPresence.java', APP / 'tests/OverlayPresenceChecks.java',
                      JAVA / 'AudioSession.java', APP / 'tests/AudioSessionChecks.java',
                      APP / 'tests/AudioRoutingChecks.java'], classes)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.SourceControllerChecks'], check=True)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.AdapterControllerChecks'], check=True)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.OverlayPresenceChecks'], check=True)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.AudioSessionChecks'], check=True)
        subprocess.run(['java', '-cp', str(classes), 'com.joying.bluetoothsettings.AudioRoutingChecks'], check=True)
        assert manifest.find("application/service[@{"+ns[1:-1]+"}name='.ControlLinkService']") is not None
        if args.android_jar:
            generated = root / 'R.java'
            generated.write_text('package com.joying.bluetoothsettings; public final class R {'
                                 'public static final class drawable { public static final int bluetooth_icon=1; }}')
            all_classes = root / 'android-classes'
            all_classes.mkdir()
            compile_java([*JAVA.glob('*.java'), generated], all_classes, args.android_jar)
            print('All production Java compiled against supplied Android SDK; generated R stand-in only. No APK assembled.')
        else:
            print('Android SDK compilation skipped; supply --android-jar /path/to/platforms/android-35/android.jar.')
    print('Manifest permissions and retained permission-result lifecycle checks passed.')

if __name__ == '__main__':
    main()
