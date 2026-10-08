package dev.touchxbox.pad;

import java.util.LinkedHashMap;

/** Saved auxiliary bindings: 0 releases, 1..16 sparse Xbox button bit + 1,
 * 32..35 D-pad, 36..37 full triggers, 256 + HID usage for keyboard keys. */
public final class FpvAuxMapping {
    public static final int UP=32,RIGHT=33,DOWN=34,LEFT=35,LT=36,RT=37,KEYBOARD=256;
    public static final LinkedHashMap<Integer,String> BUTTONS=new LinkedHashMap<>();
    static{
        int[] bits={PadState.A,PadState.B,PadState.X,PadState.Y,PadState.LB,PadState.RB,PadState.BACK,PadState.START,PadState.GUIDE,PadState.LS,PadState.RS};
        String[] names={"A","B","X","Y","LB","RB","View / Back","Menu / Start","Xbox / Guide","L3","R3"};
        for(int i=0;i<bits.length;i++)BUTTONS.put(bits[i]+1,names[i]);
        BUTTONS.put(UP,"十字键 ↑");BUTTONS.put(RIGHT,"十字键 →");BUTTONS.put(DOWN,"十字键 ↓");BUTTONS.put(LEFT,"十字键 ←");BUTTONS.put(LT,"LT（按下满值）");BUTTONS.put(RT,"RT（按下满值）");
    }
    private FpvAuxMapping(){}
    public static boolean keyboard(int binding){return binding>=KEYBOARD&&KeyboardKeys.valid(binding-KEYBOARD);}
    public static boolean valid(int binding){return binding==0||BUTTONS.containsKey(binding)||keyboard(binding);}
    public static String name(int binding){return binding==0?"不按键":keyboard(binding)?"键盘 "+KeyboardKeys.name(binding-KEYBOARD):"Xbox "+BUTTONS.get(binding);}
    public static String defaultName(int channel){return channel==4?"LT 三档":channel==5?"RT 三档":channel==6?"十字键 左 / 松开 / 右":"十字键 下 / 松开 / 上";}
    public static int buttonMask(int binding){return binding>0&&binding<=16?1<<(binding-1):0;}
    public static int hatX(int binding){return binding==LEFT?-1:binding==RIGHT?1:0;}
    public static int hatY(int binding){return binding==UP?-1:binding==DOWN?1:0;}
}
