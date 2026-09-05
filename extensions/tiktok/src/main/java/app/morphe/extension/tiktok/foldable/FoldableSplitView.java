package app.morphe.extension.tiktok.foldable;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

public final class FoldableSplitView {
    private FoldableSplitView() { }

    public static boolean shouldForce(Activity activity, Configuration configuration) {
        if (!Settings.FOLDABLE_SPLIT_VIEW.get()) return false;
        if (activity == null) activity = Utils.getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        try {
            if (Build.VERSION.SDK_INT >= 24 && (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode())) return false;
            int width = configuration == null ? 0 : configuration.screenWidthDp;
            if (width <= 0 && Build.VERSION.SDK_INT >= 30) {
                float density = activity.getResources().getDisplayMetrics().density;
                if (density > 0) width = Math.round(activity.getWindowManager().getCurrentWindowMetrics().getBounds().width() / density);
            }
            if (width <= 0) width = activity.getResources().getConfiguration().screenWidthDp;
            int threshold = Math.max(320, Math.min(1600, Settings.FOLDABLE_SPLIT_VIEW_MIN_WIDTH_DP.get()));
            return width >= threshold;
        } catch (RuntimeException error) {
            Logger.printException(() -> "Could not determine the comment panel width", error);
            return false;
        }
    }

    public static boolean shouldForceContainer() { return shouldForce(null, null); }
}
