import os, json, base64, hashlib, stat, zlib
import paramiko
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
PUB='-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyvPgBbZ4uXJ6jdmBdCNj\n04G+JlNSCCKxhXL1K8D4/3Td+HnUm5qrBFjPnoZL+mpVv8kZZ4vqeZMn4UenaQUu\nXeJbMs6o0k9gQ4AKaALY9Itq2lpiRfpvSj3WoxMk+ytKvbqdJybjTIXTd2K0TjV5\nVeX/RyjCxi0UM2Wl3NhPiRoPm+0u8D3PLqHlDTNBvPQIlz9jKdjRDqzTMnoyZw7E\n3VYXY7RkjSOdElYVBTtiy0if21MTMld5cY7WyjbQith2OVYNO6XQTOdDl6rnjc2D\nbfsAI6kzT17KjVwhVcxvELVwQ1yVczzFx68dSDAX8RYHeErUgDt4+yTX2URSq+7b\nwwIDAQAB\n-----END PUBLIC KEY-----\n'
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS: raise RuntimeError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  allowed=['protocol_sync.php','rental_inventory.php','admin.php','admin_safety.php','booking_schedule.php']
  out={}
  for name in allowed:
   path='public_html/'+name
   try: meta=s.lstat(path)
   except FileNotFoundError: continue
   if not stat.S_ISREG(meta.st_mode) or meta.st_size>250000: raise RuntimeError('Invalid source')
   with s.open(path,'rb') as f: raw=f.read(250001)
   out[name]=raw.decode()
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   path='rentmor-control-private/progress/'+name
   with s.open(path,'rb') as f: out['progress/'+name]=f.read(500001).decode()
  key=os.urandom(32);nonce=os.urandom(12)
  encrypted=AESGCM(key).encrypt(nonce,zlib.compress(json.dumps(out).encode()),None)
  rsa=serialization.load_pem_public_key(PUB.encode()).encrypt(key,padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
  print('ENCRYPTED_SOURCE='+base64.b64encode(rsa+nonce+encrypted).decode())
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e: print('SOURCE_READ_FAILED:'+type(e).__name__);raise SystemExit(1)
