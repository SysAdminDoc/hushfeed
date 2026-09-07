package app.morphe.extension.tiktok.settings.preference;

import static app.morphe.extension.shared.Utils.isDarkModeEnabled;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Switch;

import androidx.annotation.ColorInt;

public final class SettingsUi {
    public static final @ColorInt int ACCENT = Color.rgb(255, 79, 135);
    public static final @ColorInt int DARK_BACKGROUND = Color.BLACK;
    public static final @ColorInt int DARK_SURFACE = Color.rgb(17, 17, 21);
    public static final @ColorInt int DARK_SURFACE_LIFTED = Color.rgb(27, 27, 33);
    public static final @ColorInt int DARK_BORDER = Color.rgb(53, 53, 62);
    public static final @ColorInt int DARK_DIVIDER = Color.rgb(41, 41, 48);
    public static final @ColorInt int DARK_TEXT_PRIMARY = Color.rgb(245, 245, 247);
    public static final @ColorInt int DARK_TEXT_SECONDARY = Color.rgb(168, 168, 179);
    public static final @ColorInt int DARK_TEXT_DISABLED = Color.argb(255, 109, 109, 118);

    public static final @ColorInt int LIGHT_BACKGROUND = Color.rgb(245, 245, 248);
    public static final @ColorInt int LIGHT_SURFACE = Color.WHITE;
    public static final @ColorInt int LIGHT_SURFACE_LIFTED = Color.argb(255, 250, 250, 250);
    public static final @ColorInt int LIGHT_BORDER = Color.argb(255, 210, 210, 210);
    public static final @ColorInt int LIGHT_DIVIDER = Color.argb(255, 224, 224, 224);
    public static final @ColorInt int LIGHT_TEXT_PRIMARY = Color.rgb(22, 22, 28);
    public static final @ColorInt int LIGHT_TEXT_SECONDARY = Color.rgb(87, 87, 98);
    public static final @ColorInt int LIGHT_TEXT_DISABLED = Color.argb(255, 140, 140, 140);

    public static final int LIGHT_ACCENT = Color.rgb(184, 22, 77);

    private SettingsUi() {
    }

    public static boolean isDarkMode() {
        return isDarkModeEnabled();
    }

    public static @ColorInt int accent() { return isDarkMode() ? ACCENT : LIGHT_ACCENT; }

    /**
     * What a filled chip is painted with. The plain accent is a light pink, and white on it is
     * 3.1:1, under what small text needs. The darker accent carries white in either theme, so
     * a badge and a selected pill use it and stay readable.
     */
    public static @ColorInt int badgeFill() { return LIGHT_ACCENT; }

    /** Text on {@link #badgeFill()}: 6.4:1, the same in both themes. */
    public static @ColorInt int badgeText() { return Color.WHITE; }

    /**
     * TikTok's own red, for the controls this patch draws over the app itself rather than in
     * the settings screen. Those sit on the app's surfaces, not on ours.
     */
    public static final @ColorInt int OVERLAY_ACCENT = Color.rgb(254, 44, 85);

    /** The same red, dark enough to read as text on a white surface (6.4:1 rather than 3.7:1). */
    public static @ColorInt int overlayAccentOn(boolean darkSurface) {
        return darkSurface ? OVERLAY_ACCENT : LIGHT_ACCENT;
    }

