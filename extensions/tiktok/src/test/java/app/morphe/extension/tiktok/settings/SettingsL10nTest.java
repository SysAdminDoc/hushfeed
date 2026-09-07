package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.TogglePreference;
import app.morphe.extension.tiktok.settings.preference.categories.CommentsPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.DebugPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.DownloadsPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.ExtensionPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.FeedFilterPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.FeedNavigationPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.InboxPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.InterfacePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.PlaybackPreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.SharePreferenceCategory;
import app.morphe.extension.tiktok.settings.preference.categories.SimSpoofPreferenceCategory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The settings screens in the phone's language. The English text in the code is the key,
 * the generated {@link L10nTranslations} carries each language, and this walks every screen
 * to make sure nothing is left in English once German is selected.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SettingsL10nTest {
    public static final class TestActivity extends PreferenceActivity {}

    /** The tables the generator wrote, which are what the app carries. */
    private static final Map<String, String> GERMAN = L10nTranslations.of("de");
    private static final Map<String, String> INDONESIAN = L10nTranslations.of("in");

    @After
    public void resetStatus() throws Exception {
        setEveryStatus(false);
    }

    /**
     * The reflection above only reaches preference titles and summaries. A toast is a string
     * literal at a call site, so nothing checked those until this: the runtime feedback was
     * English on every phone while the settings around it were translated.
     *
     * <p>The scan is over the whole file rather than line by line, and it looks at every
     * literal anywhere inside the call's brackets. A first attempt matched only a quote
     * straight after the opening bracket, and it went green while eleven toasts were still
     * English, because {@code showToastShort(ok ? "a" : "b")} and a call wrapped across two
     * lines both slipped past it.
     *
     * <p>Feature Gate Lab is left out on purpose. It is a developer tool and its screens are
     * English by choice, which the row that opens it says. The shared extension module is not
     * walked either: it is TikTok-independent code, and this table is TikTok's.
     */
    @Test public void everyRuntimeToastGoesThroughTheTable() throws Exception {
        java.io.File root = new java.io.File("src/main/java/app/morphe/extension/tiktok");
        if (!root.isDirectory()) root = new java.io.File(
                "extensions/tiktok/src/main/java/app/morphe/extension/tiktok");
        assertTrue("could not find the source tree from " + new java.io.File(".").getAbsolutePath(),
                root.isDirectory());

        java.util.List<String> offenders = new java.util.ArrayList<>();
        java.nio.file.Path base = root.toPath();
        int scanned = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.walk(base)) {
            for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toList())) {
                if (file.toString().replace('\\', '/').contains("/featuregatelab/")) continue;
                String text = new String(java.nio.file.Files.readAllBytes(file),
                        java.nio.charset.StandardCharsets.UTF_8);
                scanned++;
                for (String offence : rawToastsIn(text)) {
                    offenders.add(base.relativize(file) + ":" + offence);
                }
            }
        }

        assertTrue("the scan found no files to read", scanned > 20);
        assertTrue("a toast is shown to the reader, so it belongs in the translation table. "
                        + "Wrap it in L10n.t, or L10n.f when it carries a value:\n"
                        + String.join("\n", offenders),
                offenders.isEmpty());
    }

    /**
     * Anything that puts words on the screen. Any method with "toast" in its name,
     * however it is spelled, plus the platform call those wrap and the undo banner,
     * which is a toast in every way that matters to a reader.
     *
     * <p>A content description counts too. Nothing shows it on screen, but it is the only
     * words a reader using TalkBack gets for a button drawn as a glyph, and three of the
     * player's overlay buttons reached that reader in English on a German phone.
     *
     * <p>Named this widely on purpose. The first version knew two method names, and a
     * file with a private toast helper of its own walked past it carrying five English
     * messages.
     */
    private static final String SHOWS_TEXT = "(?:\\b\\w*[Tt]oast\\w*|Toast\\s*\\.\\s*makeText|showUndoBanner"
            + "|setContentDescription)\\s*\\(";

    /** Marks every character of a source file as code, inside a literal, or inside a comment. */
    private static final byte CODE = 0, LITERAL = 1, COMMENT = 2;

    private static byte[] classify(String text) {
        byte[] kind = new byte[text.length()];
        int at = 0;
        while (at < text.length()) {
            char c = text.charAt(at);
            if (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '/') {
                while (at < text.length() && text.charAt(at) != '\n') kind[at++] = COMMENT;
            } else if (c == '/' && at + 1 < text.length() && text.charAt(at + 1) == '*') {
                int close = text.indexOf("*/", at + 2);
                int stop = close < 0 ? text.length() : close + 2;
                while (at < stop) kind[at++] = COMMENT;
            } else if (c == '"' || c == '\'') {
                kind[at++] = LITERAL;
                while (at < text.length()) {
                    char inside = text.charAt(at);
                    kind[at++] = LITERAL;
                    if (inside == '\\') { if (at < text.length()) kind[at++] = LITERAL; continue; }
                    if (inside == c) break;
                }
            } else {
                kind[at++] = CODE;
            }
        }
        return kind;
    }

    /**
     * Every string shown by a toast in this file that does not go through the table, as
     * "line  text". A literal counts as translated when the brackets it sits directly inside
     * belong to {@code L10n.t} or {@code L10n.f}; anything else, including a ternary and a
     * concatenation, is the reader seeing English.
     */
    private static java.util.List<String> rawToastsIn(String text) {
        byte[] kind = classify(text);
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher call = java.util.regex.Pattern
                .compile(SHOWS_TEXT).matcher(text);
        while (call.find()) {
            int open = call.end() - 1;
            if (kind[call.start()] != CODE) continue;
            int close = closingBracket(text, kind, open);
            if (close < 0) continue;
            for (int at = open + 1; at < close; at++) {
                if (kind[at] != LITERAL || text.charAt(at) != '"') continue;
                int literalEnd = at + 1;
                while (literalEnd < close && kind[literalEnd] == LITERAL) literalEnd++;
                if (!throughTheTable(text, kind, at)) {
                    found.add(lineOf(text, at) + "  "
                            + text.substring(at, Math.min(literalEnd, at + 70)));
                }
                at = literalEnd;
            }
        }
        return found;
    }

    private static int closingBracket(String text, byte[] kind, int open) {
        int depth = 0;
        for (int at = open; at < text.length(); at++) {
            if (kind[at] != CODE) continue;
            if (text.charAt(at) == '(') depth++;
            else if (text.charAt(at) == ')' && --depth == 0) return at;
        }
        return -1;
    }

    /** The call whose brackets this literal sits directly inside, if it is one of L10n's. */
    private static boolean throughTheTable(String text, byte[] kind, int literalAt) {
        int depth = 0;
        int owner = -1;
        for (int at = literalAt - 1; at >= 0; at--) {
            if (kind[at] != CODE) continue;
            char c = text.charAt(at);
            if (c == ')') depth++;
            else if (c == '(') {
                if (depth == 0) { owner = at; break; }
                depth--;
            }
        }
        if (owner < 0) return false;
        int end = owner;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        int start = end;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isJavaIdentifierPart(c) || c == '.') start--;
            else break;
        }
        String name = text.substring(start, end);
        return name.equals("L10n.t") || name.equals("L10n.f")
                || name.endsWith(".L10n.t") || name.endsWith(".L10n.f");
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int at = 0; at < index; at++) if (text.charAt(at) == '\n') line++;
        return line;
    }

    @Test
    public void everyLanguageTheBundleCarriesHasATable() {
        assertNotNull("German", L10nTranslations.of("de"));
        for (String language : L10nTranslations.LANGUAGES) {
            assertNotNull(language, L10nTranslations.of(language));
        }
        // A language nobody translated into falls back rather than failing.
        assertNull(L10nTranslations.of("xx"));
    }

    @Test
    public void aCountrySpecificLocaleFallsBackToItsLanguage() {
        // The tables are named by language; Austrian German gets the German table.
        assertEquals("Beschreibung ausblenden",
                L10n.t(contextFor("de", "AT"), "Hide the caption"));
        assertEquals("Beschreibung ausblenden",
                L10n.t(contextFor("de", ""), "Hide the caption"));
        // Indonesian arrives as either ISO code depending on the runtime, and both find it.
        assertEquals("Sembunyikan keterangan", L10n.t(contextFor("in", "ID"), "Hide the caption"));
        assertEquals("Sembunyikan keterangan", L10n.t(contextFor("id", "ID"), "Hide the caption"));
        // A language with no table keeps the English.
        assertEquals("Hide the caption", L10n.t(contextFor("fr", "FR"), "Hide the caption"));
    }

    @Test
    public void asecondLanguageAnswersWhenTheFirstHasNoTable() {
        // A phone set to French and then German shows German, because Android resolves a
        // string against the whole language list rather than only the first entry.
        Configuration configuration = new Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration());
        configuration.setLocales(new LocaleList(new Locale("fr", "FR"), new Locale("de", "DE")));
        Context context = RuntimeEnvironment.getApplication().createConfigurationContext(configuration);
        assertEquals(List.of("fr-rfr", "fr", "de-rde", "de"), L10n.tags(context));
        assertEquals("Beschreibung ausblenden", L10n.t(context, "Hide the caption"));
        // The order is the phone's: a language with a table earlier in the list wins.
        configuration.setLocales(new LocaleList(new Locale("in", "ID"), new Locale("de", "DE")));
        assertEquals("Sembunyikan keterangan", L10n.t(
                RuntimeEnvironment.getApplication().createConfigurationContext(configuration),
                "Hide the caption"));
    }

    /** A context whose resources report one locale, which is what the lookup reads. */
    private static Context contextFor(String language, String country) {
        Configuration configuration = new Configuration(
                RuntimeEnvironment.getApplication().getResources().getConfiguration());
        configuration.setLocale(new Locale(language, country));
        return RuntimeEnvironment.getApplication().createConfigurationContext(configuration);
    }

    @Test
    public void englishStaysEnglishAndUnknownTextPassesThrough() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        assertEquals("Hide the caption", L10n.t("Hide the caption"));
        assertEquals("Not a settings string", L10n.t("Not a settings string"));
        assertEquals("Current: 3 videos", L10n.f("Current: %1$s %2$s", "3", L10n.t("videos")));
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void germanShowsOnARealPreferenceUnderTheGermanLocale() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            TogglePreference toggle = new TogglePreference(activity, "Hide the caption",
                    "Hide the description under the creator's name on the feed.", Settings.HIDE_FEED_CAPTION);
            assertEquals("Beschreibung ausblenden", toggle.getTitle().toString());
            assertEquals("Die Beschreibung unter dem Namen des Creators im Feed ausblenden.",
                    toggle.getSummary().toString());
            assertEquals("Aktuell: 3 Videos", L10n.f(activity, "Current: %1$s %2$s", "3", L10n.t(activity, "videos")));
        }
    }

    @Test
    @Config(sdk = 28, qualifiers = "in-rID")
    public void indonesianShowsOnARealPreferenceUnderTheIndonesianLocale() {
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            TogglePreference toggle = new TogglePreference(activity, "Hide the caption",
                    "Hide the description under the creator's name on the feed.", Settings.HIDE_FEED_CAPTION);
            assertEquals("Sembunyikan keterangan", toggle.getTitle().toString());
            assertEquals("Sembunyikan deskripsi di bawah nama kreator pada feed.",
                    toggle.getSummary().toString());
            assertEquals("Saat ini: 3 video",
                    L10n.f(activity, "Current: %1$s %2$s", "3", L10n.t(activity, "videos")));
        }
    }

    @Test
    public void everyTableIsKeyedByTheEnglishTextItself() {
        assertEquals("Beschreibung ausblenden", GERMAN.get("Hide the caption"));
        assertEquals("Sembunyikan keterangan", INDONESIAN.get("Hide the caption"));
        for (String language : L10nTranslations.LANGUAGES) {
            Map<String, String> table = L10nTranslations.of(language);
            assertFalse(language, table.isEmpty());
            // Nothing is translated to nothing: an empty value would blank a label.
            for (Map.Entry<String, String> entry : table.entrySet()) {
                assertFalse("empty key in " + language, entry.getKey().isEmpty());
                assertFalse("empty translation of: " + entry.getKey(), entry.getValue().isEmpty());
            }
        }
    }

    @Test
    public void everyTableCoversTheSameEnglish() {
        // A language that is missing entries the others have would show a half English screen.
        for (String language : L10nTranslations.LANGUAGES) {
            assertEquals("keys of " + language, GERMAN.keySet(), L10nTranslations.of(language).keySet());
        }
    }

    @Test
    public void everySettingsStringHasATranslationEntry() throws Exception {
        Set<String> english = new LinkedHashSet<>(GERMAN.keySet());
        Set<String> shown = collectEverything();
        List<String> missing = new ArrayList<>();
        for (String text : shown) {
            // Composed text carries numbers or several lines, and a path is data; the parts
            // of composed text are entries of their own.
            boolean composed = text.contains("\n") || text.matches(".*\\d.*") || text.contains("/");
            if (!composed && !english.contains(text)) {
                missing.add(text);
            }
        }
        assertEquals("settings text without a translation entry: " + missing, 0, missing.size());
    }

    @Test
    @Config(sdk = 28, qualifiers = "de")
    public void nothingOnTheScreensStaysEnglishUnderTheGermanLocale() throws Exception {
        assertNothingStaysEnglish("de", GERMAN, "Beschreibung ausblenden");
    }

    @Test
    @Config(sdk = 28, qualifiers = "in-rID")
    public void nothingOnTheScreensStaysEnglishUnderTheIndonesianLocale() throws Exception {
        assertNothingStaysEnglish("in-rID", INDONESIAN, "Sembunyikan keterangan");
    }

    private void assertNothingStaysEnglish(String qualifier, Map<String, String> table,
                                           String expected) throws Exception {
        // Words the language spells the same way, like Transparent in German, cannot tell
        // the two apart, so only the entries that actually change are evidence.
        Set<String> english = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : table.entrySet()) {
            if (!entry.getValue().equals(entry.getKey())) {
                english.add(entry.getKey());
            }
        }
        Set<String> shown = collectEverything();
        List<String> untranslated = new ArrayList<>();
        for (String text : shown) {
            if (english.contains(text)) {
                untranslated.add(text);
            }
        }
        assertEquals("still English under " + qualifier + ": " + untranslated, 0, untranslated.size());
        assertTrue(shown.contains(expected));
    }

    private Set<String> collectEverything() throws Exception {
        setEveryStatus(true);
        Set<String> strings = new LinkedHashSet<>();
        try (var controller = Robolectric.buildActivity(TestActivity.class).setup()) {
            var activity = controller.get();
            Utils.setContext(activity);
            PreferenceScreen screen = activity.getPreferenceManager().createPreferenceScreen(activity);
            new FeedFilterPreferenceCategory(activity, screen);
            new FeedNavigationPreferenceCategory(activity, screen);
            new InterfacePreferenceCategory(activity, screen);
            new CommentsPreferenceCategory(activity, screen);
            new DownloadsPreferenceCategory(activity, screen);
            new PlaybackPreferenceCategory(activity, screen);
            new InboxPreferenceCategory(activity, screen);
            new SharePreferenceCategory(activity, screen);
            new SimSpoofPreferenceCategory(activity, screen);
            new DebugPreferenceCategory(activity, screen);
            new ExtensionPreferenceCategory(activity, screen);
            collect(screen, strings);
            for (String section : new String[]{"Feed filter", "Feed navigation", "Interface",
                    "Comments and translation", "Downloads", "Playback", "Inbox", "Share sheet",
                    "Region settings", "App behavior", "Diagnostics", "Settings"}) {
                strings.add(L10n.t(activity, section));
            }
            strings.add(L10n.t(activity, "Back up settings"));
            strings.add(L10n.t(activity, "Restore settings"));
            strings.add(L10n.t(activity, "Reset settings"));
            strings.add(L10n.t(activity, "Undo last restore"));
        }
        return strings;
    }

    private static void collect(Preference preference, Set<String> into) {
        add(preference.getTitle(), into);
        add(preference.getSummary(), into);
        if (preference instanceof ListPreference) {
            ListPreference list = (ListPreference) preference;
            add(list.getDialogTitle(), into);
            if (list.getEntries() != null) {
                for (CharSequence entry : list.getEntries()) {
                    add(entry, into);
                }
            }
        }
        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                collect(group.getPreference(i), into);
            }
        }
    }

    private static void add(CharSequence text, Set<String> into) {
        if (text != null && text.length() > 0 && !"%s".contentEquals(text)) {
            into.add(text.toString());
        }
    }

    private static void setEveryStatus(boolean value) throws Exception {
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.setBoolean(null, value);
            }
        }
    }
}
