package dev.touchxbox.pad;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.WindowManager;

/** One physical display coordinate system for editing, preview and overlay windows. */
final class ScreenSpace {
    static LayoutViewport viewport(Context context,boolean wide){
        LayoutViewport display=current(context);
        return LayoutViewport.fullScreen(display.displayWidth,display.displayHeight,wide);
    }
    static LayoutViewport current(Context c){
        // An Activity may have a Huawei freeform or letterboxed app frame. Use
        // the application display, and never subtract bar/cutout insets: those
        // change with the foreground window and used to shrink/shift layouts.
        WindowManager wm=(WindowManager)c.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if(Build.VERSION.SDK_INT>=30){Rect b=wm.getMaximumWindowMetrics().getBounds();return new LayoutViewport(b.width(),b.height(),0,0,0,0);}
        Point p=new Point();wm.getDefaultDisplay().getRealSize(p);return new LayoutViewport(p.x,p.y,0,0,0,0);
    }
    static Point size(Context c){LayoutViewport v=current(c);return new Point(v.width,v.height);}
    static Point forOrientation(Context c,boolean wide){LayoutViewport v=viewport(c,wide);return new Point(v.width,v.height);}
    static void position(WindowManager.LayoutParams p,LayoutViewport viewport,int x,int y){
        p.x=viewport.left+x;p.y=viewport.top+y;
        // Keep absolute display coordinates even when the host shows system bars.
        p.flags|=WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if(Build.VERSION.SDK_INT>=30){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
        else if(Build.VERSION.SDK_INT>=28)p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }
}
