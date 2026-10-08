import dev.touchxbox.pad.*;

public final class ShoulderInteractionTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void near(float a,float b,String why){if(Math.abs(a-b)>.005f)throw new AssertionError(why+": "+a+" != "+b);}
    static float cx(LayoutProfile.Box b){return b.x+b.w/2;}
    static float cy(LayoutProfile.Box b){return b.y+b.h/2;}
    static LayoutProfile.Box box(LayoutProfile.Page p,String key,float w,float h){return p.box(LayoutProfile.spec(key),w,h);}
    static void symmetry(LayoutProfile.Page p,float w,float h){
        LayoutProfile.Box t=box(p,"LT",w,h),b=box(p,"LB",w,h),rt=box(p,"RT",w,h),rb=box(p,"RB",w,h);
        near(cx(t)+cx(rt),w,"Trigger mirror");near(cx(b)+cx(rb),w,"Bumper mirror");near(cy(t),cy(rt),"Trigger vertical symmetry");near(cy(b),cy(rb),"Bumper vertical symmetry");
        for(LayoutProfile.Box v:new LayoutProfile.Box[]{t,b,rt,rb}){check(v.x>=-.001&&v.y>=-.001&&v.x+v.w<=w+.001&&v.y+v.h<=h+.001,"Whole linked group stays on screen");near(v.w,t.w,"All shoulder widths stay linked");}
        near(t.w/t.h,64*p.shoulderWidth/(86*p.triggerHeight),"Trigger aspect ratio preserved");near(b.w/b.h,64*p.shoulderWidth/(55*p.bumperHeight),"Bumper aspect ratio preserved");
        if(p.shoulderLayout==1){near(cx(t),cx(b),"Stack column");check(t.y+t.h<b.y,"Trigger above bumper");}
        else {near(cy(t),cy(b),"Side-by-side row");check(t.x+t.w<b.x,"Left trigger is outside");check(rb.x+rb.w<rt.x,"Right trigger mirrors outside");}
    }
    public static void main(String[] args){
        LayoutProfile profile=new LayoutProfile();
        for(LayoutProfile.Spec s:profile.specs())check(profile.holdsOutside(s)==LayoutProfile.isShoulder(s),"Only shoulders retain by default: "+s.name);
        check(!profile.holdsOutside(profile.addKey(61)),"Keyboard retention off by default");
        ButtonPress press=new ButtonPress();press.begin(true);check(press.move(false),"Shoulder stays down after leaving");check(press.move(true)&&press.move(false),"Reentry and second exit retain one press");press.end();check(!press.down()&&!press.move(true),"Up/cancel releases and cannot reacquire without a new down");press.begin(false);check(!press.move(false)&&press.move(true),"Disabled option releases outside and reacquires on reentry");press.end();
        ButtonChord chord=new ButtonChord();chord.point(1,.9f,.5f,true);chord.point(2,.9f,.5f,true);int mask=chord.mask();chord.point(1,2,.5f,true);chord.up(2);check(chord.mask()==mask,"Retained outside pointer remains until its own up");chord.up(1);check(chord.mask()==0,"Last pointer up releases chord");chord.point(3,2,.5f,true);check(chord.mask()==0,"Outside start cannot latch a key");chord.point(4,.9f,.5f);chord.point(4,2,.5f);check(chord.mask()==0,"Default chord clears outside");
        for(int mode:new int[]{1,2})for(int[] size:new int[][]{{2844,1260},{2560,1600},{1260,2844},{1260,1260}}){
            float w=size[0],h=size[1];LayoutProfile.Page p=new LayoutProfile().landscape;p.ensureRelativeSize(2560,1600);p.setShoulderLayout(mode,w,h);symmetry(p,w,h);
            for(String name:new String[]{"LT","LB","RT","RB"}){float x=name.charAt(0)=='R'?w*.7f:w*.3f;p.move(name,x/w,.45f,w,h);near(cx(box(p,name,w,h)),x,"Editor normalized drag follows finger");near(cy(box(p,name,w,h)),h*.45f,"Editor normalized vertical drag follows finger");symmetry(p,w,h);}
            p.shoulderWidth=1.4f;p.triggerHeight=1.8f;p.bumperHeight=.7f;symmetry(p,w,h);
            for(float[] point:new float[][]{{-100,-100},{w+100,h+100}}){p.move("RT",point[0]/w,point[1]/h,w,h);symmetry(p,w,h);}
            LayoutProfile.Box before=box(p,"LT",w,h);p.setShoulderLayout(0,w,h);LayoutProfile.Box after=box(p,"LT",w,h);near(before.x,after.x,"Unlink retains positions");near(before.y,after.y,"Unlink retains rows");
        }
        LayoutProfile.Box edge=new LayoutProfile.Box(1116.5f,1165.5f,155.4f,94.5f).pixels(2844,1260);check(edge.y+edge.h==1260&&edge.h==95,"Round before edge clamp: preview equals window manager frame");
        System.out.println("PASS: drag-out ownership, defaults, multi-touch release, mirrored shoulder stack/row moves, linked sizes, edge fitting, pixel alignment");
    }
}
