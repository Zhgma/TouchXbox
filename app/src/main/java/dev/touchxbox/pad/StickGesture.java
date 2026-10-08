package dev.touchxbox.pad;

/** One owned pointer. A deliberate stationary hold clicks; ordinary dragging never auto-clicks. */
public final class StickGesture {
    public static final long HOLD_MS=500;
    public boolean active,clicked; public float x,y;
    private final boolean retainY,squareRange;
    private boolean canHold;private float startX,startY,centerX,centerY,radius,slop,initialY;private long started;
    public StickGesture(){this(false);}
    public StickGesture(boolean throttle){this(throttle,throttle);}
    public StickGesture(boolean throttle,boolean square){retainY=throttle;squareRange=square||throttle;reset();}
    public void begin(float px,float py,float cx,float cy,float range,float tolerance,long now){
        active=canHold=true;clicked=false;startX=px;startY=py;centerX=cx;centerY=cy;radius=Math.max(1,range);slop=tolerance;started=now;initialY=y;move(px,py);
    }
    public void move(float px,float py){if(!active)return;if(Math.hypot(px-startX,py-startY)>slop)canHold=false;x=(px-centerX)/radius;
        y=retainY?initialY+(py-startY)/radius:(py-centerY)/radius;
        // Radio gimbals have independent axis limits; a corner reaches full X and Y.
        if(squareRange){x=Math.max(-1,Math.min(1,x));y=Math.max(-1,Math.min(1,y));if(Math.abs(x)<.06f)x=0;if(!retainY&&Math.abs(y)<.06f)y=0;}
        else{float length=(float)Math.hypot(x,y);if(length>1){x/=length;y/=length;}if(length<.06f)x=y=0;}
    }
    public boolean hold(long now){if(!active||!canHold||clicked||now-started<HOLD_MS)return false;clicked=true;return true;}
    public void end(){active=clicked=canHold=false;x=0;if(!retainY)y=0;}
    /** A new session or suspended input starts with FPV throttle at the bottom. */
    public void reset(){active=clicked=canHold=false;x=0;y=retainY?1:0;}
}
