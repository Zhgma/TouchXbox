package dev.touchxbox.pad;

/** Protocols implemented by this app, not identities assigned to the same report. */
public final class ControllerProtocol {
    public static final int XBOX=0, SWITCH_PRO=1, DS4=2, FPV=3;
    public static final String[] NAMES={"Xbox 360", "NS Pro · 体感报告", "PS / DualShock 4 · 体感", "FPV · USB HID 遥控器"};
    public static int valid(int value){return value>=0&&value<NAMES.length?value:XBOX;}
    public static int size(int mode){return mode==SWITCH_PRO?49:mode==DS4?64:mode==FPV?18:15;}
    /** Canonical controls keep stable saved IDs; labels describe the actual wire input. */
    public static String label(int mode,String key){
        if(mode==SWITCH_PRO){switch(key){case "A":return "B";case "B":return "A";case "X":return "Y";case "Y":return "X";case "LT":return "ZL";case "RT":return "ZR";case "LB":return "L";case "RB":return "R";case "L":return "左摇杆";case "R":return "右摇杆";case "VIEW":return "−";case "MENU":return "+";case "XBOX":return "截图";}}
        if(mode==DS4){switch(key){case "A":return "×";case "B":return "○";case "X":return "□";case "Y":return "△";case "LT":return "L2";case "RT":return "R2";case "LB":return "L1";case "RB":return "R1";case "L":return "L3";case "R":return "R3";case "VIEW":return "SHARE";case "MENU":return "OPTIONS";case "XBOX":return "PS";case "ABXY":return "△ ○ × □";}}
        if(mode==FPV){switch(key){case "L":return "油门 / 偏航";case "R":return "横滚 / 俯仰";}}
        return key.equals("D")?"方向键":key;
    }
    public static boolean matches(int vendor,int product){return vendor==0x045e&&product==0x028e||vendor==0x057e&&product==0x2009||vendor==0x054c&&product==0x05c4||vendor==0x1209&&product==0x5459;}
    public static boolean matches(int mode,int vendor,int product){return mode==0?vendor==0x045e&&product==0x028e:mode==1?vendor==0x057e&&product==0x2009:mode==2?vendor==0x054c&&product==0x05c4:vendor==0x1209&&product==0x5459;}
}
