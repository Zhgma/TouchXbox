package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Isolated, launcher-free instrumentation. Never run against the user's package. */
public final class UiReleaseTest extends Instrumentation {
    private final StringBuilder log=new StringBuilder();
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private void pass(String text){log.append("PASS: ").append(text).append('\n');}
    @Override public void onCreate(Bundle args){start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();check(c.getPackageName().equals("dev.touchxbox.pad.qa057"),"Refuse production package");
        check(!ShizukuInput.selected(c)&&!ShizukuInput.SHOW_ENTRY,"USB is default and Shizuku entry is hidden");
        LayoutStore store=new LayoutStore(c);check(store.all().isEmpty(),"Fresh library remains empty");
        for(String name:new String[]{"LayoutRoundTripTest","FpvStickTest","FpvModeTest"})Class.forName(name).getMethod("main",String[].class).invoke(null,(Object)new String[0]);
        pass("Android runtime: v8 template export/import, old defaults and all three FPV modes");
        List<LayoutProfile> saved=new ArrayList<>();Map<String,String> snapshots=new HashMap<>();
        String[] names={"Xbox 模板","NS 模板","PS 模板","FPV 模板"};
        for(int protocol=0;protocol<4;protocol++){LayoutProfile p=new LayoutProfile();p.protocol=protocol;p.name=names[protocol];check(store.save(p)==null,"Save fixture");saved.add(p);snapshots.put(p.id,LayoutStore.encode(p));}
        check(store.select(saved.get(1).id),"Select NS");
        check(store.move(saved.get(3).id,saved.get(0).id),"Move FPV to first");
        List<LayoutProfile> ordered=new LayoutStore(c).all();check(ordered.get(0).id.equals(saved.get(3).id)&&ordered.get(3).id.equals(saved.get(2).id),"Reload keeps order");
        check(new LayoutStore(c).active().id.equals(saved.get(1).id),"Reordering preserves selected template");
        for(LayoutProfile p:ordered)check(snapshots.get(p.id).equals(LayoutStore.encode(p)),"Reorder preserves complete configuration");
        check(!store.move("missing",saved.get(0).id)&&store.all().get(0).id.equals(saved.get(3).id),"Stale drag makes no changes");
        pass("Saved drag order survives store reload without changing selection or template fields");
        check(store.quickLaunchId().isEmpty(),"Fresh install has no quick launch");
        check(LauncherIdentity.setMode(c,LauncherIdentity.ORIGINAL),"Pin original appearance");
        check(store.setQuickLaunch(saved.get(1).id)&&LauncherIdentity.current(c)==ControllerProtocol.SWITCH_PRO,"Quick NS overrides pinned original icon");
        check(LauncherIdentity.mode(c)==LauncherIdentity.ORIGINAL,"Original appearance preference retained");
        check(store.setQuickLaunch(saved.get(2).id)&&new LayoutStore(c).quickLaunch().id.equals(saved.get(2).id),"Selecting PS replaces the only quick target");
        check(!store.setQuickLaunch("missing")&&store.quickLaunchId().equals(saved.get(2).id),"Invalid target cannot replace selection");
        LayoutProfile edited=store.quickLaunch();edited.protocol=ControllerProtocol.FPV;check(store.save(edited)==null&&LauncherIdentity.current(c)==ControllerProtocol.FPV,"Editing target protocol updates desktop appearance");
        edited.protocol=ControllerProtocol.DS4;check(store.save(edited)==null,"Restore fixture protocol");
        LayoutProfile imported=TemplateCode.decode(TemplateCode.encode(edited));imported.name="导入副本";check(store.save(imported)==null&&store.quickLaunchId().equals(edited.id),"Copy/import does not transfer quick-launch selection");
        check(store.setQuickLaunch(imported.id)&&store.delete(imported.id)&&store.quickLaunchId().isEmpty()&&LauncherIdentity.current(c)==LauncherIdentity.ORIGINAL,"Deleting target clears quick launch and restores pinned appearance");
        check(store.setQuickLaunch(saved.get(1).id)&&store.setQuickLaunch("")&&LauncherIdentity.current(c)==LauncherIdentity.ORIGINAL,"Cancel restores pinned appearance");
        check(LauncherIdentity.setMode(c,LauncherIdentity.AUTO)&&store.select(saved.get(1).id),"Restore automatic mode and active fixture");
        pass("Quick target is unique, persistent, validated, not exported; icon follows edits and restores on cancel/deletion");
        Activity main=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();render(c,"home.png",main.getWindow().getDecorView());
        for(int protocol=0;protocol<4;protocol++)check(findDescription(main.getWindow().getDecorView(),ControllerProtocol.NAMES[protocol]) instanceof ImageView,"Each card has its protocol icon");
        run(()->{
            AlertDialog menu=(AlertDialog)invoke(main,"manage",new Class<?>[]{LayoutProfile.class},saved.get(1));check(menu.getListView().getAdapter().getItem(3).equals("设为快捷启动"),"Menu offers quick launch");menu.getListView().performItemClick(null,3,3);menu.dismiss();
        });waitForIdleSync();check(store.quickLaunchId().equals(saved.get(1).id),"Quick menu action persists NS target");
        render(c,"home-quick.png",main.getWindow().getDecorView());check(findText(main.getWindow().getDecorView(),"快捷启动")!=null,"Quick target has visible badge");
        run(()->{AlertDialog menu=(AlertDialog)invoke(main,"manage",new Class<?>[]{LayoutProfile.class},saved.get(1));check(menu.getListView().getAdapter().getItem(3).equals("取消快捷启动"),"Menu can cancel active quick target");menu.dismiss();});
        int enabled=0;for(int appearance=0;appearance<5;appearance++)if(c.getPackageManager().getComponentEnabledSetting(LauncherIdentity.component(c,appearance))==PackageManager.COMPONENT_ENABLED_STATE_ENABLED)enabled++;
        check(enabled==1,"Exactly one alias enabled");
        run(()->{
            OverlayService service=new OverlayService();Method attach=ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);attach.setAccessible(true);attach.invoke(service,c);
            setField(service,"running",true);setField(service,"profile",store.quickLaunch());PadState state=(PadState)field(service,"state");state.configure(ControllerProtocol.FPV);state.stick(false,.4f,-.3f);state.button(PadState.A,true);state.keyboard("test",62,true);byte[] before=(byte[])invoke(state,"frame",new Class<?>[0]);
            for(int repeat=0;repeat<3;repeat++)service.onStartCommand(new Intent(c,OverlayService.class).setAction(OverlayService.QUICK_LAUNCH).putExtra(OverlayService.TEMPLATE_ID,store.quickLaunchId()),0,repeat);
            check(Arrays.equals(before,(byte[])invoke(state,"frame",new Class<?>[0])),"Repeated quick launch preserves all held inputs and throttle");check(field(service,"client")==null&&(Integer)field(service,"generation")==0,"Repeated quick launch does not create a connection or device");
        });
        pass("Native menu enables/cancels quick launch; one alias and one card badge; repeated service entry preserves held state");
        run(()->invoke(main,"openSettings",new Class<?>[0]));waitForIdleSync();Dialog settings=(Dialog)field(main,"settings");
        check(findText(settings.getWindow().getDecorView(),"电脑 USB 授权（默认）")!=null,"Settings offers USB authorization");
        check(findText(settings.getWindow().getDecorView(),"Shizuku")==null,"Settings hides Shizuku");run(settings::dismiss);
        final AlertDialog[] appearance={null};run(()->appearance[0]=(AlertDialog)invoke(main,"chooseAppearance",new Class<?>[0]));waitForIdleSync();render(c,"appearance.png",appearance[0].getWindow().getDecorView());run(appearance[0]::dismiss);
        Activity guide=startActivitySync(new Intent(c,AuthorizationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        check(findText(guide.getWindow().getDecorView(),OtaSource.AUTHORIZE_URL)!=null,"Guide has published CMD URL");
        for(String expected:new String[]{"1  开启手机开发者模式","2  打开 USB 调试","3  用 USB 连接电脑","4  在电脑下载并运行 CMD","5  等待成功，再启动模板","复制下载链接"})check(findText(guide.getWindow().getDecorView(),expected)!=null,"Guide contains "+expected);
        render(c,"authorization-top.png",guide.getWindow().getDecorView());ScrollView scroll=(ScrollView)findClass(guide.getWindow().getDecorView(),ScrollView.class);run(()->scroll.fullScroll(View.FOCUS_DOWN));waitForIdleSync();render(c,"authorization-bottom.png",guide.getWindow().getDecorView());run(guide::finish);
        pass("Native Android UI contains four protocol icons, USB guide/download/copy links and no Shizuku entry");
        check(store.select(saved.get(3).id),"Select FPV fixture");
        Activity editor=startActivitySync(new Intent(c,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();run(()->invoke(editor,"select",new Class<?>[]{String.class},"L"));render(c,"editor-default.png",editor.getWindow().getDecorView());
        LayoutProfile draft=(LayoutProfile)field(editor,"draft");check(draft.fpvMode==FpvMode.AMERICAN,"Editor defaults to American");
        for(int selection:new int[]{1,2}){
            Spinner spinner=(Spinner)findDescription(editor.getWindow().getDecorView(),"FPV 操控习惯");check(spinner!=null&&spinner.getCount()==3,"Three-mode selector lives on selected stick");
            run(()->{spinner.setSelection(selection);spinner.getOnItemSelectedListener().onItemSelected(spinner,null,selection,selection);});waitForIdleSync();
            check(draft.fpvMode==(selection==1?FpvMode.JAPANESE:FpvMode.CHINESE),"Editor selection changes mapping");
            check(draft.throttle(LayoutProfile.spec("R"))&&!draft.throttle(LayoutProfile.spec("L")),"Right-side throttle after switching");
        }
        render(c,"editor-chinese.png",editor.getWindow().getDecorView());
        run(()->check((Boolean)invoke(editor,"save",new Class<?>[]{boolean.class,boolean.class},false,false),"Editor save succeeds"));
        check(new LayoutStore(c).active().fpvMode==FpvMode.CHINESE,"Editor save persists Chinese mode");
        run(()->{ControllerPicker picker=new ControllerPicker(editor,draft,()->{});check(findText(picker,"横滚 / 俯仰\nCH1 / CH2")!=null&&findText(picker,"油门 / 偏航\nCH3 / CH4")!=null,"Add-controls palette follows selected mode");});
        pass("Editor switches Japanese/Chinese, keeps American default, saves selection and updates the controller palette");
        run(editor::finish);run(main::finish);waitForIdleSync();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);run(()->c.startActivity(new Intent(c,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Activity failure=waitForMonitorWithTimeout(monitor,6000);removeMonitor(monitor);check(failure!=null,"Permission failure routes back to software");waitForIdleSync();
        Dialog error=(Dialog)field(failure,"launchErrorDialog");check(error!=null&&findText(error.getWindow().getDecorView(),"悬浮窗权限")!=null,"Permission failure gives actionable reason");render(c,"quick-error.png",error.getWindow().getDecorView());run(failure::finish);
        check(store.setQuickLaunch(""),"Disable fixture quick launch");monitor=addMonitor(MainActivity.class.getName(),null,false);run(()->c.startActivity(new Intent(c,LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        Activity normal=waitForMonitorWithTimeout(monitor,6000);removeMonitor(monitor);check(normal!=null,"Without quick target desktop opens library");waitForIdleSync();check(field(normal,"launchErrorDialog")==null,"Normal launch has no quick error");run(normal::finish);
        pass("Desktop router opens the library normally; quick permission failure opens software with reason without granting permissions");
        check(c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName()),0).isEmpty(),"QA has no desktop icon");
        pass("QA exposes no launcher entry; no production data or authorization touched");
        result.putString("stream",log.toString());finish(Activity.RESULT_OK,result);
    }catch(Throwable failure){result.putString("stream",log+"FAIL: "+android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}}
    private interface Checked{void run()throws Exception;}
    private void run(Checked work){final Throwable[] failure={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new RuntimeException(failure[0]);}
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception{Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private static Object field(Object target,String name)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}
    private static void setField(Object target,String name,Object value)throws Exception{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}
    private View findText(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().contains(value))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findText(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private View findDescription(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findDescription(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private View findClass(View view,Class<?> type){if(type.isInstance(view))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findClass(((ViewGroup)view).getChildAt(i),type);if(found!=null)return found;}return null;}
    private void render(Context c,String name,View view){run(()->{
        int w=view.getWidth(),h=view.getHeight();if(w<=0||h<=0){w=c.getResources().getDisplayMetrics().widthPixels;h=c.getResources().getDisplayMetrics().heightPixels;}
        // Screen-off instrumentation has no display frames; explicitly perform pending layouts.
        view.forceLayout();view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);
        Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image));try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    });}
}
