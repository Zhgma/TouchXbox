package dev.touchxbox.pad;
import java.io.*;
import java.net.*;
public final class BridgeRetryTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(Throwable e:new Throwable[]{new ConnectException(),new SocketException(),new SocketTimeoutException(),new EOFException(),new IOException("connect",new ConnectException())}){
            check(BridgeRetry.allowed(e,0)&&BridgeRetry.allowed(e,1),"Transient transport retries");check(!BridgeRetry.allowed(e,2),"Bounded attempts");
        }
        check(!BridgeRetry.allowed(new IOException("Authentication rejected"),0),"Wrong key is never retried");
        check(!BridgeRetry.allowed(new IOException("Unsupported protocol"),0),"Rejected protocol is never retried");
        check(!BridgeRetry.allowed(new SecurityException(),0)&&!BridgeRetry.allowed(null,0),"Permission failures are not transient");
        System.out.println("PASS: bounded reconnect for transient transport only; authentication/protocol/permission failures stay terminal");
    }
}
