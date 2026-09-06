package app.morphe.extension.tiktok.share;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public final class ShareUrlSanitizer {
    private static final String LOG_PREFIX = "[Morphe TikTok Share]";

    /**
     * The hosts a TikTok share link uses. Only these are rewritten, so a link to anywhere else
     * that happens to pass through here is left exactly as it was.
     */
    private static final List<String> TIKTOK_HOSTS = Arrays.asList(
            "tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com");

    private ShareUrlSanitizer() {}

    /** What a shared or copied link becomes: the chosen host first, then the tracking taken off. */
    public static String rewriteShareUrl(String url) {
        return stripAllQueryParams(withCustomDomain(url));
    }

    /**
     * Swaps a TikTok host for the one in the settings, keeping the scheme, the path and anything
     * after it. A front end like this is what makes a link show a preview where TikTok's own does
     * not; nothing is sent anywhere by this, the link simply names a different host.
     */
    static String withCustomDomain(String url) {
        String host = domain(Settings.CUSTOM_SHARE_DOMAIN.get());
        if (host.isEmpty() || url == null) return url;

        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int hostStart = schemeEnd + 3;
            int hostEnd = url.length();
            for (int at = hostStart; at < url.length(); at++) {
                char character = url.charAt(at);
                if (character == '/' || character == '?' || character == '#') {
                    hostEnd = at;
                    break;
                }
            }
            String original = url.substring(hostStart, hostEnd);
            if (!TIKTOK_HOSTS.contains(original.toLowerCase(Locale.ROOT))) return url;
            return url.substring(0, hostStart) + host + url.substring(hostEnd);
        } catch (RuntimeException exception) {
            Logger.printInfo(() -> LOG_PREFIX + " Failed to apply the custom share domain", exception);
            return url;
        }
    }

    /** The setting as a bare host: a scheme, a path or stray spaces around it are all allowed. */
    static String domain(String configured) {
        if (configured == null) return "";
        String host = configured.trim();
        int scheme = host.indexOf("://");
        if (scheme >= 0) host = host.substring(scheme + 3);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        host = host.trim();
        // A host is the only thing that can go here; anything with a space or a colon in it is
        // not one, and writing it into a link would make the link unusable rather than private.
        if (host.isEmpty() || host.indexOf(' ') >= 0 || host.indexOf(':') >= 0 || host.indexOf('.') < 0) {
            return "";
        }
        return host;
    }

    public static String stripAllQueryParams(String url) {
        if (url == null || !BaseSettings.SANITIZE_SHARING_LINKS.get()) return url;

        try {
            int queryIndex = url.indexOf('?');
            if (queryIndex <= 0) return url;

            String sanitizedUrl = url.substring(0, queryIndex);
            if (BaseSettings.DEBUG.get()) {
                Logger.printDebug(() ->
                    LOG_PREFIX + " Sanitized share URL " + describeUrl(url) + " -> " + describeUrl(sanitizedUrl)
                );
            }
            return sanitizedUrl;
        } catch (Exception ex) {
            Logger.printInfo(() -> LOG_PREFIX + " Failed to sanitize share URL", ex);
            return url;
        }
    }

    private static String describeUrl(String url) {
        if (url == null) return "null";

        String sanitized = url.replace('\n', ' ').replace('\r', ' ');
        int queryIndex = sanitized.indexOf('?');
        if (queryIndex >= 0) {
            sanitized = sanitized.substring(0, queryIndex) + "?<redacted>";
        }
        if (sanitized.length() > 120) {
            sanitized = sanitized.substring(0, 120) + "...";
        }

        return "\"" + sanitized + "\"";
    }
}
