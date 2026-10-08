package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;

/** Checks real app-data and authenticated shell-server retention across package replacement. */
public final class UpdateRetentionTest extends Instrumentation {
    private String phase;
    @Override public void onCreate(Bundle args){phase=args.getString("phase","check");start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context c=getTargetContext();if(!c.getPackageName().equals("dev.touchxbox.pad.qa057"))throw new AssertionError("Refuse production package");
        if(phase.equals("seed")){
            byte[] random=new byte[32];new SecureRandom().nextBytes(random);StringBuilder key=new StringBuilder();for(byte b:random)key.append(String.format(java.util.Locale.US,"%02x",b&255));
            c.getSharedPreferences("bridge",0).edit().putString("token",key.toString()).commit();
            c.getSharedPreferences("connection",0).edit().putBoolean("shizuku",false).commit();
            try(FileOutputStream out=c.openFileOutput("update-test.key",Context.MODE_PRIVATE)){out.write(key.toString().getBytes("US-ASCII"));}
            LayoutProfile p=new LayoutProfile();p.name="升级保留测试";if(new LayoutStore(c).save(p)!=null)throw new AssertionError("Save template");
        }else{
            if(!BridgeClient.hasActivationKey(c)||ShizukuInput.selected(c))throw new AssertionError("Saved authorization/backend was lost");
            if(!new LayoutStore(c).active().name.equals("升级保留测试"))throw new AssertionError("Saved template was lost");
            try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress("127.0.0.1",37685),2000);socket.setSoTimeout(4000);
                DataInputStream in=new DataInputStream(socket.getInputStream());DataOutputStream out=new DataOutputStream(socket.getOutputStream());
                BridgeAuth.client(in,out,BridgeAuth.decode(c.getSharedPreferences("bridge",0).getString("token","")));
                out.writeUTF("PROBE");out.flush();String reply=in.readUTF();if(!reply.startsWith("OK:"))throw new AssertionError(reply);
            }
        }
        result.putString("stream","PASS: "+phase+"; template, saved key, authenticated bridge and UHID probe. No controller created.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
}
