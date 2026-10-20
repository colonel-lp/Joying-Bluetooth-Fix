import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).parent
SOURCE = ROOT / 'install/joying-bluetooth/run.sh'


class GenericInstallerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.usb = self.root / 'usb'
        self.oem = self.root / 'oem'
        self.bin = self.root / 'bin'
        for path in (self.usb / 'joying-bluetooth', self.oem / 'app', self.bin):
            path.mkdir(parents=True)
        self.config = self.oem / 'app/config.txt'
        self.original = b'# original\r\nsys.fyt.bluetooth_type=1\r\nkeep=yes'
        self.config.write_bytes(self.original)
        self.config.chmod(0o640)
        self.identity('different_device', '35', 'serial-one')
        self.command('pidof', 'exit 1')
        self.command('restorecon', 'exit 0')
        script = SOURCE.read_text()
        script = script.replace('JBT_TARGET=/oem/app/config.txt', f'JBT_TARGET={self.config}')
        script = script.replace('JBT_OEM=/oem', f'JBT_OEM={self.oem}')
        script = script.replace('JBT_STAGE=/oem/app/.config.bluetooth-generic-r1', f'JBT_STAGE={self.oem}/app/.stage')
        script = script.replace('JBT_RESTORECON=/system/bin/restorecon', f'JBT_RESTORECON={self.bin}/restorecon')
        self.script = self.usb / 'joying-bluetooth/run.sh'
        self.script.write_text(script)
        self.env = dict(os.environ, PATH=f'{self.bin}:' + os.environ['PATH'])

    def command(self, name, body):
        path = self.bin / name
        path.write_text('#!/bin/sh\n' + body + '\n')
        path.chmod(0o755)

    def identity(self, device, sdk, serial):
        self.command('getprop', f'case "$1" in ro.product.device) echo {device};; ro.build.version.sdk) echo {sdk};; ro.serialno) echo {serial};; esac')

    def run_mode(self, mode):
        result = subprocess.run(['/bin/sh', str(self.script), mode], env=self.env, capture_output=True)
        return result.returncode, (self.usb / f'joying-bluetooth-{mode}.log').read_text()

    def backup(self):
        return next((self.usb / 'joying-bluetooth-backup-generic-r1').glob('*/*.config.txt'))

    def test_different_models_and_android_versions_install_and_restore(self):
        for model, sdk in [('different_device', '35'), ('another_board', '23')]:
            with self.subTest(model=model):
                self.identity(model, sdk, 'serial-one')
                self.assertEqual(self.run_mode('install')[0], 0)
                installed = self.config.read_bytes()
                self.assertIn(b'sys.fyt.systemobd=1\n', installed)
                self.assertEqual(self.config.stat().st_mode & 0o777, 0o640)
                self.assertEqual(self.run_mode('install')[0], 0)
                self.assertEqual(self.config.read_bytes(), installed)
                self.assertEqual(self.run_mode('restore')[0], 0)
                self.assertEqual(self.config.read_bytes(), self.original)
                self.assertEqual(self.run_mode('restore')[0], 0)

    def test_check_reads_only_and_reports_missing_config(self):
        self.assertEqual(self.run_mode('check')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)
        self.assertFalse((self.usb / 'joying-bluetooth-backup-generic-r1').exists())
        self.config.unlink()
        code, log = self.run_mode('check')
        self.assertEqual(code, 0, log)
        self.assertIn('Boot config: unavailable', log)
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertFalse(self.config.exists())

    def test_cross_device_backup_separation(self):
        self.assertEqual(self.run_mode('install')[0], 0)
        self.identity('different_device', '35', 'serial-two')
        self.assertNotEqual(self.run_mode('restore')[0], 0)
        self.identity('different_device', '35', 'serial-one')
        self.assertEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)

    def test_refuses_later_edits_and_corrupt_backups(self):
        self.assertEqual(self.run_mode('install')[0], 0)
        installed = self.config.read_bytes()
        self.config.write_bytes(installed + b'owner=changed\n')
        self.assertNotEqual(self.run_mode('restore')[0], 0)
        self.assertTrue(self.config.read_bytes().endswith(b'owner=changed\n'))
        self.config.write_bytes(installed)
        self.backup().write_bytes(b'corrupt')
        self.assertNotEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), installed)

    def test_refuses_live_android_and_unsafe_target(self):
        self.command('pidof', 'echo 123; exit 0')
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)
        self.command('pidof', 'exit 1')
        other = self.root / 'other'
        other.write_bytes(b'untouched')
        self.config.unlink()
        self.config.symlink_to(other)
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertEqual(other.read_bytes(), b'untouched')

    def test_restores_original_after_label_failure(self):
        marker = self.root / 'failed'
        self.command('restorecon', f'if [ ! -e {marker} ]; then touch {marker}; exit 1; fi\nexit 0')
        code, log = self.run_mode('install')
        self.assertNotEqual(code, 0)
        self.assertIn('Pre-operation configuration restored.', log)
        self.assertEqual(self.config.read_bytes(), self.original)

    def test_duplicate_keys_crlf_metadata_and_no_final_newline(self):
        self.original = b'# keep\r\n  sys.fyt.systemobd \t=0\r\nsys.fyt.systemobd=false\nlast=value'
        self.config.write_bytes(self.original)
        self.config.chmod(0o2640)
        for name in ['stat', 'sed', 'grep', 'tr']:
            self.command(name, 'exit 99')
        self.assertEqual(self.run_mode('install')[0], 0)
        self.assertEqual(self.config.read_bytes(), b'# keep\r\nsys.fyt.systemobd=1\r\nsys.fyt.systemobd=1\nlast=value')
        self.assertEqual(self.config.stat().st_mode & 0o7777, 0o2640)
        self.assertEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)
        self.assertEqual(self.config.stat().st_mode & 0o7777, 0o2640)

    def test_layouts_and_single_line_updater(self):
        for folder, mode in [('Check', 'check'), ('install', 'install'), ('restore', 'restore')]:
            layout = ROOT / folder
            launcher = (layout / 'lsec_updatesh/lsec.sh').read_text()
            self.assertEqual(launcher, '#!/system/bin/sh\n/system/bin/sh /storage/sdcard1/joying-bluetooth/run.sh ' + mode + '\n')
            self.assertEqual((layout / 'joying-bluetooth/run.sh').read_bytes(), SOURCE.read_bytes())
            line = launcher.splitlines()[1].replace('/system/bin/sh', '/bin/sh').replace('/storage/sdcard1/joying-bluetooth/run.sh', str(self.script))
            result = subprocess.run(['/bin/sh', '-c', line], env=self.env, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main(verbosity=2)
