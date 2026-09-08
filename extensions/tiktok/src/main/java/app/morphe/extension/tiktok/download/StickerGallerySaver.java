/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.extension.tiktok.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import app.morphe.extension.shared.Logger;

import app.morphe.extension.tiktok.settings.L10n;
import app.morphe.extension.shared.settings.BaseSettings;
import app.morphe.extension.tiktok.settings.Settings;
import app.morphe.extension.tiktok.settings.preference.SettingsUi;

import com.ss.android.ugc.aweme.base.model.UrlModel;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

@SuppressWarnings("unused")
public final class StickerGallerySaver {
    private static final String ACTION_LABEL = "Save media";
    /** A sticker is a few hundred KB. Anything past this is not one. */
    private static final long MAX_STICKER_BYTES = 24L * 1024 * 1024;
    private static final long MAX_STICKER_PIXELS = 16L * 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    /** The sticker each decorated sheet is currently showing, so a reused sheet saves its own. */
    private static final WeakHashMap<View, StickerAsset> ATTACHED_SHEETS = new WeakHashMap<>();
    private static final WeakHashMap<Object, Object> STICKER_SOURCES = new WeakHashMap<>();

    private StickerGallerySaver() {
    }

    public static void registerStickerSource(Object previewModel, Object source) {
        if (previewModel == null || source == null) return;
        synchronized (STICKER_SOURCES) {
            STICKER_SOURCES.put(previewModel, source);
        }
    }

    public static void attachSaveImageButton(View sheetView, Object sheetModel) {
        try {
            if (sheetView == null || sheetModel == null) return;

            StickerAsset asset = findStickerAsset(sheetModel);
            if (asset == null) {
                // Forget what the sheet was showing. Leaving the last one there would let the
                // button save a sticker the reader has already moved on from.
                synchronized (ATTACHED_SHEETS) {
                    ATTACHED_SHEETS.remove(sheetView);
                }
                debugLog("[Morphe Stickers] no usable sticker URL");
                return;
            }

            // The button is added once per sheet, but the sheet is reused for whatever sticker
            // is opened next. Recording it before anything below can return keeps the button
            // pointed at what is on screen whatever happens after this.
            boolean alreadyDecorated;
            synchronized (ATTACHED_SHEETS) {
                alreadyDecorated = ATTACHED_SHEETS.containsKey(sheetView);
                ATTACHED_SHEETS.put(sheetView, asset);
            }
            if (alreadyDecorated) return;

            List<View> actionButtons = findViewsByClassName(sheetView, "X.0GSy", "LX.0GSy", "X.0Daq", "LX.0Daq");
            ViewGroup actionParent = findCommonParent(actionButtons);
            if (actionParent == null || actionButtons.size() < 2 || hasSaveImageButton(actionParent)) {
                debugLog("[Morphe Stickers] action parent unavailable buttons=" + actionButtons.size());
                return;
            }

            View template = actionButtons.get(actionButtons.size() - 1);
            int insertIndex = actionParent.indexOfChild(template) + 1;
            if (insertIndex <= 0) {
                debugLog("[Morphe Stickers] action buttons are not direct children");
                return;
            }

            TextView saveImageButton = createActionButton(template, sheetView);
            ViewGroup.LayoutParams layoutParams = cloneLayoutParams(template.getLayoutParams());
            actionParent.addView(saveImageButton, insertIndex, layoutParams);

            debugLog("[Morphe Stickers] attached Save sticker button animated=" + asset.animated
                    + " url=" + summarizeUrl(asset.url)
                    + " parent=" + actionParent.getClass().getName());
        } catch (Throwable ex) {
            if (BaseSettings.DEBUG.get()) {
                Logger.printException(() -> "[Morphe Stickers] attachSaveImageButton failure", ex);
            }
        }
    }

    private static TextView createActionButton(View template, View sheetView) {
        Context context = template.getContext();
        TextView button = new TextView(context);
        button.setText(ACTION_LABEL);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        // Read the sticker when the button is pressed, not when it was built: the sheet the
        // button lives in gets bound again for the next sticker the reader opens.
        button.setOnClickListener(view -> {
            StickerAsset showing;
            synchronized (ATTACHED_SHEETS) {
                showing = ATTACHED_SHEETS.get(sheetView);
            }
            if (showing == null) {
                debugLog("[Morphe Stickers] the sheet no longer names a sticker");
                return;
            }
            saveStickerFromButton(view, showing);
        });

        if (template instanceof TextView) {
            TextView textTemplate = (TextView) template;
            button.setTextColor(textTemplate.getTextColors());
            button.setTextSize(0, textTemplate.getTextSize());
            button.setTypeface(textTemplate.getTypeface(), textTemplate.getTypeface() == null ? Typeface.NORMAL : textTemplate.getTypeface().getStyle());
            button.setIncludeFontPadding(textTemplate.getIncludeFontPadding());
            button.setMinHeight(textTemplate.getMinHeight());
            button.setMinWidth(textTemplate.getMinWidth());
            button.setPadding(
                    textTemplate.getPaddingLeft(),
                    textTemplate.getPaddingTop(),
                    textTemplate.getPaddingRight(),
                    textTemplate.getPaddingBottom()
            );
        } else {
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);
            int paddingHorizontal = SettingsUi.dp(context, 16);
            int paddingVertical = SettingsUi.dp(context, 10);
            button.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        }

