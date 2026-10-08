package dev.touchxbox.pad;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/** Retry transport interruption only; never turn an authentication or protocol error into a retry loop. */
final class BridgeRetry {
    static final int MAX_RETRIES=2;
    static boolean allowed(Throwable failure,int previousRetries){
        if(previousRetries>=MAX_RETRIES)return false;
        for(int depth=0;failure!=null&&depth<8;depth++,failure=failure.getCause())
            if(failure instanceof SocketException||failure instanceof SocketTimeoutException||failure instanceof EOFException)return true;
        return false;
    }
}
