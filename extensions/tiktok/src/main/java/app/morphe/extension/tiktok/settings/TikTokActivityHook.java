/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/TikTokActivityHook.java
 */

package app.morphe.extension.tiktok.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.diagnostics.HookStatus;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;

import com.bytedance.ies.ugc.aweme.commercialize.compliance.personalization.AdPersonalizationActivity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Hooks AdPersonalizationActivity to inject a custom {@link TikTokPreferenceFragment}.
 */
@SuppressWarnings({"deprecation", "NewApi", "unused"})
public class TikTokActivityHook {
    private static final String SETTINGS_ACTION = "morphe_settings";
    private static final String SETTINGS_EXTRA = "morphe";
    private static final String SETTINGS_SECTION_EXTRA = "morphe_settings_section";

    /** Said once per process, because this runs every time the settings screen is opened. */
    private static boolean saidTheRowIsMissing;

    /**
     * Builds the row that opens Hushfeed's settings, or answers null when the host has renamed
     * the classes it is made of.
     *
     * <p>This was the only uncaught reflection in the tree. TikTok renames these classes on
     * every build, so a miss here threw out of the host's own settings screen and took the whole
     * page down with it. The row is the only thing that should go missing. The injected code
     * checks for null and branches past the add.
     */
    public static Object createSettingsEntry(String entryClazzName, String entryInfoClazzName) {
        try {
            Class entryClazz = Class.forName(entryClazzName);
            Class entryInfoClazz = Class.forName(entryInfoClazzName);
            Constructor entryConstructor = entryClazz.getConstructor(entryInfoClazz);
            Constructor entryInfoConstructor = entryInfoClazz.getDeclaredConstructors()[0];
            Object buttonInfo = entryInfoConstructor.newInstance(
                    "Hushfeed", null, (View.OnClickListener) view -> startSettingsActivity(), "morphe");
            return entryConstructor.newInstance(buttonInfo);
        } catch (Exception missing) {
            // Info rather than exception: this is a rename, not a fault, and printException
            // raises a toast of its own when the debug setting is on, which would say the same
            // thing twice. An Error is left to propagate.
            Logger.printInfo(() -> "Could not build the Hushfeed settings row", missing);
            HookStatus.missingMember("settings", "class", entryClazzName, "<init>");
            if (!saidTheRowIsMissing) {
                saidTheRowIsMissing = true;
                Utils.showToastLong(L10n.t("Hushfeed settings could not be added to this screen"));
            }
            return null;
        }
    }

    /***
     * Initialize the settings menu.
     * @param base The activity to initialize the settings menu on.
     * @return Whether the settings menu should be initialized.
     */
    public static boolean initialize(AdPersonalizationActivity base) {
        Intent intent = base.getIntent();
        Bundle extras = intent.getExtras();
        if ((extras == null || !extras.getBoolean(SETTINGS_EXTRA, false)) && !SETTINGS_ACTION.equals(intent.getAction())) {
            return false;
        }

        SettingsOperationJournal.initialize(base.getApplicationContext());
        SettingsOperationJournal.showRecoveryNotice(base);
        SettingsStatus.load();

        LinearLayout linearLayout = new LinearLayout(base);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setFitsSystemWindows(true);
        linearLayout.setTransitionGroup(true);

        FrameLayout fragment = new FrameLayout(base);
        fragment.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
        int fragmentId = View.generateViewId();
        fragment.setId(fragmentId);

        linearLayout.addView(fragment);
        base.setContentView(linearLayout);

        PreferenceFragment preferenceFragment = new TikTokPreferenceFragment();
        String section = intent.getStringExtra(SETTINGS_SECTION_EXTRA);
        if (section != null && !section.isEmpty()) {
            Bundle arguments = new Bundle();
            arguments.putString(SETTINGS_SECTION_EXTRA, section);
            preferenceFragment.setArguments(arguments);
        }
        base.getFragmentManager().beginTransaction().replace(fragmentId, preferenceFragment).commit();

        return true;
    }

    public static boolean handleBackPressed(AdPersonalizationActivity activity) {
        Intent intent = activity.getIntent();
        if (intent == null
                || (!SETTINGS_ACTION.equals(intent.getAction())
                && !intent.getBooleanExtra(SETTINGS_EXTRA, false))) {
            return false;
        }

        if (activity.getFragmentManager().getBackStackEntryCount() > 0) {
            activity.getFragmentManager().popBackStack();
        } else {
            activity.finish();
        }
        return true;
    }

    private static void startSettingsActivity() {
        startSettingsActivity(null);
    }

    /** Opens the extension settings directly at the feed-filter page. */
    public static void openFeedFilterSettings() {
        startSettingsActivity("FEED_FILTER");
    }

    private static void startSettingsActivity(String section) {
        Context appContext = Utils.getContext();
        if (appContext != null) {
            Intent intent = new Intent(appContext, AdPersonalizationActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(SETTINGS_ACTION);
            intent.putExtra(SETTINGS_EXTRA, true);
            if (section != null) {
                intent.putExtra(SETTINGS_SECTION_EXTRA, section);
            }
            appContext.startActivity(intent);
        } else {
            Logger.printDebug(() -> "Utils.getContext() return null");
        }
    }
}
