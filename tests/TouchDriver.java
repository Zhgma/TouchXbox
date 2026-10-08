import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;
/** ADB-shell-only test helper, NOT included in the APK. Coordinates for HBN-AL00 landscape. */
public final class TouchDriver {
    static Object im;static Method inject;static long down;
    static void event(int action,float[][] points)throws Exception{
        MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[points.length];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[points.length];
        for(int i=0;i<points.length;i++){pp[i]=new MotionEvent.PointerProperties();pp[i].id=i;pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i]=new MotionEvent.PointerCoords();pc[i].x=points[i][0];pc[i].y=points[i][1];pc[i].pressure=1;pc[i].size=.1f;}
        MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,points.length,pp,pc,0,0,1,1,0,0,0x1002,0);
        if(!(Boolean)inject.invoke(im,e,2))throw new AssertionError("Touch injection rejected");e.recycle();Thread.sleep(90);
    }
    static void tap(String name,float x,float y)throws Exception{android.util.Log.i("TouchXboxCase",name);down=SystemClock.uptimeMillis();float[][] a={{x,y}};event(0,a);event(1,a);}
    public static void main(String[] args)throws Exception{
        Class<?> cls=Class.forName("android.hardware.input.InputManager");im=cls.getMethod("getInstance").invoke(null);inject=cls.getMethod("injectInputEvent",InputEvent.class,int.class);
        if(args.length>0&&args[0].equals("hold")){down=SystemClock.uptimeMillis();float[][] one={{540,630}},two={{540,630},{2504,955}};event(0,one);event(5|(1<<8),two);Thread.sleep(10000);event(6|(1<<8),two);event(1,one);return;}
        tap("A",2504,955);tap("B",2707,789);tap("X",2302,789);tap("Y",2504,620);tap("LB",341,351);tap("RB",2644,351);tap("LS",422,1114);tap("RS",1682,1093);tap("VIEW",1221,609);tap("MENU",1763,609);
        android.util.Log.i("TouchXboxCase","LEFT_STICK_PLUS_A");down=SystemClock.uptimeMillis();float[][] one={{463,687}},two={{540,630},{2504,955}};event(0,one);event(2,new float[][]{{540,630}});event(5|(1<<8),two);event(2,new float[][]{{520,605},{2504,955}});event(6|(1<<8),two);event(1,one);
        android.util.Log.i("TouchXboxCase","BOTH_TRIGGERS");down=SystemClock.uptimeMillis();one=new float[][]{{652,350}};two=new float[][]{{652,350},{2332,350}};event(0,one);event(5|(1<<8),two);event(2,new float[][]{{652,450},{2332,415}});event(6|(1<<8),two);event(1,one);
        android.util.Log.i("TouchXboxCase","RIGHT_STICK_AND_DPAD_CANCEL");down=SystemClock.uptimeMillis();one=new float[][]{{2031,994}};two=new float[][]{{2115,925},{1015,940}};event(0,one);event(2,new float[][]{{2115,925}});event(5|(1<<8),two);event(3,two);
        android.util.Log.i("TouchXboxCase","GUIDE");tap("GUIDE",1491,429);android.util.Log.i("TouchXboxCase","DONE");System.out.println("Touch sequence completed");
    }
}
