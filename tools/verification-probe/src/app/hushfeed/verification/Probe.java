package app.hushfeed.verification;

import android.app.Application;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reaches Hushfeed's own settings on a phone, for the device checks in Roadmap_Blocked.md.
 *
 * <p>Hushfeed hangs its settings screen off TikTok's ad personalisation activity, which is not
 * exported, so adb cannot start it and the row that leads to it sits inside TikTok's Settings and
 * privacy. On an account TikTok has asked to answer a content-reuse migration, that page cannot be
 * opened without answering, and that answer is the account owner's to give. This runs inside
 * TikTok's own process instead, where starting the activity and reading or writing a setting are
 * ordinary calls.
 *
 * <h2>Why the work does not happen in onCreate</h2>
 *
 * <p>The obvious shape, do the work in {@link #onCreate} and answer through {@code am instrument
 * -w}, does not run at all on this target. TikTok replaces the thread's Instrumentation with its
 * own: the clean 46.2.3 build reflects on {@code mInstrumentation} in eight places, among them
 * {@code SharedPreferencesManager.hookInstrumentation} and {@code BTMInstrumentationProxy.hook},
 * and its replacement does not pass onCreate on. So nothing after the swap is a reliable entry
 * point, and there is no result bundle to answer with either.
 *
 * <p>{@link #newApplication} is before the swap. The framework calls it while it is building the
 * Application, which is what runs the app's own attachBaseContext, so it is the last callback that
 * is certainly still this class. That starts a thread, the thread waits for Hushfeed to have a
 * context, and then it registers a receiver. Everything after that arrives as a broadcast and is
 * answered in the log, because the instrumentation has no way back to the shell by then.
 *
 * <p>Nothing here is part of the bundle. It is a test tool, it is signed with the same key as the
 * sideloaded build so the platform lets it in, and it should be uninstalled when the checks are
 * done.
 *
 * <pre>
 *   adb -s S shell am instrument app.hushfeed.verification/.Probe
 *   adb -s S shell am broadcast -a app.hushfeed.verification.PROBE -p com.zhiliaoapp.musically \
 *       -e action dump
 *   adb -s S logcat -d | grep HushfeedProbe
 * </pre>
 */
public final class Probe extends Instrumentation {

    static final String TAG = "HushfeedProbe";
    private static final String ACTION = "app.hushfeed.verification.PROBE";
    private static final String SETTING = "app.morphe.extension.shared.settings.Setting";
    private static final String SETTINGS = "app.morphe.extension.tiktok.settings.Settings";
    private static final String UTILS = "app.morphe.extension.shared.Utils";
    private static final String SETTINGS_ACTIVITY =
            "com.bytedance.ies.ugc.aweme.commercialize.compliance.personalization."
                    + "AdPersonalizationActivity";
    /** How long to let TikTok finish starting before giving up on it. */
    private static final long READY_TIMEOUT_MS = 120_000L;

    private static volatile boolean started;

    @Override
    public Application newApplication(ClassLoader loader, String className, Context context)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Log.i(TAG, "newApplication " + className);
        Application application = super.newApplication(loader, className, context);
        listen(application);
        return application;
    }

    @Override
    public void onCreate(Bundle arguments) {
        // Reached only if the app leaves the thread's instrumentation alone, which this one does
        // not. Kept because it costs nothing and says in the log which path a build took.
        Log.i(TAG, "onCreate");
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Log.i(TAG, "onStart");
    }

    /** Waits for Hushfeed, then takes instructions by broadcast for as long as the app lives. */
    private static synchronized void listen(final Context context) {
        if (started) return;
        started = true;
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ClassLoader loader = waitForHushfeed(context);
                    IntentFilter filter = new IntentFilter(ACTION);
                    BroadcastReceiver receiver = new Commands(context, loader);
                    if (Build.VERSION.SDK_INT >= 33) {
                        // The sender is adb, which is another uid, so the receiver has to be
                        // exported. On a release build of the app there would be no receiver here
                        // at all; this package is only ever installed to run a check.
                        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
                    } else {
                        context.registerReceiver(receiver, filter);
                    }
                    Log.i(TAG, "ready");
                } catch (Throwable error) {
                    Log.e(TAG, "probe could not start", error);
                }
            }
        }, "hushfeed-probe");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Blocks until Hushfeed has a context, and answers with the class loader that has it.
     *
     * <p>This runs while the application is still being built, so the extension's classes are
     * loadable well before any of them has been initialised. Waiting on the context is waiting on
     * the thing every setting read needs.
     */
    private static ClassLoader waitForHushfeed(Context context) throws Exception {
        ClassLoader loader = context.getClassLoader();
        Method getContext = loader.loadClass(UTILS).getMethod("getContext");
        long deadline = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (getContext.invoke(null) != null) return loader;
            Thread.sleep(250L);
        }
        throw new IllegalStateException("Hushfeed never took a context; is this build patched?");
    }

    /** One broadcast, one action, one line in the log. */
    private static final class Commands extends BroadcastReceiver {
        private final Context app;
        private final ClassLoader loader;

        Commands(Context app, ClassLoader loader) {
            this.app = app;
            this.loader = loader;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            if (action == null) action = "dump";
            try {
                switch (action) {
                    case "settings":
                        openSettings();
                        Log.i(TAG, "ok settings opened");
                        break;
                    case "dump":
                        Log.i(TAG, "ok dump\n" + dump());
                        break;
                    case "refresh-shortcuts":
                        refreshShortcuts();
                        Log.i(TAG, "ok refresh-shortcuts asked for");
                        break;
                    case "get": {
                        String key = required(intent, "key");
                        Log.i(TAG, "ok get " + key + "=" + valueOf(find(key)));
                        break;
                    }
                    case "set": {
                        String key = required(intent, "key");
                        String value = required(intent, "value");
                        String before = valueOf(find(key));
                        write(key, value);
                        Log.i(TAG, "ok set " + key + " " + before + " -> " + valueOf(find(key)));
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("unknown action: " + action);
                }
            } catch (Throwable error) {
                Log.e(TAG, "failed " + action, error);
            }
        }

        private String required(Intent intent, String name) {
            String value = intent.getStringExtra(name);
            if (value == null) throw new IllegalArgumentException("-e " + name + " is required");
            return value;
        }

        /** Opens the settings screen the same way the row inside TikTok's own settings does. */
        private void openSettings() throws Exception {
            Intent intent = new Intent(app, loader.loadClass(SETTINGS_ACTIVITY));
            intent.setAction("morphe_settings");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(intent);
        }

        /**
         * Asks TikTok to publish its launcher shortcuts again.
         *
         * <p>Needed because the app publishes them about once, on the first start after an
         * install, and keeps a flag saying it has. The platform carries the published list across
         * a package replace and only rewrites its timestamps, so reinstalling does not exercise
         * the publish path either. Without this there is nothing to watch.
         *
         * <p>The service interface keeps its name; its methods do not. The one wanted is the only
         * one on it taking a scene and a flag, so it is picked by that shape rather than by a
         * name that means nothing and would change with the next build.
         */
        private void refreshShortcuts() throws Exception {
            Class<?> services = loader.loadClass(
                    "com.ss.android.ugc.aweme.framework.services.ServiceManager");
            Object manager = services.getMethod("get").invoke(null);
            Class<?> shortcuts = loader.loadClass(
                    "com.ss.android.ugc.aweme.launcher.service.shortcut.IShortcutService");
            Object service = services.getMethod("getService", Class.class)
                    .invoke(manager, shortcuts);
            if (service == null) throw new IllegalStateException("no IShortcutService");
            Method refresh = null;
            for (Method candidate : shortcuts.getMethods()) {
                Class<?>[] takes = candidate.getParameterTypes();
                if (candidate.getReturnType() == void.class && takes.length == 2
                        && takes[0] == String.class && takes[1] == boolean.class) {
                    if (refresh != null) {
                        throw new IllegalStateException(
                                "IShortcutService has more than one (String, boolean) method, so "
                                        + "the refresh cannot be told apart by its shape any more");
                    }
                    refresh = candidate;
                }
            }
            if (refresh == null) throw new IllegalStateException("no refresh on IShortcutService");
            Log.i(TAG, "refresh is " + refresh.getName());
            refresh.invoke(service, "hushfeed-check", true);
        }

        /**
         * Every setting Hushfeed has created, with its value.
         *
         * <p>Loading the tiktok Settings class is what creates them: each is a static field on it,
         * so until the class is initialised the registry is empty and a lookup finds nothing.
         */
        private String dump() throws Exception {
            List<?> all = (List<?>) registry().getMethod("allLoadedSettings").invoke(null);
            List<String> lines = new ArrayList<>();
            for (Object each : all) lines.add(keyOf(each) + "=" + valueOf(each));
            Collections.sort(lines);
            // String.join arrived in API 26, and this runs on the same floor as the bundle.
            StringBuilder out = new StringBuilder();
            for (String line : lines) {
                if (out.length() != 0) out.append('\n');
                out.append(line);
            }
            return out.toString();
        }

        private void write(String key, String value) throws Exception {
            Object setting = find(key);
            Object current = setting.getClass().getMethod("get").invoke(setting);
            // save is declared final on Setting and generic, so it erases to save(Object).
            loader.loadClass(SETTING).getMethod("save", Object.class)
                    .invoke(setting, coerce(current, value));
        }

        /** The string turned into whatever the setting already holds. */
        @SuppressWarnings({"unchecked", "rawtypes"})
        private Object coerce(Object current, String value) {
            if (current instanceof Boolean) return Boolean.parseBoolean(value);
            if (current instanceof Integer) return Integer.parseInt(value);
            if (current instanceof Long) return Long.parseLong(value);
            if (current instanceof Float) return Float.parseFloat(value);
            if (current instanceof Enum) return Enum.valueOf((Class<Enum>) current.getClass(), value);
            return value;
        }

        private Object find(String key) throws Exception {
            Object found = registry().getMethod("getSettingFromPath", String.class)
                    .invoke(null, key);
            if (found == null) throw new IllegalArgumentException("no setting called " + key);
            return found;
        }

        private Class<?> registry() throws Exception {
            Class.forName(SETTINGS, true, loader);
            return loader.loadClass(SETTING);
        }

        private String keyOf(Object setting) throws Exception {
            return String.valueOf(setting.getClass().getField("key").get(setting));
        }

        private String valueOf(Object setting) throws Exception {
            return String.valueOf(setting.getClass().getMethod("get").invoke(setting));
        }
    }
}
