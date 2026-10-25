package pl.rentmor.protokol;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;

public final class FinanceDashboard {
    private final Context context;private final JSONArray rentals,expenses;private final LinearLayout root;
    private LocalDate from=LocalDate.now().minusDays(29),to=LocalDate.now();private int grain=0;private boolean allHistory=false;
    private final boolean[] visible={true,true,true};private final int[] colors={0xff2563eb,0xffe4a000,0xff059669};
    private static final Locale PL=new Locale("pl","PL");private final Runnable equipmentAction,costAction;
    public FinanceDashboard(Context context,JSONArray rentals,JSONArray expenses,Runnable equipment,Runnable costs){this.context=context;this.rentals=rentals;this.expenses=expenses;equipmentAction=equipment;costAction=costs;root=column();render();}
    public View view(){return root;}
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(context);l.setOrientation(1);return l;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=new TextView(context);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);t.setPadding(0,dp(4),0,dp(5));parent.addView(t);return t;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(16),dp(13),dp(16),dp(13));c.setBackground(bg(Color.WHITE));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);root.addView(c,p);return c;}
    private Button button(LinearLayout row,String label,boolean selected,Runnable action){Button b=new Button(context);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(selected?Color.WHITE:0xff334155);b.setBackground(bg(selected?0xff172b43:0xffeef2f7));b.setMinHeight(dp(44));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(2),dp(4),dp(2),dp(4));row.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    private LinearLayout row(LinearLayout parent){LinearLayout row=new LinearLayout(context);row.setOrientation(0);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    public static String money(long n){return String.format(PL,"%,.2f zł",n/100.0);}
    private String date(LocalDate d){return d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));}
    private String shortDate(LocalDate d){return d.format(DateTimeFormatter.ofPattern("dd.MM"));}
    private void chooseDate(boolean start){LocalDate d=start?from:to;new DatePickerDialog(context,(v,y,m,day)->{LocalDate chosen=LocalDate.of(y,m+1,day);LocalDate a=start?chosen:from,b=start?to:chosen;if(b.isBefore(a)||ChronoUnit.DAYS.between(a,b)>3660){Toast.makeText(context,"Wybierz prawidłowy okres (maks. 10 lat).",Toast.LENGTH_LONG).show();return;}from=a;to=b;allHistory=false;render();},d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()).show();}
    private void shift(int direction){long days=ChronoUnit.DAYS.between(from,to)+1;from=from.plusDays(direction*days);to=to.plusDays(direction*days);allHistory=false;render();}
    private void showAllHistory(){
        LocalDate today=LocalDate.now(),first=null;
        for(int i=0;i<rentals.length();i++){
            JSONObject r=rentals.optJSONObject(i);
            if(r==null||!r.optBoolean("settled"))continue;
            LocalDate d=FinanceAnalytics.date(r.optString("settled_at"));
            if(d!=null&&!d.isAfter(today)&&(first==null||d.isBefore(first)))first=d;
        }
        for(int i=0;i<expenses.length();i++){
            JSONObject e=expenses.optJSONObject(i);
            LocalDate d=e==null?null:FinanceAnalytics.date(e.optString("date"));
            if(d!=null&&!d.isAfter(today)&&(first==null||d.isBefore(first)))first=d;
        }
        to=today;
        from=first==null?today.minusDays(29):first.isBefore(today.minusDays(3660))?today.minusDays(3660):first;
        grain=2;allHistory=true;render();
    }
    private void kpi(LinearLayout row,String title,long amount,int color){LinearLayout box=column();box.setPadding(dp(10),dp(10),dp(8),dp(10));box.setBackground(bg(0xfff4f6fa));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(4),dp(3),dp(4));row.addView(box,p);text(box,title,11,0xff64748b,true);TextView value=text(box,money(amount),18,color,true);value.setMaxLines(1);value.setAutoSizeTextTypeUniformWithConfiguration(11,18,1,TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);}
    private void render(){
        root.removeAllViews();FinanceAnalytics.Report report=FinanceAnalytics.calculate(rentals,expenses,from,to,grain);long span=ChronoUnit.DAYS.between(from,to)+1;
        FinanceAnalytics.Report previous=FinanceAnalytics.calculate(rentals,expenses,from.minusDays(span),from.minusDays(1),grain);
        LinearLayout controls=card();text(controls,"OKRES ANALIZY",11,0xff64748b,true);LinearLayout presets=row(controls);
        button(presets,"30 dni",false,()->{to=LocalDate.now();from=to.minusDays(29);grain=0;allHistory=false;render();});button(presets,"90 dni",false,()->{to=LocalDate.now();from=to.minusDays(89);grain=1;allHistory=false;render();});button(presets,"12 mies.",false,()->{to=LocalDate.now();from=to.withDayOfMonth(1).minusMonths(11);grain=2;allHistory=false;render();});button(presets,"Całość",allHistory,this::showAllHistory);
        LinearLayout dates=row(controls);button(dates,"Od: "+date(from),false,()->chooseDate(true));button(dates,"Do: "+date(to),false,()->chooseDate(false));
        LinearLayout navigation=row(controls);button(navigation,"‹ Poprzedni okres",false,()->shift(-1));button(navigation,"Następny okres ›",false,()->shift(1));
        LinearLayout hero=card();hero.setBackground(bg(0xff172b43));text(hero,"WYNIK PO ZAPISANYCH KOSZTACH",11,0xffb8c7da,true);text(hero,money(report.result()),32,report.result()<0?0xfffda4af:0xfffacc15,true);
        long diff=report.result()-previous.result();
        if(allHistory)text(hero,"Cała zapisana historia • "+date(from)+" – "+date(to),13,Color.WHITE,false);
        else{ text(hero,(diff>=0?"+":"")+money(diff)+" względem poprzedniego okresu",13,Color.WHITE,false);text(hero,date(previous.start)+" – "+date(previous.end),11,0xffb8c7da,false); }
        text(hero,"Przychód − straty − koszty bieżące − zakupy sprzętu",11,0xffb8c7da,false);
        LinearLayout summary=card();LinearLayout first=row(summary);kpi(first,"PRZYCHÓD",report.income,colors[0]);kpi(first,"ŁĄCZNE KOSZTY",report.costs(),colors[1]);
        LinearLayout second=row(summary);kpi(second,"ŚREDNIO ZA NAJEM",report.rentals==0?0:Math.round((double)report.income/report.rentals),0xff172b43);kpi(second,"WYNIK OPERACYJNY",report.income-report.loss-report.cost,colors[2]);
        text(summary,"Rozliczone najmy: "+report.rentals+"   •   Nierozliczone rezerwacje: "+report.pending,13,0xff475569,false);
        if(!allHistory)text(summary,previous.income>0?String.format(PL,"Przychód: %+.1f%% względem poprzedniego okresu",100.0*(report.income-previous.income)/previous.income):"Zmiana % przychodu: brak dodatniej wartości porównawczej",12,0xff64748b,false);
        // One compact disclosure, not another tab or an additional chart.
        LinearLayout addons=card();
        text(addons,"Dodatki do najmu",18,0xff172b43,true);
        text(addons,money(report.addonTotal()),24,0xff047857,true);
        text(addons,"Proszek, papier, zużycie tarczy i pozostałe dopłaty. To część przychodu powyżej, przed rabatami — nie doliczamy jej drugi raz.",12,0xff64748b,false);
        LinearLayout addonDetails=column();addonDetails.setVisibility(View.GONE);
        addons.addView(addonDetails,new LinearLayout.LayoutParams(-1,-2));
        text(addonDetails,"Proszek / chemia piorąca: "+money(report.addonPowder),13,0xff334155,false);
        text(addonDetails,"Papier ścierny: "+money(report.addonPaper),13,0xff334155,false);
        text(addonDetails,"Zużycie tarczy: "+money(report.addonDisc),13,0xff334155,false);
        text(addonDetails,"Pozostałe dodatki: "+money(report.addonOther),13,0xff334155,false);
        if(report.addonUnclassified>0)text(addonDetails,"Starsze dopłaty bez podziału: "+money(report.addonUnclassified),13,0xff925400,false);
        if(report.addonsUnknown>0)text(addonDetails,"Przy "+report.addonsUnknown+" rozliczeniach brak zapisanego podziału dodatków. Nie da się odtworzyć ich kwot z samego przychodu.",12,0xff925400,false);
        Button addonToggle=new Button(context);addonToggle.setText("Pokaż szczegóły dodatków");
        addonToggle.setAllCaps(false);addonToggle.setTextSize(13);addonToggle.setTextColor(0xff172b43);
        addonToggle.setBackground(bg(0xffeef2f7));addonToggle.setMinHeight(dp(45));
        LinearLayout.LayoutParams addonToggleParams=new LinearLayout.LayoutParams(-1,dp(45));
        addonToggleParams.topMargin=dp(8);addons.addView(addonToggle,addonToggleParams);
        addonToggle.setOnClickListener(v->{boolean open=addonDetails.getVisibility()!=View.VISIBLE;
            addonDetails.setVisibility(open?View.VISIBLE:View.GONE);
            addonToggle.setText(open?"Ukryj szczegóły dodatków":"Pokaż szczegóły dodatków");
        });
        LinearLayout chartCard=card();text(chartCard,"Jak zmieniają się wyniki",19,0xff172b43,true);LinearLayout grains=row(chartCard);String[] names={"Dni","Tygodnie","Miesiące"};for(int i=0;i<3;i++){final int g=i;button(grains,names[i],grain==i,()->{grain=g;render();});}
        LinearLayout legend=row(chartCard);String[] series={"Przychód","Koszty","Wynik"};for(int i=0;i<3;i++){final int n=i;Button b=button(legend,series[i],visible[i],()->{int active=0;for(boolean show:visible)if(show)active++;if(visible[n]&&active==1)return;visible[n]=!visible[n];render();});b.setTextColor(visible[i]?Color.WHITE:colors[i]);if(visible[i])b.setBackground(bg(colors[i]));}
        TextView selected=text(chartCard,"Dotknij punktu wykresu, aby zobaczyć kwoty.",12,0xff64748b,false);
        HorizontalScrollView scroll=new HorizontalScrollView(context);scroll.setHorizontalScrollBarEnabled(true);scroll.setFillViewport(true);LineChart chart=new LineChart(report,selected);int width=Math.max(dp(280),Math.min(dp(12000),dp(64+report.buckets.size()*42)));scroll.addView(chart,new android.widget.FrameLayout.LayoutParams(width,dp(255)));chartCard.addView(scroll,new LinearLayout.LayoutParams(-1,dp(255)));text(chartCard,"Przesuń wykres w bok • tygodnie od poniedziałku",11,0xff64748b,false);
        if(report.rentals==0&&report.costs()==0)text(chartCard,"Brak zapisanych rozliczeń i kosztów w tym okresie. Wykres nie uwzględnia nieuzupełnionych danych.",13,0xff64748b,false);
        LinearLayout costs=card();text(costs,"Z czego składają się koszty",19,0xff172b43,true);costRow(costs,"Straty przy rozliczeniu najmu",report.loss,report.costs(),0xffef4444);costRow(costs,"Koszty bieżące",report.cost,report.costs(),0xfff59e0b);costRow(costs,"Zakupy sprzętu / inwestycje",report.investment,report.costs(),0xff8b5cf6);
        LinearLayout ranking=card();text(ranking,"Który sprzęt zarabia najwięcej?",19,0xff172b43,true);
        text(ranking,"Ranking według wyniku po przypisanych kosztach",12,0xff64748b,false);
        EquipmentAnalytics.Snapshot models=EquipmentAnalytics.calculate(rentals,expenses,from,to);
        List<EquipmentAnalytics.Model> sorted=models.sorted(1);
        if(sorted.isEmpty())text(ranking,"Ranking pojawi się po rozliczeniu najmu lub przypisaniu kosztu do modelu.",13,0xff64748b,false);
        for(int i=0;i<Math.min(5,sorted.size());i++){
            EquipmentAnalytics.Model item=sorted.get(i);
            LinearLayout line=row(ranking);line.setPadding(0,dp(6),0,dp(4));
            TextView rank=text(line,String.valueOf(i+1)+".",14,i==0?0xffb28300:0xff94a3b8,true);
            rank.setWidth(dp(27));
            LinearLayout detail=column();line.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
            text(detail,item.name,14,0xff172b43,true);
            text(detail,item.rentals+" wynajmów   •   przychód: "+money(item.income),12,0xff64748b,false);
            TextView earned=text(line,money(item.result()),14,item.result()<0?0xffb42318:0xff047857,true);
            earned.setGravity(android.view.Gravity.END);
        }
        if(models.unassignedExpenses>0)text(ranking,"Uwaga: koszty ogólne bez przypisanego sprzętu nie są wliczone do wyniku modelu.",11,0xffb45309,false);
        LinearLayout rankingButtons=row(ranking);button(rankingButtons,"Wszystkie modele i szczegóły  ›",true,equipmentAction);
        LinearLayout table=card();Button show=new Button(context);show.setText("Rozwiń zestawienie okresów ("+report.buckets.size()+")");show.setAllCaps(false);table.addView(show);LinearLayout rows=column();rows.setVisibility(View.GONE);table.addView(rows);show.setOnClickListener(v->{boolean opened=rows.getVisibility()==View.VISIBLE;rows.setVisibility(opened?View.GONE:View.VISIBLE);show.setText(opened?"Rozwiń zestawienie okresów":"Zwiń zestawienie okresów");if(!opened&&rows.getChildCount()==0)for(FinanceAnalytics.Bucket b:report.buckets){text(rows,date(b.start)+(b.start.equals(b.end)?"":" – "+date(b.end)),13,0xff172b43,true);text(rows,"Przychód "+money(b.income)+"\nKoszty "+money(b.costs())+"   •   Wynik "+money(b.result()),12,0xff64748b,false);}});
        LinearLayout history=card();Button historyToggle=new Button(context);historyToggle.setAllCaps(false);historyToggle.setText("Historia rozliczeń ("+report.records.size()+")");history.addView(historyToggle);LinearLayout historyRows=column();historyRows.setVisibility(View.GONE);history.addView(historyRows);
        historyToggle.setOnClickListener(v->{boolean open=historyRows.getVisibility()==View.VISIBLE;historyRows.setVisibility(open?View.GONE:View.VISIBLE);if(!open&&historyRows.getChildCount()==0){List<JSONObject> records=new ArrayList<>(report.records);records.sort((a,b)->b.optString("settled_at").compareTo(a.optString("settled_at")));for(JSONObject o:records){text(historyRows,o.optString("equipment","Nieprzypisany sprzęt"),14,0xff172b43,true);text(historyRows,"Rozliczono: "+o.optString("settled_at"),12,0xff64748b,false);text(historyRows,String.format(PL,"Przychód: %,.2f zł • Strata: %,.2f zł",o.optDouble("income"),o.optDouble("loss")),13,0xff334155,false);if(!o.optString("loss_note").isEmpty())text(historyRows,o.optString("loss_note"),12,0xff64748b,false);}}});
        LinearLayout methods=card();text(methods,"Jak liczymy",15,0xff172b43,true);text(methods,"Przychód i straty: data rozliczenia najmu. Koszty i zakupy: data kosztu. Wynik operacyjny nie odejmuje zakupów sprzętu. Wynik końcowy je odejmuje. To zestawienie zapisów w aplikacji, nie zysk podatkowy ani saldo konta. Nie wpisuj tej samej straty ponownie jako kosztu.",12,0xff64748b,false);
        text(methods,"Źródło: dane zapisane w telefonie. Niewpisane kwoty nie są szacowane. Nierozliczone rezerwacje liczone według daty rozpoczęcia.",12,0xff64748b,false);
        if(report.undated>0||report.invalid>0)text(methods,"Pominięto: "+report.undated+" wpisów bez prawidłowej daty (w całej historii) oraz "+report.invalid+" wpisów z nieprawidłową lub brakującą kwotą w tym okresie.",12,0xffb45309,true);
        LinearLayout links=row(root);button(links,"Modele sprzętu",false,equipmentAction);button(links,"Dodaj / edytuj koszty",false,costAction);
        if(root.isAttachedToWindow()&&android.animation.ValueAnimator.areAnimatorsEnabled()){root.animate().cancel();root.setAlpha(.82f);root.animate().alpha(1f).setDuration(190).start();}
    }
    private void costRow(LinearLayout c,String label,long amount,long total,int color){text(c,label+" • "+money(amount),13,0xff475569,false);ProgressBar p=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);p.setMax(1000);p.setProgress(total==0?0:(int)(1000.0*amount/total));p.setProgressTintList(android.content.res.ColorStateList.valueOf(color));c.addView(p,new LinearLayout.LayoutParams(-1,dp(8)));}
    private class LineChart extends View {
        final FinanceAnalytics.Report report;final TextView selection;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int selected=-1;float downX,downY;
        private float reveal=1f;private android.animation.ValueAnimator drawAnimation;
        @Override protected void onAttachedToWindow(){
            super.onAttachedToWindow();
            if(android.animation.ValueAnimator.areAnimatorsEnabled()){
                // Keep the first series segment visible immediately for clarity and tests.
                reveal=.35f;
                drawAnimation=android.animation.ValueAnimator.ofFloat(.35f,1f);
                drawAnimation.setDuration(520);
                drawAnimation.setInterpolator(new android.view.animation.DecelerateInterpolator());
                drawAnimation.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});
                drawAnimation.start();
            }
        }
        @Override protected void onDetachedFromWindow(){
            if(drawAnimation!=null){drawAnimation.cancel();drawAnimation=null;}
            super.onDetachedFromWindow();
        }
        LineChart(FinanceAnalytics.Report r,TextView t){super(context);report=r;selection=t;setContentDescription("Wykres przychodów, kosztów i wyniku. Kwoty dostępne również w zestawieniu okresów.");}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            // HorizontalScrollView measures its child with UNSPECIFIED width.
            // A plain View otherwise reports zero, even when LayoutParams has a width.
            int requested=getLayoutParams()!=null?getLayoutParams().width:0;
            int desiredWidth=Math.max(dp(280),requested);
            setMeasuredDimension(resolveSize(desiredWidth,widthSpec),resolveSize(dp(255),heightSpec));
        }
        private long value(FinanceAnalytics.Bucket b,int s){return s==0?b.income:s==1?b.costs():b.result();}
        private float x(int i){return dp(55)+(getWidth()-dp(75))*(report.buckets.size()==1?.5f:(float)i/(report.buckets.size()-1));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);double min=0,max=0;for(FinanceAnalytics.Bucket b:report.buckets)for(int s=0;s<3;s++)if(visible[s]){min=Math.min(min,value(b,s));max=Math.max(max,value(b,s));}if(max==min)max=min+100;double pad=(max-min)*.12;max+=pad;if(min<0)min-=pad;float top=dp(16),bottom=getHeight()-dp(35);
            p.setTextSize(dp(10));for(int i=0;i<=4;i++){float y=top+(bottom-top)*i/4;p.setColor(0xffe2e8f0);p.setStrokeWidth(dp(1));c.drawLine(dp(52),y,getWidth()-dp(14),y,p);p.setColor(0xff64748b);double val=max-(max-min)*i/4;c.drawText(String.format(PL,"%.0f",val/100),dp(2),y-dp(3),p);}
            p.setColor(0xff64748b);c.drawText("zł",dp(2),dp(10),p);
            int step=Math.max(1,report.buckets.size()/Math.max(1,getWidth()/dp(65)));for(int i=0;i<report.buckets.size();i+=step){String label=grain==2?report.buckets.get(i).start.format(DateTimeFormatter.ofPattern("MM.yy")):shortDate(report.buckets.get(i).start);c.drawText(label,x(i)-dp(14),getHeight()-dp(10),p);}
            c.save();c.clipRect(0,0,dp(55)+(getWidth()-dp(60))*reveal,getHeight());
            for(int s=0;s<3;s++)if(visible[s]){Path path=new Path();for(int i=0;i<report.buckets.size();i++){float y=(float)(bottom-(value(report.buckets.get(i),s)-min)/(max-min)*(bottom-top));if(i==0)path.moveTo(x(i),y);else path.lineTo(x(i),y);}p.setColor(colors[s]);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));c.drawPath(path,p);p.setStyle(Paint.Style.FILL);for(int i=0;i<report.buckets.size();i++){float y=(float)(bottom-(value(report.buckets.get(i),s)-min)/(max-min)*(bottom-top));c.drawCircle(x(i),y,dp(i==selected?5:2.5f),p);}}
            c.restore();
            if(selected>=0){p.setColor(0xff94a3b8);p.setStrokeWidth(dp(1));c.drawLine(x(selected),top,x(selected),bottom,p);}
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}if(e.getAction()==MotionEvent.ACTION_UP){if(Math.abs(e.getX()-downX)<dp(12)&&Math.abs(e.getY()-downY)<dp(12)){int i=report.buckets.size()==1?0:Math.round((e.getX()-dp(55))/(getWidth()-dp(75))*(report.buckets.size()-1));selected=Math.max(0,Math.min(report.buckets.size()-1,i));FinanceAnalytics.Bucket b=report.buckets.get(selected);selection.setText(date(b.start)+(b.start.equals(b.end)?"":" – "+date(b.end))+"\nPrzychód: "+money(b.income)+"\nKoszty: "+money(b.costs())+"   •   Wynik: "+money(b.result()));invalidate();performClick();}return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
