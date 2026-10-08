package dev.touchxbox.pad;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Protocol-specific palettes. Checked controls are kept; unchecked controls are removed. */
final class ControllerPicker extends ViewGroup {
    private static final String[] KEYS={"LT","LB","L","D","VIEW","XBOX","MENU","R","Y","X","B","A","RT","RB"};
    private static final int[][] XBOX={{100,8,72,30},{181,8,72,30},{170,60,88,80},{296,132,74,68},{394,91,66,32},{466,44,60,40},{534,91,66,32},{599,132,88,80},{760,53,42,40},{712,99,42,40},{808,99,42,40},{760,145,42,40},{788,8,72,30},{707,8,72,30}};
    private static final int[][] NS={{106,8,72,30},{188,8,72,30},{181,59,88,80},{300,133,72,68},{390,73,46,35},{456,154,66,33},{542,73,46,35},{591,133,88,80},{754,53,42,40},{706,99,42,40},{802,99,42,40},{754,145,42,40},{782,8,72,30},{700,8,72,30}};
    private static final int[][] PS={{106,8,72,30},{188,8,72,30},{316,130,88,80},{182,69,74,68},{337,58,84,32},{456,132,58,36},{488,58,97,32},{560,130,88,80},{753,55,42,40},{705,101,42,40},{801,101,42,40},{753,147,42,40},{780,8,72,30},{698,8,72,30}};
    private static final int[][] FPV={{170,8,88,35},{700,8,88,35},{270,8,88,35},{600,8,88,35},{206,62,160,145},{594,62,160,145}};
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private final int mode,accent,fpvMode;private final int[][] boxes;private final Paint paint=new Paint(3);
    ControllerPicker(Context c,LayoutProfile profile,Runnable changed){
        super(c);setWillNotDraw(false);mode=profile.protocol;fpvMode=FpvMode.valid(profile.fpvMode);boxes=mode==1?NS:mode==2?PS:mode==3?FPV:XBOX;accent=mode==1?0xFFEE7280:mode==2?0xFF72B6FF:mode==3?0xFFF0B760:0xFF8DD894;
        for(String physical:mode==3?new String[]{"CH5","CH6","CH7","CH8","L","R"}:KEYS){String key=profile.displayKey(LayoutProfile.spec(physical));String title=ControllerProtocol.label(mode,key);
            if(mode==3&&(physical.equals("L")||physical.equals("R"))){boolean right=physical.equals("R");String channels=fpvMode==FpvMode.JAPANESE?(right?"CH1 / CH3":"CH4 / CH2"):right==FpvMode.throttleRight(fpvMode)?"CH3 / CH4":"CH1 / CH2";title=FpvMode.label(fpvMode,right)+"\n"+channels;}
            ToggleButton button=new ToggleButton(c);button.setTextOn(title);button.setTextOff(title);button.setText(title);button.setAllCaps(false);button.setGravity(Gravity.CENTER);button.setPadding(2,0,2,0);button.setTextColor(0xFFEAF0F8);button.setAutoSizeTextTypeUniformWithConfiguration(8,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);button.setContentDescription("手柄 "+title);
            boolean round=mode!=3&&(physical.matches("[ABXY]")||physical.equals("L")||physical.equals("R"));StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_checked},tile(true,round));background.addState(new int[]{},tile(false,round));button.setBackground(background);
            boolean exists=!profile.hiddenButtons.contains(key);button.setChecked(exists);if(exists)selected.add(key);button.setOnCheckedChangeListener((v,on)->{if(on)selected.add(key);else selected.remove(key);changed.run();});addView(button);
        }
    }
    Set<String> selection(){return new LinkedHashSet<>(selected);}
    private GradientDrawable tile(boolean on,boolean round){GradientDrawable d=new GradientDrawable();d.setColor(on?(mode==1?0xFF813640:mode==2?0xFF285882:mode==3?0xFF735126:0xFF315B39):0xFF202938);if(round)d.setShape(GradientDrawable.OVAL);else d.setCornerRadius(12);d.setStroke(on?3:1,on?accent:0xFF657083);return d;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/960f,getHeight()/220f);paint.setColor(0xFF131B28);paint.setStyle(Paint.Style.FILL);
        if(mode==3)canvas.drawRoundRect(25,0,935,219,20,20,paint);
        else {Path shell=new Path();shell.moveTo(144,38);shell.cubicTo(90,60,104,210,155,216);shell.lineTo(358,211);shell.quadTo(480,180,603,211);shell.lineTo(810,216);shell.cubicTo(865,210,870,60,820,38);shell.close();canvas.drawPath(shell,paint);paint.setColor(accent&0x00ffffff|0x70000000);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawPath(shell,paint);}
        if(mode==3){paint.setColor(accent&0x00ffffff|0x80000000);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawRoundRect(25,0,935,219,20,20,paint);paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif-medium",0));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(16);paint.setColor(accent);canvas.drawText("MODE "+fpvMode,480,34,paint);}
        canvas.restore();
    }
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=resolveSize(Math.round(w*.18f),hs);setMeasuredDimension(w,h);for(int i=0;i<boxes.length;i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(boxes[i][2]*w/960,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(boxes[i][3]*h/220,MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<boxes.length;i++){int x=boxes[i][0]*getWidth()/960,y=boxes[i][1]*getHeight()/220;View key=getChildAt(i);key.layout(x,y,x+key.getMeasuredWidth(),y+key.getMeasuredHeight());}}
}
