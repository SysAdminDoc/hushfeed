/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */

package app.morphe.extension.tiktok.feedfilter;

import android.app.Activity;
import android.app.AlertDialog;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.TikTokActivityHook;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explains repeated batches that filtering removed completely without exposing item data. */
final class FeedFilterFeedback {
    private static final int NOTICE_AFTER_BATCHES = 3;
    private static final int MAX_REASON_LINES = 4;
    private static final long WINDOW_MS = 30_000L;
    private static final long NOTICE_COOLDOWN_MS = 60_000L;
    private static final String OTHER_REASON = "Other feed filters";
    private static final long NO_WINDOW = Long.MIN_VALUE;

    private static final Object LOCK = new Object();
    private static final Map<String, Integer> reasons = new LinkedHashMap<>();
    private static int filteredBatches;
    private static long windowStartedAt = NO_WINDOW;
    private static long lastNoticeAt = Long.MIN_VALUE;
    private static boolean noticePending;

    private FeedFilterFeedback() {
    }

    /** Records one completed feed-list filtering pass and schedules a notice when needed. */
    static void onBatchResult(int before, int after, Map<String, Integer> batchReasons, long nowMs) {
        String message = recordBatch(before, after, batchReasons, nowMs);
        if (message == null) return;

        Utils.runOnMainThread(() -> showNotice(message));
    }

    /** Returns the next notice text, or null when this pass should stay quiet. */
    static String recordBatch(int before, int after, Map<String, Integer> batchReasons, long nowMs) {
        synchronized (LOCK) {
            boolean allFiltered = before > 0 && after == 0;
            if (!allFiltered) {
                resetWindowLocked();
                return null;
            }

            if (windowStartedAt == NO_WINDOW || nowMs < windowStartedAt
                    || nowMs - windowStartedAt > WINDOW_MS) {
                resetWindowLocked();
                windowStartedAt = nowMs;
            }
            filteredBatches++;
            addReasonsLocked(batchReasons);
            if (filteredBatches < NOTICE_AFTER_BATCHES
                    || noticePending
                    || (lastNoticeAt != Long.MIN_VALUE
                    && (nowMs < lastNoticeAt || nowMs - lastNoticeAt < NOTICE_COOLDOWN_MS))) {
                return null;
            }

            String message = formatMessageLocked();
            lastNoticeAt = nowMs;
            noticePending = true;
            resetWindowLocked();
            return message;
        }
    }

    private static void addReasonsLocked(Map<String, Integer> batchReasons) {
        if (batchReasons == null || batchReasons.isEmpty()) {
            addReasonLocked(OTHER_REASON, 1);
            return;
        }
        for (Map.Entry<String, Integer> entry : batchReasons.entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count > 0) addReasonLocked(reasonLabel(entry.getKey()), count);
        }
    }

    private static void addReasonLocked(String label, int count) {
        Integer existing = reasons.get(label);
        if (existing != null) {
            reasons.put(label, existing + count);
            return;
        }
        if (reasons.size() >= MAX_REASON_LINES - 1) {
            Integer other = reasons.get(OTHER_REASON);
            reasons.put(OTHER_REASON, other == null ? count : other + count);
            return;
        }
        reasons.put(label, count);
    }

    private static String formatMessageLocked() {
        List<Map.Entry<String, Integer>> ordered = new ArrayList<>(reasons.entrySet());
        // Most matches first, then by label. List.sort, Comparator.comparingInt, reversed and
        // thenComparing are all API 24 and D8 leaves them as stubs that throw, which would
        // reach TikTok's own frame from here on Android 6.
        Collections.sort(ordered, (left, right) -> {
            int byCount = right.getValue().compareTo(left.getValue());
            return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
        });
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, Integer> entry : ordered) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(L10n.t(entry.getKey())).append(" (").append(entry.getValue()).append(")");
        }
        if (summary.length() == 0) summary.append(L10n.t(OTHER_REASON));
        // One batch is the common case the first time this shows, and "to 1 batches" is not a
        // sentence.
        if (filteredBatches == 1) {
            return L10n.f(
                    "No videos remained after Hushfeed applied your feed filters to one batch. Most matches: %1$s.",
                    summary.toString()
            );
        }
        return L10n.f(
                "No videos remained after Hushfeed applied your feed filters to %1$d batches. Most matches: %2$s.",
                filteredBatches,
                summary.toString()
        );
    }

    private static String reasonLabel(String reason) {
        if ("AdsFilter".equals(reason)) return "Ads";
        if ("LiveFilter".equals(reason)) return "LIVE videos";
        if ("StoryFilter".equals(reason)) return "Stories";
        if ("ImageVideoFilter".equals(reason)) return "Photo and image posts";
        if ("ShopFilter".equals(reason)) return "Shop posts";
        if ("SoundFilter".equals(reason)) return "Blocked sounds";
        if ("PaidPartnershipFilter".equals(reason)) return "Paid partnerships";
        if ("AiGeneratedFilter".equals(reason)) return "AI-generated posts";
        if ("VerifiedFilter".equals(reason)) return "Verified creators";
        if ("SeriesFilter".equals(reason)) return "Series posts";
        if ("PlaylistFilter".equals(reason)) return "Playlist posts";
        if ("InsertedCardFilter".equals(reason)) return "Inserted cards";
        if ("SeenVideoFilter".equals(reason)) return "Seen videos";
        if ("KeywordFilter".equals(reason)) return "Blocked caption words";
        if ("CreatorFilter".equals(reason)) return "Hidden creators";
        if ("PromotionalMusicFilter".equals(reason)) return "Promotional music";
        if ("LiveReplayFilter".equals(reason)) return "LIVE replays";
        if ("RegionFilter".equals(reason)) return "Region rules";
        if ("PublicationAgeFilter".equals(reason)) return "Publication age";
        if ("QualityFilter".equals(reason)) return "Video quality";
        return OTHER_REASON;
    }

    private static void showNotice(String message) {
        synchronized (LOCK) {
            noticePending = false;
        }
        Activity activity = Utils.getActivity();
        if (activity == null || activity.isFinishing()) {
            Utils.showToastLong(message);
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(L10n.t(activity, "Hushfeed filtered this batch"))
                .setMessage(message)
                .setPositiveButton(L10n.t(activity, "Open feed filter settings"),
                        (ignored, which) -> TikTokActivityHook.openFeedFilterSettings())
                .setNegativeButton(L10n.t(activity, "Dismiss"), null)
                .create();
        dialog.setOnShowListener(ignored -> SettingsUi.styleStandardAlertDialog(dialog));
        dialog.show();
    }

    private static void resetWindowLocked() {
        filteredBatches = 0;
        windowStartedAt = NO_WINDOW;
        reasons.clear();
    }

    static void resetForTests() {
        synchronized (LOCK) {
            resetWindowLocked();
            lastNoticeAt = Long.MIN_VALUE;
            noticePending = false;
        }
    }
}
