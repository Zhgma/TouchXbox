package dev.touchxbox.pad;
import android.content.*;
/** Protected by platform DUMP permission: only shell/system may provision the secret. */
public final class ActivationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){String token=i.getStringExtra("token");if(token!=null&&token.matches("[0-9a-f]{64}")){c.getSharedPreferences("bridge",0).edit().putString("token",token).commit();c.getSharedPreferences("connection",0).edit().putBoolean("shizuku",false).commit();setResultCode(1);}}
}
