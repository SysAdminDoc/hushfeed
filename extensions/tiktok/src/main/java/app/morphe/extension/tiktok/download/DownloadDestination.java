/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.download;

import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import app.morphe.extension.tiktok.settings.L10n;

import java.util.Locale;

public final class DownloadDestination {
    public enum Kind {
        VIDEO,
        PHOTO,
        STICKER
    }

    public static final String DEFAULT_PATH = "DCIM/TikTok";

    private DownloadDestination() {
    }

    public static String validate(String path, Kind kind) {
        String normalized = normalize(path);
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        L10n.t("The destination contains an invalid folder name"));
            }
        }

        String root = canonicalRoot(segments[0]);
        if (!isAllowedRoot(root, kind)) {
            throw new IllegalArgumentException(L10n.f(
                    "%1$s destinations must start with %2$s",
                    kindLabel(kind), allowedRoots(kind)));
        }

        segments[0] = root;
        return join(segments);
    }

    public static String resolve(String path, Kind kind) {
        try {
            return validate(path, kind);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_PATH;
        }
    }

    public static String allowedRoots(Kind kind) {
        switch (kind) {
            case VIDEO:
                return list("DCIM", "Movies", "Download", "Documents");
            case PHOTO:
                return list("DCIM", "Pictures", "Download", "Documents");
            case STICKER:
                return list("DCIM", "Download", "Documents");
            default:
                throw new IllegalArgumentException("Unknown destination kind");
        }
    }

    public static Uri collectionUri(String path, boolean video) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        String root = firstSegment(path);
        if ("Download".equals(root) || "Documents".equals(root)) {
            return MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }

        return video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    }

    private static String normalize(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        String storagePrefix = "/storage/emulated/0/";
        if (normalized.startsWith(storagePrefix)) {
            normalized = normalized.substring(storagePrefix.length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.isEmpty()) {
            return DEFAULT_PATH;
        }
        return normalized;
    }

    private static String canonicalRoot(String value) {
        String root = value.toLowerCase(Locale.ROOT);
        switch (root) {
            case "dcim":
                return "DCIM";
            case "movie":
            case "movies":
            case "video":
            case "videos":
                return "Movies";
            case "picture":
            case "pictures":
            case "photo":
            case "photos":
                return "Pictures";
            case "download":
            case "downloads":
                return "Download";
            case "document":
            case "documents":
                return "Documents";
            default:
                return value;
        }
    }

    private static boolean isAllowedRoot(String root, Kind kind) {
        if ("DCIM".equals(root) || "Download".equals(root) || "Documents".equals(root)) {
            return true;
        }
        if (kind == Kind.VIDEO) {
            return "Movies".equals(root);
        }
        if (kind == Kind.PHOTO) {
            return "Pictures".equals(root);
        }
        return false;
    }

    private static String firstSegment(String path) {
        String normalized = normalize(path);
        int slash = normalized.indexOf('/');
        return canonicalRoot(slash < 0 ? normalized : normalized.substring(0, slash));
    }

    /**
     * The folder names joined the way the reader's language joins a list. The names
     * themselves are what Android calls those folders, so they stay as they are; the word
     * between the last two is the part that was English inside a translated sentence.
     */
    private static String list(String... names) {
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < names.length; index++) {
            if (index == names.length - 1 && index > 0) {
                out.append(' ').append(L10n.t("or")).append(' ');
            } else if (index > 0) {
                out.append(", ");
            }
            out.append(names[index]);
        }
        return out.toString();
    }

    private static String kindLabel(Kind kind) {
        switch (kind) {
            case VIDEO:
                return L10n.t("Video");
            case PHOTO:
                return L10n.t("Photo");
            case STICKER:
                return L10n.t("Sticker");
            default:
                return L10n.t("Media");
        }
    }

    private static String join(String[] segments) {
        StringBuilder result = new StringBuilder();
        for (String segment : segments) {
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        return result.toString();
    }
}