    public static void stylePreferenceRow(View view) {
        Context context = view.getContext();
        view.setPaddingRelative(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 18));
        view.setMinimumHeight(dp(context, 84));
        TextView title = view.findViewById(android.R.id.title);
        if (title != null) {
            if (title.getParent() instanceof View && title.getParent() != view) {
                ((View) title.getParent()).setPadding(0, 0, 0, 0);
            }
            title.setTextSize(16);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setTextColor(title.isEnabled() ? textPrimary() : textDisabled());
            title.setSingleLine(false);
            title.setMaxLines(Integer.MAX_VALUE);
            title.setEllipsize(null);
        }
        TextView summary = view.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextSize(14);
            summary.setTextColor(summary.isEnabled() ? textSecondary() : textDisabled());
            summary.setSingleLine(false);
            summary.setMaxLines(Integer.MAX_VALUE);
            summary.setEllipsize(null);
            summary.setLineSpacing(dp(context, 2), 1f);
            summary.setPadding(0, dp(context, 5), 0, 0);
        }
        styleSwitches(view);
    }

    public static void styleSwitches(View view) {
        if (view instanceof Switch) styleSwitch((Switch) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleSwitches(group.getChildAt(i));
        }
    }

    public static void styleSwitch(Switch control) {
        Context context = control.getContext();
        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{-android.R.attr.state_enabled}, switchShape(context, border(), 44, 26, 6));
        track.addState(new int[]{android.R.attr.state_checked}, switchShape(context, accent(), 44, 26, 6));
        track.addState(new int[]{}, switchShape(context, isDarkMode() ? Color.rgb(100, 100, 111) : Color.rgb(116, 116, 127), 44, 26, 6));
        control.setTrackTintList(null);
        control.setThumbTintList(null);
        control.setTrackDrawable(track);
        GradientDrawable thumb = switchShape(context, Color.WHITE, 20, 22, 4);
        thumb.setStroke(dp(context, 1), isDarkMode() ? Color.rgb(225, 225, 230) : Color.rgb(116, 116, 127));
        control.setThumbDrawable(thumb);
        control.setSwitchMinWidth(dp(context, 44));
        control.setThumbTextPadding(0);
        control.setShowText(false);
        control.setSplitTrack(false);
        control.setMinimumHeight(dp(context, 48));
    }

    private static GradientDrawable switchShape(Context context, int color, int width, int height, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, radius));
        shape.setSize(dp(context, width), dp(context, height));
        return shape;
    }

    public static Drawable groupedRow(Context context, boolean first, boolean last) {
        return new RippleDrawable(ColorStateList.valueOf((accent() & 0x00ffffff) | 0x26000000),
                new GroupRowDrawable(context, first, last), new ColorDrawable(Color.WHITE));
    }

    private static final class GroupRowDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean first, last;
        private final float radius, inset;
        GroupRowDrawable(Context context, boolean first, boolean last) {
            this.first = first;
            this.last = last;
            radius = dp(context, 10);
            inset = dp(context, 18);
        }
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float top = bounds.top, bottom = bounds.bottom;
            RectF frame = new RectF(bounds.left + 0.5f, first ? top + 0.5f : top - radius,
                    bounds.right - 0.5f, last ? bottom - 0.5f : bottom + radius);
            canvas.save();
            canvas.clipRect(bounds);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(surface());
            canvas.drawRoundRect(frame, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(border());
            canvas.drawRoundRect(frame, radius, radius, paint);
            if (!last) {
                paint.setColor(divider());
                canvas.drawLine(bounds.left + inset, bottom - 0.5f, bounds.right - inset, bottom - 0.5f, paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static @ColorInt int background() {
        return isDarkMode() ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    public static @ColorInt int surface() {
        return isDarkMode() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    public static @ColorInt int liftedSurface() {
        return isDarkMode() ? DARK_SURFACE_LIFTED : LIGHT_SURFACE_LIFTED;
    }

    public static @ColorInt int border() {
        return isDarkMode() ? DARK_BORDER : LIGHT_BORDER;
    }

    public static @ColorInt int divider() {
        return isDarkMode() ? DARK_DIVIDER : LIGHT_DIVIDER;
    }

    public static @ColorInt int textPrimary() {
        return isDarkMode() ? DARK_TEXT_PRIMARY : LIGHT_TEXT_PRIMARY;
    }

    public static @ColorInt int textSecondary() {
        return isDarkMode() ? DARK_TEXT_SECONDARY : LIGHT_TEXT_SECONDARY;
    }

    public static @ColorInt int textDisabled() {
        return isDarkMode() ? DARK_TEXT_DISABLED : LIGHT_TEXT_DISABLED;
    }

    public static void styleTitleAndSummary(View view) {
        TextView title = view.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(title.isEnabled() ? textPrimary() : textDisabled());
        }

        TextView summary = view.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTextColor(summary.isEnabled() ? textSecondary() : textDisabled());
        }
    }

    public static void styleCategory(View view) {
        TextView title = view.findViewById(android.R.id.title);
        if (title != null) {
            title.setTextColor(accent());
            title.setTextSize(13);
            title.setTypeface(title.getTypeface(), Typeface.BOLD);
        }
    }

    public static TextView text(Context context, String value, float sizeSp, int color, int style) {
        TextView textView = new TextView(context);
        textView.setText(value);
        textView.setIncludeFontPadding(false);
        textView.setTextColor(color);
        textView.setTextSize(sizeSp);
        textView.setTypeface(textView.getTypeface(), style);
        return textView;
    }

    public static GradientDrawable roundedSurface(Context context, int radiusDp, boolean lifted) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(lifted ? liftedSurface() : surface());
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable borderedSurface(Context context, int radiusDp, boolean lifted) {
        GradientDrawable drawable = roundedSurface(context, radiusDp, lifted);
        drawable.setStroke(Math.max(1, dp(context, 1)), border());
        return drawable;
    }

    public static void styleDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            View decorView = window.getDecorView();
            if (decorView != null) {
                decorView.setBackgroundColor(Color.TRANSPARENT);
            }
            constrainDialogWindow(dialog, window);
        }

        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            View content = alertDialog.findViewById(android.R.id.content);
            if (content != null) {
                content.setBackgroundColor(Color.TRANSPARENT);
            }
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL), false);
        }
    }

    public static void styleFramedDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(borderedSurface(dialog.getContext(), 10, true));
            constrainDialogWindow(dialog, window);
        }

        if (dialog instanceof AlertDialog) {
            AlertDialog alertDialog = (AlertDialog) dialog;
            View content = alertDialog.findViewById(android.R.id.content);
            if (content != null) {
                content.setBackgroundColor(Color.TRANSPARENT);
            }
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
            styleActionButton(alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL), false);
        }
    }

    public static void styleStandardAlertDialog(AlertDialog dialog) {
        styleFramedDialog(dialog);

        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }

        ListView list = dialog.getListView();
        boolean radio = list != null && list.getChoiceMode() == ListView.CHOICE_MODE_SINGLE;
        makeDialogPanelsTransparent(window.getDecorView(), dialog.getContext());
        styleDialogText(window.getDecorView(), radio);

        if (list != null) {
            list.setBackgroundColor(Color.TRANSPARENT);
            list.setDivider(new ColorDrawable(divider()));
            list.setDividerHeight(Math.max(1, dp(dialog.getContext(), 1)));
            list.post(() -> {
                styleDialogText(list, radio);
                list.postDelayed(() -> styleDialogText(list, radio), 50);
            });
        }

        styleActionButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), true);
        styleActionButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), false);
        styleActionButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), false);
    }

    public static int dialogListHeight(Context context, int preferredDp) {
        int preferred = dp(context, preferredDp);
        int screenBased = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.38f);
        return Math.min(preferred, Math.max(dp(context, 220), screenBased));
    }

    private static void constrainDialogWindow(Dialog dialog, Window window) {
        int screenWidth = dialog.getContext().getResources().getDisplayMetrics().widthPixels;
        int horizontalMargins = dp(dialog.getContext(), 32);
        int maxWidth = dp(dialog.getContext(), 560);
        int width = Math.min(maxWidth, Math.max(dp(dialog.getContext(), 280), screenWidth - horizontalMargins));
        window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private static void makeDialogPanelsTransparent(View root, Context context) {
        String[] panelNames = {
                "parentPanel",
                "topPanel",
                "contentPanel",
                "buttonPanel",
                "customPanel"
        };
        for (String panelName : panelNames) {
            int id = context.getResources().getIdentifier(panelName, "id", "android");
            if (id == 0) {
                continue;
            }
            View panel = root.findViewById(id);
            if (panel != null) {
                panel.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private static void styleDialogText(View view, boolean radio) {
        if (view instanceof CheckBox) {
            CheckBox checkBox = (CheckBox) view;
            checkBox.setTextColor(textPrimary());
            styleCheckBox(checkBox);
        } else if (view instanceof CheckedTextView) {
            CheckedTextView checkedTextView = (CheckedTextView) view;
            checkedTextView.setTextColor(textPrimary());
            Drawable[] drawables = checkedTextView.getCompoundDrawablesRelative();
            checkedTextView.setCheckMarkDrawable(null);
            checkedTextView.setCompoundDrawablesRelative(
                    new DialogCheckMarkDrawable(checkedTextView.getContext(), radio),
                    drawables[1], drawables[2], drawables[3]);
        } else if (view instanceof Button) {
            ((Button) view).setTextColor(accent());
        } else if (view instanceof TextView) {
            ((TextView) view).setTextColor(textPrimary());
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleDialogText(group.getChildAt(i), radio);
            }
        }
    }

    public static void styleActionButton(Button button, boolean primary) {
        if (button == null) {
            return;
        }
        button.setTextColor(primary ? accent() : textSecondary());
        button.setAllCaps(false);
        button.setTypeface(button.getTypeface(), primary ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static void styleTextAction(TextView button, boolean primary) {
        button.setTextColor(primary ? accent() : textSecondary());
        button.setTypeface(button.getTypeface(), primary ? Typeface.BOLD : Typeface.NORMAL);
    }

    public static void styleEditText(EditText editText) {
        editText.setTextColor(textPrimary());
        editText.setHintTextColor(textSecondary());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            editText.setBackgroundTintList(ColorStateList.valueOf(accent()));
        }
    }

    public static void styleCheckBox(CompoundButton button) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_checked},
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{}
            };
            int[] colors = new int[]{accent(), textDisabled(), textSecondary()};
            button.setButtonTintList(new ColorStateList(states, colors));
        }
    }

    private static final class DialogCheckMarkDrawable extends Drawable {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int intrinsicSize;
        private final float boxSize;
        private final float radius;
        private final boolean radio;
        private boolean checked;

        DialogCheckMarkDrawable(Context context, boolean radio) {
            intrinsicSize = dp(context, 32);
            boxSize = dp(context, 18);
            radius = dp(context, 2);
            this.radio = radio;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(2, dp(context, 2)));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            float centerX = getBounds().exactCenterX();
            float centerY = getBounds().exactCenterY();
            if (radio) {
                float ringRadius = boxSize / 2f - stroke.getStrokeWidth() / 2f;
                stroke.setColor(checked ? accent() : textSecondary());
                canvas.drawCircle(centerX, centerY, ringRadius, stroke);
                if (checked) {
                    fill.setColor(accent());
                    canvas.drawCircle(centerX, centerY, boxSize / 4f, fill);
                }
                return;
            }

            float left = centerX - boxSize / 2f;
            float top = centerY - boxSize / 2f;
            RectF box = new RectF(left, top, left + boxSize, top + boxSize);

            if (checked) {
                fill.setColor(accent());
                canvas.drawRoundRect(box, radius, radius, fill);
                stroke.setColor(Color.WHITE);
                float unit = boxSize / 18f;
                canvas.drawLine(left + 4f * unit, top + 9f * unit,
                        left + 8f * unit, top + 13f * unit, stroke);
                canvas.drawLine(left + 8f * unit, top + 13f * unit,
                        left + 15f * unit, top + 5f * unit, stroke);
            } else {
                stroke.setColor(textSecondary());
                canvas.drawRoundRect(box, radius, radius, stroke);
            }
        }

        @Override
        protected boolean onStateChange(int[] stateSet) {
            boolean nextChecked = false;
            for (int state : stateSet) {
                if (state == android.R.attr.state_checked) {
                    nextChecked = true;
                    break;
                }
            }
            if (checked == nextChecked) {
                return false;
            }
            checked = nextChecked;
            invalidateSelf();
            return true;
        }

        @Override
        public boolean isStateful() {
            return true;
        }

        @Override
        public int getIntrinsicWidth() {
            return intrinsicSize;
        }

        @Override
        public int getIntrinsicHeight() {
            return intrinsicSize;
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            stroke.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            fill.setColorFilter(colorFilter);
            stroke.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
