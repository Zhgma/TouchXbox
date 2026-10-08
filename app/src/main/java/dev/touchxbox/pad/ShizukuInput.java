package dev.touchxbox.pad;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Permission checks and Binder transport; no shell commands or copied activation keys. */
final class ShizukuInput {
    static final String PACKAGE="moe.shizuku.privileged.api";
    static final int PERMISSION_REQUEST=917;
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Object lock=new Object();private static IBinder remote;private static CountDownLatch connecting;
    private static volatile Context application;
    static {Shizuku.addBinderDeadListener(()->{synchronized(lock){remote=null;}Context app=application;if(app!=null)app.stopService(new Intent(app,OverlayService.class));});}
    private static final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder service){synchronized(lock){remote=service;if(connecting!=null)connecting.countDown();connecting=null;}}
        public void onServiceDisconnected(ComponentName name){synchronized(lock){remote=null;if(connecting!=null)connecting.countDown();connecting=null;}}
    };
    static boolean selected(Context c){return c.getSharedPreferences("connection",0).getBoolean("shizuku",true);}
    static boolean installed(Context c){try{c.getPackageManager().getPackageInfo(PACKAGE,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}}
    static boolean running(){try{return Shizuku.pingBinder()&&Shizuku.getVersion()>=13;}catch(Exception e){return false;}}
    static boolean authorized(){try{return running()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
    static String status(Context c){if(!installed(c)&&!running())return "尚未安装 Shizuku";if(!running())return "请在手机上启动 Shizuku 13 或更新版";if(!authorized())return "Shizuku 已运行，等待授权 TouchXbox";return "Shizuku 已运行并授权";}
    static void requestPermission(){Shizuku.requestPermission(PERMISSION_REQUEST);}
    static Session connect(Context context)throws Exception {
        application=context.getApplicationContext();
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Input connection must run off the UI thread");
        if(!authorized())throw new IOException(status(context));
        CountDownLatch pending;
        synchronized(lock){
            if(remote!=null&&remote.pingBinder())return new Session(remote);
            remote=null;
            if(connecting==null){connecting=new CountDownLatch(1);main.post(()->{
                try{Shizuku.UserServiceArgs args=new Shizuku.UserServiceArgs(new ComponentName(context.getPackageName(),ShizukuInputService.class.getName())).daemon(false).debuggable(false).processNameSuffix("touchxbox-input").version(7);Shizuku.bindUserService(args,connection);}
                catch(Exception e){synchronized(lock){if(connecting!=null)connecting.countDown();connecting=null;}}
            });}pending=connecting;
        }
        if(!pending.await(12,TimeUnit.SECONDS)){synchronized(lock){if(connecting==pending)connecting=null;}throw new IOException("Shizuku 后台服务启动超时，请重新启动 Shizuku 后重试");}
        synchronized(lock){if(remote==null||!remote.pingBinder())throw new IOException("Shizuku 后台服务未就绪，请重新授权后重试");return new Session(remote);}
    }
    static final class Session implements AutoCloseable {
        private final IBinder binder;private long id,lastPermissionCheck;
        Session(IBinder b){binder=b;}
        private Parcel request(){Parcel data=Parcel.obtain();data.writeInterfaceToken(ShizukuInputService.DESCRIPTOR);return data;}
        private Parcel call(int code,Parcel data)throws Exception {Parcel reply=Parcel.obtain();try{if(!binder.transact(code,data,reply,0))throw new IOException("Shizuku 输入接口不可用");reply.readException();String error=reply.readString();if(error!=null)throw new IOException(error);return reply;}catch(Exception e){reply.recycle();throw e;}finally{data.recycle();}}
        String probe()throws Exception {Parcel reply=call(ShizukuInputService.PROBE,request());reply.recycle();return "OK: Shizuku 已授权；虚拟手柄接口可访问";}
        void start(int protocol,boolean keyboard)throws Exception {Parcel data=request();data.writeInt(protocol);data.writeInt(keyboard?1:0);Parcel reply=call(ShizukuInputService.START,data);try{id=reply.readLong();}finally{reply.recycle();}}
        void send(byte[] packet)throws Exception {long now=SystemClock.elapsedRealtime();if(now-lastPermissionCheck>=1000){lastPermissionCheck=now;if(!authorized())throw new IOException("Shizuku 权限或服务已失效");}Parcel data=request();data.writeLong(id);data.writeByteArray(packet);Parcel reply=call(ShizukuInputService.REPORT,data);reply.recycle();}
        @Override public void close(){if(id==0)return;try{Parcel data=request();data.writeLong(id);Parcel reply=call(ShizukuInputService.RELEASE,data);reply.recycle();}catch(Exception ignored){}finally{id=0;}}
    }
}
