import hashlib,http.client,json,os,pathlib,shutil,socket,subprocess,tempfile,time,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
class DownloadEndpointTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.root=pathlib.Path(self.tmp.name);shutil.copy(ROOT/'server/download-app.php',self.root/'download-app.php');self.data=b'fixture-apk-only';(self.root/'RentMor.apk').write_bytes(self.data)
  self.meta={'version':'1.7.28','size':len(self.data),'sha256':hashlib.sha256(self.data).hexdigest()};self.write_meta()
  with socket.socket()as s:s.bind(('127.0.0.1',0));self.port=s.getsockname()[1]
  self.server=subprocess.Popen(['php','-S','127.0.0.1:'+str(self.port),'-t',str(self.root)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  for _ in range(100):
   try:
    with socket.create_connection(('127.0.0.1',self.port),timeout=.1):break
   except OSError:time.sleep(.02)
 def tearDown(self):self.server.terminate();self.server.wait(timeout=5);self.tmp.cleanup()
 def write_meta(self):(self.root/'app-release.json').write_text(json.dumps(self.meta))
 def request(self,method='GET'):
  c=http.client.HTTPConnection('127.0.0.1',self.port,timeout=5);c.request(method,'/download-app.php?request=fixture');r=c.getresponse();out=r.status,dict(r.getheaders()),r.read();c.close();return out
 def test_current_filename_and_no_cache(self):
  code,h,b=self.request();self.assertEqual(200,code);self.assertEqual(self.data,b);self.assertIn('no-store',h['Cache-Control']);self.assertIn('RentMor-1.7.28.apk',h['Content-Disposition']);self.assertEqual(str(len(b)),h['Content-Length'])
 def test_head_and_post(self):
  code,h,b=self.request('HEAD');self.assertEqual(200,code);self.assertEqual(b'',b);self.assertIn('Content-Disposition',h);self.assertEqual(405,self.request('POST')[0])
 def test_mismatched_release_is_never_downloaded(self):
  self.meta['sha256']='0'*64;self.write_meta();code,h,b=self.request();self.assertEqual(503,code);self.assertNotIn('Content-Disposition',h);self.assertNotEqual(self.data,b)
 def test_invalid_version_and_symlink_fail_closed(self):
  self.meta['version']='../bad';self.write_meta();self.assertEqual(503,self.request()[0]);self.meta['version']='1.7.28';self.write_meta();(self.root/'RentMor.apk').rename(self.root/'fixture');(self.root/'RentMor.apk').symlink_to(self.root/'fixture');self.assertEqual(503,self.request()[0])
if __name__=='__main__':unittest.main()
