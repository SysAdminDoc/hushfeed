package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.*;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.tiktok.settings.Settings;
import com.ss.android.ugc.aweme.feed.model.Aweme;
import java.util.List;
import java.util.Locale;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ContentAndSoundFilterTest {
    private final IFilter[] markers = {new ContentMarkerFilters.AiGeneratedFilter(),
            new ContentMarkerFilters.PaidPartnershipFilter(), new ContentMarkerFilters.SeriesFilter(),
            new ContentMarkerFilters.PlaylistFilter(), new ContentMarkerFilters.VerifiedFilter()};
    private final BooleanSetting[] switches = {Settings.HIDE_AI_GENERATED, Settings.HIDE_PAID_PARTNERSHIP,
            Settings.HIDE_SERIES, Settings.HIDE_PLAYLIST_VIDEOS, Settings.HIDE_VERIFIED};

    @Before public void setup() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        for (BooleanSetting setting : switches) setting.save(false);
        Settings.HIDE_BLOCKED_SOUNDS.save(false);
        Settings.BLOCKED_SOUND_IDS.save("");
        Settings.BLOCKED_SOUND_NAMES.save("");
    }

    @Test public void eachMarkerHasAnIndependentSwitchAndMissingDataDoesNotMatch() {
        for (int selected = 0; selected < switches.length; selected++) {
            for (int i = 0; i < switches.length; i++) switches[i].save(i == selected);
            for (int i = 0; i < markers.length; i++) {
                assertEquals(i == selected, markers[i].getEnabled());
                assertFalse(markers[i].getFiltered(null));
                assertFalse(markers[i].getFiltered(new Aweme()));
                assertFalse(markers[i].getFiltered(new Item()));
            }
        }
    }

    @Test public void aiLabelsAndModerationMarkersMatchWithoutRejectingUnmarkedStructs() {
        Item item = new Item();
        item.aigcInfo = new Label(0);
        assertFalse(markers[0].getFiltered(item));
        item.aigcInfo = new Label(1);
        assertTrue(markers[0].getFiltered(item));
        item.aigcInfo = null;
        item.moderationAigcInfo = new Object();
        assertTrue(markers[0].getFiltered(item));
    }

    @Test public void partnershipRequiresAccountsOrAnActualCommerceSignal() {
        Item item = new Item();
        item.brandContentAccounts = List.of();
        item.commerceVideoAuthInfo = new Commerce(false);
        item.commercialVideoInfo = "  ";
        assertFalse(markers[1].getFiltered(item));
        item.brandContentAccounts = List.of("brand");
        assertTrue(markers[1].getFiltered(item));
        item.brandContentAccounts = List.of();
        item.commerceVideoAuthInfo = new Commerce(true);
        assertTrue(markers[1].getFiltered(item));
        item.commerceVideoAuthInfo = new Commerce(false);
        item.commercialVideoInfo = "paid partnership";
        assertTrue(markers[1].getFiltered(item));
    }

    @Test public void seriesAndPlaylistDoNotShareTheirSignals() {
        Item item = new Item();
        item.isPaidContent = true;
        assertTrue(markers[2].getFiltered(item));
        assertFalse(markers[3].getFiltered(item));
        item.isPaidContent = false;
        item.mPaidContentInfo = new Object();
        assertTrue(markers[2].getFiltered(item));
        item.mPaidContentInfo = null;
        item.mixInfo = new Object();
        assertFalse(markers[2].getFiltered(item));
        assertTrue(markers[3].getFiltered(item));
    }

    @Test public void verifiedAccountsMatchNumericCustomAndEnterpriseMarkers() {
        Item item = new Item();
        Author author = new Author();
        item.author = author;
        assertFalse(markers[4].getFiltered(item));
        author.verificationType = 1;
        assertTrue(markers[4].getFiltered(item));
        author.verificationType = 0;
        author.customVerify = " creator ";
        assertTrue(markers[4].getFiltered(item));
        author.customVerify = " ";
        author.enterpriseVerifyReason = "business";
        assertTrue(markers[4].getFiltered(item));
        author.enterpriseVerifyReason = " ";
        assertFalse(markers[4].getFiltered(item));
    }

    @Test public void soundIdsAreExactAndNamesUseLocaleIndependentPhrases() {
        SoundFilter filter = new SoundFilter();
        Item item = new Item();
        Music music = new Music();
        item.music = music;
        Settings.HIDE_BLOCKED_SOUNDS.save(true);
        assertFalse(filter.getEnabled());
        Settings.BLOCKED_SOUND_IDS.save("123, 456");
        assertTrue(filter.getEnabled());
        music.mid = "1234";
        assertFalse(filter.getFiltered(item));
        music.mid = " 123 ";
        assertTrue(filter.getFiltered(item));
        music.mid = null;
        music.id = 456;
        assertTrue(filter.getFiltered(item));
        music.id = 0;
        music.title = "Original SOUND by somebody";
        Settings.BLOCKED_SOUND_NAMES.save(",, original sound, , unrelated");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(filter.getFiltered(item));
        } finally { Locale.setDefault(previous); }
        music.title = "something else";
        assertFalse(filter.getFiltered(item));
        Settings.HIDE_BLOCKED_SOUNDS.save(false);
        assertFalse(filter.getEnabled());
        assertFalse(filter.getFiltered(null));
        assertFalse(filter.getFiltered(new Aweme()));
    }

    @Test public void getterAndFieldMusicShapesAgreeAndEditsTakeEffectOnNextRead() {
        SoundFilter filter = new SoundFilter();
        Aweme getterItem = new Aweme() { public Object getMusic() { return new Object() {
            public String getMusicId() { return "789"; }
            public String getMusicName() { return "Song"; }
        }; } };
        Settings.BLOCKED_SOUND_IDS.save("789");
        assertTrue(filter.getFiltered(getterItem));
        Settings.BLOCKED_SOUND_IDS.save("78");
        assertFalse(filter.getFiltered(getterItem));
        Settings.BLOCKED_SOUND_NAMES.save("SONG");
        assertTrue(filter.getFiltered(getterItem));
        Settings.BLOCKED_SOUND_NAMES.save(" ");
        assertFalse(filter.getFiltered(getterItem));
    }

    private static final class Item extends Aweme {
        public Object aigcInfo, moderationAigcInfo, brandContentAccounts, commerceVideoAuthInfo;
        public String commercialVideoInfo;
        public boolean isPaidContent;
        public Object mPaidContentInfo, mixInfo, author, music;
    }
    private static final class Label {
        private final int value;
        Label(int value) { this.value = value; }
        public int getAIGCLabelType() { return value; }
    }
    private static final class Commerce {
        private final boolean value;
        Commerce(boolean value) { this.value = value; }
        public boolean isCommerce() { return value; }
    }
    private static final class Author {
        public int verificationType;
        public String customVerify, enterpriseVerifyReason;
    }
    private static final class Music {
        public String mid, title;
        public long id;
    }
}
