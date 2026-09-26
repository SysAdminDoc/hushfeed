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
        SCREEN_TIME,
        INBOX,
        SHARE,
        REGION,
        PRIVACY,
        BEHAVIOR,
        LAB,
        DIAGNOSTICS,
        BACKUP,
        NEWS
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

    /** The same icon language for compact routes on the master page. */
    public static Drawable iconDrawable(Context context, Icon icon) {
        return new MenuIconDrawable(context, icon);
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

        TextView title = SettingsUi.text(context, "", 16, SettingsUi.textPrimary(), 1);
        title.setId(android.R.id.title);
        title.setSingleLine(false);
        title.setEllipsize(null);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView summary = SettingsUi.text(context, "", 14, SettingsUi.textSecondary(), 0);
        summary.setId(android.R.id.summary);
        summary.setSingleLine(false);
        summary.setEllipsize(null);
        labels.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        // Start and end rather than left and right: in an RTL layout the icon is on the right
        // and this gap belongs between the icon and the text, not stranded on the far side.
        labelParams.setMarginStart(SettingsUi.dp(context, 14));
        labelParams.setMarginEnd(SettingsUi.dp(context, 10));
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
                    // "changed", not "on". The number is how many settings on that page
                    // are away from their default, and two of them default to on, so
                    // turning one of those off used to read as having turned it on.
                    L10n.f(getContext(), "%1$s changed", activeCount),
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
            android.graphics.drawable.GradientDrawable badgeBackground = SettingsUi.roundedSurface(getContext(), SettingsUi.RADIUS_BADGE, true);
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
        chevronParams.setMarginStart(SettingsUi.dp(getContext(), activeCount > 0 ? 9 : 0));
        accessory.addView(chevron, chevronParams);
        frame.addView(accessory);
    }

    public static final class ChevronDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int enabledColor;
        private final int disabledColor;

        public ChevronDrawable(Context context) {
            setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
            enabledColor = SettingsUi.textSecondary();
            disabledColor = SettingsUi.textDisabled();
            paint.setColor(enabledColor);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(SettingsUi.strokePx(context, 1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public boolean isAutoMirrored() {
            return true;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean enabled = false;
            for (int s : state) {
                if (s == android.R.attr.state_enabled) { enabled = true; break; }
            }
            int color = enabled ? enabledColor : disabledColor;
            if (paint.getColor() != color) {
                paint.setColor(color);
                invalidateSelf();
                return true;
            }
            return false;
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
            line.setColor(icon == Icon.SEARCH ? SettingsUi.textSecondary() : SettingsUi.accent());
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
                case PRIVACY:
                    // A shield with a tick in it.
                    path.reset();
                    path.moveTo(cx, top);
                    path.lineTo(right, top + bounds.height() * 0.08f);
                    path.lineTo(right, cy);
                    path.quadTo(right, bottom - bounds.height() * 0.05f, cx, bottom);
                    path.quadTo(left, bottom - bounds.height() * 0.05f, left, cy);
                    path.lineTo(left, top + bounds.height() * 0.08f);
                    path.close();
                    canvas.drawPath(path, line);
                    canvas.drawLine(cx - bounds.width() * 0.1f, cy, cx - bounds.width() * 0.02f,
                            cy + bounds.height() * 0.08f, line);
                    canvas.drawLine(cx - bounds.width() * 0.02f, cy + bounds.height() * 0.08f,
                            cx + bounds.width() * 0.11f, cy - bounds.height() * 0.09f, line);
                    break;
                case LAYOUT:
                    // A screen with the right column's buttons down its edge and a caption
                    // line at the foot: the feed as it is drawn.
                    canvas.drawRoundRect(new RectF(left, top, right, bottom),
                            bounds.width() * 0.06f, bounds.width() * 0.06f, line);
                    // Inside the frame with room to spare: on the tile's edge the three dots
                    // merged with the border and read as a bite out of the screen.
                    float railX = right - bounds.width() * 0.17f;
                    float railDot = bounds.width() * 0.025f;
                    canvas.drawCircle(railX, cy - bounds.height() * 0.13f, railDot, line);
                    canvas.drawCircle(railX, cy, railDot, line);
                    canvas.drawCircle(railX, cy + bounds.height() * 0.13f, railDot, line);
                    canvas.drawLine(left + bounds.width() * 0.1f, bottom - bounds.height() * 0.1f,
                            cx - bounds.width() * 0.02f, bottom - bounds.height() * 0.1f, line);
                    break;
                case SCREEN_TIME:
                    // A clock face.
                    canvas.drawCircle(cx, cy, bounds.width() * 0.23f, line);
                    canvas.drawLine(cx, cy, cx, cy - bounds.height() * 0.14f, line);
                    canvas.drawLine(cx, cy, cx + bounds.width() * 0.1f, cy + bounds.height() * 0.06f, line);
                    break;
                case BACKUP:
                    // An open box with an arrow rising out of it. Downloads is the arrow going
                    // the other way onto a line, so the two read as a pair.
                    path.reset();
                    path.moveTo(left, cy);
                    path.lineTo(left, bottom);
                    path.lineTo(right, bottom);
                    path.lineTo(right, cy);
                    canvas.drawPath(path, line);
                    canvas.drawLine(cx, top, cx, cy + bounds.height() * 0.1f, line);
                    canvas.drawLine(cx, top, cx - bounds.width() * 0.1f, top + bounds.height() * 0.1f, line);
                    canvas.drawLine(cx, top, cx + bounds.width() * 0.1f, top + bounds.height() * 0.1f, line);
                    break;
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
                case NEWS:
                    // A four-point sparkle: each arm bows in toward the centre.
                    path.reset();
                    path.moveTo(cx, top);
                    path.quadTo(cx, cy, right, cy);
                    path.quadTo(cx, cy, cx, bottom);
                    path.quadTo(cx, cy, left, cy);
                    path.quadTo(cx, cy, cx, top);
                    path.close();
                    canvas.drawPath(path, line);
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
