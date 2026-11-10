"""Read-only SSH probe: code markers and document hashes, no private records."""
import base64,hashlib,json,os,paramiko
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
phase='handshake'
try:
 t.start_client(timeout=25)
 if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key mismatch')
 phase='authentication';t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
 phase='ssh-exec';c=t.open_session(timeout=30);c.settimeout(60);c.exec_command('php')
 code="""<?php
$paths=['public_html/admin.php','rentmor-control-private/progress/CONTINUE-RENTMOR.md','rentmor-control-private/progress/RENTMOR-PROJECT-LOG.md'];
$out=['php'=>PHP_VERSION];
foreach($paths as $path){$b=is_file($path)?file_get_contents($path):false;$out[basename($path)]= $b===false?null:['sha256'=>hash('sha256',$b),'blob_sha'=>sha1('blob '.strlen($b)."\\0".$b),'mode'=>fileperms($path)&0777];}
$out['helpers']=[file_exists('public_html/search_console.php'),file_exists('public_html/search_console_admin.php')];
$out['deploy_lock']=is_dir('rentmor-control-private/search-console-publish.lock');
echo "GSC_DIAGNOSTIC_JSON ".json_encode($out)."\\n";
"""
 c.sendall(code.encode());c.shutdown_write();out=b''
 while True:
  b=c.recv(65536)
  if not b:break
  out+=b
 if len(out)>10000:raise ValueError('Oversized probe')
 text=out.decode(errors='replace')
 for line in text.splitlines():
  if line.startswith('GSC_DIAGNOSTIC_JSON '):
   print(line)
 if c.recv_exit_status()!=0:raise ValueError('PHP command failed')
except Exception as e:print('SSH_PROBE_STOPPED',phase,type(e).__name__);raise SystemExit(1)
finally:t.close()
