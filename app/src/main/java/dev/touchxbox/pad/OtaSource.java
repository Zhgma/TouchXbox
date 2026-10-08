package dev.touchxbox.pad;

import java.net.URI;

/** Change this base in a future APK to migrate the update source.
 * GitHub Releases can use https://github.com/OWNER/REPO/releases/latest/download/
 * with latest.json and its named APK attached to the same release.
 */
final class OtaSource {
    static final String BASE_URL = "https://github.com/Zhgma/TouchXbox/releases/latest/download/";
    static final String MANIFEST_URL = BASE_URL + "latest.json";
    static final String AUTHORIZE_URL = BASE_URL + "TouchXbox-authorize.cmd";
    static final String PAGE_URL = BASE_URL.replace("/releases/latest/download/", "/releases/latest");

    static boolean allows(URI source, URI target) {
        if (!"https".equalsIgnoreCase(target.getScheme()) || target.getHost() == null
                || target.getUserInfo() != null || (target.getPort() != -1 && target.getPort() != 443)
                || target.getFragment() != null) return false;
        String host = target.getHost();
        if (host.equalsIgnoreCase(source.getHost())) return true;
        // GitHub's release download endpoints redirect to its signed asset-storage URLs.
        return "github.com".equalsIgnoreCase(source.getHost())
                && ("release-assets.githubusercontent.com".equalsIgnoreCase(host)
                || "objects.githubusercontent.com".equalsIgnoreCase(host));
    }
}
