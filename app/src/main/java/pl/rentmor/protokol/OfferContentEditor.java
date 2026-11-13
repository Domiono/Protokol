package pl.rentmor.protokol;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Shared catalogue content, plain text only. Empty fields intentionally remove a section. */
final class OfferContentEditor {
    private final Context context;
    private final LinkedHashMap<String,EditText> fields=new LinkedHashMap<>();
    private final ArrayList<Row> questions=new ArrayList<>(),links=new ArrayList<>();
    private final LinearLayout questionList,linkList;
    private String revision;
    private Runnable onChange=()->{};
    private boolean restoring=false;
    private static final String[][] SPEC={
        {"title","Nagłówek dodatkowego opisu"},{"intro","Dodatkowy opis, zastosowania i ograniczenia"},
        {"booking_title","Nagłówek rezerwacji i odbioru"},{"booking_text","Rezerwacja, odbiór i zwrot"},
        {"rental_note","Informacja pod nazwą sprzętu"},{"price_note","Dodatkowe podsumowanie kosztów"},
        {"related_title","Nagłówek powiązanego sprzętu"},{"related_note","Informacja pod powiązanym sprzętem"},
        {"guide","Adres poradnika"},{"guide_label","Nazwa linku do poradnika"},{"rules_label","Nazwa linku do zasad"}
    };
    private static final class Row {
        final LinearLayout view;final EditText first,second;
        Row(LinearLayout v,EditText a,EditText b){view=v;first=a;second=b;}
    }
    OfferContentEditor(Context context,LinearLayout parent,JSONObject source){
        this.context=context;this.revision=source.optString("offer_revision");
        TextView title=new TextView(context);title.setText("PEŁNA TREŚĆ OFERTY");title.setTextSize(18);title.setTextColor(0xff172235);parent.addView(title);
        TextView hint=new TextView(context);hint.setText("Te same treści edytujesz w panelu na stronie. Puste pole usuwa daną treść. Linki prowadzą do RentMor.pl.");hint.setTextColor(0xff526174);parent.addView(hint);
        for(String[] spec:SPEC){EditText field=field(parent,spec[1]);field.setMinLines(spec[0].endsWith("text")||spec[0].equals("intro")?4:2);fields.put(spec[0],field);}
        label(parent,"Pytania i odpowiedzi");questionList=new LinearLayout(context);questionList.setOrientation(1);parent.addView(questionList);
        Button addQuestion=new Button(context);addQuestion.setText("DODAJ PYTANIE");parent.addView(addQuestion);addQuestion.setOnClickListener(v->addRow(false,"",""));
        label(parent,"Linki do powiązanego sprzętu");linkList=new LinearLayout(context);linkList.setOrientation(1);parent.addView(linkList);
        Button addLink=new Button(context);addLink.setText("DODAJ LINK");parent.addView(addLink);addLink.setOnClickListener(v->addRow(true,"",""));
        restore(source);
    }
    private void label(LinearLayout parent,String text){TextView label=new TextView(context);label.setText(text);label.setTextColor(0xff172235);label.setPadding(0,12,0,4);parent.addView(label);}
    private EditText field(LinearLayout parent,String title){
        label(parent,title);EditText input=new EditText(context);input.setTextColor(0xff172235);input.setBackgroundColor(Color.WHITE);input.setGravity(Gravity.TOP|Gravity.START);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);parent.addView(input,new LinearLayout.LayoutParams(-1,-2));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){changed();}public void afterTextChanged(Editable e){}});return input;
    }
    private void changed(){if(!restoring)onChange.run();}
    void setOnChange(Runnable callback){onChange=callback;}
    private void addRow(boolean link,String a,String b){
        ArrayList<Row> rows=link?links:questions;LinearLayout list=link?linkList:questionList;
        if(rows.size()>=40){Toast.makeText(context,"Maksymalnie 40 pozycji.",Toast.LENGTH_SHORT).show();return;}
        LinearLayout view=new LinearLayout(context);view.setOrientation(1);view.setPadding(8,8,8,12);
        EditText first=field(view,link?"Adres strony sprzętu":"Pytanie"),second=field(view,link?"Nazwa linku":"Odpowiedź");second.setMinLines(link?2:4);first.setText(a);second.setText(b);
        Row row=new Row(view,first,second);rows.add(row);list.addView(view);
        Button remove=new Button(context);remove.setText("USUŃ POZYCJĘ");view.addView(remove);remove.setOnClickListener(v->{rows.remove(row);list.removeView(view);changed();});changed();
    }
    void restore(JSONObject source){
        restoring=true;
        try{
            revision=source.optString("offer_revision",revision);JSONObject content=source.optJSONObject("offer_content");if(content==null)content=new JSONObject();
            for(Map.Entry<String,EditText> f:fields.entrySet())f.getValue().setText(content.optString(f.getKey()));
            questions.clear();links.clear();questionList.removeAllViews();linkList.removeAllViews();
            for(String key:new String[]{"questions","related"}){JSONArray rows=content.optJSONArray(key);if(rows==null)continue;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;boolean link=key.equals("related");addRow(link,row.optString(link?"url":"question"),row.optString(link?"label":"answer"));}}
        }finally{restoring=false;}
    }
    JSONObject snapshot() throws JSONException {
        JSONObject content=new JSONObject();for(Map.Entry<String,EditText> field:fields.entrySet())content.put(field.getKey(),field.getValue().getText().toString());
        content.put("questions",rows(questions,false));content.put("related",rows(links,true));
        JSONObject result=new JSONObject();result.put("offer_content",content);result.put("offer_revision",revision);return result;
    }
    private JSONArray rows(ArrayList<Row> rows,boolean link) throws JSONException {
        JSONArray result=new JSONArray();for(Row row:rows){JSONObject data=new JSONObject();data.put(link?"url":"question",row.first.getText().toString());data.put(link?"label":"answer",row.second.getText().toString());result.put(data);}return result;
    }
    void applyTo(JSONObject target) throws JSONException {JSONObject data=snapshot();target.put("offer_content",data.getJSONObject("offer_content"));target.put("offer_revision",revision);}
}
