import dev.touchxbox.pad.*;

/** FPV gestures must retain square travel and throttle while using Xbox wire reports. */
public final class FpvXboxTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int axis(byte[] b,int n){return (b[n*2]&255)|((b[n*2+1]&255)<<8);}
    private static void neutral(PadState s){byte[] b=s.report();check(b.length==15&&axis(b,0)==32768&&axis(b,1)==65535&&axis(b,2)==32768&&axis(b,3)==32768,"Xbox FPV idle has minimum throttle and centered flight axes");}
    public static void main(String[] args)throws Exception{
        LayoutProfile p=new LayoutProfile();p.protocol=ControllerProtocol.FPV;
        check(!p.fpvXbox&&p.outputProtocol()==ControllerProtocol.FPV,"Native FPV remains default");
        p.fpvXbox=true;check(p.outputProtocol()==ControllerProtocol.XBOX&&p.protocol==ControllerProtocol.FPV,"Output and layout are independent");
        for(int mode:new int[]{2,1,3}){
            p.fpvMode=mode;
            // Physical LX, LY, RX, RY -> canonical Xbox LX, LY, RX, RY.
            int[] destinations=mode==2?new int[]{0,1,2,3}:mode==1?new int[]{0,3,2,1}:new int[]{2,3,0,1};
            PadState state=new PadState(p.protocol,p.fpvMode);neutral(state);
            for(boolean floating:new boolean[]{false,true})for(boolean right:new boolean[]{false,true})for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}){
                state.reset();LayoutProfile.Spec spec=LayoutProfile.spec(right?"R":"L");
                check(p.squareStick(spec),"Xbox transport cannot change FPV square geometry");
                StickGesture g=new StickGesture(p.throttle(spec),p.squareStick(spec));float cx=floating?800:200,cy=floating?600:200;
                g.begin(cx,cy,cx,cy,100,12,0);g.move(cx+x*100,cy+(p.throttle(spec)?y-1:y)*100);
                state.stick(right,g.x,g.y);byte[] b=state.report();int offset=right?2:0;
                check(axis(b,destinations[offset])==(x<0?0:65535)&&axis(b,destinations[offset+1])==(y<0?0:65535),"Both physical axes reach their full endpoint together: mode "+mode);
                g.end();state.stick(right,g.x,g.y);b=state.report();
                check(axis(b,destinations[offset])==32768,"Horizontal axis recenters");
                check(axis(b,destinations[offset+1])==(p.throttle(spec)?(y<0?0:65535):32768),"Only throttle retains its endpoint");
                if(p.throttle(spec)){g.begin(cx+300,cy+300,cx+300,cy+300,100,12,1000);state.stick(right,g.x,g.y);check(axis(state.report(),1)==(y<0?0:65535),"Next touch retains throttle");}
                state.reset();neutral(state);
            }
            for(int level=0;level<3;level++){
                state.auxiliary(4,level);state.auxiliary(5,level);byte[] b=state.report();
                check(axis(b,4)==Math.round(level/2f*32767)&&axis(b,5)==Math.round(level/2f*32767),"CH5/6 retain three levels on Xbox triggers");
            }
            state.reset();state.auxiliary(6,2);state.auxiliary(7,2);check((state.report()[14]&255)==2,"CH7/8 combine as diagonal D-pad");
            state.auxiliary(6,1);state.auxiliary(7,1);check(state.report()[14]==0,"Auxiliary midpoint releases D-pad");
            state.keyboard("test",4,true);state.button(PadState.A,true);state.configure(p.protocol,mode);neutral(state);
            byte[] frame=state.nextFrame(0);check(frame[12]==0&&frame[13]==0&&frame[15]==0,"Reconfigure releases stale keys");
        }
        for(int protocol:new int[]{0,1,2}){p.protocol=protocol;check(p.outputProtocol()==protocol,"FPV-only switch cannot override other protocols");check(!p.squareStick(LayoutProfile.spec("L")),"Other layouts unchanged");}
        System.out.println("PASS: FPV Xbox output, all modes/corners, throttle startup/retention/reset, auxiliary mapping and other-protocol isolation");
    }
}
