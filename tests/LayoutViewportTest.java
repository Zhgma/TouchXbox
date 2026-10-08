import dev.touchxbox.pad.*;

/** Regression for the phone's lost 139 px top/left and orientation-dependent sizing. */
public final class LayoutViewportTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(int[] size:new int[][]{{1260,2844},{1600,2560}})for(boolean wide:new boolean[]{true,false}){
            LayoutViewport fromPortrait=LayoutViewport.fullScreen(size[0],size[1],wide);
            LayoutViewport fromLandscape=LayoutViewport.fullScreen(size[1],size[0],wide);
            int width=wide?size[1]:size[0],height=wide?size[0]:size[1];
            check(fromPortrait.width==width&&fromPortrait.height==height,"Full physical display must remain editable");
            check(fromPortrait.left==0&&fromPortrait.top==0,"Hidden system bars must not leave a shifted origin");
            check(fromLandscape.width==width&&fromLandscape.height==height,"Editor orientation must not change target dimensions");
            LayoutProfile p=new LayoutProfile();LayoutProfile.Page page=p.page(wide);
            for(LayoutProfile.Spec spec:p.specs()){
                LayoutProfile.Box original=page.box(spec,width,height),window=fromPortrait.screenBox(original);
                check(window.x==Math.round(original.x)&&window.y==Math.round(original.y),"A window must not apply a second inset");
                check(window.w==Math.round(original.w)&&window.h==Math.round(original.h),"Preview and window must use identical sizes");
            }
            page.move("LT",0,0,width,height);LayoutProfile.Box corner=fromPortrait.screenBox(page.box(LayoutProfile.spec("LT"),width,height));
            check(corner.x==0&&corner.y==0,"Controls must reach the actual top-left corner");
            page.move("RT",1,1,width,height);corner=fromPortrait.screenBox(page.box(LayoutProfile.spec("RT"),width,height));
            check(Math.abs(corner.x+corner.w-width)<=1&&Math.abs(corner.y+corner.h-height)<=1,"Controls must reach the actual bottom-right corner");
        }
        System.out.println("LayoutViewportTest passed: full phone/tablet bounds, orientation, shared sizes and edge placement");
    }
}
