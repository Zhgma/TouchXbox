package dev.touchxbox.pad;

import android.content.Context;
import android.os.*;
import android.system.*;
import java.io.FileDescriptor;
import java.util.Arrays;

/** Runs only as a Shizuku UserService. No exported Android service or network port. */
public final class ShizukuInputService extends Binder {
    static final String DESCRIPTOR="dev.touchxbox.pad.ShizukuInput";
    static final int PROBE=1,START=2,REPORT=3,RELEASE=4,DESTROY=16777115;
    private final int ownerUid;private UhidDevice pad,keyboard;private int reportSize;
    private long session,lastReport;
    public ShizukuInputService(Context context){
        ownerUid=context.getApplicationInfo().uid;attachInterface(null,DESCRIPTOR);
        Thread watchdog=new Thread(()->{while(true){try{Thread.sleep(250);}catch(InterruptedException e){return;}synchronized(this){if(pad!=null&&SystemClock.elapsedRealtime()-lastReport>1200)closeDevices();}}},"TouchXbox input timeout");watchdog.setDaemon(true);watchdog.start();
    }
    @Override protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
        if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        int caller=Binder.getCallingUid();
        if(code==DESTROY&&(caller==ownerUid||caller==2000||caller==0)){closeDevices();System.exit(0);return true;}
        if(ownerUid<10000||caller!=ownerUid)throw new SecurityException("Input service belongs to another application");
        data.enforceInterface(DESCRIPTOR);
        if(code==PROBE){String error=null;try{FileDescriptor fd=Os.open("/dev/uhid",OsConstants.O_RDWR|OsConstants.O_CLOEXEC,0);Os.close(fd);}catch(Exception e){error=e.getMessage();}reply.writeNoException();reply.writeString(error);return true;}
        if(code==START){
            int mode=data.readInt();boolean keys=data.readInt()!=0;closeDevices();String error=null;
            try{if(mode!=ControllerProtocol.valid(mode))throw new IllegalArgumentException("不支持的协议");pad=new UhidDevice(mode);if(keys)keyboard=new UhidDevice(4,KeyboardKeys.descriptor(),KeyboardKeys.DEVICE_NAME,KeyboardKeys.VENDOR,KeyboardKeys.PRODUCT,32);reportSize=ControllerProtocol.size(mode);lastReport=SystemClock.elapsedRealtime();session++;}
            catch(Exception e){closeDevices();error=e.getMessage();}reply.writeNoException();reply.writeString(error);reply.writeLong(error==null?session:0);return true;
        }
        if(code==REPORT){
            long requested=data.readLong();byte[] packet=data.createByteArray();String error=null;
            try{if(pad==null||requested!=session)throw new IllegalStateException("虚拟手柄会话已结束");if(packet==null||packet.length!=reportSize+32)throw new IllegalArgumentException("输入报告长度错误");pad.report(Arrays.copyOf(packet,reportSize));if(keyboard!=null)keyboard.report(Arrays.copyOfRange(packet,reportSize,packet.length));lastReport=SystemClock.elapsedRealtime();}
            catch(Exception e){if(requested==session)closeDevices();error=e.getMessage();}reply.writeNoException();reply.writeString(error);return true;
        }
        if(code==RELEASE){if(data.readLong()==session)closeDevices();reply.writeNoException();reply.writeString(null);return true;}
        return super.onTransact(code,data,reply,flags);
    }
    private void closeDevices(){if(pad!=null){pad.close();pad=null;}if(keyboard!=null){keyboard.close();keyboard=null;}}
}
