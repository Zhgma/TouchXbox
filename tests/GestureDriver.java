import android.os.SystemClock;
import android.view.*;
import java.lang.reflect.Method;
import org.json.*;
import java.nio.file.*;
/** Shell-only integration driver. Uses touch events, never synthesized gamepad results. */
public final class GestureDriver {
    public static void main(String[] args)throws Exception{
        Class<?> cls=Class.forName("android.hardware.input.InputManager");Object im=cls.getMethod("getInstance").invoke(null);Method inject=cls.getMethod("injectInputEvent",InputEvent.class,int.class);
        JSONArray steps=new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));long down=0;
        for(int n=0;n<steps.length();n++){JSONObject s=steps.getJSONObject(n);if(s.has("wait")){Thread.sleep(s.getInt("wait"));continue;}if(s.has("label")){android.util.Log.i("TouchXboxCase",s.getString("label"));continue;}
            JSONArray pts=s.getJSONArray("points");int action=s.getInt("action");if(action==0)down=SystemClock.uptimeMillis();MotionEvent.PointerProperties[] pp=new MotionEvent.PointerProperties[pts.length()];MotionEvent.PointerCoords[] pc=new MotionEvent.PointerCoords[pts.length()];
            for(int i=0;i<pts.length();i++){JSONArray pt=pts.getJSONArray(i);pp[i]=new MotionEvent.PointerProperties();pp[i].id=pt.getInt(0);pp[i].toolType=MotionEvent.TOOL_TYPE_FINGER;pc[i]=new MotionEvent.PointerCoords();pc[i].x=(float)pt.getDouble(1);pc[i].y=(float)pt.getDouble(2);pc[i].pressure=1;pc[i].size=.1f;}
            MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,pts.length(),pp,pc,0,0,1,1,0,0,0x1002,0);if(!(Boolean)inject.invoke(im,e,2))throw new AssertionError("Touch rejected at step "+n);e.recycle();Thread.sleep(s.optInt("delay",80));
        }System.out.println("Gesture scenario completed");
    }
}
