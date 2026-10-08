package dev.touchxbox.pad;

import android.os.SystemClock;
import java.util.Arrays;
import java.util.Locale;

/** Diagnostics never substitute phone measurements for InputDevice sensor events. */
public final class MotionTelemetry {
    public static final class Source {
        public final boolean enabled,accelPresent,gyroPresent;
        public final long accelCount,gyroCount,accelTime,gyroTime;
        public final float[] accel,gyro;
        Source(boolean on,boolean ap,boolean gp,long ac,long gc,long at,long gt,float[] a,float[] g){enabled=on;accelPresent=ap;gyroPresent=gp;accelCount=ac;gyroCount=gc;accelTime=at;gyroTime=gt;accel=a.clone();gyro=g.clone();}
        public String line(boolean angular){boolean present=angular?gyroPresent:accelPresent;long time=angular?gyroTime:accelTime,count=angular?gyroCount:accelCount;String prefix=angular?"GYRO rad/s":"ACC m/s²";if(!enabled)return prefix+" · 已暂停";if(!present)return prefix+" · 本机未提供";if(time==0||SystemClock.elapsedRealtimeNanos()-time>250000000)return prefix+" · 等待新样本";float[] v=angular?gyro:accel;return String.format(Locale.US,"%s #%d  %+.3f %+.3f %+.3f",prefix,count,v[0],v[1],v[2]);}
    }
    public static final class Sent {
        public final int protocol;public final long count,time;public final byte[] report;
        Sent(int p,long n,byte[] b){protocol=p;count=n;time=SystemClock.elapsedRealtime();report=b.clone();}
        public String status(){return ControllerProtocol.NAMES[protocol].split(" · ")[0]+" · 写入确认 "+count+(SystemClock.elapsedRealtime()-time>1000?"（已停止更新）":"");}
        public String values(){if(protocol!=1||report.length!=49)return "后端已确认写入 HID 报告";short[] v=new short[6];for(int i=0;i<6;i++)v[i]=(short)((report[37+2*i]&255)|((report[38+2*i]&255)<<8));return "NS IMU 原始值 "+Arrays.toString(v);}
    }
    public static volatile Source source;
    public static volatile Sent sent;
    private static long sentCount,lastSnapshot,lastLog;
    public static synchronized void begin(){sent=null;source=null;sentCount=lastSnapshot=lastLog=0;}
    public static synchronized void acknowledged(int protocol,byte[] report){if(protocol!=1&&protocol!=2)return;sentCount++;long now=SystemClock.elapsedRealtime();if(now-lastSnapshot<100)return;lastSnapshot=now;Sent value=new Sent(protocol,sentCount,report);sent=value;if(now-lastLog>=1000){lastLog=now;android.util.Log.i("TouchXboxIMU","ACK "+value.status()+" "+value.values());}}
}
