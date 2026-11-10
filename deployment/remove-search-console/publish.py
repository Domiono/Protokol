"""Owner-requested native release: same signer, exact source, SSH/PHP transaction."""
import base64
import hashlib
import json
import logging
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid
import paramiko
ROOT=Path(__file__).resolve().parents[2]
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
OLD_APK_SHA='228a2fa287a228b95b359e8fa22581079a1a4786b5d2e24daeb833a7d93ed61f'
logging.getLogger('paramiko').addHandler(logging.NullHandler());logging.getLogger('paramiko').propagate=False
def sha(data):return hashlib.sha256(data).hexdigest()
def b64(data):return base64.b64encode(data).decode()
def connect():
    t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
    try:
        t.start_client(timeout=25)
        if b64(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).rstrip('=')not in PINS:raise ValueError('Host key')
        t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
        return t
    except Exception:t.close();raise
def execute(t,command,data):
    c=t.open_session(timeout=30);c.settimeout(180);c.exec_command(command);c.sendall(data);c.shutdown_write();out=b''
    while True:
        part=c.recv(65536)
        if not part:break
        out+=part
        if len(out)>30000:raise RuntimeError('Oversized receipt')
    code=c.recv_exit_status();lines=[line for line in out.decode(errors='replace').splitlines()if line.startswith('REMOVE_GSC_')]
    for line in lines:print(line)
    if code:raise RuntimeError('Remote operation refused')
    return lines
def remote(t,payload):
    source=(ROOT/'deployment/remove-search-console/remote.php').read_text().replace('__REMOVE_GSC_PAYLOAD__',b64(json.dumps(payload).encode()))
    lines=execute(t,'php -d memory_limit=512M',source.encode())
    return next((json.loads(line.removeprefix('REMOVE_GSC_RESULT '))for line in lines if line.startswith('REMOVE_GSC_RESULT ')),None)
def stage(t,apk):
    path='rentmor-control-private/remove-gsc-stage-'+uuid.uuid4().hex[:24]+'.apk'
    # Command code contains only a generated allowlisted path and public APK hash.
    code="""umask(0077);$d='rentmor-control-private';if(is_link($d)||!is_dir($d)||(fileperms($d)&0077))exit(1);
$p=PATH_VALUE;$f=fopen($p,'xb');if(!$f)exit(1);$n=stream_copy_to_stream(STDIN,$f,100000001);fflush($f);fclose($f);chmod($p,0600);
if($n!==SIZE_VALUE||hash_file('sha256',$p)!==HASH_VALUE){unlink($p);exit(1);}echo "REMOVE_GSC_STAGE_OK\\n";
""".replace('PATH_VALUE',json.dumps(path)).replace('SIZE_VALUE',str(len(apk))).replace('HASH_VALUE',json.dumps(sha(apk)))
    if 'REMOVE_GSC_STAGE_OK'not in execute(t,'php -r '+shlex.quote(code),apk):raise RuntimeError('No staging receipt')
    return path
def signer(path):
    tool=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/apksigner'))[-1]
    text=subprocess.check_output([str(tool),'verify','--print-certs',str(path)],text=True,stderr=subprocess.DEVNULL)
    return [line for line in text.splitlines()if 'certificate SHA-256 digest:'in line]
