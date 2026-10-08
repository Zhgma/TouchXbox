package dev.touchxbox.pad;

import android.app.Activity;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.hardware.*;
import java.util.*;

/** Shows only InputDevice/KeyEvent/MotionEvent results, not the UI's requested state. */
public final class TesterActivity extends Activity implements SensorEventListener {
    private TestView view;private final Set<Integer> pressed=new TreeSet<>();private String last="尚未收到系统事件";
    private final float[] axes=new float[8];private int keyEvents,motionEvents;private boolean fpv;
    private final int[] axisIds={0,1,11,14,17,18,15,16};
    private final Handler handler=new Handler(Looper.getMainLooper());private SensorManager inputSensors;private int sensorDevice=-1,sensorEvents;private String sensorStatus="等待系统传感器",deviceName="等待系统手柄";private final float[] acceleration=new float[3],angular=new float[3];private long lastSensorLog;private boolean resumed;
    @Override public void onCreate(Bundle b){super.onCreate(b);FullScreen.apply(getWindow());view=new TestView();setContentView(view);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)FullScreen.hideBars(getWindow());}
    private boolean ours(int id){InputDevice d=InputDevice.getDevice(id);return d!=null&&(ControllerProtocol.matches(d.getVendorId(),d.getProductId())||d.getVendorId()==KeyboardKeys.VENDOR&&d.getProductId()==KeyboardKeys.PRODUCT);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(ours(e.getDeviceId())){if(e.getAction()==0)pressed.add(e.getKeyCode());else pressed.remove(e.getKeyCode());keyEvents++;last=KeyEvent.keyCodeToString(e.getKeyCode())+(e.getAction()==0?" DOWN":" UP");android.util.Log.i("TouchXboxTest",last+" device="+e.getDeviceId()+" meta="+e.getMetaState());view.invalidate();return true;}return super.dispatchKeyEvent(e);}
    @Override public boolean onGenericMotionEvent(MotionEvent e){if(ours(e.getDeviceId())){InputDevice source=e.getDevice();fpv=source!=null&&source.getProductId()==0x5459;int[] ids=fpv?new int[]{0,1,19,12,13,14,11,20}:axisIds;for(int i=0;i<axes.length;i++)axes[i]=e.getAxisValue(ids[i]);motionEvents++;last="AXES "+Arrays.toString(axes);android.util.Log.i("TouchXboxTest",last);InputDevice d=e.getDevice();if(d!=null&&d.getProductId()==0x5459){StringBuilder values=new StringBuilder("FPV ");for(InputDevice.MotionRange r:d.getMotionRanges())values.append(MotionEvent.axisToString(r.getAxis())).append('=').append(e.getAxisValue(r.getAxis())).append(' ');android.util.Log.i("TouchXboxTest",values.toString());}view.invalidate();return true;}return super.onGenericMotionEvent(e);}
    private final Runnable bind=new Runnable(){public void run(){if(!resumed)return;for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&ControllerProtocol.matches(d.getVendorId(),d.getProductId())){deviceName=d.getName();fpv=d.getProductId()==0x5459;if(sensorDevice!=id){unbind();sensorDevice=id;try{if(Build.VERSION.SDK_INT>=31){inputSensors=d.getSensorManager();List<Sensor> sensors=inputSensors.getSensorList(Sensor.TYPE_ALL);sensorStatus=sensors.isEmpty()?"系统驱动未提供体感接口":"系统体感接口："+sensors.size()+" 项";for(Sensor s:sensors)if(s.getType()==1||s.getType()==4)inputSensors.registerListener(TesterActivity.this,s,15000,handler);android.util.Log.i("TouchXboxTest","DEVICE "+id+" "+d.getName()+" sensors="+sensors);}else sensorStatus="Android 12 以下不提供此传感器 API";}catch(Exception e){sensorStatus="系统传感器接口："+e.getMessage();}}break;}}handler.postDelayed(this,1000);}};
    private void unbind(){if(inputSensors!=null){inputSensors.unregisterListener(this);inputSensors=null;}sensorDevice=-1;}
    @Override public void onSensorChanged(SensorEvent event){if(event.sensor.getType()==1)System.arraycopy(event.values,0,acceleration,0,3);else if(event.sensor.getType()==4)System.arraycopy(event.values,0,angular,0,3);sensorEvents++;long now=SystemClock.uptimeMillis();if(now-lastSensorLog>=1000){lastSensorLog=now;android.util.Log.i("TouchXboxTest","SYSTEM_IMU device="+sensorDevice+" count="+sensorEvents+" accel="+Arrays.toString(acceleration)+" gyro="+Arrays.toString(angular));}view.invalidate();}
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(bind);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(bind);unbind();pressed.clear();Arrays.fill(axes,0);super.onPause();}
    private final class TestView extends View {
        final Paint p=new Paint(3);TestView(){super(TesterActivity.this);setContentDescription("系统手柄输入测试");}
        @Override protected void onDraw(Canvas c){
            c.drawColor(0xFF0B1712);float scale=Math.min(getWidth()/1100f,getHeight()/630f);c.save();c.translate(getWidth()*.38f,getHeight()*.19f);c.scale(scale,scale);
            p.setTypeface(Typeface.create("sans-serif-medium",0));
            line(c,"SYSTEM INPUT",0,0,17,0xFF72DEAB);line(c,deviceName,0,24,12,Color.WHITE);
            line(c,"按键 "+keyEvents+" · 轴 "+motionEvents+" · 按住 "+pressed,0,46,12,0xFF9EF2BF);
            String[] names=fpv?new String[]{"CH1","CH2","CH3","CH4","CH5","CH6","CH7","CH8"}:new String[]{"LX","LY","RX","RY","LT","RT","DX","DY"};
            for(int row=0;row<4;row++)line(c,String.format(Locale.US,"%s %+.2f    %s %+.2f",names[row*2],axes[row*2],names[row*2+1],axes[row*2+1]),0,68+row*20,12,0xFFC5D8CC);
            line(c,last.length()>48?last.substring(0,48):last,0,150,10,0xFF91A89A);
            line(c,"本机传感器采集",0,181,14,0xFF72DEAB);MotionTelemetry.Source source=MotionTelemetry.source;
            line(c,source==null?"当前协议未采集体感":source.line(false),0,203,11,0xFFC5D8CC);
            line(c,source==null?"":source.line(true),0,224,11,0xFFC5D8CC);
            MotionTelemetry.Sent sent=MotionTelemetry.sent;
            line(c,sent==null?"体感报告：未写入":sent.status(),0,253,12,0xFF72DEAB);
            line(c,sent==null?"":sent.values(),0,275,10,0xFFC5D8CC);
            line(c,"系统手柄体感回传",0,306,14,0xFF72DEAB);
            line(c,sensorStatus,0,328,12,inputSensors==null||inputSensors.getSensorList(Sensor.TYPE_ALL).isEmpty()?0xFFFFD889:0xFFC5D8CC);
            if(inputSensors!=null&&!inputSensors.getSensorList(Sensor.TYPE_ALL).isEmpty()){
                line(c,String.format(Locale.US,"IMU #%d ACC %+.3f %+.3f %+.3f",sensorEvents,acceleration[0],acceleration[1],acceleration[2]),0,350,11,0xFFC5D8CC);
                line(c,String.format(Locale.US,"GYRO %+.3f %+.3f %+.3f",angular[0],angular[1],angular[2]),0,371,11,0xFFC5D8CC);
            }
            c.restore();postInvalidateDelayed(200);
        }
        void line(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(size);c.drawText(s,x,y,p);}
    }
}
