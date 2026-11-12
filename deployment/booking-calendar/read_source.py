import os, json, base64, hashlib, stat, zlib
import paramiko
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
PUB="-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzwhLyyVoi/s+CNCIlXMP\nV8XASFv6CBb4BDPIQxjvmpL2h+LligZEDCfk7CehutO/yuUvT+fD3WmH9WxP0ofo\nRbRk+TzAEWFo+U52iz2RNXbaWWSuZIM4E1qSaCtoVI5d5kGdbWdWkQKVSaqbipCV\nIo9Prah5o79VqUnlWRcPcwu4Y3iQxghjQOlJQN/GKIzrv1s2IUnXpB5JxHVhtRDS\nUTgFxIC0TsM+qIbO3v1Wptkjh5YtPxHUu1483L587955GrUAXcdyQOHOBuWM8p82\nVwGmnZxFjW7kKCcwmawdVJK+RGu6BOseN0BCAgP04ITEKZiMvmppCTuEoQhuymmi\nMQIDAQAB\n-----END PUBLIC KEY-----\n"
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS: raise RuntimeError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  allowed=['mortii_service.php','ai_booking_store.php','ai_booking_rules.php','mortii_api.php','mortii.js','mortii.css','rental_inventory.php','public_metadata.php','rental_categories.php']
  out={}
  for name in allowed:
   path='public_html/'+name
   try: meta=s.lstat(path)
   except FileNotFoundError: continue
   if not stat.S_ISREG(meta.st_mode) or meta.st_size>250000: raise RuntimeError('Invalid source')
   with s.open(path,'rb') as f: raw=f.read(250001)
   out[name]=raw.decode()
  key=os.urandom(32);nonce=os.urandom(12)
  encrypted=AESGCM(key).encrypt(nonce,zlib.compress(json.dumps(out).encode()),None)
  rsa=serialization.load_pem_public_key(PUB.encode()).encrypt(key,padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
  print('ENCRYPTED_SOURCE='+base64.b64encode(rsa+nonce+encrypted).decode())
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e: print('SOURCE_READ_FAILED:'+type(e).__name__);raise SystemExit(1)