def checked_apk():
    file=ROOT/'app/build/outputs/apk/release/app-release.apk';apk=file.read_bytes()
    with urllib.request.urlopen('https://rentmor.pl/download-app.php?release=45&verify=remove46',timeout=90)as response:old=response.read(100000001)
    if sha(old)!=OLD_APK_SHA:raise ValueError('Published APK changed')
    with tempfile.TemporaryDirectory()as tmp:
        before=Path(tmp)/'previous.apk';before.write_bytes(old)
        if not signer(before)or signer(before)!=signer(file):raise ValueError('APK signing certificate differs')
    tool=sorted(Path(os.environ['ANDROID_HOME']).glob('build-tools/*/aapt'))[-1]
    package=subprocess.check_output([str(tool),'dump','badging',str(file)],text=True,stderr=subprocess.DEVNULL).splitlines()[0]
    if "name='pl.rentmor.protokol'"not in package or "versionCode='46'"not in package or "versionName='1.7.31'"not in package:raise ValueError('Wrong APK version')
    print('REMOVE_GSC_APK_1.7.31_CODE46_SAME_SIGNER_VERIFIED')
    import zipfile,io
    z=zipfile.ZipFile(io.BytesIO(apk));dex=b''.join(z.read(n)for n in z.namelist()if n.endswith('.dex'))
    if b'SearchConsolePage'in dex or b'Google / SEO'in dex or b'AiKnowledgePage'not in dex:raise ValueError('APK feature removal or AI preservation failed')
    return apk
def check_public(apk):
    for path in ['download-app.php?release=46&verify=removed','RentMor.apk?release46=verified']:
        with urllib.request.urlopen('https://rentmor.pl/'+path,timeout=90)as r:
            if sha(r.read(100000001))!=sha(apk):raise RuntimeError('Public APK differs')
            if path.startswith('download-app')and ('no-store'not in r.headers.get('Cache-Control','')or 'RentMor-1.7.31.apk'not in r.headers.get('Content-Disposition','')):raise RuntimeError('Download headers')
    for endpoint,expected in [('search_console.php',404),('search_console_admin.php',404),('search_console_app.php',404),('protocol_sync.php?view=search_console&days=28',401)]:
        try:
            with urllib.request.urlopen('https://rentmor.pl/'+endpoint,timeout=30)as r:status=r.status
        except urllib.error.HTTPError as e:
            status=e.code
            if expected==401 and json.loads(e.read(10000)).get('ok')is not False:raise RuntimeError('Unauthenticated response')
        if status!=expected:raise RuntimeError('Unauthenticated API allowed')
    print('REMOVE_GSC_PUBLIC_APK_BYTES_HEADERS_AND_UNAUTHENTICATED_API_OK')
def main():
    progress_only='--progress-only'in sys.argv
    baseline=json.loads((ROOT/'deployment/remove-search-console'/('progress-baseline.json'if progress_only else 'baseline.json')).read_text())
    docs={name:{'before_blob':blob,'content':b64((ROOT/'docs'/name).read_bytes())}for name,blob in baseline['docs'].items()}
    payload={'mode':'publish','progress_only':progress_only,'docs':docs,'disable_connection':'--disable-connection'in sys.argv}
    apk=None
    if not progress_only:
        apk=checked_apk()
        payload.update({k:baseline[k]for k in ['source_before','helper_blobs','admin_remove','admin_auth','admin_redirect','api_remove','api_tombstone']})
        payload.update({'apk_before_sha256':OLD_APK_SHA,'apk_sha256':sha(apk),'apk_size':len(apk)})
    t=connect()
    try:
        if apk is not None:
            payload['staged_apk']=stage(t,apk);t.close();t=connect()
        result=remote(t,payload)
        if not result:raise RuntimeError('Missing removal receipt')
        try:
            if apk is not None:check_public(apk)
            for name,row in docs.items():
                if result['sha256'][name]!=sha(base64.b64decode(row['content'])):raise RuntimeError('Progress receipt differs')
        except Exception:
            t.close();t=connect();remote(t,{'mode':'rollback','backup':result['backup']})
            raise RuntimeError('Public checks failed; restore requested')from None
        print('REMOVE_GSC_TWO_PROGRESS_DOCUMENTS_SHA256_0600_VERIFIED')
        if apk is not None:print('REMOVE_GSC_PUBLISHED_VERSION_1.7.31_CODE46_SHA256='+sha(apk))
    finally:t.close()
if __name__=='__main__':
    try:main()
    except Exception as e:print('REMOVE_GSC_RELEASE_STOPPED_'+type(e).__name__);raise SystemExit(1)

