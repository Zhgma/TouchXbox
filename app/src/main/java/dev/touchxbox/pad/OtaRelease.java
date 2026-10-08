package dev.touchxbox.pad;

/** The static-site update contract. APK names never contain paths or remote URLs. */
final class OtaRelease {
    static final String PACKAGE_NAME = "dev.touchxbox.pad";
    static final long MAX_APK_BYTES = 95L * 1024 * 1024;

    final long versionCode, size;
    final int minSdk;
    final String versionName, apkFile, sha256, changelog;

    OtaRelease(int schemaVersion, String packageName, long versionCode, String versionName,
               int minSdk, String apkFile, long size, String sha256, String changelog) {
        if (schemaVersion != 1) throw new IllegalArgumentException("不支持此更新信息格式，请从发布页下载");
        if (!PACKAGE_NAME.equals(packageName)) throw new IllegalArgumentException("更新信息的应用包名不匹配");
        if (versionCode <= 0 || versionName == null || versionName.trim().isEmpty() || versionName.length() > 64)
            throw new IllegalArgumentException("更新版本号无效");
        if (minSdk < 1 || minSdk > 1000) throw new IllegalArgumentException("更新系统要求无效");
        if (apkFile == null || !apkFile.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}\\.apk"))
            throw new IllegalArgumentException("更新安装包文件名无效");
        if (size <= 0 || size > MAX_APK_BYTES) throw new IllegalArgumentException("更新安装包大小无效");
        if (sha256 == null || !sha256.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("更新安装包校验值无效");
        if (changelog == null || changelog.length() > 16000) throw new IllegalArgumentException("更新说明过长");
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.minSdk = minSdk;
        this.apkFile = apkFile;
        this.size = size;
        this.sha256 = sha256.toLowerCase(java.util.Locale.ROOT);
        this.changelog = changelog;
    }

    boolean newerThan(long installedCode) { return versionCode > installedCode; }
}
