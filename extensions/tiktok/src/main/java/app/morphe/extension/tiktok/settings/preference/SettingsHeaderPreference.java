/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.settings.preference;

import app.morphe.extension.tiktok.settings.L10n;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.preference.Preference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public final class SettingsHeaderPreference extends Preference {
    /** The product name, which reads the same in every language and is not translated. */
    private static final String BRAND_MARK = "HUSHFEED";

    private enum Kind {
        MASTER,
        SECTION,
        CAPTION
    }

    private final Kind kind;
    private final String heading;
    private final String detail;
    private final Runnable backAction;

    public static SettingsHeaderPreference master(Context context, Runnable backAction) {
        return new SettingsHeaderPreference(
                context,
                Kind.MASTER,
                "Settings",
                null,
                backAction
        );
    }

    public static SettingsHeaderPreference section(Context context, String title, Runnable backAction) {
        return new SettingsHeaderPreference(context, Kind.SECTION, title, null, backAction);
    }

    public static SettingsHeaderPreference caption(Context context, String caption) {
        return new SettingsHeaderPreference(context, Kind.CAPTION, null, caption, null);
    }

    private SettingsHeaderPreference(
            Context context,
            Kind kind,
            String heading,
            String detail,
            Runnable backAction
    ) {
        super(context);
        this.kind = kind;
        this.heading = L10n.t(context, heading);
        this.detail = L10n.t(context, detail);
        this.backAction = backAction;
        setSelectable(false);
        setOrder(kind == Kind.CAPTION ? -900 : -1000);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        if (kind == Kind.MASTER) {
            return createMasterHeader();
        }
        if (kind == Kind.CAPTION) {
            return createCaption();
        }
        return createSectionHeader();
    }

    private View createMasterHeader() {
        LinearLayout header = createHeader(getContext(), heading, backAction);
        TextView subtitle = SettingsUi.text(getContext(), L10n.t(getContext(), "Make TikTok yours."),
                14, SettingsUi.textSecondary(), 0);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = SettingsUi.dp(getContext(), 12);
        header.addView(subtitle, subtitleParams);
        TextView label = SettingsUi.text(getContext(), L10n.t(getContext(), "YOUR EXPERIENCE"),
                11, SettingsUi.textSecondary(), 1);
        label.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = SettingsUi.dp(getContext(), 38);
        labelParams.bottomMargin = SettingsUi.dp(getContext(), 12);
        header.addView(label, labelParams);
        return header;
    }

    private View createSectionHeader() { return createHeader(getContext(), heading, backAction); }

    /** Shared app-owned heading used by settings and the Lab. Parent supplies the 16 dp gutter. */
    public static LinearLayout createHeader(Context context, String title, Runnable onBack) {
        LinearLayout header = new LinearLayout(context);
        header.setTag("metra_page_header");
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(SettingsUi.dp(context, 8), SettingsUi.dp(context, 8), SettingsUi.dp(context, 8), 0);
        header.setBackgroundColor(SettingsUi.background());
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setTag("metra_toolbar");
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(context);
        back.setContentDescription(L10n.t(context, "Back"));
        back.setImageDrawable(new BackDrawable(context));
        back.setOnClickListener(view -> { if (onBack != null) onBack.run(); });
        back.setFocusable(true);
        // The same accent-at-15%-alpha the grouped rows use. divider() is 1.3:1 against the
        // dark surface, which is no press feedback at all.
        back.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(
                        (SettingsUi.accent() & 0x00ffffff) | 0x26000000),
                null,
                SettingsUi.roundedSurface(context, 6, false)));
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(SettingsUi.dp(context, 48), SettingsUi.dp(context, 48));
        // -8 lines the glyph up with the 8dp gutter. Anything more pushes the button's
        // leading edge outside the header, where it is clipped and takes no touches.
        backParams.setMarginStart(SettingsUi.dp(context, -8));
        toolbar.addView(back, backParams);
        TextView brand = SettingsUi.text(context, BRAND_MARK, 12, SettingsUi.accent(), 1);
        brand.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, -2, 1);
        brandParams.setMarginStart(SettingsUi.dp(context, 8));
        toolbar.addView(brand, brandParams);
        header.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = SettingsUi.text(
                context, title, headingSizeSp(context), SettingsUi.textPrimary(), 1);
        heading.setTag("metra_page_title");
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = SettingsUi.dp(context, 24);
        header.addView(heading, titleParams);
        return header;
    }

    private View createCaption() {
        Context context = getContext();
        TextView caption = SettingsUi.text(context, detail, 14, SettingsUi.textSecondary(), 0);
        caption.setLineSpacing(SettingsUi.dp(context, 3), 1f);
        caption.setPadding(SettingsUi.dp(context, 8), SettingsUi.dp(context, 12),
                SettingsUi.dp(context, 8), SettingsUi.dp(context, 32));
        caption.setBackgroundColor(SettingsUi.background());
        return caption;
    }

    /**
     * How large the page title may be at the reader's text scale.
     *
     * <p>40sp against a 2x scale is 80sp of page title: "Kommentare und Uebersetzung" took five
     * lines and 85% of the screen, with the first card of the page below the fold. The title is
     * allowed to grow to about a third again of its own size and no further, so an ordinary
     * scale is unchanged and a large one still leaves the page underneath it.
     */
    public static int headingSizeSp(Context context) {
        float scale = context.getResources().getConfiguration().fontScale;
        if (scale <= 1.3f) return 40;
        return Math.max(24, Math.round(40 * 1.3f / scale));
    }

    public static final class BackDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public BackDrawable(Context context) {
            paint.setColor(SettingsUi.textPrimary());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(SettingsUi.strokePx(context, 2.1f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        /**
         * The header row mirrors, by margin and gravity, and the glyph inside it did not, so an
         * Arabic or Hebrew reader got a left pointing back arrow parked at the right edge.
         */
        @Override
        public boolean isAutoMirrored() {
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            float size = Math.min(getBounds().width(), getBounds().height()) * 0.18f;
            float offset = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -size : size;
            float pointX = centerX - offset;
            canvas.drawLine(pointX, centerY, centerX + offset, centerY, paint);
            canvas.drawLine(pointX, centerY, centerX - offset * 0.1f, centerY - size * 0.9f, paint);
            canvas.drawLine(pointX, centerY, centerX - offset * 0.1f, centerY + size * 0.9f, paint);
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
}
