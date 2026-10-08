import dev.touchxbox.pad.PadState;
import dev.touchxbox.pad.HidDescriptor;
import java.util.Arrays;
public final class ProtocolTest {
    static int u16(byte[] b,int i){return (b[i]&255)|((b[i+1]&255)<<8);}
    static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        PadState s=new PadState();byte[] n=s.report();check(n.length==15,"15-byte report");
        for(int i=0;i<8;i+=2)check(u16(n,i)==32768,"centered stick");check(u16(n,8)==0&&u16(n,10)==0&&u16(n,12)==0&&n[14]==0,"neutral controls");
        s.stick(false,-1,1);s.stick(true,1,-1);s.trigger(false,.5f);s.trigger(true,1);s.button(PadState.A,true);s.button(PadState.X,true);s.button(PadState.LS,true);s.hat(8);
        byte[] b=s.report();check(u16(b,0)==0&&u16(b,2)==65535&&u16(b,4)==65535&&u16(b,6)==0,"stick extrema");check(u16(b,8)==16384&&u16(b,10)==32767,"independent analog triggers");check(u16(b,12)==0x2009,"Xbox sparse Linux button mapping");check(b[14]==8,"diagonal hat");
        s.button(PadState.A,false);check(u16(s.report(),12)==0x2008,"release one simultaneous button");s.reset();check(Arrays.equals(s.report(),n),"reset all inputs");
        int[][] dirs={{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1}};for(int i=0;i<8;i++)check(PadState.direction(dirs[i][0],dirs[i][1])==i+1,"hat direction "+i);check(PadState.direction(.1f,.1f)==0,"hat deadzone");
        // Parse HID short items independently, verify that report size really is 120 bits.
        byte[] d=HidDescriptor.bytes();int size=0,count=0,bits=0;for(int i=0;i<d.length;){int tag=d[i++]&255,len=tag&3;if(len==3)len=4;int value=0;for(int j=0;j<len;j++)value|=(d[i++]&255)<<(8*j);if(tag==0x75)size=value;if(tag==0x95)count=value;if((tag&0xFC)==0x80)bits+=size*count;}check(bits==120,"descriptor/report bit count: "+bits);
        PadState taps=new PadState();taps.button(PadState.A,true);taps.button(PadState.A,false);check(u16(taps.nextReport(1),12)==1,"short tap down preserved");check(u16(taps.nextReport(1),12)==0,"short tap release preserved");
        System.out.println("PASS: neutral, extrema, triggers, sparse buttons, simultaneous release, reset, 8-way hat, descriptor length, short taps");
    }
}
