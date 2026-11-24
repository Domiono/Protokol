package pl.rentmor.protokol;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import java.util.Arrays;

final class NoteHighlights {
    static final int[] COLORS={0xffffe680,0xffa9e9b0,0xffaedcff,0xffffbada};
    static final String[] LABELS={"Żółty","Zielony","Niebieski","Różowy"};

    static String encode(Spanned text){
        char[] mask=new char[text.length()];
        Arrays.fill(mask,'0');
        boolean colored=false;
        for(BackgroundColorSpan span:text.getSpans(0,text.length(),BackgroundColorSpan.class)){
            for(int i=0;i<COLORS.length;i++){
                if(span.getBackgroundColor()==COLORS[i]){
                    Arrays.fill(mask,text.getSpanStart(span),text.getSpanEnd(span),(char)('1'+i));
                    colored=true;
                    break;
                }
            }
        }
        return colored?new String(mask):"";
    }

    static SpannableString display(String text,String mask){
        SpannableString result=new SpannableString(text);
        if(mask.length()!=text.length())return result;
        for(int start=0;start<mask.length();){
            int end=start+1;
            while(end<mask.length() && mask.charAt(start)==mask.charAt(end))end++;
            int index=mask.charAt(start)-'1';
            if(index>=0 && index<COLORS.length)add(result,start,end,COLORS[index]);
            start=end;
        }
        return result;
    }

    static void apply(Spannable text,int start,int end,int color){
        for(BackgroundColorSpan span:text.getSpans(start,end,BackgroundColorSpan.class)){
            int from=text.getSpanStart(span),to=text.getSpanEnd(span);
            text.removeSpan(span);
            if(from<start)add(text,from,start,span.getBackgroundColor());
            if(to>end)add(text,end,to,span.getBackgroundColor());
        }
        if(color>0)add(text,start,end,COLORS[color-1]);
    }

    private static void add(Spannable text,int start,int end,int color){
        text.setSpan(new BackgroundColorSpan(color),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
