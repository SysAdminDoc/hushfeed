/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.comment;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * A comment can be a picture rather than words. TikTok keeps three shapes for that on the
 * model, and the switch has to catch all of them without touching an ordinary comment.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentMediaFilterTest {
    /** Stands in for the comment model, with the fields the filter reads. */
    public static class Comment {
        public String text = "";
        public List<Object> imageList;
        public List<Object> textImageCommentPostItemList;
        public Object stickerStruct;
        public List<Object> replyComments;
        public User user;
    }

    /** Stands in for the profile model, with the two ids the filter compares. */
    public static class User {
        public String uid;
        public String secUid;

        User(String uid, String secUid) {
            this.uid = uid;
            this.secUid = secUid;
        }
    }

    /** Stands in for CommentItemList. */
    public static class ItemList {
        List<Object> items;

        ItemList(List<Object> items) {
            this.items = items;
        }
    }

    private static Comment words(String text) {
        Comment comment = new Comment();
        comment.text = text;
        return comment;
    }

    @Before
    public void setUp() {
        Utils.setContext(RuntimeEnvironment.getApplication());
        Settings.HIDE_COMMENT_MEDIA.save(false);
        Settings.COMMENT_KEYWORD_FILTER.save(false);
        Settings.COMMENT_BLOCKED_KEYWORDS.save("");
        Settings.COMMENT_BLOCKED_USERS.save("");
        CommentTools.signedInUserIdForTests = null;
    }

    @org.junit.After
    public void tearDown() {
        CommentTools.signedInUserIdForTests = null;
    }

    @Test
    public void everyPictureShapeGoesAndTheWordsStay() {
        Settings.HIDE_COMMENT_MEDIA.save(true);

        Comment kept = words("nice one");
        Comment withImage = words("");
        withImage.imageList = new ArrayList<>(Collections.singletonList(new Object()));
        Comment withSticker = words("");
        withSticker.stickerStruct = new Object();
        Comment textOnImage = words("");
        textOnImage.textImageCommentPostItemList = new ArrayList<>(Collections.singletonList(new Object()));

        ItemList page = new ItemList(new ArrayList<>(
                Arrays.asList(kept, withImage, withSticker, textOnImage)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Collections.singletonList(kept), page.items);
    }

    @Test
    public void yourOwnStickersAndImagesStay() {
        // Hiding comments with pictures is about what other people post. Watching your own
        // sticker vanish from a thread you are in reads as the comment having failed to send.
        Settings.HIDE_COMMENT_MEDIA.save(true);
        CommentTools.signedInUserIdForTests = "me-123";

        Comment mine = words("");
        mine.stickerStruct = new Object();
        mine.user = new User("me-123", "sec-me");
        Comment mineBySecUid = words("");
        mineBySecUid.imageList = new ArrayList<>(Collections.singletonList(new Object()));
        mineBySecUid.user = new User(null, "me-123");
        Comment theirs = words("");
        theirs.stickerStruct = new Object();
        theirs.user = new User("someone-else", "sec-them");

        ItemList page = new ItemList(new ArrayList<>(Arrays.asList(mine, mineBySecUid, theirs)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Arrays.asList(mine, mineBySecUid), page.items);
    }

    @Test
    public void withNobodySignedInEveryPictureStillGoes() {
        // The account service answers nothing when signed out or when it has moved, and the
        // filter has to behave as it always did rather than keeping everything.
        Settings.HIDE_COMMENT_MEDIA.save(true);
        CommentTools.signedInUserIdForTests = "";

        Comment withSticker = words("");
        withSticker.stickerStruct = new Object();
        withSticker.user = new User("someone", "sec-someone");
        Comment noUser = words("");
        noUser.imageList = new ArrayList<>(Collections.singletonList(new Object()));

        ItemList page = new ItemList(new ArrayList<>(Arrays.asList(withSticker, noUser)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Collections.emptyList(), page.items);
    }

    @Test
    public void anEmptyListOfImagesIsNotAPicture() {
        Settings.HIDE_COMMENT_MEDIA.save(true);

        Comment kept = words("still words");
        kept.imageList = new ArrayList<>();
        kept.textImageCommentPostItemList = new ArrayList<>();

        ItemList page = new ItemList(new ArrayList<>(Collections.singletonList(kept)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Collections.singletonList(kept), page.items);
    }

    @Test
    public void repliesArePicturesToo() {
        Settings.HIDE_COMMENT_MEDIA.save(true);

        Comment reply = words("");
        reply.stickerStruct = new Object();
        Comment keptReply = words("agreed");
        Comment parent = words("what do you think");
        parent.replyComments = new ArrayList<>(Arrays.asList(reply, keptReply));

        ItemList page = new ItemList(new ArrayList<>(Collections.singletonList(parent)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Collections.singletonList(parent), page.items);
        assertEquals(Collections.singletonList(keptReply), parent.replyComments);
    }

    @Test
    public void theSwitchOffLeavesEveryCommentAlone() {
        Comment withImage = words("");
        withImage.imageList = new ArrayList<>(Collections.singletonList(new Object()));
        List<Object> all = new ArrayList<>(Arrays.asList(words("one"), withImage));

        ItemList page = new ItemList(all);
        CommentTools.onCommentListLoaded(page);

        assertEquals(2, page.items.size());
    }

    @Test
    public void theKeywordFilterAloneLeavesPicturesAlone() {
        // The other way round: words are filtered, pictures are not, because that switch
        // is off. Without the media guard every picture would go with them.
        Settings.COMMENT_KEYWORD_FILTER.save(true);
        Settings.COMMENT_BLOCKED_KEYWORDS.save("spam");
        Settings.HIDE_COMMENT_MEDIA.save(false);

        Comment spam = words("this is spam");
        Comment withImage = words("");
        withImage.imageList = new ArrayList<>(Collections.singletonList(new Object()));
        Comment withSticker = words("");
        withSticker.stickerStruct = new Object();

        ItemList page = new ItemList(new ArrayList<>(Arrays.asList(spam, withImage, withSticker)));
        CommentTools.onCommentListLoaded(page);

        assertEquals(Arrays.asList(withImage, withSticker), page.items);
    }

    @Test
    public void thePictureSwitchWorksWithoutTheKeywordFilter() {
        // The two switches are independent: one on and the other off has to still filter.
        Settings.HIDE_COMMENT_MEDIA.save(true);
        Settings.COMMENT_KEYWORD_FILTER.save(false);
        Settings.COMMENT_BLOCKED_KEYWORDS.save("spam");

        Comment spam = words("this is spam");
        Comment withImage = words("");
        withImage.imageList = new ArrayList<>(Collections.singletonList(new Object()));

        ItemList page = new ItemList(new ArrayList<>(Arrays.asList(spam, withImage)));
        CommentTools.onCommentListLoaded(page);

        // Only the picture goes: the keyword filter is off, so its list is not read.
        assertEquals(Collections.singletonList(spam), page.items);
    }
}
