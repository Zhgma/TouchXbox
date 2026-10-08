import dev.touchxbox.pad.*;

/** ADB shell QA: real UHID reports, observed independently by Android TesterActivity. */
public final class FpvXboxDeviceTest {
    private static void send(UhidDevice device,PadState state,String label)throws Exception{
        android.util.Log.i("FpvXboxDeviceTest",label);device.report(state.report());Thread.sleep(450);
    }
    private static void send(UhidDevice device,UhidDevice keys,PadState state,String label)throws Exception{
        android.util.Log.i("FpvXboxDeviceTest",label);byte[] frame=state.nextFrame(0);device.report(java.util.Arrays.copyOf(frame,15));keys.report(java.util.Arrays.copyOfRange(frame,15,47));Thread.sleep(300);
    }
    public static void main(String[] args)throws Exception{
        for(int mode:new int[]{2,1,3}){
            LayoutProfile p=new LayoutProfile();p.protocol=3;p.fpvXbox=true;p.fpvMode=mode;
            PadState s=new PadState();s.configure(p);
            try(UhidDevice device=new UhidDevice(p.outputProtocol())){
                Thread.sleep(900);send(device,s,"MODE "+mode+" START minimum throttle");
                for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}){s.stick(false,x,y);s.stick(true,x,y);send(device,s,"MODE "+mode+" CORNER "+x+","+y);}
                s.reset();send(device,s,"MODE "+mode+" RESET minimum throttle");
            }
            Thread.sleep(300);
        }
        LayoutProfile p=new LayoutProfile();p.protocol=3;p.fpvXbox=true;p.fpvXboxAux.put(4,new int[]{PadState.A+1,0,FpvAuxMapping.KEYBOARD+44});p.fpvXboxAux.put(5,new int[]{0,0,FpvAuxMapping.KEYBOARD+44});
        PadState s=new PadState();s.configure(p);
        try(UhidDevice device=new UhidDevice(p.outputProtocol());UhidDevice keys=new UhidDevice(4,KeyboardKeys.descriptor(),KeyboardKeys.DEVICE_NAME,KeyboardKeys.VENDOR,KeyboardKeys.PRODUCT,32)){
            Thread.sleep(900);send(device,keys,s,"BINDINGS START no keys");
            s.auxiliary(4,2);send(device,keys,s,"BINDINGS CH5 high Space down");
            s.auxiliary(5,2);send(device,keys,s,"BINDINGS CH6 high shared Space");
            s.auxiliary(4,1);send(device,keys,s,"BINDINGS CH5 middle Space remains held by CH6");
            s.auxiliary(5,1);send(device,keys,s,"BINDINGS CH6 middle Space up");
            s.auxiliary(4,0);send(device,keys,s,"BINDINGS CH5 low Xbox A down");
            s.auxiliary(4,1);send(device,keys,s,"BINDINGS CH5 middle Xbox A up");
            s.auxiliary(4,2);send(device,keys,s,"BINDINGS CH5 high Space down again");
            s.reset();send(device,keys,s,"BINDINGS RESET all keys up");
        }
        System.out.println("DONE: Xbox corners, startup/reset and custom gamepad/keyboard sessions closed");
    }
}
