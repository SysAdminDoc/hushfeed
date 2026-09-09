/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public final class SettingsMenuPreference extends Preference {
    public enum Icon {
        FILTER,
        SEARCH,
        TABS,
        LAYOUT,
        COMMENTS,
        DOWNLOADS,
        PLAYBACK,
        INBOX,
        SHARE,
        REGION,
        BEHAVIOR,
        LAB,
        DIAGNOSTICS
    }

    private static final int ACCESSORY_TAG = 0x4D4D454E;
    private int activeCount;

    public SettingsMenuPreference(
            Context context,
            String title,
            String summary,
            Icon icon,
            int activeCount,
            OnPreferenceClickListener listener
    ) {
        super(context);
        this.activeCount = activeCount;
        setTitle(title);
        setSummary(summary);
        setIcon(new MenuIconDrawable(context, icon));
        setOnPreferenceClickListener(listener);
    }

    /**
     * How many settings on the page behind this row are away from their default.
     *
     * <p>Not final any more. The master menu builds its rows once and keeps them across every
     * trip into a section and back, so a count fixed at build time was the count from whenever
     * the screen was first opened. Changing three switches and pressing back left the old
     * number sitting there for the rest of the session.
     */
    public void setActiveCount(int count) {
        if (activeCount == count) return;
        activeCount = count;
        notifyChanged();
    }

    /** What the badge is showing, or zero for no badge. */
    public int activeCount() {
        return activeCount;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setMinimumHeight(SettingsUi.dp(context, 68));
        row.setPadding(
                SettingsUi.dp(context, 20),
                SettingsUi.dp(context, 8),
                SettingsUi.dp(context, 20),
                SettingsUi.dp(context, 8)
        );

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setId(android.R.id.icon_frame);
        ImageView icon = new ImageView(context);
        icon.setId(android.R.id.icon);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(
                SettingsUi.dp(context, 52),
                SettingsUi.dp(context, 52),
                Gravity.CENTER
        ));
        row.addView(iconFrame, new LinearLayout.LayoutParams(
                SettingsUi.dp(context, 52),
                SettingsUi.dp(context, 52)
        ));

        LinearLayout labels = new LinearLayout(context);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.setOrientation(LinearLayout.VERTICAL);

        TextView title = SettingsUi.text(context, "", 15.5f, SettingsUi.textPrimary(), 1);
        title.setId(android.R.id.title);
        title.setSingleLine(false);
        title.setEllipsize(null);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView summary = SettingsUi.text(context, "", 12.8f, SettingsUi.textSecondary(), 0);
        summary.setId(android.R.id.summary);
        summary.setSingleLine(false);
        summary.setEllipsize(null);
        labels.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.leftMargin = SettingsUi.dp(context, 14);
        labelParams.rightMargin = SettingsUi.dp(context, 10);
        row.addView(labels, labelParams);

        LinearLayout widget = new LinearLayout(context);
        widget.setId(android.R.id.widget_frame);
        widget.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        row.addView(widget, new LinearLayout.LayoutParams(-2, -1));
        return row;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        SettingsUi.styleTitleAndSummary(view);
        view.setMinimumHeight(SettingsUi.dp(getContext(), 68));
        styleText(view);
        styleIcon(view);
        addAccessory(view);
    }

    /**
     * Wrapping, which a long translation needs. The sizes are deliberately not set here: the
     * list adapter settles those after this runs, so a number written here would be read as
     * the one on screen and never be it.
     */
    private void styleText(View view) {
        TextView title = view.findViewById(android.R.id.title);
        if (title != null) {
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(false);
            title.setEllipsize(null);
        }

        TextView summary = view.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setSingleLine(false);
            summary.setEllipsize(null);
        }
    }

    private void styleIcon(View view) {
        ImageView icon = view.findViewById(android.R.id.icon);
        if (icon == null) {
            return;
        }
        int size = SettingsUi.dp(getContext(), 52);
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        params.width = size;
        params.height = size;
        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
        }
        icon.setLayoutParams(params);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void addAccessory(View view) {
        View widget = view.findViewById(android.R.id.widget_frame);
        if (!(widget instanceof ViewGroup)) {
            return;
        }

        ViewGroup frame = (ViewGroup) widget;
        frame.setVisibility(View.VISIBLE);
        View previous = frame.findViewWithTag(ACCESSORY_TAG);
        if (previous != null) {
            frame.removeView(previous);
        }

        LinearLayout accessory = new LinearLayout(getContext());
        accessory.setTag(ACCESSORY_TAG);
        accessory.setGravity(Gravity.CENTER_VERTICAL);
        accessory.setOrientation(LinearLayout.HORIZONTAL);

        if (activeCount > 0) {
            TextView badge = SettingsUi.text(
                    getContext(),
                    L10n.f(getContext(), "%1$s on", activeCount),
                    12,
                    SettingsUi.badgeText(),
                    1
            );
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(
                    SettingsUi.dp(getContext(), 9),
                    SettingsUi.dp(getContext(), 5),
                    SettingsUi.dp(getContext(), 9),
                    SettingsUi.dp(getContext(), 5)
            );
            android.graphics.drawable.GradientDrawable badgeBackground = SettingsUi.roundedSurface(getContext(), 4, true);
            badgeBackground.setColor(SettingsUi.badgeFill());
            badge.setBackground(badgeBackground);
            accessory.addView(badge);
        }

        ImageView chevron = new ImageView(getContext());
        chevron.setImageDrawable(new ChevronDrawable(getContext()));
        // Decorative, like the one the list adapter adds to every other row.
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                SettingsUi.dp(getContext(), 18),
                SettingsUi.dp(getContext(), 18)
        );
        chevronParams.leftMargin = SettingsUi.dp(getContext(), activeCount > 0 ? 9 : 0);
        accessory.addView(chevron, chevronParams);
        frame.addView(accessory);
    }

    public static final class ChevronDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChevronDrawable(Context context) {
            // For the one call site that hands this to a TextView rather than an ImageView:
            // FeatureGateDetailFragment builds a spinner row and hangs the chevron off it as a
            // compound drawable, and an adapter's row is unattached, so it has no direction of
            // its own to pass on and the arrow went out pointing away from the text an Arabic
            // reader was reading. An ImageView is a different story: setImageDrawable sets the
            // direction from the view there and then, so for those sites this line is
            // overwritten immediately and the direction arrives when the view resolves.
            setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
            paint.setColor(SettingsUi.textSecondary());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(SettingsUi.strokePx(context, 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        /** The row it sits at the end of mirrors; pointing into the text is what it did before. */
        @Override
        public boolean isAutoMirrored() {
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            float size = Math.min(getBounds().width(), getBounds().height()) * 0.22f;
            float offset = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -size : size;
            canvas.drawLine(centerX - offset, centerY - size, centerX + offset, centerY, paint);
            canvas.drawLine(centerX + offset, centerY, centerX - offset, centerY + size, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private static final class MenuIconDrawable extends Drawable {
        private final Icon icon;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        MenuIconDrawable(Context context, Icon icon) {
            this.icon = icon;
            fill.setColor(SettingsUi.liftedSurface());
            border.setColor(SettingsUi.border());
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(SettingsUi.strokePx(context, 1f));
            line.setColor(SettingsUi.textSecondary());
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(SettingsUi.strokePx(context, 1.8f));
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float radius = bounds.width() * 0.20f;
            canvas.drawRoundRect(bounds, radius, radius, fill);
            canvas.drawRoundRect(bounds, radius, radius, border);

            float left = bounds.left + bounds.width() * 0.27f;
            float right = bounds.right - bounds.width() * 0.27f;
            float top = bounds.top + bounds.height() * 0.27f;
            float bottom = bounds.bottom - bounds.height() * 0.27f;
            float cx = bounds.centerX();
            float cy = bounds.centerY();

            switch (icon) {
                case FILTER:
                    path.reset();
                    path.moveTo(left, top);
                    path.lineTo(right, top);
                    path.lineTo(cx + bounds.width() * 0.06f, cy);
                    path.lineTo(cx + bounds.width() * 0.06f, bottom);
                    path.lineTo(cx - bounds.width() * 0.06f, bottom + bounds.height() * 0.05f);
                    path.lineTo(cx - bounds.width() * 0.06f, cy);
                    path.close();
                    canvas.drawPath(path, line);
                    break;
                case SEARCH:
                    canvas.drawCircle(cx - bounds.width() * 0.05f, cy - bounds.height() * 0.05f,
                            bounds.width() * 0.18f, line);
                    canvas.drawLine(cx + bounds.width() * 0.08f, cy + bounds.height() * 0.08f,
                            right, bottom, line);
                    break;
                case TABS:
                    float gap = bounds.width() * 0.05f;
                    canvas.drawRoundRect(new RectF(left, top, cx - gap, cy - gap), 1, 1, line);
                    canvas.drawRoundRect(new RectF(cx + gap, top, right, cy - gap), 1, 1, line);
                    canvas.drawRoundRect(new RectF(left, cy + gap, cx - gap, bottom), 1, 1, line);
                    canvas.drawRoundRect(new RectF(cx + gap, cy + gap, right, bottom), 1, 1, line);
                    break;
                case PLAYBACK:
                    path.reset();
                    path.moveTo(left + bounds.width() * 0.06f, top);
                    path.lineTo(right, cy);
                    path.lineTo(left + bounds.width() * 0.06f, bottom);
                    path.close();
                    canvas.drawPath(path, line);
                    break;
                case INBOX:
                    path.reset();
                    path.moveTo(left, cy);
                    path.lineTo(left + bounds.width() * 0.07f, top);
                    path.lineTo(right - bounds.width() * 0.07f, top);
                    path.lineTo(right, cy);
                    path.lineTo(right, bottom);
                    path.lineTo(left, bottom);
                    path.close();
                    canvas.drawPath(path, line);
                    path.reset();
                    path.moveTo(left, cy);
                    path.lineTo(cx - bounds.width() * 0.09f, cy);
                    path.lineTo(cx - bounds.width() * 0.05f, cy + bounds.width() * 0.09f);
                    path.lineTo(cx + bounds.width() * 0.05f, cy + bounds.width() * 0.09f);
                    path.lineTo(cx + bounds.width() * 0.09f, cy);
                    path.lineTo(right, cy);
                    canvas.drawPath(path, line);
                    break;
                case SHARE:
                    path.reset();
                    path.moveTo(cx, top + bounds.width() * 0.08f);
                    path.lineTo(left, top + bounds.width() * 0.08f);
                    path.lineTo(left, bottom);
                    path.lineTo(right - bounds.width() * 0.08f, bottom);
                    path.lineTo(right - bounds.width() * 0.08f, cy);
                    canvas.drawPath(path, line);
                    canvas.drawLine(cx, cy, right, top, line);
                    canvas.drawLine(cx + bounds.width() * 0.04f, top, right, top, line);
                    canvas.drawLine(right, top, right, cy - bounds.width() * 0.04f, line);
                    break;
                case COMMENTS:
                    path.reset();
                    path.moveTo(left, top);
                    path.lineTo(right, top);
                    path.lineTo(right, bottom - bounds.height() * 0.08f);
                    path.lineTo(cx, bottom - bounds.height() * 0.08f);
                    path.lineTo(left + bounds.width() * 0.06f, bottom);
                    path.lineTo(left + bounds.width() * 0.08f, bottom - bounds.height() * 0.08f);
                    path.lineTo(left, bottom - bounds.height() * 0.08f);
                    path.close();
                    canvas.drawPath(path, line);
                    break;
                case DOWNLOADS:
                    canvas.drawLine(cx, top, cx, cy + bounds.height() * 0.05f, line);
                    canvas.drawLine(cx, cy + bounds.height() * 0.05f, cx - bounds.width() * 0.1f, cy - bounds.height() * 0.05f, line);
                    canvas.drawLine(cx, cy + bounds.height() * 0.05f, cx + bounds.width() * 0.1f, cy - bounds.height() * 0.05f, line);
                    canvas.drawLine(left, bottom, right, bottom, line);
                    break;
                case REGION:
                    canvas.drawCircle(cx, cy, bounds.width() * 0.23f, line);
                    canvas.drawLine(left, cy, right, cy, line);
                    canvas.drawOval(new RectF(cx - bounds.width() * 0.1f, top, cx + bounds.width() * 0.1f, bottom), line);
                    break;
                case LAYOUT:
                case BEHAVIOR:
                    canvas.drawLine(left, top + bounds.height() * 0.05f, right, top + bounds.height() * 0.05f, line);
                    canvas.drawLine(left, cy, right, cy, line);
                    canvas.drawLine(left, bottom - bounds.height() * 0.05f, right, bottom - bounds.height() * 0.05f, line);
                    canvas.drawCircle(cx + bounds.width() * 0.09f, top + bounds.height() * 0.05f, bounds.width() * 0.045f, line);
                    canvas.drawCircle(cx - bounds.width() * 0.08f, cy, bounds.width() * 0.045f, line);
                    canvas.drawCircle(cx + bounds.width() * 0.02f, bottom - bounds.height() * 0.05f, bounds.width() * 0.045f, line);
                    break;
                case LAB:
                    path.reset();
                    path.moveTo(cx - bounds.width() * 0.08f, top);
                    path.lineTo(cx - bounds.width() * 0.08f, cy - bounds.height() * 0.04f);
                    path.lineTo(left, bottom);
                    path.lineTo(right, bottom);
                    path.lineTo(cx + bounds.width() * 0.08f, cy - bounds.height() * 0.04f);
                    path.lineTo(cx + bounds.width() * 0.08f, top);
                    canvas.drawPath(path, line);
                    canvas.drawLine(left + bounds.width() * 0.08f, cy + bounds.height() * 0.09f,
                            right - bounds.width() * 0.08f, cy + bounds.height() * 0.09f, line);
                    break;
                case DIAGNOSTICS:
                    canvas.drawCircle(cx, cy, bounds.width() * 0.16f, line);
                    canvas.drawLine(cx, top, cx, cy - bounds.height() * 0.16f, line);
                    canvas.drawLine(cx, cy + bounds.height() * 0.16f, cx, bottom, line);
                    canvas.drawLine(left, cy, cx - bounds.width() * 0.16f, cy, line);
                    canvas.drawLine(cx + bounds.width() * 0.16f, cy, right, cy, line);
                    break;
            }
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            border.setAlpha(alpha);
            line.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            border.setColorFilter(colorFilter);
            line.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 40;
        }

        @Override
        public int getIntrinsicHeight() {
            return 40;
        }
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(L10n.t(getContext(), title));
    }

    @Override
    public void setSummary(CharSequence summary) {
        super.setSummary(L10n.t(getContext(), summary));
    }
}
