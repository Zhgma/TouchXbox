package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;

/** Isolated QA package only. Exercises the actual editor checkbox and stored template. */
public final class FpvXboxUiTest extends Instrumentation {
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @Override public void onCreate(Bundle args){start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();check(c.getPackageName().equals("dev.touchxbox.pad.qa058"),"Refuse user package");
        Class.forName("LayoutRoundTripTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        Class.forName("FpvAuxMappingTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        Class.forName("FpvXboxTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        LayoutStore store=new LayoutStore(c);LayoutProfile p=new LayoutProfile();p.protocol=ControllerProtocol.FPV;p.name=store.uniqueName("FPV Xbox 验证");p.fpvMode=FpvMode.JAPANESE;
        check(store.save(p)==null,"Save isolated fixture");
        Activity editor=startActivitySync(new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{
            CheckBox option=(CheckBox)find(editor.getWindow().getDecorView(),"模拟 Xbox 协议");check(option!=null&&!option.isChecked(),"FPV option visible and off by default");option.performClick();
            check(((LayoutProfile)field(editor,"draft")).fpvXbox,"Checkbox changes draft");
            check(find(editor.getWindow().getDecorView(),"FPV · Xbox 输出 ▾")!=null,"Header shows output");
            check(find(editor.getWindow().getDecorView(),"CH5 · LT 三档")!=null,"Channel settings appear below Xbox switch");
            AlertDialog xbox=(AlertDialog)invoke(editor,"chooseFpvKey",new Class<?>[]{int.class,int.class,boolean.class,Runnable.class},4,0,false,(Runnable)()->{});xbox.getListView().performItemClick(null,0,0);xbox.dismiss();
            AlertDialog keyboard=(AlertDialog)invoke(editor,"chooseFpvKey",new Class<?>[]{int.class,int.class,boolean.class,Runnable.class},4,2,true,(Runnable)()->{});int space=new java.util.ArrayList<>(KeyboardKeys.KEYS.keySet()).indexOf(44);keyboard.getListView().performItemClick(null,space,space);keyboard.dismiss();
            LayoutProfile mapped=(LayoutProfile)field(editor,"draft");check(mapped.fpvXboxAux.get(4)[0]==PadState.A+1&&mapped.fpvXboxAux.get(4)[2]==FpvAuxMapping.KEYBOARD+44&&mapped.needsKeyboard(),"Actual pickers bind Xbox A and keyboard Space");
            check((Boolean)invoke(editor,"save",new Class<?>[]{boolean.class,boolean.class},false,false),"Editor saves");
            invoke(editor,"select",new Class<?>[]{String.class},"R");
        });waitForIdleSync();render(c,"fpv-xbox-editor.png",editor.getWindow().getDecorView());
        final AlertDialog[] mapping={null};runOnMainSync(()->mapping[0]=(AlertDialog)invoke(editor,"editFpvAux",new Class<?>[]{int.class},4));waitForIdleSync();
        check(find(mapping[0].getWindow().getDecorView(),"0% · Xbox A")!=null&&find(mapping[0].getWindow().getDecorView(),"100% · 键盘 Space")!=null,"Mapping dialog shows selected output names");render(c,"fpv-xbox-ch5-mapping.png",mapping[0].getWindow().getDecorView());runOnMainSync(()->mapping[0].dismiss());
        LayoutProfile saved=new LayoutStore(c).active();check(saved.protocol==3&&saved.fpvXbox&&saved.fpvMode==1&&saved.outputProtocol()==0,"Saved layout/mode/output independent");check(saved.fpvXboxAux.get(4)[2]==FpvAuxMapping.KEYBOARD+44,"Custom bindings persisted");
        runOnMainSync(editor::finish);
        Activity reopened=startActivitySync(new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{
            CheckBox option=(CheckBox)find(reopened.getWindow().getDecorView(),"模拟 Xbox 协议");check(option.isChecked(),"Checkbox restored after reopening");option.performClick();
            check((Boolean)invoke(reopened,"save",new Class<?>[]{boolean.class,boolean.class},false,false),"Save native output");
            AlertDialog restore=(AlertDialog)invoke(reopened,"editFpvAux",new Class<?>[]{int.class},4);restore.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        });waitForIdleSync();
        runOnMainSync(()->{check(!((LayoutProfile)field(reopened,"draft")).fpvXboxAux.containsKey(4),"Restore default channel output");
            LayoutProfile draft=(LayoutProfile)field(reopened,"draft");draft.protocol=ControllerProtocol.XBOX;invoke(reopened,"select",new Class<?>[]{String.class},"L");check(find(reopened.getWindow().getDecorView(),"模拟 Xbox 协议")==null,"Option only appears in FPV layout");
        });
        check(new LayoutStore(c).active().outputProtocol()==3,"Can restore native output");runOnMainSync(reopened::finish);
        result.putString("stream","PASS: v9 copy/import and legacy defaults; real editor toggle/save/reopen/off; FPV modes and geometry preserved; non-FPV option hidden; Xbox/keyboard pickers and restoring default CH output\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(0,result);}}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static View find(View v,String text){if(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void render(Context c,String name,View v){runOnMainSync(()->{int w=v.getWidth(),h=v.getHeight();check(w>0&&h>0,"Editor layout exists");v.forceLayout();v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}bitmap.recycle();});}
}
