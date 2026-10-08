package dev.touchxbox.pad;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A visible QWERTY keyboard with native accessible, checkable keycaps. */
final class KeyboardPicker extends ViewGroup {
    static final class Cap {final int code,row;final float x,w;Cap(int c,int r,float x,float w){code=c;row=r;this.x=x;this.w=w;}}
    static final ArrayList<Cap> CAPS=new ArrayList<>();
    static {
        row(0,new int[]{41,58,59,60,61,62,63,64,65,66,67,68,69},new float[]{1.5f,1,1,1,1,1,1,1,1,1,1,1,1});
        row(1,new int[]{53,30,31,32,33,34,35,36,37,38,39,45,46,42},new float[]{1,1,1,1,1,1,1,1,1,1,1,1,1,2});
        row(2,new int[]{43,20,26,8,21,23,28,24,12,18,19,47,48,49},new float[]{1.5f,1,1,1,1,1,1,1,1,1,1,1,1,1.5f});
        row(3,new int[]{57,4,22,7,9,10,11,13,14,15,51,52,40},new float[]{1.8f,1,1,1,1,1,1,1,1,1,1,1,2.2f});
        row(4,new int[]{225,29,27,6,25,5,17,16,54,55,56,229},new float[]{2.3f,1,1,1,1,1,1,1,1,1,1,2.7f});
        row(5,new int[]{224,227,226,44,230,231,228},new float[]{1.5f,1.5f,1.5f,6,1.5f,1.5f,1.5f});
        for(int i=0;i<3;i++){CAPS.add(new Cap(70+i,0,15.5f+i,1));CAPS.add(new Cap(73+i,1,15.5f+i,1));CAPS.add(new Cap(76+i,2,15.5f+i,1));}
        CAPS.add(new Cap(82,4,16.5f,1));CAPS.add(new Cap(80,5,15.5f,1));CAPS.add(new Cap(81,5,16.5f,1));CAPS.add(new Cap(79,5,17.5f,1));
    }
    private static void row(int row,int[] codes,float[] widths){float x=0;for(int i=0;i<codes.length;i++){CAPS.add(new Cap(codes[i],row,x,widths[i]));x+=widths[i];}}
    private final LinkedHashSet<Integer> selected=new LinkedHashSet<>();private final int limit;private final Runnable changed;
    KeyboardPicker(Context c,int remaining,Runnable update){super(c);limit=remaining;changed=update;setPadding(0,0,0,0);
        for(Cap cap:CAPS){ToggleButton key=new ToggleButton(c);String name=caption(cap.code);key.setTextOn(name);key.setTextOff(name);key.setText(name);key.setAllCaps(false);key.setPadding(1,0,1,0);key.setTextColor(0xFFEAF0F8);key.setContentDescription("键盘 "+KeyboardKeys.name(cap.code));key.setAutoSizeTextTypeUniformWithConfiguration(8,14,1,TypedValue.COMPLEX_UNIT_SP);
            StateListDrawable colors=new StateListDrawable();colors.addState(new int[]{android.R.attr.state_checked},tile(0xFF2867A5,0xFF87C6FF));colors.addState(new int[]{},tile(0xFF232F3F,0xFF526276));key.setBackground(colors);key.setOnCheckedChangeListener((v,on)->{if(on&&selected.size()>=limit&&!selected.contains(cap.code)){key.setChecked(false);Toast.makeText(c,"最多再添加 "+limit+" 个按键",0).show();return;}if(on)selected.add(cap.code);else selected.remove(cap.code);changed.run();});addView(key);
        }
    }
    private GradientDrawable tile(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(5*getResources().getDisplayMetrics().density);d.setStroke(1,stroke);return d;}
    static String caption(int code){switch(code){case 42:return "Backspace";case 57:return "Caps";case 70:return "PrtSc";case 71:return "ScrLk";case 75:return "PgUp";case 78:return "PgDn";case 79:return "→";case 80:return "←";case 81:return "↓";case 82:return "↑";case 224:case 228:return "Ctrl";case 225:case 229:return "Shift";case 226:case 230:return "Alt";case 227:case 231:return "Meta";default:return KeyboardKeys.name(code);}}
    Set<Integer> selection(){return new LinkedHashSet<>(selected);}
    static Cap cap(int code){for(Cap cap:CAPS)if(cap.code==code)return cap;throw new IllegalArgumentException("Unknown keycap");}
    @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=resolveSize(Math.round(w*.29f),heightSpec);setMeasuredDimension(w,h);float unit=w/18.5f,row=h/6f,gap=3*getResources().getDisplayMetrics().density;for(int i=0;i<CAPS.size();i++){Cap cap=CAPS.get(i);getChildAt(i).measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(cap.w*unit-gap)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(row-gap)),MeasureSpec.EXACTLY));}}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){float unit=getWidth()/18.5f,row=getHeight()/6f,gap=1.5f*getResources().getDisplayMetrics().density;for(int i=0;i<CAPS.size();i++){Cap cap=CAPS.get(i);View key=getChildAt(i);int x=Math.round(cap.x*unit+gap),y=Math.round(cap.row*row+gap);key.layout(x,y,x+key.getMeasuredWidth(),y+key.getMeasuredHeight());}}
}
