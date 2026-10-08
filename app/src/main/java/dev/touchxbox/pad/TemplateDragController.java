package dev.touchxbox.pad;

import android.content.ClipData;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridView;

/** A dedicated drag handle keeps template management on the card's normal long press. */
final class TemplateDragController implements View.OnDragListener {
    interface Callbacks {
        String idAt(int position);
        boolean move(String id,String targetId);
    }
    private static final class Session {
        final String id;final View card;
        Session(String id,View card){this.id=id;this.card=card;}
    }
    private final GridView grid;
    private final Callbacks callbacks;
    private final int touchSlop,edge,scrollStep;
    private Session session;
    private View targetView;
    private String targetId;
    private float dragX,dragY;
    private boolean inside;
    private final Runnable scroll=new Runnable(){public void run(){
        if(session==null||!inside)return;
        int direction=scrollDirection();
        if(direction==0||!grid.canScrollVertically(direction))return;
        grid.scrollListBy(direction*scrollStep);updateTarget();grid.postDelayed(this,16);
    }};

    TemplateDragController(GridView grid,Callbacks callbacks){
        this.grid=grid;this.callbacks=callbacks;
        float density=grid.getResources().getDisplayMetrics().density;
        touchSlop=ViewConfiguration.get(grid.getContext()).getScaledTouchSlop();
        edge=Math.round(52*density);scrollStep=Math.max(1,Math.round(7*density));
        grid.setOnDragListener(this);
    }
    void bind(View card,View handle,String id){
        final float[] down=new float[2];
        handle.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    down[0]=event.getX();down[1]=event.getY();
                    grid.requestDisallowInterceptTouchEvent(true);break;
                case MotionEvent.ACTION_MOVE:
                    if(Math.hypot(event.getX()-down[0],event.getY()-down[1])>touchSlop){
                        if(begin(card,id)){v.setPressed(false);return true;}
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    grid.requestDisallowInterceptTouchEvent(false);break;
            }
            return session!=null;
        });
        handle.setOnLongClickListener(v->{boolean started=begin(card,id);if(started)v.setPressed(false);return started;});
    }
    private boolean begin(View card,String id){
        if(session!=null||grid.getCount()<2)return false;
        session=new Session(id,card);
        boolean started=card.startDragAndDrop(ClipData.newPlainText("TouchXbox template",id),new View.DragShadowBuilder(card),session,0);
        if(!started)clear();
        return started;
    }
    @Override public boolean onDrag(View view,DragEvent event){
        if(session==null||event.getLocalState()!=session)return false;
        switch(event.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:
                session.card.setAlpha(.4f);return true;
            case DragEvent.ACTION_DRAG_ENTERED:
            case DragEvent.ACTION_DRAG_LOCATION:
                inside=true;dragX=event.getX();dragY=event.getY();updateTarget();
                grid.removeCallbacks(scroll);grid.post(scroll);return true;
            case DragEvent.ACTION_DROP:
                dragX=event.getX();dragY=event.getY();updateTarget();
                // Persistence happens only here. Leaving the grid or cancelling changes nothing.
                return targetId!=null&&callbacks.move(session.id,targetId);
            case DragEvent.ACTION_DRAG_EXITED:
                inside=false;grid.removeCallbacks(scroll);clearTarget();return true;
            case DragEvent.ACTION_DRAG_ENDED:
                clear();return true;
            default:return true;
        }
    }
    private void updateTarget(){
        clearTarget();
        if(dragX<0||dragY<0||dragX>=grid.getWidth()||dragY>=grid.getHeight())return;
        int position=grid.pointToPosition((int)dragX,(int)dragY);
        if(position==GridView.INVALID_POSITION){
            // Gaps and padding belong to the nearest visible tile, including the last partial row.
            float distance=Float.MAX_VALUE;
            for(int i=0;i<grid.getChildCount();i++){
                View child=grid.getChildAt(i);
                if(child.getBottom()<=0||child.getTop()>=grid.getHeight())continue;
                float dx=dragX-(child.getLeft()+child.getRight())/2f,dy=dragY-(child.getTop()+child.getBottom())/2f;
                float candidate=dx*dx+dy*dy;
                if(candidate<distance){distance=candidate;position=grid.getFirstVisiblePosition()+i;}
            }
        }
        if(position<0||position>=grid.getCount())return;
        targetId=callbacks.idAt(position);
        targetView=grid.getChildAt(position-grid.getFirstVisiblePosition());
        if(targetView!=null&&!session.id.equals(targetId))targetView.setActivated(true);
    }
    private int scrollDirection(){return dragY<edge?-1:dragY>grid.getHeight()-edge?1:0;}
    private void clearTarget(){if(targetView!=null)targetView.setActivated(false);targetView=null;targetId=null;}
    private void clear(){
        grid.removeCallbacks(scroll);clearTarget();inside=false;
        if(session!=null)session.card.setAlpha(1);session=null;
        grid.requestDisallowInterceptTouchEvent(false);
    }
    void cancel(){if(session!=null)grid.cancelDragAndDrop();clear();}
}
