package app.morphe.extension.tiktok.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ShareSheetToolsTest {
    private Context context;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Utils.setContext(context);
        Settings.SHARE_CONFIRM_SEND.save(false);
    }

    @After public void tearDown() {
        Settings.SHARE_CONFIRM_SEND.save(false);
    }

    @Test public void recycledCellsRestoreTheirOriginalWidthAfterHiding() {
        FrameLayout cell = new FrameLayout(context);
        cell.setLayoutParams(new FrameLayout.LayoutParams(120, 48));

        ShareSheetTools.setCellHidden(cell, true);
        assertEquals(View.GONE, cell.getVisibility());
        assertEquals(0, cell.getLayoutParams().width);

        ShareSheetTools.setCellHidden(cell, false);
        assertEquals(View.VISIBLE, cell.getVisibility());
        assertEquals(120, cell.getLayoutParams().width);

        cell.getLayoutParams().width = 64;
        ShareSheetTools.setCellHidden(cell, true);
        ShareSheetTools.setCellHidden(cell, false);
        assertEquals("the recycled cell returns to its first measured width", 120,
                cell.getLayoutParams().width);
    }

    @Test public void nestedContactTouchesResolveTheLabeledCellAndForwardOnlyTheSecondTap() {
        FrameLayout cell = new FrameLayout(context);
        cell.setContentDescription("  Alice  ");
        FrameLayout child = new FrameLayout(context);
        cell.addView(child);
        AtomicInteger clicks = new AtomicInteger();
        child.setOnClickListener(view -> clicks.incrementAndGet());
        Settings.SHARE_CONFIRM_SEND.save(true);

        assertSame(cell, ShareSheetTools.cellOf(child));
        assertEquals("Alice", ShareSheetTools.labelOf(cell));

        ShareSheetTools.onTap(child);
        assertEquals(0, clicks.get());
        ShareSheetTools.onTap(child);
        assertEquals(1, clicks.get());
        assertTrue(child.isClickable());
    }
}
