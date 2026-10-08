package dev.touchxbox.pad;

import java.util.Arrays;

/** Independently implemented NS Pro HID wire format; see protocol references in README. */
public final class SwitchProCodec {
    public static final int VENDOR=0x057e,PRODUCT=0x2009,REPORT_SIZE=49;
    public static final String NAME="TouchXbox NS Pro Controller";
    private static final byte[] MAC={2,0x54,0x58,0,0,1}; // Local virtual identity, not a physical device address.
    private static final int[] DESCRIPTOR={
        0x05,0x01,0x09,0x05,0xa1,0x01,0x06,0x00,0xff,0x09,0x01,0x15,0x00,0x26,0xff,0x00,0x75,0x08,
        0x85,0x21,0x95,0x3f,0x81,0x02,0x85,0x30,0x95,0x30,0x81,0x02,0x85,0x81,0x95,0x3f,0x81,0x02,
        0x85,0x01,0x95,0x3f,0x91,0x02,0x85,0x80,0x95,0x3f,0x91,0x02,0x85,0x10,0x95,0x08,0x91,0x02,0xc0
    };
    public static byte[] descriptor(){byte[] b=new byte[DESCRIPTOR.length];for(int i=0;i<b.length;i++)b[i]=(byte)DESCRIPTOR[i];return b;}
    public static byte[] report(byte[] pad,short[][] samples,long uptimeMs){
        if(pad.length!=15)throw new IllegalArgumentException("Canonical pad report size");
        byte[] b=new byte[REPORT_SIZE];b[0]=0x30;b[1]=(byte)(uptimeMs/5);b[2]=(byte)0x90;
        int keys=u16(pad,12),right=0,left=0,misc=0;
        // Preserve Android logical A/B/X/Y across the native Nintendo physical layout.
        if(on(keys,PadState.A))right|=4;if(on(keys,PadState.B))right|=8;if(on(keys,PadState.X))right|=1;if(on(keys,PadState.Y))right|=2;
        if(on(keys,PadState.RB))right|=64;if(on(keys,PadState.LB))left|=64;
        if(u16(pad,8)>0)left|=128;if(u16(pad,10)>0)right|=128;
        if(on(keys,PadState.BACK))misc|=1;if(on(keys,PadState.START))misc|=2;
        if(on(keys,PadState.RS))misc|=4;if(on(keys,PadState.LS))misc|=8;
        // Android maps Nintendo Capture to BUTTON_MODE; native Home is the system Home key.
        if(on(keys,PadState.GUIDE))misc|=32;
        int hat=pad[14]&255;if(hat==1||hat==2||hat==8)left|=2;if(hat>=4&&hat<=6)left|=1;if(hat>=2&&hat<=4)left|=4;if(hat>=6&&hat<=8)left|=8;
        b[3]=(byte)right;b[4]=(byte)misc;b[5]=(byte)left;
        pair(b,6,axis(u16(pad,0),false),axis(u16(pad,2),true));pair(b,9,axis(u16(pad,4),false),axis(u16(pad,6),true));
        if(samples!=null)for(int sample=0;sample<3;sample++)for(int a=0;a<6;a++)put16(b,13+sample*12+a*2,samples[Math.min(sample,samples.length-1)][a]);return b;
    }
    private static boolean on(int mask,int bit){return (mask&(1<<bit))!=0;}
    private static int axis(int n,boolean reverse){double v=n<32768?(n-32768)/32768d:(n-32768)/32767d;if(reverse)v=-v;return 2048+(int)Math.round(v*(v<0?2048:2047));}
    static void pair(byte[] b,int p,int x,int y){b[p]=(byte)x;b[p+1]=(byte)((x>>8)|((y&15)<<4));b[p+2]=(byte)(y>>4);}
    static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
    static void put16(byte[] b,int p,int n){b[p]=(byte)n;b[p+1]=(byte)(n>>8);}
    /** Host USB/subcommand handshake. Replies retain the most recent input state. */
    public static byte[] reply(byte[] output,byte[] latest){
        if(output.length<2)return null;int id=output[0]&255;
        if(id==0x80){byte[] r=new byte[64];r[0]=(byte)0x81;r[1]=output[1];if(output[1]==1){r[2]=0;r[3]=3;System.arraycopy(MAC,0,r,4,6);}return r;}
        if(id!=1||output.length<11)return null;
        int sub=output[10]&255;byte[] r=new byte[64];System.arraycopy(latest,0,r,0,13);r[0]=0x21;r[13]=(byte)0x80;r[14]=(byte)sub;
        if(sub==2){r[13]=(byte)0x82;r[15]=4;r[16]=0;r[17]=3;r[18]=2;System.arraycopy(MAC,0,r,19,6);r[25]=1;r[26]=1;}
        else if(sub==0x10&&output.length>=16){int count=output[15]&255;if(count>44){r[13]=0;return r;}r[13]=(byte)0x90;System.arraycopy(output,11,r,15,5);long address=0;for(int i=0;i<4;i++)address|=(long)(output[11+i]&255)<<(8*i);for(int i=0;i<count;i++)r[20+i]=flash(address+i);}
        else if(sub==0x11||sub==0x12)r[13]=0; // Calibration flash is read-only.
        return r;
    }
    private static byte flash(long address){
        byte[] sticks=new byte[18];pair(sticks,0,2047,2047);pair(sticks,3,2048,2048);pair(sticks,6,2048,2048);pair(sticks,9,2048,2048);pair(sticks,12,2048,2048);pair(sticks,15,2047,2047);
        if(address>=0x603d&&address<0x604f)return sticks[(int)address-0x603d];
        if(address>=0x6020&&address<0x6038){byte[] imu=new byte[24];for(int i=0;i<3;i++){put16(imu,6+2*i,16384);put16(imu,18+2*i,13371);}return imu[(int)address-0x6020];}
        return (byte)0xff;
    }
}
