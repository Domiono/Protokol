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

PHP_SOURCE = "<?php\ndeclare(strict_types=1);\n$p=json_decode(base64_decode('__PAYLOAD__'),true,512,JSON_THROW_ON_ERROR);\numask(0077);\nfunction readf($p){if(is_link($p)||!is_file($p))throw new RuntimeException('Unsafe path');return file_get_contents($p);}\nfunction putf($p,$s,$m){$t=$p.'.seo-'.bin2hex(random_bytes(8));try{if(file_put_contents($t,$s)!==strlen($s)||!chmod($t,$m)||!rename($t,$p)||readf($p)!==$s)throw new RuntimeException('Write');}finally{if(is_file($t))unlink($t);}}\ntry{\nforeach(['rentmor-control-private','rentmor-control-private/progress']as $d)if(is_link($d)||!is_dir($d)||(fileperms($d)&0077))throw new RuntimeException('Private directory');\n$l='rentmor-control-private/catalogue-seo.lock';if(is_link($l))throw new RuntimeException('Lock');\n$f=fopen($l,'c+');chmod($l,0600);if(!$f||!flock($f,LOCK_EX|LOCK_NB))throw new RuntimeException('Busy');\n$rows=[];\n$path='public_html/category.php';$old=readf($path);$new=$old;\nforeach($p['patches']as $patch){if(substr_count($new,$patch[0])!==1)throw new RuntimeException('Template changed');$new=str_replace($patch[0],$patch[1],$new);}\ntoken_get_all($new,TOKEN_PARSE);\n$rows[]=['path'=>$path,'old'=>$old,'new'=>$new,'mode'=>fileperms($path)&0777];\nforeach($p['docs']as $name=>$doc){if(!in_array($name,['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md'],true))throw new RuntimeException('Document');\n$path='rentmor-control-private/progress/'.$name;$old=readf($path);\nif(sha1('blob '.strlen($old).\"\\0\".$old)!==$doc['before_blob'])throw new RuntimeException('Progress changed');\n$rows[]=['path'=>$path,'old'=>$old,'new'=>$doc['content'],'mode'=>0600];}\n$b='rentmor-control-private/catalogue-seo-backup-'.bin2hex(random_bytes(12));if(!mkdir($b,0700))throw new RuntimeException('Backup');\nforeach($rows as $i=>$row)putf($b.'/'.$i,$row['old'],0600);\n$manifest=[];foreach($rows as $i=>$row)$manifest[]=['path'=>$row['path'],'index'=>$i,'before'=>hash('sha256',$row['old']),'after'=>hash('sha256',$row['new']),'mode'=>$row['mode']];\nputf($b.'/manifest.json',json_encode($manifest),0600);\ntry{foreach($rows as $row){if(readf($row['path'])!==$row['old'])throw new RuntimeException('Concurrent change');putf($row['path'],$row['new'],$row['mode']);}\nforeach($rows as $row)if(readf($row['path'])!==$row['new']||(fileperms($row['path'])&0777)!==$row['mode'])throw new RuntimeException('Readback');\n}catch(Throwable $e){foreach(array_reverse($rows)as $row){$current=readf($row['path']);if($current===$row['new'])putf($row['path'],$row['old'],$row['mode']);}throw $e;}\necho 'REMOVE_GSC_RESULT '.json_encode(['ok'=>true,'category_sha256'=>hash('sha256',$new),'backup'=>$b,'private_progress_sha256_verified'=>true]).\"\\n\";\n}catch(Throwable $e){echo 'REMOVE_GSC_STOPPED '.get_class($e).' '.$e->getMessage().\"\\n\";exit(1);}\n"
PATCHES = []
DOC_BASELINES = json.loads("{\"CONTINUE-RENTMOR.md\":\"565f781a2922237bc34aad31e507d85525a257ad\",\"RENTMOR-PROJECT-LOG.md\":\"5267c4e26e42940dbcfdb6c9f0480c7df073500f\"}")
def main():
    docs={n:{'before_blob':blob,'content':(ROOT/'docs'/n).read_text()} for n,blob in DOC_BASELINES.items()}
    source=PHP_SOURCE.replace('__PAYLOAD__',b64(json.dumps({'patches':PATCHES,'docs':docs}).encode()))
    from html.parser import HTMLParser
    class Cards(HTMLParser):
        def __init__(self):super().__init__();self.images=[];self.links=[]
        def handle_starttag(self,tag,attrs):
            a=dict(attrs)
            if tag=='img' and a.get('width')=='300':self.images.append(a.get('alt',''))
            if tag=='a':self.links.append(a.get('href',''))
    for slug in ['sprzet-budowlany','sprzet-ogrodniczy','sprzet-czyszczacy']:
        with urllib.request.urlopen('https://rentmor.pl/'+slug+'/?verify=seo154final',timeout=30) as response:
            html=response.read().decode()
        parser=Cards();parser.feed(html)
        if not parser.images or not all(parser.images) or 'Wynajem:' not in html:raise RuntimeError('Category image/link check')
        if slug=='sprzet-budowlany' and 'Wynajem żyrafy z odkurzaczem w Krakowie' not in html:raise RuntimeError('Set link')
        if '/mortii.js?v=6' not in html:raise RuntimeError('Mortii hook')
        print('CATALOGUE_PUBLIC_OK '+slug+' images='+str(len(parser.images)))
    t=connect()
    try:
        lines=execute(t,'php -d memory_limit=128M',source.encode())
        if not any(x.startswith('REMOVE_GSC_RESULT ')for x in lines):raise RuntimeError('Missing receipt')
    finally:t.close()
if __name__=='__main__':
    try:main()
    except Exception as e:print('CATALOGUE_DEPLOY_STOPPED_'+type(e).__name__);raise SystemExit(1)
