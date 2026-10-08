package dev.touchxbox.pad;

import android.content.Context;
import java.net.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class BridgeClient implements AutoCloseable {
    private final Socket socket=new Socket();private final Context context;
    public BridgeClient(Context c){context=c;}
    private DataInputStream in;private DataOutputStream out;private ShizukuInput.Session local;
    public void connect() throws Exception {
        if(ShizukuInput.selected(context)){local=ShizukuInput.connect(context);return;}
        String token=context.getSharedPreferences("bridge",0).getString("token","");
        if(token.length()!=64)throw new IOException("请先通过 activate.ps1 激活");
        socket.connect(new InetSocketAddress("127.0.0.1",BridgeDaemon.PORT),1500);socket.setSoTimeout(3500);socket.setTcpNoDelay(true);
        in=new DataInputStream(socket.getInputStream());out=new DataOutputStream(socket.getOutputStream());
        BridgeAuth.client(in,out,BridgeAuth.decode(token));
    }
    public String command(String command) throws Exception {if(local!=null){if(command.equals("PROBE"))return local.probe();throw new IOException("不支持的本机服务命令");}out.writeUTF(command);out.flush();return in.readUTF();}
    public void start() throws Exception {start(0,false);}
    public void start(int protocol,boolean keyboard) throws Exception {connect();if(local!=null){local.start(protocol,keyboard);return;}out.writeUTF("START_V2");out.writeByte(protocol);out.writeBoolean(keyboard);out.flush();String reply=in.readUTF();if(!reply.equals("OK"))throw new IOException(reply);socket.setSoTimeout(900);}
    public void send(byte[] report) throws Exception {if(local!=null){local.send(report);return;}out.write(report);out.flush();if(in.readUnsignedByte()!=1)throw new IOException("UHID 写入失败");}
    @Override public void close(){if(local!=null)local.close();try{socket.close();}catch(Exception ignored){}}
}
