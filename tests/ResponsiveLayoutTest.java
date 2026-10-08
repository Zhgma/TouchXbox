import dev.touchxbox.pad.*;

/** User-visible portability: a template keeps its proportions on another display. */
public final class ResponsiveLayoutTest {
    static void near(float a,float b,String message){if(Math.abs(a-b)>.002f)throw new AssertionError(message+": "+a+" != "+b);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(boolean wide:new boolean[]{true,false}){
            LayoutProfile p=new LayoutProfile();LayoutProfile.Spec keyboard=p.addKey(26);LayoutProfile.Page page=p.page(wide);
            float sw=wide?2844:1260,sh=wide?1260:2844;
            page.shoulderWidth=.8f;page.triggerHeight=1.4f;page.bumperHeight=.7f;page.keyboardScale=1.25f;
            for(LayoutProfile.Item item:page.items.values()){item.x=.45f;item.y=.55f;item.scale=1.3f;}
            LayoutProfile.Box before=page.box(LayoutProfile.spec("ABXY"),sw,sh);
            check(page.ensureRelativeSize(sw,sh),"Old layout captures a size basis once");
            LayoutProfile.Box after=page.box(LayoutProfile.spec("ABXY"),sw,sh);
            near(before.x,after.x,"Migration keeps position");near(before.w,after.w,"Migration keeps size");
            float saved=page.unitRatio;
            for(float[] size:new float[][]{{2560,1600},{1920,1080},{2844*2,1260*2},{1260,1260}}){
                float tw=wide?size[0]:size[1],th=wide?size[1]:size[0],factor=Math.min(tw,th)/1260f;
                check(!page.ensureRelativeSize(tw,th)&&page.unitRatio==saved,"A different device cannot reset the stored size basis");
                for(LayoutProfile.Spec spec:p.specs()){
                    LayoutProfile.Box source=page.box(spec,sw,sh),target=page.box(spec,tw,th);
                    near(target.w,source.w*factor,"Width scales with canvas: "+spec.name);
                    near(target.h,source.h*factor,"Height scales with canvas: "+spec.name);
                    near((target.x+target.w/2)/tw,.45f,"Horizontal relative position survives");
                    near((target.y+target.h/2)/th,.55f,"Vertical relative position survives");
                    near(target.w/target.h,source.w/source.h,"Aspect ratio survives");
                }
                LayoutProfile.Box fold=page.fold(tw,th);near(fold.w/tw,page.foldW,"Fold width is relative");near(fold.h/th,page.foldH,"Fold height is relative");
            }
            LayoutProfile.Box first=page.box(keyboard,sw,sh),second=page.box(p.addKey(4),sw,sh);near(first.w,second.w,"All keyboard keys keep shared sizing");
            page.items.get("ABXY").x=.99f;page.items.get("ABXY").y=.01f;
            LayoutProfile.Box edge=page.box(LayoutProfile.spec("ABXY"),1600,1600);
            check(edge.x>=0&&edge.y>=0&&edge.x+edge.w<=1600.01f,"Adapted controls remain on screen");
            check(page.items.get("ABXY").x==.99f&&page.items.get("ABXY").y==.01f,"Clamping must not rewrite the portable anchor");
            page.unitRatio=.01f;edge=page.box(LayoutProfile.spec("ABXY"),900,1600);near(edge.w,edge.h,"Oversized controls fit without distortion");
        }
        LayoutProfile aligned=new LayoutProfile();LayoutProfile.Spec key=aligned.addKey(61);
        for(boolean wide:new boolean[]{true,false}){
            LayoutProfile.Page page=aligned.page(wide);page.ensureRelativeSize(wide?2560:1600,wide?1600:2560);
            page.items.get(key.name).width=74;page.items.get(key.name).height=45;page.items.get("XBOX").width=74;
            for(int[] size:new int[][]{{2560,1600},{2844,1260},{1600,2560}}){
                LayoutProfile.Box k=page.box(key,size[0],size[1]);
                for(String name:new String[]{"VIEW","XBOX","MENU"}){LayoutProfile.Box b=page.box(LayoutProfile.spec(name),size[0],size[1]);near(k.w,b.w,"Preset keyboard width matches function key");near(k.h,b.h,"Preset keyboard height matches function key");}
            }
        }
        System.out.println("ResponsiveLayoutTest passed: phone/tablet/square, both orientations, positions, sizes, keyboard linkage, legacy migration and edge clamping");
    }
}
