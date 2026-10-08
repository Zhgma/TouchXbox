package dev.touchxbox.pad;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Shared thumbnail/full-size editor surface. It never creates or sends input. */
class LayoutCanvas extends View {
    interface Listener { void selected(String key); void touched(); void collision(String name); }
    LayoutProfile profile;private final boolean fullSize;private final Listener listener;
    private final Paint paint=new Paint(3);private final PadPainter painter=new PadPainter();
    private boolean wide,down;private String selected="FOLD";private LayoutViewport viewport;
    float targetW=960,targetH=490;private float zoom=1,ox,oy,grabX,grabY;private int pointer=-1;
    LayoutCanvas(Context c,LayoutProfile draft,boolean landscape,boolean full,Listener callback){super(c);profile=draft;wide=landscape;fullSize=full;listener=callback;setContentDescription(full?"全屏布局预览，可拖动控件":"手柄布局画布");}
    void orientation(boolean landscape){wide=landscape;pointer=-1;invalidate();}
    void selection(String key,boolean pressed){selected=key;down=pressed;invalidate();}
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);pointer=-1;postInvalidateOnAnimation();}
    private void fit(){
        viewport=ScreenSpace.viewport(getContext(),wide);targetW=viewport.width;targetH=viewport.height;
        if(fullSize){int[] pos=new int[2];getLocationOnScreen(pos);zoom=1;ox=-pos[0];oy=-pos[1];}
        else {zoom=Math.max(.001f,Math.min((getWidth()-dp(24))/(float)viewport.displayWidth,(getHeight()-dp(36))/(float)viewport.displayHeight));ox=(getWidth()-viewport.displayWidth*zoom)/2;oy=(getHeight()-viewport.displayHeight*zoom)/2;}
    }
    @Override protected void onDraw(Canvas canvas){
        fit();Canvas c=canvas;c.save();c.translate(ox,oy);c.scale(zoom,zoom);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF080F12);c.drawRect(0,0,viewport.displayWidth,viewport.displayHeight,paint);
        c.save();c.translate(viewport.left,viewport.top);c.clipRect(0,0,targetW,targetH);
        paint.setColor(0xFF192C25);c.drawRect(0,0,targetW,targetH,paint);
        if(profile.leftFloating){paint.setColor(0x183CCA91);c.drawRect(0,0,targetW/2,targetH,paint);}
        if(profile.rightFloating){paint.setColor(0x183C9ACA);c.drawRect(targetW/2,0,targetW,targetH,paint);}
        paint.setColor(fullSize?0x125F9680:0x245F9680);paint.setStrokeWidth(1/zoom);
        for(int i=1;i<8;i++){c.drawLine(targetW*i/8,0,targetW*i/8,targetH,paint);c.drawLine(0,targetH*i/8,targetW,targetH*i/8,paint);}
        LayoutProfile.Page page=profile.page(wide);profile.highlighted.clear();
        if(down){LayoutProfile.Spec chosen=LayoutProfile.spec(selected);if(chosen!=null){if(chosen.kind==4)profile.highlighted.addAll(Arrays.asList("A","B","X","Y"));else if(chosen.kind==2)profile.highlighted.addAll(Arrays.asList("D_0","D_1","D_2","D_3"));else profile.highlighted.add(profile.displayKey(chosen));}}
        for(LayoutProfile.Spec s:profile.specs()){
            if(!profile.visible(s))continue;LayoutProfile.Box b=rounded(page.box(s,targetW,targetH));boolean pressed=down&&s.name.equals(selected);
            c.save();c.translate(b.x,b.y);painter.draw(c,s,b.w,b.h,pressed,0,profile.throttle(s)?1:0,pressed?1:s.kind==6?.5f:0,pressed,profile.floating(s)?.38f:profile.opacity,pressed?27:0,profile);c.restore();
            if(s.name.equals(selected))outline(c,b,0xFFEABD73);
        }
        LayoutProfile.Box fold=rounded(page.fold(targetW,targetH));paint.setColor(0x3973AAE8);c.drawRect(fold.x,fold.y,fold.x+fold.w,fold.y+fold.h,paint);outline(c,fold,selected.equals("FOLD")?0xFFFFD889:0xFF96C4ED);
        painter.text(c,"收起区域",fold.x+fold.w/2,fold.y+fold.h/2,Math.min(fold.h*.35f,fullSize?dp(14):22/zoom),0xFFE1EEFF);
        c.restore();c.restore();if(listener!=null)listener.collision(profile.foldConflict(wide,targetW,targetH));
    }
    private LayoutProfile.Box rounded(LayoutProfile.Box b){return b.pixels(targetW,targetH);}
    private void outline(Canvas c,LayoutProfile.Box b,int color){paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth((fullSize?dp(1):2)/zoom);c.drawRoundRect(b.x,b.y,b.x+b.w,b.y+b.h,8,8,paint);paint.setStyle(Paint.Style.FILL);}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(listener!=null)listener.touched();fit();float x=(event.getX()-ox)/zoom-viewport.left,y=(event.getY()-oy)/zoom-viewport.top;
            LayoutProfile.Page page=profile.page(wide);String hit=null;LayoutProfile.Box box=rounded(page.fold(targetW,targetH));
            if(box.contains(x,y))hit="FOLD";
            else {List<LayoutProfile.Spec> all=profile.specs();for(int i=all.size()-1;i>=0;i--){LayoutProfile.Spec s=all.get(i);if(!profile.visible(s))continue;LayoutProfile.Box b=rounded(page.box(s,targetW,targetH));if(b.contains(x,y)){box=b;hit=s.name;break;}}}
            if(hit==null){pointer=-1;return true;}selected=hit;pointer=event.getPointerId(0);grabX=x-(box.x+box.w/2);grabY=y-(box.y+box.h/2);if(listener!=null)listener.selected(hit);invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointer)){pointer=-1;return true;}
        if(action==MotionEvent.ACTION_MOVE&&pointer>=0){int index=event.findPointerIndex(pointer);if(index>=0){profile.page(wide).move(selected,((event.getX(index)-ox)/zoom-viewport.left-grabX)/targetW,((event.getY(index)-oy)/zoom-viewport.top-grabY)/targetH,targetW,targetH);invalidate();}}return true;
    }
}
