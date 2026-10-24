package pl.rentmor.protokol;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/** Lightweight native Android profitability explorer in RentMor colours. */
public final class EquipmentDashboard {
    private static final int NAVY=0xff172b43, YELLOW=0xffffcc18, MUTED=0xff64748b, GREEN=0xff047857;
    private static final Locale PL=new Locale("pl","PL");
    private final Activity activity;
    private final JSONArray rentals,expenses;
    private final Consumer<String> addCost;
    private final LinearLayout root, cards;
    private int sort=0, year=0;
    private Spinner years;
    private LinearLayout filters;

    public EquipmentDashboard(Activity activity, JSONArray rentals, JSONArray expenses, Consumer<String> addCost) {
        this.activity=activity;this.rentals=rentals;this.expenses=expenses;this.addCost=addCost;
        root=column();
        build();
        cards=column();
        root.addView(cards,new LinearLayout.LayoutParams(-1,-2));
        render();
    }
    public View view(){return root;}
    private int dp(float p){return Math.round(p*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);return v;}
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    private void surface(View v,int color,int radius) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22ffffff),shape(color,radius),null));
    }
    private LinearLayout card(LinearLayout parent,int color) {
        LinearLayout v=column();v.setPadding(dp(16),dp(14),dp(16),dp(14));
        v.setBackground(shape(color,18));v.setElevation(dp(2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        parent.addView(v,p);return v;
    }
    private TextView text(LinearLayout parent,String value,int sp,int color,boolean bold) {
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(null,1);t.setPadding(0,dp(3),0,dp(3));parent.addView(t);return t;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r=new LinearLayout(activity);r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;
    }
    private static String money(long value){return FinanceDashboard.money(value);}
    private Button choice(LinearLayout row,String title,boolean active,Runnable action) {
        Button b=new Button(activity);b.setText(title);b.setAllCaps(false);b.setTextSize(12);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(45));
        b.setTextColor(active?NAVY:0xff475569);
        surface(b,active?YELLOW:0xffeef2f7,12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(b,p);
        b.setOnClickListener(v->action.run());return b;
    }
    private void build() {
        LinearLayout heading=card(root,NAVY);
        text(heading,"WYNIKI TWOJEGO SPRZĘTU",12,0xffe5c255,true);
        text(heading,"Które maszyny zarabiają najwięcej?",20,Color.WHITE,true);
        text(heading,"Zestawienie modeli na podstawie rozliczonych wynajmów oraz przypisanych kosztów.",12,0xffc1cee0,false);
        LinearLayout controls=card(root,Color.WHITE);
        text(controls,"ZAKRES DANYCH",11,MUTED,true);
        years=new Spinner(activity);
        ArrayList<String> options=new ArrayList<>();options.add("Cała historia");
        int current=LocalDate.now().getYear(),earliest=current;
        for(int i=0;i<rentals.length();i++){
            JSONObject r=rentals.optJSONObject(i);
            LocalDate d=r==null?null:FinanceAnalytics.date(r.optString("settled_at"));
            if(d!=null)earliest=Math.min(earliest,d.getYear());
        }
        for(int i=0;i<expenses.length();i++){
            JSONObject e=expenses.optJSONObject(i);
            LocalDate d=e==null?null:FinanceAnalytics.date(e.optString("date"));
            if(d!=null)earliest=Math.min(earliest,d.getYear());
        }
        for(int y=current;y>=earliest;y--)options.add(String.valueOf(y));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        years.setAdapter(adapter);years.setMinimumHeight(dp(50));years.setPadding(dp(10),0,dp(10),0);
        years.setBackground(shape(0xfff1f5f9,12));
        controls.addView(years,new LinearLayout.LayoutParams(-1,dp(50)));
        years.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){
                int selected=position==0?0:Integer.parseInt(options.get(position));
                if(year!=selected){year=selected;render();}
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        text(controls,"SORTUJ WEDŁUG",11,MUTED,true);
        filters=row(controls);renderFilters();
    }
    private void renderFilters(){
        filters.removeAllViews();
        String[] names={"Przychód","Zysk / strata","Wynajmy"};
        for(int i=0;i<3;i++){final int which=i;choice(filters,names[i],sort==i,()->{if(sort==which)return;sort=which;renderFilters();render();});}
    }
    private void summary(LinearLayout parent,String label,long amount,int color){
        LinearLayout b=column();b.setPadding(dp(8),dp(10),dp(8),dp(10));
        b.setBackground(shape(0xfff4f6fa,12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),dp(4),dp(3),dp(4));parent.addView(b,p);
        text(b,label,11,MUTED,true);
        TextView amountView=text(b,money(amount),18,color,true);
        amountView.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
        amountView.setMaxLines(1);
    }
    private void render() {
        if(cards==null)return;
        cards.removeAllViews();
        LocalDate from=year==0?null:LocalDate.of(year,1,1);
        LocalDate to=year==0?null:LocalDate.of(year,12,31);
        EquipmentAnalytics.Snapshot data=EquipmentAnalytics.calculate(rentals,expenses,from,to);
        List<EquipmentAnalytics.Model> sorted=data.sorted(sort);
        long operating=0,attributed=0,expensesAssigned=0;
        for(EquipmentAnalytics.Model m:sorted){operating+=m.operatingResult();attributed+=m.result();expensesAssigned+=m.runningCosts+m.investments;}
        LinearLayout overview=card(cards,Color.WHITE);
        text(overview,"PODSUMOWANIE • "+(year==0?"CAŁA HISTORIA":year),12,MUTED,true);
        LinearLayout a=row(overview);summary(a,"PRZYCHÓD",data.income,0xff2563eb);summary(a,"PO KOSZTACH MODELI",attributed,attributed<0?0xffc24141:GREEN);
        text(overview,"Rozliczone najmy: "+data.settledRentals+"   •   Modele: "+sorted.size(),13,NAVY,true);
        text(overview,"Wynik uwzględnia straty i koszty przypisane do modeli. Koszty ogólne firmy nie są rozdzielane na sprzęt.",12,MUTED,false);
        if(data.unassignedExpenses>0)text(overview,"Koszty bez wskazanego sprzętu: "+money(data.unassignedCost)+" ("+data.unassignedExpenses+" wpisów) – nie są odjęte od wyniku modeli.",12,0xffb45309,true);
        if(data.skipped>0)text(overview,"Pominięto "+data.skipped+" wpisów bez pełnych danych rozliczenia, kwoty lub daty.",12,0xffb45309,false);
        text(cards,"RANKING MODELI  •  "+sorted.size(),13,NAVY,true);
        if(sorted.isEmpty()) {
            LinearLayout empty=card(cards,Color.WHITE);
            text(empty,"Brak rozliczeń i kosztów sprzętów dla wybranego okresu.",14,MUTED,false);
            return;
        }
        long maxIncome=0;for(EquipmentAnalytics.Model m:sorted)maxIncome=Math.max(maxIncome,m.income);
        for(int i=0;i<sorted.size();i++)showModel(sorted.get(i),i,maxIncome);
    }
    private void showModel(EquipmentAnalytics.Model model,int index,long maxIncome){
        LinearLayout card=card(cards,Color.WHITE);
        LinearLayout top=row(card);
        TextView badge=new TextView(activity);badge.setText(String.valueOf(index+1));badge.setGravity(Gravity.CENTER);
        badge.setTextColor(NAVY);badge.setTextSize(14);badge.setTypeface(null,1);
        badge.setBackground(shape(index==0?YELLOW:0xffe9eef4,10));
        top.addView(badge,new LinearLayout.LayoutParams(dp(35),dp(35)));
        LinearLayout name=column();name.setPadding(dp(10),0,dp(4),0);
        top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        text(name,model.name,16,NAVY,true);
        text(name,model.rentals+" wynajmów • "+(model.missingDays==0?model.knownDays+" dni najmu":model.knownDays+" znanych dni"),12,MUTED,false);
        TextView chevron=new TextView(activity);chevron.setText("⌄");chevron.setTextSize(25);chevron.setTextColor(MUTED);top.addView(chevron);
        LinearLayout amounts=row(card);summary(amounts,"PRZYCHÓD",model.income,0xff2563eb);summary(amounts,"WYNIK MODELU",model.result(),model.result()<0?0xffc24141:GREEN);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000);bar.setProgress(maxIncome==0?0:(int)Math.round(1000.0*model.income/maxIncome));
        bar.setProgressTintList(ColorStateList.valueOf(YELLOW));bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xffe9eef4));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(7));bp.topMargin=dp(8);bp.bottomMargin=dp(5);card.addView(bar,bp);
        TextView hint=text(card,"Pokaż szczegóły i historię  ›",12,0xff475569,false);
        LinearLayout details=column();details.setVisibility(View.GONE);card.addView(details);
        costLine(details,"Straty przy wynajmie",model.losses);
        costLine(details,"Naprawy i koszty przypisane",model.runningCosts);
        costLine(details,"Zakupy sprzętu / inwestycje",model.investments);
        costLine(details,"Wynik bez kosztów zakupu",model.operatingResult());
        if(model.missingDays>0)text(details,"Brak dat najmu przy "+model.missingDays+" rozliczeniach – nie doliczano szacowanych dni.",12,0xffb45309,false);
        text(details,"PRZYCHODY W OSTATNICH MIESIĄCACH",11,MUTED,true);
        YearMonth last=year==0?YearMonth.now():YearMonth.of(year,12);
        long max=0;for(int n=5;n>=0;n--){long[] a=model.monthly.get(last.minusMonths(n));if(a!=null)max=Math.max(max,a[0]);}
        for(int n=5;n>=0;n--){YearMonth m=last.minusMonths(n);long[] a=model.monthly.get(m);long val=a==null?0:a[0];monthLine(details,m,val,max);}
        if(!model.history.isEmpty()){
            text(details,"OSTATNIE ROZLICZENIA",11,MUTED,true);
            ArrayList<JSONObject> history=new ArrayList<>(model.history);
            history.sort((a,b)->b.optString("settled_at").compareTo(a.optString("settled_at")));
            for(int i=0;i<Math.min(history.size(),4);i++){
                JSONObject r=history.get(i);
                Long rev=FinanceAnalytics.amount(r,"income"),loss=FinanceAnalytics.amount(r,"loss");
                text(details,r.optString("settled_at")+"  •  "+money(rev==null?0:rev)+" przychodu"+(loss!=null&&loss>0?"  •  strata "+money(loss):""),12,MUTED,false);
            }
        }
        Button newCost=new Button(activity);newCost.setText("＋ Dodaj koszt tego modelu");newCost.setAllCaps(false);newCost.setTextSize(13);
        newCost.setTextColor(NAVY);newCost.setMinHeight(dp(48));surface(newCost,YELLOW,12);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(48));ap.topMargin=dp(12);details.addView(newCost,ap);
        newCost.setOnClickListener(v->addCost.accept(model.name));
        top.setContentDescription("Szczegóły modelu "+model.name);
        View.OnClickListener toggle=v->{
            boolean expanded=details.getVisibility()==View.VISIBLE;
            if(ValueAnimator.areAnimatorsEnabled()) {
                AutoTransition transition=new AutoTransition();transition.setDuration(210);
                TransitionManager.beginDelayedTransition(card,transition);
            }
            details.setVisibility(expanded?View.GONE:View.VISIBLE);
            hint.setText(expanded?"Pokaż szczegóły i historię  ›":"Zwiń szczegóły  ▴");
            chevron.animate().rotation(expanded?0:180).setDuration(ValueAnimator.areAnimatorsEnabled()?210:0).start();
        };
        top.setOnClickListener(toggle);hint.setOnClickListener(toggle);
        if(ValueAnimator.areAnimatorsEnabled()&&index<8){
            card.setAlpha(0f);card.setTranslationY(dp(8));
            card.animate().alpha(1f).translationY(0).setDuration(250).setStartDelay(index*35L).start();
        }
    }
    private void costLine(LinearLayout parent,String label,long amount){
        LinearLayout line=row(parent);TextView title=text(line,label,12,MUTED,false);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        text(line,money(amount),12,amount<0?0xffb42318:NAVY,true);
    }
    private void monthLine(LinearLayout parent,YearMonth month,long amount,long max){
        LinearLayout line=row(parent);
        String date=month.format(DateTimeFormatter.ofPattern("MM.yyyy"));
        TextView label=text(line,date,11,MUTED,false);label.setLayoutParams(new LinearLayout.LayoutParams(dp(57),-2));
        ProgressBar p=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);p.setMax(1000);
        int goal=max==0?0:(int)Math.round(1000.0*amount/max);
        p.setProgressTintList(ColorStateList.valueOf(0xff2563eb));
        p.setProgressBackgroundTintList(ColorStateList.valueOf(0xffeff2f6));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(7),1);lp.setMargins(dp(6),0,dp(6),0);
        line.addView(p,lp);
        TextView num=text(line,money(amount),11,NAVY,false);num.setGravity(Gravity.END);
        num.setLayoutParams(new LinearLayout.LayoutParams(dp(96),-2));
        if(ValueAnimator.areAnimatorsEnabled()&&amount>0) {
            ObjectAnimator.ofInt(p,"progress",0,goal).setDuration(360).start();
        } else p.setProgress(goal);
    }
}
