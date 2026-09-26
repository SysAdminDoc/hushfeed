/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.settings.preference;

import android.content.Context;
import android.preference.Preference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** The three common settings destinations, reachable without scrolling. */
@SuppressWarnings("deprecation")
public final class SettingsQuickActionsPreference extends Preference {
    public static final String KEY = "hushfeed_quick_routes";
    public static final String ROW_TAG = "hushfeed_quick_routes_row";
    public static final String FEED_TAG = "hushfeed_quick_feed";
    public static final String PRIVACY_TAG = "hushfeed_quick_privacy";
    public static final String SCREEN_TIME_TAG = "hushfeed_quick_screen_time";

    /** A route has no Android dependency besides its icon, so its behavior stays testable. */
    public static final class Action {
        final String title;
        final String tag;
        final SettingsMenuPreference.Icon icon;
        final Runnable onClick;

        public Action(
                String title,
                String tag,
                SettingsMenuPreference.Icon icon,
                Runnable onClick
        ) {
            this.title = title;
            this.tag = tag;
            this.icon = icon;
            this.onClick = onClick;
        }
    }

    private final List<Action> actions;

    public SettingsQuickActionsPreference(Context context, List<Action> actions) {
        super(context);
        this.actions = new ArrayList<>(actions);
        setKey(KEY);
        setPersistent(false);
        setSelectable(false);
        setOrder(-850);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setTag(ROW_TAG);
        boolean stack = context.getResources().getConfiguration().fontScale >= 1.3f
                || context.getResources().getConfiguration().screenWidthDp < 360;
        row.setOrientation(stack ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        for (int index = 0; index < actions.size(); index++) {
            Action action = actions.get(index);
            LinearLayout button = createButton(context, action);
            LinearLayout.LayoutParams params = stack
                    ? new LinearLayout.LayoutParams(-1, -2)
                    : new LinearLayout.LayoutParams(0, -2, 1);
            if (index > 0) {
                if (stack) params.topMargin = SettingsUi.dp(context, 8);
                else params.setMarginStart(SettingsUi.dp(context, 8));
            }
            row.addView(button, params);
        }

        FrameLayout holder = new FrameLayout(context);
        holder.setBackgroundColor(SettingsUi.background());
        holder.setPadding(0, 0, 0, SettingsUi.dp(context, 8));
        holder.addView(row, new FrameLayout.LayoutParams(-1, -2));
        return holder;
    }

    private LinearLayout createButton(Context context, Action action) {
        LinearLayout button = new LinearLayout(context);
        button.setTag(action.tag);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setMinimumWidth(SettingsUi.dp(context, 48));
        button.setMinimumHeight(SettingsUi.dp(context, 60));
        button.setPadding(
                SettingsUi.dp(context, 10), SettingsUi.dp(context, 6),
                SettingsUi.dp(context, 10), SettingsUi.dp(context, 6));
        button.setBackground(SettingsUi.pressAndFocusOver(
                context,
                SettingsUi.RADIUS_CARD,
                SettingsUi.borderedSurface(context, SettingsUi.RADIUS_CARD, true)));
        button.setContentDescription(action.title);
        button.setFocusable(true);
        button.setClickable(true);
        SettingsUi.markAsButton(button);
        button.setOnClickListener(view -> action.onClick.run());

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(SettingsMenuPreference.iconDrawable(context, action.icon));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        button.addView(icon, new LinearLayout.LayoutParams(
                SettingsUi.dp(context, 32), SettingsUi.dp(context, 32)));

        TextView label = SettingsUi.text(
                context, action.title, 14, SettingsUi.textPrimary(),
                android.graphics.Typeface.BOLD);
        label.setSingleLine(false);
        label.setMaxLines(2);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.setMarginStart(SettingsUi.dp(context, 8));
        button.addView(label, labelParams);
        return button;
    }
}
