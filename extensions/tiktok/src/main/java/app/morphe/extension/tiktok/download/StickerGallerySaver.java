/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */

package app.morphe.extension.tiktok.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import androidx.annotation.RequiresApi;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.diagnostics.HookStatus;

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
import java.lang.ref.WeakReference;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

@SuppressWarnings("unused")
public final class StickerGallerySaver {
    private static final String ACTION_LABEL = "Save media";
    private static final String HOOK_FAMILY = "sticker saves";

    static {
        // The save button reads no switch, so Pause leaves it working; the export says so.
        HookStatus.runsWhilePaused(HOOK_FAMILY);
    }
    /**
     * Marks the button this file added, so finding it again does not depend on its wording.
     *
     * <p>The label was the check: a sheet already carrying a child reading "Save media" was
     * taken as done. Translating the label alone would have given every German sheet a second
     * button, because the first one no longer said what the check was looking for.
     */
    private static final String SAVE_BUTTON_TAG = "morphe_save_media";
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

            List<View> actionButtons = findActionButtons(sheetView);
            ViewGroup actionParent = findCommonParent(actionButtons);
            if (actionParent == null || actionButtons.size() < 2) {
                // Said in the export, not only in the debug log: this is how issue #23 went
                // unnoticed, a sheet with no Save media button and nothing anywhere saying why.
                HookStatus.missingMember(HOOK_FAMILY, "view", sheetView.getClass().getName(),
                        "two action buttons of one type");
                debugLog("[Morphe Stickers] action parent unavailable buttons=" + actionButtons.size());
                return;
            }
            HookStatus.bound(HOOK_FAMILY, "sheet action buttons");
            if (hasSaveImageButton(actionParent)) return;

