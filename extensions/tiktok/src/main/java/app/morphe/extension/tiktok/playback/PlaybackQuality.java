package app.morphe.extension.tiktok.playback;

import android.content.Context;
import android.net.ConnectivityManager;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.download.QualitySelector;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PlaybackQuality {
    /**
     * How long the metered answer is reused. Every parsed video model asks, and the answer
     * comes from a system service, so it is not worth a binder call each time.
     */
    static final long METERED_CACHE_MS = 5_000L;

    private static volatile JsonCache cache;
    private static volatile MeteredState meteredState;

    private PlaybackQuality() {}

    /**
     * The quality that applies right now. The mobile data choice is a ceiling: it lowers what
     * plays on a metered connection and never raises it, so setting 360p everywhere and 720p
     * on mobile data still gives 360p.
     */
    public static String mode() {
        return effectiveMode(Settings.PLAYBACK_QUALITY.get(), Settings.PLAYBACK_QUALITY_METERED.get(), isMetered());
    }

    static String effectiveMode(String mode, String meteredMode, boolean metered) {
        if (!metered || meteredMode == null || "off".equals(meteredMode)) return mode;
        return ceiling(meteredMode) < ceiling(mode) ? meteredMode : mode;
    }

    /** The tallest video a mode allows. Lower is more restrictive. */
    private static int ceiling(String mode) {
        if (mode == null) return Integer.MAX_VALUE;
        switch (mode) {
            case "lowest": return 0;
            case "highest": return Integer.MAX_VALUE - 1;
            case "auto": return Integer.MAX_VALUE;
            default:
                try {
                    return Integer.parseInt(mode);
                } catch (NumberFormatException ignored) {
                    return Integer.MAX_VALUE;
                }
        }
    }

    private static boolean isMetered() {
        long now = System.currentTimeMillis();
        MeteredState cached = meteredState;
        if (cached != null && now - cached.atMs <= METERED_CACHE_MS) return cached.metered;

        boolean metered = false;
        try {
            Context context = Utils.getContext();
            ConnectivityManager manager = context == null ? null
                    : (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            metered = manager != null && manager.isActiveNetworkMetered();
        } catch (Throwable ignored) {
            // No permission or no service: treat the connection as unmetered and change nothing.
        }
        meteredState = new MeteredState(metered, now);
        return metered;
    }

    public static List<?> filter(List<?> original) {
        Object selected = QualitySelector.choose(original, mode());
        return selected == null ? original : new ArrayList<>(Collections.singletonList(selected));
    }

    public static Object cacheModel(Object original) {
        // Native parsed objects can retain a previous quality. Rebuild them from the filtered model.
        return "auto".equals(mode()) ? original : null;
    }

    public static Map<?, ?> filterMap(Map<?, ?> original) {
        String mode = mode();
        if (original == null || "auto".equals(mode)) return original;
        Object raw = original.get("dynamic_video");
        if (!(raw instanceof Map<?, ?>)) return original;
        Map<?, ?> dynamic = (Map<?, ?>) raw;
        Object list = dynamic.get("dynamic_video_list");
        if (!(list instanceof List<?>) || ((List<?>) list).size() < 2) return original;
        List<?> variants = (List<?>) list;
        int selected = select(variants, mode);
        if (selected < 0) return original;
        Map<Object, Object> filtered = new LinkedHashMap<>(dynamic);
        filtered.put("dynamic_video_list", new ArrayList<>(Collections.singletonList(variants.get(selected))));
        Map<Object, Object> result = new LinkedHashMap<>(original);
        result.put("dynamic_video", filtered);
        return result;
    }

    public static String filterJson(String original) {
        String mode = mode();
        if (original == null || "auto".equals(mode)) return original;
        JsonCache previous = cache;
        if (previous != null && previous.mode.equals(mode) && previous.source.equals(original)) return previous.result;
        String result = original;
        try {
            JSONObject root = new JSONObject(original);
            JSONObject dynamic = root.optJSONObject("dynamic_video");
            JSONArray values = dynamic == null ? null : dynamic.optJSONArray("dynamic_video_list");
            if (values != null && values.length() > 1) {
                List<Object> variants = new ArrayList<>();
                for (int i = 0; i < values.length(); i++) variants.add(values.get(i));
                int selected = select(variants, mode);
                if (selected >= 0) {
                    dynamic.put("dynamic_video_list", new JSONArray().put(values.get(selected)));
                    result = root.toString();
                }
            }
        } catch (JSONException exception) {
            Logger.printException(() -> "Could not read the playback quality model", exception);
        }
        cache = new JsonCache(original, mode, result);
        return result;
    }

    private static int select(List<?> variants, String mode) {
        List<Variant> gears = new ArrayList<>();
        for (int i = 0; i < variants.size(); i++) gears.add(new Variant(variants.get(i), i));
        Object selected = QualitySelector.choose(gears, mode);
        return selected == null ? -1 : ((Variant) selected).index;
    }

    private static Object value(Object source, String key) {
        if (source instanceof JSONObject) return ((JSONObject) source).opt(key);
        return source instanceof Map<?, ?> ? ((Map<?, ?>) source).get(key) : null;
    }

    private static long number(Object source, String key) {
        Object value = value(source, key);
        if (value instanceof Number) return ((Number) value).longValue();
        try { return value == null ? 0 : Long.parseLong(value.toString()); }
        catch (NumberFormatException exception) { return 0; }
    }

    /** Named fields let the shared gear selector read the same metadata as native BitRate objects. */
    public static final class Variant {
        public final String gearName;
        public final long bitRate;
        public final Address playAddr;
        final int index;
        Variant(Object source, int index) {
            this.index = index;
            Object name = value(source, "gear_name");
            gearName = name == null ? "" : name.toString();
            long actual = number(source, "real_bitrate");
            bitRate = actual > 0 ? actual : number(source, "bitrate");
            playAddr = new Address(source);
        }
    }

    public static final class Address {
        public final long size, width, height;
        public final List<String> urlList = new ArrayList<>();
        Address(Object source) {
            size = number(source, "size"); width = number(source, "vwidth"); height = number(source, "vheight");
            for (String key : new String[]{"main_url", "backup_url_1", "backup_url_2", "backup_url_3"}) {
                Object url = value(source, key);
                if (url instanceof String) urlList.add((String) url);
            }
        }
    }

    private static final class MeteredState {
        final boolean metered;
        final long atMs;
        MeteredState(boolean metered, long atMs) { this.metered = metered; this.atMs = atMs; }
    }

    private static final class JsonCache {
        final String source, mode, result;
        JsonCache(String source, String mode, String result) { this.source = source; this.mode = mode; this.result = result; }
    }
}
