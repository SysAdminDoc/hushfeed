/*
 * Forked from:
 * https://github.com/ReVanced/revanced-patches/blob/377d4e15016296b45d809697f7f69bce74badd3a/extensions/tiktok/src/main/java/app/revanced/extension/tiktok/settings/preference/TikTokPreferenceFragment.java
 */

package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.Window;
import android.widget.ListView;

import androidx.annotation.NonNull;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.shared.settings.Setting;
import app.morphe.extension.shared.settings.preference.AbstractPreferenceFragment;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabFragment;
import app.morphe.extension.tiktok.featuregatelab.FeatureGateLabRuntime;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.share.ShareUrlSanitizer;
import app.morphe.extension.tiktok.settings.SettingsStatus;
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

@SuppressWarnings("deprecation")
public class TikTokPreferenceFragment extends AbstractPreferenceFragment {
    private static final int REQUEST_DOWNLOAD_PATH_FOLDER = 8841;
    private static final String ARG_SECTION = "morphe_settings_section";
    private static final String ARG_SEARCH = "morphe_settings_search";
    private static final String ARG_TARGET_KEY = "morphe_settings_target_key";
    private static TikTokPreferenceFragment activeFragment;
    /**
     * Which folder setting the picker was opened for, by key rather than by the preference
     * itself. The picker is a separate activity, so this one is routinely destroyed behind it
     * and the object that was waiting no longer belongs to the screen that comes back.
     */
    private static String pendingDownloadPathKey;
    private static final String PENDING_DOWNLOAD_PATH_STATE = "morphe_pending_download_path";
    private SettingsListAdapter styledAdapter;
    private PreferenceScreen searchScreen;
    private List<SearchResult> searchIndex;
    private final List<Preference> searchRows = new ArrayList<>();

    /**
     * Each section carries one sentence, used both as the subtitle on the home row and as the
     * caption at the top of its own page. There used to be two, and they disagreed: the
     * Downloads row promised quality and subtitles while its page said path and watermark.
     */
    private enum Section {
        FEED_FILTER("Feed filter", "Choose what reaches your feed"),
        FEED_NAVIGATION("Feed navigation", "Arrange your feed and bottom tabs"),
        INTERFACE("Interface", "Captions, gestures and on-screen controls"),
        COMMENTS("Comments and translation", "Filters, translation and copy options"),
        DOWNLOADS("Downloads", "Quality, files, subtitles and hand-off"),
        PLAYBACK("Playback", "Quality, speed and automatic advance"),
        INBOX("Inbox", "Choose which rows and controls appear"),
        SHARE("Share sheet", "People, shortcuts and sending controls"),
        REGION("Region settings", "Country and network preferences"),
        BEHAVIOR("App behavior", "Links, privacy and player tools"),
        DIAGNOSTICS("Diagnostics", "Backups and troubleshooting");

        final String title;
        final String description;

