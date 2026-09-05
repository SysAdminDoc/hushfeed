package app.morphe.extension.tiktok;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;

/** Renders test-owned Android views without using the desktop display. */
public final class UiCapture {
    private UiCapture() {}
    public static void save(View view, String name) throws Exception {
        String directory = System.getProperty("morphe.screenshotDir");
        if (directory == null) return;
        int width = 480, height = 960;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        File output = new File(directory, name);
        if (!output.getParentFile().isDirectory() && !output.getParentFile().mkdirs()) {
            throw new java.io.IOException("Cannot create screenshot directory");
        }
        try (FileOutputStream stream = new FileOutputStream(output)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new java.io.IOException("Cannot encode screenshot");
            }
        }
        bitmap.recycle();
    }
}
