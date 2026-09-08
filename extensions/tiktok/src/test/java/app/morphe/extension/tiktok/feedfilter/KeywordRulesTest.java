package app.morphe.extension.tiktok.feedfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import app.morphe.extension.shared.Utils;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The blocked word lists take two operators as well as plain phrases. A list is typed by hand
 * into a settings box, so what it does with a line nobody finished matters as much as what it
 * does with a good one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class KeywordRulesTest {
    @Before public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
    }

    private static boolean hides(String list, String text) {
        return KeywordRules.anyMatches(KeywordRules.parse(list), text);
    }

    @Test public void aPlainPhraseStillMeansWhatItAlwaysDid() {
        assertTrue(hides("crypto, giveaway", "free giveaway today"));
        assertTrue("case is still ignored", hides("Crypto", "a CRYPTO video"));
        assertFalse(hides("crypto, giveaway", "a video about cats"));
        assertFalse("an empty list hides nothing", hides("", "anything at all"));
        assertFalse("a list of nothing but separators hides nothing", hides(" , , \\n ", "anything"));
    }

    @Test public void bothPhrasesHaveToBeThereForTheAndRule() {
        assertTrue(hides("\"cat\" & \"dog\"", "my cat and my dog"));
        assertFalse("one of the two was enough", hides("\"cat\" & \"dog\"", "just my cat"));
        assertFalse(hides("\"cat\" & \"dog\"", "just my dog"));
        assertFalse(hides("\"cat\" & \"dog\"", "neither one"));
    }

    @Test public void theSecondPhraseKeepsTheRuleOffForTheAndNotRule() {
        assertTrue(hides("\"cat\" !& \"dog\"", "just my cat"));
        assertFalse("the second phrase did not hold the rule off",
                hides("\"cat\" !& \"dog\"", "my cat and my dog"));
        assertFalse("the first phrase was not needed", hides("\"cat\" !& \"dog\"", "just my dog"));

        // Written the other way round, which is how half the people who use it will type it.
        assertTrue(hides("\"cat\" &! \"dog\"", "just my cat"));
        assertFalse(hides("\"cat\" &! \"dog\"", "my cat and my dog"));
    }

    @Test public void aRuleNobodyFinishedIsLeftOutAndSaidSo() {
        // Matched literally it would hide nothing, and say nothing about why.
        assertFalse(hides("\"cat\" & ", "a cat and a \"cat\" & "));
        assertNotNull("nothing was said about the unfinished rule",
                KeywordRules.problem("\"cat\" & "));
        assertTrue("the refusal does not name the line: " + KeywordRules.problem("\"cat\" & "),
                KeywordRules.problem("\"cat\" & ").contains("\"cat\" &"));

        // And the rest of the list still works, so one bad line does not cost the others.
        assertTrue(hides("crypto\n\"cat\" & ", "a crypto video"));
    }

    @Test public void anAmpersandInAPhraseIsStillAPhrase() {
        assertNull("AT&T was read as a rule nobody finished", KeywordRules.problem("AT&T"));
        assertTrue(hides("AT&T", "an at&t advert"));
    }

    @Test public void aCommaInsideQuotesIsNotASeparator() {
        // Split on it, the two halves are a quote that never closes and a rule with no left
        // hand side, and the entry hides nothing at all.
        List<String> entries = KeywordRules.split("\"hello, world\" & \"goodbye\", crypto");
        assertEquals(2, entries.size());
        assertEquals("\"hello, world\" & \"goodbye\"", entries.get(0));
        assertEquals("crypto", entries.get(1));
        assertTrue(hides("\"hello, world\" & \"goodbye\"", "hello, world and goodbye"));
    }

    @Test public void aNewlineSeparatesTheSameWayACommaDoes() {
        assertTrue(hides("crypto\n\"cat\" & \"dog\"", "my cat and my dog"));
        assertTrue(hides("crypto\n\"cat\" & \"dog\"", "a crypto video"));
    }
}
