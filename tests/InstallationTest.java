package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.util.*;

/** Test-only instrumentation, built into an isolated QA package, never the delivered APK. */
public final class InstallationTest extends Instrumentation {
    private Bundle options;private final StringBuilder log=new StringBuilder();
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void pass(String message){log.append("PASS: ").append(message).append('\n');}
    @Override public void onCreate(Bundle args){options=args;start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();check(c.getPackageName().equals("dev.touchxbox.pad.qa055"),"Refuse to test against a user installation");
            if(options!=null&&"restore".equals(options.getString("phase"))){
                check(LauncherIdentity.current(c)==LauncherIdentity.ORIGINAL&&LauncherIdentity.mode(c)==LauncherIdentity.ORIGINAL,"Pinned original identity persists across process restart");
                check(LauncherIdentity.lastProtocol(c)==3,"Real FPV activation remembered independently");LauncherIdentity.restore(c);verifyLauncher(c,LauncherIdentity.ORIGINAL);
                Activity main=main(c);waitForIdleSync();check(contains(main.getWindow().getDecorView(),"TouchXbox"),"Restored fixed name appears in app header");runOnMainSync(main::finish);
                check(new LayoutStore(c).all().size()==2,"Saved templates survive process restart");pass("cold restart retains original fixed icon/name, actual FPV protocol and both saved templates");
            }else{
                check(!c.getSharedPreferences("templates-v2",0).contains("profiles"),"QA must be freshly installed");
                LayoutStore store=new LayoutStore(c);check(store.all().isEmpty()&&store.active()==null,"Fresh install must have no templates");
                check(store.all().isEmpty(),"Reading empty library twice must not create templates");
                Activity main=main(c);waitForIdleSync();Thread.sleep(400);check(contains(main.getWindow().getDecorView(),"暂无模板"),"Fresh home must show empty state");
                drawWindow(c,"fresh-install-empty-view.png",main.getWindow().getDecorView());runOnMainSync(main::finish);
                Activity editor=startActivitySync(new Intent(c,EditorActivity.class).putExtra("new",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();runOnMainSync(editor::finish);
                check(store.all().isEmpty(),"Opening and abandoning a new draft must not create a template");pass("fresh home empty, no active template, repeated reads and abandoned editor remain empty");

                c.getSharedPreferences("templates-v2",0).edit().clear().commit();
                c.getSharedPreferences("layout",0).edit().putFloat("landscapeLx",.23f).putFloat("opacity",.64f).commit();
                List<LayoutProfile> migrated=store.all();check(migrated.size()==1,"Real legacy layout must migrate once");
                check(migrated.get(0).landscape.items.get("L").x==.23f&&migrated.get(0).opacity==.64f,"Legacy geometry and opacity preserved");
                check(store.delete(migrated.get(0).id)&&store.all().isEmpty(),"Deleted library must not resurrect legacy template");pass("real legacy data migrates once; deleting all templates remains empty");

                LayoutProfile ns=new LayoutProfile();ns.name="QA NS";ns.protocol=1;check(store.save(ns)==null,"Save first user template");
                LayoutProfile fpv=new LayoutProfile();fpv.name="QA FPV";fpv.protocol=3;check(store.save(fpv)==null,"Save second user template");
                check(store.all().size()==2&&store.active().id.equals(fpv.id),"Only explicitly saved templates appear");
                check(LauncherIdentity.current(c)==0,"Editing/saving a protocol must not impersonate successful activation");
                Class.forName("LayoutRoundTripTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
                Class.forName("FpvStickTest").getMethod("main",String[].class).invoke(null,(Object)new String[0]);pass("template round trip and FPV touch-to-HID tests on Android runtime");

                Bitmap icons=Bitmap.createBitmap(1280,400,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(icons);canvas.drawColor(0xFF101820);
                Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(29);text.setTextAlign(Paint.Align.CENTER);
                Set<Integer> resources=new HashSet<>();
                for(int protocol=0;protocol<4;protocol++){
                    check(LauncherIdentity.activate(c,protocol),"Identity activation returns success");
                    ActivityInfo activity=verifyLauncher(c,protocol);check(resources.add(activity.icon),"Each protocol must have a distinct icon resource");
                    Drawable icon=activity.loadIcon(c.getPackageManager());icon.setBounds(protocol*320+48,42,protocol*320+272,266);icon.draw(canvas);
                    canvas.drawText(LauncherIdentity.name(protocol),protocol*320+160,326,text);
                    LauncherIdentity.restore(c);verifyLauncher(c,protocol);
                }
                write(c,"launcher-icons-055.png",icons);icons.recycle();
                check(store.all().size()==2,"Launcher switches preserve templates");
                pass("four launcher labels/icons, exactly one entry each, DONT_KILL_APP switch and restore, template retention");

                check(LauncherIdentity.setMode(c,2),"Pin PS appearance");
                check(LauncherIdentity.activate(c,1),"Record NS activation while PS is pinned");verifyLauncher(c,2);
                check(LauncherIdentity.lastProtocol(c)==1,"Pinned mode still remembers actual protocol");
                check(LauncherIdentity.setMode(c,LauncherIdentity.AUTO),"Return to auto");verifyLauncher(c,1);
                check(LauncherIdentity.setMode(c,LauncherIdentity.ORIGINAL),"Pin original icon");
                check(verifyLauncher(c,LauncherIdentity.ORIGINAL).icon==R.drawable.ic_pad,"Original icon is the exact pre-change drawable");
                check(LauncherIdentity.activate(c,3),"FPV activation must not replace pinned original");verifyLauncher(c,LauncherIdentity.ORIGINAL);
                check(!LauncherIdentity.setMode(c,5)&&LauncherIdentity.mode(c)==LauncherIdentity.ORIGINAL,"Invalid mode rejected without changing selection");
                Activity settingsMain=main(c);waitForIdleSync();
                final AlertDialog[] chooser={null};
                runOnMainSync(()->{try{java.lang.reflect.Method method=MainActivity.class.getDeclaredMethod("chooseAppearance");method.setAccessible(true);chooser[0]=(AlertDialog)method.invoke(settingsMain);}catch(Exception e){throw new RuntimeException(e);}});
                waitForIdleSync();check(chooser[0].getListView().getAdapter().getCount()==6,"Picker has auto, original and four protocol presets");
                check(chooser[0].getListView().getAdapter().getItem(3).toString().contains("TouchNS")&&chooser[0].getListView().getAdapter().getItem(4).toString().contains("TouchPS"),"Picker names capitalize NS and PS");
                drawWindow(c,"appearance-picker-view.png",chooser[0].getWindow().getDecorView());
                runOnMainSync(()->chooser[0].getListView().performItemClick(null,4,4));waitForIdleSync();verifyLauncher(c,2);
                check(contains(settingsMain.getWindow().getDecorView(),"TouchPS"),"Picker click immediately updates app header");
                check(LauncherIdentity.lastProtocol(c)==3&&store.active().protocol==3,"Appearance choice never changes actual protocol/template");
                check(LauncherIdentity.setMode(c,LauncherIdentity.ORIGINAL),"Restore pinned original for cold-restart test");runOnMainSync(settingsMain::finish);
                pass("fixed PS survives NS activation; auto restores NS; original retained across FPV activation; real settings picker updates label without changing input protocol");
            }
            result.putString("stream",log.toString());finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream",log+"FAIL: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
    }
    private Activity main(Context c){return startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private ActivityInfo verifyLauncher(Context c,int protocol){
        Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(c.getPackageName());
        List<ResolveInfo> entries=c.getPackageManager().queryIntentActivities(launch,0);
        check(entries.size()==1,"Must expose exactly one launcher entry, got "+entries.size());
        ActivityInfo activity=entries.get(0).activityInfo;check(activity.loadLabel(c.getPackageManager()).toString().equals(LauncherIdentity.name(protocol)),"Desktop label matches active protocol");
        check(LauncherIdentity.current(c)==protocol,"Stored launcher identity matches active protocol");
        return activity;
    }
    private boolean contains(View view,String needle){if(view instanceof TextView&&((TextView)view).getText().toString().contains(needle))return true;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),needle))return true;}return false;}
    private void drawWindow(Context c,String name,View view)throws IOException{
        final Bitmap[] image={null};final Throwable[] error={null};runOnMainSync(()->{try{
            int width=view.getWidth(),height=view.getHeight();
            // A sleeping display may leave a newly shown dialog unmeasured.
            // Render the real view hierarchy offscreen; this is not a device screenshot.
            if(width<=0||height<=0){android.util.DisplayMetrics metrics=c.getResources().getDisplayMetrics();width=Math.min(metrics.widthPixels,(int)(480*metrics.density));int limit=Math.min(metrics.heightPixels,(int)(640*metrics.density));view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));height=view.getMeasuredHeight();check(height>0,"Offscreen view must measure");view.layout(0,0,width,height);}
            image[0]=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(image[0]));
        }catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null)throw new IOException("Offscreen view render failed",error[0]);
        write(c,name,image[0]);image[0].recycle();
    }
    private void write(Context c,String name,Bitmap image)throws IOException{try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}}
}
