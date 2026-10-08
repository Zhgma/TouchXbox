package dev.touchxbox.pad;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Icon;
import android.util.Log;
import java.util.Collections;

/** A launcher long-press entry that always opens the library, bypassing quick launch. */
final class LauncherShortcuts {
    static final String OPEN_APP="open_app";
    private LauncherShortcuts(){}
    static Intent openIntent(Context c){return new Intent(c,MainActivity.class).setAction("dev.touchxbox.pad.OPEN_APP").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
    static void ensure(Context c,ComponentName launcher){
        try{
            ShortcutManager manager=c.getSystemService(ShortcutManager.class);if(manager==null)return;
            for(ShortcutInfo s:manager.getDynamicShortcuts())if(OPEN_APP.equals(s.getId())&&launcher.equals(s.getActivity())&&s.isEnabled())return;
            ShortcutInfo shortcut=new ShortcutInfo.Builder(c,OPEN_APP).setShortLabel("打开软件").setLongLabel("打开软件，管理模板和设置").setIcon(Icon.createWithResource(c,R.drawable.ic_pad)).setActivity(launcher).setIntent(openIntent(c)).build();
            if(!manager.addDynamicShortcuts(Collections.singletonList(shortcut)))Log.w("TouchXboxLauncher","Launcher shortcut update was rate limited");
        }catch(RuntimeException e){Log.w("TouchXboxLauncher","Launcher shortcuts unavailable",e);}
    }
}
