package app.morphe.extension.tiktok.feedfilter;

import androidx.annotation.NonNull;

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
            return value != null && matchesWithinBudget(pattern, value);
        }
    }

    /**
     * How many characters a single name may be read for, counting the re-reads backtracking
     * costs. The two sides are far apart, so the number between them is not delicate:
     * measured against a 66 character display name, {@code dropship} costs 61 reads,
     * {@code ^The Very} costs 8, a six-way alternation costs 398, and every ordinary pattern
     * tried stayed under 500. On the other side, {@code ^(a+)+\1$} against 25 characters
     * costs 67 million reads and a quarter of a second, and four characters more never
     * finishes at all.
     *
     * <p>Two hundred thousand leaves a pattern somebody would actually write four hundred
     * times the room it needs, and cuts a runaway off in well under a millisecond. It also
     * catches the merely expensive: {@code (.*)(.*)(.*)z} costs 2.7 million reads and 19 ms
     * on that same name, which is not a hang but is still too much to spend per video.
     */
    private static final int MATCH_BUDGET = 200_000;

    /** One entry that ran out of budget, so it is only complained about once. */
    private static final Map<Pattern, Boolean> RUNAWAY = new ConcurrentHashMap<>();

    /**
     * Whether the pattern matches, giving up rather than hanging the thread it is on.
     *
     * <p>{@link Pattern} has no time limit of its own. The name is handed over through a
     * wrapper that counts the reads and throws once it has had enough, which is the only
     * place the regex engine can be interrupted from outside.
     */
    static boolean matchesWithinBudget(Pattern pattern, String value) {
        if (Boolean.TRUE.equals(RUNAWAY.get(pattern))) return false;
        try {
            return pattern.matcher(new BudgetedText(value, MATCH_BUDGET)).find();
        } catch (BudgetSpent spent) {
            if (RUNAWAY.putIfAbsent(pattern, Boolean.TRUE) == null) {
                Utils.showToastLong(L10n.f(
                        "A creator pattern is taking too long and was switched off: %1$s",
                        pattern.pattern()));
            }
            return false;
        }
    }

    /** Thrown out of the regex engine once a single match has read enough characters. */
    private static final class BudgetSpent extends RuntimeException {
        BudgetSpent() {
            super(null, null, false, false);
        }
    }

    /** A name the regex engine may only look at so many times. */
    private static final class BudgetedText implements CharSequence {
        private final CharSequence text;
        private int left;

        BudgetedText(CharSequence text, int budget) {
            this.text = text;
            this.left = budget;
        }

        @Override public int length() {
            return text.length();
        }

        @Override public char charAt(int index) {
            if (--left < 0) throw new BudgetSpent();
            return text.charAt(index);
        }

        @Override public CharSequence subSequence(int start, int end) {
            return new BudgetedText(text.subSequence(start, end), left);
        }

        @NonNull @Override public String toString() {
            return text.toString();
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
     * How long a creator pattern may be. It is user input and it runs against every name in
     * every feed page, so a long one with nested quantifiers can take the feed thread with
     * it. Anything a person types to match a handle fits well inside this, and the list rides
     * along in a settings backup, which is the way somebody else's pattern could arrive.
     */
    private static final int MAX_PATTERN_LENGTH = 200;

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
        if (source.length() > MAX_PATTERN_LENGTH) {
            COMPILED.put(entry, INVALID);
            Utils.showToastLong(L10n.f(
                    "That creator pattern is too long to use, so it was skipped: %1$s", entry));
            return null;
        }
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