        Section(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    private static final class SearchResult {
        final Section section;
        final String key;
        final String title;
        final String summary;
        final String category;
        /** Folded once when the index is built: it never changes, and a query is typed a letter
         *  at a time over about 170 of these. */
        final String normalized;

        SearchResult(Section section, String key, String title, String summary, String category) {
            this.section = section;
            this.key = key;
            this.title = title;
            this.summary = summary;
            this.category = category;
            this.normalized = normalizeSearchText(title + " " + summary + " " + category);
        }

        String displaySummary() {
            if (summary.isEmpty()) {
                return category;
            }
            return category + " · " + summary;
        }
    }

    private static boolean isDarkModeEnabled(Context context) {
        final int currentNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void openDownloadPathFolderPicker(DownloadPathPreference preference) {
        if (activeFragment == null) {
            app.morphe.extension.shared.Utils.showToastLong(L10n.t(
                    "This phone has no folder picker. Type the folder into the row instead."));
            return;
        }

        pendingDownloadPathKey = preference.getKey();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activeFragment.startActivityForResult(intent, REQUEST_DOWNLOAD_PATH_FOLDER);
        } catch (ActivityNotFoundException exception) {
            pendingDownloadPathKey = null;
            app.morphe.extension.shared.Utils.showToastLong(L10n.t(
                    "This phone has no folder picker. Type the folder into the row instead."));
        }
    }

    @Override
    protected void syncSettingWithPreference(
            @NonNull Preference pref,
            @NonNull Setting setting,
            boolean applySettingToPreference
    ) {
        if (pref instanceof NumberInputPreference) {
            NumberInputPreference numberInputPreference = (NumberInputPreference) pref;
            if (applySettingToPreference) {
                numberInputPreference.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, numberInputPreference.getValue());
            }
        } else if (pref instanceof CreatorListPreference) {
            CreatorListPreference creatorListPreference = (CreatorListPreference) pref;
            if (applySettingToPreference) {
                creatorListPreference.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, creatorListPreference.getValue());
            }
        } else if (pref instanceof RangeValuePreference) {
            RangeValuePreference rangeValuePref = (RangeValuePreference) pref;
            if (applySettingToPreference) {
                rangeValuePref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, rangeValuePref.getValue());
            }
        } else if (pref instanceof DownloadPathPreference) {
            DownloadPathPreference downloadPathPref = (DownloadPathPreference) pref;
            if (applySettingToPreference) {
                downloadPathPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, downloadPathPref.getValue());
            }
        } else if (pref instanceof TabSelectionPreference) {
            TabSelectionPreference tabSelectionPref = (TabSelectionPreference) pref;
            if (applySettingToPreference) {
                tabSelectionPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, tabSelectionPref.getValue());
            }
        } else {
            super.syncSettingWithPreference(pref, setting, applySettingToPreference);
        }
    }

    @Override
    protected boolean prefIsSetToDefault(Preference pref, Setting<?> setting) {
        String defaultValue = setting.defaultValue.toString();
        if (pref instanceof NumberInputPreference) {
            return defaultValue.equals(((NumberInputPreference) pref).getValue());
        }
        if (pref instanceof CreatorListPreference) {
            return defaultValue.equals(((CreatorListPreference) pref).getValue());
        }
        if (pref instanceof RangeValuePreference) {
            return defaultValue.equals(((RangeValuePreference) pref).getValue());
        }
        if (pref instanceof DownloadPathPreference) {
            return defaultValue.equals(((DownloadPathPreference) pref).getValue());
        }
        if (pref instanceof TabSelectionPreference) {
            return defaultValue.equals(((TabSelectionPreference) pref).getValue());
        }
        return super.prefIsSetToDefault(pref, setting);
    }

    @Override
    protected void initialize() {
        final var context = getActivity();
        activeFragment = this;

        // Nothing sets one. The framework only raises that dialog for a setting carrying a
        // userDialogMessage, and no setting in this bundle has one, so this was a translated
        // string for a screen nobody could reach. The safety net Reset and Undo do want is its
        // own item on the roadmap.
        confirmDialogTitle = null;

        Utils.setIsDarkModeEnabled(isDarkModeEnabled(context));

        PreferenceScreen preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);

        Section section = getRequestedSection();
        if (isSearchRequested()) {
            createSearchMenu(context, preferenceScreen);
        } else if (section == null) {
            createMasterMenu(context, preferenceScreen);
        } else {
            createSectionMenu(context, preferenceScreen, section);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ListView list = view.findViewById(android.R.id.list);
        if (list != null) {
            list.setBackgroundColor(SettingsUi.background());
            list.setCacheColorHint(SettingsUi.background());
            list.setDivider(null);
            list.setDividerHeight(0);
            list.setPadding(SettingsUi.dp(getActivity(), 16), 0, SettingsUi.dp(getActivity(), 16), SettingsUi.dp(getActivity(), 24));
            list.setClipToPadding(false);
            list.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        }

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Window window = activity.getWindow();
        // setStatusBarColor and setNavigationBarColor were here. Both do nothing from target 35,
        // which TikTok is well past, so the bars are painted by the root view's own background
        // reaching behind them instead. The icon flags below still work.
        view.setBackgroundColor(SettingsUi.background());

        View decor = window.getDecorView();
        int visibility = decor.getSystemUiVisibility();
        if (SettingsUi.isDarkMode()) {
            visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            visibility |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                visibility |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(visibility);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(PENDING_DOWNLOAD_PATH_STATE, pendingDownloadPathKey);
    }

    @Override public void onActivityCreated(Bundle state) {
        super.onActivityCreated(state);
        if (pendingDownloadPathKey == null && state != null) {
            pendingDownloadPathKey = state.getString(PENDING_DOWNLOAD_PATH_STATE);
        }
        ListView list = getView().findViewById(android.R.id.list);
        if (list != null && list.getAdapter() != null) {
            styledAdapter = new SettingsListAdapter(list.getAdapter());
            list.setAdapter(styledAdapter);
            String targetKey = getTargetKey();
            if (targetKey != null) {
                scrollToPreference(list, targetKey);
                list.post(() -> scrollToPreference(list, targetKey));
            }
        }
    }

    @Override public void onDestroyView() {
        if (styledAdapter != null) {
            styledAdapter.dispose();
            styledAdapter = null;
        }
        super.onDestroyView();
    }

    private Section getRequestedSection() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return null;
        }
        String name = arguments.getString(ARG_SECTION);
        if (name == null) {
            return null;
        }
        try {
            return Section.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isSearchRequested() {
        Bundle arguments = getArguments();
        return arguments != null && arguments.getBoolean(ARG_SEARCH, false);
    }

    private String getTargetKey() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return null;
        }
        String key = arguments.getString(ARG_TARGET_KEY);
        return key == null || key.isEmpty() ? null : key;
    }

    private void createSearchMenu(Context context, PreferenceScreen screen) {
        searchScreen = screen;
        screen.addPreference(SettingsHeaderPreference.section(context, "Search settings", this::navigateBack));
        screen.addPreference(SettingsHeaderPreference.caption(context,
                "Search translated titles and descriptions, then open the original setting."));
        screen.addPreference(new SettingsSearchInputPreference(context, this::updateSearchResults));
        searchIndex = buildSearchIndex(context);
        updateSearchResults("");
    }

    private void updateSearchResults(String query) {
        if (searchScreen == null) {
            return;
        }
        for (Preference row : searchRows) {
            searchScreen.removePreference(row);
        }
        searchRows.clear();

        // Folding happens before the empty check: a query of nothing but accent marks is not
        // empty as typed but folds away to nothing, and every setting contains "".
        String normalizedQuery = normalizeSearchText(query == null ? "" : query.trim());
        if (normalizedQuery.isEmpty()) {
            addSearchState("Type to search settings", "Search a title, description or category.");
            return;
        }

        List<SearchResult> matches = new ArrayList<>();
        for (SearchResult result : searchIndex) {
            if (result.normalized.contains(normalizedQuery)) {
                matches.add(result);
            }
        }
        if (matches.isEmpty()) {
            addSearchState("No matching settings", "Try a different word or clear the search.");
            return;
        }

        int order = 0;
        for (SearchResult result : matches) {
            Preference row = new Preference(getActivity());
            row.setTitle(result.title);
            row.setSummary(result.displaySummary());
            row.setOrder(order++);
            row.setOnPreferenceClickListener(preference -> {
                openSection(result.section, result.key);
                return true;
            });
            searchRows.add(row);
            searchScreen.addPreference(row);
        }
    }

    private void addSearchState(String title, String summary) {
        Preference state = new Preference(getActivity());
        state.setTitle(L10n.t(getActivity(), title));
        state.setSummary(L10n.t(getActivity(), summary));
        state.setSelectable(false);
        state.setOrder(0);
        searchRows.add(state);
        searchScreen.addPreference(state);
    }

    /**
     * How many settings on one section are away from their default.
     *
     * <p>Each badge used to read a list of settings kept by hand beside the page it described.
     * The Feed filter list named 17 of the 34 settings that page binds, so nine of its switches
     * moved nothing, and every list counted a switch that is on by default as "on", which is
     * why a stock install showed "Comments and translation, 3 on". Building the section is what
     * the search index already does two methods down, so the count and the page can no longer
     * disagree about what is on the page.
     */
    private int countChangedSettings(Context context, Section section) {
        int count = 0;
        // A plain loop: Collection.stream arrived in API 24 and the payload runs from 23.
        for (Setting<?> setting : settingsOn(context, section)) {
            // A setting this build cannot reach is not on the page, so it is not something the
            // reader has turned on.
            if (setting.isAvailable()
                    && !java.util.Objects.equals(setting.get(), setting.defaultValue)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The settings one section binds, worked out once and remembered.
     *
     * <p>Building the page is how this knows what is on it, and building eleven pages on every
     * return to the master menu is 184 preferences of work for a number. The pages do not gain
     * or lose rows while the screen is open: what changes is the values, and those are read
     * again every time.
     */
    private List<Setting<?>> settingsOn(Context context, Section section) {
        List<Setting<?>> known = sectionSettings.get(section);
        if (known != null) {
            return known;
        }
        List<Setting<?>> found = new ArrayList<>();
        PreferenceScreen scratch = getPreferenceManager().createPreferenceScreen(context);
        PreferenceCategory category = createCategory(context, scratch, section);
        if (category != null) {
            collectSettings(category, found, new java.util.HashSet<>());
            scratch.removePreference(category);
        }
        sectionSettings.put(section, found);
        return found;
    }

    private static void collectSettings(PreferenceGroup group, List<Setting<?>> found,
            java.util.Set<String> seen) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (preference instanceof PreferenceGroup) {
                collectSettings((PreferenceGroup) preference, found, seen);
                continue;
            }
            if (!preference.hasKey()) {
                continue;
            }
            Setting<?> setting = Setting.getSettingFromPath(preference.getKey());
            // A row with no setting behind it is an action. Two rows can share one setting, and
            // a page can bind the same row twice.
            if (setting == null || !seen.add(setting.key)) {
                continue;
            }
            found.add(setting);
        }
    }

    /**
     * Whether App behavior has anything in it. Every row on that page belongs to a patch, so
     * with none of them installed the row would open a page with nothing but its heading.
     */
    private static boolean hasBehaviorSettings() {
        return SettingsStatus.foldableSplitViewEnabled
                || SettingsStatus.sanitizeShareUrlsEnabled
                || SettingsStatus.externalBrowserEnabled
                || SettingsStatus.showSeekbarEnabled
                || SettingsStatus.seekbarThumbnailEnabled
                || SettingsStatus.stopVideoLoopingEnabled
                || SettingsStatus.resumeVideoAfterScrollEnabled
                || SettingsStatus.longPressSpeedLockEnabled
                || SettingsStatus.disableLongPressQuickShareEnabled
                || SettingsStatus.disableLongPressRepostEnabled
                || SettingsStatus.disableTelemetryEnabled
                || SettingsStatus.ghostModeEnabled
                || SettingsStatus.blockAuthorEnabled
                || SettingsStatus.notInterestedEnabled
                || SettingsStatus.nonPersonalizedSearchEnabled
                || SettingsStatus.liveSearchEnabled
                || SettingsStatus.duetStitchEnabled
                || SettingsStatus.refreshRateEnabled;
    }

    private List<SearchResult> buildSearchIndex(Context context) {
        List<SearchResult> results = new ArrayList<>();
        PreferenceScreen scratch = getPreferenceManager().createPreferenceScreen(context);
        for (Section section : Section.values()) {
            PreferenceCategory category = createCategory(context, scratch, section);
            if (category == null) {
                continue;
            }
            String categoryTitle = String.valueOf(category.getTitle());
            indexRows(results, category, section, categoryTitle);

            // Back up, Restore, Reset and Undo are added straight to the section screen rather
            // than into the category, so walking the category alone never saw them: searching
            // "backup" or "restore" answered "No matching settings" for four rows one page away.
            if (section == Section.DIAGNOSTICS) {
                PreferenceScreen backupRows = getPreferenceManager().createPreferenceScreen(context);
                SettingsBackupPreference.addTo(this, backupRows);
                indexRows(results, backupRows, section, categoryTitle);
            }
            scratch.removePreference(category);
        }
        return results;
    }

    private void indexRows(List<SearchResult> results, PreferenceGroup group, Section section,
            String categoryTitle) {
        for (int index = 0; index < group.getPreferenceCount(); index++) {
            Preference preference = group.getPreference(index);
            if (!preference.hasKey() || !preference.isSelectable()) {
                continue;
            }
            Setting<?> setting = Setting.getSettingFromPath(preference.getKey());
            if (setting != null && !setting.isAvailable()) {
                continue;
            }
            CharSequence title = preference.getTitle();
            if (title == null || title.length() == 0) {
                continue;
            }
            CharSequence summary = preference.getSummary();
            results.add(new SearchResult(
                    section,
                    preference.getKey(),
                    title.toString(),
                    summary == null ? "" : summary.toString(),
                    categoryTitle
            ));
        }
    }

    private static String normalizeSearchText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static PreferenceCategory createCategory(
            Context context,
            PreferenceScreen screen,
            Section section
    ) {
        switch (section) {
            case FEED_FILTER:
                return new FeedFilterPreferenceCategory(context, screen);
            case FEED_NAVIGATION:
                return new FeedNavigationPreferenceCategory(context, screen);
            case INTERFACE:
                return new InterfacePreferenceCategory(context, screen);
            case COMMENTS:
                return new CommentsPreferenceCategory(context, screen);
            case DOWNLOADS:
                return new DownloadsPreferenceCategory(context, screen);
            case PLAYBACK:
                return new PlaybackPreferenceCategory(context, screen);
            case INBOX:
                return new InboxPreferenceCategory(context, screen);
            case SHARE:
                return new SharePreferenceCategory(context, screen);
            case REGION:
                return new SimSpoofPreferenceCategory(context, screen);
            case DIAGNOSTICS:
                return new DebugPreferenceCategory(context, screen);
            case BEHAVIOR:
            default:
                return new ExtensionPreferenceCategory(context, screen);
        }
    }

    private static void scrollToPreference(ListView list, String key) {
        if (list == null || list.getAdapter() == null) {
            return;
        }
        for (int position = 0; position < list.getAdapter().getCount(); position++) {
            Object item = list.getAdapter().getItem(position);
            if (item instanceof Preference && key.equals(((Preference) item).getKey())) {
                list.setSelection(position);
                return;
            }
        }
    }

    private void createMasterMenu(Context context, PreferenceScreen screen) {
        screen.addPreference(SettingsHeaderPreference.master(context, this::closeSettings));
        screen.addPreference(new SettingsMenuPreference(
                context,
                "Search settings",
                "Find a setting by title or description",
                SettingsMenuPreference.Icon.SEARCH,
                0,
                preference -> {
                    openSearch();
                    return true;
                }
        ));

        if (FeedFilterPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.FEED_FILTER, SettingsMenuPreference.Icon.FILTER);
        }
        if (FeedNavigationPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.FEED_NAVIGATION, SettingsMenuPreference.Icon.TABS);
        }
        if (InterfacePreferenceCategory.isAvailable()) {
            addMenu(screen, Section.INTERFACE, SettingsMenuPreference.Icon.LAYOUT);
        }
        if (CommentsPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.COMMENTS, SettingsMenuPreference.Icon.COMMENTS);
        }
        if (DownloadsPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.DOWNLOADS, SettingsMenuPreference.Icon.DOWNLOADS);
        }
        if (PlaybackPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.PLAYBACK, SettingsMenuPreference.Icon.PLAYBACK);
        }
        if (InboxPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.INBOX, SettingsMenuPreference.Icon.INBOX);
        }
        if (SharePreferenceCategory.isAvailable()) {
            addMenu(screen, Section.SHARE, SettingsMenuPreference.Icon.SHARE);
        }
        if (SimSpoofPreferenceCategory.isAvailable()) {
            addMenu(screen, Section.REGION, SettingsMenuPreference.Icon.REGION);
        }

        // App behavior and Diagnostics keep their own conditions on purpose. The App
        // behavior page is the fallback every unmatched section falls to, so it is always
        // buildable and only the row is conditional; the Diagnostics row always shows,
        // because settings backup and restore live on that page whether or not the
        // diagnostics patch is in the bundle.
        if (hasBehaviorSettings()) {
            addMenu(screen, Section.BEHAVIOR, SettingsMenuPreference.Icon.BEHAVIOR);
        }

        if (FeatureGateLabRuntime.isInstalled()) {
            screen.addPreference(new SettingsMenuPreference(
                    context,
                    "Feature Gate Lab",
                    "Search and override gate flags",
                    SettingsMenuPreference.Icon.LAB,
                    0,
                    preference -> {
                        FeatureGateLabFragment.open(getActivity());
                        return true;
                    }
            ));
        }

        if (SettingsStatus.featureGateRecorderEnabled) {
            screen.addPreference(new FeatureGateRecorderPreference(context));
        }

        addMenu(screen, Section.DIAGNOSTICS, SettingsMenuPreference.Icon.DIAGNOSTICS);

        screen.addPreference(new MorpheTikTokAboutPreference(context));
    }

    /** The master menu's rows and the section each one opens, for the badge refresh. */
    private final java.util.Map<SettingsMenuPreference, Section> menuSections =
            new java.util.LinkedHashMap<>();

    /** What each section binds, so counting a badge does not build the page again. */
    private final java.util.Map<Section, List<Setting<?>>> sectionSettings =
            new java.util.EnumMap<>(Section.class);

    private void addMenu(
            PreferenceScreen screen,
            Section section,
            SettingsMenuPreference.Icon icon
    ) {
        String description = L10n.t(getActivity(), section.description);
        if (description.endsWith(".")) {
            description = description.substring(0, description.length() - 1);
        }
        SettingsMenuPreference row = new SettingsMenuPreference(
                getActivity(),
                section.title,
                description,
                icon,
                countChangedSettings(getActivity(), section),
                preference -> {
                    openSection(section);
                    return true;
                }
        );
        screen.addPreference(row);
        menuSections.put(row, section);
    }

    private void createSectionMenu(Context context, PreferenceScreen screen, Section section) {
        screen.addPreference(SettingsHeaderPreference.section(context, section.title, this::navigateBack));
        screen.addPreference(SettingsHeaderPreference.caption(context, section.description));

        PreferenceCategory category = createCategory(context, screen, section);
        flattenCategory(screen, category);
        if (section == Section.DIAGNOSTICS) SettingsBackupPreference.addTo(this, screen);
    }

    void refreshBackupSettings() { updateUIToSettingValues(); }

    private static void flattenCategory(PreferenceScreen screen, PreferenceCategory category) {
        if (category == null) {
            return;
        }
        int count = category.getPreferenceCount();
        Preference[] children = new Preference[count];
        for (int index = 0; index < count; index++) {
            children[index] = category.getPreference(index);
        }
        for (Preference child : children) {
            category.removePreference(child);
        }
        screen.removePreference(category);
        for (int index = 0; index < children.length; index++) {
            Preference child = children[index];
            child.setOrder(index);
            screen.addPreference(child);
        }
    }

    private void openSearch() {
        FragmentManager manager = getFragmentManager();
        if (manager == null || getId() == 0) {
            Utils.showToastShort(L10n.t("Could not open settings search"));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_SEARCH, true);
        fragment.setArguments(arguments);
        manager.beginTransaction()
                .setCustomAnimations(
                        android.R.animator.fade_in,
                        android.R.animator.fade_out,
                        android.R.animator.fade_in,
                        android.R.animator.fade_out
                )
                .replace(getId(), fragment)
                .addToBackStack("search")
                .commit();
    }

    private void openSection(Section section) {
        openSection(section, null);
    }

    private void openSection(Section section, String targetKey) {
        FragmentManager manager = getFragmentManager();
        if (manager == null || getId() == 0) {
            Utils.showToastShort(L10n.t("Could not open settings section"));
            return;
        }

        TikTokPreferenceFragment fragment = new TikTokPreferenceFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_SECTION, section.name());
        if (targetKey != null) {
            arguments.putString(ARG_TARGET_KEY, targetKey);
        }
        fragment.setArguments(arguments);

        manager.beginTransaction()
                .setCustomAnimations(
                        android.R.animator.fade_in,
                        android.R.animator.fade_out,
                        android.R.animator.fade_in,
                        android.R.animator.fade_out
                )
                .replace(getId(), fragment)
                .addToBackStack(section.name())
                .commit();
    }

    private void navigateBack() {
        FragmentManager manager = getFragmentManager();
        if (manager != null) {
            manager.popBackStack();
        }
    }

    private void closeSettings() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        activeFragment = this;
        refreshMenuBadges();
    }

    /**
     * Brings every badge on the master menu up to date.
     *
     * <p>openSection replaces this fragment and puts it on the back stack, so the master screen
     * and every one of its rows survives a trip into a section and back. Nothing rebuilt them on
     * the way back, which is why the numbers were the ones from whenever the screen was first
     * opened: turning on three filters and pressing back left the old count in place.
     */
    private void refreshMenuBadges() {
        Context context = getActivity();
        PreferenceScreen screen = getPreferenceScreen();
        if (context == null || screen == null || menuSections.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<SettingsMenuPreference, Section> entry : menuSections.entrySet()) {
            entry.getKey().setActiveCount(countChangedSettings(context, entry.getValue()));
        }
    }

    @Override
    public void onDestroy() {
        if (activeFragment == this) {
            activeFragment = null;
            // The pending key deliberately survives: the picker destroys this fragment while it
            // is open, and clearing it here dropped the folder the reader had just chosen.
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (SettingsBackupPreference.onResult(this, requestCode, resultCode, data)) return;
        if (requestCode != REQUEST_DOWNLOAD_PATH_FOLDER) {
            return;
        }

        String pendingKey = pendingDownloadPathKey;
        pendingDownloadPathKey = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Preference found = pendingKey == null ? null : findPreference(pendingKey);
        if (!(found instanceof DownloadPathPreference)) {
            app.morphe.extension.shared.Utils.showToastLong(
                    L10n.t("Could not tell which folder to update. Choose it again."));
            return;
        }
        DownloadPathPreference preference = (DownloadPathPreference) found;

        String relativePath = getRelativePrimaryStoragePath(data.getData());
        if (relativePath == null) {
            app.morphe.extension.shared.Utils.showToastLong(L10n.t("Only internal storage folders are supported"));
            return;
        }

        preference.applyPickedPath(relativePath);
    }

    private static String getRelativePrimaryStoragePath(Uri uri) {
        try {
            String treeDocumentId = DocumentsContract.getTreeDocumentId(uri);
            if (treeDocumentId == null) {
                return null;
            }

            String prefix = "primary:";
            if (!treeDocumentId.startsWith(prefix)) {
                return null;
            }

            return treeDocumentId.substring(prefix.length());
        } catch (Exception ignored) {
            return null;
        }
    }
}
