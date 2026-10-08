package dev.touchxbox.pad;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;

public final class OtaTest {
    private static int checks;
    private interface Attempt { void run() throws Exception; }
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Attempt attempt, String message) throws Exception {
        try { attempt.run(); } catch (IllegalArgumentException | IOException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static OtaRelease release(byte[] bytes) throws Exception {
        return new OtaRelease(1, OtaRelease.PACKAGE_NAME, 11, "0.5.6", 26,
                "TouchXbox-11.apk", bytes.length, OtaDownload.hex(MessageDigest.getInstance("SHA-256").digest(bytes)), "更新说明");
    }
    private static void failedDownload(byte[] bytes, OtaRelease release, OtaDownload.Task task, boolean cancelDuring) throws Exception {
        File directory = Files.createTempDirectory("touchxbox-ota-rejected-").toFile();
        try {
            rejects(() -> OtaDownload.receive(new ByteArrayInputStream(bytes), directory, release, task,
                    received -> { if (cancelDuring) task.cancel(); }), "bad download accepted");
            check(directory.list().length == 0, "failed download left a partial or installable APK");
        } finally { directory.delete(); }
    }
    public static void main(String[] args) throws Exception {
        byte[] bytes = new byte[65537];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
        OtaRelease release = release(bytes);
        check(release.newerThan(10), "new version not offered");
        check(!release.newerThan(11), "same version offered");
        check(!release.newerThan(12), "downgrade offered");
        rejects(() -> new OtaRelease(2, OtaRelease.PACKAGE_NAME, 11, "v", 26, "a.apk", 1, release.sha256, ""), "schema");
        rejects(() -> new OtaRelease(1, "other.app", 11, "v", 26, "a.apk", 1, release.sha256, ""), "package");
        rejects(() -> new OtaRelease(1, OtaRelease.PACKAGE_NAME, 0, "v", 26, "a.apk", 1, release.sha256, ""), "version");
        for (String unsafe : new String[]{"../a.apk", "https://other/a.apk", "a.apk?x=1", "a%2f.apk", "a\\b.apk", "a.html"})
            rejects(() -> new OtaRelease(1, OtaRelease.PACKAGE_NAME, 11, "v", 26, unsafe, 1, release.sha256, ""), "unsafe APK path");
        rejects(() -> new OtaRelease(1, OtaRelease.PACKAGE_NAME, 11, "v", 26, "a.apk", OtaRelease.MAX_APK_BYTES + 1, release.sha256, ""), "size limit");
        rejects(() -> new OtaRelease(1, OtaRelease.PACKAGE_NAME, 11, "v", 26, "a.apk", 1, "xyz", ""), "hash");

        URI pages = URI.create("https://zhgma.github.io/download/touchxbox/");
        URI github = URI.create("https://github.com/Zhgma/TouchXbox/releases/latest/download/");
        check(OtaSource.allows(pages, URI.create(pages + "latest.json")), "own source rejected");
        check(OtaSource.allows(github, URI.create("https://github.com/Zhgma/TouchXbox/releases/download/v1/app.apk")), "GitHub release redirect");
        check(OtaSource.allows(github, URI.create("https://release-assets.githubusercontent.com/assets/app?signature=value")), "GitHub asset redirect");
        for (String bad : new String[]{"http://github.com/a", "https://github.com.evil.invalid/a", "https://github.com@evil.invalid/a",
                "https://evil@github.com/a", "https://github.com:8443/a", "file:///a", "https://github.com/a#fragment"})
            check(!OtaSource.allows(github, URI.create(bad)), "unsafe redirect accepted: " + bad);
        check(!OtaSource.allows(pages, URI.create("https://release-assets.githubusercontent.com/a")), "unrelated CDN allowed for Pages");

        String text = "{\"changelog\":\"中文更新说明\"}";
        check(text.equals(OtaDownload.readManifest(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), new OtaDownload.Task())), "UTF-8");
        rejects(() -> OtaDownload.readManifest(new ByteArrayInputStream(new byte[128 * 1024 + 1]), new OtaDownload.Task()), "oversized manifest");

        File directory = Files.createTempDirectory("touchxbox-ota-valid-").toFile();
        File apk = null;
        try {
            long[] progress = {0};
            apk = OtaDownload.receive(new ByteArrayInputStream(bytes), directory, release, new OtaDownload.Task(), count -> progress[0] = count);
            check(apk.getName().equals(release.sha256 + ".apk"), "unverified filename");
            check(progress[0] == bytes.length && Arrays.equals(bytes, Files.readAllBytes(apk.toPath())), "download bytes");
            OtaDownload.verify(apk, release, new OtaDownload.Task());
            check(directory.list().length == 1, "partial file remained");
            byte[] corrupted = bytes.clone(); corrupted[100] ^= 1; Files.write(apk.toPath(), corrupted);
            final File changed = apk;
            rejects(() -> OtaDownload.verify(changed, release, new OtaDownload.Task()), "tampered cached APK accepted");
        } finally { if (apk != null) apk.delete(); directory.delete(); }
        failedDownload(Arrays.copyOf(bytes, bytes.length - 1), release, new OtaDownload.Task(), false);
        failedDownload(Arrays.copyOf(bytes, bytes.length + 1), release, new OtaDownload.Task(), false);
        byte[] corrupt = bytes.clone(); corrupt[0] ^= 1;
        failedDownload(corrupt, release, new OtaDownload.Task(), false);
        OtaDownload.Task cancelled = new OtaDownload.Task(); cancelled.cancel();
        failedDownload(bytes, release, cancelled, false);
        failedDownload(bytes, release, new OtaDownload.Task(), true);
        System.out.println("OTA tests passed: " + checks + " checks");
    }
}
