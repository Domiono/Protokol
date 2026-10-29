package pl.rentmor.protokol;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.crypto.spec.SecretKeySpec;
public class SecurePrefsTest {
  @Test public void authenticatedEncryptionRejectsTampering() throws Exception {
    byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)i;
    SecretKeySpec key=new SecretKeySpec(b,"AES");
    String value="Klient testowy: 123 zł";
    String enc=SecurePrefs.seal(key,value);
    assertEquals(value,SecurePrefs.open(key,enc));
    assertFalse(enc.contains("123 zł"));
    char c=enc.charAt(enc.length()-2);
    String altered=enc.substring(0,enc.length()-2)+(c=='A'?'B':'A')+enc.substring(enc.length()-1);
    try{SecurePrefs.open(key,altered);fail("Tampering accepted");}catch(Exception expected){}
  }
}
