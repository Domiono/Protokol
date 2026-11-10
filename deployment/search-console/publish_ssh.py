"""Publish only panel source and progress documents via pinned SSH/PHP stdin."""
import base64
import hashlib
import json
import os
from pathlib import Path
import sys
import paramiko
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from patch_search_console import HOOKS
sys.path.insert(0,str(Path(__file__).resolve().parent))
from publish import PINS,DOCS,http

def command(t,payload):
    source=(ROOT/'deployment/search-console/remote.php').read_text()
    encoded=base64.b64encode(json.dumps(payload).encode()).decode()
    source=source.replace('__GSC_PAYLOAD__',encoded)
    channel=t.open_session(timeout=30);channel.settimeout(120);channel.exec_command('php')
    channel.sendall(source.encode());channel.shutdown_write();result=b''
    while True:
        part=channel.recv(65536)
        if not part:break
        result+=part
        if len(result)>20000:raise RuntimeError('Oversized publication response')
    code=channel.recv_exit_status()
    lines=[line for line in result.decode(errors='replace').splitlines()if line.startswith('GSC_SSH_')]
    for line in lines:print(line)
    if code:raise RuntimeError('SSH publication refused')
    return next((json.loads(line.removeprefix('GSC_SSH_RESULT '))for line in lines if line.startswith('GSC_SSH_RESULT ')),None)

def main():
    notes_only='--progress-only'in sys.argv
    expected=DOCS
    if notes_only:expected=json.loads((ROOT/'deployment/search-console/progress-baseline.json').read_text())
    payload={'mode':'publish','progress_only':notes_only,'hooks':HOOKS,'helpers':{},'docs':{},
        'admin_before_sha':'4cbf795e663e73fe893a209138366cca4662ab8e982448242b86a2f4b2d03783'}
    for name in ['search_console.php','search_console_admin.php']:
        payload['helpers'][name]=base64.b64encode((ROOT/'server'/name).read_bytes()).decode()
    for name,sha in expected.items():
        payload['docs'][name]={'before_blob':sha,'content':base64.b64encode((ROOT/'docs'/name).read_bytes()).decode()}
    t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
    try:
        t.start_client(timeout=25)
        if base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')not in PINS:raise ValueError('Host key')
        t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
        result=command(t,payload)
        if not result:raise RuntimeError('No publication receipt')
        try:
            if not notes_only:
                http('admin.php',200);http('search_console.php',404);http('search_console_admin.php',404);http('admin.php?tab=google&gsc_callback=1',403)
            for name,row in payload['docs'].items():
                if result['sha256'][name]!=hashlib.sha256(base64.b64decode(row['content'])).hexdigest():raise RuntimeError('Document receipt differs')
        except Exception:
            command(t,{'mode':'rollback','backup':result['backup']})
            raise RuntimeError('Public check failed; rollback requested')from None
        print('TWO_PROGRESS_DOCUMENTS_SHA256_AND_0600_VERIFIED')
        if not notes_only:print('SEARCH_CONSOLE_PUBLISHED_LOGIN_PROTECTED_GOOGLE_OWNER_SETUP_REQUIRED')
    finally:t.close()

if __name__=='__main__':
    try:main()
    except Exception as e:print('SEARCH_CONSOLE_SSH_PUBLICATION_STOPPED_'+type(e).__name__);raise SystemExit(1)
