package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.UUID;
import java.util.*;
import android.text.*;

/** All layout changes happen in this Activity, on an isolated draft. */
public final class EditorActivity extends Activity {
    private LayoutStore store;private LayoutProfile draft;private String saved;
    private boolean wide=true,previewDown;private String selected="FOLD";
    private EditText name;private TextView detail,rangeText;private Button orientation,protocol;
    private LinearLayout properties;private Preview preview;
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new LayoutStore(this);
        if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("HIDE"));
        draft=b!=null?LayoutStore.decode(b.getString("draft")):getIntent().getBooleanExtra("new",false)?new LayoutProfile():store.active();
        if(draft==null)draft=new LayoutProfile();
        if(b==null&&getIntent().getBooleanExtra("new",false))draft.name=store.uniqueName("新模板");
        store.prepareSize(draft);
        saved=b==null?LayoutStore.encode(draft):b.getString("saved");if(b!=null){wide=b.getBoolean("wide",true);selected=b.getString("selected","FOLD");}
        FullScreen.apply(getWindow());getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),0,dp(12),0);root.setBackgroundColor(0xFF0E1915);setContentView(root);
        LinearLayout header=row(root,52);button(header,"返回",()->onBackPressed(),72);
        name=new EditText(this);name.setText(draft.name);name.setTextColor(Color.WHITE);name.setTextSize(17);name.setSingleLine(true);name.setSelectAllOnFocus(true);name.setHint("模板名称");name.setContentDescription("模板名称");header.addView(name,new LinearLayout.LayoutParams(0,-1,1));
        button(header,"预览",()->openPreview(),80);button(header,"保存",()->save(false,false),80);button(header,"另存为",()->saveAs(),94);
        LinearLayout modes=row(root,46);orientation=button(modes,"",()->{wide=!wide;select("FOLD");updateModes();},116);
        protocol=button(modes,"",()->chooseProtocol(),226);button(modes,"+ 键盘 / 手柄",()->addKeyboard(),136);
        CheckBox landscapeOnly=new CheckBox(this);landscapeOnly.setText("仅横屏启用");landscapeOnly.setTextColor(0xFFB4C8BD);landscapeOnly.setChecked(draft.landscapeOnly);landscapeOnly.setOnCheckedChangeListener((v,on)->draft.landscapeOnly=on);modes.addView(landscapeOnly,new LinearLayout.LayoutParams(dp(140),-1));TextView hint=label("点组件修改 · 拖动布局",13,0xFF9CB3A6);modes.addView(hint,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout body=new LinearLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));preview=new Preview();body.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView panel=new ScrollView(this);body.addView(panel,new LinearLayout.LayoutParams(dp(226),-1));properties=new LinearLayout(this);properties.setOrientation(1);properties.setPadding(dp(12),dp(8),0,dp(8));panel.addView(properties);
        updateModes();select(selected);
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout row(LinearLayout parent,int height){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);parent.addView(r,new LinearLayout.LayoutParams(-1,dp(height)));return r;}
    private Button button(LinearLayout p,String text,Runnable action,int width){Button b=new Button(this);b.setText(text);b.setTextColor(0xFFB9FBD6);b.setTextSize(13);b.setAllCaps(false);b.setPadding(0,0,0,0);p.addView(b,new LinearLayout.LayoutParams(dp(width),dp(44)));b.setOnClickListener(v->{hideKeyboard();action.run();});return b;}
    private TextView label(String text,int sp,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(0,dp(4),0,dp(4));return t;}
    private void updateModes(){orientation.setText(wide?"横屏布局 ⇄":"竖屏布局 ⇄");protocol.setText((draft.protocol==ControllerProtocol.FPV&&draft.fpvXbox?"FPV · Xbox 输出":ControllerProtocol.NAMES[draft.protocol])+" ▾");preview.orientation(wide);preview.invalidate();}
    private void chooseProtocol(){new AlertDialog.Builder(this).setTitle("手柄协议").setSingleChoiceItems(ControllerProtocol.NAMES,draft.protocol,(d,which)->{draft.protocol=which;if(!selected.equals("FOLD")&&!draft.available(LayoutProfile.spec(selected)))selected="FOLD";updateModes();d.dismiss();String info=which==0?"Xbox 360：按键、摇杆、模拟扳机。此协议不包含体感。":which==1?"NS Pro：按键、摇杆和体感报告；ZL / ZR 为数字按键。系统能否读取体感取决于 Nintendo 驱动版本。":which==2?"PS / DualShock 4：按键、摇杆、模拟扳机与体感。": "FPV USB 遥控器：默认美国手，点选任一摇杆可切换日本手或中国手。CH5～CH8 为四个可选辅助通道，点按切换 0% / 50% / 100%。油门所在摇杆初始在底部；松手左右回中、上下保持，再次触摸从上次油门位置继续拖动。";new AlertDialog.Builder(this).setTitle(ControllerProtocol.NAMES[which]).setMessage(info+((which==1||which==2)?"\n\n"+DeviceMotion.availability(this)+"\n缺失的传感器不会生成测量值。":"")).setPositiveButton("确定",null).show();select(selected);}).setNegativeButton("取消",null).show();}
    private void addKeyboard(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),0,dp(20),dp(12));TextView summary=label("手柄点亮保留，取消高亮删除 · 键盘多选新增",13,0xFFB4C8BD);body.addView(summary);
        final KeyboardPicker[] ref=new KeyboardPicker[1];final ControllerPicker[] gamepad=new ControllerPicker[1];final AlertDialog[] dialogs=new AlertDialog[1];Runnable update=()->{int n=ref[0].selection().size()+gamepad[0].selection().size();summary.setText("手柄保留 "+gamepad[0].selection().size()+" 项 · 新增键盘 "+ref[0].selection().size()+" 个");if(dialogs[0]!=null){dialogs[0].getButton(-1).setEnabled(true);dialogs[0].getButton(-1).setText("应用选择");}};body.addView(label(ControllerProtocol.NAMES[draft.protocol]+" · 点亮保留，取消高亮删除，再点恢复",12,0xFF9CB3A6));gamepad[0]=new ControllerPicker(this,draft,update);body.addView(gamepad[0],new LinearLayout.LayoutParams(-1,-2));body.addView(label("键盘 · 多选新增，所选按键高亮",12,0xFF9CB3A6));KeyboardPicker picker=new KeyboardPicker(this,32-draft.keyboard.size(),update);ref[0]=picker;body.addView(picker,new LinearLayout.LayoutParams(-1,-2));ScrollView scroller=new ScrollView(this);scroller.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加按键").setView(scroller).setNegativeButton("取消",null).setPositiveButton("应用选择",(d,w)->{
            Set<String> kept=gamepad[0].selection();for(LayoutProfile.Spec s:LayoutProfile.SPECS)if(s.kind!=4&&s.kind!=5&&draft.available(s)){if(kept.contains(s.name))draft.hiddenButtons.remove(s.name);else draft.hiddenButtons.add(s.name);}
            Set<Integer> chosen=picker.selection();float minX=99,maxX=0,minY=99,maxY=0;for(int code:chosen){KeyboardPicker.Cap cap=KeyboardPicker.cap(code);minX=Math.min(minX,cap.x+cap.w/2);maxX=Math.max(maxX,cap.x+cap.w/2);minY=Math.min(minY,cap.row);maxY=Math.max(maxY,cap.row);}String first=null;
            for(int code:chosen){KeyboardPicker.Cap cap=KeyboardPicker.cap(code);LayoutProfile.Spec key=draft.addKey(code);if(first==null)first=key.name;for(boolean direction:new boolean[]{true,false}){LayoutProfile.Page page=draft.page(direction);LayoutProfile.Item item=page.items.get(key.name);float dx=Math.min(direction?.09f:.13f,.7f/(maxX-minX+1)),dy=Math.min(direction?.14f:.085f,.45f/(maxY-minY+1));item.x=.5f+(cap.x+cap.w/2-(minX+maxX)/2)*dx;item.y=.4f+(cap.row-(minY+maxY)/2)*dy;}}select(first==null?"FOLD":first);
        }).create();dialog.setOnShowListener(d->{dialogs[0]=dialog;dialog.getButton(-1).setEnabled(true);dialog.getWindow().setLayout(Math.round(getResources().getDisplayMetrics().widthPixels*.96f),-2);});dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();
    }
    private void editLabels(String key,String fallback){
        String[] values=draft.labels.get(key);if(values==null)values=new String[]{fallback,fallback};LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(24),0,dp(24),0);
        body.addView(label("普通状态名称",13,0xFFB4C8BD));EditText up=new EditText(this);up.setText(values[0]);up.setSingleLine(true);up.setSelectAllOnFocus(true);up.setContentDescription("普通名称");up.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});body.addView(up);
        CheckBox toggle=new CheckBox(this);toggle.setText("点按切换高亮");toggle.setChecked(draft.toggleLabels.contains(key));body.addView(toggle);
        CheckBox same=new CheckBox(this);same.setText("高亮名称与普通一致");same.setChecked(values[0].equals(values[1]));body.addView(same);body.addView(label("高亮状态名称",13,0xFFB4C8BD));EditText down=new EditText(this);down.setText(values[1]);down.setSingleLine(true);down.setSelectAllOnFocus(true);down.setContentDescription("高亮名称");down.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});down.setEnabled(!same.isChecked());body.addView(down);
        same.setOnCheckedChangeListener((v,on)->{down.setEnabled(!on);if(on)down.setText(up.getText());});up.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(same.isChecked())down.setText(s);}public void afterTextChanged(Editable e){}});
        body.addView(label("高亮只改变显示，不会持续按住。实际输入："+fallback,12,0xFF9CB3A6));ScrollView scroller=new ScrollView(this);scroller.addView(body);new AlertDialog.Builder(this).setTitle("名称与高亮").setView(scroller).setNegativeButton("取消",null).setNeutralButton("恢复默认",(d,w)->{draft.labels.remove(key);draft.toggleLabels.remove(key);select(selected);}).setPositiveButton("应用",(d,w)->{draft.labels.put(key,new String[]{up.getText().toString(),same.isChecked()?up.getText().toString():down.getText().toString()});if(toggle.isChecked())draft.toggleLabels.add(key);else draft.toggleLabels.remove(key);select(selected);}).show();
    }
    private AlertDialog editFpvAux(int channel){
        LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),0,dp(20),dp(8));
        TextView summary=label("",13,0xFFB4C8BD);body.addView(summary);Button[] levels=new Button[3];
        Runnable refresh=()->{int[] bindings=draft.fpvXboxAux.get(channel);summary.setText(bindings==null?"当前默认输出："+FpvAuxMapping.defaultName(channel)+"。选择任一档位的按键后，此通道改用自定义映射。":"自定义按键：进入档位时按住，切换档位时释放；启动、收起和中断时全部释放。未设置的档位不按键。");for(int i=0;i<3;i++)if(levels[i]!=null)levels[i].setText((i*50)+"% · "+(bindings==null?"默认输出":FpvAuxMapping.name(bindings[i])));};
        for(int i=0;i<3;i++){final int level=i;levels[i]=button(body,"",()->chooseFpvBinding(channel,level,refresh),300);levels[i].setContentDescription("CH"+(channel+1)+" "+(i*50)+"% 映射");}
        refresh.run();ScrollView scroll=new ScrollView(this);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("CH"+(channel+1)+" 档位映射").setView(scroll).setNeutralButton("恢复默认输出",(d,w)->{draft.fpvXboxAux.remove(channel);select(selected);}).setPositiveButton("完成",(d,w)->select(selected)).create();dialog.show();return dialog;
    }
    private AlertDialog chooseFpvBinding(int channel,int level,Runnable refresh){
        AlertDialog chooser=new AlertDialog.Builder(this).setTitle("CH"+(channel+1)+" · "+(level*50)+"% 输出").setItems(new String[]{"不按键（释放）","Xbox 按键","键盘按键"},(d,which)->{
            if(which==0){setFpvBinding(channel,level,0);refresh.run();return;}
            chooseFpvKey(channel,level,which==2,refresh);
        }).setNegativeButton("取消",null).create();chooser.show();return chooser;
    }
    private AlertDialog chooseFpvKey(int channel,int level,boolean keyboard,Runnable refresh){
        List<Integer> codes=new ArrayList<>(keyboard?KeyboardKeys.KEYS.keySet():FpvAuxMapping.BUTTONS.keySet());String[] names=new String[codes.size()];
        for(int i=0;i<codes.size();i++)names[i]=keyboard?KeyboardKeys.name(codes.get(i)):FpvAuxMapping.BUTTONS.get(codes.get(i));
        AlertDialog picker=new AlertDialog.Builder(this).setTitle(keyboard?"选择键盘按键":"选择 Xbox 按键").setItems(names,(pick,index)->{setFpvBinding(channel,level,codes.get(index)+(keyboard?FpvAuxMapping.KEYBOARD:0));refresh.run();}).setNegativeButton("取消",null).create();picker.show();return picker;
    }
    private void setFpvBinding(int channel,int level,int binding){
        if(channel<4||channel>7||level<0||level>2||!FpvAuxMapping.valid(binding))throw new IllegalArgumentException("无效的辅助通道映射");
        int[] levels=draft.fpvXboxAux.get(channel);if(levels==null){levels=new int[3];draft.fpvXboxAux.put(channel,levels);}levels[level]=binding;
    }
    private void select(String key){selected=key;properties.removeAllViews();LayoutProfile.Page page=draft.page(wide);
        if(draft.protocol==ControllerProtocol.FPV){
            toggle("模拟 Xbox 协议",draft.fpvXbox,v->{draft.fpvXbox=v==1;updateModes();select(key);});
            properties.addView(label(draft.fpvXbox?"用于 Moonlight 等串流。保留 FPV 布局、方形行程、操控习惯和油门保持。":"关闭时输出原生 FPV 遥控器；开启后输出 Xbox 手柄。",12,0xFFB4C8BD));
            if(draft.fpvXbox)for(int ch=4;ch<8;ch++){final int channel=ch;button(properties,"CH"+(ch+1)+" · "+(draft.fpvXboxAux.containsKey(ch)?"自定义按键":FpvAuxMapping.defaultName(ch)),()->editFpvAux(channel),200);}
        }
        detail=label(key.equals("FOLD")?"收起区域":key+" 控件",19,Color.WHITE);properties.addView(detail);
        if(key.equals("FOLD")){
            properties.addView(label("运行时不可见。点击区域内任意位置，收起到系统悬浮球。",12,0xFFB4C8BD));
            slider("区域宽度",8,60,Math.round(page.foldW*100),v->{page.foldW=v/100f;page.move("FOLD",page.foldX,page.foldY,1,1);});
            slider("区域高度",4,40,Math.round(page.foldH*100),v->{page.foldH=v/100f;page.move("FOLD",page.foldX,page.foldY,1,1);});
        }else{
            LayoutProfile.Spec s=LayoutProfile.spec(key);page.box(s,preview.targetW,preview.targetH);LayoutProfile.Item item=page.items.get(key);
            detail.setText(draft.label(s)+" 控件");
            if(s.kind==4)button(properties,"编辑四个按键名称",()->new AlertDialog.Builder(this).setTitle("选择实际输入").setItems(faceLabels(),(d,i)->{String k=new String[]{"A","B","X","Y"}[i];editLabels(k,ControllerProtocol.label(draft.protocol,k));}).show(),200);
            else if(s.kind==2)button(properties,"编辑方向名称",()->new AlertDialog.Builder(this).setTitle("选择方向").setItems(new String[]{"上","右","下","左"},(d,i)->editLabels("D_"+i,new String[]{"↑","→","↓","←"}[i])).show(),200);
            else {String labelKey=s.kind==0&&s.name.matches("[ABXY]")?LayoutProfile.buttonName(draft.mapButton(s.code)):s.name;button(properties,"编辑按键名称",()->editLabels(labelKey,s.kind==5?KeyboardKeys.name(s.code):ControllerProtocol.label(draft.protocol,labelKey)),200);}
            toggle("预览高亮状态",previewDown,v->{previewDown=v==1;});
            if(LayoutProfile.canHoldOutside(s)){toggle("滑出后保持按下",draft.holdsOutside(s),v->{if(v==1)draft.holdOutside.add(key);else draft.holdOutside.remove(key);});properties.addView(label("开启后滑出范围仍保持，抬手释放；收起或中断也会释放。",12,0xFFB4C8BD));}
            if(s.kind==5){properties.addView(label("键盘输入："+KeyboardKeys.name(s.code)+"\n松手释放；滑出行为由上方开关控制。",12,0xFFB4C8BD));button(properties,"删除这个键盘按键",()->{draft.removeKey(key);select("FOLD");},200);}
            else button(properties,"删除按键",()->{if(s.kind==4)new AlertDialog.Builder(this).setTitle("删除组合按键").setItems(new String[]{"整组",faceLabels()[0],faceLabels()[1],faceLabels()[2],faceLabels()[3]},(d,i)->{if(i==0)draft.hiddenButtons.addAll(Arrays.asList("A","B","X","Y"));else draft.hiddenButtons.add(new String[]{"","A","B","X","Y"}[i]);select("FOLD");}).show();else{draft.hiddenButtons.add(draft.displayKey(s));select("FOLD");}},200);
            if(s.kind==1)styles("摇杆样式",new String[]{"固定位置",s.code==0?"左半屏浮动":"右半屏浮动"},draft.floating(s)?1:0,v->{if(s.code==0)draft.leftFloating=v==1;else draft.rightFloating=v==1;select(key);});
            if(draft.squareStick(s)){
                int mode=FpvMode.valid(draft.fpvMode);
                styles("FPV 操控习惯",new String[]{"美国手（默认）","日本手","中国手"},mode==FpvMode.AMERICAN?0:mode==FpvMode.JAPANESE?1:2,v->{draft.fpvMode=new int[]{FpvMode.AMERICAN,FpvMode.JAPANESE,FpvMode.CHINESE}[v];select(key);});
                properties.addView(label(FpvMode.description(draft.fpvMode),12,0xFFB4C8BD));
            }
            if(s.kind==2)styles("方向键样式",new String[]{"圆形","十字键","八边形"},draft.dpadStyle,v->{draft.dpadStyle=v;select(key);});
            if(s.kind==3)styles("扳机样式",new String[]{"向下滑动（模拟量）","点击（按下满值）"},draft.triggerClick(s)?1:0,v->{if(s.code==0)draft.leftTriggerClick=v==1;else draft.rightTriggerClick=v==1;select(key);});
            if(s.kind==4||s.name.matches("[ABXY]")){
                styles("组合按键样式",new String[]{"四个独立圆键","八边形组合"},draft.abxyStyle,v->{draft.abxyStyle=v;select(v==1?"ABXY":"A");});
                TextView order=label(faceHint(s.kind==4),12,0xFFB4C8BD);
                toggle("互换 "+faceLabels()[0]+" / "+faceLabels()[1],draft.swapAB,v->{draft.swapAB=v==1;detail.setText(draft.label(s)+" 控件");order.setText(faceHint(s.kind==4));});
                toggle("互换 "+faceLabels()[2]+" / "+faceLabels()[3],draft.swapXY,v->{draft.swapXY=v==1;detail.setText(draft.label(s)+" 控件");order.setText(faceHint(s.kind==4));});
                properties.addView(order);
            }
            if(draft.throttle(s))properties.addView(label("油门启动时在底部。上下保持，左右回中；每次触摸从上次油门高度继续拖动。固定与浮动样式都适用。",12,0xFFB4C8BD));
            if(s.kind==1&&!draft.throttle(s))properties.addView(label(draft.floating(s)?"这一侧无按键的空白处均可起摇杆；触点就是中心。预览图形只表示尺寸。":"固定中心，拖动拨杆。",12,0xFFB4C8BD));
            if(draft.squareStick(s))properties.addView(label("正方形操作范围，两轴独立到达满值。"+(draft.throttle(s)?"":"松手后两轴都回中。"),12,0xFFB4C8BD));
            if(s.kind==6)properties.addView(label("辅助通道：点按依次切换 0%、50%、100%，松手保留当前档位。初始和收起后位于 50% 中档。"+(draft.fpvXbox?(draft.fpvXboxAux.containsKey(s.code)?"\n当前使用自定义按键；启动、收起或中断后，按键全部释放。可在上方 CH 映射中修改。":"\n默认 Xbox 输出：CH5 / CH6 对应 LT / RT；CH7 对应左 / 松开 / 右，CH8 对应下 / 松开 / 上。可在上方改为按键或键盘。"):""),12,0xFFB4C8BD));
            if(s.kind==3)properties.addView(label(draft.triggerClick(s)?"按住即满值，松手归零；滑出行为由上方开关控制。":"向下滑动增加扳机力度，松手归零。",12,0xFFB4C8BD));
            if(LayoutProfile.isShoulder(s)){
                styles("四肩键位置联动",new String[]{"关闭（独立移动）","左右对称 · 上下排列","左右对称 · 并列排列"},page.shoulderLayout,v->{page.setShoulderLayout(v,preview.targetW,preview.targetH);select(key);});
                slider("宽度（四键联动）",50,180,Math.round(page.shoulderWidth*100),v->page.shoulderWidth=v/100f);
                slider(s.kind==3?"高度（LT / RT 联动）":"高度（LB / RB 联动）",40,220,Math.round((s.kind==3?page.triggerHeight:page.bumperHeight)*100),v->{if(s.kind==3)page.triggerHeight=v/100f;else page.bumperHeight=v/100f;});
            }else if(s.kind==5)slider("键盘尺寸（全部联动）",55,165,Math.round(page.keyboardScale*100),v->{page.keyboardScale=v/100f;});
            else slider(s.kind==1?"摇杆尺寸":s.kind==4?"组合尺寸":"控件尺寸",55,165,Math.round(item.scale*100),v->{item.scale=v/100f;page.move(key,item.x,item.y,preview.targetW,preview.targetH);});
            if(s.kind==1)properties.addView(label("原地长按 0.5 秒按下 "+(s.code==0?"L3":"R3")+"，随后可拖动，松手释放。正常连续拖动不会自动按下。",12,0xFFB4C8BD));
            if(LayoutProfile.isShoulder(s))button(properties,"恢复肩键上下排布",()->{page.shoulderLayout=0;page.restore("LT","LB","RT","RB");page.shoulderWidth=page.triggerHeight=page.bumperHeight=1;select(key);},200);
            if(s.name.matches("VIEW|MENU|XBOX"))button(properties,"恢复三键底部排布",()->{page.restore("VIEW","XBOX","MENU");select(key);},200);
        }
        slider("显示不透明度",25,90,Math.round(draft.opacity*100),v->draft.opacity=v/100f);
        rangeText=label("",12,0xFFEABD73);properties.addView(rangeText);
        button(properties,"选择收起区域",()->select("FOLD"),200);
        button(properties,"重置当前方向",()->new AlertDialog.Builder(this).setMessage("重置当前方向的按键位置、尺寸和收起区域？保存后才会替换模板。").setNegativeButton("取消",null).setPositiveButton("重置",(d,w)->{if(wide)draft.landscape=new LayoutProfile.Page();else draft.portrait=new LayoutProfile.Page();store.prepareSize(draft);select("FOLD");}).show(),200);
        properties.addView(label("位置按画布宽高比例保存，尺寸按短边等比缩放。横竖屏分别保存，复制后自动适配其他设备。",11,0xFF8DA496));preview.selection(selected,previewDown);preview.invalidate();
    }
    private String[] faceLabels(){return new String[]{ControllerProtocol.label(draft.protocol,"A"),ControllerProtocol.label(draft.protocol,"B"),ControllerProtocol.label(draft.protocol,"X"),ControllerProtocol.label(draft.protocol,"Y")};}
    private String faceHint(boolean combined){String order="上 "+draft.label(LayoutProfile.spec("Y"))+" · 左 "+draft.label(LayoutProfile.spec("X"))+" · 右 "+draft.label(LayoutProfile.spec("B"))+" · 下 "+draft.label(LayoutProfile.spec("A"));return combined?order+"。八边形内分为四个触区，支持多指组合和跨区滑动。":"字母显示与实际输入同步互换，设置随模板保存。";}
    interface Change{void set(int value);}
    private void toggle(String title,boolean checked,Change change){CheckBox box=new CheckBox(this);box.setText(title);box.setTextSize(14);box.setTextColor(0xFFB9FBD6);box.setChecked(checked);properties.addView(box,new LinearLayout.LayoutParams(-1,dp(44)));box.setOnCheckedChangeListener((v,on)->{change.set(on?1:0);preview.invalidate();});}
    private void styles(String title,String[] labels,int selected,Change change){properties.addView(label(title,13,0xFFB4C8BD));Spinner spinner=new Spinner(this);spinner.setContentDescription(title);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);properties.addView(spinner,new LinearLayout.LayoutParams(-1,dp(48)));spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int pos,long id){if(pos!=selected)change.set(pos);}public void onNothingSelected(AdapterView<?> a){}});}
    private void slider(String title,int min,int max,int value,Change change){TextView t=label(title+"  "+value+"%",13,0xFFB4C8BD);properties.addView(t);SeekBar b=new SeekBar(this);b.setContentDescription(title);b.setMax(max-min);b.setProgress(value-min);properties.addView(b,new LinearLayout.LayoutParams(-1,dp(36)));b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user){change.set(p+min);t.setText(title+"  "+(p+min)+"%");preview.invalidate();}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});}
    private void hideKeyboard(){name.clearFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(name.getWindowToken(),0);}
    private void saveAs(){EditText text=new EditText(this);text.setSingleLine(true);text.setText(name.getText()+" 副本");text.selectAll();AlertDialog d=new AlertDialog.Builder(this).setTitle("另存为新模板").setView(text).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{String oldId=draft.id,oldName=name.getText().toString();draft.id=UUID.randomUUID().toString();name.setText(text.getText());if(save(true,false))d.dismiss();else{draft.id=oldId;name.setText(oldName);}}));d.show();}
    private boolean save(boolean copy,boolean leave){
        for(boolean landscape:new boolean[]{true,false}){Point dim=ScreenSpace.forOrientation(this,landscape);String collision=draft.foldConflict(landscape,dim.x,dim.y);if(collision!=null){wide=landscape;select("FOLD");updateModes();Toast.makeText(this,(wide?"横屏":"竖屏")+"收起区域与 "+collision+" 重叠，请拖动或缩小区域",1).show();return false;}}
        draft.name=name.getText().toString();String error=store.save(draft);if(error!=null){Toast.makeText(this,error,1).show();return false;}saved=LayoutStore.encode(draft);name.setText(draft.name);getIntent().putExtra("new",false);
        if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("RELOAD"));Toast.makeText(this,"已保存模板："+draft.name,0).show();if(leave)finish();return true;
    }
    @Override public void onBackPressed(){draft.name=name.getText().toString();if(!saved.equals(LayoutStore.encode(draft)))new AlertDialog.Builder(this).setTitle("布局尚未保存").setMessage("保存修改后离开？").setPositiveButton("保存",(d,w)->save(false,true)).setNegativeButton("放弃修改",(d,w)->finish()).setNeutralButton("继续编辑",null).show();else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle b){draft.name=name.getText().toString();b.putString("draft",LayoutStore.encode(draft));b.putString("saved",saved);b.putString("selected",selected);b.putBoolean("wide",wide);super.onSaveInstanceState(b);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)FullScreen.hideBars(getWindow());}

    private void openPreview(){draft.name=name.getText().toString();startActivityForResult(new Intent(this,PreviewActivity.class).putExtra("draft",LayoutStore.encode(draft)).putExtra("wide",wide).putExtra("selected",selected),73);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==73&&result==RESULT_OK&&data!=null){try{draft=LayoutStore.decode(data.getStringExtra("draft"));preview.profile=draft;name.setText(draft.name);String key=data.getStringExtra("selected");select(key==null?"FOLD":key);updateModes();}catch(Exception e){Toast.makeText(this,"预览草稿读取失败，保留原布局",1).show();}}}
    private final class Preview extends LayoutCanvas {
        Preview(){super(EditorActivity.this,draft,wide,false,new LayoutCanvas.Listener(){
            public void selected(String key){select(key);}
            public void touched(){hideKeyboard();}
            public void collision(String name){String text=name==null?"收起区域与按键无重叠":"收起区域与 "+name+" 重叠";if(rangeText!=null&&!text.contentEquals(rangeText.getText()))rangeText.setText(text);}
        });}
    }
}
