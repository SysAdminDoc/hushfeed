package app.morphe.extension.tiktok.download;

import android.content.Context;
import android.os.Build;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.tiktok.blockauthor.Reflect;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SubtitleDownloads {
    static final class Track {
        final String language, format;
        final List<String> urls;
        final boolean original;
        Track(String language, String format, List<String> urls, boolean original) {
            this.language = language; this.format = format; this.urls = urls; this.original = original;
        }
    }

    static List<Track> tracks(Object video, String choice, Locale locale) {
        Object model = Reflect.property(video, "getCaptionModel", "captionModel");
        Object list = Reflect.readField(model, "captionList");
        if (!(list instanceof List<?>)) return Collections.emptyList();
        LinkedHashMap<String, Track> languages = new LinkedHashMap<>();
        Set<String> filenames = new HashSet<>();
        for (Object caption : (List<?>) list) {
            String format = Reflect.string(caption, "getFormat", "format");
            if (format == null || !List.of("srt", "webvtt", "vtt", "creator_caption", "json").contains(format)) continue;
            List<String> urls = new ArrayList<>();
            String url = Reflect.string(caption, "getUrl", "url");
            if (url != null && url.startsWith("https://")) urls.add(url);
            Object mirrors = Reflect.readField(caption, "urlList");
            if (mirrors instanceof List<?>) for (Object mirror : (List<?>) mirrors) {
                if (mirror instanceof String && ((String) mirror).startsWith("https://") && !urls.contains(mirror)) urls.add((String) mirror);
            }
            if (urls.isEmpty()) continue;
            String label = Reflect.firstNonBlank(Reflect.string(caption, "getLanguageCode", "languageCode"),
                    Reflect.string(caption, "getLanguageName", "languageName"));
            label = label == null ? "" : label.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            String identity = label.isEmpty() ? "url:" + urls.get(0) : label;
            Track old = languages.get(identity);
            String language = old == null ? label.replaceAll("[^\\p{L}\\p{N}-]", "") : old.language;
            if (old == null) {
                if (language.isEmpty()) language = "und";
                String stem = language.substring(0, language.offsetByCodePoints(0, Math.min(48, language.codePointCount(0, language.length()))));
                language = stem;
                for (int suffix = 2; !filenames.add(language); suffix++) language = stem + "-" + suffix;
            }
            boolean original = Boolean.TRUE.equals(Reflect.property(caption, "isOriginalCaption", "isOriginalCaption"));
            if (old == null || priority(format) < priority(old.format)) languages.put(identity, new Track(language, format, urls, original || (old != null && old.original)));
            else if (original && !old.original) languages.put(identity, new Track(language, old.format, old.urls, true));
        }
        List<Track> result = new ArrayList<>(languages.values());
        if (result.isEmpty() || "all".equals(choice)) return result;
        if ("device".equals(choice)) {
            for (Track track : result) if (track.language.equalsIgnoreCase(locale.toLanguageTag())) return List.of(track);
            for (Track track : result) if (track.language.split("-")[0].equals(locale.getLanguage())) return List.of(track);
        }
        for (Track track : result) if (track.original) return List.of(track);
        return List.of(result.get(0));
    }

    private static int priority(String format) { return "srt".equals(format) ? 0 : (format.contains("vtt") ? 1 : 2); }

    static String pairedPath(String videoPath) {
        if (Build.VERSION.SDK_INT < 29) return videoPath;
        int slash = videoPath.indexOf('/');
        // Android 11+ restricts subtitles in MediaStore.Files to Music or Movies.
        return (Build.VERSION.SDK_INT >= 30 ? "Movies" : "Download") + (slash < 0 ? "/TikTok" : videoPath.substring(slash));
    }

    static int save(Context context, List<Track> tracks, String videoName, String path) {
        int saved = 0;
        String stem = videoName.substring(0, videoName.lastIndexOf('.'));
        for (Track track : tracks) {
            File temp = null;
            try {
                String srt = fetch(track.urls, track.format);
                temp = File.createTempFile("subtitle-", ".srt", context.getCacheDir());
                try (var output = new FileOutputStream(temp)) { output.write(srt.getBytes(StandardCharsets.UTF_8)); }
                MediaFileWriter.publish(context, temp, stem + "." + track.language + ".srt", "application/x-subrip", path, false);
                saved++;
            } catch (IOException | RuntimeException error) {
                Logger.printException(() -> "Could not save " + track.language + " subtitles", error);
            } finally {
                if (temp != null && !temp.delete()) Logger.printInfo(() -> "Could not remove temporary subtitle file");
            }
        }
        return saved;
    }

    static String fetch(List<String> urls, String format) throws IOException {
        IOException last = new IOException("No subtitle URL");
        for (String url : urls) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (connection.getResponseCode() != 200) throw new IOException("Subtitle server returned " + connection.getResponseCode());
                if (connection.getContentLengthLong() > 2 * 1024 * 1024) throw new IOException("Subtitle file is too large");
                try (var input = connection.getInputStream(); var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 2 * 1024 * 1024) throw new IOException("Subtitle file is too large");
                        output.write(buffer, 0, count);
                    }
                    return SubtitleFormat.toSrt(new String(output.toByteArray(), StandardCharsets.UTF_8), format);
                }
            } catch (IOException error) { last = error; }
            finally { if (connection != null) connection.disconnect(); }
        }
        throw last;
    }
}
