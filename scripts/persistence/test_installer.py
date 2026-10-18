import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).parent / 'run.sh'


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.usb = self.root / 'usb'
        self.oem = self.root / 'oem'
        self.bin = self.root / 'bin'
        for p in (self.usb, self.oem / 'app', self.bin):
            p.mkdir(parents=True)
        self.config = self.oem / 'app/config.txt'
        self.original = b'# original\r\nsys.fyt.bluetooth_type=1\r\nother=retain exactly\r\n'
        self.config.write_bytes(self.original)
        self.config.chmod(0o640)
        self.write_command('getprop', 'case "$1" in ro.product.device) echo sp9853i_1h10;; ro.build.version.sdk) echo 27;; esac')
        self.write_command('pidof', 'exit 1')
        self.write_command('restorecon', 'exit 0')
        script = SOURCE.read_text()
        script = script.replace('JBT_USB=/storage/sdcard1', f'JBT_USB={self.usb}')
        script = script.replace('JBT_TARGET=/oem/app/config.txt', f'JBT_TARGET={self.config}')
        script = script.replace('JBT_OEM=/oem', f'JBT_OEM={self.oem}')
        script = script.replace('JBT_STAGE=/oem/app/.config.bluetooth-receiver-r1', f'JBT_STAGE={self.oem}/app/.stage')
        script = script.replace('JBT_RESTORECON=/system/bin/restorecon', f'JBT_RESTORECON={self.bin}/restorecon')
        self.script = self.root / 'run.sh'
        self.script.write_text(script)
        self.env = dict(os.environ, PATH=f'{self.bin}:' + os.environ['PATH'])

    def write_command(self, name, body):
        p = self.bin / name
        p.write_text('#!/bin/sh\n' + body + '\n')
        p.chmod(0o755)

    def run_mode(self, mode):
        r = subprocess.run(['/bin/sh', str(self.script), mode], env=self.env, capture_output=True)
        return r.returncode, (self.usb / f'joying-bluetooth-{mode}.log').read_text()

    def test_install_idempotence_and_exact_restore(self):
        code, log = self.run_mode('install')
        self.assertEqual(code, 0, log)
        changed = self.config.read_bytes()
        self.assertTrue(changed.startswith(self.original))
        self.assertIn(b'sys.fyt.systemobd=1\n', changed)
        self.assertEqual(self.config.stat().st_mode & 0o777, 0o640)
        self.assertEqual(self.run_mode('install')[0], 0)
        self.assertEqual(self.config.read_bytes(), changed)
        code, log = self.run_mode('restore')
        self.assertEqual(code, 0, log)
        self.assertEqual(self.config.read_bytes(), self.original)
        self.assertEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.run_mode('install')[0], 0)

    def test_existing_duplicate_native_values(self):
        self.original += b'sys.fyt.systemobd=0\r\nsys.fyt.systemobd=false\n'
        self.config.write_bytes(self.original)
        code, log = self.run_mode('install')
        self.assertEqual(code, 0, log)
        self.assertEqual(self.config.read_bytes().count(b'sys.fyt.systemobd=1'), 2)
        self.assertEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)

    def test_read_only_check_and_wrong_device(self):
        self.assertEqual(self.run_mode('check')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)
        self.assertFalse((self.usb / 'joying-bluetooth-backup-r1').exists())
        self.write_command('getprop', 'echo wrong')
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)

    def test_refuses_later_owner_edits(self):
        self.assertEqual(self.run_mode('install')[0], 0)
        changed = self.config.read_bytes() + b'owner=later edit\n'
        self.config.write_bytes(changed)
        self.assertNotEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), changed)

    def test_refuses_corrupt_backup(self):
        self.assertEqual(self.run_mode('install')[0], 0)
        changed = self.config.read_bytes()
        backup = next((self.usb / 'joying-bluetooth-backup-r1').glob('*.config.txt'))
        backup.write_bytes(b'corrupt')
        self.assertNotEqual(self.run_mode('restore')[0], 0)
        self.assertEqual(self.config.read_bytes(), changed)

    def test_rolls_back_if_labelling_fails(self):
        marker = self.root / 'restorecon-failed'
        self.write_command('restorecon', f'if [ ! -e {marker} ]; then touch {marker}; exit 1; fi\nexit 0')
        code, log = self.run_mode('install')
        self.assertNotEqual(code, 0)
        self.assertIn('Pre-operation configuration restored.', log)
        self.assertEqual(self.config.read_bytes(), self.original)

    def test_rejects_symlink_and_running_android(self):
        destination = self.root / 'unrelated'
        destination.write_bytes(b'preserve')
        self.config.unlink()
        self.config.symlink_to(destination)
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertEqual(destination.read_bytes(), b'preserve')
        self.config.unlink()
        self.config.write_bytes(self.original)
        self.write_command('pidof', 'echo 123; exit 0')
        self.assertNotEqual(self.run_mode('install')[0], 0)
        self.assertEqual(self.config.read_bytes(), self.original)


if __name__ == '__main__':
    unittest.main(verbosity=2)
