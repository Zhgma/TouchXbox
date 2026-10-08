package dev.touchxbox.pad;
import java.io.*;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
/** Mutual challenge-response. Fresh nonces and role separation prevent replay/reflection. */
public final class BridgeAuth {
    public static byte[] decode(String s)throws IOException{if(!s.matches("[0-9a-fA-F]{64}"))throw new IOException("Invalid activation key");byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
    private static byte[] nonce(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return b;}
    private static byte[] mac(byte[] k,byte role,byte[] a,byte[] b)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(k,"HmacSHA256"));m.update(role);m.update(a);return m.doFinal(b);}
    public static void client(DataInputStream in,DataOutputStream out,byte[] key)throws Exception{
        byte[] a=nonce(),b=new byte[32],proof=new byte[32];out.write(a);out.flush();in.readFully(b);in.readFully(proof);
        if(!MessageDigest.isEqual(proof,mac(key,(byte)1,a,b)))throw new IOException("桥接身份验证失败，请重新激活");
        out.write(mac(key,(byte)2,a,b));out.flush();if(in.readUnsignedByte()!=1)throw new IOException("Client authentication rejected");
    }
    public static void server(DataInputStream in,DataOutputStream out,byte[] key)throws Exception{
        byte[] a=new byte[32],b=nonce(),proof=new byte[32];in.readFully(a);out.write(b);out.write(mac(key,(byte)1,a,b));out.flush();in.readFully(proof);
        if(!MessageDigest.isEqual(proof,mac(key,(byte)2,a,b)))throw new IOException("Authentication rejected");out.writeByte(1);out.flush();
    }
}
