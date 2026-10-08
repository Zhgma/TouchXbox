package dev.touchxbox.pad;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/** One launcher entry: follow the last started protocol, or keep a chosen appearance. */
public final class LauncherIdentity {
    public static final int AUTO=-1,ORIGINAL=4;
    private static final String[] ALIASES={"LauncherXbox","LauncherNs","LauncherPs","LauncherFPV","LauncherOriginal"};
    private static final String[] NAMES={"TouchXbox","TouchNS","TouchPS","TouchFPV","TouchXbox"};
    private static final int[] ICONS={R.mipmap.ic_launcher_xbox,R.mipmap.ic_launcher_ns,R.mipmap.ic_launcher_ps,R.mipmap.ic_launcher_fpv,R.drawable.ic_pad};
    private LauncherIdentity(){}
    public static int mode(Context c){int mode=c.getSharedPreferences("launcher-identity",0).getInt("mode",AUTO);return mode>=AUTO&&mode<=ORIGINAL?mode:AUTO;}
    public static int lastProtocol(Context c){return ControllerProtocol.valid(c.getSharedPreferences("launcher-identity",0).getInt("protocol",ControllerProtocol.XBOX));}
    public static int current(Context c){int selected=mode(c);return selected==AUTO?lastProtocol(c):selected;}
    public static String name(int appearance){return NAMES[appearance>=0&&appearance<NAMES.length?appearance:0];}
    public static int icon(int appearance){return ICONS[appearance>=0&&appearance<ICONS.length?appearance:0];}
    public static void restore(Context c){apply(c,current(c));}
    public static boolean activate(Context c,int protocol){
        protocol=ControllerProtocol.valid(protocol);
        // Remember real activation even while the user has pinned a different icon.
        if(!c.getSharedPreferences("launcher-identity",0).edit().putInt("protocol",protocol).commit())return false;
        return apply(c,current(c));
    }
    public static boolean setMode(Context c,int selected){
        if(selected<AUTO||selected>ORIGINAL)return false;
        int previous=mode(c);
        if(!c.getSharedPreferences("launcher-identity",0).edit().putInt("mode",selected).commit())return false;
        if(apply(c,current(c)))return true;
        c.getSharedPreferences("launcher-identity",0).edit().putInt("mode",previous).commit();restore(c);return false;
    }
    private static boolean apply(Context c,int protocol){
        try{
            PackageManager pm=c.getPackageManager();
            ComponentName target=new ComponentName(c.getPackageName(),c.getPackageName()+"."+ALIASES[protocol]);
            // Enable the replacement before hiding the old entry. Never disable MainActivity.
            if(!enabled(pm,target,protocol))pm.setComponentEnabledSetting(target,PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);
            for(int i=0;i<ALIASES.length;i++)if(i!=protocol){
                ComponentName other=new ComponentName(c.getPackageName(),c.getPackageName()+"."+ALIASES[i]);
                if(enabled(pm,other,i))pm.setComponentEnabledSetting(other,PackageManager.COMPONENT_ENABLED_STATE_DISABLED,PackageManager.DONT_KILL_APP);
            }
            return true;
        }catch(RuntimeException e){Log.w("TouchXboxLauncher","Could not update launcher identity",e);return false;}
    }
    private static boolean enabled(PackageManager pm,ComponentName component,int protocol){
        int state=pm.getComponentEnabledSetting(component);
        return state==PackageManager.COMPONENT_ENABLED_STATE_ENABLED||state==PackageManager.COMPONENT_ENABLED_STATE_DEFAULT&&protocol==ControllerProtocol.XBOX;
    }
}
