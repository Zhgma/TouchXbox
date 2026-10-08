package dev.touchxbox.pad;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Linux UHID ABI. Only the ADB shell process instantiates this class. */
public final class UhidDevice implements AutoCloseable {
    private FileDescriptor fd;private final int protocol;private final int reportSize;private byte[] latest;
    public UhidDevice() throws Exception {this(ControllerProtocol.XBOX);}
    public UhidDevice(int mode) throws Exception {this(mode,mode==1?SwitchProCodec.descriptor():mode==2?Ds4Codec.descriptor():mode==3?FpvCodec.descriptor():HidDescriptor.bytes(),mode==1?SwitchProCodec.NAME:mode==2?Ds4Codec.NAME:mode==3?FpvCodec.NAME:HidDescriptor.NAME,mode==1?0x057e:mode==2?0x054c:mode==3?0x1209:HidDescriptor.VENDOR,mode==1?0x2009:mode==2?0x05c4:mode==3?0x5459:HidDescriptor.PRODUCT,ControllerProtocol.size(mode));}
    public UhidDevice(int mode,byte[] desc,String name,int vendor,int product,int length) throws Exception {
        protocol=mode;reportSize=length;latest=mode==1?SwitchProCodec.report(new PadState().report(),null,0):mode==2?Ds4Codec.report(new PadState().report(),null,0):mode==3?FpvCodec.report(new PadState(mode).report()):new byte[length];
        fd=Os.open("/dev/uhid",OsConstants.O_RDWR|OsConstants.O_NONBLOCK|OsConstants.O_CLOEXEC,0);
        try {
            ByteBuffer b=buffer(280+desc.length);b.putInt(11);
            b.put(name.getBytes(StandardCharsets.UTF_8));b.position(132);
            b.put(("touchxbox/"+mode).getBytes(StandardCharsets.UTF_8));b.position(196);b.put("02:54:58:00:00:01".getBytes(StandardCharsets.UTF_8));b.position(260);
            b.putShort((short)desc.length).putShort((short)(mode==1||mode==2?3:6));
            // hid-sony sets bit 0x8000 for its standardized Linux button layout. Android
            // selects the matching Version_8000 keylayout only with base version zero.
            b.putInt(vendor).putInt(product).putInt(mode==2?0:1).putInt(0).put(desc);
            write(b.array());
            long deadline=android.os.SystemClock.elapsedRealtime()+2500;boolean started=false;
            while(android.os.SystemClock.elapsedRealtime()<deadline){
                StructPollfd p=new StructPollfd();p.fd=fd;p.events=(short)OsConstants.POLLIN;
                if(Os.poll(new StructPollfd[]{p},200)>0 && (p.revents&OsConstants.POLLIN)!=0){
                    byte[] event=new byte[4380];int n=Os.read(fd,event,0,event.length);
                    if(n>=4&&ByteBuffer.wrap(event).order(ByteOrder.nativeOrder()).getInt()==2){started=true;break;}handle(event,n);
                }
            }
            if(!started)throw new IOException("内核未确认 UHID_START；不能宣称设备创建成功");
            report(mode==ControllerProtocol.XBOX?new PadState().report():latest);
        } catch(Exception e){close();throw e;}
    }
    public void report(byte[] data) throws Exception {if(data.length!=reportSize)throw new IOException("Invalid report length");latest=data.clone();input(data);drain();}
    private void input(byte[] data) throws Exception {ByteBuffer b=buffer(6+data.length);b.putInt(12).putShort((short)data.length).put(data);write(b.array());}
    private void drain() throws Exception {
        StructPollfd p=new StructPollfd();p.fd=fd;p.events=(short)OsConstants.POLLIN;
        for(int count=0;count<16 && Os.poll(new StructPollfd[]{p},0)>0;count++){
            if((p.revents&OsConstants.POLLIN)==0)throw new IOException("UHID poll error: "+p.revents);
            byte[] event=new byte[4380];int n=Os.read(fd,event,0,event.length);if(n<4)break;
            handle(event,n);
        }
    }
    private void handle(byte[] event,int n)throws Exception{
        if(n<4)return;ByteBuffer r=ByteBuffer.wrap(event).order(ByteOrder.nativeOrder());int type=r.getInt();
        if(type==6&&n>=4103){int length=r.getShort(4100)&65535;if(length<=4096)output(Arrays.copyOfRange(event,4,4+length));}
        if(type==9&&n>=10){byte[] data=protocol==2?Ds4Codec.feature(event[8]&255):null;ByteBuffer reply=buffer(12+(data==null?0:data.length));reply.putInt(10).putInt(r.getInt()).putShort((short)(data==null?5:0)).putShort((short)(data==null?0:data.length));if(data!=null)reply.put(data);write(reply.array());}
        if(type==13&&n>=12){int request=r.getInt(),length=r.getShort(10)&65535;boolean ok=length<=4096&&12+length<=n&&(protocol==1||protocol==2);if(ok)output(Arrays.copyOfRange(event,12,12+length));ByteBuffer reply=buffer(10);reply.putInt(14).putInt(request).putShort((short)(ok?0:5));write(reply.array());}
    }
    private void output(byte[] data)throws Exception{if(protocol==ControllerProtocol.SWITCH_PRO){byte[] reply=SwitchProCodec.reply(data,latest);if(reply!=null)input(reply);}}
    private static ByteBuffer buffer(int n){return ByteBuffer.allocate(n).order(ByteOrder.nativeOrder());}
    private void write(byte[] b) throws Exception {if(Os.write(fd,b,0,b.length)!=b.length)throw new IOException("Short UHID write");}
    @Override public void close(){if(fd!=null){try{Os.close(fd);}catch(Exception ignored){}fd=null;}}
}
