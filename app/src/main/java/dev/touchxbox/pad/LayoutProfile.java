package dev.touchxbox.pad;

import java.util.*;

/** Screen-relative layout shared by the editor and the overlay. No input is sent here. */
public final class LayoutProfile {
    public static final class Spec {
        public final String name; public final int kind,code; public final float x,y,w,h;
        Spec(String n,int k,int c,float px,float py,float width,float height){name=n;kind=k;code=c;x=px;y=py;w=width;h=height;}
    }
    public static final Spec[] SPECS={
        new Spec("LB",0,PadState.LB,.075f,.255f,64,55),new Spec("LT",3,0,.075f,.10f,64,86),
        new Spec("RB",0,PadState.RB,.925f,.255f,64,55),new Spec("RT",3,1,.925f,.10f,64,86),
        new Spec("L",1,0,.12f,.49f,154,154),new Spec("R",1,1,.70f,.76f,154,154),
        new Spec("D",2,0,.30f,.77f,137,137),new Spec("Y",0,PadState.Y,.875f,.43f,66,66),
        new Spec("X",0,PadState.X,.80f,.58f,66,66),new Spec("B",0,PadState.B,.95f,.58f,66,66),
        new Spec("A",0,PadState.A,.875f,.73f,66,66),new Spec("ABXY",4,0,.875f,.58f,210,210),new Spec("VIEW",0,PadState.BACK,.42f,1f,74,45),
        new Spec("MENU",0,PadState.START,.58f,1f,74,45),new Spec("XBOX",0,PadState.GUIDE,.50f,1f,70,45),
        new Spec("CH5",6,4,.075f,.10f,74,55),new Spec("CH6",6,5,.925f,.10f,74,55),
        new Spec("CH7",6,6,.075f,.255f,74,55),new Spec("CH8",6,7,.925f,.255f,74,55)
    };
    public static final class Box {
        public float x,y,w,h;
        public Box(float a,float b,float c,float d){x=a;y=b;w=c;h=d;}
        public boolean contains(float px,float py){return px>=x&&py>=y&&px<x+w&&py<y+h;}
        public boolean overlaps(Box b){return x<b.x+b.w&&x+w>b.x&&y<b.y+b.h&&y+h>b.y;}
        public Box pixels(float canvasW,float canvasH){float pw=Math.min(Math.round(canvasW),Math.max(1,Math.round(w))),ph=Math.min(Math.round(canvasH),Math.max(1,Math.round(h)));return new Box(clamp(Math.round(x),0,Math.round(canvasW)-pw),clamp(Math.round(y),0,Math.round(canvasH)-ph),pw,ph);}
    }
    public static final class Item {
        public float x,y,scale;
        /** Optional template shape in layout units; zero keeps the component default. */
        public float width,height;
        public Item(float a,float b,float s){x=a;y=b;scale=s;}
    }
    public static final class Page {
        public final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
        public float foldX=.5f,foldY=.075f,foldW=.21f,foldH=.10f;
        public float shoulderWidth=1,triggerHeight=1,bumperHeight=1,keyboardScale=1;
        /** One layout unit as a fraction of the canvas short edge; zero is a legacy page. */
        public float unitRatio;
        /** 0 independent, 1 stacked triggers above bumpers, 2 mirrored side-by-side. */
        public int shoulderLayout;
        public float shoulderX=.2f,shoulderY=.2f;
        Page(){for(Spec s:SPECS)items.put(s.name,new Item(s.x,s.y,1));}
        public boolean ensureRelativeSize(float w,float h){
            if(unitRatio>0||!(w>0&&h>0))return false;
            unitRatio=Math.min(w/960f,h/490f)/Math.min(w,h);return true;
        }
        public Box fold(float w,float h){float fw=w*foldW,fh=h*foldH;return new Box(clamp(w*foldX-fw/2,0,w-fw),clamp(h*foldY-fh/2,0,h-fh),fw,fh);}
        public Box box(Spec s,float w,float h){
            if(shoulderLayout!=0&&isShoulder(s))return linkedShoulder(s,w,h);
            return independentBox(s,w,h);
        }
        private Box independentBox(Spec s,float w,float h){
            Item p=items.get(s.name);if(p==null){p=new Item(s.x,s.y,1);items.put(s.name,p);}
            boolean shoulder=isShoulder(s);float scale=s.kind==5?keyboardScale:p.scale;
            float u=unitRatio>0?unitRatio*Math.min(w,h):Math.min(w/960f,h/490f);
            float baseW=!shoulder&&p.width>0?p.width:s.w,baseH=!shoulder&&p.height>0?p.height:s.h;
            float bw=baseW*u*(shoulder?shoulderWidth:scale),bh=baseH*u*(shoulder?(s.kind==3?triggerHeight:bumperHeight):scale);
            // Fit extreme sizes uniformly so a circular control never becomes an ellipse.
            float fit=Math.min(1,Math.min(w/bw,h/bh));bw*=fit;bh*=fit;
            return new Box(clamp(w*p.x-bw/2,0,w-bw),clamp(h*p.y-bh/2,0,h-bh),bw,bh);
        }
        private float[] shoulderGeometry(float w,float h){
            float u=unitRatio>0?unitRatio*Math.min(w,h):Math.min(w/960f,h/490f),gap=6*u;
            Box trigger=new Box(0,0,64*u*shoulderWidth,86*u*triggerHeight),bumper=new Box(0,0,64*u*shoulderWidth,55*u*bumperHeight);
            float gw=shoulderLayout==2?trigger.w+gap+bumper.w:Math.max(trigger.w,bumper.w);
            float gh=shoulderLayout==2?Math.max(trigger.h,bumper.h):trigger.h+gap+bumper.h;
            float fit=Math.min(1,Math.min(w/(2*gw),h/gh));
            return new float[]{trigger.w*fit,trigger.h*fit,bumper.w*fit,bumper.h*fit,gw*fit,gh*fit};
        }
        private Box linkedShoulder(Spec s,float w,float h){
            float[] g=shoulderGeometry(w,h);boolean trigger=s.kind==3,right=s.name.charAt(0)=='R';
            float bw=g[trigger?0:2],bh=g[trigger?1:3],cx=clamp(shoulderX*w,g[4]/2,w/2-g[4]/2),cy=clamp(shoulderY*h,g[5]/2,h-g[5]/2);
            float dx=shoulderLayout==2?(trigger?-g[4]/2+bw/2:g[4]/2-bw/2):0;
            float dy=shoulderLayout==1?(trigger?-g[5]/2+bh/2:g[5]/2-bh/2):0;
            cx+=dx;if(right)cx=w-cx;return new Box(cx-bw/2,cy+dy-bh/2,bw,bh);
        }
        public void setShoulderLayout(int mode,float w,float h){
            Box t=box(spec("LT"),w,h),b=box(spec("LB"),w,h);
            if(mode==0){for(String key:new String[]{"LT","LB","RT","RB"}){Box v=box(spec(key),w,h);Item p=items.get(key);p.x=(v.x+v.w/2)/w;p.y=(v.y+v.h/2)/h;}}
            if(shoulderLayout==0&&mode!=0){shoulderX=((t.x+t.w/2)+(b.x+b.w/2))/2/w;shoulderY=(Math.min(t.y,b.y)+Math.max(t.y+t.h,b.y+b.h))/2/h;}
            shoulderLayout=mode;
        }
        /** cx/cy are normalized canvas coordinates, for both thumbnail and full-size editors. */
        public void move(String key,float cx,float cy,float w,float h){
            if("FOLD".equals(key)){foldX=clamp(cx,foldW/2,1-foldW/2);foldY=clamp(cy,foldH/2,1-foldH/2);return;}
            Spec s=spec(key);if(s==null)return;
            if(shoulderLayout!=0&&isShoulder(s)){float[] g=shoulderGeometry(w,h);boolean trigger=s.kind==3;float bw=g[trigger?0:2],bh=g[trigger?1:3];cx*=w;cy*=h;if(s.name.charAt(0)=='R')cx=w-cx;float dx=shoulderLayout==2?(trigger?-g[4]/2+bw/2:g[4]/2-bw/2):0,dy=shoulderLayout==1?(trigger?-g[5]/2+bh/2:g[5]/2-bh/2):0;shoulderX=clamp(cx-dx,g[4]/2,w/2-g[4]/2)/w;shoulderY=clamp(cy-dy,g[5]/2,h-g[5]/2)/h;return;}
            Box b=box(s,w,h);Item p=items.get(key);p.x=clamp(cx,b.w/w/2,1-b.w/w/2);p.y=clamp(cy,b.h/h/2,1-b.h/h/2);
        }
        public void restore(String... keys){for(String key:keys){Spec s=spec(key);if(s!=null)items.put(key,new Item(s.x,s.y,1));}}
    }
    public String id=UUID.randomUUID().toString(),name="默认布局";
    public boolean leftFloating,rightFloating;
    public boolean leftTriggerClick,rightTriggerClick;
    public boolean swapAB,swapXY;
    public int abxyStyle=1,dpadStyle;
    public int protocol=ControllerProtocol.XBOX;
    public int fpvMode=FpvMode.AMERICAN;
    public boolean fpvXbox;
    /** Missing channel retains default Xbox output; each custom channel has three bindings. */
    public final LinkedHashMap<Integer,int[]> fpvXboxAux=new LinkedHashMap<>();
    public boolean landscapeOnly;
    public final List<Spec> keyboard=new ArrayList<>();
    public final LinkedHashMap<String,String[]> labels=new LinkedHashMap<>();
    public final HashSet<String> toggleLabels=new HashSet<>();
    public final HashSet<String> holdOutside=new HashSet<>(Arrays.asList("LT","LB","RT","RB"));
    public final HashSet<String> hiddenButtons=new HashSet<>();
    /** Session-only visual state: never changes or latches a HID input. */
    public final HashSet<String> highlighted=new HashSet<>();
    public float opacity=.72f;
    public Page landscape=new Page(),portrait=new Page();
    public Page page(boolean wide){return wide?landscape:portrait;}
    /** Layout/gesture protocol stays FPV when its reports use the Xbox transport. */
    public int outputProtocol(){return protocol==ControllerProtocol.FPV&&fpvXbox?ControllerProtocol.XBOX:protocol;}
    public boolean needsKeyboard(){if(!keyboard.isEmpty())return true;if(protocol==ControllerProtocol.FPV&&fpvXbox)for(int[] levels:fpvXboxAux.values())for(int binding:levels)if(FpvAuxMapping.keyboard(binding))return true;return false;}
    public boolean floating(Spec s){return visible(s)&&s.kind==1&&(s.code==0?leftFloating:rightFloating);}
    public boolean triggerClick(Spec s){return s.kind==3&&(s.code==0?leftTriggerClick:rightTriggerClick);}
    public static boolean canHoldOutside(Spec s){return s!=null&&(s.kind==0||s.kind==2||s.kind==3||s.kind==4||s.kind==5);}
    public boolean holdsOutside(Spec s){return canHoldOutside(s)&&holdOutside.contains(s.name);}
    public boolean available(Spec s){return protocol==ControllerProtocol.FPV?s.kind==1||s.kind==5||s.kind==6:s.kind!=6;}
    public boolean visible(Spec s){if(!available(s))return false;if(s.kind==4)return abxyStyle==1&&!(hiddenButtons.contains("A")&&hiddenButtons.contains("B")&&hiddenButtons.contains("X")&&hiddenButtons.contains("Y"));return !hiddenButtons.contains(displayKey(s))&&!(abxyStyle==1&&s.name.matches("[ABXY]"));}
    /** The same permutation drives labels and real gamepad events in both ABXY styles. */
    public int mapButton(int code){if(swapAB){if(code==PadState.A)return PadState.B;if(code==PadState.B)return PadState.A;}if(swapXY){if(code==PadState.X)return PadState.Y;if(code==PadState.Y)return PadState.X;}return code;}
    public int mapFaceMask(int mask){int result=mask;for(int bit:new int[]{PadState.A,PadState.B,PadState.X,PadState.Y})result&=~(1<<bit);for(int bit:new int[]{PadState.A,PadState.B,PadState.X,PadState.Y})if((mask&(1<<bit))!=0&&!hiddenButtons.contains(buttonName(mapButton(bit))))result|=1<<mapButton(bit);return result;}
    public static String buttonName(int code){return code==PadState.A?"A":code==PadState.B?"B":code==PadState.X?"X":code==PadState.Y?"Y":"";}
    public String label(Spec s){return label(s,false);}
    public String displayKey(Spec s){return s.kind==0&&s.name.matches("[ABXY]")?buttonName(mapButton(s.code)):s.name;}
    public String label(Spec s,boolean lit){String key=displayKey(s);String[] pair=labels.get(key);return pair==null?(s.kind==5?KeyboardKeys.name(s.code):squareStick(s)?FpvMode.label(fpvMode,s.code==1):ControllerProtocol.label(protocol,key)):pair[lit?1:0];}
    public boolean squareStick(Spec s){return protocol==ControllerProtocol.FPV&&s.kind==1;}
    public boolean throttle(Spec s){return squareStick(s)&&(s.code==1)==FpvMode.throttleRight(fpvMode);}
    public boolean inFoldRegion(float x,float y,float w,float h){return page(w>h).fold(w,h).contains(x,y);}
    public void activateLabel(String key){if(toggleLabels.contains(key)){if(!highlighted.add(key))highlighted.remove(key);}}
    public String directionLabel(int direction,boolean down){String[] pair=labels.get("D_"+direction);return pair==null?new String[]{"↑","→","↓","←"}[direction]:pair[down?1:0];}
    public static boolean labelKey(String key){return spec(key)!=null||key.matches("D_[0-3]");}
    public List<Spec> specs(){List<Spec> all=new ArrayList<>(Arrays.asList(SPECS));all.addAll(keyboard);return all;}
    public Spec addKey(int usage){if(!KeyboardKeys.valid(usage)||keyboard.size()>=32)throw new IllegalArgumentException("最多添加 32 个有效键盘按键");Spec s=spec("KEY_"+UUID.randomUUID().toString().replace("-","")+"_"+usage);keyboard.add(s);for(boolean wide:new boolean[]{true,false})page(wide).items.put(s.name,new Item(.5f,.4f,1));return s;}
    public void removeKey(String key){keyboard.removeIf(s->s.name.equals(key));labels.remove(key);toggleLabels.remove(key);holdOutside.remove(key);highlighted.remove(key);landscape.items.remove(key);portrait.items.remove(key);}
    public static Spec spec(String name){for(Spec s:SPECS)if(s.name.equals(name))return s;if(name!=null&&name.matches("KEY_[a-f0-9]{32}_[0-9]{1,3}")){int usage=Integer.parseInt(name.substring(name.lastIndexOf('_')+1));if(KeyboardKeys.valid(usage))return new Spec(name,5,usage,.5f,.4f,76,55);}return null;}
    public static boolean isShoulder(Spec s){return s.name.matches("[LR][BT]");}
    public String foldConflict(boolean wide,float w,float h){Page p=page(wide);Box f=p.fold(w,h);for(Spec s:specs())if(visible(s)&&!floating(s)&&p.box(s,w,h).overlaps(f))return label(s);return null;}
    public static float clamp(float v,float lo,float hi){return Float.isNaN(v)||Float.isInfinite(v)?lo:Math.max(lo,Math.min(hi,v));}
}
