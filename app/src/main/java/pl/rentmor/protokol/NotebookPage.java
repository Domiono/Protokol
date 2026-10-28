package pl.rentmor.protokol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.util.List;

/** Small, offline checklist with explicit save and reversible completion. */
final class NotebookPage {
    private final Activity activity;
    private final LinearLayout root;
    private final NotebookStore store;
    private final SyncManager sync;
    private final Runnable home;
    private boolean syncing=false,again=false;
    private LinearLayout list;
    private TextView summary;
    private String category;
    private boolean completed;
    NotebookPage(Activity activity,LinearLayout root,NotebookStore store,SyncManager sync,Runnable home){
        this.activity=activity;this.root=root;this.store=store;this.sync=sync;this.home=home;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color){
        GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(12));return bg;
    }
    private TextView text(String value,int size,int color){
        TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);return view;
    }
    private Button button(String label,boolean primary,Runnable action){
        Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(0xff17263e);b.setMinHeight(dp(48));b.setPadding(dp(10),dp(8),dp(10),dp(8));
        b.setBackgroundTintList(null);b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2017263e),background(primary?0xffffd45c:0xffeaf0f7),null));
        b.setOnClickListener(v->action.run());return b;
    }
    void show(){
        root.removeAllViews();root.setPadding(dp(3),dp(5),dp(3),dp(16));
        root.addView(button("‹ Panel główny",false,home),new LinearLayout.LayoutParams(-1,-2));
        TextView title=text("Notatki i zadania",26,0xff17263e);title.setTypeface(null,1);title.setPadding(0,dp(18),0,dp(6));root.addView(title);
        TextView hint=text("Dostępne offline • synchronizacja wyłącznie dla administratora",12,0xff526174);root.addView(hint);
        summary=text("",14,0xff526174);summary.setPadding(0,dp(10),0,dp(10));root.addView(summary);
        root.addView(button("＋ Dodaj wpis",true,()->edit(null)),new LinearLayout.LayoutParams(-1,-2));
        root.addView(button("⟳ Synchronizuj notatnik",false,()->syncNow()),new LinearLayout.LayoutParams(-1,-2));
        Spinner categories=new Spinner(activity);
        String[] options={"Wszystkie kategorie","Zakupy","Umowy","Inne"};
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);categories.setAdapter(adapter);
        categories.setContentDescription("Filtr kategorii");categories.setMinimumHeight(dp(48));
        root.addView(categories,new LinearLayout.LayoutParams(-1,-2));
        CheckBox done=new CheckBox(activity);done.setText("Pokaż zrobione");done.setTextColor(0xff17263e);done.setMinHeight(dp(48));root.addView(done);
        list=new LinearLayout(activity);list.setOrientation(1);root.addView(list,new LinearLayout.LayoutParams(-1,-2));
        categories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){category=position==0?null:options[position];render();}
            public void onNothingSelected(AdapterView<?> p){}
        });
        done.setOnCheckedChangeListener((v,checked)->{completed=checked;render();});render();syncNow();
    }
    private void render(){
        list.removeAllViews();
        try{
            summary.setText("Do zrobienia: "+store.pendingCount()+(store.conflicts()>0?" • Konflikty: "+store.conflicts():""));
            List<NotebookStore.Entry> entries=store.entries(category,completed);
            if(entries.isEmpty()){
                TextView empty=text(completed?"Brak zrobionych wpisów w tej kategorii.":"Nie ma tu spraw do zrobienia. Dodaj np. zakup worków albo wydruk umów.",15,0xff526174);
                empty.setPadding(dp(6),dp(20),dp(6),dp(20));list.addView(empty);
            }
            for(NotebookStore.Entry entry:entries)addEntry(entry);
        }catch(Exception error){error(error);}
    }
    private void addEntry(NotebookStore.Entry entry){
        LinearLayout card=new LinearLayout(activity);card.setOrientation(1);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(background(Color.WHITE));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);list.addView(card,cp);
        TextView categoryLabel=text(entry.category+(entry.conflict?" • KONFLIKT: sprawdź wpis i zapisz ponownie":""),12,entry.conflict?0xffb45309:0xff526174);card.addView(categoryLabel);
        CheckBox check=new CheckBox(activity);check.setText(entry.text);check.setTextSize(16);check.setTextColor(entry.done?0xff697386:0xff17263e);
        check.setMinHeight(dp(48));check.setGravity(Gravity.TOP);check.setPadding(0,dp(8),0,dp(8));check.setChecked(entry.done);
        if(entry.done)check.setPaintFlags(check.getPaintFlags()|Paint.STRIKE_THRU_TEXT_FLAG);
        card.addView(check,new LinearLayout.LayoutParams(-1,-2));
        if(entry.conflict){
            String remote=entry.remoteDeleted?"Wpis usunięty na innym urządzeniu":
                "Wersja z drugiego urządzenia ("+entry.remoteCategory+"): "+entry.remoteText;
            TextView other=text(remote+"\nSprawdź obie wersje i wybierz „Edytuj”, aby świadomie zapisać własną.",12,0xff9a3412);
            other.setPadding(0,dp(8),0,dp(8));card.addView(other);
        }
        check.setOnCheckedChangeListener((v,checked)->{
            try{store.setDone(entry.id,checked);render();syncNow();}catch(Exception error){check.setChecked(entry.done);error(error);}
        });
        LinearLayout actions=new LinearLayout(activity);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);left.rightMargin=dp(8);
        actions.addView(button("Edytuj",false,()->edit(entry)),left);
        actions.addView(button("Usuń",false,()->new AlertDialog.Builder(activity).setTitle("Usunąć wpis?")
            .setMessage("Wpis zostanie usunięty z notatnika.").setNegativeButton("Anuluj",null)
            .setPositiveButton("Usuń",(dialog,which)->{try{store.delete(entry.id);render();syncNow();}catch(Exception error){error(error);}}).show()),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(actions);
    }
    private void edit(NotebookStore.Entry entry){
        LinearLayout form=new LinearLayout(activity);form.setOrientation(1);form.setPadding(dp(20),dp(8),dp(20),dp(8));
        Spinner type=new Spinner(activity);ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,NotebookStore.CATEGORIES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(adapter);type.setMinimumHeight(dp(48));type.setContentDescription("Kategoria wpisu");
        String initial=entry==null?category:entry.category;
        for(int i=0;i<NotebookStore.CATEGORIES.length;i++)if(NotebookStore.CATEGORIES[i].equals(initial))type.setSelection(i);
        form.addView(type,new LinearLayout.LayoutParams(-1,-2));
        EditText input=new EditText(activity);input.setHint("Co trzeba zrobić?");input.setTextSize(16);input.setMinLines(3);input.setMaxLines(8);input.setGravity(Gravity.TOP);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NotebookStore.MAX_LENGTH)});
        if(entry!=null)input.setText(entry.text);
        form.addView(input,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(activity);scroll.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(entry==null?"Nowy wpis":"Edytuj wpis")
            .setView(scroll).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                store.save(entry==null?null:entry.id,input.getText().toString(),type.getSelectedItem().toString());
                dialog.dismiss();render();syncNow();
            }catch(IllegalArgumentException error){input.setError(error.getMessage());}
            catch(Exception error){error(error);}
        });
    }
    private void syncNow(){
        if(syncing){again=true;return;}
        syncing=true;
        new Thread(()->{
            try {
                int conflicts=sync.syncNotebook(store);
                activity.runOnUiThread(()->{
                    syncing=false;
                    render();
                    if(again){again=false;syncNow();}
                    Toast.makeText(activity,conflicts>0?
                        "Notatnik zsynchronizowany, ale wykryto konflikty. Sprawdź oznaczone wpisy.":
                        "Notatnik zsynchronizowany.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                activity.runOnUiThread(()->{
                    syncing=false;again=false;
                    Toast.makeText(activity,"Notatki pozostają w telefonie. Brak synchronizacji: "+
                        e.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    private void error(Exception error){Toast.makeText(activity,"Nie udało się zapisać lub odczytać wpisu. "+error.getMessage(),Toast.LENGTH_LONG).show();}
}
