package pl.rentmor.protokol;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.CheckBox;
import android.widget.TextView;
import java.util.List;

/** Keeps a missing acknowledgement distinct from a confirmed physical loss. */
final class ReturnChecklist {
    static String pending(List<CheckBox> checks) {
        StringBuilder names=new StringBuilder();
        for(CheckBox check:checks)if(!check.isChecked()){
            if(names.length()>0)names.append("\n");
            names.append("• ").append(check.getText());
        }
        return names.toString();
    }
    static void refresh(TextView summary,List<CheckBox> checks) {
        int confirmed=0;for(CheckBox check:checks)if(check.isChecked())confirmed++;
        summary.setText(checks.isEmpty()
            ?"Przy wydaniu nie wpisano akcesoriów. Sprawdź komplet i opisz wynik w uwagach."
            :"Potwierdzono: "+confirmed+" z "+checks.size()+". Zaznacz każdy sprawdzony element.");
        summary.setTextColor(checks.isEmpty()||confirmed<checks.size()?0xff92400e:0xff166534);
    }
    static void confirm(Context context,List<CheckBox> checks,Runnable save) {
        String missing=pending(checks);
        if(missing.isEmpty()){save.run();return;}
        new AlertDialog.Builder(context).setTitle("Niepotwierdzone akcesoria")
            .setMessage(missing+"\n\nWróć do sprawdzania albo zapisz zwrot z listą niepotwierdzonych elementów w uwagach. Nie oznacza to automatycznego naliczenia opłaty.")
            .setNegativeButton("Sprawdź",null)
            .setPositiveButton("Zapisz z uwagą",(d,w)->save.run()).show();
    }
}
