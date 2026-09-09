package app.morphe.extension.tiktok.settings;

import static org.junit.Assert.assertNotEquals;
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
    @Test public void theGeneratedTableIsTheOneInTheTables() throws Exception {
        // Every other check here reads L10nTranslations, which is generated. A value edited in a
        // table without rerunning scripts/gen-l10n.py was invisible to all of them, and so was a
        // generated file edited by hand, which its own header forbids.
        //
        // German is a tab table and Indonesian is the comma form Weblate hosts, so both readers
        // are exercised on every run rather than one of them being a claim in the README.
        for (String language : languages()) {
            java.util.Map<String, String> generated = L10nTranslations.of(language);
            java.util.Map<String, String> table = readTable(language);

            java.util.List<String> problems = new java.util.ArrayList<>();
            for (java.util.Map.Entry<String, String> row : table.entrySet()) {
                String was = generated.get(row.getKey());
                if (was == null) {
                    problems.add(language + " has a row the generated table does not: " + row.getKey());
                } else if (!was.equals(row.getValue())) {
                    problems.add(language + " differs for " + row.getKey()
                            + ": tsv has " + row.getValue() + ", generated has " + was);
                }
            }
            for (String key : generated.keySet()) {
                if (!table.containsKey(key)) {
                    problems.add(language + " generated a row the table does not have: " + key);
                }
            }
            assertEquals("the generated translations are not the ones in the " + language
                    + " table, so run scripts/gen-l10n.py: " + problems, 0, problems.size());
            assertFalse("the " + language + " table is empty", table.isEmpty());
        }
    }

    @Test public void theTableComparisonCanActuallyFail() throws Exception {
        // The comparison above only means something if a changed value is visible to it.
        java.util.Map<String, String> table = readTable("de");
        String key = table.keySet().iterator().next();
        java.util.Map<String, String> changed = new java.util.LinkedHashMap<>(table);
        changed.put(key, changed.get(key) + " x");
        assertNotEquals("a changed value read the same", table.get(key), changed.get(key));
        assertFalse("the tables compare equal after a change", table.equals(changed));
    }

    /**
     * One language table, in whichever form it is kept.
     *
     * <p>Tab separated, one key then the translation with # for a comment, or the comma form
     * Weblate hosts: a source,target header and one row per entry.
     */
    private static java.util.Map<String, String> readTable(String language) throws Exception {
        java.io.File file = tableFile(language, ".tsv");
        if (file == null) file = tableFile(language, ".csv");
        assertNotNull("no .tsv or .csv table for " + language, file);
        String body = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        // A spreadsheet round trip leaves a byte order mark, which the generator now skips.
        if (body.startsWith("\ufeff")) body = body.substring(1);
        return file.getName().endsWith(".csv")
                ? readCsvRows(language, body) : readTsvRows(language, body);
    }

    private static java.io.File l10nDirectory() {
        java.io.File directory = new java.io.File("src/main/l10n");
        if (!directory.isDirectory()) directory = new java.io.File("extensions/tiktok/src/main/l10n");
        assertTrue("could not find the l10n tables", directory.isDirectory());
        return directory;
    }

    /** Every language with a table, in either form. Named so a new one is covered on sight. */
    private static java.util.List<String> languages() {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String name : java.util.Objects.requireNonNull(l10nDirectory().list())) {
            if (!name.endsWith(".tsv") && !name.endsWith(".csv")) continue;
            // Lower-cased, because that is what the generator does. A pt-rBR table, which is the
            // spelling L10n builds for a regional tag, otherwise asks for a language the
            // generated class has never heard of and gets null back.
            String language = name.substring(0, name.length() - 4)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!ENGLISH_BASE.equals(language)) found.add(language);
        }
        java.util.Collections.sort(found);
        assertTrue("no language tables at all", found.size() >= 2);
        return found;
    }

    private static java.io.File tableFile(String language, String extension) {
        java.io.File file = new java.io.File(l10nDirectory(), language + extension);
        return file.isFile() ? file : null;
    }

    /** The keys are English text, so the base is a list of source strings, not a translation. */
    private static final String ENGLISH_BASE = "en";

    /** A newline is the two characters \n in a table, the same as the generator reads it. */
    private static String unescapeTableNewlines(String text) {
        return text.replace("\\n", "\n");
    }

    private static java.util.Map<String, String> readTsvRows(String language, String body) {
        java.util.Map<String, String> rows = new java.util.LinkedHashMap<>();
        for (String line : body.split("\\n")) {
            String text = line.replace("\r", "");
            if (text.isEmpty() || text.startsWith("#")) continue;
            int tab = text.indexOf('\t');
            assertTrue("a row with no tab in " + language + ".tsv: " + text, tab > 0);
            rows.put(unescapeTableNewlines(text.substring(0, tab)),
                    unescapeTableNewlines(text.substring(tab + 1)));
        }
        return rows;
    }

    /** Enough of RFC 4180 for these tables: quoted fields, doubled quotes inside them. */
    private static java.util.Map<String, String> readCsvRows(String language, String body) {
        java.util.List<java.util.List<String>> records = new java.util.ArrayList<>();
        java.util.List<String> record = new java.util.ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean atFieldStart = true;
        for (int index = 0; index < body.length(); index++) {
            char character = body.charAt(index);
            if (quoted) {
                if (character != '"') {
                    field.append(character);
                } else if (index + 1 < body.length() && body.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = false;
                }
                continue;
            }
            if (character == '"' && atFieldStart) {
                quoted = true;
                atFieldStart = false;
            } else if (character == ',') {
                record.add(field.toString());
                field.setLength(0);
                atFieldStart = true;
            } else if (character == '\n' || character == '\r') {
                // A bare CR ends a record, the same as Python's csv. CRLF is one terminator,
                // so the LF after a CR is taken with it rather than ending a second, empty one.
                if (character == '\r' && index + 1 < body.length()
                        && body.charAt(index + 1) == '\n') {
                    index++;
                }
                record.add(field.toString());
                field.setLength(0);
                records.add(record);
                record = new java.util.ArrayList<>();
                atFieldStart = true;
            } else {
                field.append(character);
                atFieldStart = false;
            }
        }
        if (field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }

        assertFalse("the " + language + " table has no header", records.isEmpty());
        java.util.List<String> header = records.get(0);
        assertTrue("the " + language + " header has to start source,target, not " + header,
                header.size() >= 2
                        && "source".equalsIgnoreCase(header.get(0).trim())
                        && "target".equalsIgnoreCase(header.get(1).trim()));

        java.util.Map<String, String> rows = new java.util.LinkedHashMap<>();
        for (int index = 1; index < records.size(); index++) {
            java.util.List<String> line = records.get(index);
            if (line.isEmpty() || line.get(0).isEmpty() || line.get(0).startsWith("#")) continue;
            assertTrue("a row with no target column in " + language + ".csv: " + line,
                    line.size() >= 2);
            rows.put(unescapeTableNewlines(line.get(0)), unescapeTableNewlines(line.get(1)));
        }
        return rows;
    }

    @Test public void theCsvReaderEndsARowWhereThePythonOneDoes() {
        // No shipped table carries a bare CR, so nothing here would have caught the reader
        // swallowing it and running two rows together. These are the shapes Python's csv module
        // produces, checked against it.
        java.util.Map<String, String> unixEndings =
                readCsvRows("probe", "source,target\na,1\nb,2\n");
        assertEquals(2, unixEndings.size());
        assertEquals("1", unixEndings.get("a"));

        java.util.Map<String, String> windowsEndings =
                readCsvRows("probe", "source,target\r\na,1\r\nb,2\r\n");
        assertEquals("a CRLF was read as two row endings", 2, windowsEndings.size());
        assertEquals("1", windowsEndings.get("a"));

        java.util.Map<String, String> classicMacEndings =
                readCsvRows("probe", "source,target\ra,1\rb,2\r");
        assertEquals("a bare CR did not end a row", 2, classicMacEndings.size());
        assertEquals("1", classicMacEndings.get("a"));

        java.util.Map<String, String> quoted =
                readCsvRows("probe", "source,target\n\"a,b\",\"says \"\"hi\"\"\"\n");
        assertEquals(1, quoted.size());
        assertEquals("says \"hi\"", quoted.get("a,b"));

        java.util.Map<String, String> looseQuote = readCsvRows("probe", "source,target\na,b\"c\n");
        assertEquals("a quote inside an unquoted field opened one", "b\"c",
                looseQuote.get("a"));
    }

    @Test public void theEnglishBaseIsExactlyTheStringsTheTablesCarry() throws Exception {
        // Weblate translates from a monolingual base rather than from a language table. It is
        // generated, so it goes stale the same way L10nTranslations does if nobody reruns the
        // script, and nothing else here would notice. Both directions matter: a string added to
        // a table has to reach it, and a string deleted from every table has to leave it, or
        // translators keep being asked for text nothing shows any more.
        java.util.Map<String, String> base = readTable(ENGLISH_BASE);
        java.util.Set<String> carried = new java.util.TreeSet<>();
        for (String language : languages()) carried.addAll(readTable(language).keySet());

        java.util.Set<String> missing = new java.util.TreeSet<>(carried);
        missing.removeAll(base.keySet());
        assertEquals(ENGLISH_BASE + ".csv is missing source strings, so run scripts/gen-l10n.py: "
                + missing, 0, missing.size());

        java.util.Set<String> extra = new java.util.TreeSet<>(base.keySet());
        extra.removeAll(carried);
        assertEquals(ENGLISH_BASE + ".csv still lists strings no table carries, so run "
                + "scripts/gen-l10n.py: " + extra, 0, extra.size());

        for (java.util.Map.Entry<String, String> row : base.entrySet()) {
            assertEquals("the base translates a string instead of repeating it",
                    row.getKey(), row.getValue());
        }
    }

    @Test public void everyTranslationKeepsTheShapeOfItsKey() {
        // Defects the key-set checks cannot see. A placeholder that changed, was dropped or was
        // invented; a sentence that lost or gained its terminator; a quote pair that does not
        // close. The first pass of this check only looked at numbered placeholders in the key,
        // so a bare "%s" and an invented placeholder both walked past it.
        java.util.List<String> problems = new java.util.ArrayList<>();
        java.util.Map<String, java.util.Map<String, String>> tables = new java.util.LinkedHashMap<>();
        tables.put("de", GERMAN);
        tables.put("in", INDONESIAN);

        for (java.util.Map.Entry<String, java.util.Map<String, String>> table : tables.entrySet()) {
            String language = table.getKey();
            for (java.util.Map.Entry<String, String> row : table.getValue().entrySet()) {
                String key = row.getKey();
                String value = row.getValue();

                // Both directions and both spellings, so a dropped, changed, added or
                // renumbered placeholder is all the same finding.
                java.util.List<String> wanted = placeholders(key);
                java.util.List<String> given = placeholders(value);
                if (!wanted.equals(given)) {
                    problems.add(language + " placeholders " + wanted + " became " + given
                            + " in: " + key);
                }

                if (terminator(key) != terminator(value)) {
                    problems.add(language + " ends the sentence with '" + terminator(value)
                            + "' where the key ends with '" + terminator(key) + "' in: " + key);
                }

                // German opens low and closes high. Everything else here uses the English pair.
                char open = "de".equals(language) ? '\u201e' : '\u201c';
                char close = "de".equals(language) ? '\u201c' : '\u201d';
                long opened = value.chars().filter(c -> c == open).count();
                long closed = value.chars().filter(c -> c == close).count();
                if (opened != closed) {
                    problems.add(language + " opened " + opened + " quotes and closed " + closed
                            + " in: " + key);
                }
            }
        }
        assertEquals("translations that do not keep the shape of their key: " + problems,
                0, problems.size());
    }

    @Test public void theTranslationShapeCheckCanActuallyFail() {
        // A scan with nothing to find is a scan that proves nothing. These are the four shapes
        // the check exists for, put in front of it on purpose.
        assertNotEquals("a dropped placeholder", placeholders("across %1$d surfaces"),
                placeholders("auf %d Oberflachen"));
        assertNotEquals("a renumbered placeholder", placeholders("%1$s and %2$s"),
                placeholders("%2$s and %1$s"));
        assertNotEquals("an invented placeholder", placeholders("no placeholder here"),
                placeholders("keiner %1$s hier"));
        assertNotEquals("a dropped full stop", terminator("Hide the caption."),
                terminator("Beschreibung ausblenden"));
        assertNotEquals("a changed terminator", terminator("Really?"), terminator("Wirklich."));
    }

    /** Every placeholder in order, numbered or bare, so a change of either kind shows up. */
    private static java.util.List<String> placeholders(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        var match = java.util.regex.Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]").matcher(text);
        while (match.find()) found.add(match.group());
        return found;
    }

    /** The character a string ends a sentence with, or a space when it ends with none. */
    private static char terminator(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return ' ';
        char last = trimmed.charAt(trimmed.length() - 1);
        return ".!?:\u2026".indexOf(last) >= 0 ? last : ' ';
    }

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
        assertTrue("this text reaches the reader, so it belongs in the translation table. "
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
            + "|setContentDescription|setStateDescription|SettingsUi\\s*\\.\\s*text)\\s*\\(";

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
                // An empty literal says nothing, so there is nothing to translate. Eight of
                // these are the getTitle() == null ? "" : ... idiom on the hand built dialogs.
                boolean empty = literalEnd == at + 2;
                if (!empty && !throughTheTable(text, kind, at)) {
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
            // Text built at runtime from a placeholder, and text spanning lines, are assembled
            // from parts that are entries of their own. Exempting anything merely carrying a
            // digit or a slash let 63 of 619 strings through, including every message with a
            // value in it.
            boolean composed = text.contains("\n") || text.matches("(?s).*%\\d\\$.*");
            if (!composed && !isValueRatherThanProse(text) && !english.contains(text)) {
                missing.add(text);
            }
        }
        assertEquals("settings text without a translation entry: " + missing, 0, missing.size());
    }

    /**
     * A chosen value shown back to the reader rather than wording of ours. A resolution and a
     * speed are written the same way in every language, and the download folder is a real path
     * on the device. An entry mapping each of these to itself would only pad the table.
     *
     * <p>The SIM preset summary is the exception that is a real gap. It is built at runtime as
     * "country, carrier (mccmnc)", and the country reads differently in German and Indonesian.
     * Translating the preset list is its own task and is on the roadmap; matching it on the
     * carrier code it ends with keeps this check honest about what it lets past rather than
     * widening the rule until it disappears.
     */
    private static boolean isValueRatherThanProse(String text) {
        return text.matches("\\d+p")
                || text.matches("\\d+(\\.\\d+)?x")
                || text.matches("[A-Za-z]+(/[A-Za-z0-9_-]+)+")
                || text.matches(".*\\(\\d{5,6}\\)");
    }

    @Test
    public void theTableCarriesNothingTheScreensNoLongerSay() throws Exception {
        // A wording that drifted leaves its old entry behind, translated and unreachable, and
        // the next reader of the table cannot tell it from one that is still in use.
        Set<String> shown = new LinkedHashSet<>(collectEverything());
        shown.addAll(runtimeStringsInSource());

        List<String> orphaned = new ArrayList<>();
        for (String english : GERMAN.keySet()) {
            if (!shown.contains(english)) {
                orphaned.add(english);
            }
        }

        assertEquals("translation entries nothing shows any more, so remove them from the tsv "
                + "files and rerun scripts/gen-l10n.py: " + orphaned, 0, orphaned.size());
    }

    @Test
    public void everyStringHandedToL10nHasAnEntry() throws Exception {
        // Wrapping a string in L10n.t is what the toast and content-description check looks for,
        // and on its own it proves only the shape of the call. Without this, a new overlay label
        // wrapped correctly but never added to the tsv files passes every check and still
        // reaches the reader in English.
        java.io.File root = new java.io.File("src/main/java/app/morphe/extension/tiktok");
        if (!root.isDirectory()) root = new java.io.File(
                "extensions/tiktok/src/main/java/app/morphe/extension/tiktok");
        assertTrue("could not find the source tree", root.isDirectory());

        java.util.regex.Pattern call = java.util.regex.Pattern.compile("L10n\\s*\\.\\s*[tf]\\s*\\(");
        List<String> missing = new ArrayList<>();
        int found = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.walk(root.toPath())) {
            for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .collect(java.util.stream.Collectors.toList())) {
                String text = new String(java.nio.file.Files.readAllBytes(file),
                        java.nio.charset.StandardCharsets.UTF_8);
                byte[] kind = classify(text);
                java.util.regex.Matcher match = call.matcher(text);
                while (match.find()) {
                    if (kind[match.start()] != CODE) continue;
                    int close = closingBracket(text, kind, match.end() - 1);
                    if (close < 0) continue;
                    List<String> parts = literalsIn(text, kind, match.end(), close);
                    if (parts.isEmpty()) continue;
                    found++;

                    // One message split across lines is a single key; a choice between two
                    // messages is two keys. Both are written as several literals in one call,
                    // so either reading is accepted and only a part in neither is reported.
                    if (GERMAN.containsKey(String.join("", parts))) continue;
                    for (String part : parts) {
                        if (!GERMAN.containsKey(part)) {
                            missing.add(file.getFileName() + ": " + part);
                        }
                    }
                }
            }
        }

        assertTrue("the scan found no L10n calls to read", found > 50);
        assertEquals("strings handed to L10n with no entry in the tsv files: " + missing,
                0, missing.size());
    }

    /**
     * The string literals inside one call, in order. A message too long for one line is written
     * as several literals with a plus between them and is one key; a choice between two messages
     * is written the same way and is two keys. The caller decides which reading fits.
     */
    private static List<String> literalsIn(String text, byte[] kind, int from, int to) {
        List<String> parts = new ArrayList<>();
        for (int at = from; at < to; at++) {
            if (kind[at] != LITERAL || text.charAt(at) != '"') continue;
            int end = at + 1;
            while (end < to && kind[end] == LITERAL) end++;
            // The run covers both quotes, so the closing one is not part of the text.
            int contentEnd = end > at + 1 && text.charAt(end - 1) == '"' ? end - 1 : end;
            parts.add(unescape(text.substring(at + 1, contentEnd)));
            at = end - 1;
        }
        return parts;
    }

    private static String unescape(String literal) {
        StringBuilder plain = new StringBuilder();
        for (int at = 0; at < literal.length(); at++) {
            char c = literal.charAt(at);
            if (c != '\\' || at + 1 >= literal.length()) {
                plain.append(c);
                continue;
            }
            char next = literal.charAt(++at);
            switch (next) {
                case 'n': plain.append('\n'); break;
                case 't': plain.append('\t'); break;
                default: plain.append(next); break;
            }
        }
        return plain.toString();
    }

    /**
     * Every string the extension's own source says, from both trees that reach the table. A
     * message written as several literals with a plus between them counts as its parts and as
     * the whole, the way the compiler joins it.
     *
     * <p>L10nTranslations.java is skipped on purpose. It is generated from the tsv files and
     * repeats every key back as a literal, so including it left the orphan check unable to fail:
     * a key was "shown" because the generator had written it out again.
     */
    private static Set<String> runtimeStringsInSource() throws Exception {
        Set<String> literals = new LinkedHashSet<>();
        // Gradle runs the tests with the module directory as the working directory, so the paths
        // are tried from the repository root and from inside extensions/tiktok. Skipping a root
        // that resolves from neither would quietly shrink what counts as shown.
        for (String relative : new String[]{
                "extensions/tiktok/src/main/java",
                "extensions/shared/library/src/main/java"}) {
            java.io.File root = new java.io.File(relative);
            if (!root.isDirectory()) root = new java.io.File("../../" + relative);
            if (!root.isDirectory()) root = new java.io.File(relative.replaceFirst("^extensions/tiktok/", ""));
            assertTrue("could not find " + relative + " from "
                    + new java.io.File(".").getAbsolutePath(), root.isDirectory());
            try (java.util.stream.Stream<java.nio.file.Path> files =
                         java.nio.file.Files.walk(root.toPath())) {
                for (java.nio.file.Path file : files.filter(p -> p.toString().endsWith(".java"))
                        .collect(java.util.stream.Collectors.toList())) {
                    if (file.getFileName().toString().equals("L10nTranslations.java")) continue;
                    collectStrings(new String(java.nio.file.Files.readAllBytes(file),
                            java.nio.charset.StandardCharsets.UTF_8), literals);
                }
            }
        }
        assertTrue("the scan found no source to read", literals.size() > 100);
        return literals;
    }

    /** Adds each literal, and for a run joined by plus signs the joined message as well. */
    private static void collectStrings(String text, Set<String> into) {
        byte[] kind = classify(text);
        int at = 0;
        while (at < text.length()) {
            if (kind[at] != LITERAL || text.charAt(at) != '"') {
                at++;
                continue;
            }
            List<String> parts = new ArrayList<>();
            while (at < text.length() && kind[at] == LITERAL && text.charAt(at) == '"') {
                int end = at + 1;
                while (end < text.length() && kind[end] == LITERAL) end++;
                int contentEnd = end > at + 1 && text.charAt(end - 1) == '"' ? end - 1 : end;
                parts.add(unescape(text.substring(at + 1, contentEnd)));
                into.add(parts.get(parts.size() - 1));

                int probe = end;
                while (probe < text.length() && kind[probe] == CODE
                        && Character.isWhitespace(text.charAt(probe))) probe++;
                if (probe < text.length() && kind[probe] == CODE && text.charAt(probe) == '+') {
                    probe++;
                    while (probe < text.length() && kind[probe] == CODE
                            && Character.isWhitespace(text.charAt(probe))) probe++;
                    at = probe;
                    continue;
                }
                at = end;
                break;
            }
            if (parts.size() > 1) into.add(String.join("", parts));
        }
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
            // The wording the backup row actually uses. This list said "Undo last restore" long
            // after the row started saying "or reset" too, which kept a dead tsv row alive.
            strings.add(L10n.t(activity, "Undo last restore or reset"));
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
