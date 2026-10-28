package pl.rentmor.protokol;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Private camera output for offer photos, independent of protocol captures. */
public final class ProductCameraStore {
    private final Context context;
    public ProductCameraStore(Context context) { this.context=context.getApplicationContext(); }

    private File pendingFile() {
        String name=context.getSharedPreferences("product_camera",0).getString("pending","");
        if(!name.matches("capture-[a-f0-9-]+\\.jpg"))return null;
        return new File(new File(context.getFilesDir(),"product-camera"),name);
    }

    public Uri pending() {
        File file=pendingFile();
        return file==null?null:FileProvider.getUriForFile(context,context.getPackageName()+".protocolphotos",file);
    }

    public Uri create() throws IOException {
        if(pending()!=null)throw new IOException("Poprzednie zdjęcie oczekuje na odczyt");
        File dir=new File(context.getFilesDir(),"product-camera");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Nie można utworzyć folderu zdjęć");
        File file=new File(dir,"capture-"+UUID.randomUUID()+".jpg");
        if(!file.createNewFile())throw new IOException("Nie można utworzyć pliku zdjęcia");
        if(!context.getSharedPreferences("product_camera",0).edit().putString("pending",file.getName()).commit()) {
            file.delete();
            throw new IOException("Nie udało się zapamiętać zdjęcia");
        }
        return pending();
    }

    public void clear() {
        Uri uri=pending();
        File file=pendingFile();
        if(uri!=null)context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        context.getSharedPreferences("product_camera",0).edit().remove("pending").commit();
        if(file!=null)file.delete();
    }

    public File draftFile() { return new File(context.getFilesDir(),"product-camera-draft.json"); }

    public void saveDraft(String json) throws IOException {
        File target=draftFile();
        File temp=new File(target.getParentFile(),target.getName()+".tmp");
        byte[] data=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(temp)) {
            stream.write(data);
            stream.getFD().sync();
        }
        if(!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Nie udało się zabezpieczyć formularza");
        }
    }

    public void clearDraft() { draftFile().delete(); }
}
