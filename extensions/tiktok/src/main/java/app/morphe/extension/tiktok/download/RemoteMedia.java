package app.morphe.extension.tiktok.download;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

final class RemoteMedia {
    private RemoteMedia() {}
    static String fetch(List<String> urls, File target, boolean image) throws IOException {
        IOException failure = new IOException("No media URL succeeded");
        for (String url : urls) {
            java.net.URLConnection opened = new URL(url).openConnection();
            if (!(opened instanceof HttpURLConnection)) {
                // Not an IOException, so letting this through as a cast would jump out of the
                // loop and leave every remaining mirror untried.
                failure.addSuppressed(new IOException("Media URL is not HTTP: " + url));
                continue;
            }
            HttpURLConnection connection = (HttpURLConnection) opened;
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            try {
                if (connection.getResponseCode() != 200) throw new IOException("Media server returned " + connection.getResponseCode());
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
                    try (FileOutputStream output = new FileOutputStream(target)) { count = MediaFileWriter.copy(input, output); }
                    long expected = contentLength(connection);
                    if (expected >= 0 && count != expected) throw new IOException("Media download is incomplete");
                    return extension;
                }
            } catch (IOException exception) {
                failure.addSuppressed(exception);
            } finally { connection.disconnect(); }
        }
        throw failure;
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
