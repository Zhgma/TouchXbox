package dev.touchxbox.pad;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import javax.net.ssl.HttpsURLConnection;

/** Network and integrity checks, with no Android or Activity dependencies. */
final class OtaDownload {
    interface Progress { void changed(long received); }

    static final class Task {
        private volatile boolean cancelled;
        private volatile HttpsURLConnection connection;
        void cancel() {
            cancelled = true;
            HttpsURLConnection current = connection;
            if (current != null) current.disconnect();
        }
        void check() throws InterruptedIOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("已取消");
        }
    }

    private static HttpsURLConnection open(String address, Task task) throws IOException {
        URI source = URI.create(OtaSource.BASE_URL);
        URL url = new URL(address);
        for (int redirects = 0; redirects <= 5; redirects++) {
            task.check();
            try {
                if (!OtaSource.allows(source, url.toURI())) throw new IOException("更新地址跳转到了未允许的站点");
            } catch (URISyntaxException e) { throw new IOException("更新地址无效", e); }
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            task.connection = connection;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "TouchXbox-OTA/1");
            try {
                task.check();
                int code = connection.getResponseCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) throw new IOException("更新地址跳转无效");
                    url = new URL(url, location);
                    connection.disconnect(); task.connection = null;
                    continue;
                }
                if (code == 404) throw new IOException("更新文件尚未发布（HTTP 404），请稍后重试");
                if (code != 200) throw new IOException("更新服务器返回 HTTP " + code + "，请稍后重试");
                return connection;
            } catch (IOException e) {
                connection.disconnect(); task.connection = null;
                throw e;
            }
        }
        throw new IOException("更新地址跳转次数过多");
    }

    static String manifest(Task task) throws IOException {
        HttpsURLConnection connection = open(OtaSource.MANIFEST_URL + "?t=" + System.currentTimeMillis(), task);
        try (InputStream input = connection.getInputStream()) {
            return readManifest(input, task);
        } finally { connection.disconnect(); task.connection = null; }
    }

    static String readManifest(InputStream input, Task task) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            task.check();
            if (bytes.size() + count > 128 * 1024) throw new IOException("更新信息文件过大");
            bytes.write(buffer, 0, count);
        }
        task.check();
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    static File download(OtaRelease release, File directory, Task task, Progress progress) throws Exception {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建更新下载目录");
        File target = new File(directory, release.sha256 + ".apk");
        if (target.isFile()) {
            try { verify(target, release, task); return target; }
            catch (InterruptedIOException e) { throw e; }
            catch (IOException e) { if (!target.delete()) throw new IOException("无法清理损坏的更新文件"); }
        }
        HttpsURLConnection connection = open(OtaSource.BASE_URL + release.apkFile, task);
        try {
            long length = connection.getContentLengthLong();
            if (length >= 0 && length != release.size) throw new IOException("安装包大小与更新信息不符，请稍后重新检查更新");
            try (InputStream input = connection.getInputStream()) {
                return receive(input, directory, release, task, progress);
            }
        } finally { connection.disconnect(); task.connection = null; }
    }

    // Only a complete file with the expected digest becomes an installable .apk.
    static File receive(InputStream input, File directory, OtaRelease release, Task task, Progress progress) throws Exception {
        File partial = File.createTempFile("download-", ".part", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long received = 0;
            byte[] buffer = new byte[32768];
            try (FileOutputStream output = new FileOutputStream(partial)) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    task.check();
                    received += count;
                    if (received > release.size) throw new IOException("下载内容超过声明的安装包大小");
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    progress.changed(received);
                }
                task.check();
                if (received != release.size) throw new IOException("安装包下载不完整，请重试");
                if (!hex(digest.digest()).equals(release.sha256)) throw new IOException("安装包 SHA-256 校验失败，请重试");
                output.getFD().sync();
            }
            task.check();
            File target = new File(directory, release.sha256 + ".apk");
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } finally { if (partial.exists()) partial.delete(); }
    }

    static void verify(File file, OtaRelease release, Task task) throws Exception {
        task.check();
        if (!file.isFile() || file.length() != release.size) throw new IOException("安装包不存在或大小不符，请重新下载");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) { task.check(); digest.update(buffer, 0, count); }
        }
        task.check();
        if (!hex(digest.digest()).equals(release.sha256)) throw new IOException("安装包 SHA-256 校验失败，请重新下载");
    }

    static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
