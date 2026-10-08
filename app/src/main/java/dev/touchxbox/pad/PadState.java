package dev.touchxbox.pad;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;

/** Complete reports, never incremental presses: reconnects and watchdogs cannot retain a key. */
public final class PadState {
    public static final int A=0, B=1, X=3, Y=4, LB=6, RB=7, BACK=10, START=11, GUIDE=12, LS=13, RS=14;
    private int buttons, hat,aux7,aux8,fpvMode=FpvMode.AMERICAN;private boolean fpv;
    private float lx, ly, rx, ry, lt, rt;
    private final ArrayDeque<byte[]> pending=new ArrayDeque<>();
    private final HashMap<String,Integer> keyboard=new HashMap<>();
    private final int[][] fpvBindings=new int[4][];
    private final int[] fpvLevels=new int[4];
    public PadState(){this(ControllerProtocol.XBOX);}
    public PadState(int protocol){this(protocol,FpvMode.AMERICAN);}
    public PadState(int protocol,int mode){fpv=protocol==ControllerProtocol.FPV;fpvMode=FpvMode.valid(mode);clear();}
    public synchronized void configure(int protocol){configure(protocol,FpvMode.AMERICAN);}
    public synchronized void configure(int protocol,int mode){fpv=protocol==ControllerProtocol.FPV;fpvMode=FpvMode.valid(mode);Arrays.fill(fpvBindings,null);reset();}
    public synchronized void configure(LayoutProfile profile){
        fpv=profile.protocol==ControllerProtocol.FPV;fpvMode=FpvMode.valid(profile.fpvMode);Arrays.fill(fpvBindings,null);
        if(fpv&&profile.fpvXbox)for(int channel=4;channel<8;channel++){int[] levels=profile.fpvXboxAux.get(channel);if(levels!=null)fpvBindings[channel-4]=levels.clone();}
        reset();
    }
    public synchronized void button(int bit, boolean down) { if(down) buttons |= 1<<bit; else buttons &= ~(1<<bit);changed(false); }
    public synchronized void faceButtons(int mask){int faces=(1<<A)|(1<<B)|(1<<X)|(1<<Y);buttons=(buttons&~faces)|(mask&faces);changed(false);}
    public synchronized void keyboard(String owner,int usage,boolean down){if(!KeyboardKeys.valid(usage))return;Integer previous=down?keyboard.put(owner,usage):keyboard.remove(owner);if(down&&previous!=null&&previous==usage||!down&&previous==null)return;changed(false);}
    public synchronized void stick(boolean right,float x,float y){
        x=clamp(x,-1,1);y=clamp(y,-1,1);
        if(fpv&&fpvMode==FpvMode.JAPANESE){if(right){rx=x;ly=y;}else{lx=x;ry=y;}}
        else if(fpv&&fpvMode==FpvMode.CHINESE){if(right){lx=x;ly=y;}else{rx=x;ry=y;}}
        else if(right){rx=x;ry=y;}else{lx=x;ly=y;}
        changed(true);
    }
    public synchronized void trigger(boolean right,float v){if(right)rt=clamp(v,0,1);else lt=clamp(v,0,1);changed(false);}
    public synchronized void auxiliary(int channel,int level){level=Math.max(0,Math.min(2,level));if(channel>=4&&channel<8&&fpvBindings[channel-4]!=null)fpvLevels[channel-4]=level;else if(channel==4)lt=level/2f;else if(channel==5)rt=level/2f;else if(channel==6)aux7=level;else if(channel==7)aux8=level;changed(false);}
    public synchronized void resetAuxiliary(int channel){if(channel>=4&&channel<8&&fpvBindings[channel-4]!=null){fpvLevels[channel-4]=-1;changed(false);}else auxiliary(channel,1);}
    public synchronized void hat(int value){hat=value>=0&&value<=8?value:0;changed(false);}
    public synchronized void reset(){clear();pending.clear();changed(false);}
    private void clear(){buttons=hat=0;lx=rx=ry=0;lt=fpv&&fpvBindings[0]==null?.5f:0;rt=fpv&&fpvBindings[1]==null?.5f:0;aux7=aux8=fpv?1:0;ly=fpv?1:0;keyboard.clear();Arrays.fill(fpvLevels,-1);}
    private void changed(boolean analog){
        byte[] b=frame(),last=pending.peekLast();if(last!=null&&Arrays.equals(last,b))return;
        // Only coalesce stick moves with the same digital/trigger state, never a button edge.
        if(analog&&last!=null&&Arrays.equals(Arrays.copyOfRange(last,8,47),Arrays.copyOfRange(b,8,47)))pending.removeLast();
        if(pending.size()>=256){pending.clear();clear();b=frame();}
        pending.addLast(b);notifyAll();
    }
    public synchronized byte[] nextReport(long timeout)throws InterruptedException{return Arrays.copyOf(nextFrame(timeout),15);}
    public synchronized byte[] nextFrame(long timeout)throws InterruptedException{if(pending.isEmpty()&&timeout>0)wait(timeout);return pending.isEmpty()?frame():pending.removeFirst();}
    private int binding(int channel){return fpvBindings[channel]!=null&&fpvLevels[channel]>=0?fpvBindings[channel][fpvLevels[channel]]:0;}
    private byte[] frame(){byte[] b=Arrays.copyOf(report(),47);for(int usage:keyboard.values())b[15+usage/8]|=1<<(usage%8);for(int ch=0;ch<4;ch++){int bind=binding(ch);if(FpvAuxMapping.keyboard(bind)){int usage=bind-FpvAuxMapping.KEYBOARD;b[15+usage/8]|=1<<(usage%8);}}return b;}
    public synchronized byte[] report(){
        byte[] b=new byte[15];
        put16(b,0,axis(lx));put16(b,2,axis(ly));put16(b,4,axis(rx));put16(b,6,axis(ry));
        int outputButtons=buttons,outputHat=fpv?direction(aux7-1,1-aux8):hat;float outputLt=lt,outputRt=rt;
        int hx=outputHat>=2&&outputHat<=4?1:outputHat>=6&&outputHat<=8?-1:0,hy=outputHat==1||outputHat==2||outputHat==8?-1:outputHat>=4&&outputHat<=6?1:0;
        boolean up=hy<0,down=hy>0,left=hx<0,right=hx>0;
        for(int ch=0;ch<4;ch++){int bind=binding(ch);outputButtons|=FpvAuxMapping.buttonMask(bind);if(bind==FpvAuxMapping.LT)outputLt=1;if(bind==FpvAuxMapping.RT)outputRt=1;up|=bind==FpvAuxMapping.UP;down|=bind==FpvAuxMapping.DOWN;left|=bind==FpvAuxMapping.LEFT;right|=bind==FpvAuxMapping.RIGHT;}
        outputHat=direction((right?1:0)-(left?1:0),(down?1:0)-(up?1:0));
        put16(b,8,Math.round(outputLt*32767));put16(b,10,Math.round(outputRt*32767));put16(b,12,outputButtons);b[14]=(byte)outputHat;return b;
    }
    private static int axis(float v){return v<0?32768+Math.round(v*32768):32768+Math.round(v*32767);}
    private static float clamp(float v,float a,float b){return Float.isNaN(v)?0:Math.max(a,Math.min(b,v));}
    private static void put16(byte[] b,int i,int v){b[i]=(byte)v;b[i+1]=(byte)(v>>8);}
    public static int direction(float x,float y){if(Math.hypot(x,y)<0.25)return 0;return ((int)Math.round(Math.atan2(x,-y)/(Math.PI/4))+8)%8+1;}
}
