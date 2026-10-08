package dev.touchxbox.pad;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import rikka.shizuku.Shizuku;

/** A template library. Connection details and utility actions live in the settings panel. */
public final class MainActivity extends Activity {
    private static final int BG=0xFF0D1118,PANEL=0xFF171E29,TEXT=0xFFEAF0F8,MUTED=0xFF93A4BA,ACCENT=0xFF70B6FF;
    private LayoutStore store;private GridView grid;private BaseAdapter templatesAdapter;private TemplateDragController templateDrag;private TextView empty,status,headerTitle;private Dialog settings;
    private final Handler handler=new Handler(Looper.getMainLooper());private boolean visible;private java.util.List<LayoutProfile> profiles=new ArrayList<>();
    private LayoutProfile pendingLaunch;private boolean pendingTest,shizukuGuide;private AlertDialog shizukuDialog,launchErrorDialog;private int shizukuStage;
    private final Shizuku.OnBinderReceivedListener shizukuReady=()->handler.post(this::resumePending);
    private final Shizuku.OnRequestPermissionResultListener shizukuPermission=(code,result)->{if(code==ShizukuInput.PERMISSION_REQUEST)handler.post(()->{if(result==android.content.pm.PackageManager.PERMISSION_GRANTED){toast("Shizuku 已授权");resumePending();}else{pendingLaunch=null;toast("未授予权限，可以稍后在设置中重新授权");}refreshStatus();});};
    @Override public void onCreate(Bundle b){super.onCreate(b);store=new LayoutStore(this);LauncherIdentity.restore(this);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        if(ShizukuInput.SHOW_ENTRY){Shizuku.addBinderReceivedListenerSticky(shizukuReady);Shizuku.addRequestPermissionResultListener(shizukuPermission);}
        LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),0,dp(18),0);root.addView(header,new LinearLayout.LayoutParams(-1,dp(76)));
        header.addView(icon("settings","设置",()->openSettings()),new LinearLayout.LayoutParams(dp(52),dp(52)));
        headerTitle=label(LauncherIdentity.name(LauncherIdentity.current(this)),24,TEXT);headerTitle.setTypeface(Typeface.create("sans-serif-medium",0));headerTitle.setGravity(Gravity.CENTER);header.addView(headerTitle,new LinearLayout.LayoutParams(0,-1,1));
        header.addView(icon("add","添加模板",()->addTemplate()),new LinearLayout.LayoutParams(dp(52),dp(52)));
        View divider=new View(this);divider.setBackgroundColor(0xFF242D3A);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        FrameLayout content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        grid=new GridView(this);grid.setNumColumns(GridView.AUTO_FIT);grid.setColumnWidth(dp(236));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setHorizontalSpacing(dp(20));grid.setVerticalSpacing(dp(24));grid.setPadding(dp(28),dp(28),dp(28),dp(28));grid.setClipToPadding(false);grid.setSelector(new ColorDrawable(Color.TRANSPARENT));content.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty=label("暂无模板\n点击右上角 + 添加",18,MUTED);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(dp(10),1);content.addView(empty,new FrameLayout.LayoutParams(-1,-1));grid.setEmptyView(empty);
        grid.setOnItemClickListener((a,v,i,id)->launch(profiles.get(i),false));grid.setOnItemLongClickListener((a,v,i,id)->{manage(profiles.get(i));return true;});
        templatesAdapter=new BaseAdapter(){public int getCount(){return profiles.size();}public Object getItem(int i){return profiles.get(i);}public long getItemId(int i){return i;}public View getView(int i,View old,android.view.ViewGroup parent){return card(profiles.get(i));}};grid.setAdapter(templatesAdapter);
        templateDrag=new TemplateDragController(grid,new TemplateDragController.Callbacks(){
            public String idAt(int position){return profiles.get(position).id;}
            public boolean move(String id,String targetId){if(!store.move(id,targetId)){toast("排序保存失败，请重试");return false;}loadTemplates();return true;}
        });
        if(b!=null&&b.getBoolean("settings"))handler.post(()->openSettings());
        handleLaunchError(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleLaunchError(intent);}
    private void handleLaunchError(Intent intent){
        String reason=intent.getStringExtra(LauncherActivity.ERROR);if(reason==null)return;boolean authorize=intent.getBooleanExtra(LauncherActivity.AUTHORIZE,false);intent.removeExtra(LauncherActivity.ERROR);intent.removeExtra(LauncherActivity.AUTHORIZE);
        if(launchErrorDialog!=null)launchErrorDialog.dismiss();launchErrorDialog=new AlertDialog.Builder(this).setTitle("快捷启动未完成").setMessage(reason).setPositiveButton(authorize?"查看授权步骤":"打开设置",(d,w)->{if(authorize)activationHelp();else openSettings();}).setNegativeButton("稍后",null).show();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Drawable ripple(Drawable background){return new RippleDrawable(ColorStateList.valueOf(0x336FAFFF),background,rounded(Color.WHITE,0,12));}
    private View icon(String kind,String description,Runnable action){Icon v=new Icon(kind);v.setContentDescription(description);v.setBackground(ripple(null));v.setFocusable(true);v.setOnClickListener(w->action.run());return v;}
    private void loadTemplates(){profiles=store.all();templatesAdapter.notifyDataSetChanged();}
    private View card(LayoutProfile profile){
        LinearLayout card=column();StateListDrawable background=new StateListDrawable();background.addState(new int[]{android.R.attr.state_activated},rounded(0xFF223D58,ACCENT,14));background.addState(new int[0],rounded(PANEL,0xFF2A3545,14));card.setBackground(ripple(background));card.setClipToOutline(true);card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);card.setContentDescription(profile.name+"，点按启动，长按管理，右上角把手拖动排序");
        FrameLayout preview=new FrameLayout(this);preview.addView(new Thumbnail(profile),new FrameLayout.LayoutParams(-1,-1));card.addView(preview,new LinearLayout.LayoutParams(-1,dp(156)));
        if(profile.id.equals(store.quickLaunchId())){TextView badge=label("快捷启动",11,TEXT);badge.setPadding(dp(10),dp(5),dp(10),dp(5));badge.setBackground(rounded(0xFF244D78,0,8));FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);badgeParams.setMargins(dp(12),dp(12),dp(12),0);preview.addView(badge,badgeParams);card.setContentDescription(profile.name+"，已设为快捷启动，长按管理");}
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(dp(18),0,dp(16),0);card.addView(footer,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView name=label(profile.name,17,TEXT);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);name.setTypeface(Typeface.create("sans-serif-medium",0));footer.addView(name,new LinearLayout.LayoutParams(0,-1,1));name.setGravity(Gravity.CENTER_VERTICAL);
        ImageView protocol=new ImageView(this);protocol.setImageDrawable(new RoundedIconDrawable(getDrawable(LauncherIdentity.icon(ControllerProtocol.valid(profile.protocol)))));protocol.setScaleType(ImageView.ScaleType.FIT_CENTER);protocol.setContentDescription(ControllerProtocol.NAMES[ControllerProtocol.valid(profile.protocol)]);footer.addView(protocol,new LinearLayout.LayoutParams(dp(22),dp(22)));
        Icon drag=new Icon("drag");drag.setContentDescription("拖动排序："+profile.name);drag.setBackground(ripple(null));drag.setOnClickListener(v->manage(profile));FrameLayout.LayoutParams dragParams=new FrameLayout.LayoutParams(dp(40),dp(40),Gravity.TOP|Gravity.END);preview.addView(drag,dragParams);templateDrag.bind(card,drag,profile.id);return card;
    }
    private void launch(LayoutProfile profile,boolean test){if(profile==null){toast("先点击 + 添加一个模板");return;}if(!ShizukuInput.selected(this)&&!BridgeClient.hasActivationKey(this)){activationHelp();return;}if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle("允许悬浮显示").setMessage("开启悬浮窗权限后，即可在其他应用上使用手柄。").setPositiveButton("去设置",(d,w)->overlayPermission()).setNegativeButton("取消",null).show();return;}if(!store.select(profile.id)){toast("模板切换失败");return;}
        if(ShizukuInput.selected(this)&&!ShizukuInput.authorized()){if(!ShizukuInput.SHOW_ENTRY){activationHelp();return;}pendingLaunch=profile;pendingTest=test;showShizuku();return;}
        if(settings!=null)settings.dismiss();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
        if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("RELOAD"));startForegroundService(new Intent(this,OverlayService.class).setAction("EXPAND"));if(test)startActivity(new Intent(this,TesterActivity.class));else moveTaskToBack(true);
    }
    private void resumePending(){
        refreshStatus();if(!ShizukuInput.SHOW_ENTRY||!visible||!ShizukuInput.selected(this))return;
        if(ShizukuInput.authorized()){shizukuGuide=false;if(shizukuDialog!=null)shizukuDialog.dismiss();if(pendingLaunch!=null){LayoutProfile p=pendingLaunch;boolean test=pendingTest;pendingLaunch=null;launch(p,test);}}
        else if(shizukuGuide&&(shizukuDialog==null||!shizukuDialog.isShowing()||shizukuStage!=(ShizukuInput.running()?1:0)))showShizuku();
    }
    private void chooseConnection(){if(!ShizukuInput.SHOW_ENTRY)return;boolean selected=ShizukuInput.selected(this);new AlertDialog.Builder(this).setTitle("连接方式").setSingleChoiceItems(new String[]{"手机内授权 · Shizuku","电脑 USB / ADB 激活"},selected?0:1,(d,which)->{stopService(new Intent(this,OverlayService.class));pendingLaunch=null;getSharedPreferences("connection",0).edit().putBoolean("shizuku",which==0).apply();d.dismiss();if(settings!=null)settings.dismiss();openSettings();if(which==0)showShizuku();}).setNegativeButton("取消",null).show();}
    private void activationHelp(){pendingLaunch=null;startActivity(new Intent(this,AuthorizationActivity.class));}
    private void updateIdentity(){String title=LauncherIdentity.name(LauncherIdentity.current(this));headerTitle.setText(title);setTitle(title);setTaskDescription(new ActivityManager.TaskDescription(title));}
    private String appearanceStatus(){LayoutProfile quick=store.quickLaunch();if(quick!=null)return "快捷启动 · "+quick.name+" · "+LauncherIdentity.name(quick.protocol);int mode=LauncherIdentity.mode(this);return mode==LauncherIdentity.AUTO?"跟随已激活协议 · "+LauncherIdentity.name(LauncherIdentity.current(this)):mode==LauncherIdentity.ORIGINAL?"固定 · 原始图标 · TouchXbox":"固定 · "+LauncherIdentity.name(mode);}
    private AlertDialog chooseAppearance(){
        final int[] modes={LauncherIdentity.AUTO,LauncherIdentity.ORIGINAL,0,1,2,3};
        final String[] names={"跟随已激活协议（自动）","原始图标 · TouchXbox","Xbox 手柄 · TouchXbox","NS · TouchNS","PS · TouchPS","FPV · TouchFPV"};
        int selected=0;for(int i=0;i<modes.length;i++)if(modes[i]==LauncherIdentity.mode(this))selected=i;
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_single_choice,names){
            @Override public View getView(int position,View reusable,android.view.ViewGroup parent){
                CheckedTextView row=(CheckedTextView)super.getView(position,reusable,parent);int appearance=modes[position]==LauncherIdentity.AUTO?LauncherIdentity.lastProtocol(MainActivity.this):modes[position];
                Drawable icon=new RoundedIconDrawable(getDrawable(LauncherIdentity.icon(appearance)));icon.setBounds(0,0,dp(42),dp(42));row.setCompoundDrawablesRelative(icon,null,null,null);row.setCompoundDrawablePadding(dp(14));row.setMinHeight(dp(66));row.setTextSize(15);return row;
            }
        };
        return new AlertDialog.Builder(this).setTitle(store.quickLaunchId().isEmpty()?"图标和应用名称":"关闭快捷启动后的图标和名称").setSingleChoiceItems(adapter,selected,(dialog,which)->{
            if(!LauncherIdentity.setMode(this,modes[which])){toast("外观切换失败，请重试");return;}
            updateIdentity();if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("APPEARANCE"));dialog.dismiss();if(settings!=null)settings.dismiss();openSettings();
        }).setNegativeButton("取消",null).show();
    }
    private void showShizuku(){
        if(!ShizukuInput.SHOW_ENTRY){activationHelp();return;}
        if(!ShizukuInput.selected(this)){stopService(new Intent(this,OverlayService.class));getSharedPreferences("connection",0).edit().putBoolean("shizuku",true).apply();}
        boolean installed=ShizukuInput.installed(this),running=ShizukuInput.running(),granted=ShizukuInput.authorized();
        shizukuGuide=true;shizukuStage=running?1:0;if(shizukuDialog!=null)shizukuDialog.dismiss();
        String message=ShizukuInput.status(this)+"\n\n1. 在手机上安装并打开 Shizuku。\n2. 按其指引，通过系统无线调试配对并启动；已有 Root 也可直接启动。\n3. 返回这里，授权 TouchXbox 后点击模板。\n\n无线调试需要设备设置提供配对入口；重启手机后通常需要重新启动 Shizuku。";
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("手机内授权 · Shizuku").setMessage(message).setNegativeButton("稍后",(d,w)->{shizukuGuide=false;pendingLaunch=null;}).setOnCancelListener(d->{shizukuGuide=false;pendingLaunch=null;});
        if(granted)dialog.setPositiveButton("完成",(d,w)->{shizukuGuide=false;resumePending();});
        else if(running)dialog.setPositiveButton("授权 TouchXbox",(d,w)->{shizukuGuide=false;try{ShizukuInput.requestPermission();}catch(Exception e){toast("授权失败："+e.getMessage());}});
        else dialog.setPositiveButton(installed?"打开 Shizuku":"下载 Shizuku",(d,w)->{try{Intent i=installed?getPackageManager().getLaunchIntentForPackage(ShizukuInput.PACKAGE):new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/"));if(i!=null)startActivity(i);else toast("请从手机桌面打开 Shizuku");}catch(Exception e){toast("请从手机桌面打开 Shizuku，或访问 shizuku.rikka.app");}});
        shizukuDialog=dialog.show();
    }
    private AlertDialog manage(LayoutProfile profile){boolean quick=profile.id.equals(store.quickLaunchId());return new AlertDialog.Builder(this).setTitle(profile.name).setItems(new String[]{"编辑","删除","复制配置",quick?"取消快捷启动":"设为快捷启动"},(d,which)->{
        if(which==0){if(store.select(profile.id))startActivity(new Intent(this,EditorActivity.class));else toast("模板读取失败");}
        else if(which==1)new AlertDialog.Builder(this).setTitle("删除模板").setMessage("删除“"+profile.name+"”？").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{LayoutProfile active=store.active();if(store.delete(profile.id)){if(active!=null&&active.id.equals(profile.id))stopService(new Intent(this,OverlayService.class));updateIdentity();loadTemplates();}else toast("删除失败，请重试");}).show();
        else if(which==2){try{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TouchXbox 模板配置",TemplateCode.encode(profile)));toast("配置已复制，点击 + 可粘贴导入");}catch(IllegalArgumentException e){toast("配置复制失败");}}
        else if(store.setQuickLaunch(quick?"":profile.id)){updateIdentity();loadTemplates();if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("APPEARANCE"));toast(quick?"已取消快捷启动，桌面图标恢复原设置":"已设为快捷启动；长按桌面图标 → 打开软件可返回");}else toast("快捷启动设置失败，请重试");
    }).show();}
    private void addTemplate(){
        LinearLayout body=column();body.setPadding(dp(24),dp(8),dp(24),0);TextView hint=label("新建一套布局，或粘贴复制的模板配置。",14,MUTED);body.addView(hint);
        EditText code=new EditText(this);code.setTextColor(TEXT);code.setTextSize(13);code.setHint("TXPAD1:…");code.setHintTextColor(MUTED);code.setContentDescription("配置字符串");code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setMinLines(3);code.setMaxLines(5);code.setGravity(Gravity.TOP);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(90000)});body.addView(code,new LinearLayout.LayoutParams(-1,-2));
        Button paste=new Button(this);paste.setAllCaps(false);paste.setText("粘贴配置");paste.setTextColor(ACCENT);body.addView(paste,new LinearLayout.LayoutParams(-1,dp(46)));paste.setOnClickListener(v->{ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData clip=cb.getPrimaryClip();if(clip!=null&&clip.getItemCount()>0)code.setText(clip.getItemAt(0).coerceToText(this));else code.setError("剪贴板为空");});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加模板").setView(body).setNegativeButton("取消",null).setNeutralButton("新建模板",(d,w)->startActivity(new Intent(this,EditorActivity.class).putExtra("new",true))).setPositiveButton("导入配置",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{try{LayoutProfile p=TemplateCode.decode(code.getText().toString());p.name=store.uniqueName(p.name);String error=store.save(p);if(error!=null){code.setError(error);return;}dialog.dismiss();loadTemplates();toast("已添加模板："+p.name);}catch(IllegalArgumentException e){code.setError(e.getMessage());}}));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();
    }
    private void openSettings(){if(settings!=null&&settings.isShowing())return;settings=new Dialog(this);settings.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=column();panel.setPadding(dp(24),dp(20),dp(24),dp(20));panel.setBackgroundColor(PANEL);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=label("设置",23,TEXT);heading.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));heading.addView(icon("close","关闭设置",()->settings.dismiss()),new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(heading);
        ScrollView scroll=new ScrollView(this);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout body=column();scroll.addView(body);section(body,"手柄");status=label("",13,MUTED);status.setPadding(0,dp(4),0,dp(20));body.addView(status);
        action(body,"系统输入测试","启动当前模板，查看系统回传的按键和摇杆事件",()->launch(store.active(),true));
        action(body,"传感器检测","查看本机加速度计、陀螺仪及未校准接口",()->{ScrollView scroller=new ScrollView(this);TextView text=label(DeviceMotion.diagnostics(this),13,TEXT);text.setPadding(dp(22),dp(12),dp(22),dp(12));text.setTextIsSelectable(true);scroller.addView(text);new AlertDialog.Builder(this).setTitle("本机传感器").setView(scroller).setPositiveButton("确定",null).show();});
        action(body,"停止手柄","释放所有输入并移除虚拟设备",()->{stopService(new Intent(this,OverlayService.class));handler.postDelayed(this::refreshStatus,150);});
        section(body,"外观");action(body,"图标和应用名称",appearanceStatus(),()->chooseAppearance());
        section(body,"连接与权限");action(body,"电脑 USB 授权（默认）","查看完整步骤，下载或复制独立 CMD 链接",()->activationHelp());if(ShizukuInput.SHOW_ENTRY){action(body,"连接方式",ShizukuInput.selected(this)?"手机内授权 · Shizuku":"电脑 USB / ADB 激活",()->chooseConnection());action(body,"手机内授权",ShizukuInput.status(this),()->showShizuku());}
        action(body,"悬浮窗权限","管理在其他应用上层显示的权限",()->overlayPermission());action(body,"检测输入服务","检查当前连接方式能否创建虚拟手柄",()->probe());
        String connectionHelp=ShizukuInput.SHOW_ENTRY&&ShizukuInput.selected(this)?"在手机上启动 Shizuku 并授权本应用后，点击模板即可使用。支持系统无线调试或已有 Root。":ShizukuInput.selected(this)?"已保留现有输入连接。连接中断时，可在电脑运行 TouchXbox-authorize.cmd 重新激活。":"在电脑运行 TouchXbox-authorize.cmd。激活后可拔掉 USB；设备重启后需重新激活。";
        TextView bridge=label(connectionHelp,13,MUTED);bridge.setPadding(0,dp(8),0,dp(12));body.addView(bridge);
        section(body,"使用说明");TextView help=label("点模板直接启动，长按可编辑、删除、复制配置或设为快捷启动。拖动卡片右上角把手调整顺序，松手保存；名称右侧图标表示模板协议。\n\n快捷启动仅能选择一个模板，桌面图标和名称跟随它。点击桌面图标直接启动或展开遥控；长按桌面图标选“打开软件”，或点击运行通知返回软件。取消或删除此模板后恢复原来的图标设置。\n\n点击编辑好的收起区域，手柄会收进系统贴边气泡；点气泡展开。\n\nL / R 原地长按半秒按下摇杆，随后可继续拖动。锁屏会停止手柄。",14,MUTED);help.setLineSpacing(dp(3),1);body.addView(help);
        section(body,"关于");action(body,"检查更新","当前版本 "+OtaPackage.versionName(this),()->startActivity(new Intent(this,UpdateActivity.class)));TextView about=label(LauncherIdentity.name(LauncherIdentity.current(this))+" "+OtaPackage.versionName(this)+"\nXbox / NS / PS / FPV · 键盘与体感\n开源 · Apache-2.0",13,MUTED);about.setLineSpacing(dp(4),1);body.addView(about);
        settings.setContentView(panel);settings.setOnDismissListener(d->{status=null;});Window window=settings.getWindow();window.setBackgroundDrawable(new ColorDrawable(PANEL));window.setDimAmount(.55f);settings.show();window.setGravity(Gravity.END);window.setLayout(Math.min(dp(390),Math.round(getResources().getDisplayMetrics().widthPixels*.95f)),-1);refreshStatus();
    }
    private void section(LinearLayout body,String name){TextView t=label(name,12,ACCENT);t.setTypeface(Typeface.create("sans-serif-medium",0));t.setPadding(0,dp(24),0,dp(12));body.addView(t);}
    private void action(LinearLayout body,String title,String subtitle,Runnable action){LinearLayout row=column();row.setPadding(dp(12),dp(14),dp(12),dp(14));row.setBackground(ripple(null));row.setFocusable(true);TextView t=label(title,16,TEXT),s=label(subtitle,12,MUTED);s.setPadding(0,dp(5),0,0);row.addView(t);row.addView(s);body.addView(row,new LinearLayout.LayoutParams(-1,-2));row.setOnClickListener(v->action.run());}
    private void refreshStatus(){if(status==null)return;LayoutProfile p=store.active();status.setText("当前模板："+(p==null?"未选择":p.name)+"\n悬浮权限："+(Settings.canDrawOverlays(this)?"已允许":"未允许")+"\n手柄："+(OverlayService.active?(OverlayService.collapsed?"已收起":"运行中"):"未运行")+"\n"+OverlayService.status+"\n"+devices()+"\n"+DeviceMotion.availability(this)+"\n"+DeviceMotion.status);}
    private void overlayPermission(){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    private void probe(){if(OverlayService.active){toast("手柄会话已连接");return;}new Thread(()->{String message;try(BridgeClient c=new BridgeClient(this)){c.connect();message=c.command("PROBE");}catch(Exception e){message="输入服务未就绪：\n"+e.getMessage();}final String result=message;runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("输入服务检测").setMessage(result).setPositiveButton("好",null).show();});},"TouchXbox bridge check").start();}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    static String devices(){for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&ControllerProtocol.matches(d.getVendorId(),d.getProductId()))return String.format(Locale.US,"系统发现：%s\nID %d · sources 0x%x",d.getName(),id,d.getSources());}return "系统尚未发现虚拟手柄设备";}
    private final Runnable refresh=new Runnable(){public void run(){if(!visible)return;refreshStatus();handler.postDelayed(this,1200);}};
    @Override protected void onResume(){super.onResume();updateIdentity();if(OverlayService.active)startService(new Intent(this,OverlayService.class).setAction("HIDE"));loadTemplates();visible=true;handler.removeCallbacks(refresh);handler.post(refresh);handler.post(this::resumePending);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(refresh);if(templateDrag!=null)templateDrag.cancel();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("settings",settings!=null&&settings.isShowing());super.onSaveInstanceState(b);}
    @Override protected void onDestroy(){if(ShizukuInput.SHOW_ENTRY){Shizuku.removeBinderReceivedListener(shizukuReady);Shizuku.removeRequestPermissionResultListener(shizukuPermission);}if(shizukuDialog!=null)shizukuDialog.dismiss();if(launchErrorDialog!=null)launchErrorDialog.dismiss();if(settings!=null)settings.dismiss();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private final class Icon extends View {
        final String kind;final Paint p=new Paint(3);Icon(String k){super(MainActivity.this);kind=k;}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight()),s=size/48f;c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(s,s);p.setColor(TEXT);p.setStrokeWidth(2.1f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
            if(kind.equals("add")){c.drawLine(24,13,24,35,p);c.drawLine(13,24,35,24,p);}else if(kind.equals("close")){c.drawLine(16,16,32,32,p);c.drawLine(16,32,32,16,p);}else if(kind.equals("drag")){p.setColor(MUTED);p.setStyle(Paint.Style.FILL);for(int row=0;row<3;row++)for(int col=0;col<2;col++)c.drawCircle(20+8*col,16+8*row,1.8f,p);}else{Path gear=new Path();for(int i=0;i<48;i++){double a=i*Math.PI/24;float r=i%6<3?13:10.5f;float x=24+(float)Math.cos(a)*r,y=24+(float)Math.sin(a)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(24,24,4.5f,p);}c.restore();}
    }
    private final class Thumbnail extends View {
        final LayoutProfile profile;final PadPainter painter=new PadPainter();final Paint p=new Paint(3);Thumbnail(LayoutProfile layout){super(MainActivity.this);profile=layout;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas c){LayoutViewport viewport=ScreenSpace.viewport(MainActivity.this,true);float width=viewport.displayWidth,height=viewport.displayHeight,zoom=Math.min((getWidth()-dp(32))/width,(getHeight()-dp(30))/height);c.save();c.translate((getWidth()-width*zoom)/2,(getHeight()-height*zoom)/2);c.scale(zoom,zoom);p.setColor(0xFF101722);p.setStyle(Paint.Style.FILL);c.drawRoundRect(0,0,width,height,28,28,p);p.setColor(0xFF354356);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2/zoom);c.drawRoundRect(0,0,width,height,28,28,p);
            c.translate(viewport.left,viewport.top);for(LayoutProfile.Spec s:profile.specs())if(profile.visible(s)){LayoutProfile.Box b=profile.landscape.box(s,viewport.width,viewport.height);c.save();c.translate(b.x,b.y);painter.draw(c,s,b.w,b.h,false,0,profile.throttle(s)?1:0,s.kind==6?.5f:0,false,profile.floating(s)?.65f:.95f,0,profile);c.restore();}c.restore();}
    }
}
