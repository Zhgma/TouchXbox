package dev.touchxbox.pad;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;

/** Only desktop aliases use this transient entry. MainActivity always opens the app. */
public final class LauncherActivity extends Activity {
    static final String ERROR="quickLaunchError",AUTHORIZE="quickLaunchAuthorize";
    private boolean routed;
    @Override protected void onCreate(Bundle state){super.onCreate(state);}
    @Override protected void onResume(){super.onResume();if(routed)return;routed=true;
        LauncherIdentity.restore(this);LayoutStore store=new LayoutStore(this);String id=store.quickLaunchId();
        if(id.isEmpty()){openMain(this,null,false);finish();return;}
        LayoutProfile profile=store.quickLaunch();
        if(profile==null){store.setQuickLaunch("");openMain(this,"快捷启动模板已不存在，请重新选择。",false);finish();return;}
        if(!Settings.canDrawOverlays(this)){openMain(this,"需要允许悬浮窗权限。请在设置中打开“悬浮窗权限”后重试。",false);finish();return;}
        if(ShizukuInput.selected(this)?!ShizukuInput.authorized():!BridgeClient.hasActivationKey(this)){openMain(this,"输入服务尚未授权，请按电脑 USB 授权步骤完成激活。",true);finish();return;}
        try{startForegroundService(new Intent(this,OverlayService.class).setAction(OverlayService.QUICK_LAUNCH).putExtra(OverlayService.TEMPLATE_ID,profile.id));}
        catch(RuntimeException e){openMain(this,"无法启动遥控："+e.getMessage(),false);}
        finish();
    }
    static void openMain(Context c,String reason,boolean authorize){
        Intent intent=LauncherShortcuts.openIntent(c);if(reason!=null)intent.putExtra(ERROR,reason).putExtra(AUTHORIZE,authorize);c.startActivity(intent);
    }
}
