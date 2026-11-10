import io
import stat
import unittest
from types import SimpleNamespace
from deploy import ALLOWED, digest, transaction

class Remote:
    def __init__(self):
        self.files = {'public_html/'+n:b'old' for n in ALLOWED}
        self.modes = {p:stat.S_IFREG|0o644 for p in self.files}
        self.modes['rentmor-control-private']=stat.S_IFDIR|0o700
        self.writes = 0
        self.fail_on = None
    def lstat(self, path):
        return SimpleNamespace(st_mode=self.modes[path], st_size=len(self.files.get(path,b'')))
    def open(self, path, mode):
        if mode=='rb': return io.BytesIO(self.files[path])
        remote=self
        class Writer(io.BytesIO):
            def close(self):
                if not self.closed:
                    remote.writes+=1
                    remote.files[path]=self.getvalue()
                    remote.modes.setdefault(path,stat.S_IFREG|0o600)
                    if remote.writes==remote.fail_on:
                        remote.files[path]=b'partial'
                        super().close()
                        raise OSError('Synthetic partial write')
                super().close()
        return Writer()
    def chmod(self,path,mode): self.modes[path]=stat.S_IFMT(self.modes[path])|mode
    def mkdir(self,path,mode): self.modes[path]=stat.S_IFDIR|mode

class Tests(unittest.TestCase):
    def setUp(self):
        self.remote=Remote()
        self.entries=[{'name':n,'sha256':digest(b'old'),'replacements':[['old','new']]} for n in sorted(ALLOWED)]
    def run_tx(self,**kwargs):
        return transaction(self.remote,self.entries,validator=lambda n,b:None,**kwargs)
    def assert_old(self):
        self.assertTrue(all(self.remote.files['public_html/'+n]==b'old' for n in ALLOWED))
    def test_check_never_writes(self):
        self.run_tx(); self.assertEqual(self.remote.writes,0)
    def test_stale_last_file_never_writes(self):
        self.entries[-1]['sha256']='0'*64
        with self.assertRaises(ValueError): self.run_tx(apply=True)
        self.assertEqual(self.remote.writes,0)
    def test_symlink_refused(self):
        self.remote.modes['public_html/mortii.js']=stat.S_IFLNK|0o777
        with self.assertRaises(ValueError): self.run_tx(apply=True)
        self.assertEqual(self.remote.writes,0)
    def test_duplicate_or_path_traversal_refused(self):
        self.entries[-1]['name']='../mortii.js'
        with self.assertRaises(ValueError): self.run_tx(apply=True)
        self.assertEqual(self.remote.writes,0)
    def test_partial_second_upload_rolls_back(self):
        self.remote.fail_on=6  # four backups then second production write
        with self.assertRaisesRegex(RuntimeError,'previous files restored'): self.run_tx(apply=True)
        self.assert_old()
    def test_public_failure_rolls_back(self):
        def fail(candidate): raise RuntimeError('Synthetic HTTP failure')
        with self.assertRaisesRegex(RuntimeError,'previous files restored'): self.run_tx(apply=True,health=fail)
        self.assert_old()
    def test_private_backup_required(self):
        self.remote.modes['rentmor-control-private']=stat.S_IFDIR|0o755
        with self.assertRaises(ValueError): self.run_tx(apply=True)
        self.assertEqual(self.remote.writes,0)
    def test_success_keeps_private_backups(self):
        self.run_tx(apply=True)
        for name in ALLOWED:
            self.assertEqual(self.remote.files['public_html/'+name],b'new')
            backup=next(p for p in self.remote.files if p.startswith('rentmor-control-private/') and p.endswith('/'+name))
            self.assertEqual(self.remote.files[backup],b'old')
            self.assertEqual(self.remote.modes[backup]&0o777,0o600)

if __name__=='__main__': unittest.main()
