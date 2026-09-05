package app.morphe.extension.tiktok.share;

import static org.junit.Assert.*;
import app.morphe.extension.tiktok.settings.Settings;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class ShareModelFilterTest {
    public static final class Item {
        private final String key;
        Item(String key) { this.key = key; }
        public String key() { return key; }
    }
    @After public void reset() {
        Settings.SHARE_HIDDEN_ITEMS.save("");
        Settings.HIDE_SHARE_CHANNELS.save(false);
        Settings.HIDE_SHARE_ACTIONS.save(false);
        Settings.HIDE_SHARE_CONTACTS.save(false);
    }
    @Test public void filtersBeforeRenderingWithoutChangingTheBuilderOrUnknownItems() {
        Object copy = new Item("copy"), save = new Item("save"), unknown = new Object();
        List<?> input = Arrays.asList(copy, save, unknown);
        assertSame(input, ShareModelFilter.channels(input));
        Settings.SHARE_HIDDEN_ITEMS.save("Copy link, save video");
        assertEquals(List.of(unknown), ShareModelFilter.channels(input));
        assertEquals(3, input.size());
        Settings.SHARE_HIDDEN_ITEMS.save("");
        assertSame(input, ShareModelFilter.channels(input));
    }
    @Test public void rowsCanBeHiddenIndependently() {
        List<?> input = List.of(new Item("save"));
        Settings.HIDE_SHARE_ACTIONS.save(true);
        assertTrue(ShareModelFilter.actions(input).isEmpty());
        assertSame(input, ShareModelFilter.channels(input));
        assertTrue(ShareModelFilter.contacts(true));
        Settings.HIDE_SHARE_CONTACTS.save(true);
        assertFalse(ShareModelFilter.contacts(true));
        assertFalse(ShareModelFilter.contacts(false));
    }
}
