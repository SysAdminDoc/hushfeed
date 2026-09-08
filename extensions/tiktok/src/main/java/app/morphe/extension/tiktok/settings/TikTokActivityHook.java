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
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;
import app.morphe.extension.tiktok.settings.preference.TikTokPreferenceFragment;

import com.bytedance.ies.ugc.aweme.commercialize.compliance.personalization.AdPersonalizationActivity;


/**
 * Hooks AdPersonalizationActivity to inject a custom {@link TikTokPreferenceFragment}.
 */
@SuppressWarnings({"deprecation", "NewApi", "unused"})
public class TikTokActivityHook {
    private static final String SETTINGS_ACTION = "morphe_settings";
    private static final String SETTINGS_EXTRA = "morphe";
    private static final String SETTINGS_SECTION_EXTRA = "morphe_settings_section";

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
        // The background sits here rather than only on the fragment, so it reaches behind the
        // bars. fitsSystemWindows would pad this view too, but it consumes the insets on the way
        // past, and it has no answer for a display cutout on a side edge.
        linearLayout.setBackgroundColor(SettingsUi.background());
        SystemBarInsets.applyTo(linearLayout);
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

        // Back here rode entirely on the host's onBackPressed, which stops being called once
        // TikTok drops enableOnBackInvokedCallback="false" from its manifest at target 36. The
        // Lab has registered a dispatcher callback since API 33; this screen had not, so it
        // would have lost Back the day that attribute goes. The hook below stays as the path
        // for everything older.
        new SystemBackHandler("HushfeedSettingsBackCallback")
                .registerUntilDetached(base, linearLayout, () -> handleBackPressed(base));

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
