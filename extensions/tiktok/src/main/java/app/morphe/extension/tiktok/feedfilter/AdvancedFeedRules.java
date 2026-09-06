package app.morphe.extension.tiktok.feedfilter;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import com.ss.android.ugc.aweme.feed.model.AwemeStatistics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class AdvancedFeedRules {
    private AdvancedFeedRules() {}

    public static final class KeywordFilter implements IFilter {
        public boolean getEnabled() { return !Settings.BLOCKED_CAPTION_WORDS.get().trim().isEmpty(); }
        public boolean getFiltered(Aweme item) {
            String caption = Reflect.string(item, "getDesc", "desc");
            if (caption == null) return false;
            caption = caption.toLowerCase(Locale.ROOT);
            for (String word : terms(Settings.BLOCKED_CAPTION_WORDS.get())) {
                if (!word.isEmpty() && caption.contains(word)) return true;
            }
            return false;
        }
    }

    public static final class CreatorFilter implements IFilter {
        public boolean getEnabled() { return !Settings.BLOCKED_CREATORS.get().trim().isEmpty(); }
        public boolean getFiltered(Aweme item) {
            Object author = Reflect.property(item, "getAuthor", "author");
            String uid = Reflect.string(author, "getUid", "uid");
            String handle = Reflect.string(author, "getUniqueId", "uniqueId");
            String nickname = Reflect.string(author, "getNickname", "nickname");
            for (String entry : rawTerms(Settings.BLOCKED_CREATORS.get())) {
                if (isPattern(entry)) {
                    Pattern pattern = compiled(entry);
                    if (pattern != null && (matches(pattern, handle) || matches(pattern, nickname))) {
                        return true;
                    }
                    continue;
                }
                if (entry.startsWith("@")) entry = entry.substring(1);
                if (!entry.isEmpty() && (entry.equalsIgnoreCase(uid) || entry.equalsIgnoreCase(handle))) return true;
            }
            return false;
        }

        private static boolean matches(Pattern pattern, String value) {
            return value != null && pattern.matcher(value).find();
        }
    }

    /** An entry between slashes is a pattern rather than a name to match exactly. */
    static boolean isPattern(String entry) {
        return entry.length() > 2 && entry.startsWith("/") && entry.endsWith("/");
    }

    /** One entry that will not compile, remembered so it is only ever said once. */
    private static final Pattern INVALID = Pattern.compile("");
    /**
     * Every entry seen so far, compiled. Each item on a feed page runs through every entry,
     * so a cache of one would never hit; and a map keeps the answer and the entry it belongs
     * to together, which two fields written in sequence do not.
     */
    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    /**
     * The pattern for one entry, compiled once. A pattern that will not compile is dropped
     * and said once, because the entry otherwise looks like it is working.
     */
    static Pattern compiled(String entry) {
        Pattern cached = COMPILED.get(entry);
        if (cached != null) {
            return cached == INVALID ? null : cached;
        }

        String source = entry.substring(1, entry.length() - 1);
        Pattern pattern;
        try {
            pattern = Pattern.compile(source, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException invalid) {
            COMPILED.put(entry, INVALID);
            Utils.showToastLong(L10n.f("Hushfeed cannot read the creator pattern %1$s", entry));
            return null;
        }
        COMPILED.put(entry, pattern);
        return pattern;
    }

    public static final class PromotionalMusicFilter implements IFilter {
        public boolean getEnabled() { return Settings.HIDE_PROMOTIONAL_MUSIC.get(); }
        public boolean getFiltered(Aweme item) { return item.isWithPromotionalMusic(); }
    }

    public static final class LiveReplayFilter implements IFilter {
        public boolean getEnabled() { return Settings.HIDE_LIVE_REPLAYS.get(); }
        public boolean getFiltered(Aweme item) { return item.isLiveReplay(); }
    }

    /** Last content rule, so fallback candidates have passed every hard block. */
    public static final class QualityFilter implements IFilter {
        public boolean getEnabled() {
            return Settings.MAX_VIDEO_SECONDS.get() > 0 || Settings.MAX_VIEWS_PER_LIKE.get() > 0;
        }
        public boolean getFiltered(Aweme item) { return distance(item) > 0; }

        static double distance(Aweme item) {
            double distance = 0;
            int seconds = Settings.MAX_VIDEO_SECONDS.get();
            if (seconds > 0) {
                Object video = Reflect.property(item, "getVideo", "video");
                long ms = positive(Reflect.property(video, "getDuration", "videoLength"));
                if (ms <= 0) ms = positive(Reflect.property(video, "getPilotLength", "pilotLength"));
                if (ms > 0) distance = Math.max(0, ms / (seconds * 1000.0) - 1);
            }
            int maximum = Settings.MAX_VIEWS_PER_LIKE.get();
            AwemeStatistics stats = maximum > 0 ? item.getStatistics() : null;
            if (stats != null) {
                long views = stats.getPlayCount(), likes = stats.getDiggCount();
                // Missing/negative counts and zero views carry no engagement signal.
                if (views > 0 && likes >= 0) {
                    double ratio = likes == 0 ? Double.MAX_VALUE : views / (double) likes;
                    distance = Math.max(distance, ratio / maximum - 1);
                }
            }
            return distance;
        }
    }

    private static long positive(Object value) {
        return value instanceof Number ? Math.max(0, ((Number) value).longValue()) : 0;
    }

    private static String[] terms(String value) {
        return value.toLowerCase(Locale.ROOT).trim().split("\\s*[,\\n]\\s*");
    }

    /**
     * The same entries with their case intact, which a pattern needs: lower casing turns
     * \D into \d.
     *
     * A comma inside a pattern is not a separator, so a fragment that opens a pattern takes
     * the ones after it until one closes it. A line is as far as that reaches, and a pattern
     * nothing closes gives its fragments back as they were: a stray slash must not swallow
     * the names after it.
     */
    static String[] rawTerms(String value) {
        List<String> entries = new ArrayList<>();
        for (String line : value.trim().split("\\s*\\n\\s*")) {
            List<String> pending = null;
            for (String fragment : line.split("\\s*,\\s*")) {
                if (pending != null) {
                    pending.add(fragment);
                    if (fragment.endsWith("/")) {
                        entries.add(String.join(",", pending));
                        pending = null;
                    }
                    continue;
                }
                if (fragment.startsWith("/") && !isPattern(fragment)) {
                    pending = new ArrayList<>();
                    pending.add(fragment);
                    continue;
                }
                entries.add(fragment);
            }
            if (pending != null) entries.addAll(pending);
        }
        return entries.toArray(new String[0]);
    }
}
