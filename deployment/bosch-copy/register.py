"""Run fixed inventory registration over verified SSH; never log server contents."""
import base64
import hashlib
import logging
import os
from pathlib import Path
import shlex
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'mortii-copy'))
from deploy import PINS
import paramiko
logging.getLogger('paramiko').addHandler(logging.NullHandler())
logging.getLogger('paramiko').propagate=False
transport=paramiko.Transport((os.environ['FTP_SERVER'],22222))
try:
    transport.start_client(timeout=25)
    key=base64.b64encode(hashlib.sha256(transport.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
    if key not in PINS: raise ValueError('Host key')
    transport.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
    php=(Path(__file__).with_name('register.php')).read_text()
    # PHP CLI is requested through the existing SSH account. No HTTP endpoint.
    channel=transport.open_session(timeout=20);channel.settimeout(60)
    channel.exec_command('cd public_html && php -r '+shlex.quote(php.removeprefix('<?php\n')))
    output=channel.makefile('rb').read(4096).decode(errors='replace')
    status=channel.recv_exit_status()
    allowed={'REGISTERED_ONE_COPY_BACKUP_VERIFIED','ONE_COPY_ALREADY_REGISTERED','TARGET_TERM_AVAILABLE','TARGET_TERM_REQUIRES_OWNER_REVIEW'}
    for line in output.splitlines():
        if line in allowed: print(line)
    if status!=0 or not any(x in output.splitlines() for x in allowed):
        raise RuntimeError('SSH execution unavailable or registration stopped')
except Exception as e:
    print('STOPPED_'+type(e).__name__);sys.exit(1)
finally:transport.close()
