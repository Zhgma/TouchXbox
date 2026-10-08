package dev.touchxbox.pad;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import org.json.JSONObject;

final class OtaPackage {
    static OtaRelease parse(String json) throws Exception {
        JSONObject data = new JSONObject(json);
        return new OtaRelease(data.getInt("schemaVersion"), data.getString("packageName"),
                data.getLong("versionCode"), data.getString("versionName"), data.getInt("minSdk"),
                data.getString("apkFile"), data.getLong("size"), data.getString("sha256"), data.optString("changelog", ""));
    }

    static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    static PackageInfo installed(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getPackageInfo(context.getPackageName(), signatureFlags());
    }
    static String versionName(Context context) {
        try { return installed(context).versionName; } catch (Exception e) { return "未知"; }
    }
    private static int signatureFlags() {
        return Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
    }
    private static Signature[] signatures(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 && info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : info.signatures;
    }
    static File directory(Context context) { return new File(context.getCacheDir(), "ota"); }
    static File file(Context context, OtaRelease release) { return new File(directory(context), release.sha256 + ".apk"); }

    static void verify(Context context, File file, OtaRelease release, OtaDownload.Task task) throws Exception {
        OtaDownload.verify(file, release, task);
        PackageInfo apk = context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), signatureFlags());
        PackageInfo current = installed(context);
        if (apk == null || !context.getPackageName().equals(apk.packageName)) throw new IOException("安装包不是本应用");
        if (code(apk) != release.versionCode || !release.versionName.equals(apk.versionName))
            throw new IOException("安装包版本与更新信息不符");
        if (!release.newerThan(code(current))) throw new IOException("当前应用已是此版本或更新版本");
        if (apk.applicationInfo == null || apk.applicationInfo.minSdkVersion != release.minSdk || release.minSdk > Build.VERSION.SDK_INT)
            throw new IOException("安装包不适用于当前系统或系统要求与更新信息不符");
        Signature[] expected = signatures(current), actual = signatures(apk);
        if (expected == null || actual == null || expected.length == 0 || actual.length == 0
                || !new HashSet<>(Arrays.asList(expected)).equals(new HashSet<>(Arrays.asList(actual))))
            throw new IOException("更新包签名与已安装应用不同，不能覆盖安装");
        task.check();
    }
}
