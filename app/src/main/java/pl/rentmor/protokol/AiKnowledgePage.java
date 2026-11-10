package pl.rentmor.protokol;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Online editor; never queues writes or stores AI knowledge in shared preferences. */
final class AiKnowledgePage {
    private final Activity activity;
    private final LinearLayout root;
    private final SyncManager sync;
    private final Runnable home;
    private LinearLayout page;
    private EditText editor;
    private TextView status,counter;
    private Button save,refresh,switchAudience;
    private String audience="public",revision="",saved="";
    private boolean busy=false,ready=false;
    AiKnowledgePage(Activity activity,LinearLayout root,SyncManager sync,Runnable home){
        this.activity=activity;this.root=root;this.sync=sync;this.home=home;
    }
    private boolean admin(){return "admin".equals(activity.getSharedPreferences("rentmor",0).getString("role",""));}
    private boolean active(){return !activity.isFinishing()&&page!=null&&page.getParent()==root;}
    private int bytes(String s){return s.getBytes(StandardCharsets.UTF_8).length;}
    private TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(0xff17263e);page.addView(t);return t;}
    private Button button(String label,Runnable action){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setOnClickListener(v->action.run());page.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;}
    void show(){
        if(!admin()){home.run();return;}
        root.removeAllViews();page=new LinearLayout(activity);page.setOrientation(1);root.addView(page,new LinearLayout.LayoutParams(-1,-2));
        button("‹ Panel główny",()->leave(home));
        text("AI — Wiedza",26);
        text("Wiedza dla klientów jest używana w odpowiedziach Mortii. Wiedza prywatna pozostaje wyłącznie w czacie administratora na stronie. Nie wpisuj danych klientów, haseł ani kluczy. Zapis nie wywołuje płatnego zapytania AI.",14);
        status=text("",15);
        switchAudience=button("Otwórz wiedzę prywatną",()->leave(()->{audience=audience.equals("public")?"private":"public";load();}));
        editor=new EditText(activity);editor.setHint("Dopisz zasady, instrukcje i porady o sprzęcie…");editor.setContentDescription("Treść wiedzy AI");editor.setGravity(android.view.Gravity.TOP);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);editor.setMinLines(10);editor.setMaxLines(18);editor.setTextSize(16);page.addView(editor,new LinearLayout.LayoutParams(-1,-2));
        counter=text("0 / 12000 bajtów",13);
        save=button("Zapisz i udostępnij Mortii",()->{
            if(!admin()||busy||!ready)return;
            if(audience.equals("public"))new AlertDialog.Builder(activity).setTitle("Udostępnić wiedzę Mortii?").setMessage("Mortii będzie mógł przekazywać te informacje klientom w kolejnych odpowiedziach.").setNegativeButton("Anuluj",null).setPositiveButton("Zapisz i udostępnij",(d,w)->write()).show();
            else write();
        });
        refresh=button("Pobierz aktualną wiedzę",()->leave(this::load));
        editor.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){controls();}public void afterTextChanged(Editable e){}});
        load();
    }
    void back(){leave(home);}
    private void leave(Runnable action){
        if(busy){Toast.makeText(activity,"Poczekaj na zakończenie operacji.",Toast.LENGTH_SHORT).show();return;}
        if(ready&&!editor.getText().toString().equals(saved))new AlertDialog.Builder(activity).setTitle("Niezapisana wiedza").setMessage("Masz niezapisany tekst. Skopiuj go lub anuluj i zapisz przed przejściem dalej.").setNegativeButton("Anuluj",null).setPositiveButton("Odrzuć zmiany",(d,w)->action.run()).show();
        else action.run();
    }
    private void controls(){
        if(editor==null)return;
        int count=bytes(editor.getText().toString());counter.setText(count+" / 12000 bajtów");counter.setTextColor(count>12000?0xffb91c1c:0xff526174);
        editor.setEnabled(ready&&!busy);save.setEnabled(admin()&&ready&&!busy&&count<=12000&&!editor.getText().toString().equals(saved));
        refresh.setEnabled(!busy);switchAudience.setEnabled(!busy);
        save.setText(audience.equals("public")?"Zapisz i udostępnij Mortii":"Zapisz wiedzę prywatną");
        switchAudience.setText(audience.equals("public")?"Otwórz wiedzę prywatną":"Otwórz wiedzę dla klientów");
    }
    private void load(){
        if(!admin()){home.run();return;}
        busy=true;ready=false;saved="";revision="";editor.setText("");status.setText("Pobieranie…");controls();
        final String requested=audience;
        new Thread(()->{try{JSONObject data=sync.fetchAiKnowledge(requested);activity.runOnUiThread(()->{if(!active())return;if(!admin()){home.run();return;}apply(data);});}
        catch(Exception e){activity.runOnUiThread(()->{if(!active())return;busy=false;status.setText("Nie pobrano wiedzy. "+e.getMessage());controls();});}}).start();
    }
    private void apply(JSONObject data){
        JSONObject note=data.optJSONObject("knowledge");
        if(note==null||!data.optString("audience").equals(audience)||!note.optString("revision").matches("[a-f0-9]{64}")){
            busy=false;status.setText("Nieprawidłowa odpowiedź serwera. Pobierz wiedzę ponownie.");controls();return;
        }
        saved=note.optString("text");revision=note.optString("revision");busy=false;ready=true;editor.setText(saved);
        String updated=note.isNull("updated_at")?"brak zapisu":note.optString("updated_at");
        status.setText((audience.equals("public")?"Wiedza dla klientów — Mortii":"Wiedza prywatna — administrator")+"\nOstatni zapis: "+updated);controls();
    }
    private void write(){
        if(!admin()){home.run();return;}
        if(busy||!ready)return;
        final String draft=editor.getText().toString(),base=revision,kind=audience;
        if(bytes(draft)>12000)return;
        busy=true;status.setText("Zapisywanie…");controls();
        new Thread(()->{try{JSONObject data=sync.saveAiKnowledge(kind,draft,base);activity.runOnUiThread(()->{if(!active())return;if(!admin()){home.run();return;}apply(data);});}
        catch(Exception e){activity.runOnUiThread(()->{if(!active())return;busy=false;status.setText("Nie potwierdzono zapisu. Twój tekst pozostaje w polu. "+e.getMessage());controls();});}}).start();
    }
}
