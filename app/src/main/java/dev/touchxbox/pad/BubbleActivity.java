package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;

/** A real Huawei multiwindow task: the OS owns the collapsed bubble and its edge docking. */
public final class BubbleActivity extends Activity {
    private boolean parked,requested;private final Handler main=new Handler();
    public static void open(Context c){Bundle options=ActivityOptions.makeBasic().toBundle();options.putInt("android.activity.windowingMode",102);c.startActivity(new Intent(c,BubbleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK),options);}
    public static void dismiss(Context c){for(ActivityManager.AppTask task:((ActivityManager)c.getSystemService(ACTIVITY_SERVICE)).getAppTasks()){try{android.content.ComponentName base=task.getTaskInfo().baseActivity;if(base!=null&&base.getClassName().equals(BubbleActivity.class.getName()))task.finishAndRemoveTask();}catch(Exception ignored){}}}
    @Override public void onCreate(Bundle b){super.onCreate(b);if(b!=null){parked=b.getBoolean("parked");requested=b.getBoolean("requested");}
        TextView v=new TextView(this);v.setText("TouchXbox\n点击恢复手柄");v.setTextColor(0xFFA8EDC8);v.setTextSize(24);v.setGravity(Gravity.CENTER);v.setBackgroundColor(0xFF142A24);setContentView(v);v.setOnClickListener(w->expand());
    }
    @Override protected void onResume(){super.onResume();if(parked){main.post(this::expand);return;}if(!requested){requested=true;getWindow().getDecorView().postDelayed(this::minimize,220);}}
    private void minimize(){
        // HarmonyOS turns a freeform task moved to the back into its native floating ball.
        // Use Activity's public lifecycle API; no hidden minimize API or drawn imitation.
        if(!isInMultiWindowMode()){failed("系统未以悬浮窗模式打开");return;}
        parked=true;boolean ok=moveTaskToBack(true);
        android.util.Log.i("TouchXboxBubble","Native task parked="+ok+" multiwindow="+isInMultiWindowMode());
        if(!ok){parked=false;failed("系统没有接受最小化请求");}
    }
    private void failed(String reason){Toast.makeText(this,reason+"；可从通知恢复手柄",Toast.LENGTH_LONG).show();finishAndRemoveTask();}
    private void expand(){if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("EXPAND"));finishAndRemoveTask();}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("parked",parked);b.putBoolean("requested",requested);super.onSaveInstanceState(b);}
    @Override public void onBackPressed(){if(parked)super.onBackPressed();else minimize();}
}
