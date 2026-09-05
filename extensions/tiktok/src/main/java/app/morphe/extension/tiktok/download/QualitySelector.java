package app.morphe.extension.tiktok.download;

import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.base.model.UrlModel;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects only actual video gears, never cover or thumbnail URLs. */
public final class QualitySelector {
    private static final Pattern HEIGHT = Pattern.compile("(?<![0-9])(2160|1440|1080|960|720|640|576|540|480|432|360|288|240)(?![0-9])");
    private QualitySelector() {}

    public static UrlModel download(Object video) {
        String mode = Settings.DOWNLOAD_VIDEO_QUALITY.get();
        if ("auto".equals(mode)) return null;
        // Separate DASH tracks need the full download path and muxer, not a video-only URL.
        if (Boolean.TRUE.equals(Reflect.invoke(video, "hasDashBitrate"))) return null;
        // Read the backing field: getBitRate may itself be patched for playback.
        Object raw = Reflect.readField(video, "bitRate");
        Object gear = raw instanceof List<?> ? choose((List<?>) raw, mode) : null;
        Object address = Reflect.property(gear, "getPlayAddr", "playAddr");
        return address instanceof UrlModel ? (UrlModel) address : null;
    }

    public static Object choose(List<?> gears, String mode) {
        if (gears == null || "auto".equals(mode)) return null;
        int target;
        switch (mode == null ? "" : mode) {
            case "highest": case "lowest": target = 0; break;
            case "1080": case "720": case "540": case "480": case "360": target = Integer.parseInt(mode); break;
            default: return null;
        }
        Object best = null;
        for (Object gear : gears) {
            if (!usable(Reflect.property(gear, "getPlayAddr", "playAddr"))) continue;
            if (target > 0 && height(gear) == 0) continue;
            if (best == null || better(gear, best, target, "lowest".equals(mode))) best = gear;
        }
        return best;
    }

    private static boolean better(Object candidate, Object current, int target, boolean lowest) {
        int a = height(candidate), b = height(current);
        if (target > 0) {
            if ((a <= target) != (b <= target)) return a <= target;
            if (a != b) return a <= target ? a > b : a < b;
        }
        int compare = Integer.compare(a, b);
        if (compare == 0) compare = Long.compare(number(candidate, "getBitRate", "bitRate"), number(current, "getBitRate", "bitRate"));
        if (compare == 0) compare = Long.compare(size(candidate), size(current));
        return lowest ? compare < 0 : compare > 0;
    }

    private static int height(Object gear) {
        String name = Reflect.string(gear, "getGearName", "gearName");
        if (name != null) {
            Matcher matcher = HEIGHT.matcher(name);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
        }
        Object address = Reflect.property(gear, "getPlayAddr", "playAddr");
        long width = number(address, "getWidth", "width"), height = number(address, "getHeight", "height");
        return (int) (width > 0 && height > 0 ? Math.min(width, height) : Math.max(width, height));
    }

    private static long size(Object gear) {
        return number(Reflect.property(gear, "getPlayAddr", "playAddr"), "getSize", "size");
    }

    private static long number(Object value, String getter, String field) {
        Object raw = Reflect.property(value, getter, field);
        return raw instanceof Number ? ((Number) raw).longValue() : 0;
    }

    private static boolean usable(Object address) {
        Object urls = Reflect.property(address, "getUrlList", "urlList");
        if (!(urls instanceof List<?>)) return false;
        for (Object url : (List<?>) urls) {
            if (url instanceof String && (((String) url).startsWith("https://") || ((String) url).startsWith("http://"))) return true;
        }
        return false;
    }
}
