import dev.touchxbox.pad.BridgeAuth;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
public class AuthTest {
    public static void main(String[] args)throws Exception{
        byte[] key=new byte[32];key[0]=42;
        for(boolean valid:new boolean[]{true,false})try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            AtomicBoolean accepted=new AtomicBoolean();Thread t=new Thread(()->{try(Socket s=listener.accept()){s.setSoTimeout(1000);BridgeAuth.server(new DataInputStream(s.getInputStream()),new DataOutputStream(s.getOutputStream()),key);accepted.set(true);}catch(Exception expected){}});t.start();
            boolean clientAccepted=false;try(Socket s=new Socket(InetAddress.getLoopbackAddress(),listener.getLocalPort())){s.setSoTimeout(1000);BridgeAuth.client(new DataInputStream(s.getInputStream()),new DataOutputStream(s.getOutputStream()),valid?key:new byte[32]);clientAccepted=true;}catch(IOException expected){}
            t.join(2000);if(accepted.get()!=valid||clientAccepted!=valid)throw new AssertionError("Mutual auth valid="+valid);
        }
        System.out.println("PASS: both peers authenticate; wrong activation key rejected before commands");
    }
}
