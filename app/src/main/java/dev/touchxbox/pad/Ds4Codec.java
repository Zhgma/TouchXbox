package dev.touchxbox.pad;

/** USB DS4 input and feature reports. No Bluetooth CRC or physical radio is involved. */
public final class Ds4Codec {
    public static final String NAME="TouchXbox DualShock 4";
    private static final int[] DESC={
        0x05,1,0x09,5,0xa1,1,0x85,1,0x15,0,0x26,255,0,0x75,8,0x95,4,0x09,0x30,0x09,0x31,0x09,0x32,0x09,0x35,0x81,2,
        0x15,0,0x25,7,0x75,4,0x95,1,0x09,0x39,0x81,0x42,0x05,9,0x19,1,0x29,14,0x15,0,0x25,1,0x75,1,0x95,14,0x81,2,
        0x06,0,255,0x75,6,0x95,1,0x81,3,0x05,1,0x15,0,0x26,255,0,0x75,8,0x95,2,0x09,0x33,0x09,0x34,0x81,2,
        0x06,0,255,0x09,1,0x95,54,0x81,2,0x85,5,0x95,31,0x91,2,0x85,2,0x95,36,0xb1,2,0x85,0x81,0x95,6,0xb1,2,
        0x85,0xa3,0x95,48,0xb1,2,0x85,0x12,0x95,15,0xb1,2,0xc0
    };
    public static byte[] descriptor(){byte[] r=new byte[DESC.length];for(int i=0;i<r.length;i++)r[i]=(byte)DESC[i];return r;}
    public static byte[] feature(int id){
        byte[] b;
        if(id==2){b=new byte[37];for(int i=0;i<3;i++){put(b,7+i*4,16000);put(b,9+i*4,-16000);put(b,23+i*4,8192);put(b,25+i*4,-8192);}put(b,19,1000);put(b,21,1000);}
        else if(id==0x81){b=new byte[]{(byte)id,2,0x54,0x58,0,0,2};}
        else if(id==0xa3){b=new byte[49];b[35]=1;b[41]=1;}
        else if(id==0x12){b=new byte[16];b[1]=2;b[2]=0x54;b[3]=0x58;b[6]=2;}
        else return null;b[0]=(byte)id;return b;
    }
    public static byte[] report(byte[] pad,short[] ns,long nanos){
        byte[] b=new byte[64];b[0]=1;for(int i=0;i<4;i++)b[1+i]=(byte)Math.round(u16(pad,i*2)*255f/65535);
        int keys=u16(pad,12),hat=pad[14]&255;b[5]=(byte)(hat==0?8:hat-1);
        if(on(keys,PadState.X))b[5]|=16;if(on(keys,PadState.A))b[5]|=32;if(on(keys,PadState.B))b[5]|=64;if(on(keys,PadState.Y))b[5]|=128;
        if(on(keys,PadState.LB))b[6]|=1;if(on(keys,PadState.RB))b[6]|=2;if(u16(pad,8)>0)b[6]|=4;if(u16(pad,10)>0)b[6]|=8;
        if(on(keys,PadState.BACK))b[6]|=16;if(on(keys,PadState.START))b[6]|=32;if(on(keys,PadState.LS))b[6]|=64;if(on(keys,PadState.RS))b[6]|=128;
        b[7]=(byte)(((nanos/15000000)&63)<<2);if(on(keys,PadState.GUIDE))b[7]|=1;
        b[8]=(byte)Math.round(u16(pad,8)*255f/32767);b[9]=(byte)Math.round(u16(pad,10)*255f/32767);put(b,10,(int)((nanos/1000)*3/16));
        if(ns!=null){int[] order={1,2,0};int[] signs={-1,1,-1};for(int i=0;i<3;i++){put(b,13+i*2,clamp(Math.round(ns[3+order[i]]*signs[i]*16/MotionCodec.GYRO_PER_DPS)));put(b,19+i*2,clamp(ns[order[i]]*signs[i]*2L));}}
        b[30]=0x1b;b[33]=1;b[35]=(byte)0x80;b[39]=(byte)0x80;return b;
    }
    private static boolean on(int mask,int bit){return (mask&(1<<bit))!=0;}
    private static int u16(byte[] b,int p){return SwitchProCodec.u16(b,p);}
    private static void put(byte[] b,int p,int n){SwitchProCodec.put16(b,p,n);}
    private static int clamp(long n){return (int)Math.max(-32768,Math.min(32767,n));}
}
