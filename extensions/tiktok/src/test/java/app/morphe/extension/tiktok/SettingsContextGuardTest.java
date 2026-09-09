package app.morphe.extension.tiktok;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every test class that touches the settings registry installs a context before its first case.
 *
 * <p>A class that does not poisons {@code BaseSettings} for the rest of the Robolectric sandbox,
 * and the failure lands on the classes that run after it rather than on the one at fault. That
 * makes it an ordering bug: it appears when a new test class changes the order Gradle scans in,
 * a long way from the change that caused it. On 2026-09-08 three added classes turned it into
 * 419 failures in one run.
 *
 * <p>A class passes this guard by declaring {@link SettingsContextRule} or by calling
 * {@code Utils.setContext} inside a method annotated {@code @Before}. A call in {@code @After}
 * does not count: the registry has already been read by then, which is exactly what
 * NumberInputPreferenceTest and ShareModelFilterTest each did.
 */
public class SettingsContextGuardTest {
    /** A read or a write of a setting: Settings.SOME_KEY, or a Setting built by hand. */
    private static final Pattern TOUCHES_SETTINGS =
            Pattern.compile("\\bSettings\\.[A-Z][A-Z0-9_]+|\\bnew\\s+\\w*Setting\\s*\\(");

    private static final Pattern DECLARES_RULE =
            Pattern.compile("@Rule[^;]*\\bnew\\s+SettingsContextRule\\s*\\(", Pattern.DOTALL);

    /**
     * A {@code @Before} method whose body calls {@code setContext}. The body is matched up to the
     * next annotation or the end of the file rather than by counting braces, which is enough to
     * tell it apart from a {@code setContext} that only appears under {@code @After}.
     */
    private static final Pattern BEFORE_INSTALLS_CONTEXT =
            Pattern.compile("@Before\\b(?:(?!@After|@Test|@AfterClass|@Rule).)*?setContext\\s*\\(",
                    Pattern.DOTALL);

    @Test
    public void everyTestClassThatTouchesASettingInstallsAContextFirst() throws IOException {
        List<Path> sources = testSources();
        assertTrue("no test sources were found to check", sources.size() > 50);

        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            // This class names the pattern it looks for, in a string, and is plain JUnit: it has
            // no Robolectric sandbox to take an application context from.
            if (source.getFileName().toString().equals("SettingsContextGuardTest.java")) continue;
            // Files.readString is not on the Android bootclasspath this module compiles
            // against, and neither is Stream.toList below.
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            if (!TOUCHES_SETTINGS.matcher(text).find()) continue;
            if (DECLARES_RULE.matcher(text).find()) continue;
            if (BEFORE_INSTALLS_CONTEXT.matcher(text).find()) continue;
            offenders.add(source.getFileName().toString());
        }

        if (!offenders.isEmpty()) {
            fail("These test classes read or write a Setting without installing a context before"
                    + " their first case, which poisons BaseSettings for every class that runs"
                    + " after them in the same sandbox. Add"
                    + " `@Rule public final SettingsContextRule settingsContext = new"
                    + " SettingsContextRule();` to each: " + String.join(", ", offenders));
        }
    }

    /** This class itself names a Setting pattern, so the guard has to see its own source. */
    @Test
    public void theGuardCanSeeTheSourcesItChecks() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path source : testSources()) names.add(source.getFileName().toString());

        assertTrue("the guard did not find its own source, so it is scanning the wrong tree",
                names.contains("SettingsContextGuardTest.java"));
        assertTrue("the guard did not find a class it is meant to hold",
                names.contains("ShareModelFilterTest.java"));
    }

    private static List<Path> testSources() throws IOException {
        File root = new File("src/test/java");
        if (!root.isDirectory()) root = new File("extensions/tiktok/src/test/java");
        assertTrue("the test source tree was not found from " + new File(".").getAbsolutePath(),
                root.isDirectory());

        try (Stream<Path> walk = Files.walk(root.toPath())) {
            return walk.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Keeps the offender pattern honest: it has to match the shapes it is written for. */
    @Test
    public void theSettingPatternMatchesBothShapes() {
        for (String line : List.of(
                "Settings.SHARE_HIDDEN_ITEMS.save(\"\");",
                "new IntegerSetting(\"unit_test\", 3)",
                "new BooleanSetting(\"x\", false)")) {
            Matcher matcher = TOUCHES_SETTINGS.matcher(line);
            assertTrue("the guard would not notice: " + line, matcher.find());
        }

        assertTrue("a lower case field should not read as a setting",
                !TOUCHES_SETTINGS.matcher("Settings.getSomething()").find());
    }
}
