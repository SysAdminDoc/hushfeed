/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import app.morphe.extension.tiktok.settings.L10n;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shows published changes locally and remembers the last version the reader dismissed. */
public final class ReleaseNotes {
    static final String KEY = "action_release_notes";
    public static final String PREFS_NAME = "hushfeed_release_notes";
    static final String DISMISSED = "dismissed_version";
    private static final Pattern VERSION = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(\\S*)");
    private static final Pattern HEADING = Pattern.compile("(?m)^## (\\d+\\.\\d+\\.\\d+) ");

    private ReleaseNotes() {}

    static boolean pending(Context context, String current) {
        return !text(current, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(DISMISSED, null)).isEmpty();
    }

    static String text(String current, String dismissed) {
        return text(ReleaseNotesData.TEXT, current, dismissed);
    }

    static String text(String source, String current, String dismissed) {
        BigInteger[] installed = version(current);
        if (installed == null) return "";
        BigInteger[] last = version(dismissed);
        if (last != null && compare(last, installed) >= 0) return "";

        Matcher matcher = HEADING.matcher(source);
        StringBuilder visible = new StringBuilder();
        while (matcher.find()) {
            BigInteger[] entry = version(matcher.group(1));
            if (entry == null || compare(entry, installed) > 0
                    || (last == null && compare(entry, installed) != 0)
                    || (last != null && compare(entry, last) <= 0)) continue;
            int start = matcher.start();
            int end = matcher.find() ? matcher.start() : source.length();
            matcher.region(end, source.length());
            if (visible.length() > 0) visible.append("\n\n");
            visible.append(source, start, end);
        }
        if (visible.length() == 0) return "";
        return visible.toString().trim()
                .replaceAll("(?m)^## ", "Hushfeed ")
                .replaceAll("(?m)^\\* (\\*\\*TikTok:\\*\\* )?", "• ")
                .replace("**", "");
    }

    static void show(Context context, String current, Runnable onDismiss) {
        TextView body = new TextView(context);
        body.setText(text(current, context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(DISMISSED, null)));
        body.setTextColor(SettingsUi.textPrimary());
        body.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        body.setTextIsSelectable(true);
        int padding = SettingsUi.dp(context, 22);
        body.setPadding(padding, padding, padding, padding);

        ScrollView scroller = new ScrollView(context);
        scroller.addView(body, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SettingsUi.dialogListHeight(context, 480)));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(L10n.t(context, "What's new"))
                .setView(scroller)
                // Got it is the answer most readers want, so it takes the primary slot. Later
                // keeps the row for another look. The pair used to be Close and Dismiss update,
                // and the neutral slot made the choice that changes state look like the minor one.
                .setPositiveButton(L10n.t(context, "Got it"), (target, which) -> {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putString(DISMISSED, current).apply();
                    onDismiss.run();
                })
                .setNegativeButton(L10n.t(context, "Later"), (target, which) -> target.dismiss())
                .create();
        dialog.show();
        SettingsUi.styleStandardAlertDialog(dialog);
    }

    /**
     * Major, minor and patch, then 1 for a release and 0 for anything after the number, as in
     * 0.61.0-dev.2: a build ahead of a release sorts below it. Read as the release, Got it on
     * a dev build also dismissed the release's own notes when it came.
     */
    private static BigInteger[] version(String value) {
        if (value == null) return null;
        Matcher match = VERSION.matcher(value.trim());
        if (!match.find()) return null;
        return new BigInteger[]{
                new BigInteger(match.group(1)),
                new BigInteger(match.group(2)),
                new BigInteger(match.group(3)),
                match.group(4).isEmpty() ? BigInteger.ONE : BigInteger.ZERO
        };
    }

    /**
     * The newest version the notes would show, for the row to name. The installed version
     * isn't always one of them: a build with no published section of its own shows the ones
     * before it.
     */
    static String newestShown(String text) {
        Matcher heading = SHOWN_HEADING.matcher(text == null ? "" : text);
        return heading.find() ? heading.group(1) : null;
    }

    private static final Pattern SHOWN_HEADING = Pattern.compile("(?m)^Hushfeed (\\d+\\.\\d+\\.\\d+) ");

    private static int compare(BigInteger[] left, BigInteger[] right) {
        for (int index = 0; index < left.length; index++) {
            int result = left[index].compareTo(right[index]);
            if (result != 0) return result;
        }
        return 0;
    }
}
