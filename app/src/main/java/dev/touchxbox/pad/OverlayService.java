package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class OverlayService extends Service {
    public static volatile boolean active,collapsed;
    public static volatile String status="尚未启动";
    private final PadState state=new PadState();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Control> controls=new ArrayList<>();
    private WindowManager wm;private FoldRegion fold;private LayoutProfile profile;private LayoutViewport viewport;private Notification.Builder notification;
    private volatile boolean running;private volatile BridgeClient client;
    private volatile int generation,requestedProtocol;private volatile boolean requestedKeyboard;private volatile DeviceMotion motion;
    private boolean receiverRegistered,orientationBlocked;private int width,height;
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){public void onReceive(Context c,Intent i){stopSelf();}};
    @Override public void onCreate(){
        super.onCreate();wm=(WindowManager)getSystemService(WINDOW_SERVICE);collapsed=false;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("pad","虚拟手柄",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction("STOP"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent expand=PendingIntent.getService(this,2,new Intent(this,OverlayService.class).setAction("EXPAND"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        notification=new Notification.Builder(this,"pad").setSmallIcon(R.drawable.ic_pad).setContentTitle(LauncherIdentity.name(LauncherIdentity.current(this))+" 悬浮手柄")
            .setContentText("点击管理布局 · 收起后点系统悬浮球恢复").setContentIntent(open).addAction(new Notification.Action.Builder(null,"展开",expand).build()).addAction(new Notification.Action.Builder(null,"停止",stop).build()).setOngoing(true);
        startForeground(7,notification.build());
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));receiverRegistered=true;
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?null:intent.getAction();
        if("STOP".equals(action)){stopSelf();return START_NOT_STICKY;}
        if("APPEARANCE".equals(action)){if(running)updateNotification();else stopSelf();return START_NOT_STICKY;}
        if(running){
            if("HIDE".equals(action)){setCollapsed(true);BubbleActivity.dismiss(this);}
            else if("RELOAD".equals(action))reload();
            else if("FOLD".equals(action))collapseToBubble();
            else setCollapsed(false);
            return START_NOT_STICKY;
        }
        if("HIDE".equals(action)||"RELOAD".equals(action)){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){status="需要悬浮窗权限";stopSelf();return START_NOT_STICKY;}
        profile=new LayoutStore(this).active();if(profile==null){stopSelf();return START_NOT_STICKY;}requestedProtocol=profile.protocol;state.configure(requestedProtocol);requestedKeyboard=!profile.keyboard.isEmpty();running=true;configureMotion();status="正在创建系统手柄…";
        new Thread(()->{
            while(running){int turn=generation,protocol=requestedProtocol;boolean keys=requestedKeyboard,connected=false;BridgeClient c=new BridgeClient(this);client=c;
                try{
                    c.start(protocol,keys);MotionTelemetry.begin();connected=true;if(!running||turn!=generation)continue;active=true;status=ControllerProtocol.NAMES[protocol]+" 已连接";
                    main.post(()->{if(running&&turn==generation){try{remove();show();verifyDevice(turn,protocol,0);}catch(Exception e){status="悬浮窗失败: "+e.getMessage();stopSelf();}}});
                    while(running&&turn==generation){byte[] frame=state.nextFrame(protocol==0||protocol==3?100:15),pad=Arrays.copyOf(frame,15);DeviceMotion sensor=motion;short[][] samples=sensor==null?null:sensor.samples();byte[] report=protocol==1?SwitchProCodec.report(pad,samples,SystemClock.uptimeMillis()):protocol==2?Ds4Codec.report(pad,samples==null?null:samples[2],SystemClock.elapsedRealtimeNanos()):protocol==3?FpvCodec.report(pad):pad;byte[] packet=Arrays.copyOf(report,report.length+32);System.arraycopy(frame,15,packet,report.length,32);c.send(packet);MotionTelemetry.acknowledged(protocol,report);}
                }catch(Exception e){if(running&&turn==generation){android.util.Log.e("TouchXboxSvc","Transport stopped",e);status=(connected?"连接中断: ":"启动失败: ")+e.getMessage();main.post(()->{Toast.makeText(this,status,Toast.LENGTH_LONG).show();stopSelf();});break;}}
                finally{c.close();active=false;}
            }
        },"TouchXbox transport").start();return START_NOT_STICKY;
    }
    private void reload(){state.reset();remove();LayoutProfile next=new LayoutStore(this).active();if(next==null){stopSelf();return;}boolean reconnect=next.protocol!=requestedProtocol||!next.keyboard.isEmpty()!=requestedKeyboard;profile=next;state.configure(next.protocol);if(reconnect){requestedProtocol=next.protocol;requestedKeyboard=!next.keyboard.isEmpty();generation++;active=false;BridgeClient c=client;if(c!=null)c.close();configureMotion();}else show();}
    private void configureMotion(){DeviceMotion old=motion;motion=null;if(old!=null)old.close();Point size=ScreenSpace.size(this);orientationBlocked=profile.landscapeOnly&&size.x<=size.y;if(requestedProtocol==1||requestedProtocol==2){DeviceMotion sensor=new DeviceMotion(this);sensor.setEnabled(!collapsed&&!orientationBlocked);motion=sensor;}}
    private void updateNotification(){notification.setContentTitle(LauncherIdentity.name(LauncherIdentity.current(this))+" 悬浮手柄");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(7,notification.build());}
    private void verifyDevice(int turn,int protocol,int attempts){if(!running||generation!=turn)return;boolean found=false;for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&ControllerProtocol.matches(protocol,d.getVendorId(),d.getProductId())){found=true;break;}}if(found){LauncherIdentity.activate(this,protocol);updateNotification();}else{if(attempts<60)main.postDelayed(()->verifyDevice(turn,protocol,attempts+1),150);else{status="系统驱动未建立此协议的输入设备";Toast.makeText(this,status,1).show();stopSelf();}}}
    private WindowManager.LayoutParams params(int w,int h,int x,int y,String title){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;ScreenSpace.position(p,viewport,x,y);p.alpha=1;p.setTitle("TouchXbox "+title);return p;
    }
    private void show(){
        viewport=ScreenSpace.current(this);width=viewport.width;height=viewport.height;if(profile==null){status="请先添加布局模板";stopSelf();return;}orientationBlocked=profile.landscapeOnly&&width<=height;DeviceMotion sensor=motion;if(sensor!=null)sensor.setEnabled(!collapsed&&!orientationBlocked);if(orientationBlocked)status="等待横屏，输入已暂停";LayoutProfile.Page page=profile.page(width>height);
        // Empty-half windows are underneath all buttons. InputDispatcher gives buttons and
        // the fold region priority, and FLAG_SPLIT_TOUCH preserves independent fingers.
        for(LayoutProfile.Spec s:profile.specs())if(profile.floating(s)){
            LayoutProfile.Box b=page.box(s,width,height);Control c=new Control(s,true,Math.min(b.w,b.h));
            int left=s.code==0?0:width/2;add(c,params(s.code==0?width/2:width-width/2,height,left,0,s.name+" floating"));
        }
        for(LayoutProfile.Spec s:profile.specs())if(profile.visible(s)&&!profile.floating(s)){
            LayoutProfile.Box b=page.box(s,width,height).pixels(width,height);Control c=new Control(s,false,Math.min(b.w,b.h));add(c,params(Math.round(b.w),Math.round(b.h),Math.round(b.x),Math.round(b.y),s.name));
        }
        LayoutProfile.Box f=page.fold(width,height).pixels(width,height);fold=new FoldRegion();wm.addView(fold,params(Math.round(f.w),Math.round(f.h),Math.round(f.x),Math.round(f.y),"fold region"));
        applyVisibility();
    }
    private void add(Control c,WindowManager.LayoutParams p){controls.add(c);wm.addView(c,p);}
    private void applyVisibility(){for(Control c:controls)c.setVisibility(collapsed||orientationBlocked?View.GONE:View.VISIBLE);if(fold!=null)fold.setVisibility(collapsed||orientationBlocked?View.GONE:View.VISIBLE);}
    private void setCollapsed(boolean value){state.reset();for(Control c:controls)c.release(true);collapsed=value;DeviceMotion sensor=motion;if(sensor!=null)sensor.setEnabled(!value&&!orientationBlocked);applyVisibility();if(!value)BubbleActivity.dismiss(this);status=value?"已收起，输入已释放":orientationBlocked?"等待横屏，输入已暂停":ControllerProtocol.NAMES[requestedProtocol]+" 已连接";}
    private void collapseToBubble(){setCollapsed(true);try{BubbleActivity.open(this);}catch(Exception e){android.util.Log.e("TouchXboxBubble","Open failed",e);Toast.makeText(this,"系统悬浮球启动失败，可从通知展开",1).show();}}
    private void remove(){for(Control c:controls){c.release(true);try{wm.removeView(c);}catch(Exception ignored){}}controls.clear();if(fold!=null){try{wm.removeView(fold);}catch(Exception ignored){}fold=null;}}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(active&&fold!=null){state.reset();remove();show();}}
    @Override public void onDestroy(){running=false;state.reset();BridgeClient c=client;if(c!=null)c.close();DeviceMotion sensor=motion;motion=null;if(sensor!=null)sensor.close();active=false;remove();BubbleActivity.dismiss(this);if(receiverRegistered)unregisterReceiver(screenOff);stopForeground(true);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}

    private final class FoldRegion extends View {
        float downX,downY;boolean tap;
        FoldRegion(){super(OverlayService.this);setContentDescription("收起手柄区域");}
        @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();if(a==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();tap=true;return true;}
            if(a==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())tap=false;
            if(a==MotionEvent.ACTION_POINTER_DOWN||a==MotionEvent.ACTION_CANCEL)tap=false;
            if(a==MotionEvent.ACTION_UP){if(tap&&e.getX()>=0&&e.getY()>=0&&e.getX()<getWidth()&&e.getY()<getHeight())main.post(()->collapseToBubble());tap=false;}return true;}
    }
    private final class Control extends View {
        final LayoutProfile.Spec spec;final boolean floating;final float diameter;
        final PadPainter painter=new PadPainter();final StickGesture stick;final ButtonChord chord=new ButtonChord();final ButtonPress button=new ButtonPress();int chordMask;
        int pointer=-1,auxLevel;boolean pressed,inputDown,foldTouch,foldTap;float value,cx,cy,foldDownX,foldDownY;
        final Runnable hold;
        Control(LayoutProfile.Spec s,boolean dynamic,float size){super(OverlayService.this);spec=s;floating=dynamic;diameter=size;auxLevel=s.kind==6?1:0;value=auxLevel/2f;stick=new StickGesture(profile.throttle(s),profile.squareStick(s));setContentDescription(profile.label(s));hold=()->{if(stick.hold(SystemClock.uptimeMillis())){profile.activateLabel(spec.name);state.button(spec.code==1?PadState.RS:PadState.LS,true);performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);invalidate();}};}
        @Override protected void onDraw(Canvas c){
            if(floating){if(!stick.active)return;c.save();c.translate(cx-diameter/2,cy-diameter/2);painter.draw(c,spec,diameter,diameter,true,stick.x,stick.y,0,stick.clicked,profile.opacity,0,profile);c.restore();}
            else painter.draw(c,spec,getWidth(),getHeight(),pressed,stick.x,stick.y,value,stick.clicked,profile.opacity,chordMask,profile);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();if(collapsed||orientationBlocked)return false;
            // Enforce this in the gesture handler as well as window stacking: no
            // floating stick can acquire a finger inside the saved fold rectangle.
            if(floating&&(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)){
                int i=e.getActionIndex();if(inFold(e.getX(i),e.getY(i))){if(stick.active)release();pointer=e.getPointerId(i);foldTouch=foldTap=true;foldDownX=e.getX(i);foldDownY=e.getY(i);return true;}
            }
            if(foldTouch){if(action==MotionEvent.ACTION_CANCEL){foldTouch=foldTap=false;pointer=-1;return true;}int i=e.findPointerIndex(pointer);if(i<0)return true;
                if(action==MotionEvent.ACTION_MOVE&&Math.hypot(e.getX(i)-foldDownX,e.getY(i)-foldDownY)>ViewConfiguration.get(getContext()).getScaledTouchSlop())foldTap=false;
                if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer)){boolean tap=foldTap&&inFold(e.getX(i),e.getY(i));foldTouch=foldTap=false;pointer=-1;if(tap)main.post(()->collapseToBubble());}return true;}
            if(spec.kind==4)return chordTouch(e);
            if(action==MotionEvent.ACTION_DOWN){pointer=e.getPointerId(0);pressed=true;button.begin(profile.holdsOutside(spec));
                if(spec.kind==1){cx=floating?e.getX():getWidth()/2f;cy=floating?e.getY():getHeight()/2f;if(floating&&profile.throttle(spec))cy-=stick.y*(diameter/2-3)*.60f;stick.begin(e.getX(),e.getY(),cx,cy,diameter*.36f,ViewConfiguration.get(getContext()).getScaledTouchSlop(),SystemClock.uptimeMillis());main.postDelayed(hold,StickGesture.HOLD_MS);}
                update(e.getX(),e.getY());invalidate();return true;}
            if(action==MotionEvent.ACTION_CANCEL){release();return true;}
            int index=e.findPointerIndex(pointer);if(index<0)return true;
            if(action==MotionEvent.ACTION_UP||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer)){release();return true;}
            if(action==MotionEvent.ACTION_MOVE){update(e.getX(index),e.getY(index));invalidate();}return true;
        }
        boolean inFold(float x,float y){return profile.inFoldRegion(x+(spec.code==1?width/2:0),y,width,height);}
        void update(float px,float py){
            boolean inside=px>=0&&py>=0&&px<getWidth()&&py<getHeight();
            if(spec.kind==0||spec.kind==5){pressed=button.move(inside);if(pressed&&!inputDown)profile.activateLabel(profile.displayKey(spec));inputDown=pressed;if(spec.kind==0)state.button(profile.mapButton(spec.code),pressed);else state.keyboard(spec.name,spec.code,pressed);}
            if(spec.kind==6){pressed=px>=0&&py>=0&&px<getWidth()&&py<getHeight();if(!inputDown){auxLevel=(auxLevel+1)%3;state.auxiliary(spec.code,auxLevel);profile.activateLabel(spec.name);inputDown=true;}value=auxLevel/2f;}
            if(spec.kind==1){stick.move(px,py);state.stick(spec.code==1,stick.x,stick.y);}
            if(spec.kind==2){if(!inside&&profile.holdsOutside(spec))return;pressed=inside;int before=directionMask((int)value);value=inside?PadState.direction((px-getWidth()/2f)/(getWidth()/2f),(py-getHeight()/2f)/(getHeight()/2f)):0;int next=directionMask((int)value);for(int i=0;i<4;i++)if((next&~before&(1<<i))!=0)profile.activateLabel("D_"+i);state.hat((int)value);}
            if(spec.kind==3){if(!inside&&profile.holdsOutside(spec))return;pressed=inside;value=!inside?0:profile.triggerClick(spec)?1:Math.max(0,Math.min(1,py/getHeight()));if(value>0&&!inputDown)profile.activateLabel(spec.name);inputDown=value>0;state.trigger(spec.code==1,value);}
        }
        int directionMask(int hat){return (hat==1||hat==2||hat==8?1:0)|(hat>=2&&hat<=4?2:0)|(hat>=4&&hat<=6?4:0)|(hat>=6&&hat<=8?8:0);}
        private boolean chordTouch(MotionEvent e){int a=e.getActionMasked(),index=e.getActionIndex();if(a==MotionEvent.ACTION_DOWN)chord.reset();if(a==MotionEvent.ACTION_CANCEL||a==MotionEvent.ACTION_UP)chord.reset();else if(a==MotionEvent.ACTION_POINTER_UP)chord.up(e.getPointerId(index));else if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN)chord.point(e.getPointerId(index),e.getX(index)/getWidth(),e.getY(index)/getHeight(),profile.holdsOutside(spec));else if(a==MotionEvent.ACTION_MOVE)for(int i=0;i<e.getPointerCount();i++)chord.point(e.getPointerId(i),e.getX(i)/getWidth(),e.getY(i)/getHeight(),profile.holdsOutside(spec));syncChord();return true;}
        private void syncChord(){int next=profile.mapFaceMask(chord.mask());if(next!=chordMask){for(int bit:new int[]{PadState.A,PadState.B,PadState.X,PadState.Y})if((next&~chordMask&(1<<bit))!=0)profile.activateLabel(LayoutProfile.buttonName(bit));state.faceButtons(next);}chordMask=next;pressed=next!=0;invalidate();}
        void release(){release(false);}
        void release(boolean reset){main.removeCallbacks(hold);button.end();pointer=-1;pressed=inputDown=foldTouch=foldTap=false;if(spec.kind==6){if(reset){auxLevel=1;state.auxiliary(spec.code,1);}value=auxLevel/2f;}else value=0;if(reset)stick.reset();else stick.end();if(spec.kind==0)state.button(profile.mapButton(spec.code),false);if(spec.kind==5)state.keyboard(spec.name,spec.code,false);if(spec.kind==1){state.stick(spec.code==1,stick.x,stick.y);state.button(spec.code==1?PadState.RS:PadState.LS,false);}if(spec.kind==2)state.hat(0);if(spec.kind==3)state.trigger(spec.code==1,0);if(spec.kind==4){chord.reset();syncChord();}invalidate();}
    }
}
