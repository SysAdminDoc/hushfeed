/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.featuregatelab;

import app.morphe.extension.tiktok.settings.L10n;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceFragment;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.SettingsJson;
import app.morphe.extension.tiktok.settings.SettingsOperationJournal;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

@SuppressWarnings({"deprecation", "SetTextI18n"})
public final class FeatureGateLabFragment extends Fragment {
    private static final String[] VIEW_LABELS = {"Loaded", "All actionable", "Overrides"};
    private static final String[] FILTER_LABELS = {"All", "Boolean", "Enabled", "Disabled", "Unloaded"};

    /**
     * The five filter choices in the reader's language.
     *
     * <p>The button and the dialog it opens show the same five words, so they are translated
     * from one place. Translating one without the other would read worse than leaving both.
     */
    private static String[] filterLabels(android.content.Context context) {
        String[] translated = new String[FILTER_LABELS.length];
        for (int index = 0; index < FILTER_LABELS.length; index++) {
            translated[index] = L10n.t(context, FILTER_LABELS[index]);
        }
        return translated;
    }
    private static final String[] SOURCE_LABELS = {
            "All", "App AB", "Config", "Player", "Live", "Media", "Activity"
    };
    private static final String[] SOURCE_MANAGERS = {
            null,
            FeatureGateLabStore.MANAGER_ABMOCK,
            FeatureGateLabStore.MANAGER_SETTINGS_MANAGER,
            FeatureGateLabStore.MANAGER_PLAYER_CONFIG,
            FeatureGateLabStore.MANAGER_LIVE,
            FeatureGateLabStore.MANAGER_VE_CONFIG,
            FeatureGateLabStore.MANAGER_PIA_ACTIVITY_CENTER
    };
    private static final long SEARCH_DELAY_MS = 160;
    private static final java.util.concurrent.atomic.AtomicBoolean CHANGING = new java.util.concurrent.atomic.AtomicBoolean();
    private static final ExecutorService FILE_IO_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MorpheGateFileIO");
        thread.setDaemon(true);
        return thread;
    });
    private static final int REQUEST_EXPORT_LOADED = 0x6f10;
    private static final int REQUEST_IMPORT_LOADED = 0x6f11;
    private static final int MAX_COMPRESSED_IMPORT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_JSON_IMPORT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_IMPORT_RULES = 1024;
    private static final SettingsJson.Limits IMPORT_JSON_LIMITS = new SettingsJson.Limits(
            24, 8192, 64 * 1024, MAX_IMPORT_RULES, MAX_JSON_IMPORT_BYTES);
    private static final int FILTER_ALL = 0;
    private static final int FILTER_BOOLEAN = 1;
    private static final int FILTER_ENABLED = 2;
    private static final int FILTER_DISABLED = 3;
    private static final int FILTER_UNLOADED = 4;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final app.morphe.extension.tiktok.settings.SystemBackHandler systemBack =
            new app.morphe.extension.tiktok.settings.SystemBackHandler("FeatureGateLabBackCallback");
    private final List<FeatureGateCatalog.Entry> visible = new ArrayList<>();
    /**
     * The gates a long press has gathered, by identity, in the order they were chosen.
     *
     * <p>Empty means the list is behaving as it always did: a tap opens the gate. Held by
     * identity rather than by index because the search and the filters rebuild {@link #visible}
     * underneath a selection.
     */
    private final java.util.LinkedHashMap<String, FeatureGateCatalog.Entry> selection =
            new java.util.LinkedHashMap<>();

    private FeatureGateCatalog.Snapshot snapshot;
    private GateAdapter adapter;
    private TextView count;
    private TextView loading;
    private TextView empty;
    private EditText search;
    private View clearSearch;
    private LinearLayout viewTabs;
    private final TextView[] viewTabLabels = new TextView[VIEW_LABELS.length];
    private final TextView[] sourceTabLabels = new TextView[SOURCE_LABELS.length];
    private final View[] sourceTabIndicators = new View[SOURCE_LABELS.length];
    private TextView filterButton;
    private Switch master;
    private ListView list;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private boolean exitPromptScheduled;
    private String searchQuery = "";
    private int selectedView;
    private int selectedFilter;
    private int selectedSource;
    private int listPosition;
    private int listOffset;
    private boolean restoreListPosition;
    private int originalSoftInputMode;
    private boolean softInputModeChanged;

    private final Runnable delayedSearch = this::rebuild;

    public static void open(Activity activity) {
        int containerId = findFragmentContainer(activity);
        if (containerId == View.NO_ID) {
            Utils.showToastLong(L10n.t(Utils.getContext(),
                    "Feature Gate Lab could not find the settings container"));
            return;
        }
        FeatureGateLabSession.begin();
        activity.getFragmentManager()
                .beginTransaction()
                .replace(containerId, new FeatureGateLabFragment())
                .addToBackStack("feature_gate_lab")
                .commit();
    }

    static void awaitFileIoForTests() throws Exception {
        FILE_IO_EXECUTOR.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    static void resetForTests() {
        CHANGING.set(false);
    }

    @Override
    public View onCreateView(android.view.LayoutInflater inflater, ViewGroup container, Bundle state) {
        Context context = getActivity();
        SettingsOperationJournal.initialize(context == null ? null : context.getApplicationContext());
        SettingsOperationJournal.showRecoveryNotice(context);
        FeatureGateLabUi.syncTheme(context);

        Activity activity = getActivity();
        if (activity != null && !softInputModeChanged) {
            originalSoftInputMode = activity.getWindow().getAttributes().softInputMode;
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            softInputModeChanged = true;
        }

        FeatureGateLabUi.BackInterceptLayout root =
                new FeatureGateLabUi.BackInterceptLayout(context, this::handleSystemBack);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SettingsUi.background());
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        FrameLayout header = FeatureGateLabUi.header(
                context,
                L10n.t(context, "Feature Gate Lab"),
                this::leaveLab,
                this::showOverflow
        );
        root.addView(header, FeatureGateLabUi.matchWrap());

        LinearLayout controls = new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(
                FeatureGateLabUi.dp(context, 16),
                0,
                FeatureGateLabUi.dp(context, 16),
                FeatureGateLabUi.dp(context, 8)
        );
        root.addView(controls, FeatureGateLabUi.matchWrap());

        LinearLayout masterRow = new LinearLayout(context);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        masterRow.setGravity(Gravity.CENTER_VERTICAL);
        masterRow.setPadding(FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 12),
                FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 12));
        masterRow.setBackground(SettingsUi.borderedSurface(context, 10, false));
        LinearLayout masterText = new LinearLayout(context);
        masterText.setOrientation(LinearLayout.VERTICAL);
        masterText.addView(FeatureGateLabUi.body(context, L10n.t(context, "Enable overrides")),
                FeatureGateLabUi.matchWrap());
        masterText.addView(FeatureGateLabUi.label(context,
                L10n.t(context, "Applies saved rules at supported getters")),
                FeatureGateLabUi.matchWrap());
        masterRow.addView(masterText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        master = new Switch(context);
        master.setChecked(FeatureGateLabStore.masterEnabled());
        master.setContentDescription(L10n.t(context, "Enable overrides"));
        SettingsUi.styleSwitch(master);
        masterRow.addView(master, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                FeatureGateLabUi.dp(context, 48)
        ));
        controls.addView(masterRow, FeatureGateLabUi.matchWrap());

        TextView warning = FeatureGateLabUi.label(
                context,
                L10n.t(context, "Account warning: forced client values apply to every account in this app data and cannot bypass server controls.")
        );
        warning.setTextColor(FeatureGateLabUi.warningColor(context));
        LinearLayout.LayoutParams warningParams = FeatureGateLabUi.matchWrap();
        warningParams.setMargins(FeatureGateLabUi.dp(context, 4), FeatureGateLabUi.dp(context, 14), FeatureGateLabUi.dp(context, 4), FeatureGateLabUi.dp(context, 16));
        controls.addView(warning, warningParams);

        LinearLayout searchRow = new LinearLayout(context);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(FeatureGateLabUi.dp(context, 8), 0, 0, 0);
        searchRow.setBackground(SettingsUi.borderedSurface(context, 6, false));
        search = new EditText(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        // No content description on a search box. On an editable view it replaces what was
        // typed in the announcement, so "cats" came back as the label. The hint names it.
        search.setHint(L10n.t(context, "Search words or key"));
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setTextColor(SettingsUi.textPrimary());
        search.setHintTextColor(SettingsUi.textSecondary());
        search.setText(searchQuery);
        search.setSelection(search.length());
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((view, actionId, event) -> {
            boolean submitted = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP);
            if (!submitted) return false;
            searchHandler.removeCallbacks(delayedSearch);
            searchQuery = search.getText().toString();
            rebuild();
            FeatureGateLabUi.hideKeyboard(search);
            View rootView = getView();
            if (rootView != null) rootView.requestFocus();
            return true;
        });
        // A fixed 48dp box clipped 32sp glyphs at 2x text. CommentSearch documents the
        // same trap and answers it the same way: 48dp is the floor, not the height.
        search.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
        searchRow.addView(search, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        clearSearch = FeatureGateLabUi.clearSearchButton(context, () -> search.setText(""));
        clearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
        searchRow.addView(clearSearch, new LinearLayout.LayoutParams(
                FeatureGateLabUi.dp(context, 48),
                FeatureGateLabUi.dp(context, 48)
        ));
        LinearLayout.LayoutParams searchParams = FeatureGateLabUi.matchWrap();
        searchParams.setMargins(0, 0, 0, FeatureGateLabUi.dp(context, 10));
        controls.addView(searchRow, searchParams);

        HorizontalScrollView sourceScroller = new HorizontalScrollView(context);
        sourceScroller.setHorizontalScrollBarEnabled(false);
        sourceScroller.setFillViewport(false);
        LinearLayout sourceTabs = new LinearLayout(context);
        sourceTabs.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < SOURCE_LABELS.length; i++) {
            final int position = i;
            LinearLayout tabContainer = new LinearLayout(context);
            tabContainer.setOrientation(LinearLayout.VERTICAL);
            tabContainer.setGravity(Gravity.CENTER);
            tabContainer.setFocusable(true);
            tabContainer.setOnClickListener(view -> onSourceSelected(position));

            TextView tab = FeatureGateLabUi.text(
                    context,
                    SOURCE_LABELS[i],
                    14,
                    SettingsUi.textSecondary(),
                    Typeface.BOLD
            );
            tab.setGravity(Gravity.CENTER);
            tab.setMinWidth(FeatureGateLabUi.dp(context, 72));
            tab.setMinHeight(FeatureGateLabUi.dp(context, 46));
            tab.setPadding(
                    FeatureGateLabUi.dp(context, 14),
                    0,
                    FeatureGateLabUi.dp(context, 14),
                    0
            );
            View indicator = new View(context);
            sourceTabLabels[i] = tab;
            sourceTabIndicators[i] = indicator;
            tabContainer.addView(tab, FeatureGateLabUi.matchWrap());
            tabContainer.addView(indicator, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    FeatureGateLabUi.dp(context, 2)
            ));
            sourceTabs.addView(tabContainer, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    FeatureGateLabUi.dp(context, 48)
            ));
        }
        sourceScroller.addView(sourceTabs, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams sourceParams = FeatureGateLabUi.matchWrap();
        sourceParams.setMargins(0, 0, 0, FeatureGateLabUi.dp(context, 8));
        controls.addView(sourceScroller, sourceParams);

        viewTabs = new LinearLayout(context);
        viewTabs.setOrientation(LinearLayout.HORIZONTAL);
        viewTabs.setPadding(
                FeatureGateLabUi.dp(context, 2),
                FeatureGateLabUi.dp(context, 2),
                FeatureGateLabUi.dp(context, 2),
                FeatureGateLabUi.dp(context, 2)
        );
        viewTabs.setBackground(SettingsUi.borderedSurface(context, 6, false));
        for (int i = 0; i < VIEW_LABELS.length; i++) {
            final int position = i;
            TextView tab = FeatureGateLabUi.text(context, VIEW_LABELS[i], 14, SettingsUi.textSecondary(), Typeface.BOLD);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(FeatureGateLabUi.dp(context, 48));
            tab.setFocusable(true);
            tab.setOnClickListener(view -> onViewSelected(position));
            viewTabLabels[i] = tab;
            viewTabs.addView(tab, new LinearLayout.LayoutParams(0, FeatureGateLabUi.dp(context, 48), 1f));
        }
        controls.addView(viewTabs, FeatureGateLabUi.matchWrap());

        LinearLayout resultRow = new LinearLayout(context);
        resultRow.setOrientation(LinearLayout.HORIZONTAL);
        resultRow.setGravity(Gravity.CENTER_VERTICAL);
        count = FeatureGateLabUi.label(context, L10n.t(context, "Loading gates..."));
        count.setGravity(Gravity.CENTER_VERTICAL);
        resultRow.addView(count, new LinearLayout.LayoutParams(0, FeatureGateLabUi.dp(context, 44), 1f));
        filterButton = FeatureGateLabUi.text(context, "", 14, SettingsUi.textPrimary(), Typeface.BOLD);
        filterButton.setGravity(Gravity.CENTER);
        filterButton.setPadding(
                FeatureGateLabUi.dp(context, 12),
                0,
                FeatureGateLabUi.dp(context, 12),
                0
        );
        filterButton.setBackground(SettingsUi.borderedSurface(context, 6, false));
        filterButton.setFocusable(true);
        filterButton.setOnClickListener(view -> showFilterPicker());
        resultRow.addView(filterButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                FeatureGateLabUi.dp(context, 48)
        ));
        controls.addView(resultRow, FeatureGateLabUi.matchWrap());

        loading = FeatureGateLabUi.label(context,
                L10n.t(context, "Loading local catalog and current TikTok cache..."));
        controls.addView(loading, FeatureGateLabUi.matchWrap());

        FrameLayout listContainer = new FrameLayout(context);
        list = new ListView(context);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(FeatureGateLabUi.dp(context, 16), 0, FeatureGateLabUi.dp(context, 16), FeatureGateLabUi.dp(context, 24));
        list.setClipToPadding(false);
        list.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        adapter = new GateAdapter(context, visible);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            FeatureGateCatalog.Entry gate = visible.get(position);
            // While a selection is up a tap adds and removes rather than opening, which is what
            // every list that does this does: entering the mode changes what a tap means.
            if (selection.isEmpty()) {
                openDetail(gate);
                return;
            }
            String identity = gate.identity();
            if (selection.remove(identity) == null) selection.put(identity, gate);
            onSelectionChanged();
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            FeatureGateCatalog.Entry gate = visible.get(position);
            selection.put(gate.identity(), gate);
            onSelectionChanged();
            return true;
        });
        listContainer.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        empty = FeatureGateLabUi.label(context,
                L10n.t(context, "No gates match this search and filter."));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(
                FeatureGateLabUi.dp(context, 24),
                FeatureGateLabUi.dp(context, 24),
                FeatureGateLabUi.dp(context, 24),
                FeatureGateLabUi.dp(context, 24)
        );
        listContainer.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        list.setEmptyView(empty);
        root.addView(listContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildSelectionBar(context), FeatureGateLabUi.matchWrap());

        master.setOnCheckedChangeListener((button, checked) -> onMasterChanged(checked));
        search.addTextChangedListener(new SimpleTextWatcher(() -> {
            searchQuery = search.getText().toString();
            clearSearch.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
            searchHandler.removeCallbacks(delayedSearch);
            searchHandler.postDelayed(delayedSearch, SEARCH_DELAY_MS);
        }));

        updateControls();
        load(false);
        root.post(this::showMigrationNoticeIfNeeded);
        return root;
    }

    private void showMigrationNoticeIfNeeded() {
        Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || !FeatureGateLabStore.consumeMigrationNotice()) {
            return;
        }
        Utils.showToastLong(L10n.f(Utils.getContext(),
                "Older overrides were kept disabled. Review their values before enabling them on TikTok %1$s.",
                FeatureGateLabStore.TARGET_VERSION));
    }

    @Override
    public void onResume() {
        super.onResume();
        systemBack.register(getActivity(), this::handleSystemBack);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_LOADED) writeLoadedValuesFile(uri);
        if (requestCode == REQUEST_IMPORT_LOADED) readLoadedValuesFile(uri);
    }

    @Override
    public void onPause() {
        systemBack.unregister();
        Activity activity = getActivity();
        if (activity != null && activity.isFinishing() && FeatureGateLabSession.restartNeeded()) {
            FeatureGateLabSession.showAfterHostExit(activity);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacks(delayedSearch);
        if (search != null) searchQuery = search.getText().toString();
        if (list != null && list.getChildCount() > 0) {
            listPosition = list.getFirstVisiblePosition();
            listOffset = list.getChildAt(0).getTop();
            restoreListPosition = true;
        }
        adapter = null;
        count = null;
        loading = null;
        empty = null;
        search = null;
        clearSearch = null;
        viewTabs = null;
        filterButton = null;
        master = null;
        list = null;
        selectionBar = null;
        selectionCount = null;
        for (int i = 0; i < viewTabLabels.length; i++) viewTabLabels[i] = null;
        for (int i = 0; i < sourceTabLabels.length; i++) {
            sourceTabLabels[i] = null;
            sourceTabIndicators[i] = null;
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        Activity activity = getActivity();
        if (activity != null && softInputModeChanged) {
            activity.getWindow().setSoftInputMode(originalSoftInputMode);
            softInputModeChanged = false;
        }
        super.onDestroy();
        if (activity == null || activity.isChangingConfigurations() || activity.isFinishing()) return;
        activity.getWindow().getDecorView().post(() -> {
            if (!exitPromptScheduled && FeatureGateLabSession.restartNeeded()) {
                exitPromptScheduled = true;
                FeatureGateLabSession.showRestartDialog(activity);
            }
        });
    }

    private void leaveLab() {
        FeatureGateLabUi.hideKeyboard(search);
        FragmentManager manager = getFragmentManager();
        if (manager != null) manager.popBackStack();
    }

    private void handleSystemBack() {
        // Entering selection changed what a tap means, so Back is what takes that back. Without
        // this the only way out was the Cancel action, and Back left the Lab entirely.
        if (!selection.isEmpty()) {
            selection.clear();
            onSelectionChanged();
            return;
        }
        if (search != null && search.hasFocus()) {
            FeatureGateLabUi.hideKeyboard(search);
            View root = getView();
            if (root != null) root.requestFocus();
            return;
        }
        leaveLab();
    }

    private void load(boolean refresh) {
        loading.setVisibility(View.VISIBLE);
        loading.setText(L10n.t(getContext(), refresh
                ? "Refreshing current TikTok cache..."
                : "Loading local catalog and current TikTok cache..."));
        FeatureGateCatalog.loadAsync(refresh, new FeatureGateCatalog.Callback() {
            @Override
            public void onLoaded(FeatureGateCatalog.Snapshot loaded) {
                if (!isAdded() || getView() == null) return;
                snapshot = loaded;
                if (loaded.catalogComplete) {
                    loading.setVisibility(View.GONE);
                } else {
                    loading.setVisibility(View.VISIBLE);
                    loading.setText(L10n.t(getContext(),
                            "Loaded current values. Loading all known gates..."));
                }
                empty.setText(L10n.t(getContext(), "No gates match this search and filter."));
                rebuild();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || getView() == null) return;
                loading.setText(
                        L10n.f(getContext(), "Current cache unavailable: %1$s", message));
                FeatureGateCatalog.Snapshot cached = FeatureGateCatalog.cachedSnapshot();
                if (cached != null) {
                    snapshot = cached;
                    rebuild();
                } else {
                    count.setText(L10n.t(getContext(), "Gate data unavailable"));
                    empty.setText(L10n.t(getContext(), "No gate data is available. Refresh values from the menu to try again."));
                }
            }
        });
    }

    private void rebuild() {
        if (snapshot == null || adapter == null) return;
        String query = normalizeSearchText(searchQuery);
        Map<String, FeatureGateLabStore.Rule> rules = rulesByIdentity();
        Map<FeatureGateCatalog.Entry, Integer> searchRanks = new HashMap<>();

        visible.clear();
        for (FeatureGateCatalog.Entry entry : snapshot.entries) {
            if (!entry.userVisible()) continue;
            String sourceManager = SOURCE_MANAGERS[selectedSource];
            if (sourceManager != null && !sourceManager.equals(entry.manager)) continue;
            FeatureGateLabStore.Rule rule = rules.get(ruleIdentity(entry));
            if (selectedView == 0 && !entry.loaded) continue;
            if (selectedView == 2 && rule == null) continue;
            if (!matchesFilter(entry)) continue;
            if (!query.isEmpty()) {
                int rank = searchRank(entry, query);
                if (rank < 0) continue;
                searchRanks.put(entry, rank);
            }
            visible.add(entry);
        }

        if (selectedView == 2) {
            Collections.sort(visible, (left, right) -> {
                FeatureGateLabStore.Rule leftRule = rules.get(ruleIdentity(left));
                FeatureGateLabStore.Rule rightRule = rules.get(ruleIdentity(right));
                return Long.compare(
                        rightRule == null ? 0 : rightRule.updatedAtMs,
                        leftRule == null ? 0 : leftRule.updatedAtMs
                );
            });
        } else if (!query.isEmpty()) {
            Collections.sort(visible, (left, right) -> {
                int rank = Integer.compare(searchRanks.get(left), searchRanks.get(right));
                return rank != 0 ? rank : left.title.compareToIgnoreCase(right.title);
            });
        }

        count.setText(visible.size() + (visible.size() == 1 ? " result" : " results"));
        adapter.notifyDataSetChanged();
        if (restoreListPosition && list != null) {
            int position = listPosition;
            int offset = listOffset;
            restoreListPosition = false;
            list.post(() -> {
                if (list != null) list.setSelectionFromTop(position, offset);
            });
        }
    }

    private Map<String, FeatureGateLabStore.Rule> rulesByIdentity() {
        Map<String, FeatureGateLabStore.Rule> rules = new HashMap<>();
        for (FeatureGateLabStore.Rule rule : FeatureGateLabStore.rules()) {
            rules.put(rule.manager + "\n" + rule.key + "\n" + rule.type, rule);
        }
        return rules;
    }

    private static String ruleIdentity(FeatureGateCatalog.Entry entry) {
        return entry.manager + "\n" + entry.key + "\n" + entry.type;
    }

    private boolean matchesFilter(FeatureGateCatalog.Entry entry) {
        if (selectedFilter == FILTER_ALL) return true;
        if (selectedFilter == FILTER_UNLOADED) return !entry.loaded;
        if (!entry.loaded) return false;

        boolean isBoolean = "BOOLEAN".equalsIgnoreCase(entry.type)
                || "BOOLEAN".equalsIgnoreCase(entry.currentType);
        if (!isBoolean) return false;
        if (selectedFilter == FILTER_BOOLEAN) return true;
        if (selectedFilter == FILTER_ENABLED) return "true".equalsIgnoreCase(entry.currentValue);
        if (selectedFilter == FILTER_DISABLED) return "false".equalsIgnoreCase(entry.currentValue);
        return true;
    }

    private void onViewSelected(int position) {
        selectedView = position;
        if (selectedView == 0 && selectedFilter == FILTER_UNLOADED) selectedFilter = FILTER_ALL;
        if (selectedView != 0 && selectedFilter >= FILTER_BOOLEAN
                && selectedFilter <= FILTER_DISABLED) selectedFilter = FILTER_ALL;
        updateControls();
        rebuild();
    }

    private void onFilterSelected(int position) {
        selectedFilter = position;
        if (selectedFilter >= FILTER_BOOLEAN && selectedFilter <= FILTER_DISABLED) selectedView = 0;
        if (selectedFilter == FILTER_UNLOADED) selectedView = 1;
        updateControls();
        rebuild();
    }

    private void onSourceSelected(int position) {
        selectedSource = position;
        updateControls();
        rebuild();
    }

    private void updateControls() {
        for (int i = 0; i < viewTabLabels.length; i++) {
            TextView tab = viewTabLabels[i];
            if (tab == null) continue;
            boolean selected = i == selectedView;
            tab.setSelected(selected);
            tab.setTextColor(selected ? SettingsUi.badgeText() : SettingsUi.textSecondary());
            if (selected) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(SettingsUi.badgeFill());
                background.setCornerRadius(FeatureGateLabUi.dp(tab.getContext(), 5));
                tab.setBackground(background);
            } else {
                tab.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        for (int i = 0; i < sourceTabLabels.length; i++) {
            TextView tab = sourceTabLabels[i];
            View indicator = sourceTabIndicators[i];
            if (tab == null || indicator == null) continue;
            boolean selected = i == selectedSource;
            tab.setSelected(selected);
            tab.setTextColor(selected ? SettingsUi.accent() : SettingsUi.textSecondary());
            tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            indicator.setBackgroundColor(selected ? SettingsUi.accent() : Color.TRANSPARENT);
        }
        if (filterButton != null) {
            filterButton.setText(L10n.f(getContext(), "Filter: %1$s",
                    filterLabels(getContext())[selectedFilter]));
        }
    }

    private void showFilterPicker() {
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(L10n.t(getContext(), "Show gates"))
                .setSingleChoiceItems(filterLabels(getContext()), selectedFilter, (choiceDialog, which) -> {
                    onFilterSelected(which);
                    choiceDialog.dismiss();
                })
                .setNegativeButton(L10n.t(getContext(), "Cancel"), null)
                .create();
        showStyled(dialog);
    }

    /**
     * Puts the switch back where the store is, when the screen is still up.
     *
     * <p>The null check is the point: a Lab change finishes on the main thread after work that
     * takes long enough to leave the screen during, and {@link #onDestroyView} has cleared the
     * switch by then.
     */
    private void syncMasterSwitch() {
        if (master == null) return;
        master.setChecked(FeatureGateLabStore.masterEnabled());
        master.setContentDescription(L10n.t(getContext(), "Enable overrides"));
        SettingsUi.styleSwitch(master);
    }

    private void onMasterChanged(boolean checked) {
        if (FeatureGateLabStore.masterEnabled() == checked) return;
        if (CHANGING.get()) {
            syncMasterSwitch();
            Utils.showToastLong(L10n.t(Utils.getContext(),
                    "A Lab change is already running"));
            return;
        }
        // This is storage, not a flag: the journal lock, two write-and-verify cycles and a
        // blocking commit. The Lab's other three mutations already go this way; this one held
        // the main thread, and a settings restore holding the journal lock froze the screen
        // until it finished. runLabChange puts the switch back for us either way.
        runLabChange(() -> FeatureGateLabUndo.setMasterEnabled(checked),
                checked ? "Overrides enabled. Restart TikTok to apply saved values."
                        : "Overrides disabled. Restart TikTok to restore native values.");
    }

    /**
     * The row of actions that appears once a long press has gathered a selection.
     *
     * <p>Built with the screen and hidden, rather than added and removed, so nothing about the
     * layout moves when a selection starts.
     */
    private LinearLayout buildSelectionBar(Context context) {
        selectionBar = new LinearLayout(context);
        selectionBar.setOrientation(LinearLayout.VERTICAL);
        selectionBar.setVisibility(View.GONE);
        int padding = FeatureGateLabUi.dp(context, 16);
        selectionBar.setPadding(padding, FeatureGateLabUi.dp(context, 12), padding,
                FeatureGateLabUi.dp(context, 12));
        selectionBar.setBackground(SettingsUi.roundedSurface(context, 0, false));

        selectionCount = FeatureGateLabUi.label(context, "");
        selectionBar.addView(selectionCount, FeatureGateLabUi.matchWrap());

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.addView(selectionAction(context, L10n.t(context, "Reset"), this::resetSelection));
        actions.addView(selectionAction(context, L10n.t(context, "Disable"),
                () -> forceSelection(false)));
        actions.addView(selectionAction(context, L10n.t(context, "Enable"),
                () -> forceSelection(true)));
        actions.addView(selectionAction(context, L10n.t(context, "Cancel"), () -> {
            selection.clear();
            onSelectionChanged();
        }));
        selectionBar.addView(actions, FeatureGateLabUi.matchWrap());
        return selectionBar;
    }

    private TextView selectionAction(Context context, String label, Runnable action) {
        TextView button = FeatureGateLabUi.text(context, label, 14, SettingsUi.accent(), Typeface.BOLD);
        button.setMinimumHeight(FeatureGateLabUi.dp(context, 48));
        button.setMinimumWidth(FeatureGateLabUi.dp(context, 48));
        button.setGravity(Gravity.CENTER);
        int side = FeatureGateLabUi.dp(context, 12);
        button.setPadding(side, 0, side, 0);
        button.setContentDescription(label);
        // A TextView with a click listener is read as text, and these are the whole of what a
        // selection can do.
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(
                    View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        button.setOnClickListener(view -> action.run());
        return button;
    }

    /** Shows or hides the bar and repaints the rows, which draw their own chosen state. */
    private void onSelectionChanged() {
        if (selectionBar != null) {
            selectionBar.setVisibility(selection.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (selectionCount != null) {
            selectionCount.setText(selection.size() == 1
                    ? L10n.t(getContext(), "1 gate selected")
                    : L10n.f(getContext(), "%1$d gates selected", selection.size()));
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void forceSelection(boolean value) {
        List<FeatureGateCatalog.Entry> gates = new ArrayList<>(selection.values());
        int total = gates.size();
        boolean started = runLabChange(() -> {
            int written = FeatureGateLabUndo.forceBoolean(gates, value);
            if (written == 0) {
                throw new IllegalStateException(L10n.t(Utils.getContext(),
                        "None of these gates takes a true or false value."));
            }
            // The message says what happened rather than what was asked for. Reported as a
            // failure, which is what throwing here did, it said nothing went through on a run
            // where most of it had.
            //
            // Eight whole sentences rather than a verb, a count and two tails glued together.
            // A table row holds a sentence: assembled here, the plural rule of the language
            // never got a say and neither did the order the pieces go in.
            android.content.Context context = Utils.getContext();
            if (written == total) {
                if (value) {
                    return written == 1
                            ? L10n.t(context, "Forced 1 gate. Restart TikTok to apply this.")
                            : L10n.f(context, "Forced %1$d gates. Restart TikTok to apply this.",
                                    written);
                }
                return written == 1
                        ? L10n.t(context, "Turned off 1 gate. Restart TikTok to apply this.")
                        : L10n.f(context, "Turned off %1$d gates. Restart TikTok to apply this.",
                                written);
            }
            if (value) {
                return written == 1
                        ? L10n.f(context, "Forced 1 gate of %1$d; the rest do not take a true or false value. Restart TikTok to apply this.", total)
                        : L10n.f(context, "Forced %1$d gates of %2$d; the rest do not take a true or false value. Restart TikTok to apply this.", written, total);
            }
            return written == 1
                    ? L10n.f(context, "Turned off 1 gate of %1$d; the rest do not take a true or false value. Restart TikTok to apply this.", total)
                    : L10n.f(context, "Turned off %1$d gates of %2$d; the rest do not take a true or false value. Restart TikTok to apply this.", written, total);
        });
        if (started) {
            selection.clear();
            onSelectionChanged();
        }
    }

    private void resetSelection() {
        List<FeatureGateCatalog.Entry> gates = new ArrayList<>(selection.values());
        int total = gates.size();
        boolean started = runLabChange(() -> {
            int dropped = FeatureGateLabUndo.resetAll(gates);
            if (dropped == 0) {
                throw new IllegalStateException(L10n.t(Utils.getContext(),
                        "None of these gates had an override to reset."));
            }
            // Dropped, not selected: choosing five gates of which two had an override resets two.
            // One whole sentence per shape. Built from a count and three fragments it was four
            // rows no table could hold, and the plural rule of the language never got a say.
            android.content.Context context = Utils.getContext();
            if (dropped == total) {
                return dropped == 1
                        ? L10n.t(context, "Reset 1 gate. Restart TikTok to apply this.")
                        : L10n.f(context, "Reset %1$d gates. Restart TikTok to apply this.",
                                dropped);
            }
            return dropped == 1
                    ? L10n.f(context, "Reset 1 gate of %1$d. Restart TikTok to apply this.",
                            total)
                    : L10n.f(context, "Reset %1$d gates of %2$d. Restart TikTok to apply this.",
                            dropped, total);
        });
        if (started) {
            selection.clear();
            onSelectionChanged();
        }
    }

    private void openDetail(FeatureGateCatalog.Entry entry) {
        FeatureGateLabUi.hideKeyboard(search);
        FeatureGateDetailFragment detail = FeatureGateDetailFragment.forEntry(entry.manager, entry.key, entry.type);
        getFragmentManager().beginTransaction()
                .replace(getId(), detail)
                .addToBackStack("feature_gate_detail")
                .commit();
    }

    private void showOverflow() {
        View anchor = getView() == null ? null : getView().findViewWithTag("feature_gate_menu");
        if (anchor == null) return;
        int popupTheme = SettingsUi.isDarkMode()
                ? android.R.style.Theme_Material
                : android.R.style.Theme_Material_Light;
        PopupMenu menu = new PopupMenu(
                new ContextThemeWrapper(getActivity(), popupTheme),
                anchor
        );
        menu.getMenu().add(0, 1, 0, L10n.t(getContext(), "Refresh values"));
        menu.getMenu().add(0, 2, 1, L10n.t(getContext(), "Export loaded values"));
        menu.getMenu().add(0, 3, 2, L10n.t(getContext(), "Import loaded values"));
        menu.getMenu().add(0, 4, 3, L10n.t(getContext(), "Reset all overrides"));
        menu.getMenu().add(0, 5, 4, L10n.t(getContext(), "Reset all Lab data"));
        menu.getMenu().add(0, 6, 5, L10n.t(getContext(), "Undo last Lab change"));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    load(true);
                    return true;
                case 2:
                    exportLoadedValues();
                    return true;
                case 3:
                    chooseLoadedValuesFile();
                    return true;
                case 4:
                    reset(false);
                    return true;
                case 5:
                    reset(true);
                    return true;
                case 6:
                    runLabChange(FeatureGateLabUndo::undo, "Restored the previous Lab settings. Restart TikTok.");
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void exportLoadedValues() {
        try {
            if (getActivity() == null || snapshot == null) return;
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/gzip")
                    .putExtra(Intent.EXTRA_TITLE,
                            "tiktok-46.2.3-loaded-feature-gates-" + timestamp + ".json.gz");
            startActivityForResult(intent, REQUEST_EXPORT_LOADED);
        } catch (Throwable throwable) {
            Utils.showToastLong(L10n.t(Utils.getContext(),
                    "Could not open the export file picker"));
        }
    }

    private void chooseLoadedValuesFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES,
                            new String[]{"application/gzip", "application/json", "application/octet-stream"});
            startActivityForResult(intent, REQUEST_IMPORT_LOADED);
        } catch (Throwable throwable) {
            Utils.showToastLong(L10n.t(Utils.getContext(),
                    "Could not open the import file picker"));
        }
    }

    private void writeLoadedValuesFile(Uri uri) {
        Activity activity = getActivity();
        ContentResolver resolver = activity == null ? null : activity.getContentResolver();
        FILE_IO_EXECUTOR.execute(() -> {
            try {
                if (resolver == null) throw new IllegalStateException("Activity detached");
                ExportPayload payload = buildExportPayload();
                try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) throw new IllegalStateException("Document provider returned no output stream");
                    output.write(payload.gzipBytes);
                }
                postToast(payload.count == 1
                        ? L10n.t(Utils.getContext(), "Exported 1 loaded value")
                        : L10n.f(Utils.getContext(), "Exported %1$d loaded values",
                                payload.count));
            } catch (Throwable throwable) {
                Logger.printException(() -> "Loaded-value file export failed", throwable);
                postToast(L10n.t(Utils.getContext(), deleteCreatedDocument(resolver, uri)
                        ? "Loaded-value file export failed"
                        : "Loaded-value file export failed; cleanup also failed"));
            }
        });
    }

    private void readLoadedValuesFile(Uri uri) {
        FILE_IO_EXECUTOR.execute(() -> {
            try {
                Activity activity = getActivity();
                if (activity == null) return;
                byte[] encoded;
                try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalStateException("Document provider returned no input stream");
                    encoded = readLimited(input, MAX_COMPRESSED_IMPORT_BYTES);
                }
                reviewLoadedImport(readLoadedJson(encoded));
            } catch (Throwable throwable) {
                Logger.printException(() -> "Loaded-value file import failed", throwable);
                postToast(L10n.t(Utils.getContext(),
                        "Loaded-value file is invalid or too large"));
            }
        });
    }

    private void reviewLoadedImport(JSONObject imported) throws Exception {
        Activity activity = getActivity();
        FeatureGateCatalog.Snapshot currentSnapshot = snapshot;
        if (activity == null || currentSnapshot == null) return;
        if (!"loaded_values".equals(imported.optString("payload_kind"))) {
            throw new IllegalArgumentException("Unsupported Feature Gate Lab export type");
        }
        if (!FeatureGateLabStore.TARGET_VERSION.equals(imported.optString("tiktok_version"))) {
            throw new IllegalArgumentException("Loaded values target a different TikTok version");
        }

        Map<String, FeatureGateLabStore.Rule> existingRules = rulesByIdentity();
        JSONArray sourceRules = imported.optJSONArray("rules");
        JSONArray candidates = new JSONArray();
        int same = 0;
        int unavailable = 0;
        int malformed = 0;
        if (sourceRules == null) throw new IllegalArgumentException("Missing loaded values");
        if (sourceRules.length() > MAX_IMPORT_RULES) {
            throw new IllegalArgumentException("Loaded values contain too many rules");
        }
        {
            for (int i = 0; i < sourceRules.length(); i++) {
                JSONObject item = sourceRules.optJSONObject(i);
                if (item == null) {
                    malformed++;
                    continue;
                }
                if (!isStringField(item, "manager") || !isStringField(item, "key")
                        || !isStringField(item, "type") || !isStringField(item, "value")) {
                    malformed++;
                    continue;
                }
                String manager = item.optString("manager");
                String key = item.optString("key");
                String type = item.optString("type", "").toUpperCase(Locale.ROOT);
                FeatureGateCatalog.Entry local = currentSnapshot.byIdentity.get(manager + "\n" + key);
                if (local == null) {
                    unavailable++;
                    continue;
                }

                String importedValue = item.optString("value", "");
                FeatureGateLabStore.Rule existingRule = existingRules.get(
                        manager + "\n" + key + "\n" + type);
                boolean alreadyRestored = existingRule != null
                        ? !existingRule.enabled && importedValue.equals(existingRule.value)
                        : local.loaded && importedValue.equals(local.currentValue);
                if (alreadyRestored) {
                    same++;
                    continue;
                }
                candidates.put(item);
            }
        }

        JSONObject profile = new JSONObject();
        profile.put("schema", 1);
        profile.put("target", "TikTok global");
        profile.put("tiktok_version", FeatureGateLabStore.TARGET_VERSION);
        profile.put("rules", candidates);
        FeatureGateLabStore.ImportReview review = FeatureGateLabStore.reviewProfile(
                profile.toString(), currentSnapshot.byIdentity);

        String message = L10n.f(Utils.getContext(),
                "Imported %1$d disabled values. %2$d already matched, %3$d unavailable, %4$d rejected. Undo last Lab change is in the menu.",
                review.accepted.size(), same, unavailable, review.rejected.size() + malformed);
        runLabChange(() -> FeatureGateLabUndo.importRules(review), message);
    }

    private ExportPayload buildExportPayload() throws Exception {
        FeatureGateCatalog.Snapshot currentSnapshot = snapshot;
        if (currentSnapshot == null) throw new IllegalStateException("No loaded Feature Gate Lab snapshot");

        JSONArray rules = new JSONArray();
        for (FeatureGateCatalog.Entry entry : currentSnapshot.entries) {
            if (!entry.loaded || !entry.actionable) continue;
            String type = entry.type.toUpperCase(Locale.ROOT);
            if (!isPrimitiveType(type)) continue;
            String value = entry.currentValue == null ? "" : entry.currentValue;
            if (value.isEmpty() && !"STRING".equals(type)) continue;

            JSONObject item = new JSONObject();
            item.put("manager", entry.manager);
            item.put("key", entry.key);
            item.put("type", type);
            item.put("value", value);
            item.put("force", false);
            rules.put(item);
        }

        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("payload_kind", "loaded_values");
        root.put("target", "TikTok global");
        root.put("tiktok_version", FeatureGateLabStore.TARGET_VERSION);
        root.put("exported_at_ms", System.currentTimeMillis());
        root.put("entry_count", rules.length());
        root.put("rules", rules);
        JSONArray settingsManagerObservations =
                FeatureGateLabRuntime.settingsManagerObservationsJson();
        root.put("settings_manager_observation_count", settingsManagerObservations.length());
        root.put("settings_manager_observations", settingsManagerObservations);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(root.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new ExportPayload(bytes.toByteArray(), rules.length());
    }

    private static JSONObject readLoadedJson(byte[] encoded) throws Exception {
        byte[] json = isGzip(encoded) ? readGzipJson(encoded) : encoded;
        return SettingsJson.parseObject(json, IMPORT_JSON_LIMITS);
    }

    private static boolean isGzip(byte[] encoded) {
        return encoded.length >= 2 && (encoded[0] & 0xff) == 0x1f
                && (encoded[1] & 0xff) == 0x8b;
    }

    private static byte[] readGzipJson(byte[] compressed) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return readLimited(gzip, MAX_JSON_IMPORT_BYTES);
        }
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IllegalArgumentException("File exceeds the import size limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean deleteCreatedDocument(ContentResolver resolver, Uri uri) {
        if (resolver == null || uri == null) return false;
        try {
            return resolver.delete(uri, null, null) > 0;
        } catch (Throwable cleanupError) {
            Logger.printException(() -> "Loaded-value export cleanup failed", cleanupError);
            return false;
        }
    }

    private static boolean isPrimitiveType(String type) {
        return "BOOLEAN".equals(type) || "INT".equals(type) || "LONG".equals(type)
                || "FLOAT".equals(type) || "DOUBLE".equals(type) || "STRING".equals(type);
    }

    private static boolean isStringField(JSONObject object, String key) {
        return object.has(key) && object.opt(key) instanceof String;
    }

    private void postToast(String text) {
        new Handler(Looper.getMainLooper()).post(() -> Utils.showToastLong(text));
    }

    private static final class ExportPayload {
        final byte[] gzipBytes;
        final int count;

        ExportPayload(byte[] gzipBytes, int count) {
            this.gzipBytes = gzipBytes;
            this.count = count;
        }
    }

    private void reset(boolean allData) {
        runLabChange(() -> FeatureGateLabUndo.reset(allData),
                "Lab " + (allData ? "data" : "overrides") + " reset. Undo last Lab change is in the menu. Restart TikTok.");
    }

    private interface LabChange { void run() throws Exception; }

    /** A change that decides its own message from what it turned out to do. */
    private interface ReportingLabChange { String run() throws Exception; }

    /**
     * The thread the last Lab change ran on.
     *
     * <p>Every one of them touches storage, so a test needs to be able to say which thread that
     * happened on rather than only that it happened.
     */
    private static volatile String lastChangeThreadForTests;

    static String lastChangeThreadForTests() {
        return lastChangeThreadForTests;
    }

    private void runLabChange(LabChange change, String message) {
        runLabChange(() -> {
            change.run();
            return message;
        });
    }

    /**
     * @return false when another change was already running, so nothing was started. Callers with
     *         state of their own, such as the gate selection, keep it rather than giving it up
     *         for a change that never ran.
     */
    private boolean runLabChange(ReportingLabChange change) {
        if (!CHANGING.compareAndSet(false, true)) {
            postToast(L10n.t(Utils.getContext(), "A Lab change is already running"));
            return false;
        }
        Utils.runOnBackgroundThread(() -> {
            lastChangeThreadForTests = Thread.currentThread().getName();
            String result;
            try {
                result = change.run();
            } catch (Exception error) {
                Logger.printException(() -> "Lab change failed", error);
                // The sentence is translated; what the failure itself said is not ours
                // to translate, and dropping it would take the only clue with it.
                result = L10n.t(Utils.getContext(), "Could not change Lab settings.")
                        + " " + error.getMessage();
            }
            String notice = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                // Released first. The flag is process-wide and nothing else clears it, so a
                // failure while putting the screen back used to refuse every later Lab change
                // until TikTok was restarted.
                CHANGING.set(false);
                syncMasterSwitch();
                rebuild();
                Utils.showToastLong(notice);
            });
        });
        return true;
    }

    private static void showStyled(AlertDialog dialog) {
        dialog.setOnShowListener(ignored -> FeatureGateLabUi.styleDialog(dialog));
        dialog.show();
    }

    private static int searchRank(FeatureGateCatalog.Entry entry, String query) {
        String key = normalizeSearchText(entry.key);
        String title = normalizeSearchText(entry.title);
        if (key.equals(query)) return 0;
        if (title.equals(query)) return 1;
        if (key.startsWith(query)) return 2;
        if (title.startsWith(query)) return 3;
        if (key.contains(query)) return 4;
        if (title.contains(query)) return 5;

        String[] queryTokens = query.split(" ");
        String[] candidateTokens = (key + " " + title).split(" ");
        int score = 10;
        for (String queryToken : queryTokens) {
            int bestTokenScore = Integer.MAX_VALUE;
            for (String candidateToken : candidateTokens) {
                bestTokenScore = Math.min(
                        bestTokenScore,
                        tokenMatchScore(queryToken, candidateToken)
                );
                if (bestTokenScore == 0) break;
            }
            if (bestTokenScore == Integer.MAX_VALUE) return -1;
            score += bestTokenScore;
        }
        return score;
    }

    private static String normalizeSearchText(String text) {
        if (text == null || text.isEmpty()) return "";
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        boolean previousWasSpace = true;
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private static int tokenMatchScore(String queryToken, String candidateToken) {
        if (queryToken.equals(candidateToken)) return 0;
        if (candidateToken.startsWith(queryToken)) return 1;
        if (queryToken.length() >= 3 && queryToken.startsWith(candidateToken)) return 2;
        if (queryToken.length() >= 4 && candidateToken.contains(queryToken)) return 3;

        int allowedDistance;
        if (queryToken.length() >= 7) {
            allowedDistance = 2;
        } else if (queryToken.length() >= 4) {
            allowedDistance = 1;
        } else {
            return Integer.MAX_VALUE;
        }
        int distance = editDistanceWithin(queryToken, candidateToken, allowedDistance);
        return distance < 0 ? Integer.MAX_VALUE : 4 + distance;
    }

    private static int editDistanceWithin(String left, String right, int maximum) {
        if (Math.abs(left.length() - right.length()) > maximum) return -1;

        int[] previousPrevious = null;
        int[] previous = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) previous[column] = column;

        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            int rowMinimum = current[0];
            for (int column = 1; column <= right.length(); column++) {
                int substitutionCost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + substitutionCost
                );
                if (previousPrevious != null
                        && row > 1
                        && column > 1
                        && left.charAt(row - 1) == right.charAt(column - 2)
                        && left.charAt(row - 2) == right.charAt(column - 1)) {
                    current[column] = Math.min(
                            current[column],
                            previousPrevious[column - 2] + 1
                    );
                }
                rowMinimum = Math.min(rowMinimum, current[column]);
            }
            if (rowMinimum > maximum) return -1;
            previousPrevious = previous;
            previous = current;
        }
        return previous[right.length()] <= maximum ? previous[right.length()] : -1;
    }

    private static int findFragmentContainer(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        return findFragmentContainer(activity.getFragmentManager(), content);
    }

    private static int findFragmentContainer(FragmentManager manager, View view) {
        if (view == null) return View.NO_ID;
        if (view.getId() != View.NO_ID && manager.findFragmentById(view.getId()) instanceof PreferenceFragment) {
            return view.getId();
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                int found = findFragmentContainer(manager, group.getChildAt(i));
                if (found != View.NO_ID) return found;
            }
        }
        return View.NO_ID;
    }

    private final class GateAdapter extends BaseAdapter {
        private final Context context;
        private final List<FeatureGateCatalog.Entry> entries;

        GateAdapter(Context context, List<FeatureGateCatalog.Entry> entries) {
            this.context = context;
            this.entries = entries;
        }

        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return ruleIdentity(entries.get(position)).hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                // Two columns work while the text is small enough for both to fit. Past that
                // the state column is squeezing three labels into a width that does not grow
                // with them, so the row stacks and each half gets the whole width.
                boolean stacked = context.getResources().getConfiguration().fontScale > 1.3f;

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
                row.setGravity(stacked ? Gravity.START : Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(FeatureGateLabUi.dp(context, 84));
                row.setPadding(
                        FeatureGateLabUi.dp(context, 16),
                        FeatureGateLabUi.dp(context, 16),
                        FeatureGateLabUi.dp(context, 16),
                        FeatureGateLabUi.dp(context, 16)
                );

                LinearLayout textColumn = new LinearLayout(context);
                textColumn.setOrientation(LinearLayout.VERTICAL);
                TextView title = FeatureGateLabUi.text(context, "", 15, SettingsUi.textPrimary(), Typeface.BOLD);
                title.setMaxLines(2);
                TextView key = FeatureGateLabUi.label(context, "");
                key.setTextSize(12);
                key.setTypeface(Typeface.MONOSPACE);
                key.setPadding(0, FeatureGateLabUi.dp(context, 6), 0, 0);
                key.setSingleLine(true);
                key.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                textColumn.addView(title, FeatureGateLabUi.matchWrap());
                textColumn.addView(key, FeatureGateLabUi.matchWrap());
                row.addView(textColumn, stacked
                        ? new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT)
                        : new LinearLayout.LayoutParams(
                                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                int sideGravity = stacked ? Gravity.START : Gravity.END;
                LinearLayout stateColumn = new LinearLayout(context);
                stateColumn.setOrientation(LinearLayout.VERTICAL);
                stateColumn.setGravity(sideGravity | Gravity.CENTER_VERTICAL);
                TextView type = FeatureGateLabUi.text(context, "", 12, SettingsUi.textSecondary(), Typeface.BOLD);
                type.setGravity(sideGravity);
                TextView state = FeatureGateLabUi.text(context, "", 12, SettingsUi.textSecondary(), Typeface.NORMAL);
                state.setGravity(sideGravity);
                TextView value = FeatureGateLabUi.text(context, "", 12, SettingsUi.textPrimary(), Typeface.NORMAL);
                value.setGravity(sideGravity);
                // The value is the reason the row is worth reading. Given the width it wraps
                // rather than shortening; only past three lines is there nothing else to do.
                value.setSingleLine(false);
                value.setMaxLines(3);
                value.setEllipsize(TextUtils.TruncateAt.END);
                stateColumn.addView(type, FeatureGateLabUi.matchWrap());
                stateColumn.addView(value, FeatureGateLabUi.matchWrap());
                stateColumn.addView(state, FeatureGateLabUi.matchWrap());
                LinearLayout.LayoutParams stateParams = stacked
                        ? new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT)
                        : new LinearLayout.LayoutParams(
                                FeatureGateLabUi.dp(context, 116),
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                if (stacked) stateParams.setMargins(0, FeatureGateLabUi.dp(context, 8), 0, 0);
                row.addView(stateColumn, stateParams);

                holder = new RowHolder(title, key, type, value, state);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            FeatureGateCatalog.Entry entry = entries.get(position);
            FeatureGateLabStore.Rule rule = FeatureGateLabStore.rule(entry.manager, entry.key, entry.type);
            holder.title.setText(entry.title);
            holder.key.setText(entry.key);
            holder.type.setText(entry.shortSourceName() + " " + entry.type);
            String shownValue;
            if (rule != null && rule.enabled && FeatureGateLabStore.masterEnabled()) {
                shownValue = "Returns " + rule.value;
            } else if (entry.loaded) {
                shownValue = "Current " + entry.currentValue;
            } else if (rule != null) {
                shownValue = "Saved " + rule.value;
            } else {
                shownValue = "No current value";
            }
            holder.value.setText(shownValue);
            holder.value.setVisibility(entry.loaded || rule != null ? View.VISIBLE : View.GONE);
            convertView.setBackground(SettingsUi.groupedRow(context, position == 0, position == entries.size() - 1));
            // A chosen row is drawn as chosen, and says so to a screen reader further down: a
            // selection you cannot see is a selection you act on by accident.
            boolean chosen = selection.containsKey(entry.identity());
            convertView.setActivated(chosen);
            convertView.setAlpha(!selection.isEmpty() && !chosen ? 0.55f : 1f);

            String state;
            int stateColor;
            if (rule != null && rule.enabled && FeatureGateLabRuntime.isTriggered(entry.manager, entry.key, entry.type)) {
                state = "Getter used";
                stateColor = SettingsUi.accent();
            } else if (rule != null && rule.enabled) {
                state = "Waiting";
                stateColor = FeatureGateLabUi.warningColor(context);
            } else if (rule != null) {
                state = "Override off";
                stateColor = SettingsUi.textSecondary();
            } else {
                state = entry.loaded ? "Loaded" : "Unloaded";
                stateColor = entry.loaded ? SettingsUi.textSecondary() : SettingsUi.textDisabled();
            }
            holder.state.setText(state);
            holder.state.setTextColor(stateColor);
            // The row read out as one sentence. The parts are the gate's own words, and
            // only the last piece is ours, so only that one is a key.
            String spoken = entry.title + ", " + entry.key + ", " + entry.type
                    + ", " + shownValue + ", " + state;
            convertView.setContentDescription(chosen
                    ? L10n.f(getContext(), "%1$s, selected", spoken)
                    : spoken);
            return convertView;
        }
    }

    private static final class RowHolder {
        final TextView title;
        final TextView key;
        final TextView type;
        final TextView value;
        final TextView state;

        RowHolder(TextView title, TextView key, TextView type, TextView value, TextView state) {
            this.title = title;
            this.key = key;
            this.type = type;
            this.value = value;
            this.state = state;
        }
    }

    private static final class SimpleTextWatcher implements TextWatcher {
        private final Runnable changed;

        SimpleTextWatcher(Runnable changed) {
            this.changed = changed;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
        @Override public void afterTextChanged(Editable s) { }
    }
}
