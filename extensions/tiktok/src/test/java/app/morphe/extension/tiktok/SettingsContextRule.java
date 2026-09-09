package app.morphe.extension.tiktok;

import app.morphe.extension.shared.Utils;

import org.junit.rules.ExternalResource;
import org.robolectric.RuntimeEnvironment;

/**
 * Installs the application context before each test, and again after it.
 *
 * <p>{@code Setting}'s static initialiser reads {@link Utils#getContext()}. A test class that
 * touches the settings registry without one poisons {@code BaseSettings} for every later class
 * in the same Robolectric sandbox, and nothing in the failure says which class did it: the
 * classes that follow report {@code NoClassDefFoundError} and the class at fault passes. Whether
 * a given class got away with it therefore depended on an earlier class having set a context,
 * which is to say on the order Gradle happened to scan the tree in. Adding three test classes on
 * 2026-09-08 changed that order and 419 of 736 tests failed at once.
 *
 * <p>Declare it as a {@code @Rule} in any test class that reads or writes a {@code Setting}:
 *
 * <pre>{@code @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();}</pre>
 *
 * <p>An {@code @Before} method that calls {@code Utils.setContext} itself does the same job and
 * {@code SettingsContextGuardTest} accepts either. What neither of them is, is a call in
 * {@code @After}: by the time that runs the registry has already been touched.
 */
public final class SettingsContextRule extends ExternalResource {
    @Override
    protected void before() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    @Override
    protected void after() {
        // The sandbox is shared with whatever runs next, and a test is free to swap the context
        // for one of its own.
        Utils.setContext(RuntimeEnvironment.getApplication());
    }
}
