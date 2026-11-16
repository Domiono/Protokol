package pl.rentmor.protokol;

import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Editor for arbitrary extra equipment; dates and customer belong to the whole booking. */
public final class AdditionalEquipmentEditor {
    private final Context context;
    private final LinearLayout host,rows;
    private final JSONArray names,assets;
    private final boolean admin,locked;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private static final class Entry {
        JSONObject original; LinearLayout box; Spinner model,copy; EditText rate;
        ArrayList<JSONObject> choices=new ArrayList<>();
    }
    public AdditionalEquipmentEditor(Context c,LinearLayout host,JSONArray names,JSONArray assets,JSONObject original,boolean admin){
        this.context=c;this.host=host;this.names=names;this.assets=assets;this.admin=admin;
        locked=original.optBoolean("done")||original.optBoolean("returned")||original.optBoolean("settled");
        TextView hint=new TextView(c);hint.setText("Dodatkowe sprzęty • ten sam klient i termin. Każda pozycja blokuje własny egzemplarz.");host.addView(hint);
        rows=new LinearLayout(c);rows.setOrientation(1);host.addView(rows);
        JSONArray existing=original.optJSONArray("additional_items");
        if(existing!=null)for(int i=0;i<existing.length();i++)add(existing.optJSONObject(i));
        Button add=new Button(c);add.setText("＋ Dodaj sprzęt do rezerwacji");add.setEnabled(!locked);host.addView(add);
        add.setOnClickListener(v->{if(entries.size()>=19){Toast.makeText(c,"Maksymalnie 20 sprzętów w rezerwacji.",Toast.LENGTH_LONG).show();return;}add(null);});
    }
    private void add(JSONObject old){
        Entry e=new Entry();try{e.original=old==null?new JSONObject().put("line_id",UUID.randomUUID().toString()):new JSONObject(old.toString());}catch(JSONException x){throw new IllegalArgumentException(x);}
        e.box=new LinearLayout(context);e.box.setOrientation(1);e.box.setPadding(8,12,8,12);rows.addView(e.box);entries.add(e);
        ArrayList<String> models=new ArrayList<>();models.add("— Wybierz dodatkowy sprzęt —");for(int i=0;i<names.length();i++)if(!models.contains(names.optString(i)))models.add(names.optString(i));
        String previous=e.original.optString("equipment");if(!previous.isEmpty()&&!models.contains(previous))models.add(previous);
        e.model=new Spinner(context);e.model.setContentDescription("Dodatkowy sprzęt");e.model.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,models));
        e.model.setSelection(Math.max(0,models.indexOf(previous)));e.model.setEnabled(!locked);e.box.addView(e.model);
        e.copy=new Spinner(context);e.box.addView(e.copy);e.copy.setEnabled(!locked);
        e.rate=new EditText(context);e.rate.setSingleLine(true);e.rate.setHint("Cena tego sprzętu za dobę (zł)");e.rate.setInputType(8194);e.rate.setText(e.original.optString("daily_rate","0"));
        if(admin){TextView rateLabel=new TextView(context);rateLabel.setText("Cena tego sprzętu za dobę (zł)");e.box.addView(rateLabel);e.box.addView(e.rate);}
        Runnable refresh=()->{
            String model=String.valueOf(e.model.getSelectedItem()),role=RentalRules.kind(model);
            e.choices.clear();e.choices.add(null);ArrayList<String> labels=new ArrayList<>();labels.add("Dobierz wolny egzemplarz przy zapisie");int selected=0;
            for(int i=0;i<assets.length();i++){
                JSONObject a=assets.optJSONObject(i);if(a==null)continue;
                if(("standard".equals(role)?model.equals(a.optString("equipment")):role.equals(RentalRules.assetKind(a)))||a.optString("id").equals(e.original.optString("asset_id"))&&model.equals(previous)){
                    e.choices.add(a);labels.add(a.optString("label")+(a.optBoolean("in_service")?" • serwis":""));
                    if(model.equals(previous)&&a.optString("id").equals(e.original.optString("asset_id")))selected=e.choices.size()-1;
                }
            }
            e.copy.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,labels));e.copy.setSelection(selected);
        };
        e.model.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int n,long id){refresh.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}});refresh.run();
        Button remove=new Button(context);remove.setText("Usuń tę pozycję");remove.setEnabled(!locked);e.box.addView(remove);remove.setOnClickListener(v->{entries.remove(e);rows.removeView(e.box);});
    }
    public JSONArray value()throws JSONException{
        JSONArray result=new JSONArray();for(Entry e:entries){
            if(e.model.getSelectedItemPosition()<=0)throw new IllegalArgumentException("Wybierz sprzęt w każdej dodanej pozycji.");
            JSONObject line=new JSONObject(e.original.toString());String model=String.valueOf(e.model.getSelectedItem());line.put("equipment",model);
            JSONObject a=e.choices.get(Math.max(0,e.copy.getSelectedItemPosition()));
            line.put("auto_select",a==null);line.put("asset_id",a==null?"":a.optString("id"));line.put("asset_label",a==null?"":a.optString("label"));
            if(!model.equals(e.original.optString("equipment"))){line.remove("vacuum_asset_id");line.remove("vacuum_asset_label");line.put("needs_vacuum",false);}
            if(admin)line.put("daily_rate",RentalRules.money(e.rate.getText().toString()).toPlainString());result.put(line);
        }return result;
    }
    public void clear(){entries.clear();rows.removeAllViews();}
}