        Drawable background = template.getBackground();
        if (background != null && background.getConstantState() != null) {
            button.setBackground(background.getConstantState().newDrawable().mutate());
        } else {
            button.setBackground(template.getBackground());
        }

        button.setEnabled(template.isEnabled());
        button.setClickable(true);
        button.setFocusable(true);
        button.setAlpha(template.getAlpha());
        return button;
    }

    private static void saveStickerFromButton(View button, StickerAsset asset) {
        Context context = button.getContext().getApplicationContext();
        button.setEnabled(false);
        toast(context, L10n.t("Saving sticker"));

        MediaJobScheduler.JobHandle job = MediaJobScheduler.submit("sticker", () -> {
            SaveResult result = saveSticker(context, asset);
            MAIN_HANDLER.post(() -> {
                button.setEnabled(true);
                toast(context, result.message);
                if (result.success) {
                    debugLog("[Morphe Stickers] saved sticker path=" + result.path);
                } else if (BaseSettings.DEBUG.get()) {
                    Logger.printInfo(() -> "[Morphe Stickers] save failed reason=" + result.message
                            + " url=" + summarizeUrl(asset.url));
                }
            });
        }, () -> MAIN_HANDLER.post(() -> button.setEnabled(true)));
        if (job == null) MAIN_HANDLER.post(() -> button.setEnabled(true));
    }

    private static SaveResult saveSticker(Context context, StickerAsset asset) {
        MediaBudget.Deadline deadline = MediaBudget.deadline();
        IOException failure = new IOException("No sticker URL succeeded");
        for (String url : asset.urls) {
            File source = null;
            try {
                source = MediaCache.createTempFile(context, "sticker-source-", ".tmp");
                String contentType = downloadSticker(url, source, context, deadline);
                SaveResult result = saveDownloadedSticker(context, asset, url, source, contentType);
                if (!result.success) throw new IOException(result.message);
                return result;
            } catch (Throwable error) {
                if (MediaBudget.isCancellation(error)) {
                    if (BaseSettings.DEBUG.get()) {
                        Logger.printException(() -> "[Morphe Stickers] saveSticker cancelled", error);
                    }
                    return SaveResult.failure(L10n.t("The sticker could not be saved"));
                }
                failure.addSuppressed(new IOException(
                        "Sticker mirror failed (" + error.getClass().getSimpleName() + "): "
                                + summarizeUrl(url), error));
            } finally {
                if (source != null && !MediaCache.delete(source)) {
                    Logger.printInfo(() -> "Could not remove sticker source file");
                }
            }
        }
        if (BaseSettings.DEBUG.get()) {
            Logger.printException(() -> "[Morphe Stickers] saveSticker failure", failure);
        }
        return SaveResult.failure(L10n.t("The sticker could not be saved"));
    }

    private static String downloadSticker(
            String url,
            File target,
            Context context,
            MediaBudget.Deadline deadline
    ) throws IOException {
        IOException failure = new IOException("Sticker URL failed");
        for (int attempt = 0; attempt < MediaBudget.MAX_ATTEMPTS_PER_MIRROR; attempt++) {
            HttpURLConnection connection = null;
            try {
                MediaBudget.check(deadline);
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(MediaBudget.timeoutMillis(deadline, CONNECT_TIMEOUT_MS));
                connection.setReadTimeout(MediaBudget.timeoutMillis(deadline, READ_TIMEOUT_MS));
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "TikTok 46.2.3 Morphe");
                int responseCode = connection.getResponseCode();
                if (MediaBudget.isTransientStatus(responseCode)
                        && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                    MediaBudget.waitBeforeRetry(connection.getHeaderField("Retry-After"), attempt, deadline);
                    continue;
                }
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Sticker server returned " + responseCode);
                }
                long declaredLength = contentLength(connection);
                MediaBudget.checkTransferLength(declaredLength);
                MediaBudget.checkDiskSpace(context.getCacheDir(), declaredLength, deadline);
                try (InputStream input = connection.getInputStream();
                     OutputStream output = new FileOutputStream(target)) {
                    MediaFileWriter.copy(input, output, MAX_STICKER_BYTES, deadline);
                }
                return connection.getContentType();
            } catch (IOException | RuntimeException error) {
                if (MediaBudget.isCancellation(error)) {
                    if (error instanceof InterruptedIOException) throw (InterruptedIOException) error;
                    throw new InterruptedIOException("Media job cancelled");
                }
                boolean retryable = MediaBudget.isRetryableTransport(error);
                if (retryable && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                    MediaBudget.waitBeforeRetry(null, attempt, deadline);
                    continue;
                }
                failure.addSuppressed(new IOException(
                        "Sticker attempt failed (" + error.getClass().getSimpleName() + "): "
                                + summarizeUrl(url), error));
                break;
            } finally {
                if (connection != null) connection.disconnect();
            }
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

    private static SaveResult saveDownloadedSticker(
            Context context,
            StickerAsset asset,
            String url,
            File source,
            String contentType
    ) throws Exception {
        MediaBudget.check(null);
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(source))) {
            MediaFormat format = detectMediaFormat(contentType, url, inputStream, asset.animated);
            String mediaId = Integer.toUnsignedString(url.hashCode(), 16);
            String displayName = DownloadFilenameFormatter.formatCommentMediaName(format.extension, mediaId);

            if (!format.convertToPng) {
                if (format.convertToMp4 || format.convertToGif) {
                    byte[] animatedWebp = readFully(inputStream);
                    try {
                        if (format.convertToGif) {
                            return saveConvertedSticker(context, animatedWebp, displayName,
                                    "image/gif", false, "GIF");
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Uri uri = saveAnimatedWebpMp4WithMediaStore(context, animatedWebp, displayName);
                            return SaveResult.success(savedPath(context, uri, displayName, true), uri.toString(), "MP4");
                        }

                        File outputFile = saveAnimatedWebpMp4WithLegacyStorage(context, animatedWebp, displayName);
                        return SaveResult.success(outputFile.getAbsolutePath(), outputFile.getAbsolutePath(), "MP4");
                    } catch (Throwable ex) {
                        // Better the sticker in the format it arrived in than nothing at all.
                        debugLog("[Morphe Stickers] conversion failed, keeping the WebP: " + ex);
                        MediaFormat sourceFormat = MediaFormat.webp();
                        String webpName = DownloadFilenameFormatter.formatCommentMediaName(
                                sourceFormat.extension, mediaId);
                        try (InputStream bytes = new java.io.ByteArrayInputStream(animatedWebp)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                Uri uri = saveStreamWithMediaStore(context, bytes, webpName, sourceFormat);
                                return SaveResult.success(savedPath(context, uri, webpName, false),
                                        uri.toString(), sourceFormat.label);
                            }
                            File saved = saveStreamWithLegacyStorage(context, bytes, webpName, sourceFormat.mimeType);
                            return SaveResult.success(saved.getAbsolutePath(), saved.getAbsolutePath(),
                                    sourceFormat.label);
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri uri = saveStreamWithMediaStore(context, inputStream, displayName, format);
                    return SaveResult.success(savedPath(context, uri, displayName, format.video),
                            uri.toString(), format.label);
                }

                File outputFile = saveStreamWithLegacyStorage(context, inputStream, displayName, format.mimeType);
                return SaveResult.success(outputFile.getAbsolutePath(), outputFile.getAbsolutePath(), format.label);
            }

            Bitmap bitmap = decodeStaticSticker(source);
            if (bitmap == null) return SaveResult.failure(
                    L10n.t("That sticker is in a format Hushfeed cannot read"));

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Uri uri = saveBitmapWithMediaStore(context, bitmap, displayName);
                    return SaveResult.success(savedPath(context, uri, displayName, false), uri.toString(), "PNG");
                }

                File outputFile = saveBitmapWithLegacyStorage(context, bitmap, displayName);
                return SaveResult.success(outputFile.getAbsolutePath(), outputFile.getAbsolutePath(), "PNG");
            } finally {
                bitmap.recycle();
            }
        }
    }

    private static Bitmap decodeStaticSticker(File source) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > MAX_STICKER_PIXELS) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        return BitmapFactory.decodeFile(source.getAbsolutePath(), options);
    }

    private interface PendingWriter {
        void write(ContentResolver resolver, Uri uri) throws Exception;
    }

    private interface FileWriter {
        void write(File file) throws Exception;
    }

    /** Publishes one pending MediaStore row and removes it again when writing fails. */
    private static Uri savePendingWithMediaStore(
            Context context,
            String displayName,
            String mimeType,
            boolean video,
            PendingWriter writer
    ) throws Exception {
        MediaBudget.check(null);
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        String relativePath = stickerRelativePath(video);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = MediaCache.insertPending(
                context, resolver, DownloadDestination.collectionUri(relativePath, video), values);
        try {
            writer.write(resolver, uri);
            MediaBudget.check(null);
            ContentValues complete = new ContentValues();
            complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
            completePending(context, resolver, uri, complete);
            return uri;
        } catch (Throwable ex) {
            discardPending(context, resolver, uri, ex);
            throw ex;
        }
    }

    /** Claims a unique legacy file, scans it after writing and removes only that file on failure. */
    private static File saveWithLegacyStorage(
            Context context,
            String displayName,
            String mimeType,
            boolean video,
            FileWriter writer
    ) throws Exception {
        MediaBudget.check(null);
        File directory = new File(Environment.getExternalStorageDirectory(), stickerRelativePath(video));
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create " + directory);
        }

        File outputFile = MediaFileWriter.claim(directory, displayName);
        try {
            writer.write(outputFile);
            MediaBudget.check(null);
        } catch (Throwable ex) {
            if (outputFile.exists() && !outputFile.delete()) {
                debugLog("[Morphe Stickers] could not remove partial file=" + outputFile.getAbsolutePath());
            }
            throw ex;
        }
        MediaScannerConnection.scanFile(context, new String[]{outputFile.getAbsolutePath()},
                new String[]{mimeType}, null);
        return outputFile;
    }

    private static Uri saveBitmapWithMediaStore(Context context, Bitmap bitmap, String displayName) throws Exception {
        return savePendingWithMediaStore(context, displayName, "image/png", false,
                (resolver, uri) -> {
                    try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                        if (outputStream == null) {
                            throw new IllegalStateException("MediaStore output stream returned null");
                        }
                        writePng(bitmap, outputStream);
                    }
                });
    }

    private static Uri saveStreamWithMediaStore(
            Context context,
            InputStream inputStream,
            String displayName,
            MediaFormat format
    ) throws Exception {
        return savePendingWithMediaStore(context, displayName, format.mimeType, format.video,
                (resolver, uri) -> {
                    try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                        if (outputStream == null) {
                            throw new IllegalStateException("MediaStore output stream returned null");
                        }
                        copy(inputStream, outputStream);
                    }
                });
    }

    private static File saveBitmapWithLegacyStorage(Context context, Bitmap bitmap, String displayName) throws Exception {
        return saveWithLegacyStorage(context, displayName, "image/png", false,
                outputFile -> {
                    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                        writePng(bitmap, outputStream);
                    }
                });
    }

    private static File saveStreamWithLegacyStorage(
            Context context,
            InputStream inputStream,
            String displayName,
            String mimeType
    ) throws Exception {
        boolean video = mimeType != null && mimeType.startsWith("video/");
        return saveWithLegacyStorage(context, displayName, mimeType, video,
                outputFile -> {
                    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                        copy(inputStream, outputStream);
                    }
                });
    }

    /** Runs a converter into the gallery, and cleans up after itself when it throws. */
    private static SaveResult saveConvertedSticker(
            Context context,
            byte[] animatedWebp,
            String displayName,
            String mimeType,
            boolean video,
            String label
    ) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Uri uri = savePendingWithMediaStore(context, displayName, mimeType, video,
                    (resolver, pendingUri) -> {
                        try (OutputStream output = resolver.openOutputStream(pendingUri)) {
                            if (output == null) {
                                throw new IllegalStateException("MediaStore output stream returned null");
                            }
                            AnimatedWebpGifConverter.convert(animatedWebp, output);
                        }
                    });
            return SaveResult.success(savedPath(context, uri, displayName, video), uri.toString(), label);
        }

        File outputFile = saveWithLegacyStorage(context, displayName, mimeType, video,
                file -> {
                    try (OutputStream output = new FileOutputStream(file)) {
                        AnimatedWebpGifConverter.convert(animatedWebp, output);
                    }
                });
        return SaveResult.success(outputFile.getAbsolutePath(), outputFile.getAbsolutePath(), label);
    }

    private static Uri saveAnimatedWebpMp4WithMediaStore(
            Context context,
            byte[] animatedWebp,
            String displayName
    ) throws Exception {
        return savePendingWithMediaStore(context, displayName, "video/mp4", true,
                (resolver, uri) -> {
                    try (ParcelFileDescriptor output = resolver.openFileDescriptor(uri, "w")) {
                        if (output == null) {
                            throw new IllegalStateException("MediaStore file descriptor returned null");
                        }
                        AnimatedWebpMp4Converter.convert(animatedWebp, output.getFileDescriptor());
                    }
                });
    }

    private static File saveAnimatedWebpMp4WithLegacyStorage(
            Context context,
            byte[] animatedWebp,
            String displayName
    ) throws Exception {
        return saveWithLegacyStorage(context, displayName, "video/mp4", true,
                outputFile -> AnimatedWebpMp4Converter.convert(animatedWebp, outputFile.getAbsolutePath()));
    }

    private static String stickerRelativePath(boolean video) {
        String path = DownloadDestination.resolve(
                Settings.DOWNLOAD_STICKER_PATH.get(),
                DownloadDestination.Kind.STICKER
        );
        return path;
    }

    private static String displayPath(String displayName, boolean video) {
        return stickerRelativePath(video) + "/" + displayName;
    }

    /**
     * Where a sticker actually landed.
     *
     * <p>The display name handed to MediaStore is a request. Saving the same sticker twice gets
     * the second one a suffix of the provider's choosing, and telling the reader the name that
     * was asked for points them at a file that is not the one just written. Falls back to the
     * requested name, because a path that is probably right beats no path at all.
     */
    private static String savedPath(Context context, Uri uri, String requestedName, boolean video) {
        String name = requestedName;
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String saved = cursor.getString(0);
                if (saved != null && !saved.isEmpty()) name = saved;
            }
        } catch (RuntimeException unreadable) {
            debugLog("[Morphe Stickers] could not read the saved name back: " + unreadable);
        }
        return displayPath(name, video);
    }

    private static void writePng(Bitmap bitmap, OutputStream outputStream) throws Exception {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
            throw new IllegalStateException("PNG encoding failed");
        }
        outputStream.flush();
    }

    private static void completePending(
            Context context,
            ContentResolver resolver,
            Uri uri,
            ContentValues values
    ) throws IOException {
        if (resolver.update(uri, values, null, null) != 1) {
            throw new IOException("Could not publish sticker");
        }
        try {
            MediaCache.clearPending(context, uri);
        } catch (IOException error) {
            // Reconciliation checks IS_PENDING before deleting a journaled URI, so a completed
            // row remains safe if this final cleanup write is interrupted.
            Logger.printException(() -> "Could not clear media publication journal", error);
        }
    }

    private static void discardPending(
            Context context,
            ContentResolver resolver,
            Uri uri,
            Throwable failure
    ) {
        boolean deleted = false;
        try {
            deleted = resolver.delete(uri, null, null) > 0;
        } catch (Throwable cleanup) {
            failure.addSuppressed(cleanup);
        }
        if (deleted) {
            try {
                MediaCache.clearPending(context, uri);
            } catch (IOException journalError) {
                failure.addSuppressed(journalError);
            }
        }
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws Exception {
        MediaFileWriter.copy(inputStream, outputStream, MAX_STICKER_BYTES);
    }

    /**
     * The whole sticker in memory, which is what the animated converters need. The cap is the
     * reason this is safe to do: the URL is server supplied and a body with no end to it would
     * otherwise be read until the process runs out of heap.
     */
    private static byte[] readFully(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(inputStream, output);
        return output.toByteArray();
    }

    private static MediaFormat detectMediaFormat(
            String contentType,
            String url,
            BufferedInputStream inputStream,
            boolean animated
    ) throws Exception {
        byte[] header = peekHeader(inputStream);
        String mimeType = normalizedContentType(contentType);
        String extension = extensionFromUrl(url);

        if (isMp4(header) || "video/mp4".equals(mimeType) || "m4v".equals(extension)) {
            return MediaFormat.passthrough("video/mp4", "mp4", true, "MP4");
        }
        if (isWebm(header) || "video/webm".equals(mimeType) || "webm".equals(extension)) {
            return MediaFormat.passthrough("video/webm", "webm", true, "WebM");
        }
        if (mimeType != null && mimeType.startsWith("video/")) {
            String videoExtension = extension == null ? "mp4" : extension;
            return MediaFormat.passthrough(
                    mimeType,
                    videoExtension,
                    true,
                    videoExtension.toUpperCase(java.util.Locale.ROOT)
            );
        }
        if (isGif(header) || "image/gif".equals(mimeType) || "gif".equals(extension)) {
            return MediaFormat.passthrough("image/gif", "gif", false, "GIF");
        }
        if (isWebp(header) || "image/webp".equals(mimeType) || "webp".equals(extension)) {
            // The model calls anything whose type merely contains "webp" animated, so the
            // VP8X animation flag decides it whenever the header was long enough to carry one.
            boolean readableHeader = header.length >= 21 && isWebp(header);
            if (readableHeader ? isAnimatedWebp(header) : animated) {
                return MediaFormat.animated(Settings.DOWNLOAD_STICKER_FORMAT.get());
            }
            return MediaFormat.png();
        }
        if (isPng(header) || isJpeg(header) || (mimeType != null && mimeType.startsWith("image/"))) {
            return MediaFormat.png();
        }
        if (animated && mimeType != null) {
            String safeExtension = extension == null ? "bin" : extension;
            return MediaFormat.passthrough(
                    mimeType,
                    safeExtension,
                    false,
                    safeExtension.toUpperCase(java.util.Locale.ROOT)
            );
        }
        throw new IllegalStateException("Unsupported sticker media type");
    }

    private static byte[] peekHeader(BufferedInputStream inputStream) throws Exception {
        inputStream.mark(32);
        byte[] buffer = new byte[32];
        int count = inputStream.read(buffer);
        inputStream.reset();
        if (count == buffer.length) return buffer;
        byte[] result = new byte[Math.max(count, 0)];
        if (count > 0) System.arraycopy(buffer, 0, result, 0, count);
        return result;
    }

    private static String normalizedContentType(String contentType) {
        if (contentType == null) return null;
        int separator = contentType.indexOf(';');
        String value = (separator >= 0 ? contentType.substring(0, separator) : contentType).trim().toLowerCase();
        return value.isEmpty() || "application/octet-stream".equals(value) ? null : value;
    }

    private static boolean isPng(byte[] header) {
        return header.length >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P'
                && header[2] == 'N' && header[3] == 'G';
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                && (header[2] & 0xff) == 0xff;
    }

    private static boolean isGif(byte[] header) {
        return header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F'
                && header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
    }

    private static boolean isWebp(byte[] header) {
        return header.length >= 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F'
                && header[3] == 'F' && header[8] == 'W' && header[9] == 'E' && header[10] == 'B'
                && header[11] == 'P';
    }

    private static boolean isAnimatedWebp(byte[] header) {
        return header.length >= 21 && isWebp(header) && header[12] == 'V' && header[13] == 'P'
                && header[14] == '8' && header[15] == 'X' && (header[20] & 0x02) != 0;
    }

    private static boolean isMp4(byte[] header) {
        return header.length >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
    }

    private static boolean isWebm(byte[] header) {
        return header.length >= 4 && (header[0] & 0xff) == 0x1a && (header[1] & 0xff) == 0x45
                && (header[2] & 0xff) == 0xdf && (header[3] & 0xff) == 0xa3;
    }

    private static StickerAsset findStickerAsset(Object model) {
        StickerAsset sourceAsset = findSourceStickerAsset(model);
        if (sourceAsset != null) return sourceAsset;

        UrlModel urlModel = findUrlModel(model);
        List<String> urls = usableUrls(urlModel);
        if (urls.isEmpty()) return null;
        return new StickerAsset(urls, isAnimatedStickerModel(model));
    }

    private static StickerAsset findSourceStickerAsset(Object previewModel) {
        Object source;
        synchronized (STICKER_SOURCES) {
            source = STICKER_SOURCES.get(previewModel);
        }
        if (source == null) return null;

        Object sticker = resolveSourceSticker(source);
        if (sticker != null) {
            for (String methodName : new String[]{"getAnimateUrl", "getAnimatedUrl"}) {
                UrlModel animated = bestResolutionUrl(invokeNoArg(sticker, methodName));
                List<String> animatedUrls = usableUrls(animated);
                if (!animatedUrls.isEmpty()) {
                    String animatedUrl = animatedUrls.get(0);
                    debugLog("[Morphe Stickers] selected source animated URL " + summarizeUrl(animatedUrl));
                    return new StickerAsset(animatedUrls, true);
                }
            }

            UrlModel staticModel = bestResolutionUrl(invokeNoArg(sticker, "getStaticUrl"));
            List<String> staticUrls = usableUrls(staticModel);
            if (!staticUrls.isEmpty()) {
                String staticUrl = staticUrls.get(0);
                debugLog("[Morphe Stickers] selected source static URL " + summarizeUrl(staticUrl));
                return new StickerAsset(staticUrls, false);
            }

            Object directValue = invokeNoArg(sticker, "getUrl");
            if (directValue instanceof String) {
                String directUrl = ((String) directValue).trim();
                if (!directUrl.isEmpty() && !"null".equalsIgnoreCase(directUrl)) {
                    Object typeValue = invokeNoArg(sticker, "getType");
                    String type = typeValue == null
                            ? ""
                            : typeValue.toString().toLowerCase(java.util.Locale.ROOT);
                    boolean animated = !type.contains("static")
                            && !type.contains("png")
                            && !type.contains("jpeg")
                            && !type.contains("jpg");
                    debugLog("[Morphe Stickers] selected source direct URL "
                            + summarizeUrl(directUrl) + " type=" + type);
                    return new StickerAsset(directUrl, animated);
                }
            }
        }

        Object image = invokeNoArg(source, "currentImage");
        List<String> directUrls = usableUrlList(readNamedField(image, "urlList"));
        if (directUrls.isEmpty()) return null;
        String directUrl = directUrls.get(0);

        Object imageTypeValue = readNamedField(image, "imageType");
        String imageType = imageTypeValue == null ? "" : imageTypeValue.toString().toLowerCase(java.util.Locale.ROOT);
        boolean animated = imageType.contains("anim") || imageType.contains("webp") || imageType.contains("gif");
        debugLog("[Morphe Stickers] selected StickerItem image URL " + summarizeUrl(directUrl)
                + " type=" + imageType);
        return new StickerAsset(directUrls, animated);
    }

    private static Object resolveSourceSticker(Object source) {
        Object legacySticker = readNamedField(source, "LLILLIZIL");
        if (legacySticker != null) return legacySticker;

        if (invokeNoArg(source, "getStaticUrl") != null
                || invokeNoArg(source, "getAnimateUrl") != null
                || invokeNoArg(source, "getAnimatedUrl") != null) {
            return source;
        }

        try {
            Class<?> helperClass = Class.forName("X.0UD5");
            for (java.lang.reflect.Method method : helperClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || parameterTypes.length != 1
                        || !parameterTypes[0].isAssignableFrom(source.getClass())
                        || !isRichStickerType(method.getReturnType())) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(null, source);
                if (value != null) return value;
            }
        } catch (Throwable ignored) {
            // Fall through to StickerItem.currentImage().
        }
        return null;
    }

    private static boolean isRichStickerType(Class<?> type) {
        String name = type.getName();
        return name.endsWith(".SetSticker")
                || name.endsWith(".VideoSticker")
                || name.endsWith(".IMGiphyInfo");
    }

    static List<String> usableUrlList(Object value) {
        if (!(value instanceof List<?>)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item == null) continue;
            // Over TLS only. These bytes are fetched and handed to a native WebP decoder, so a
            // cleartext mirror is an unauthenticated body reaching a parser written in C.
            String url = item.toString().trim();
            if (url.startsWith("https://")) result.add(url);
        }
        return result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
    }

    private static UrlModel bestResolutionUrl(Object stickerUrlStruct) {
        if (stickerUrlStruct instanceof UrlModel) return (UrlModel) stickerUrlStruct;
        if (stickerUrlStruct == null) return null;
        for (String methodName : new String[]{
                "getHighResolutionUrl", "getMidResolutionUrl", "getLowResolutionUrl"
        }) {
            Object value = invokeNoArg(stickerUrlStruct, methodName);
            if (value instanceof UrlModel && firstUsableUrl((UrlModel) value) != null) {
                return (UrlModel) value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object instance, String methodName) {
        if (instance == null) return null;
        try {
            java.lang.reflect.Method method = instance.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readNamedField(Object instance, String fieldName) {
        if (instance == null) return null;
        Class<?> current = instance.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isAnimatedStickerModel(Object model) {
        if (model == null) return false;
        Class<?> current = model.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field staticFlag = current.getDeclaredField("LIZIZ");
                if (staticFlag.getType() == Boolean.TYPE || staticFlag.getType() == Boolean.class) {
                    staticFlag.setAccessible(true);
                    Object value = staticFlag.get(model);
                    if (value instanceof Boolean) return !((Boolean) value);
                }
            } catch (Throwable ignored) {
                // Continue with the media signature when the target model changes.
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static UrlModel findUrlModel(Object model) {
        if (model == null) return null;

        Class<?> current = model.getClass();
        while (current != null) {
            java.lang.reflect.Field[] fields = current.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof UrlModel) {
                        return (UrlModel) value;
                    }
                } catch (Throwable ignored) {
                    // Keep probing other fields.
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static String extensionFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null) return null;
            int dotIndex = path.lastIndexOf('.');
            if (dotIndex < 0 || dotIndex == path.length() - 1) return null;
            String extension = path.substring(dotIndex + 1).toLowerCase(java.util.Locale.ROOT);
            return extension.length() <= 5 ? extension : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String firstUsableUrl(UrlModel model) {
        List<String> urls = usableUrls(model);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private static List<String> usableUrls(UrlModel model) {
        if (model == null) return Collections.emptyList();
        try {
            return usableUrlList(model.getUrlList());
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static List<View> findViewsByClassName(View root, String... classNames) {
        List<View> matches = new ArrayList<>();
        collectViewsByClassName(root, matches, classNames);
        return matches;
    }

    private static void collectViewsByClassName(View view, List<View> matches, String... classNames) {
        if (view == null) return;

        String viewClassName = view.getClass().getName();
        for (String className : classNames) {
            if (viewClassName.equals(className)) {
                matches.add(view);
                break;
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectViewsByClassName(group.getChildAt(i), matches, classNames);
            }
        }
    }

    private static ViewGroup findCommonParent(List<View> views) {
        if (views.size() < 2) return null;

        View first = views.get(0);
        View second = views.get(1);
        ViewParent parent = first.getParent();
        while (parent != null) {
            if (isAncestor(parent, second)) {
                return parent instanceof ViewGroup ? (ViewGroup) parent : null;
            }
            parent = parent.getParent();
        }

        return null;
    }

    private static boolean isAncestor(ViewParent candidate, View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == candidate) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean hasSaveImageButton(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView && ACTION_LABEL.contentEquals(((TextView) child).getText())) {
                return true;
            }
        }
        return false;
    }

    private static ViewGroup.LayoutParams cloneLayoutParams(ViewGroup.LayoutParams params) {
        if (params instanceof LinearLayout.LayoutParams) {
            return new LinearLayout.LayoutParams((LinearLayout.LayoutParams) params);
        }
        if (params instanceof ViewGroup.MarginLayoutParams) {
            return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) params);
        }
        if (params != null) {
            return new ViewGroup.LayoutParams(params);
        }
        return new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String summarizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "null";

        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path != null && path.length() > 96) {
                path = path.substring(0, 96) + "...";
            }
            return uri.getHost() + path;
        } catch (Throwable ignored) {
            int queryIndex = url.indexOf('?');
            String withoutQuery = queryIndex >= 0 ? url.substring(0, queryIndex) : url;
            return withoutQuery.length() <= 96 ? withoutQuery : withoutQuery.substring(0, 96) + "...";
        }
    }

    private static void toast(Context context, String message) {
        MAIN_HANDLER.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private static void debugLog(String message) {
        if (BaseSettings.DEBUG.get()) {
            Logger.printInfo(() -> message);
        }
    }

    private static final class StickerAsset {
        final String url;
        final List<String> urls;
        final boolean animated;

        StickerAsset(String url, boolean animated) {
            this(Collections.singletonList(url), animated);
        }

        StickerAsset(List<String> urls, boolean animated) {
            this.urls = urls == null || urls.isEmpty()
                    ? Collections.emptyList() : List.copyOf(urls);
            this.url = this.urls.isEmpty() ? "" : this.urls.get(0);
            this.animated = animated;
        }
    }

    static final class MediaFormat {
        final String mimeType;
        final String extension;
        final boolean video;
        final boolean convertToPng;
        final boolean convertToMp4;
        final boolean convertToGif;
        final String label;

        private MediaFormat(
                String mimeType,
                String extension,
                boolean video,
                boolean convertToPng,
                boolean convertToMp4,
                boolean convertToGif,
                String label
        ) {
            this.mimeType = mimeType;
            this.extension = extension;
            this.video = video;
            this.convertToPng = convertToPng;
            this.convertToMp4 = convertToMp4;
            this.convertToGif = convertToGif;
            this.label = label;
        }

        static MediaFormat png() {
            return new MediaFormat("image/png", "png", false, true, false, false, "PNG");
        }

        static MediaFormat mp4() {
            return new MediaFormat("video/mp4", "mp4", true, false, true, false, "MP4");
        }

        static MediaFormat gif() {
            return new MediaFormat("image/gif", "gif", false, false, false, true, "GIF");
        }

        static MediaFormat webp() {
            return passthrough("image/webp", "webp", false, "WebP");
        }

        /**
         * What an animated sticker is written as. WebP is the file TikTok sent, byte for byte,
         * which is also what a messaging app that takes animated stickers wants; the other two
         * are converted here. Anything unrecognised keeps the old behaviour.
         */
        static MediaFormat animated(String choice) {
            if ("gif".equals(choice)) return gif();
            if ("webp".equals(choice)) return webp();
            return mp4();
        }

        static MediaFormat passthrough(String mimeType, String extension, boolean video, String label) {
            return new MediaFormat(mimeType, extension, video, false, false, false, label);
        }
    }

    private static final class SaveResult {
        final boolean success;
        final String message;
        final String path;

        private SaveResult(boolean success, String message, String path) {
            this.success = success;
            this.message = message;
            this.path = path;
        }

        static SaveResult success(String path, String uri, String format) {
            return new SaveResult(true, L10n.f("Sticker saved as %1$s", format),
                    path + " (" + uri + ")");
        }

        static SaveResult failure(String message) {
            return new SaveResult(false, message, null);
        }
    }
}
