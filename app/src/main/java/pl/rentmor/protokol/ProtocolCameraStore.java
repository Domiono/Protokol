package pl.rentmor.protokol;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** One private output file with a durable reference across activity/process recreation. */
public final class ProtocolCameraStore {
    private final Context context;
    public ProtocolCameraStore(Context context) { this.context=context.getApplicationContext(); }
    private File pendingFile() {
        String name=context.getSharedPreferences("protocol_camera",0).getString("pending","");
        if(!name.matches("capture-[a-f0-9-]+\\.jpg"))return null;
        return new File(new File(context.getFilesDir(),"protocol-camera"),name);
    }
    public Uri pending() {
        File file=pendingFile();
        return file==null?null:androidx.core.content.FileProvider.getUriForFile(context,context.getPackageName()+".protocolphotos",file);
    }
    public Uri create() throws IOException {
        if(pending()!=null)throw new IOException("Poprzednie zdjęcie oczekuje na odczyt");
        File directory=new File(context.getFilesDir(),"protocol-camera");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Brak miejsca na zdjęcie");
        File file=new File(directory,"capture-"+UUID.randomUUID()+".jpg");
        if(!file.createNewFile())throw new IOException("Nie można utworzyć pliku zdjęcia");
        if(!context.getSharedPreferences("protocol_camera",0).edit().putString("pending",file.getName()).commit()){
            file.delete();throw new IOException("Nie można zapamiętać pliku zdjęcia");
        }
        return pending();
    }
    public void clear() {
        Uri uri=pending();File file=pendingFile();
        if(uri!=null)context.revokeUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION|android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        context.getSharedPreferences("protocol_camera",0).edit().remove("pending").commit();
        if(file!=null)file.delete();
    }
}
