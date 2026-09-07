package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.TikTokActivityHook;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FeedFilterFeedbackTest {
    private static final long START = 1_700_000_000_000L;

    @Before public void reset() {
        FeedFilterFeedback.resetForTests();
    }

    @After public void cleanup() {
        FeedFilterFeedback.resetForTests();
    }

    @Test public void emptyServerResponsesDoNotCountAsFilteredBatches() {
        Map<String, Integer> reasons = reasons("KeywordFilter", 4);
        assertNull(FeedFilterFeedback.recordBatch(0, 0, reasons, START));
        assertNull(FeedFilterFeedback.recordBatch(4, 0, reasons, START + 1));
        assertNull(FeedFilterFeedback.recordBatch(4, 0, reasons, START + 2));
        String notice = FeedFilterFeedback.recordBatch(4, 0, reasons, START + 3);
        assertNotNull(notice);
        assertTrue(notice.contains("3 batches"));
    }

    @Test public void aPartiallyKeptBatchResetsTheRepeatedEmptyWindow() {
        Map<String, Integer> reasons = reasons("CreatorFilter", 2);
        assertNull(FeedFilterFeedback.recordBatch(2, 0, reasons, START));
        assertNull(FeedFilterFeedback.recordBatch(2, 0, reasons, START + 1));
        assertNull(FeedFilterFeedback.recordBatch(2, 1, reasons, START + 2));
        assertNull(FeedFilterFeedback.recordBatch(2, 0, reasons, START + 3));
        assertNull(FeedFilterFeedback.recordBatch(2, 0, reasons, START + 4));
    }

    @Test public void anOldWindowDoesNotCombineWithCurrentBatches() {
        Map<String, Integer> reasons = reasons("RegionFilter", 1);
        assertNull(FeedFilterFeedback.recordBatch(1, 0, reasons, START));
        assertNull(FeedFilterFeedback.recordBatch(1, 0, reasons, START + 1));
        assertNull(FeedFilterFeedback.recordBatch(1, 0, reasons, START + 30_001));
    }

    @Test public void reasonSummaryIsBoundedAndContainsNoItemIdentity() {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        reasons.put("KeywordFilter", 1);
        reasons.put("CreatorFilter", 2);
        reasons.put("PublicationAgeFilter", 3);
        reasons.put("RegionFilter", 4);
        reasons.put("SoundFilter", 5);
        reasons.put("AdsFilter", 6);
        assertNull(FeedFilterFeedback.recordBatch(6, 0, reasons, START));
        assertNull(FeedFilterFeedback.recordBatch(6, 0, reasons, START + 1));
        String notice = FeedFilterFeedback.recordBatch(6, 0, reasons, START + 2);
        assertNotNull(notice);
        assertTrue(notice.contains("Blocked caption words"));
        assertTrue(notice.contains("Other feed filters"));
        assertFalse(notice.contains("aid"));
        assertTrue(notice.split("\\(").length - 1 <= 4);
    }

    @Test public void feedbackActionTargetsTheFeedFilterSettingsPage() {
        try (var owner = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = owner.get();
            Utils.setContext(activity);
            TikTokActivityHook.openFeedFilterSettings();
            Intent intent = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(intent);
            assertTrue(intent.getBooleanExtra("morphe", false));
            assertTrue("morphe_settings".equals(intent.getAction()));
            assertTrue("FEED_FILTER".equals(intent.getStringExtra("morphe_settings_section")));
        }
    }

    private static Map<String, Integer> reasons(String name, int count) {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        reasons.put(name, count);
        return reasons;
    }
}
