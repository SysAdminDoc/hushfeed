/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.download;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which layer owns a name that is already taken.
 *
 * <p>Not this one. What is formatted here is a wish: the name the download would like to be
 * published under. It is worked out beside TikTok's private staging file, which lives in the
 * app's own cache and has nothing in common with the folder the file ends up in, so asking
 * whether a name is free at this point is asking about the wrong directory. It could only ever
 * answer no when it should have said yes.
 *
 * <p>The destination settles it, and does so without a race. On Android 10 and later
 * {@code MediaFileWriter} hands the name to the provider, which appends its own suffix and
 * reports the name it actually used. Below that, {@code MediaFileWriter.claim} creates the file
 * in the destination folder and steps to _2, _3 until {@code createNewFile} succeeds, so two
 * saves running at once cannot land on one name. A probe here would have been a guess in front
 * of both.
 */
public final class DownloadFilenameFormatter {
    private static final int MAX_BASENAME_LENGTH = 160;
    /** What is left of a 255 byte filename once an extension and a collision suffix fit too. */
    private static final int MAX_BASENAME_BYTES = 200;
    private static final int MAX_EXTENSION_LENGTH = 12;
    private static final long PENDING_NAME_TTL_MS = 10 * 60 * 1000L;
    private static final Map<String, PendingName> PENDING_NAMES = new LinkedHashMap<String, PendingName>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PendingName> eldest) {
            return size() > 64;
        }
    };

    private DownloadFilenameFormatter() {
    }

    public static void registerDownloadedMediaName(String originalPath, Object aweme) {
        if (originalPath == null || originalPath.trim().isEmpty() || aweme == null) {
            return;
        }

        try {
            File original = new File(originalPath);
            if (!original.isFile()) {
                return;
            }

            String extension = extensionOf(original.getName());
            boolean photo = isPhotoAweme(aweme) || isImageExtension(extension);
            String template = photo
                    ? Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get()
                    : Settings.DOWNLOAD_VIDEO_FILENAME_TEMPLATE.get();

            String aid = firstNonBlank(invokeString(aweme, "getAid"), readStringField(aweme, "aid"), "unknown");
            Object author = firstNonNull(invoke(aweme, "getAuthor"), readField(aweme, "author"));
            String creator = firstNonBlank(
                    invokeString(author, "getUniqueId"),
                    readStringField(author, "uniqueId"),
                    invokeString(author, "getNickname"),
                    readStringField(author, "nickname"),
                    invokeString(author, "getUid"),
                    readStringField(author, "uid"),
                    "unknown"
            );
            long createdAt = readCreateTime(aweme);

            File target = resolveTarget(
                    original,
                    template,
                    extension,
                    sanitizeToken(creator),
                    formatDate(createdAt),
                    sanitizeToken(aid),
                    null
            );
            if (target.equals(original)) {
                return;
            }
            synchronized (PENDING_NAMES) {
                PENDING_NAMES.put(original.getName(), new PendingName(target.getName(), System.currentTimeMillis()));
            }
            debug("prepared type=" + (photo ? "photo" : "video") + " file=" + target.getName());
        } catch (Throwable ex) {
            if (BaseSettings.DEBUG.get()) {
                Logger.printException(() -> "[Morphe Downloads] filename formatting failed", ex);
            }
        }
    }

    public static String resolveDestinationName(String originalName) {
        return resolveDestinationName(originalName, false);
    }

    public static String consumeDestinationName(String originalName) {
        return resolveDestinationName(originalName, true);
    }

    private static String resolveDestinationName(String originalName, boolean consume) {
        if (originalName == null || originalName.trim().isEmpty()) return originalName;
        synchronized (PENDING_NAMES) {
            PendingName pending = PENDING_NAMES.get(originalName);
            if (pending == null) return originalName;
            if (System.currentTimeMillis() - pending.createdAt > PENDING_NAME_TTL_MS) {
                PENDING_NAMES.remove(originalName);
                return originalName;
            }
            if (consume) {
                PENDING_NAMES.remove(originalName);
            }
            return pending.name;
        }
    }

    public static String formatCommentMediaName(String extension, String mediaId) {
        String template = Settings.DOWNLOAD_COMMENT_MEDIA_FILENAME_TEMPLATE.get();
        String normalizedExtension = sanitizeExtension(extension);
        File placeholder = new File(".", "comment." + normalizedExtension);
        File result = resolveTarget(
                placeholder,
                template,
                normalizedExtension,
                null,
                formatDate(System.currentTimeMillis()),
                null,
                sanitizeToken(mediaId)
        );
        return result.getName();
    }

    static String formatOriginalPhotoName(Object aweme, int index, String extension) {
        return formatSourceName(aweme, index, extension, true);
    }

    static String formatSelectedVideoName(Object aweme) {
        return formatSourceName(aweme, 1, "mp4", false);
    }

    /** The sound takes the video's own name, so the pair sorts together. */
    static String formatSelectedAudioName(Object aweme) {
        return formatSourceName(aweme, 1, "m4a", false);
    }

    /**
     * The original sound is named after itself. The video template's tokens are all about the
     * post, and the same sound saved from two posts should be the same file, not two.
     */
    static String formatSoundName(String title) {
        String base = trimToLength(sanitizeBaseName(sanitizeToken(title)), MAX_BASENAME_LENGTH);
        if (base.isEmpty()) base = "sound";
        return base + ".m4a";
    }

    /**
     * A profile picture is named after the account rather than by the video template, whose
     * tokens have nothing to fill them here.
     */
    static String formatProfilePictureName(String handle) {
        String base = trimToLength(sanitizeBaseName(sanitizeToken(handle)), MAX_BASENAME_LENGTH);
        if (base.isEmpty()) base = "profile";
        return base + "_profile.jpg";
    }

    private static String formatSourceName(Object aweme, int index, String extension, boolean photo) {
        Object author = invoke(aweme, "getAuthor");
        String creator = firstNonBlank(invokeString(author, "getUniqueId"), invokeString(author, "getNickname"), "unknown");
        String id = firstNonBlank(invokeString(aweme, "getAid"), "unknown");
        String template = photo ? Settings.DOWNLOAD_PHOTO_FILENAME_TEMPLATE.get() : Settings.DOWNLOAD_VIDEO_FILENAME_TEMPLATE.get();
        if (template == null || template.trim().isEmpty()) template = "{creator}_{date}_{video_id}" + (photo ? "_{index}" : "");
        String base = template.replace("{creator}", sanitizeToken(creator))
                .replace("{date}", formatDate(readCreateTime(aweme)))
                .replace("{video_id}", sanitizeToken(id)).replace("{index}", String.valueOf(index));
        if (photo && !template.contains("{index}")) base += "_" + index;
        // Every photo of a slideshow comes through here with its own number, so that number has
        // to survive the length cap or they all end up named the same.
        boolean carriesOrdinal = photo || template.contains("{index}");
        String sanitized = sanitizeBaseName(base);
        base = carriesOrdinal
                ? boundTemplatedName(sanitized, MAX_BASENAME_LENGTH, String.valueOf(index))
                : trimToLength(sanitized, MAX_BASENAME_LENGTH);
        if (base.isEmpty()) base = photo ? "original_photo_" + index : "video";
        return base + "." + sanitizeExtension(extension);
    }

    private static File resolveTarget(
            File original,
            String template,
            String extension,
            String creator,
            String date,
            String videoId,
            String mediaId
    ) {
        String source = template == null ? "" : template.trim();
        if (source.isEmpty()) {
            return original;
        }

        String originalBase = stripExtension(original.getName());
        boolean hasIndexToken = source.contains("{index}");
        // A single number, because this is not where a taken name is discovered. The token
        // numbers the photos of a slideshow, and those arrive already numbered through
        // formatOriginalPhotoName; a single video has one of itself.
        String counter = "1";
        String base = source
                .replace("{creator}", safeToken(creator))
                .replace("{date}", safeToken(date))
                .replace("{video_id}", safeToken(videoId))
                .replace("{media_id}", safeToken(mediaId))
                .replace("{index}", counter)
                .replace("{original}", sanitizeToken(originalBase));
        base = sanitizeBaseName(base);
        if (base.isEmpty()) {
            return original;
        }

        String boundedBase = hasIndexToken
                ? boundTemplatedName(base, MAX_BASENAME_LENGTH, counter)
                : trimToLength(base, MAX_BASENAME_LENGTH, MAX_BASENAME_BYTES);
        return new File(original.getParentFile(), boundedBase + "." + sanitizeExtension(extension));
    }

    /**
     * Keeps a filled-in template inside the length limits without losing its counter. A creator
     * name long enough to reach the cut would otherwise take {@code index} with it, leaving every
     * photo of a slideshow with one name and the collision search with nothing to advance, so a
     * name that had to be shortened carries the counter on its end instead.
     */
    private static String boundTemplatedName(String base, int limit, String counter) {
        int room = Math.max(1, limit);
        String bounded = trimToLength(base, room);
        if (bounded.equals(base)) {
            return bounded;
        }

        int reserved = counter.length() + 1;
        String stem = trimToLength(base, Math.max(1, room - reserved), Math.max(1, MAX_BASENAME_BYTES - reserved));
        return sanitizeBaseName(stem + "_" + counter);
    }

    private static boolean isPhotoAweme(Object aweme) {
        Object imageInfos = invoke(aweme, "getImageInfos");
        if (imageInfos instanceof List && !((List<?>) imageInfos).isEmpty()) {
            return true;
        }
        return invoke(aweme, "getPhotoModeImageInfo") != null || readField(aweme, "photoModeImageInfo") != null;
    }

    private static boolean isImageExtension(String extension) {
        String value = extension.toLowerCase(Locale.ROOT);
        return value.equals("jpg") || value.equals("jpeg") || value.equals("png")
                || value.equals("webp") || value.equals("gif") || value.equals("heic") || value.equals("heif");
    }

    private static long readCreateTime(Object aweme) {
        Object value = firstNonNull(invoke(aweme, "getCreateTime"), readField(aweme, "createTime"));
        if (!(value instanceof Number)) {
            return System.currentTimeMillis();
        }
        long timestamp = ((Number) value).longValue();
        return timestamp > 0 && timestamp < 100_000_000_000L ? timestamp * 1000L : timestamp;
    }

    private static String formatDate(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(timestamp));
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String invokeString(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value instanceof String ? (String) value : null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value instanceof String ? (String) value : null;
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private static String safeToken(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value;
    }

    private static String sanitizeToken(String value) {
        return sanitizeBaseName(value == null ? "" : value);
    }

    private static String sanitizeBaseName(String value) {
        String cleaned = value.replace('/', '_').replace('\\', '_');
        cleaned = cleaned.replaceAll("[\\p{Cntrl}]", "_").replaceAll("\\s+", " ").trim();
        while (cleaned.startsWith(".")) cleaned = cleaned.substring(1);
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        return cleaned;
    }

    private static String sanitizeExtension(String extension) {
        String cleaned = extension == null ? "bin" : extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) return "bin";
        // An extension comes from the name the host handed over, so it is as arbitrary as the
        // rest of it. Nothing real is longer than this, and the byte budget assumes a short one.
        return cleaned.length() > MAX_EXTENSION_LENGTH ? cleaned.substring(0, MAX_EXTENSION_LENGTH) : cleaned;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "mp4";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Both bounds a filename has to stay inside. The character count is what the templates
     * are written against; the byte count is what the filesystem enforces, and 160 characters
     * of Japanese is 480 bytes of UTF-8, well past the 255 ext4 and f2fs allow. The cut lands
     * on a code point rather than between the halves of a surrogate pair.
     */
    private static String trimToLength(String value, int maxLength) {
        return trimToLength(value, maxLength, MAX_BASENAME_BYTES);
    }

    private static String trimToLength(String value, int maxLength, int maxBytes) {
        String trimmed = value;
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, maxLength));
        }
        while (!trimmed.isEmpty() && trimmed.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(trimmed.length(), -1));
        }
        return trimmed.trim();
    }

    private static void debug(String message) {
        if (BaseSettings.DEBUG.get()) {
            Logger.printInfo(() -> "[Morphe Downloads] " + message);
        }
    }

    private static final class PendingName {
        final String name;
        final long createdAt;

        PendingName(String name, long createdAt) {
            this.name = name;
            this.createdAt = createdAt;
        }
    }
}
