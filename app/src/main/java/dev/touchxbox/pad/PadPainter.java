package dev.touchxbox.pad;
import android.graphics.*;
/** Shared editor/runtime drawing. Window alpha stays at 1 on Huawei. */
final class PadPainter {
    private final Paint p=new Paint(3);
    void draw(Canvas c,LayoutProfile.Spec s,float w,float h,boolean pressed,float x,float y,float value,boolean clicked,float opacity,int chordMask,LayoutProfile profile){
        int layer=c.saveLayerAlpha(0,0,w,h,Math.round(opacity*255));float cx=w/2,cy=h/2,r=Math.min(w,h)/2-3;
        int dpadStyle=profile.dpadStyle;boolean triggerClick=profile.triggerClick(s),lit=profile.highlighted.contains(profile.displayKey(s));
        if(s.kind==4){abxy(c,w,h,chordMask,profile);c.restoreToCount(layer);return;}
        p.setStyle(Paint.Style.FILL);p.setColor(lit?0xEE245F96:pressed?0xEE32654E:0xDF142A24);shape(c,s,w,h,dpadStyle,profile.protocol);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(clicked||lit?4:1.7f);p.setColor(lit?0xFF9ACFFF:clicked?0xFFFFCD72:0xFF8CCCB1);shape(c,s,w,h,dpadStyle,profile.protocol);p.setStyle(Paint.Style.FILL);
        if(s.kind==1){p.setColor(clicked?0xFFFFCD72:0xFF7DCDA5);c.drawCircle(cx+x*r*.60f,cy+y*r*.60f,r*.32f,p);fitText(c,profile.label(s,lit),cx,profile.throttle(s)?cy-r*.78f:cy+r*.73f,r*.22f,w*.8f,0xFFCADFD4);}
        else if(s.kind==2){
            int hat=(int)value;boolean[] down={hat==1||hat==2||hat==8,hat>=2&&hat<=4,hat>=4&&hat<=6,hat>=6&&hat<=8};float[][] pos={{cx,cy-r*.56f},{cx+r*.56f,cy},{cx,cy+r*.56f},{cx-r*.56f,cy}};for(int i=0;i<4;i++){boolean highlighted=profile.highlighted.contains("D_"+i);fitText(c,profile.directionLabel(i,highlighted),pos[i][0],pos[i][1],r*.38f,w*.32f,highlighted?0xFF84C5FF:down[i]?Color.WHITE:0xFFD6EADD);}
            if(value>0){double angle=(value-1)*Math.PI/4;p.setColor(0xFFFFD889);c.drawCircle(cx+(float)Math.sin(angle)*r*.32f,cy-(float)Math.cos(angle)*r*.32f,r*.13f,p);}
        }
        else if(s.kind==6){if(value>0){p.setColor(0xAAAB813A);c.drawRoundRect(5,h*(1-value)+3,w-5,h-5,10,10,p);}fitText(c,profile.label(s,lit),cx,h*.32f,h*.28f,w*.85f,0xFFFFD898);fitText(c,Math.round(value*100)+"%",cx,h*.74f,h*.24f,w*.85f,0xFFFFE8BF);}
        else if(s.kind==3){p.setColor(0xAA72E6AF);if(value>0)c.drawRoundRect(6,6,w-6,Math.max(6,h*value-5),8,8,p);fitText(c,profile.label(s,lit),cx,cy,h*.38f,w*.85f,0xFFE5F3EA);if(!triggerClick)text(c,"↓",cx,h*.83f,Math.min(w,h)*.22f,0xFFB6D8C4);}
        else {String label=profile.label(s,lit);fitText(c,label,cx,cy,h*.38f,w*.85f,color(s.kind==0?LayoutProfile.buttonName(profile.mapButton(s.code)):""));}
        c.restoreToCount(layer);
    }
    private void shape(Canvas c,LayoutProfile.Spec s,float w,float h,int style,int protocol){
        if(s.kind==2&&style!=0){Path q=new Path();float[][] pts=style==1?new float[][]{{.34f,0},{.66f,0},{.66f,.34f},{1,.34f},{1,.66f},{.66f,.66f},{.66f,1},{.34f,1},{.34f,.66f},{0,.66f},{0,.34f},{.34f,.34f}}:new float[][]{{.293f,0},{.707f,0},{1,.293f},{1,.707f},{.707f,1},{.293f,1},{0,.707f},{0,.293f}};for(int i=0;i<pts.length;i++){float x=2+pts[i][0]*(w-4),y=2+pts[i][1]*(h-4);if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}q.close();c.drawPath(q,p);}
        else if(s.kind==1&&protocol==ControllerProtocol.FPV)c.drawRoundRect(2,2,w-2,h-2,18,18,p);
        else if(s.kind==1||s.kind==2||s.name.matches("[ABXY]"))c.drawCircle(w/2,h/2,Math.min(w,h)/2-3,p);
        else c.drawRoundRect(2,2,w-2,h-2,14,14,p);
    }
    private void abxy(Canvas c,float w,float h,int mask,LayoutProfile profile){
        int[] bits={PadState.Y,PadState.B,PadState.A,PadState.X};float[][] labels={{.5f,.19f},{.81f,.5f},{.5f,.81f},{.19f,.5f}};
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setColor(0xFF8CCCB1);
        Path edge=new Path();float[][] corners={{.293f,0},{.707f,0},{1,.293f},{1,.707f},{.707f,1},{.293f,1},{0,.707f},{0,.293f}};for(int i=0;i<corners.length;i++){float px=2+corners[i][0]*(w-4),py=2+corners[i][1]*(h-4);if(i==0)edge.moveTo(px,py);else edge.lineTo(px,py);}edge.close();c.drawPath(edge,p);
        p.setStyle(Paint.Style.FILL);
        for(int i=0;i<4;i++){int bit=profile.mapButton(bits[i]);String name=LayoutProfile.buttonName(bit);if(profile.hiddenButtons.contains(name))continue;boolean down=(mask&(1<<bit))!=0,lit=profile.highlighted.contains(name);String label=profile.label(LayoutProfile.spec(LayoutProfile.buttonName(bits[i])),lit);if(lit){p.setColor(0xBB245F96);c.drawCircle(w*labels[i][0],h*labels[i][1],Math.min(w,h)*.115f,p);}fitText(c,label,w*labels[i][0],h*labels[i][1],Math.min(w,h)*(down?.14f:.12f),w*.32f,lit?0xFFBDE0FF:down?Color.WHITE:color(name));}
    }
    private int color(String name){return name.equals("A")?0xFF8FF0A4:name.equals("B")?0xFFFF8D91:name.equals("X")?0xFF83C2FF:name.equals("Y")?0xFFFFD981:0xFFE5F3EA;}
    void fitText(Canvas c,String s,float x,float y,float size,float width,int color){p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextSize(size);float measured=p.measureText(s);if(measured>width)size*=width/measured;p.setColor(color);p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);}
    void text(Canvas c,String s,float x,float y,float size,int color){p.setColor(color);p.setTextSize(Math.max(10,size));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);}
}
