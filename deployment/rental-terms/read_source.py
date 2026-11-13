import os, json, base64, hashlib, stat, zlib
import paramiko
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
PUB="-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoXNwys41mUpQ21GXPRWC\nz1KMOUtoE47VfYWthFqp5/8r0enWTRpsAThs/ATXEgWlnTvulPbioYTQ9ql/okyt\nB9+ipvOCX4QT4ItsSUTToZobaN3lDzFf7/05lIhTgNSfP5QxoglBKaUkGBK3WWdA\nzM8w06hAiSQs4vVq1cwHv4cXLbYKlL3ECZcePtv18K3YPIo1+vxNqC5HNHBfRTa3\n6PreXLA6ZYY2oCNsxll7G74maylo58Bwt4eeyXpgGTQ6cC8q2h+oE5eEVcVYQk9D\nIH4qhmJY5bSDp19cBBcwx1SH3LGtIrfEF1GTPoWFvBF9i7YFpX7vX7f3a+9VAJua\n7QIDAQAB\n-----END PUBLIC KEY-----\n"
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS: raise RuntimeError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  allowed=['zasady.php','seo_structure.php','rental_offer_details.php','product_description.php']
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
