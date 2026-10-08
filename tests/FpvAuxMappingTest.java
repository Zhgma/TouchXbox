import dev.touchxbox.pad.*;

public final class FpvAuxMappingTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static boolean key(byte[] frame,int usage){return (frame[15+usage/8]&(1<<(usage%8)))!=0;}
    private static int word(byte[] frame,int offset){return (frame[offset]&255)|((frame[offset+1]&255)<<8);}
    private static void released(byte[] f){check(word(f,12)==0&&f[14]==0,"Gamepad bindings released");for(int i=15;i<47;i++)check(f[i]==0,"Keyboard bindings released");}
    public static void main(String[] args)throws Exception{
        LayoutProfile p=new LayoutProfile();p.protocol=3;p.fpvXbox=true;
        p.fpvXboxAux.put(4,new int[]{PadState.A+1,FpvAuxMapping.KEYBOARD+44,FpvAuxMapping.KEYBOARD+4});
        p.fpvXboxAux.put(5,new int[]{0,0,FpvAuxMapping.KEYBOARD+4});
        p.fpvXboxAux.put(6,new int[]{0,0,PadState.A+1});
        p.fpvXboxAux.put(7,new int[]{FpvAuxMapping.LEFT,0,FpvAuxMapping.UP});
        check(p.keyboard.isEmpty()&&p.needsKeyboard(),"Auxiliary-only keyboard mapping creates keyboard transport");
        PadState state=new PadState();state.configure(p);byte[] f=state.nextFrame(0);released(f);check(word(f,8)==0&&word(f,10)==0,"Custom channels suppress default trigger outputs at startup");
        state.auxiliary(4,2);f=state.nextFrame(0);check(key(f,4)&&!key(f,44),"High enters keyboard A");
        state.auxiliary(4,0);f=state.nextFrame(0);check(!key(f,4)&&(word(f,12)&1)!=0,"Switch atomically releases keyboard and presses Xbox A");
        state.auxiliary(6,2);state.nextFrame(0);state.auxiliary(4,1);f=state.nextFrame(0);check((word(f,12)&1)!=0&&key(f,44),"Another CH keeps shared Xbox button pressed");
        state.resetAuxiliary(6);f=state.nextFrame(0);check(word(f,12)==0&&key(f,44),"Releasing one CH leaves independent keyboard key held");
        state.resetAuxiliary(4);released(state.nextFrame(0));
        state.auxiliary(4,2);state.nextFrame(0);state.auxiliary(5,2);state.nextFrame(0);state.resetAuxiliary(4);check(key(state.nextFrame(0),4),"One channel cannot release another channel's same key");
        state.keyboard("screen-key",4,true);state.nextFrame(0);state.resetAuxiliary(5);check(key(state.nextFrame(0),4),"Physical on-screen keyboard owner remains pressed");
        state.keyboard("screen-key",4,false);released(state.nextFrame(0));
        state.auxiliary(7,2);check(state.nextFrame(0)[14]==1,"Custom D-pad up");state.auxiliary(7,0);check(state.nextFrame(0)[14]==7,"Custom D-pad left");
        state.reset();released(state.nextFrame(0));state.resetAuxiliary(4);released(state.nextFrame(0));check(word(state.report(),2)==65535,"Collapse cannot re-press mapped middle key or raise throttle");
        p.fpvXboxAux.put(4,new int[]{0,0,FpvAuxMapping.LT});state.configure(p);state.nextFrame(0);state.auxiliary(4,2);check(word(state.nextFrame(0),8)==32767,"Mapped trigger reaches full value");state.auxiliary(4,0);check(word(state.nextFrame(0),8)==0,"Mapped trigger releases");
        // Runtime mapping is a snapshot; editing a draft cannot change a running held key.
        p.fpvXboxAux.get(4)[2]=PadState.B+1;state.auxiliary(4,2);f=state.nextFrame(0);check(word(f,8)==32767&&word(f,12)==0,"Runtime binding copies are isolated");
        p.fpvXbox=false;check(!p.needsKeyboard(),"Native FPV ignores auxiliary Xbox keyboard bindings");state.configure(p);state.nextFrame(0);state.auxiliary(4,2);f=state.nextFrame(0);released(f);check(word(f,8)==32767,"Native FPV auxiliary channel preserved");
        p.protocol=0;p.fpvXbox=true;check(!p.needsKeyboard(),"Other layout protocols ignore FPV mapping");
        for(int invalid:new int[]{-1,3,17,31,38,255,256,512,999})check(!FpvAuxMapping.valid(invalid),"Invalid binding rejected "+invalid);
        System.out.println("PASS: CH gamepad/keyboard transitions, shared owners, neutral/reset/reconfigure release, trigger/D-pad and keyboard-only transport");
    }
}
