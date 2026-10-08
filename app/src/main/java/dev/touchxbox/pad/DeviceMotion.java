package dev.touchxbox.pad;

import android.content.Context;
import android.hardware.*;
import android.os.*;
import android.view.WindowManager;
import java.util.*;

/** Samples this phone/tablet's sensors. No downstream app is involved. */
public final class DeviceMotion implements SensorEventListener,AutoCloseable {
    private final SensorManager manager;private final WindowManager windows;
    private final Sensor accel,gyro;private final HandlerThread thread=new HandlerThread("TouchXbox IMU");
    private final float[] a=new float[3],g=new float[3];private final short[][] ring=new short[3][6];
    private int cursor,count;private long lastAccel,lastGyro,lastLog,accelCount,gyroCount,lastSnapshot;private boolean enabled;
    public static volatile String status="体感尚未启动";
    private static Sensor find(SensorManager m,int type){Sensor s=m.getDefaultSensor(type);if(s!=null)return s;List<Sensor> list=m.getSensorList(type);return list.isEmpty()?null:list.get(0);}
    private static Sensor gyro(SensorManager m){Sensor s=find(m,4);return s!=null?s:find(m,16);}
    public DeviceMotion(Context c){manager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);windows=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);accel=find(manager,Sensor.TYPE_ACCELEROMETER);gyro=gyro(manager);thread.start();}
    public static String availability(Context c){SensorManager m=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);Sensor gyro=gyro(m);return "加速度计："+(find(m,1)==null?"未提供":"可用")+" · 陀螺仪："+(gyro==null?"未提供":gyro.getType()==16?"可用（未校准接口）":"可用");}
    public static String diagnostics(Context c){SensorManager m=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);StringBuilder text=new StringBuilder(availability(c));text.append("\n已校准陀螺仪：").append(m.getSensorList(4).size()).append(" 项\n未校准陀螺仪：").append(m.getSensorList(16).size()).append(" 项\n系统声明陀螺仪：").append(c.getPackageManager().hasSystemFeature("android.hardware.sensor.gyroscope")?"是":"否");text.append("\n\n本机传感器列表（不含虚拟手柄）：");for(Sensor s:m.getSensorList(Sensor.TYPE_ALL))text.append("\n").append(s.getType()).append(" · ").append(s.getName());return text.toString();}
    public synchronized void setEnabled(boolean value){if(value==enabled)return;enabled=value;manager.unregisterListener(this);Arrays.fill(a,0);Arrays.fill(g,0);cursor=count=0;lastAccel=lastGyro=0;for(short[] frame:ring)Arrays.fill(frame,(short)0);
        if(value){Handler h=new Handler(thread.getLooper());boolean registered=accel!=null&&manager.registerListener(this,accel,5000,h);boolean angular=gyro!=null&&manager.registerListener(this,gyro,5000,h);status="采集：加速度"+(registered?"开启":"不可用")+" / 陀螺仪"+(angular?"开启":"不可用（不生成测量值）");}else status="体感已暂停";publish();
    }
    @Override public synchronized void onSensorChanged(SensorEvent e){if(!enabled)return;long now=SystemClock.elapsedRealtimeNanos();if(e.sensor.getType()==Sensor.TYPE_GYROSCOPE||e.sensor.getType()==Sensor.TYPE_GYROSCOPE_UNCALIBRATED){System.arraycopy(e.values,0,g,0,3);if(e.sensor.getType()==16&&e.values.length>=6)for(int i=0;i<3;i++)g[i]-=e.values[i+3];lastGyro=now;gyroCount++;}else if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){System.arraycopy(e.values,0,a,0,3);lastAccel=now;accelCount++;}else return;
        if(lastGyro==0||now-lastGyro>250000000)Arrays.fill(g,0);
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER||accel==null){ring[cursor]=MotionCodec.convert(a,g,windows.getDefaultDisplay().getRotation());cursor=(cursor+1)%3;count=Math.min(3,count+1);}
        if(now-lastSnapshot>=100000000){lastSnapshot=now;publish();}
        if(now-lastLog>1000000000){lastLog=now;android.util.Log.i("TouchXboxIMU",String.format(Locale.US,"SOURCE accel=%s gyro=%s samples=%d gyroPresent=%s",Arrays.toString(a),Arrays.toString(g),count,gyro!=null));}
    }
    private void publish(){MotionTelemetry.source=new MotionTelemetry.Source(enabled,accel!=null,gyro!=null,accelCount,gyroCount,lastAccel,lastGyro,a,g);}
    public synchronized short[][] samples(){short[][] result=new short[3][6];if(!enabled||count==0||lastAccel!=0&&SystemClock.elapsedRealtimeNanos()-lastAccel>250000000)return result;for(int i=0;i<3;i++){int age=Math.min(2-i,count-1);result[i]=ring[(cursor+2-age)%3].clone();}return result;}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public synchronized void close(){enabled=false;manager.unregisterListener(this);thread.quitSafely();status="体感已停止";publish();}
}
