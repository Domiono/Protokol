package pl.rentmor.protokol;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

/** Debounced local drafts: never sends text or signatures to the network. */
final class FormAutosave {
    private static final long DELAY_MS=1200;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable writer;
    private final Runnable pending=this::write;
    private boolean scheduled=false,closed=false;

    FormAutosave(Runnable writer) { this.writer=writer; }

    void watch(EditText... fields) {
        for(EditText field:fields)field.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){schedule();}
            @Override public void afterTextChanged(Editable text){}
        });
    }

    void schedule() {
        if(closed)return;
        handler.removeCallbacks(pending);
        scheduled=true;
        handler.postDelayed(pending,DELAY_MS);
    }

    private void write() {
        if(closed || !scheduled)return;
        scheduled=false;
        writer.run();
    }

    void flush() {
        if(closed || !scheduled)return;
        handler.removeCallbacks(pending);
        write();
    }

    void stop() {
        closed=true;
        scheduled=false;
        handler.removeCallbacks(pending);
    }
}
