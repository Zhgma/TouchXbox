package dev.touchxbox.pad;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Instructions only: opening this screen never grants permission or starts the input service. */
public final class AuthorizationActivity extends Activity {
    private static final int BG=0xFF0D1118,TEXT=0xFFEAF0F8,MUTED=0xFF93A4BA,ACCENT=0xFF70B6FF;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setTitle("电脑 USB 授权");
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(8),dp(16),dp(8));
        Button back=new Button(this);back.setText("返回");back.setTextColor(ACCENT);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(76),dp(48)));
        TextView heading=text("电脑 USB 授权",22,TEXT);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setPadding(dp(12),0,0,0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout body=column();body.setPadding(dp(24),dp(12),dp(24),dp(28));scroll.addView(body);
        body.addView(text("默认授权方式：在 Windows 电脑运行独立 CMD。手机先安装本应用，再按以下步骤操作。",16,TEXT));

        LinearLayout developer=step(body,"1  开启手机开发者模式","华为 / 鸿蒙 4.2：设置 → 关于手机（或关于平板）→ 版本号，连续点击 7 次，按提示输入锁屏密码，直到提示已开启开发者模式。其他设备可在设置里搜索“版本号”。");
        button(developer,"打开关于手机",()->openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS));
        LinearLayout debug=step(body,"2  打开 USB 调试","设置 → 系统和更新 → 开发人员选项，打开“USB 调试”并确认。找不到此菜单时，可在设置里搜索“USB 调试”。");
        button(debug,"打开开发人员选项",()->openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        step(body,"3  用 USB 连接电脑","用支持数据传输的 USB 线连接 Windows 电脑，保持手机解锁。出现“允许 USB 调试”时点击“允许”；这个提示也可能在下一步运行 CMD 后才出现。若电脑找不到设备，可切换 USB 用途为“传输文件”并检查数据线。");
        LinearLayout download=step(body,"4  在电脑下载并运行 CMD","在 Windows 电脑下载 TouchXbox-authorize.cmd，双击运行。它是独立文件，无需另外准备 APK 或 PS1；手机需先安装本应用。若电脑没有 ADB，脚本会自动下载，首次使用需联网。连接多台设备时按提示选择目标。运行前请保存正在编辑的模板。");
        TextView link=text(OtaSource.AUTHORIZE_URL,13,ACCENT);link.setTextIsSelectable(true);link.setPadding(0,dp(12),0,dp(8));download.addView(link);
        button(download,"下载授权 CMD（Windows）",()->openUrl(OtaSource.AUTHORIZE_URL));
        button(download,"复制下载链接",()->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TouchXbox 授权 CMD",OtaSource.AUTHORIZE_URL));
            Toast.makeText(this,"链接已复制，请在 Windows 电脑浏览器打开",Toast.LENGTH_LONG).show();
        });
        button(download,"打开发布页（APK 与 CMD）",()->openUrl(OtaSource.PAGE_URL));
        TextView computer=text("CMD 需要在 Windows 电脑运行。手机上可复制链接后传到电脑；若先下载到手机，请把文件传到电脑再运行。",13,MUTED);computer.setPadding(0,dp(8),0,0);download.addView(computer);
        LinearLayout finish=step(body,"5  等待成功，再启动模板","电脑显示“输入服务激活成功”后，返回应用，允许悬浮窗权限，再点击已保存的模板启动。成功后可拔掉 USB。新版 CMD 使用独立后台文件，正常覆盖更新可复用已有授权；从旧方式切换需运行一次新版 CMD。手机重启、清除应用数据或输入服务退出后，仍需连接电脑运行 CMD。");
        button(finish,"设置悬浮窗权限",()->{
            try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
            catch(ActivityNotFoundException e){Toast.makeText(this,"请在系统设置中允许本应用显示悬浮窗",Toast.LENGTH_LONG).show();}
        });
        button(body,"完成，返回应用",this::finish);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private TextView text(String value,int size,int color){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(dp(4),1);return view;}
    private LinearLayout step(LinearLayout body,String title,String description){
        LinearLayout section=column();section.setPadding(0,dp(26),0,dp(6));body.addView(section);
        TextView heading=text(title,18,ACCENT);heading.setTypeface(Typeface.create("sans-serif-medium",0));section.addView(heading);
        TextView detail=text(description,15,TEXT);detail.setPadding(0,dp(10),0,0);section.addView(detail);return section;
    }
    private void button(LinearLayout parent,String title,Runnable action){
        Button button=new Button(this);button.setText(title);button.setTextColor(ACCENT);button.setAllCaps(false);button.setMinHeight(dp(48));button.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(10);parent.addView(button,params);
    }
    private void openSettings(String action){
        try{startActivity(new Intent(action));}
        catch(ActivityNotFoundException e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(ActivityNotFoundException unavailable){Toast.makeText(this,"请手动打开手机设置",Toast.LENGTH_LONG).show();}}
    }
    private void openUrl(String url){
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(ActivityNotFoundException e){Toast.makeText(this,"没有可用浏览器，请复制链接到电脑打开",Toast.LENGTH_LONG).show();}
    }
}
