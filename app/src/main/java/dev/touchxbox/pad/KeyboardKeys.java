package dev.touchxbox.pad;
import java.util.*;

/** USB HID keyboard usages. Display names never participate in the input mapping. */
public final class KeyboardKeys {
    public static final int VENDOR=0x1209,PRODUCT=0x5458;
    public static final String DEVICE_NAME="TouchXbox Keyboard";
    public static final LinkedHashMap<Integer,String> KEYS=new LinkedHashMap<>();
    static {
        for(int i=0;i<26;i++)KEYS.put(4+i,String.valueOf((char)('A'+i)));
        for(int i=0;i<9;i++)KEYS.put(30+i,String.valueOf(i+1));KEYS.put(39,"0");
        String[] names={"Enter","Esc","Backspace","Tab","Space","-","=","[","]","\\","#",";","'","`",",",".","/","Caps Lock"};for(int i=0;i<names.length;i++)KEYS.put(40+i,names[i]);
        for(int i=0;i<12;i++)KEYS.put(58+i,"F"+(i+1));
        String[] nav={"Print Screen","Scroll Lock","Pause","Insert","Home","Page Up","Delete","End","Page Down","Right","Left","Down","Up"};for(int i=0;i<nav.length;i++)KEYS.put(70+i,nav[i]);
        String[] modifiers={"Left Ctrl","Left Shift","Left Alt","Left Meta","Right Ctrl","Right Shift","Right Alt","Right Meta"};for(int i=0;i<8;i++)KEYS.put(224+i,modifiers[i]);
    }
    public static boolean valid(int code){return KEYS.containsKey(code);}
    public static String name(int code){return KEYS.getOrDefault(code,"Key "+code);}
    public static byte[] descriptor(){int[] v={5,1,9,6,0xa1,1,5,7,0x19,0,0x2a,255,0,0x15,0,0x25,1,0x75,1,0x96,0,1,0x81,2,0xc0};byte[] b=new byte[v.length];for(int i=0;i<v.length;i++)b[i]=(byte)v[i];return b;}
}
