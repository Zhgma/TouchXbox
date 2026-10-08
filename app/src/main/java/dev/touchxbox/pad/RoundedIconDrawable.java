package dev.touchxbox.pad;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Consistent rounded corners for both template logos and appearance choices. */
final class RoundedIconDrawable extends Drawable {
    private final Drawable source;
    private final Path clip=new Path();
    RoundedIconDrawable(Drawable source){this.source=source.mutate();}
    @Override protected void onBoundsChange(Rect bounds){
        source.setBounds(bounds);float radius=Math.min(bounds.width(),bounds.height())*.22f;
        clip.reset();clip.addRoundRect(new RectF(bounds),radius,radius,Path.Direction.CW);
    }
    @Override public void draw(Canvas canvas){int saved=canvas.save();canvas.clipPath(clip);source.draw(canvas);canvas.restoreToCount(saved);}
    @Override public void setAlpha(int alpha){source.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){source.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public int getIntrinsicWidth(){return source.getIntrinsicWidth();}
    @Override public int getIntrinsicHeight(){return source.getIntrinsicHeight();}
}
