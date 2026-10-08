package dev.touchxbox.pad;

/** Physical stick modes. The USB report always keeps CH1 roll, CH2 pitch, CH3 throttle, CH4 yaw. */
public final class FpvMode {
    public static final int JAPANESE=1,AMERICAN=2,CHINESE=3;
    private FpvMode(){}
    public static int valid(int mode){return mode>=1&&mode<=3?mode:AMERICAN;}
    public static boolean throttleRight(int mode){return valid(mode)!=AMERICAN;}
    public static String name(int mode){return valid(mode)==JAPANESE?"日本手（Mode 1）":valid(mode)==CHINESE?"中国手（Mode 3）":"美国手（Mode 2）";}
    public static String label(int mode,boolean right){
        switch(valid(mode)){
            case JAPANESE:return right?"横滚 / 油门":"偏航 / 俯仰";
            case CHINESE:return right?"油门 / 偏航":"横滚 / 俯仰";
            default:return right?"横滚 / 俯仰":"油门 / 偏航";
        }
    }
    public static String description(int mode){
        switch(valid(mode)){
            case JAPANESE:return "左摇杆：左右偏航，上下俯仰\n右摇杆：左右横滚，上下油门";
            case CHINESE:return "左摇杆：左右横滚，上下俯仰\n右摇杆：左右偏航，上下油门";
            default:return "左摇杆：左右偏航，上下油门\n右摇杆：左右横滚，上下俯仰";
        }
    }
}
