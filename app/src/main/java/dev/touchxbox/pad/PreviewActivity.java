package dev.touchxbox.pad;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Full-size layout adjustment, returning only an unsaved draft to the editor. */
public final class PreviewActivity extends Activity {
    private LayoutProfile draft;private boolean wide;private String selected="FOLD";
    private LayoutCanvas canvas;private FrameLayout root;private LinearLayout toolbar;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String data=state==null?getIntent().getStringExtra("draft"):state.getString("draft");
        try{draft=LayoutStore.decode(data);}catch(Exception e){finish();return;}
        wide=state==null?getIntent().getBooleanExtra("wide",true):state.getBoolean("wide");selected=state==null?getIntent().getStringExtra("selected"):state.getString("selected");if(selected==null)selected="FOLD";
        setRequestedOrientation(wide?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        Window window=getWindow();window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FullScreen.apply(window);
        root=new FrameLayout(this);root.setBackgroundColor(0xFF080F12);setContentView(root);
        canvas=new LayoutCanvas(this,draft,wide,true,new LayoutCanvas.Listener(){public void selected(String key){selected=key;}public void touched(){}public void collision(String name){}});canvas.selection(selected,false);root.addView(canvas,new FrameLayout.LayoutParams(-1,-1));
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(10),dp(3),dp(5),dp(3));
        GradientDrawable background=new GradientDrawable();background.setColor(0xF023303A);background.setCornerRadius(dp(12));background.setStroke(dp(1),0xFF55716D);toolbar.setBackground(background);toolbar.setElevation(dp(8));
        TextView handle=new TextView(this);handle.setText("⠿ 拖动控件调整\n拖这里移动工具条");handle.setTextSize(12);handle.setTextColor(0xFFBCD0C4);handle.setPadding(dp(4),0,dp(10),0);handle.setContentDescription("移动预览工具条");toolbar.addView(handle,new LinearLayout.LayoutParams(dp(150),dp(44)));
        Button back=new Button(this);back.setText("返回编辑");back.setTextSize(13);back.setTextColor(0xFFB9FBD6);back.setAllCaps(false);back.setPadding(0,0,0,0);toolbar.addView(back,new LinearLayout.LayoutParams(dp(92),dp(44)));back.setOnClickListener(v->returnDraft());
        root.addView(toolbar,new FrameLayout.LayoutParams(-2,-2));handle.setOnTouchListener(new View.OnTouchListener(){float startX,startY,barX,barY;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();barX=toolbar.getX();barY=toolbar.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){placeToolbar(barX+e.getRawX()-startX,barY+e.getRawY()-startY);return true;}return true;}});
        root.post(()->centerToolbar());hideBars();
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void centerToolbar(){if(toolbar==null)return;LayoutViewport v=ScreenSpace.current(this);int[] pos=new int[2];root.getLocationOnScreen(pos);placeToolbar(v.left+(v.width-toolbar.getWidth())/2f-pos[0],v.top+dp(12)-pos[1]);}
    private void placeToolbar(float x,float y){LayoutViewport v=ScreenSpace.current(this);int[] pos=new int[2];root.getLocationOnScreen(pos);toolbar.setX(LayoutProfile.clamp(x,v.left-pos[0],Math.max(v.left-pos[0],v.left+v.width-pos[0]-toolbar.getWidth())));toolbar.setY(LayoutProfile.clamp(y,v.top-pos[1],Math.max(v.top-pos[1],v.top+v.height-pos[1]-toolbar.getHeight())));}
    private void hideBars(){FullScreen.hideBars(getWindow());}
    private void returnDraft(){setResult(RESULT_OK,new Intent().putExtra("draft",LayoutStore.encode(draft)).putExtra("selected",selected));finish();}
    @Override public void onBackPressed(){returnDraft();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)hideBars();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(canvas!=null)root.post(()->{centerToolbar();canvas.requestLayout();canvas.invalidate();hideBars();});}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("draft",LayoutStore.encode(draft));state.putString("selected",selected);state.putBoolean("wide",wide);}
}
