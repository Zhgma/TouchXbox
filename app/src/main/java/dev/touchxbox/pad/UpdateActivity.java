package dev.touchxbox.pad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

/** A user-initiated foreground update. Downloading never requires input-service activation. */
public final class UpdateActivity extends Activity {
    private static final int INSTALL_PERMISSION = 801, INSTALL_APK = 802;
    private static final int TEXT = 0xFFE8EEF6, MUTED = 0xFFA4B1C3, ACCENT = 0xFF70B6FF;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView latest, details, status;
    private Button action, back;
    private ProgressBar progress;
    private OtaRelease release;
    private String releaseJson;
    private long currentCode;
    private boolean ready, awaitingPermission;
    private OtaDownload.Task task;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF0D1118);
        getWindow().setNavigationBarColor(0xFF0D1118);
        try { currentCode = OtaPackage.code(OtaPackage.installed(this)); }
        catch (Exception e) { currentCode = Long.MAX_VALUE; }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF0D1118);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        TextView title = text("应用更新", 25, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", 0));
        body.addView(title);
        TextView current = text(LauncherIdentity.name(LauncherIdentity.current(this)) + " · 当前版本 " + OtaPackage.versionName(this), 15, MUTED);
        current.setPadding(0, dp(14), 0, dp(24));
        body.addView(current);
        latest = text("", 20, TEXT); body.addView(latest);
        details = text("", 15, MUTED); details.setPadding(0, dp(12), 0, dp(20)); body.addView(details);
        status = text("", 15, ACCENT); body.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(16));
        progressParams.topMargin = dp(16); body.addView(progress, progressParams);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(20), 0, dp(12));
        back = new Button(this); back.setText("返回"); buttons.addView(back);
        action = new Button(this); buttons.addView(action);
        body.addView(buttons);
        TextView source = text("发布页面 ↗\n" + OtaSource.PAGE_URL.replaceFirst("https://", ""), 13, MUTED);
        source.setPadding(0, dp(20), 0, dp(12));
        source.setFocusable(true);
        source.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(OtaSource.PAGE_URL))); }
            catch (ActivityNotFoundException e) { status.setText("未找到浏览器，请手动打开发布页面"); }
        });
        body.addView(source);
        body.addView(text("更新会保留模板和设置。下载完成后，需要在系统安装界面确认。", 13, MUTED));
        back.setOnClickListener(v -> { if (task != null) cancel(); else finish(); });
        action.setOnClickListener(v -> {
            if (task != null) return;
            if (!available()) check(); else if (ready) install(); else download();
        });
        if (saved != null) {
            try {
                releaseJson = saved.getString("release");
                if (releaseJson != null) release = OtaPackage.parse(releaseJson);
                ready = release != null && saved.getBoolean("ready") && OtaPackage.file(this, release).isFile();
                awaitingPermission = saved.getBoolean("awaitingPermission");
            } catch (Exception ignored) { release = null; releaseJson = null; }
        }
        if (release == null) check(); else showRelease();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setLineSpacing(dp(4), 1); return view;
    }
    private boolean available() { return release != null && release.newerThan(currentCode) && release.minSdk <= Build.VERSION.SDK_INT; }
    private void buttons() {
        boolean busy = task != null;
        action.setEnabled(!busy);
        action.setText(!available() ? "检查更新" : ready ? "安装更新" : "下载更新");
        back.setText(busy ? "取消" : "返回");
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }
    private void showRelease() {
        latest.setText("最新版本 " + release.versionName);
        details.setText(String.format(Locale.CHINA, "%.2f MB\n\n%s", release.size / 1048576.0,
                release.changelog.isEmpty() ? "此版本未提供更新说明。" : release.changelog));
        if (!release.newerThan(currentCode)) status.setText("当前已是最新版本");
        else if (release.minSdk > Build.VERSION.SDK_INT) status.setText("此版本需要 Android API " + release.minSdk + " 或更高版本，当前系统不适用");
        else status.setText(ready ? "下载完成，点击“安装更新”继续。" : "发现新版本，可以下载更新。");
        buttons();
    }
    private OtaDownload.Task begin(String message) {
        task = new OtaDownload.Task(); status.setText(message); progress.setIndeterminate(true); buttons(); return task;
    }
    private void complete(OtaDownload.Task run, Runnable callback) {
        main.post(() -> {
            if (task != run || isFinishing() || isDestroyed()) return;
            task = null; callback.run(); buttons();
        });
    }
    private void fail(OtaDownload.Task run, Exception error) {
        complete(run, () -> status.setText("更新未完成：" + (error.getMessage() == null ? "请检查网络后重试" : error.getMessage())));
    }
    private void check() {
        release = null; releaseJson = null; ready = false; latest.setText(""); details.setText("");
        OtaDownload.Task run = begin("正在检查更新…");
        new Thread(() -> {
            try {
                String json = OtaDownload.manifest(run);
                OtaRelease candidate = OtaPackage.parse(json);
                complete(run, () -> { release = candidate; releaseJson = json; showRelease(); });
            } catch (Exception e) { fail(run, e); }
        }, "TouchXbox update check").start();
    }
    private void download() {
        final OtaRelease candidate = release;
        final android.content.Context context = getApplicationContext();
        OtaDownload.Task run = begin("正在下载，离开此页面会取消未完成的下载…");
        progress.setIndeterminate(false); progress.setProgress(0);
        new Thread(() -> {
            try {
                final int[] lastPercent = {-1};
                File apk = OtaDownload.download(candidate, OtaPackage.directory(context), run, received -> {
                    int percent = (int) (received * 100 / candidate.size);
                    if (percent == lastPercent[0]) return;
                    lastPercent[0] = percent;
                    main.post(() -> {
                        if (task == run && !isDestroyed()) {
                            progress.setProgress(percent);
                            status.setText("下载中 " + percent + "% · 离开此页面会取消未完成的下载");
                        }
                    });
                });
                main.post(() -> { if (task == run && !isDestroyed()) status.setText("正在校验安装包…"); });
                OtaPackage.verify(context, apk, candidate, run);
                complete(run, () -> { ready = true; showRelease(); });
            } catch (Exception e) { fail(run, e); }
        }, "TouchXbox update download").start();
    }
    private void cancel() {
        OtaDownload.Task previous = task; task = null;
        if (previous != null) previous.cancel();
        status.setText("已取消，可重新尝试。"); buttons();
    }
    private void install() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this).setTitle("允许安装更新")
                    .setMessage("请在系统设置中允许本应用安装应用，返回后将继续安装更新。")
                    .setNegativeButton("稍后", null).setPositiveButton("去设置", (dialog, which) -> {
                        try {
                            awaitingPermission = true;
                            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION);
                        } catch (ActivityNotFoundException e) {
                            awaitingPermission = false;
                            status.setText("请在系统设置中开启本应用的“安装未知应用”权限，再返回安装。");
                        }
                    }).show();
            return;
        }
        final OtaRelease candidate = release;
        final android.content.Context context = getApplicationContext();
        OtaDownload.Task run = begin("正在核对安装包…");
        new Thread(() -> {
            try {
                OtaPackage.verify(context, OtaPackage.file(context, candidate), candidate, run);
                complete(run, () -> launchInstaller(candidate));
            } catch (Exception e) {
                complete(run, () -> { ready = false; status.setText("安装前校验失败：" + e.getMessage() + "。可重新下载。"); });
            }
        }, "TouchXbox update verify").start();
    }
    private void launchInstaller(OtaRelease candidate) {
        Uri uri = UpdateApkProvider.uri(candidate);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setClipData(ClipData.newRawUri("应用更新", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        try {
            try { startActivityForResult(intent, INSTALL_APK); }
            catch (ActivityNotFoundException e) { intent.setAction(Intent.ACTION_VIEW); startActivityForResult(intent, INSTALL_APK); }
            status.setText("请在系统安装界面确认更新。");
        } catch (Exception e) { status.setText("无法打开系统安装界面：" + e.getMessage()); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == INSTALL_PERMISSION) {
            awaitingPermission = false;
            if (getPackageManager().canRequestPackageInstalls() && release != null && ready) install();
            else status.setText("尚未允许安装更新，可稍后再次点击“安装更新”。");
        } else if (request == INSTALL_APK) {
            // Some OEM installers do not return a reliable result; the installed version is authoritative.
            try { currentCode = OtaPackage.code(OtaPackage.installed(this)); } catch (Exception ignored) { }
            status.setText(release != null && !release.newerThan(currentCode) ? "更新已安装" : "安装尚未完成，可再次点击“安装更新”。");
            buttons();
        }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("release", releaseJson); out.putBoolean("ready", ready);
        out.putBoolean("awaitingPermission", awaitingPermission); super.onSaveInstanceState(out);
    }
    @Override protected void onResume() {
        super.onResume();
        if (awaitingPermission && release != null && ready && task == null && getPackageManager().canRequestPackageInstalls()) {
            awaitingPermission = false;
            install();
        }
    }
    @Override protected void onDestroy() {
        if (task != null) { task.cancel(); task = null; }
        main.removeCallbacksAndMessages(null); super.onDestroy();
    }
}
