/*
 * Copyright (c) 2026 Metra TikTok Patches
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.download;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;

import java.io.FileDescriptor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Converts TikTok animated WebP sticker frames to a Gallery-compatible MP4. */
final class AnimatedWebpMp4Converter {
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 30;
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long CODEC_TIMEOUT_US = 10_000L;

    private AnimatedWebpMp4Converter() {
    }

    static void convert(byte[] webpData, FileDescriptor output) throws Exception {
        convert(webpData, new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4));
    }

    static void convert(byte[] webpData, String outputPath) throws Exception {
        convert(webpData, new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4));
    }

    private static void convert(byte[] webpData, MediaMuxer muxer) throws Exception {
        Object image = null;
        MediaCodec encoder = null;
        CodecSurface codecSurface = null;
        boolean muxerStarted = false;
        try {
            Class<?> imageClass = Class.forName("com.facebook.animated.webp.WebPImage");
            Method create = imageClass.getDeclaredMethod("create", byte[].class);
            create.setAccessible(true);
            image = create.invoke(null, (Object) webpData);
            if (image == null) throw new IllegalStateException("WebP decoder returned null");

            int sourceWidth = invokeInt(image, "getWidth");
            int sourceHeight = invokeInt(image, "getHeight");
            int frameCount = invokeInt(image, "getFrameCount");
            int[] durations = (int[]) invoke(image, "getFrameDurations");
            if (sourceWidth <= 0 || sourceHeight <= 0 || frameCount <= 0) {
                throw new IllegalStateException("Invalid animated WebP dimensions or frame count");
            }

            int outputWidth = sourceWidth + (sourceWidth & 1);
            int outputHeight = sourceHeight + (sourceHeight & 1);
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, outputWidth, outputHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, chooseBitRate(outputWidth, outputHeight));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            Surface inputSurface = encoder.createInputSurface();
            codecSurface = new CodecSurface(inputSurface, outputWidth, outputHeight);
            encoder.start();

            Bitmap composed = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
            Canvas composedCanvas = new Canvas(composed);
            Paint sourcePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            Paint replacePaint = new Paint(sourcePaint);
            replacePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
            Paint clearPaint = new Paint();
            clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

            int previousX = 0;
            int previousY = 0;
            int previousWidth = 0;
            int previousHeight = 0;
            boolean clearPrevious = false;
            long presentationTimeNs = 0L;
            EncoderState state = new EncoderState();

            try {
                for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                    if (clearPrevious) {
                        composedCanvas.drawRect(
                                previousX,
                                previousY,
                                previousX + previousWidth,
                                previousY + previousHeight,
                                clearPaint
                        );
                    }

                    Object frame = null;
                    Bitmap frameBitmap = null;
                    try {
                        frame = invokeFrame(image, frameIndex);
                        int frameWidth = invokeInt(frame, "getWidth");
                        int frameHeight = invokeInt(frame, "getHeight");
                        int xOffset = invokeInt(frame, "getXOffset");
                        int yOffset = invokeInt(frame, "getYOffset");
                        validateFrame(sourceWidth, sourceHeight, frameWidth, frameHeight, xOffset, yOffset);

                        boolean blend = invokeBoolean(frame, "LIZ");
                        clearPrevious = invokeBoolean(frame, "LIZIZ");
                        frameBitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
                        invoke(frame, "renderFrame", new Class<?>[]{
                                int.class, int.class, Bitmap.class
                        }, frameWidth, frameHeight, frameBitmap);
                        composedCanvas.drawBitmap(
                                frameBitmap,
                                xOffset,
                                yOffset,
                                blend ? sourcePaint : replacePaint
                        );

                        codecSurface.draw(composed, presentationTimeNs);
                        drainEncoder(encoder, muxer, false, state);

                        int durationMs = durations != null && frameIndex < durations.length
                                ? durations[frameIndex]
                                : 100;
                        presentationTimeNs += Math.max(10, durationMs) * 1_000_000L;
                        previousX = xOffset;
                        previousY = yOffset;
                        previousWidth = frameWidth;
                        previousHeight = frameHeight;
                    } finally {
                        if (frameBitmap != null) frameBitmap.recycle();
                        dispose(frame);
                    }
                }

                encoder.signalEndOfInputStream();
                drainEncoder(encoder, muxer, true, state);
                muxerStarted = state.muxerStarted;
            } finally {
                composed.recycle();
                sourcePaint.setXfermode(null);
                replacePaint.setXfermode(null);
                clearPaint.setXfermode(null);
            }
        } finally {
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (Throwable ignored) {
                    // An encoder that never started, or already failed, refuses to stop. The
                    // release below is what has to happen either way.
                }
                encoder.release();
            }
            if (codecSurface != null) codecSurface.release();
            try {
                if (muxerStarted) muxer.stop();
            } finally {
                muxer.release();
            }
            dispose(image);
        }
    }

    static int chooseBitRate(int width, int height) {
        long proposed = (long) width * height * 4L;
        return (int) Math.max(500_000L, Math.min(8_000_000L, proposed));
    }

    private static void drainEncoder(
            MediaCodec encoder,
            MediaMuxer muxer,
            boolean endOfStream,
            EncoderState state
    ) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (state.muxerStarted) throw new IllegalStateException("Encoder format changed twice");
                state.trackIndex = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                state.muxerStarted = true;
            } else if (outputIndex >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputIndex);
                if (outputBuffer == null) throw new IllegalStateException("Encoder output buffer was null");
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0) {
                    if (!state.muxerStarted) throw new IllegalStateException("Muxer has not started");
                    outputBuffer.position(info.offset);
                    outputBuffer.limit(info.offset + info.size);
                    muxer.writeSampleData(state.trackIndex, outputBuffer, info);
                }
                encoder.releaseOutputBuffer(outputIndex, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
    }

    private static Object invokeFrame(Object image, int index) throws Exception {
        for (Method method : image.getClass().getMethods()) {
            if (!"getFrame".equals(method.getName()) || method.getParameterTypes().length != 1) continue;
            if (!method.getReturnType().getName().endsWith("WebPFrame")) continue;
            method.setAccessible(true);
            return method.invoke(image, index);
        }
        throw new NoSuchMethodException("WebPImage.getFrame(int)");
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        return invoke(target, methodName, new Class<?>[0]);
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
        return ((Number) invoke(target, methodName)).intValue();
    }

    private static boolean invokeBoolean(Object target, String methodName) throws Exception {
        return (Boolean) invoke(target, methodName);
    }

    private static void dispose(Object target) {
        if (target == null) return;
        try {
            invoke(target, "dispose");
        } catch (Throwable ignored) {
            // Best effort on a decoder this build may not have, or may already have closed.
            // Nothing the caller can do about it, and the conversion is finished either way.
        }
    }

    static void validateFrame(
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

    private static final class EncoderState {
        int trackIndex = -1;
        boolean muxerStarted;
    }

    private static final class CodecSurface {
        private static final float[] VERTICES = {
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
        };
        private static final String VERTEX_SHADER =
                "attribute vec2 aPosition;attribute vec2 aTexCoord;varying vec2 vTexCoord;" +
                        "void main(){gl_Position=vec4(aPosition,0.0,1.0);vTexCoord=aTexCoord;}";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;uniform sampler2D uTexture;varying vec2 vTexCoord;" +
                        "void main(){gl_FragColor=texture2D(uTexture,vTexCoord);}";

        private final Surface surface;
        private final EGLDisplay display;
        private final EGLContext context;
        private final EGLSurface eglSurface;
        private final int program;
        private final int texture;
        private final FloatBuffer vertices;
        private final int width;
        private final int height;

        CodecSurface(Surface surface, int width, int height) {
            this.surface = surface;
            this.width = width;
            this.height = height;
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("No EGL display");
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw new IllegalStateException("Could not initialize EGL");
            }
            int[] configAttributes = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    0x3142, 1,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] configCount = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, configCount, 0)
                    || configCount[0] == 0) {
                throw new IllegalStateException("No recordable EGL config");
            }
            int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(
                    display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0
            );
            int[] surfaceAttributes = {EGL14.EGL_NONE};
            eglSurface = EGL14.eglCreateWindowSurface(
                    display, configs[0], surface, surfaceAttributes, 0
            );
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
                throw new IllegalStateException("Could not make codec EGL surface current");
            }

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            texture = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            vertices = ByteBuffer.allocateDirect(VERTICES.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertices.put(VERTICES).position(0);
        }

        void draw(Bitmap bitmap, long presentationTimeNs) {
            GLES20.glViewport(0, 0, width, height);
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(program);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            int positionLocation = GLES20.glGetAttribLocation(program, "aPosition");
            int textureLocation = GLES20.glGetAttribLocation(program, "aTexCoord");
            vertices.position(0);
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 16, vertices);
            GLES20.glEnableVertexAttribArray(positionLocation);
            vertices.position(2);
            GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 16, vertices);
            GLES20.glEnableVertexAttribArray(textureLocation);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, presentationTimeNs);
            if (!EGL14.eglSwapBuffers(display, eglSurface)) {
                throw new IllegalStateException("Could not submit MP4 frame");
            }
        }

        void release() {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                        display,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT
                );
                EGL14.eglDestroySurface(display, eglSurface);
                EGL14.eglDestroyContext(display, context);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(display);
            }
            surface.release();
        }

        private static int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            int result = GLES20.glCreateProgram();
            GLES20.glAttachShader(result, vertexShader);
            GLES20.glAttachShader(result, fragmentShader);
            GLES20.glLinkProgram(result);
            int[] status = new int[1];
            GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            if (status[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(result);
                GLES20.glDeleteProgram(result);
                throw new IllegalStateException("Could not link sticker video shader: " + log);
            }
            return result;
        }

        private static int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] status = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("Could not compile sticker video shader: " + log);
            }
            return shader;
        }
    }
}
