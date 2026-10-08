import dev.touchxbox.pad.*;
/** Shell-only, bounded integration probe. Closes its device after the requested duration. */
public final class NintendoProbe {
    public static void main(String[] args)throws Exception{
        long end=android.os.SystemClock.uptimeMillis()+(args.length>0?Integer.parseInt(args[0]):15000);
        int protocol=args.length>1?Integer.parseInt(args[1]):1;
        try(UhidDevice device=new UhidDevice(protocol)){
            System.out.println("Nintendo UHID started; waiting for kernel input and IMU registration");
            PadState state=new PadState();short[][] samples={{0,0,4096,0,0,0},{0,0,4096,0,0,0},{0,0,4096,0,0,0}};
            while(android.os.SystemClock.uptimeMillis()<end){device.report(protocol==1?SwitchProCodec.report(state.report(),samples,android.os.SystemClock.uptimeMillis()):Ds4Codec.report(state.report(),samples[0],android.os.SystemClock.elapsedRealtimeNanos()));Thread.sleep(15);}
        }System.out.println("Nintendo probe closed");
    }
}
