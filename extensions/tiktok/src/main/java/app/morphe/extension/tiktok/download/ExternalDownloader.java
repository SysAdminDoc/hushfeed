/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.download;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.share.ShareUrlSanitizer;

/**
 * Hands the video's link to another app instead of saving it here.
 *
 * <p>Some people already have a downloader they trust, and would rather the save button
 * opened that with the link filled in. The link is TikTok's own: {@code Aweme.getShareUrl()}
 * kept its name, and when it is empty the handle and the id build the same address.
 */
public final class ExternalDownloader {
    private ExternalDownloader() {}

    /** True when the link went to another app, so nothing here should save anything. */
    public static boolean handOff(Object aweme, Context context) {
        String target = packageName();
        if (target.isEmpty() || aweme == null || context == null) return false;

        String url = shareUrl(aweme);
        if (url == null) {
            Utils.showToastShort(L10n.t(
                    "This video has no link to send, so TikTok's own save runs instead"));
            return false;
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        // The same treatment a shared link gets. TikTok's own link carries the parameters that
        // say who sent it, and handing that to another app is still handing it out.
        send.putExtra(Intent.EXTRA_TEXT, ShareUrlSanitizer.rewriteShareUrl(url));
        send.setPackage(target);
        send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(send);
            return true;
        } catch (ActivityNotFoundException notInstalled) {
            Utils.showToastShort(L10n.f("%1$s isn't installed or doesn't take links", target));
            return false;
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not hand the link to " + target, exception);
            return false;
        }
    }

    /** The app the link goes to, as typed, or empty when the save stays here. */
    static String packageName() {
        String value = Settings.EXTERNAL_DOWNLOADER_PACKAGE.get();
        if (value == null) return "";
        String name = value.trim();
        // An Android package name, and nothing that could be a path or an argument.
        if (name.isEmpty() || !name.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")) {
            return "";
        }
        return name;
    }

    /** TikTok's own link for the video, or one built from the handle and the id. */
    static String shareUrl(Object aweme) {
        String shared = Reflect.string(aweme, "getShareUrl", "shareUrl");
        if (shared != null) return shared;
        Object author = Reflect.property(aweme, "getAuthor", "author");
        String handle = Reflect.string(author, "getUniqueId", "uniqueId");
        String id = Reflect.string(aweme, "getAid", "aid");
        if (handle == null || id == null) return null;
        return "https://www.tiktok.com/@" + handle + "/video/" + id;
    }
}
