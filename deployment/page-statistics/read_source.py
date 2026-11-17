import os, json, base64, hashlib, stat, zlib
import paramiko
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
PINS={'nJyQovQRMmJEJuzI44hZS/VhZPrOhzRoEyh92Go6TEk','GDfRh92pfMOviF06fYXQSIHQ0OSKwDNOXqzr+Tslrr0'}
PUB='-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzZ7ZQV0mOopUH3NZsypZ\nM03ykMKZiqC5PeYvhwFPsBgDiH4o6Os6/va7OmYNOzDNIc2inaBtY3z9AznndQfs\nmCHzjAV/p18gi55V67XvdswybfaFQ/uSAfNw7HXP6YT6GgQfY2bAqOJLxTbJRDv1\nCUBXs2ll9LMH5Tb5lGZ8vg3K0rW/2z2Il0zlmuBrEgg/7kOdTJ0gbSCggpT2gKX4\n3N50k0Q4rj4qCBiwiJou/W3DQBgo6q9tN7cCZrx6oTuAMACgc4W3xa3NQjQOjm55\nbzs0253M9lKlzEe+jeh2/nuWJhp+EAvW4cqhp36i+oZi7nAhV2hqtJqyE4nrybSo\nqQIDAQAB\n-----END PUBLIC KEY-----\n'
def main():
 t=paramiko.Transport((os.environ['FTP_SERVER'],22222))
 try:
  t.start_client(timeout=25)
  pin=base64.b64encode(hashlib.sha256(t.get_remote_server_key().asbytes()).digest()).decode().rstrip('=')
  if pin not in PINS: raise RuntimeError('Host key mismatch')
  t.auth_password(os.environ['FTP_USERNAME'],os.environ['FTP_PASSWORD'],fallback=False)
  s=paramiko.SFTPClient.from_transport(t);s.get_channel().settimeout(30)
  allowed=['browser_stats.php','pageview.php','online.js','category.php','zasady.php','polityka-prywatnosci.php','public_metadata.php','index.php','admin.php','poradnik-jaki-papier-scierny-do-zyrafy.php','poradnik-pranie-tapicerki-karcher-puzzi.php','poradnik-agregat-malarski-jak-malowac-dobor-dysz.php']
  out={}
  for name in allowed:
   path='public_html/'+name
   try: meta=s.lstat(path)
   except FileNotFoundError: continue
   if not stat.S_ISREG(meta.st_mode) or meta.st_size>1000000: raise RuntimeError('Invalid source')
   with s.open(path,'rb') as f: raw=f.read(1000001)
   out[name]=raw.decode()
  for name in ['CONTINUE-RENTMOR.md','RENTMOR-PROJECT-LOG.md']:
   with s.open('rentmor-control-private/progress/'+name,'rb') as f: out['progress/'+name]=f.read(2000000).decode()
  key=os.urandom(32);nonce=os.urandom(12)
  encrypted=AESGCM(key).encrypt(nonce,zlib.compress(json.dumps(out).encode()),None)
  rsa=serialization.load_pem_public_key(PUB.encode()).encrypt(key,padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
  print('ENCRYPTED_SOURCE='+base64.b64encode(rsa+nonce+encrypted).decode())
 finally:t.close()
if __name__=='__main__':
 try:main()
 except Exception as e: print('SOURCE_READ_FAILED:'+type(e).__name__);raise SystemExit(1)
