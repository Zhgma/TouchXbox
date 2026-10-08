package dev.touchxbox.pad;
import java.util.HashMap;
/** One triangle per touch. Two fingers on one button keep it held until both leave. */
public final class ButtonChord {
    private final HashMap<Integer,Integer> pointers=new HashMap<>();
    public void point(int id,float x,float y){point(id,x,y,false);}
    public void point(int id,float x,float y,boolean holdOutside){int key=triangle(x,y);if(key<0){if(!holdOutside)pointers.remove(id);}else pointers.put(id,key);}
    public void up(int id){pointers.remove(id);}
    public void reset(){pointers.clear();}
    public int mask(){int mask=0;for(int key:pointers.values())mask|=1<<key;return mask;}
    public static int triangle(float x,float y){if(x<0||x>1||y<0||y>1||Float.isNaN(x)||Float.isNaN(y))return -1;float dx=x-.5f,dy=y-.5f;if(Math.abs(dx)+Math.abs(dy)>.7071068f||(Math.abs(dx)<.015f&&Math.abs(dy)<.015f))return -1;return Math.abs(dx)>Math.abs(dy)?(dx<0?PadState.X:PadState.B):(dy<0?PadState.Y:PadState.A);}
}
