/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.download;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.SettingsStatus;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Saves a profile picture from a long press on the avatar.
 *
 * TikTok never puts the full size avatar on screen, so the URL comes from the profile the app
 * last loaded rather than from the view. `UserResponse` is the profile fetch, and its user is
 * recorded as it is read; the avatar on screen belongs to whichever profile was loaded most
 * recently, which is the one the page is showing.
 */
@SuppressWarnings("unused")
public final class ProfileAvatarSaver {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    /**
     * The avatar sizes TikTok carries, largest first. 300 and "larger" are the full size
     * uploads; the rest are display crops and only stand in when the big ones are missing.
     */
    private static final String[][] SIZES = {
            {"getAvatar300", "avatar300"},
            {"getAvatarLarger", "avatarLarger"},
            {"getAvatarMedium", "avatarMedium"},
            {"getAvatar168", "avatar168"},
            {"getAvatarThumb", "avatarThumb"},
    };

    private static volatile Object profileUser;

    private ProfileAvatarSaver() {
    }

    /**
     * Called with the profile fetch response from inside its own getUser, so the field is read
     * directly: going through the getter would call this again, and again, until the stack ran
     * out. The overflow is swallowed by the reflection helper, so it costs time rather than
     * crashing, which is exactly why it would not have shown up.
     */
    public static void recordProfileResponse(Object response) {
        Object user = Reflect.readField(response, "user");
        if (user != null) profileUser = user;
    }

    /**
     * Called with the profile header's avatar view as it is bound. A view holds one long click
     * listener, so this only takes it when the feature is on: with the switch off TikTok's own
     * long press is left alone. Turning the switch on takes effect the next time a profile is
     * opened.
     */
    public static void attachAvatar(View view) {
        if (view == null || !enabled()) return;
        try {
            view.setOnLongClickListener(anchor -> {
                if (!enabled()) return false;
                save(anchor, profileUser);
                return true;
            });
        } catch (RuntimeException exception) {
            Logger.printException(() -> "Could not attach the profile picture save", exception);
        }
    }

    /**
     * Whether the profile that was loaded last is the one whose avatar was pressed. There is one
     * recorded profile and a header can be shown again from cache without fetching, so the handle
     * on screen is the check: a follower list visited in between would otherwise save the wrong
     * person's picture under their name.
     *
     * @return false only when a handle is on screen and it is somebody else's.
     */
    static boolean matchesProfileOnScreen(View avatar, Object user) {
        String handle = Reflect.string(user, "getUniqueId", "uniqueId");
        if (handle == null || handle.trim().isEmpty()) return true;
        View root = avatar;
        for (int step = 0; step < 8 && root.getParent() instanceof View; step++) {
            root = (View) root.getParent();
        }
        Boolean shown = findHandle(root, "@" + handle.trim());
        return shown == null || shown;
    }

    /** null when no handle is on screen at all, true when this one is, false when another is. */
    private static Boolean findHandle(View view, String wanted) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            String value = text == null ? "" : text.toString().trim();
            if (value.startsWith("@") && !value.contains(" ")) return wanted.equals(value);
            return null;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        Boolean answer = null;
        for (int index = 0; index < group.getChildCount(); index++) {
            Boolean found = findHandle(group.getChildAt(index), wanted);
            if (Boolean.TRUE.equals(found)) return true;
            if (found != null) answer = false;
        }
        return answer;
    }

    /** What the last profile fetch carried, which is the profile the page is showing. */
    static Object recordedProfileUser() {
        return profileUser;
    }

    static boolean enabled() {
        return SettingsStatus.advancedDownloadsEnabled && Settings.SAVE_PROFILE_PICTURE.get();
    }

    /** The addresses for the largest avatar the profile carries, best first. */
    static List<String> avatarUrls(Object user) {
        for (String[] size : SIZES) {
            Object address = Reflect.property(user, size[0], size[1]);
            List<String> found = VideoDownloads.urls(address);
            if (!found.isEmpty()) return found;
        }
        return Collections.emptyList();
    }

    /** The file the picture is saved as, named after the account it belongs to. */
    static String avatarName(Object user) {
        String handle = Reflect.string(user, "getUniqueId", "uniqueId");
        if (handle == null || handle.trim().isEmpty()) {
            handle = Reflect.string(user, "getNickname", "nickname");
        }
        if (handle == null || handle.trim().isEmpty()) handle = "profile";
        return DownloadFilenameFormatter.formatProfilePictureName(handle);
    }

    static void save(View avatar, Object user) {
        if (avatar == null) return;
        Context context = avatar.getContext();
        if (context == null) return;
        if (user == null || !matchesProfileOnScreen(avatar, user)) {
            Utils.showToastShort(L10n.t("Open the profile again and try once more"));
            return;
        }
        List<String> urls = avatarUrls(user);
        if (urls.isEmpty()) {
            Utils.showToastShort(L10n.t("This profile picture isn't available to save"));
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23 && android.os.Build.VERSION.SDK_INT < 29
                && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;

        Context app = context.getApplicationContext();
        String name = avatarName(user);
        String path = DownloadsPatch.getPhotoDownloadPath();
        if (!RUNNING.compareAndSet(false, true)) {
            Utils.showToastShort(L10n.t("Still saving the last one"));
            return;
        }
        WORKER.execute(() -> {
            File temp = null;
            try {
                temp = File.createTempFile("profile-picture-", ".tmp", app.getCacheDir());
                String extension = RemoteMedia.fetch(urls, temp, true);
                String mime = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
                String saved = name.substring(0, name.lastIndexOf('.') + 1) + extension;
                MediaFileWriter.publish(app, temp, saved, mime, path, false);
                Utils.showToastShort(L10n.f("Profile picture saved to %1$s", path));
            } catch (IOException | RuntimeException exception) {
                Logger.printException(() -> "Profile picture download failed", exception);
                Utils.showToastLong(L10n.t("The profile picture couldn't be saved."));
            } finally {
                if (temp != null && !temp.delete()) {
                    Logger.printInfo(() -> "Could not remove profile picture temporary file");
                }
                RUNNING.set(false);
            }
        });
    }
}
