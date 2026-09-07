package app.morphe.extension.tiktok.blockauthor;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import app.morphe.extension.shared.Utils;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class OverlayControlsTest {
    @Test public void renderAccessibleOverlayControls() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Utils.setContext(activity);
        Bitmap bitmap = Bitmap.createBitmap(304, 88, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(24, 24, 27));
        String[] methods = {"createButton", "createLocalHideButton", "createSoundButton", "createNotInterestedButton"};
        for (int i = 0; i < methods.length; i++) {
            Method factory = BlockAuthorOverlay.class.getDeclaredMethod(methods[i], Activity.class);
            factory.setAccessible(true);
            View view = (View) factory.invoke(null, activity);
            assertNotNull(view.getContentDescription());
            assertTrue(view.hasOnClickListeners());
            int size = View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY);
            view.measure(size, size);
            view.layout(0, 0, 56, 56);
            canvas.save();
            canvas.translate(16 + i * 72, 16);
            view.draw(canvas);
            canvas.restore();
        }
        String directory = System.getProperty("morphe.screenshotDir");
        if (directory != null) {
            File output = new File(directory, "overlay-controls.png");
            assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
        }
        activity.finish();
    }
}
