package dev.touchxbox.pad;

/** Geometry shared by scaled previews, full-size editing and physical windows. */
public final class LayoutViewport {
    public final int displayWidth,displayHeight,left,top,width,height;
    public LayoutViewport(int w,int h,int l,int t,int r,int b){
        displayWidth=Math.max(1,w);displayHeight=Math.max(1,h);
        left=Math.max(0,Math.min(l,displayWidth-1));top=Math.max(0,Math.min(t,displayHeight-1));
        width=Math.max(1,displayWidth-left-Math.max(0,r));height=Math.max(1,displayHeight-top-Math.max(0,b));
    }
    public LayoutProfile.Box screenBox(LayoutProfile.Box box){return new LayoutProfile.Box(left+Math.round(box.x),top+Math.round(box.y),Math.round(box.w),Math.round(box.h));}
    public static LayoutViewport fullScreen(int w,int h,boolean wide){return (w>h)==wide?new LayoutViewport(w,h,0,0,0,0):new LayoutViewport(h,w,0,0,0,0);}
}
