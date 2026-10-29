package pl.rentmor.protokol;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.util.Base64;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
/** Keystore-backed authenticated encryption for device credentials and cached finance data. */
final class SecurePrefs {
  private static final String ALIAS="rentmor.secure.preferences.v1", PREFIX="rm_enc_", FORMAT="v1:";
  private SecurePrefs(){}
  private static volatile SecretKey robolectricTestKey;
  static void setRobolectricTestKey(SecretKey key){
    if(!"robolectric".equals(Build.FINGERPRINT)) throw new SecurityException("Only available in isolated tests");
    robolectricTestKey=key;
  }
  private static SecretKey key() throws Exception {
    if(robolectricTestKey!=null)return robolectricTestKey;
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    SecretKey result=(SecretKey)store.getKey(ALIAS,null);
    if(result!=null)return result;
    synchronized(SecurePrefs.class){
      store.load(null);result=(SecretKey)store.getKey(ALIAS,null);
      if(result!=null)return result;
      KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
      generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
      return generator.generateKey();
    }
  }
  static String seal(SecretKey key,String plain) throws Exception {
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);
    byte[] iv=c.getIV(),ciphertext=c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
    byte[] out=new byte[iv.length+ciphertext.length];
    System.arraycopy(iv,0,out,0,iv.length);System.arraycopy(ciphertext,0,out,iv.length,ciphertext.length);
    return FORMAT+Base64.getEncoder().encodeToString(out);
  }
  static String open(SecretKey key,String value) throws Exception {
    if(value==null||!value.startsWith(FORMAT))throw new SecurityException("Invalid format");
    byte[] all=Base64.getDecoder().decode(value.substring(FORMAT.length()));
    if(all.length<29)throw new SecurityException("Truncated value");
    byte[] iv=new byte[12];System.arraycopy(all,0,iv,0,12);
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
    return new String(c.doFinal(all,12,all.length-12),StandardCharsets.UTF_8);
  }
  static String get(Context context,String field,String fallback){
    SharedPreferences p=context.getSharedPreferences("rentmor",0);
    String cipher=p.getString(PREFIX+field,null);
    if(cipher!=null){
      try{return open(key(),cipher);}
      catch(Exception e){return fallback;} // Fail closed. Do not use a plaintext fallback.
    }
    String legacy=p.getString(field,null);
    if(legacy==null)return fallback;
    put(context,field,legacy);
    return legacy;
  }
  static void put(Context context,String field,String plain){
    try{
      String cipher=seal(key(),plain);
      if(!context.getSharedPreferences("rentmor",0).edit().putString(PREFIX+field,cipher).remove(field).commit())
        throw new SecurityException("Encrypted preference write failed");
    }catch(Exception e){throw new IllegalStateException("Nie można zaszyfrować danych aplikacji.",e);}
  }
  static void remove(Context context,String field){
    context.getSharedPreferences("rentmor",0).edit().remove(PREFIX+field).remove(field).commit();
  }
}
