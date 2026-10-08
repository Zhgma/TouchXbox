import dev.touchxbox.pad.*;

public final class FpvModeTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int channel(PadState state,int index){byte[] report=FpvCodec.report(state.report());return (report[index*2]&255)|((report[index*2+1]&255)<<8);}
    private static void neutral(PadState state){for(int ch=0;ch<4;ch++)check(channel(state,ch)==(ch==2?0:32768),"Idle CH"+(ch+1));}
    private static StickGesture gesture(LayoutProfile p,boolean right){LayoutProfile.Spec s=LayoutProfile.spec(right?"R":"L");return new StickGesture(p.throttle(s),p.squareStick(s));}
    public static void main(String[] args)throws Exception{
        check(new LayoutProfile().fpvMode==FpvMode.AMERICAN,"Existing behavior remains the default");
        for(int mode:new int[]{FpvMode.AMERICAN,FpvMode.JAPANESE,FpvMode.CHINESE}){
            LayoutProfile p=new LayoutProfile();p.protocol=ControllerProtocol.FPV;p.fpvMode=mode;
            // Expected channel destinations for physical LX, LY, RX, RY (zero-based).
            int[] expected=mode==2?new int[]{3,2,0,1}:mode==1?new int[]{3,1,0,2}:new int[]{0,1,3,2};
            boolean throttleRight=mode!=2;
            check(p.throttle(LayoutProfile.spec(throttleRight?"R":"L")),"Correct side owns throttle");
            check(!p.throttle(LayoutProfile.spec(throttleRight?"L":"R")),"Other side recenters");
            check(p.label(LayoutProfile.spec(throttleRight?"R":"L")).contains("油门"),"Visible label follows throttle");
            PadState state=new PadState(ControllerProtocol.FPV,mode);neutral(state);
            for(int axis=0;axis<4;axis++)for(int extreme:new int[]{-1,1}){
                state.reset();boolean right=axis>=2;float x=axis%2==0?extreme:0,y=right==throttleRight?1:0;
                if(axis%2==1)y=extreme;state.stick(right,x,y);
                for(int ch=0;ch<4;ch++){
                    int value=ch==expected[axis]?(axis%2==0?(extreme<0?0:65535):(extreme<0?65535:0)):(ch==2?0:32768);
                    check(channel(state,ch)==value,"Mode "+mode+" axis "+axis+" must affect only CH"+(expected[axis]+1));
                }
            }
            for(boolean floating:new boolean[]{false,true})for(boolean right:new boolean[]{false,true}){
                state.reset();StickGesture stick=gesture(p,right);
                float cx=floating?800:100,cy=floating?500:100;
                check(stick.y==(right==throttleRight?1:0),"Initial knob position");
                stick.begin(cx,cy,cx,cy,100,12,0);stick.move(cx+100,cy-200);state.stick(right,stick.x,stick.y);
                check(channel(state,expected[right?2:0])==65535&&channel(state,expected[right?3:1])==65535,"Both physical axes reach full value at square corner");
                stick.end();state.stick(right,stick.x,stick.y);
                check(channel(state,expected[right?2:0])==32768,"Horizontal axis always recenters");
                check(channel(state,expected[right?3:1])==(right==throttleRight?65535:32768),"Only throttle holds after release");
                if(right==throttleRight){
                    stick.begin(cx+240,cy+300,cx+240,cy+300,100,12,1000);state.stick(right,stick.x,stick.y);
                    check(channel(state,2)==65535,"New touch preserves prior throttle");
                    stick.move(cx+240,cy+400);state.stick(right,stick.x,stick.y);check(channel(state,2)==32768,"Throttle continues from prior position");
                    StickGesture other=gesture(p,!right);other.begin(10,10,10,10,100,12,2000);other.move(60,40);state.stick(!right,other.x,other.y);other.end();state.stick(!right,other.x,other.y);
                    check(channel(state,2)==32768,"Other stick cannot reset retained throttle");
                }
                stick.reset();state.reset();neutral(state);
            }
            state.stick(throttleRight,1,-1);state.button(PadState.A,true);state.keyboard("key",4,true);
            state.configure(ControllerProtocol.FPV,mode==2?3:2);neutral(state);
            byte[] frame=state.nextFrame(0);check(frame[12]==0&&frame[13]==0&&frame[15]==0,"Switching mode clears stale buttons and keyboard");
            p.labels.put("L",new String[]{"自定义","自定义高亮"});check(p.label(LayoutProfile.spec("L")).equals("自定义"),"Custom names are retained");
        }
        for(int mode:new int[]{-1,0,4,99}){neutral(new PadState(ControllerProtocol.FPV,mode));check(FpvMode.valid(mode)==FpvMode.AMERICAN,"Unknown mode defaults to American");}
        for(int protocol:new int[]{ControllerProtocol.XBOX,ControllerProtocol.SWITCH_PRO,ControllerProtocol.DS4}){
            PadState ordinary=new PadState(protocol),withSavedFpvMode=new PadState(protocol,FpvMode.CHINESE);
            ordinary.stick(false,.2f,-.6f);withSavedFpvMode.stick(false,.2f,-.6f);
            check(java.util.Arrays.equals(ordinary.report(),withSavedFpvMode.report()),"Other protocols ignore FPV stick mode");
        }
        System.out.println("PASS: three FPV modes, physical-axis to HID mapping, square corners, retained throttle, new touches, reset and other-protocol isolation");
    }
}
