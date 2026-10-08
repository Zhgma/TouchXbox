package dev.touchxbox.pad;

/** Generic unsigned 16-bit Mode 2 radio. CH3 uses the native unipolar
 * throttle usage, so Android starts at zero without synthesizing a startup move. */
public final class FpvCodec {
    public static final String NAME="TouchXbox FPV USB Radio";
    public static byte[] descriptor(){int[] d={5,1,9,4,0xa1,1,0x15,0,0x27,255,255,0,0,0x75,16,0x95,8,9,0x30,9,0x31,9,0x36,9,0x33,9,0x34,9,0x35,9,0x32,9,0x37,0x81,2,5,9,0x19,1,0x29,16,0x15,0,0x25,1,0x75,1,0x95,16,0x81,2,0xc0};byte[] b=new byte[d.length];for(int i=0;i<d.length;i++)b[i]=(byte)d[i];return b;}
    public static byte[] report(byte[] p){byte[] b=new byte[18];put(b,0,u16(p,4));put(b,2,invert(u16(p,6)));put(b,4,invert(u16(p,2)));put(b,6,u16(p,0));put(b,8,Math.round(u16(p,8)*65535f/32767));put(b,10,Math.round(u16(p,10)*65535f/32767));int h=p[14]&255;put(b,12,h>=2&&h<=4?65535:h>=6&&h<=8?0:32768);put(b,14,h==1||h==2||h==8?65535:h>=4&&h<=6?0:32768);put(b,16,u16(p,12));return b;}
    private static int invert(int n){return n==32768?32768:65535-n;}
    private static int u16(byte[] p,int i){return SwitchProCodec.u16(p,i);}
    private static void put(byte[] p,int i,int v){SwitchProCodec.put16(p,i,v);}
}
