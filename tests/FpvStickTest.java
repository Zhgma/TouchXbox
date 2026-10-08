import dev.touchxbox.pad.*;

/** Touch displacement -> gesture -> radio HID channels, for fixed and following origins. */
public final class FpvStickTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.0001f,message+": "+actual);}
    private static int channel(byte[] report,int index){int offset=index*2;return (report[offset]&255)|((report[offset+1]&255)<<8);}
    private static StickGesture gesture(LayoutProfile profile,String name){LayoutProfile.Spec s=LayoutProfile.spec(name);return new StickGesture(profile.throttle(s),profile.squareStick(s));}
    private static byte[] report(StickGesture stick){PadState state=new PadState(ControllerProtocol.FPV);state.stick(true,stick.x,stick.y);return FpvCodec.report(state.report());}

    public static void main(String[] args){
        LayoutProfile profile=new LayoutProfile();profile.protocol=ControllerProtocol.FPV;
        for(boolean floating:new boolean[]{false,true}){
            profile.rightFloating=floating;
            float cx=floating?821:140,cy=floating?477:140,range=100;
            StickGesture right=gesture(profile,"R");
            for(int dx:new int[]{-1,1})for(int dy:new int[]{-1,1}){
                right.begin(cx,cy,cx,cy,range,12,0);
                right.move(cx+dx*range,cy+dy*range);
                near(right.x,dx,"Corner must reach full roll");near(right.y,dy,"Corner must reach full pitch");
                byte[] hid=report(right);
                check(channel(hid,0)==(dx<0?0:65535),"CH1 full range at diagonal");
                check(channel(hid,1)==(dy<0?65535:0),"CH2 full range at diagonal, preserving pitch inversion");
                check(channel(hid,2)==0,"Right stick cannot raise initial throttle");
                check(!right.hold(1000),"Ordinary diagonal drag cannot click stick");
                right.end();near(right.x,0,"Release centers roll");near(right.y,0,"Release centers pitch");
                hid=report(right);check(channel(hid,0)==32768&&channel(hid,1)==32768,"Both right channels centered after release");
            }
            right.begin(cx,cy,cx,cy,range,12,2000);
            right.move(cx+80,cy-80);near(right.x,.8f,"Diagonal roll stays linear");near(right.y,-.8f,"Diagonal pitch stays linear");
            right.move(cx+300,cy+40);near(right.x,1,"Roll limit clamps independently");near(right.y,.4f,"Full roll cannot reduce pitch");
            right.move(cx-30,cy-300);near(right.x,-.3f,"Full pitch cannot reduce roll");near(right.y,-1,"Pitch limit clamps independently");
            right.move(cx+4,cy+50);near(right.x,0,"Roll dead zone");near(right.y,.5f,"Roll dead zone cannot zero pitch");
            right.move(cx+50,cy+4);near(right.x,.5f,"Pitch dead zone cannot zero roll");near(right.y,0,"Pitch dead zone");
            right.reset();near(right.x,0,"Reset centers roll");near(right.y,0,"Reset centers pitch");
            right.begin(cx,cy,cx,cy,range,12,3000);check(right.hold(3500),"Stationary right long press remains available");
            right.move(cx+range,cy-range);check(right.clicked,"Dragging after a long press preserves click");right.end();check(!right.clicked,"Lift releases right click");
        }
        // Fixed control uses its saved center even if touched away from the center.
        StickGesture fixed=gesture(profile,"R");fixed.begin(180,60,100,100,100,12,0);near(fixed.x,.8f,"Fixed origin X");near(fixed.y,-.4f,"Fixed origin Y");
        StickGesture left=gesture(profile,"L");near(left.y,1,"Throttle starts at bottom");left.begin(500,400,500,400,100,12,0);left.move(600,200);near(left.x,1,"Left yaw still full at corner");near(left.y,-1,"Left throttle still full at corner");left.end();near(left.x,0,"Yaw recenters");near(left.y,-1,"Throttle remains held");left.begin(800,700,800,700,100,12,1000);near(left.y,-1,"New origin preserves throttle");left.move(800,750);near(left.y,-.5f,"Throttle continues from prior position");
        for(int protocol:new int[]{ControllerProtocol.XBOX,ControllerProtocol.SWITCH_PRO,ControllerProtocol.DS4}){
            profile.protocol=protocol;
            for(String name:new String[]{"L","R"}){
                StickGesture regular=gesture(profile,name);regular.begin(0,0,0,0,100,12,0);regular.move(100,100);
                near(regular.x,(float)(1/Math.sqrt(2)),"Other protocols retain circular roll limit");
                near(regular.y,(float)(1/Math.sqrt(2)),"Other protocols retain circular pitch limit");
                regular.end();near(regular.y,0,"Other protocols recenter Y");
            }
        }
        System.out.println("PASS: FPV square corners and independent axes -> full CH1/CH2 HID values; fixed/floating, recenter, hold, throttle and circular-protocol regressions");
    }
}
