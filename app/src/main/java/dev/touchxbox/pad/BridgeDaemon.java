package dev.touchxbox.pad;

import java.net.*;
import java.nio.file.*;
import android.system.Os;
import android.system.OsConstants;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;

/** Shell-owned UHID bridge, bound exclusively to authenticated phone loopback. */
public final class BridgeDaemon {
    public static final int PORT=37684;
    public static void main(String[] args) throws Exception {
        run(args,PORT);
    }
    /** Alternate port permits isolated lifecycle tests without interrupting the real bridge. */
    static void run(String[] args,int port) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected app UID and private key path");
        int uid=Integer.parseInt(args[0]);if(uid<10000)throw new IllegalArgumentException("Invalid application UID");
        byte[] key=BridgeAuth.decode(new String(Files.readAllBytes(Paths.get(args[1])),"US-ASCII").trim());
        Files.delete(Paths.get(args[1]));
        try(ServerSocket server=new ServerSocket()){
            server.setReuseAddress(true);server.bind(new InetSocketAddress("127.0.0.1",port),2);
            System.out.println("TouchXbox bridge ready; app uid="+uid+"; authenticated loopback");
            boolean running=true;
            while(running){
                try(Socket socket=server.accept()){
                    socket.setSoTimeout(1000);socket.setTcpNoDelay(true);
                    DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                    BridgeAuth.server(in,out,key);
                    String command=in.readUTF();
                    if(command.equals("STOP")){out.writeUTF("OK");out.flush();running=false;continue;}
                    if(command.equals("PROBE")){
                        try{FileDescriptor fd=Os.open("/dev/uhid",OsConstants.O_RDWR|OsConstants.O_CLOEXEC,0);Os.close(fd);out.writeUTF("OK: /dev/uhid 可访问；启动悬浮层后验证系统设备");}
                        catch(Exception e){out.writeUTF("ERR: "+e.getMessage());}out.flush();continue;
                    }
                    boolean v2=command.equals("START_V2");if(!v2&&!command.equals("START"))continue;
                    int protocol=v2?in.readUnsignedByte():0;boolean keyboard=v2&&in.readBoolean();if(protocol!=ControllerProtocol.valid(protocol))throw new IllegalArgumentException("Unknown protocol");
                    try(UhidDevice device=new UhidDevice(protocol);UhidDevice keys=keyboard?new UhidDevice(4,KeyboardKeys.descriptor(),KeyboardKeys.DEVICE_NAME,KeyboardKeys.VENDOR,KeyboardKeys.PRODUCT,32):null){
                        out.writeUTF("OK");out.flush();byte[] report=new byte[ControllerProtocol.size(protocol)],keyReport=new byte[32];
                        while(true){in.readFully(report);if(v2)in.readFully(keyReport);device.report(report);if(keys!=null)keys.report(keyReport);out.writeByte(1);out.flush();}
                    }catch(Exception e){try{out.writeUTF("ERR: "+e.getMessage());out.flush();}catch(Exception ignored){}System.out.println("Session ended: "+e);}
                    // Closing /dev/uhid unregisters the controller, including on timeout/crash.
                }catch(Exception e){System.out.println("Client ended: "+e);}
            }
        }
    }
}
