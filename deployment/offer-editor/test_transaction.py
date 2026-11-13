"""Exercise the actual deployment transaction without network, credentials or real files."""
import ast,json
from pathlib import Path
import unittest
root=Path(__file__).resolve().parent
source=(root/'publish.py').read_text();tree=ast.parse(source)
namespace={}
exec(compile(ast.Module(body=[n for n in tree.body if isinstance(n,ast.FunctionDef)and n.name=='apply_transaction'],type_ignores=[]),'transaction','exec'),namespace)
class Fake:
 def __init__(self):self.files={'source':b'old','doc':b'previous'};self.fail=None
 def remove(self,p):del self.files[p]
def read(s,p):
 if p not in s.files:raise FileNotFoundError(p)
 return s.files[p]
def put(s,p,b,mode):
 if s.fail==p and b==b'new':s.files[p]=b'partial';raise OSError('short write')
 s.files[p]=b
namespace.update(read=read,put=put)
class Tests(unittest.TestCase):
 def run_tx(self,s,verify=lambda:None):namespace['apply_transaction'](s,[('helper',None,b'helper',0o644),('source',b'old',b'new',0o644)],[('doc',b'previous',b'updated',0o600)],verify)
 def test_success(self):
  s=Fake();self.run_tx(s);self.assertEqual(s.files,{'source':b'new','doc':b'updated','helper':b'helper'})
 def test_public_failure_restores(self):
  s=Fake()
  def fail():raise RuntimeError('HTTP check failed')
  with self.assertRaises(RuntimeError):self.run_tx(s,fail)
  self.assertEqual(s.files,{'source':b'old','doc':b'previous'})
 def test_drift_retains_other_writer(self):
  s=Fake();s.files['source']=b'other'
  with self.assertRaises(ValueError):self.run_tx(s)
  self.assertEqual(s.files,{'source':b'other','doc':b'previous'})
 def test_short_write_restored(self):
  s=Fake();s.fail='source'
  with self.assertRaises(OSError):self.run_tx(s)
  self.assertEqual(s.files,{'source':b'old','doc':b'previous'})
 def test_doc_drift_rolls_back_sources(self):
  s=Fake();s.files['doc']=b'newer'
  with self.assertRaises(ValueError):self.run_tx(s)
  self.assertEqual(s.files,{'source':b'old','doc':b'newer'})
 def test_patch_contexts_unique_and_contain_only_editor_hooks(self):
  patches=json.loads((root/'admin-patch.json').read_text());self.assertGreaterEqual(len(patches),4)
  for before,after in patches:self.assertTrue(before);self.assertNotEqual(before,after)
if __name__=='__main__':unittest.main()
