/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.blockauthor;

/** The account that posted the video currently on screen. */
public final class VideoAuthor {
    public final String uid;
    public final String secUid;
    public final String displayName;
    /** Aweme id of the video the author was read from, used only for logging. */
    public final String awemeId;

    public VideoAuthor(String uid, String secUid, String displayName, String awemeId) {
        this.uid = uid;
        this.secUid = secUid;
        this.displayName = displayName;
        this.awemeId = awemeId;
    }

    /**
     * The block endpoint accepts either identifier, but rejects a request carrying
     * neither, so a usable author needs at least one.
     */
    public boolean isUsable() {
        return (uid != null && !uid.isEmpty()) || (secUid != null && !secUid.isEmpty());
    }

    /** Stable creator key used by local filtering when TikTok omits the ordinary uid. */
    public String stableId() {
        if (uid != null && !uid.isEmpty()) return uid;
        return secUid;
    }

    public String label() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }
        if (uid != null && !uid.isEmpty()) {
            return uid;
        }
        return "this account";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VideoAuthor)) {
            return false;
        }
        VideoAuthor that = (VideoAuthor) other;
        if (uid != null && that.uid != null) {
            return uid.equals(that.uid);
        }
        return secUid != null && secUid.equals(that.secUid);
    }

    @Override
    public int hashCode() {
        if (uid != null) {
            return uid.hashCode();
        }
        return secUid != null ? secUid.hashCode() : 0;
    }
}