            View template = lastChildOf(actionParent, actionButtons);
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
        button.setText(L10n.t(button.getContext(), ACTION_LABEL));
        button.setTag(SAVE_BUTTON_TAG);
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
            boolean dark = SettingsUi.isDarkContext(template.getContext());
            int textColor = SettingsUi.textPrimaryOn(dark);
            button.setTextColor(SettingsUi.enabledTextColors(textColor));
            button.setTextSize(16);
            int paddingHorizontal = SettingsUi.dp(context, 16);
            int paddingVertical = SettingsUi.dp(context, 10);
            button.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
        }

        Drawable background = template.getBackground();
        if (background != null && background.getConstantState() != null) {
            button.setBackground(background.getConstantState().newDrawable().mutate());
        } else {
            boolean dark = SettingsUi.isDarkContext(template.getContext());
            button.setBackground(SettingsUi.overlayAction(context, SettingsUi.RADIUS_CONTROL,
                    SettingsUi.textPrimaryOn(dark)));
        }

        button.setEnabled(template.isEnabled());
        button.setClickable(true);
        button.setFocusable(true);
        button.setAlpha(template.getAlpha());
        // A clickable TextView announces as text, so this was offered to a screen reader as a
        // label rather than as something to press, and the same helper the settings screen's
        // hand built actions use now gives it the role, the action and its enabled state.
        SettingsUi.markAsButton(button);
        // The template is one of TikTok's own row labels and can be shorter than a finger. The
        // minimum is a floor, not a height, so a large font scale still grows the button rather
        // than clipping it.
        int minimum = SettingsUi.dp(context, 48);
        button.setMinHeight(Math.max(button.getMinHeight(), minimum));
        button.setMinWidth(Math.max(button.getMinWidth(), minimum));
        return button;
    }

    private static void saveStickerFromButton(View button, StickerAsset asset) {
        Context context = button.getContext().getApplicationContext();
        SettingsUi.setBusy(button, true,
                L10n.t(button.getContext(), "Saving"));
        Utils.showToastShort(L10n.t("Saving the sticker"));

        // A submitted job can wait behind eight others, then run up to the two minute deadline.
        // Capturing the button would hold the sheet's Activity for that whole window after the
        // sheet itself was gone. Every other view this file keeps hold of is already weak.
        WeakReference<View> anchor = new WeakReference<>(button);
        boolean submitted = MediaJobScheduler.submit(
                "sticker", stickerSaveWork(context, asset, anchor));
        if (!submitted) handBackLater(anchor).run();
    }

    /** The work one sticker save does, holding the sheet by nothing stronger than {@code anchor}. */
    static Runnable stickerSaveWork(Context context, StickerAsset asset, WeakReference<View> anchor) {
        return () -> {
            SaveResult result = saveSticker(context, asset);
            MAIN_HANDLER.post(() -> {
                handBack(anchor);
                Utils.showToastShort(result.message);
                if (result.success) {
                    debugLog("[Morphe Stickers] saved sticker path=" + result.path);
                } else if (BaseSettings.DEBUG.get()) {
                    Logger.printInfo(() -> "[Morphe Stickers] save failed reason=" + result.message
                            + " url=" + summarizeUrl(asset.url));
                }
            });
        };
    }

    /** Hands the button back on the main thread, for a save that never ran. */
    static Runnable handBackLater(WeakReference<View> anchor) {
        return () -> MAIN_HANDLER.post(() -> handBack(anchor));
    }

    /** Re-enables the Save button, unless the sheet that owned it has already gone. */
    private static void handBack(WeakReference<View> anchor) {
        View button = anchor.get();
        if (button != null) SettingsUi.setBusy(button, false, null);
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
        return SaveResult.failure(L10n.t("The sticker couldn't be saved. Try again."));
    }

    private static String downloadSticker(
            String url,
            File target,
            Context context,
            MediaBudget.Deadline deadline
    ) throws IOException {
        return downloadSticker(url, target, context, deadline, MediaTransport.DEFAULT);
    }

    static String downloadSticker(
            String url,
            File target,
            Context context,
            MediaBudget.Deadline deadline,
            MediaTransport.Client transport
    ) throws IOException {
        IOException failure = new IOException("Sticker URL failed");
        for (int attempt = 0; attempt < MediaBudget.MAX_ATTEMPTS_PER_MIRROR; attempt++) {
            try (MediaTransport.Response response = transport.open(
                    url, deadline, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS,
                    "TikTok 46.2.3 Morphe", false)) {
                MediaBudget.check(deadline);
                int responseCode = response.statusCode;
                if (MediaBudget.isTransientStatus(responseCode)
                        && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                    MediaBudget.waitBeforeRetry(response.header("Retry-After"), attempt, deadline);
                    continue;
                }
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("Sticker server returned " + responseCode);
                }
                long declaredLength = contentLength(response.header("Content-Length"));
                MediaBudget.checkTransferLength(declaredLength);
                MediaBudget.checkDiskSpace(context.getCacheDir(), declaredLength, deadline);
                try (InputStream input = response.inputStream();
                     OutputStream output = new FileOutputStream(target)) {
                    MediaFileWriter.copy(input, output, MAX_STICKER_BYTES, deadline,
                            target.getParentFile());
                }
                return response.contentType();
            } catch (IOException | RuntimeException error) {
                boolean cleaned = MediaCache.delete(target);
                boolean retryable = MediaBudget.isRetryableTransport(error);
                if (cleaned && retryable && attempt + 1 < MediaBudget.MAX_ATTEMPTS_PER_MIRROR) {
                    MediaBudget.waitBeforeRetry(null, attempt, deadline);
                    continue;
                }
                failure.addSuppressed(new IOException(
                        "Sticker attempt failed (" + error.getClass().getSimpleName() + "): "
                                + summarizeUrl(url), error));
                if (!cleaned) failure.addSuppressed(
                        new IOException("Could not remove partial sticker output"));
                break;
            }
        }
        throw failure;
    }

    private static long contentLength(String header) {
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
                    L10n.t("That sticker is in a format Hushfeed can't read"));

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
            // The row went in under a placeholder MediaStore could not collide with, so the
            // publish is where it takes the name the reader asked for.
            complete.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
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

    // Carries the converter's own API 26 floor up to the call site, which is behind a Q check.
    @RequiresApi(26)
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
        // The row went in under a placeholder, so a publish that does not name the file leaves
        // one called hushfeed-pending-1a2b3c.webp in the gallery. Nothing here reads the name
        // back afterwards, so this is where that gets caught.
        if (values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) == null) {
            throw new IOException("Sticker publish did not name the file");
        }
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
        // Locale.ROOT: a Turkish phone lowercases "IMAGE/GIF" to "ımage/gıf", which matches nothing.
        String value = (separator >= 0 ? contentType.substring(0, separator) : contentType).trim()
                .toLowerCase(java.util.Locale.ROOT);
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
        Object source;
        synchronized (STICKER_SOURCES) {
            source = STICKER_SOURCES.get(model);
        }
        StickerAsset sourceAsset = findSourceStickerAsset(source);
        if (sourceAsset != null) {
            HookStatus.bound(HOOK_FAMILY, source.getClass().getName() + "#source adapter");
            return sourceAsset;
        }

        UrlModel urlModel = findUrlModel(model);
        List<String> urls = usableUrls(urlModel);
        if (urls.isEmpty()) {
            if (source != null && !hasKnownStickerSourceMember(source)) {
                HookStatus.missingMember(
                        HOOK_FAMILY,
                        "source adapter",
                        source.getClass().getName(),
                        "LLILLIZIL or X.0UD5");
            }
            return null;
        }
        return new StickerAsset(urls, isAnimatedStickerModel(model));
    }

    private static StickerAsset findSourceStickerAsset(Object source) {
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
                if (MediaTransport.hasAllowedShape(directUrl)) {
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

        java.lang.reflect.Method adapter = richStickerAdapter(source);
        try {
            if (adapter != null) {
                adapter.setAccessible(true);
                Object value = adapter.invoke(null, source);
                if (value != null) return value;
            }
        } catch (Throwable ignored) {
            // Fall through to StickerItem.currentImage().
        }
        return null;
    }

    /**
     * Whether this source still exposes any supported route to a sticker.
     *
     * <p>The legacy field, the conversion helper and StickerItem are alternatives. Reporting
     * each miss while another one works would call a healthy build broken, so this is checked
     * only after the source and preview routes both fail.
     */
    private static boolean hasKnownStickerSourceMember(Object source) {
        if (hasNamedField(source.getClass(), "LLILLIZIL")) return true;
        for (String method : new String[]{
                "getStaticUrl", "getAnimateUrl", "getAnimatedUrl", "currentImage"
        }) {
            if (hasNoArgMethod(source.getClass(), method)) return true;
        }
        return richStickerAdapter(source) != null;
    }

    private static boolean hasNamedField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                current.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) {
                // Keep climbing.
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasNoArgMethod(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static java.lang.reflect.Method richStickerAdapter(Object source) {
        try {
            Class<?> helperClass = Class.forName("X.0UD5");
            for (java.lang.reflect.Method method : helperClass.getDeclaredMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && parameterTypes.length == 1
                        && parameterTypes[0].isAssignableFrom(source.getClass())
                        && isRichStickerType(method.getReturnType())) {
                    return method;
                }
            }
        } catch (Throwable ignored) {
            // The caller decides whether another supported source route remains.
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
            if (MediaTransport.hasAllowedShape(url)) result.add(url);
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

    /**
     * The sheet's own action buttons, found by the sheet's shape.
     *
     * <p>TikTok's button is a TextView subclass whose name changes with every build: X.0GSy on
     * 46.2.3, X.02Lg on 47.0.3, where X.0GSy is an unrelated class. Naming it is what left
     * 47.0.3 with no Save media button (issue #23). What holds on all five retained builds is
     * that the row keeps its two actions in fields of one shared type, while its caption is the
     * only field of its own type. So the actions are the largest set of attached TextView fields
     * declared with the same type, and a lone TextView is never one of them.
     */
    static List<View> findActionButtons(View sheetView) {
        java.util.Map<Class<?>, List<View>> byType = new java.util.LinkedHashMap<>();
        for (Class<?> type = sheetView.getClass();
             type != null && !type.getName().startsWith("android."); type = type.getSuperclass()) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || !TextView.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(sheetView);
                    if (!(value instanceof View) || ((View) value).getParent() == null) continue;
                    List<View> sameType = byType.get(field.getType());
                    if (sameType == null) byType.put(field.getType(), sameType = new ArrayList<>());
                    if (!sameType.contains(value)) sameType.add((View) value);
                } catch (Throwable unreadable) {
                    // A field this build will not hand over is not an action button.
                }
            }
        }
        List<View> actions = Collections.emptyList();
        for (List<View> sameType : byType.values()) {
            if (sameType.size() >= 2 && sameType.size() > actions.size()) actions = sameType;
        }
        return actions;
    }

    /** The action that sits last in its parent, whatever order the fields were declared in. */
    private static View lastChildOf(ViewGroup parent, List<View> views) {
        View last = views.get(views.size() - 1);
        for (View view : views) {
            if (parent.indexOfChild(view) > parent.indexOfChild(last)) last = view;
        }
        return last;
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
            if (SAVE_BUTTON_TAG.equals(parent.getChildAt(i).getTag())) return true;
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

    private static void debugLog(String message) {
        if (BaseSettings.DEBUG.get()) {
            Logger.printInfo(() -> message);
        }
    }

    /** Package-private with {@link #stickerSaveWork}, which a test builds directly. */
    static final class StickerAsset {
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
            // The four other savers say "saved to DCIM/TikTok/..."; this said "saved as MP4"
            // and kept the path for the debug log. The folder is what a reader needs, not the
            // format, because the format is obvious from the file and the folder is not.
            String folder = path;
            int slash = folder.lastIndexOf('/');
            if (slash > 0) folder = folder.substring(0, slash);
            return new SaveResult(true, L10n.f("Sticker saved to %1$s", folder),
                    path + " (" + uri + ")");
        }

        static SaveResult failure(String message) {
            return new SaveResult(false, message, null);
        }
    }
}
