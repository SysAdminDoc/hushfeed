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

import app.morphe.extension.tiktok.SettingsContextRule;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;

import org.junit.Rule;
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
    @Rule public final SettingsContextRule settingsContext = new SettingsContextRule();
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
        Settings.MIN_MAX_VIEWS.resetToDefault();
        Settings.DOWNLOAD_VIDEO_PATH.resetToDefault();
    }

    /** The field as the settings screen really builds it, not one made up for the test. */
    private interface WithField {
        void check(InputTextPreference field) throws Exception;
    }


    /**
     * Refusing a value used to close the dialog first and say why afterwards, over whatever
     * screen the reader was left looking at, with the box emptied. Saying no is only useful
     * next to the thing being said no about.
     */
    @Test public void aRefusedPatternLeavesTheDialogOpenWithTheReasonUnderTheField()
            throws Exception {
        onScreen("FEED_FILTER", "blocked_creators", field -> {
            openDialog(field);
            android.app.AlertDialog dialog = (android.app.AlertDialog) field.getDialog();
            assertNotNull("the dialog did not open", dialog);
            field.getEditText().setText("/^news_/, /([bad/");

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertTrue("the dialog closed on a value it refused", dialog.isShowing());
            assertNotNull("nothing was said under the field",
                    field.getEditText().getError());
            assertEquals("what was typed was thrown away",
                    "/^news_/, /([bad/", field.getEditText().getText().toString());
            assertEquals("a refused pattern was saved anyway", "",
                    Settings.BLOCKED_CREATORS.get());

            // And a value it will take closes it, which is the half that would still pass if
            // the button simply stopped working.
            field.getEditText().setText("news_uk, @someone");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("the dialog stayed open on a value it took", dialog.isShowing());
            assertEquals("news_uk, @someone", Settings.BLOCKED_CREATORS.get());
        });
    }

    /** The same for the two-box rows, where retyping meant retyping both numbers. */
    @Test public void aBackwardsRangeLeavesTheDialogOpenWithBothNumbersStillThere()
            throws Exception {
        onRangeScreen("min_max_views", field -> {
            openDialog(field);
            android.app.AlertDialog dialog = (android.app.AlertDialog) field.getDialog();
            assertNotNull("the dialog did not open", dialog);
            android.widget.EditText[] boxes = rangeBoxes(dialog);
            boxes[0].setText("900");
            boxes[1].setText("100");

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertTrue("the dialog closed on a range it refused", dialog.isShowing());
            assertNotNull("nothing was said under the first box", boxes[0].getError());
            assertEquals("the smallest was thrown away", "900", boxes[0].getText().toString());
            assertEquals("the largest was thrown away", "100", boxes[1].getText().toString());

            boxes[1].setText("9000");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("the dialog stayed open on a range it took", dialog.isShowing());
            assertEquals("900-9000", Settings.MIN_MAX_VIEWS.get());
        });
    }

    /**
     * The download folder is a fourth row of the same shape, found by the refutation pass over
     * the first three: it validated the path after the dialog had gone and then said what was
     * wrong over whatever screen was behind it.
     */
    @Test public void ARefusedDownloadFolderLeavesTheDialogOpenWithTheReasonUnderTheField()
            throws Exception {
        onScreenRaw("DOWNLOADS", "download_video_path", found -> {
            assertTrue("download_video_path is a " + found.getClass().getSimpleName(),
                    found instanceof DownloadPathPreference);
            String before = Settings.DOWNLOAD_VIDEO_PATH.get();
            openDialog(found);
            android.app.AlertDialog dialog =
                    (android.app.AlertDialog) ((android.preference.DialogPreference) found)
                            .getDialog();
            assertNotNull("the dialog did not open", dialog);
            android.widget.EditText box = pathBox(dialog);
            box.setText("/data/local/tmp/anywhere");

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertTrue("the dialog closed on a folder it refused", dialog.isShowing());
            assertNotNull("nothing was said under the field", box.getError());
            assertEquals("what was typed was thrown away",
                    "/data/local/tmp/anywhere", box.getText().toString());
            assertEquals("a refused folder was saved anyway", before,
                    Settings.DOWNLOAD_VIDEO_PATH.get());

            box.setText("DCIM/Hushfeed");
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertFalse("the dialog stayed open on a folder it took", dialog.isShowing());
            assertEquals("DCIM/Hushfeed", Settings.DOWNLOAD_VIDEO_PATH.get());
        });
    }

    private static android.widget.EditText pathBox(android.app.AlertDialog dialog) {
        java.util.List<android.widget.EditText> found = new java.util.ArrayList<>();
        collectEditTexts(dialog.getWindow().getDecorView(), found);
        assertEquals("the folder dialog does not have one box", 1, found.size());
        return found.get(0);
    }

    private interface WithRange {
        void check(RangeValuePreference field) throws Exception;
    }

    private void onRangeScreen(String key, WithRange body) throws Exception {
        onScreenRaw("FEED_FILTER", key, found -> {
            assertTrue(key + " is a " + found.getClass().getSimpleName(),
                    found instanceof RangeValuePreference);
            body.check((RangeValuePreference) found);
        });
    }

    /** The two boxes in the order the dialog shows them, smallest first. */
    private static android.widget.EditText[] rangeBoxes(android.app.AlertDialog dialog) {
        java.util.List<android.widget.EditText> found = new java.util.ArrayList<>();
        collectEditTexts(dialog.getWindow().getDecorView(), found);
        assertEquals("the range dialog does not have two boxes", 2, found.size());
        return new android.widget.EditText[]{found.get(0), found.get(1)};
    }

    private static void collectEditTexts(
            android.view.View view, java.util.List<android.widget.EditText> found) {
        if (view instanceof android.widget.EditText) {
            found.add((android.widget.EditText) view);
        } else if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                collectEditTexts(group.getChildAt(index), found);
            }
        }
    }

    /** DialogPreference.showDialog is protected, and the row is reached through the screen. */
    public static void openDialog(Preference row) throws Exception {
        java.lang.reflect.Method show = null;
        for (Class<?> type = row.getClass(); type != null; type = type.getSuperclass()) {
            try {
                show = type.getDeclaredMethod("showDialog", Bundle.class);
                break;
            } catch (NoSuchMethodException keepLooking) {
                // The override lives further up.
            }
        }
        assertNotNull("this row has no dialog", show);
        show.setAccessible(true);
        show.invoke(row, (Bundle) null);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private interface WithPreference {
        void check(Preference found) throws Exception;
    }

    private void onScreenRaw(String section, String key, WithPreference body) throws Exception {
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
            ShadowToast.reset();
            body.check(found);
        }
    }

    private void onScreen(String section, String key, WithField body) throws Exception {
        onScreenRaw(section, key, found -> {
            assertTrue(key + " is a " + found.getClass().getSimpleName(),
                    found instanceof InputTextPreference);
            body.check((InputTextPreference) found);
        });
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
