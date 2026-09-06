/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.Looper;
import android.preference.Preference;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * The free-text fields used to take anything and fail somewhere else: a creator pattern that
 * would not compile was only reported at the next feed page, an operator code that was not
 * digits reached TikTok's own telephony code, and an app name with a typo saved quietly and
 * did nothing at the next download. Each one now says no while the dialog is still open.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InputCheckTest {
    @After public void reset() throws Exception {
        for (Field field : SettingsStatus.class.getDeclaredFields()) {
            if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.setBoolean(null, false);
            }
        }
        Settings.BLOCKED_CREATORS.save("");
        Settings.SIMSPOOF_MCCMNC.save(Settings.SIMSPOOF_MCCMNC.defaultValue);
        Settings.EXTERNAL_DOWNLOADER_PACKAGE.save("");
    }

    /** The field as the settings screen really builds it, not one made up for the test. */
    private interface WithField {
        void check(InputTextPreference field) throws Exception;
    }

    private void onScreen(String section, String key, WithField body) throws Exception {
        try (var owner = Robolectric.buildActivity(
                app.morphe.extension.tiktok.captions.CaptionToolsTest.CaptionActivity.class)
                .setup().visible()) {
            var activity = owner.get();
            Utils.setContext(activity);
            for (Field field : SettingsStatus.class.getDeclaredFields()) {
                if (field.getType() == boolean.class && Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.setBoolean(null, true);
                }
            }
            var fragment = new TikTokPreferenceFragment();
            Bundle arguments = new Bundle();
            arguments.putString("morphe_settings_section", section);
            fragment.setArguments(arguments);
            activity.getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment).commit();
            activity.getFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            Preference found = fragment.findPreference(key);
            assertNotNull(key + " is not on the " + section + " screen", found);
            assertTrue(key + " is a " + found.getClass().getSimpleName(),
                    found instanceof InputTextPreference);
            ShadowToast.reset();
            body.check((InputTextPreference) found);
        }
    }

    @Test public void aCreatorPatternThatWillNotCompileIsRefusedInTheDialog() throws Exception {
        onScreen("FEED_FILTER", "blocked_creators", field -> {
            assertFalse("an unclosed group should not save",
                    field.callChangeListener("/^news_/, /([bad/"));
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            String said = ShadowToast.getTextOfLatestToast();
            assertNotNull("nothing was said", said);
            assertTrue("the bad entry is named, said: " + said, said.contains("/([bad/"));

            // A pattern nobody would type, long enough to be somebody else's backup.
            StringBuilder huge = new StringBuilder("/");
            for (int index = 0; index < 60; index++) huge.append("(a+)+");
            huge.append("$/");
            assertFalse(field.callChangeListener(huge.toString()));

            // Plain handles and a pattern that compiles both save.
            assertTrue(field.callChangeListener("news_uk, @someone"));
            assertTrue(field.callChangeListener("/^news_/, /dropship/"));
            assertTrue(field.callChangeListener(""));
        });
    }

    @Test public void anOperatorCodeThatIsNotDigitsIsRefusedInTheDialog() throws Exception {
        onScreen("REGION", "simspoof_mccmnc", field -> {
            for (String nonsense : new String[]{"abc", "3102", "3102601", "31026a", ""}) {
                assertFalse("saved " + nonsense, field.callChangeListener(nonsense));
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            String said = ShadowToast.getTextOfLatestToast();
            assertNotNull("nothing was said", said);
            assertTrue("said: " + said, said.contains("310260"));

            assertTrue(field.callChangeListener("310260"));
            assertTrue(field.callChangeListener("23415"));
            // Whitespace either side is a typing accident, not a different code.
            assertTrue(field.callChangeListener(" 310260 "));
        });
    }

    @Test public void anAppNameWithATypoIsRefusedInTheDialog() throws Exception {
        onScreen("DOWNLOADS", "external_downloader_package", field -> {
            for (String typo : new String[]{"com dv adm", "adm", "com.", "/data/local/tmp",
                    "com.dv.adm --flag"}) {
                assertFalse("saved " + typo, field.callChangeListener(typo));
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull("nothing was said", ShadowToast.getTextOfLatestToast());

            assertTrue(field.callChangeListener("com.dv.adm"));
            assertTrue(field.callChangeListener("idm.internet.download.manager"));
            // Empty means the save stays in TikTok, which is the setting's own default.
            assertTrue(field.callChangeListener(""));
            assertTrue(field.callChangeListener("   "));
        });
    }

    @Test public void aCountryCodeThatIsNotOneIsRefusedInTheDialog() throws Exception {
        onScreen("REGION", "simspoof_iso", field -> {
            for (String nonsense : new String[]{"zz", "united kingdom", "u", "", "12"}) {
                assertFalse("saved " + nonsense, field.callChangeListener(nonsense));
            }
            assertTrue(field.callChangeListener("gb"));
            assertTrue(field.callChangeListener("JP"));
        });
    }

    @Test public void aFieldWithNoCheckStillSaves() throws Exception {
        onScreen("FEED_FILTER", "blocked_caption_words", field ->
                assertEquals(true, field.callChangeListener("anything at all, /([bad/")));
    }
}
