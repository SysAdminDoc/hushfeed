/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 *
 * Built on icysymmetra/tiktok-patches-for-morphe (GPL-3.0).
 */
package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns TikTok's animated WebP sticker frames into a GIF.
 *
 * WebP frames can be a patch of the picture that blends over what came before, while a GIF
 * frame here is the whole picture, so the frames are painted onto a running canvas and each
 * complete state is handed to {@link GifEncoder}. The reflection into Fresco's decoder mirrors
 * the MP4 and APNG converters beside this one; TikTok ships that decoder, this repository does
 * not, so it can only be reached by name.
 */
final class AnimatedWebpGifConverter {
    /** A cap on the frames held at once, so a long sticker cannot run the app out of memory. */
    private static final long MAX_PIXELS = 8L * 1024 * 1024;

    private AnimatedWebpGifConverter() {
    }

    static void convert(byte[] webpData, OutputStream outputStream) throws Exception {
        Object image = null;
        Bitmap canvas = null;
        try {
            Class<?> imageClass = Class.forName("com.facebook.animated.webp.WebPImage");
            Method create = imageClass.getDeclaredMethod("create", byte[].class);
            create.setAccessible(true);
            image = create.invoke(null, (Object) webpData);
            if (image == null) throw new IllegalStateException("WebP decoder returned null");

            int width = invokeInt(image, "getWidth");
            int height = invokeInt(image, "getHeight");
            int frameCount = invokeInt(image, "getFrameCount");
            int[] durations = (int[]) invoke(image, "getFrameDurations");
            if (width <= 0 || height <= 0 || frameCount <= 0) {
                throw new IllegalStateException("Invalid animated WebP dimensions or frame count");
            }
            // libwebp's demuxer stores a still picture as one frame and reports a count of 1,
            // so "it decoded" is not the same as "it is an animation". Writing that out would
            // produce a one-frame GIF from a still, which is worse than refusing it.
            if (frameCount < 2) {
                throw new IllegalStateException("WebP is a still picture, not an animation");
            }
            if ((long) width * height * frameCount > MAX_PIXELS) {
                throw new IllegalStateException("Animated WebP is too large to hold as a GIF");
            }

            canvas = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas painter = new Canvas(canvas);
            Paint clear = new Paint();
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            List<GifEncoder.Frame> frames = new ArrayList<>(frameCount);
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                Object frame = null;
                Bitmap piece = null;
                try {
                    frame = invokeFrame(image, frameIndex);
                    int frameWidth = invokeInt(frame, "getWidth");
                    int frameHeight = invokeInt(frame, "getHeight");
                    int xOffset = invokeInt(frame, "getXOffset");
                    int yOffset = invokeInt(frame, "getYOffset");
                    validateFrame(width, height, frameWidth, frameHeight, xOffset, yOffset);
                    boolean blend = invokeBoolean(frame, "LIZ");
                    boolean disposeToBackground = invokeBoolean(frame, "LIZIZ");

                    piece = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
                    invoke(frame, "renderFrame", new Class<?>[]{
                            int.class, int.class, Bitmap.class
                    }, frameWidth, frameHeight, piece);

                    // A frame that does not blend replaces what is under it, transparency and all.
                    if (!blend) {
                        painter.drawRect(xOffset, yOffset, xOffset + frameWidth, yOffset + frameHeight, clear);
                    }
                    painter.drawBitmap(piece, xOffset, yOffset, null);

                    int[] pixels = new int[width * height];
                    canvas.getPixels(pixels, 0, width, 0, 0, width, height);
                    int duration = durations != null && frameIndex < durations.length
                            ? durations[frameIndex]
                            : 100;
                    frames.add(new GifEncoder.Frame(pixels, duration));

                    if (disposeToBackground) {
                        painter.drawRect(xOffset, yOffset, xOffset + frameWidth, yOffset + frameHeight, clear);
                    }
                } finally {
                    if (piece != null) piece.recycle();
                    dispose(frame);
                }
            }

            GifEncoder.write(outputStream, width, height, frames);
            outputStream.flush();
        } finally {
            if (canvas != null) canvas.recycle();
            dispose(image);
        }
    }

    private static Object invokeFrame(Object image, int index) throws Exception {
        for (Method method : image.getClass().getMethods()) {
            if (!"getFrame".equals(method.getName()) || method.getParameterTypes().length != 1) continue;
            method.setAccessible(true);
            Object frame = method.invoke(image, index);
            if (frame == null) throw new IllegalStateException("WebP frame " + index + " is missing");
            return frame;
        }
        throw new NoSuchMethodException("getFrame(int)");
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object... arguments
    ) throws Exception {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static int invokeInt(Object target, String methodName) throws Exception {
        return (Integer) invoke(target, methodName);
    }

    private static boolean invokeBoolean(Object target, String methodName) throws Exception {
        return Boolean.TRUE.equals(invoke(target, methodName));
    }

    private static void dispose(Object target) {
        if (target == null) return;
        try {
            invoke(target, "dispose");
        } catch (Throwable ignored) {
            // Native resources are also finalized by Fresco; explicit disposal is best effort.
        }
    }

    private static void validateFrame(
            int canvasWidth,
            int canvasHeight,
            int frameWidth,
            int frameHeight,
            int xOffset,
            int yOffset
    ) {
        if (frameWidth <= 0 || frameHeight <= 0 || xOffset < 0 || yOffset < 0
                || (long) xOffset + frameWidth > canvasWidth
                || (long) yOffset + frameHeight > canvasHeight) {
            throw new IllegalStateException("Animated WebP frame is outside its canvas");
        }
    }
}
