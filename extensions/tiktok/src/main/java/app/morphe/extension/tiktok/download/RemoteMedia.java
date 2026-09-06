package app.morphe.extension.tiktok.download;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.List;

final class RemoteMedia {
    private RemoteMedia() {}
    static String fetch(List<String> urls, File target, boolean image) throws IOException {
        IOException failure = new IOException("No media URL succeeded");
        MediaBudget.Deadline deadline = MediaBudget.deadline();
        for (String url : urls == null ? Collections.<String>emptyList() : urls) {
            for (int attempt = 0; attempt < MediaBudget.MAX_ATTEMPTS_PER_MIRROR; attempt++) {
                HttpURLConnection connection = null;
                try {
                    MediaBudget.check(deadline);
                    URLConnection opened = new URL(url).openConnection();
                    if (!(opened instanceof HttpURLConnection)) {
                        throw new IOException("Media URL is not HTTP");
                    }
                    connection = (HttpURLConnection) opened;
                    connection.setConnectTimeout(MediaBudget.timeoutMillis(deadline, 15000));
                    connection.setReadTimeout(MediaBudget.timeoutMillis(deadline, 30000));
                    int responseCode = connection.getResponseCode();
                    if (MediaBudget.isTransientStatus(responseCode)
                            && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                        MediaBudget.waitBeforeRetry(connection.getHeaderField("Retry-After"), attempt, deadline);
                        continue;
                    }
                    if (responseCode != 200) throw new IOException("Media server returned " + responseCode);
                    long expected = contentLength(connection);
                    MediaBudget.checkTransferLength(expected);
                    MediaBudget.checkDiskSpace(target == null ? null : target.getParentFile(), expected);
                    try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream())) {
                        input.mark(32);
                        byte[] header = new byte[16];
                        int offset = 0, read;
                        while (offset < header.length && (read = input.read(header, offset, header.length - offset)) != -1) offset += read;
                        if (offset != header.length) throw new IOException("Media response is too short");
                        input.reset();
                        String extension = image ? imageExtension(header) : (new String(header, 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp") ? "mp4" : null);
                        if (extension == null) throw new IOException("Media server returned an unsupported format");
                        long count;
                        try (FileOutputStream output = new FileOutputStream(target)) {
                            count = MediaFileWriter.copy(input, output, MediaBudget.MAX_TRANSFER_BYTES, deadline);
                        }
                        if (expected >= 0 && count != expected) throw new IOException("Media download is incomplete");
                        return extension;
                    }
                } catch (IOException | RuntimeException exception) {
                    if (exception instanceof InterruptedIOException) throw (InterruptedIOException) exception;
                    boolean retryable = exception instanceof SocketTimeoutException
                            || exception instanceof ConnectException;
                    if (retryable && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                        MediaBudget.waitBeforeRetry(null, attempt, deadline);
                        continue;
                    }
                    // URL parsing and transport failures stay with this mirror. The aggregate
                    // message contains only a query-free, bounded address summary.
                    failure.addSuppressed(new IOException(
                            "Media mirror failed (" + exception.getClass().getSimpleName() + "): "
                                    + summarizeUrl(url)));
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }
        if (target != null) target.delete();
        throw failure;
    }

    static String summarizeUrl(String url) {
        if (url == null) return "null";
        int query = url.indexOf('?');
        String withoutQuery = query < 0 ? url : url.substring(0, query);
        int schemeEnd = withoutQuery.indexOf("://");
        if (schemeEnd >= 0) {
            int userInfoEnd = withoutQuery.indexOf('@', schemeEnd + 3);
            if (userInfoEnd >= 0) {
                withoutQuery = withoutQuery.substring(0, schemeEnd + 3)
                        + withoutQuery.substring(userInfoEnd + 1);
            }
        }
        return withoutQuery.length() <= 96 ? withoutQuery : withoutQuery.substring(0, 96) + "...";
    }

    private static long contentLength(HttpURLConnection connection) {
        String header = connection.getHeaderField("Content-Length");
        if (header == null) return -1L;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private static String imageExtension(byte[] header) {
        if (header.length < 12) return null;
        if ((header[0] & 255) == 255 && (header[1] & 255) == 216) return "jpg";
        if (header[0] == (byte) 137 && header[1] == 80 && header[2] == 78 && header[3] == 71) return "png";
        String signature = new String(header, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (signature.startsWith("GIF8")) return "gif";
        if (signature.startsWith("RIFF") && signature.substring(8, 12).equals("WEBP")) return "webp";
        if (signature.substring(4, 8).equals("ftyp")) {
            String brand = signature.substring(8, 12);
            if (brand.equals("avif") || brand.equals("avis")) return "avif";
            if (brand.equals("heic") || brand.equals("heix")) return "heic";
            if (brand.equals("mif1") || brand.equals("msf1")) return "heif";
        }
        return null;
    }
}
