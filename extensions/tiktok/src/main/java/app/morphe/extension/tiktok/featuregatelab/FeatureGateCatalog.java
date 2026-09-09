/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.extension.tiktok.featuregatelab;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

public final class FeatureGateCatalog {
    private static final String TAG = "MorpheFeatureGateLab";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object LOCK = new Object();

    private static volatile List<Entry> staticEntries;
    private static volatile Snapshot cachedSnapshot;

    /**
     * AB key to the type the catalogue declares for it, and nothing else.
     *
     * <p>The runtime's AB fallback asks the catalogue one question: does this key's declared type
     * match the type the saved rule carries. Answering it through {@link #cachedSnapshot} meant a
     * phone with one AB rule saved held all 16,052 entries for the life of the process, on a
     * launch where the Lab screen was never opened. This holds one string pair per AB key, and
     * the type strings are shared rather than one per line.
     */
    private static volatile Map<String, String> cachedAbTypes;

    private FeatureGateCatalog() {
    }

    public interface Callback {
        void onLoaded(Snapshot snapshot);

        void onError(String message);
    }

    public interface AbTypesCallback {
        void onLoaded(Map<String, String> abTypes);

        void onError(String message);
    }

    /**
     * Loads {@link #cachedAbTypes} without building a snapshot.
     *
     * <p>It reads the same two places {@link #merge} does for AB entries, in the same order: the
     * static catalogue first, then TikTok's own AB store for keys the catalogue does not carry,
     * so a key answers with the same type either way.
     */
    public static void loadAbTypesAsync(AbTypesCallback callback) {
        Map<String, String> cached = cachedAbTypes;
        if (cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                Map<String, String> types = readAbTypes();
                cachedAbTypes = types;
                MAIN.post(() -> callback.onLoaded(types));
                Log.i(TAG, "catalog ab_types=" + types.size());
            } catch (Throwable throwable) {
                String message = throwable.getClass().getSimpleName()
                        + ": " + String.valueOf(throwable.getMessage());
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    public static Map<String, String> cachedAbTypes() {
        return cachedAbTypes;
    }

    public static void loadAsync(boolean refreshCurrentCache, Callback callback) {
        Snapshot cached = cachedSnapshot;
        if (!refreshCurrentCache && cached != null) {
            MAIN.post(() -> callback.onLoaded(cached));
            return;
        }
        EXECUTOR.execute(() -> {
            long startedAt = System.currentTimeMillis();
            try {
                Map<String, Map<String, Object>> current = readCurrentKevaMaps();
                long currentReadyAt = System.currentTimeMillis();
                List<Entry> base = staticEntries;
                if (base == null) {
                    Snapshot currentOnly = merge(Collections.emptyList(), current, false);
                    MAIN.post(() -> callback.onLoaded(currentOnly));
                    synchronized (LOCK) {
                        base = staticEntries;
                        if (base == null) {
                            base = Collections.unmodifiableList(readStaticCatalog());
                            staticEntries = base;
                        }
                    }
                }
                long catalogReadyAt = System.currentTimeMillis();
                Snapshot snapshot = merge(base, current, true);
                cachedSnapshot = snapshot;
                cachedAbTypes = abTypesOf(snapshot);
                MAIN.post(() -> callback.onLoaded(snapshot));
                Log.i(TAG, "catalog current_ms=" + (currentReadyAt - startedAt)
                        + " static_ms=" + (catalogReadyAt - currentReadyAt)
                        + " merge_ms=" + (System.currentTimeMillis() - catalogReadyAt)
                        + " loaded=" + snapshot.loadedCount
                        + " total=" + snapshot.entries.size());
            } catch (Throwable throwable) {
                String message = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
                MAIN.post(() -> callback.onError(message));
            }
        });
    }

    public static Snapshot cachedSnapshot() {
        return cachedSnapshot;
    }

    static void awaitForTests() throws Exception {
        EXECUTOR.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    static void resetForTests() {
        staticEntries = null;
        cachedSnapshot = null;
        cachedAbTypes = null;
    }

    /** The AB half of a loaded snapshot, so both paths answer a key the same way. */
    private static Map<String, String> abTypesOf(Snapshot snapshot) {
        String prefix = FeatureGateLabStore.MANAGER_ABMOCK + "\n";
        Map<String, String> result = new HashMap<>();
        Map<String, String> shared = new HashMap<>();
        for (Map.Entry<String, Entry> item : snapshot.byIdentity.entrySet()) {
            if (!item.getKey().startsWith(prefix)) {
                continue;
            }
            result.put(item.getValue().key, share(shared, item.getValue().type));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> readAbTypes() throws Exception {
        Map<String, String> result = new HashMap<>();
        Map<String, String> shared = new HashMap<>();
        appendStaticAbTypes(result, shared, GeneratedFeatureGateCatalog.GZIP_BASE64);
        appendStaticAbTypes(result, shared, GeneratedPlayerFeatureGateCatalog.GZIP_BASE64);
        appendStaticAbTypes(result, shared, GeneratedVeFeatureGateCatalog.GZIP_BASE64);

        // A key TikTok's AB store carries that the catalogue does not becomes an entry of its own
        // in a snapshot, typed from the value that is there. The static type wins where both have
        // the key, which is what merge does by removing the key as it walks the static entries.
        for (Map.Entry<String, Object> item
                : readKevaMapSafely("libra_config_center_repo").entrySet()) {
            if (result.containsKey(item.getKey())) {
                continue;
            }
            String dynamicType = runtimeType(item.getValue());
            if (!FeatureGateLabStore.supportsOverride(
                    FeatureGateLabStore.MANAGER_ABMOCK, dynamicType)) {
                continue;
            }
            result.put(item.getKey(), share(shared, dynamicType));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void appendStaticAbTypes(
            Map<String, String> result,
            Map<String, String> shared,
            String[] chunks
    ) throws Exception {
        try (BufferedReader reader = openCatalog(chunks)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 10) {
                    continue;
                }
                // The same three conditions an Entry would have to meet to reach byIdentity:
                // the AB manager, actionable, and a type the Lab can override.
                if (!FeatureGateLabStore.MANAGER_ABMOCK.equals(fields[1])
                        || !"1".equals(fields[3])
                        || !FeatureGateLabStore.supportsOverride(fields[1], fields[2])) {
                    continue;
                }
                result.put(fields[0], share(shared, fields[2]));
            }
        }
    }

    /** One instance per distinct type, rather than one per line of the catalogue. */
    private static String share(Map<String, String> shared, String type) {
        String existing = shared.get(type);
        if (existing != null) {
            return existing;
        }
        shared.put(type, type);
        return type;
    }

    private static BufferedReader openCatalog(String[] chunks) throws Exception {
        StringBuilder encoded = new StringBuilder();
        for (String chunk : chunks) {
            encoded.append(chunk);
        }
        byte[] compressed = Base64.decode(encoded.toString(), Base64.DEFAULT);
        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(compressed)),
                StandardCharsets.UTF_8));
    }

    private static List<Entry> readStaticCatalog() throws Exception {
        List<Entry> result = new ArrayList<>(
                GeneratedFeatureGateCatalog.ENTRY_COUNT
                        + GeneratedPlayerFeatureGateCatalog.ENTRY_COUNT
                        + GeneratedVeFeatureGateCatalog.ENTRY_COUNT
                        + GeneratedSettingsManagerCatalog.ENTRY_COUNT
        );
        appendStaticCatalog(result, GeneratedFeatureGateCatalog.GZIP_BASE64);
        appendStaticCatalog(result, GeneratedPlayerFeatureGateCatalog.GZIP_BASE64);
        appendStaticCatalog(result, GeneratedVeFeatureGateCatalog.GZIP_BASE64);
        appendStructuredStaticCatalog(result, GeneratedSettingsManagerCatalog.GZIP_BASE64);
        return result;
    }

    private static void appendStaticCatalog(List<Entry> result, String[] chunks) throws Exception {
        try (BufferedReader reader = openCatalog(chunks)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 10) {
                    continue;
                }
                Entry entry = new Entry(
                        fields[0],
                        titleFor(fields[0]),
                        fields[1],
                        fields[2],
                        "1".equals(fields[3]),
                        "1".equals(fields[4]),
                        jsonValues(fields[5]),
                        jsonValues(fields[6]),
                        jsonValues(fields[7]),
                        fields[8],
                        fields[9],
                        false,
                        null,
                        null
                );
                if (entry.userVisible()) {
                    result.add(entry);
                }
            }
        }
    }

    private static void appendStructuredStaticCatalog(
            List<Entry> result,
            String[] chunks
    ) throws Exception {
        try (BufferedReader reader = openCatalog(chunks)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 6) {
                    continue;
                }
                if (!StructuredConfigController.hasActionableFields(fields[1])) {
                    continue;
                }
                result.add(new Entry(
                        fields[0],
                        titleFor(fields[0]),
                        FeatureGateLabStore.MANAGER_SETTINGS_MANAGER,
                        "OBJECT",
                        true,
                        "generated_registry".equals(fields[5]),
                        Collections.singletonList(fields[2]),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "",
                        fields[3] + " in " + fields[4],
                        false,
                        null,
                        null,
                        fields[1]
                ));
            }
        }
    }

    private static Map<String, Map<String, Object>> readCurrentKevaMaps() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        result.put(
                FeatureGateLabStore.MANAGER_ABMOCK,
                readKevaMapSafely("libra_config_center_repo")
        );
        result.put(
                FeatureGateLabStore.MANAGER_PLAYER_CONFIG,
                FeatureGateLabRuntime.playerObservedValues()
        );
        result.put(
                FeatureGateLabStore.MANAGER_LIVE,
                readKevaMapSafely("live_settings_repo")
        );
        result.put(
                FeatureGateLabStore.MANAGER_VE_CONFIG,
                readVeConfigMapSafely()
        );
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readVeConfigMapSafely() {
        try {
            Class<?> centerClass = Class.forName("com.ss.android.vesdk.VEConfigCenter");
            Method getInstance = centerClass.getMethod("getInstance");
            Object center = getInstance.invoke(null);
            Method getConfigs = centerClass.getMethod("getConfigs");
            Object configs = getConfigs.invoke(center);
            if (!(configs instanceof Map)) {
                return Collections.emptyMap();
            }
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> item : ((Map<?, ?>) configs).entrySet()) {
                if (item.getKey() == null || item.getValue() == null) {
                    continue;
                }
                Method getValue = item.getValue().getClass().getMethod("getValue");
                result.put(String.valueOf(item.getKey()), getValue.invoke(item.getValue()));
            }
            return result;
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readKevaMapSafely(String repoName) {
        try {
            Class<?> kevaClass = Class.forName("com.bytedance.keva.Keva");
            Method getRepo = kevaClass.getMethod("getRepo", String.class);
            Object repo = getRepo.invoke(null, repoName);
            Method getAll = kevaClass.getMethod("getAll");
            Object result = getAll.invoke(repo);
            if (!(result instanceof Map)) {
                return Collections.emptyMap();
            }
            Map<String, Object> copy = new HashMap<>();
            for (Map.Entry<?, ?> item : ((Map<?, ?>) result).entrySet()) {
                if (item.getKey() != null) {
                    copy.put(String.valueOf(item.getKey()), item.getValue());
                }
            }
            return copy;
        } catch (Throwable ignored) {
            return Collections.emptyMap();
        }
    }

    private static Snapshot merge(
            List<Entry> base,
            Map<String, Map<String, Object>> current,
            boolean catalogComplete
    ) {
        List<Entry> result = new ArrayList<>(base.size() + 1000);
        Map<String, Entry> byIdentity = new HashMap<>();
        Map<String, Map<String, Object>> remaining = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> source : current.entrySet()) {
            remaining.put(source.getKey(), new HashMap<>(source.getValue()));
        }
        int loaded = 0;
        for (Entry entry : base) {
            Map<String, Object> sourceValues = remaining.get(entry.manager);
            boolean isLoaded = sourceValues != null && sourceValues.containsKey(entry.key);
            Object value = sourceValues == null ? null : sourceValues.remove(entry.key);
            Entry merged = entry.withCurrent(isLoaded, value);
            result.add(merged);
            byIdentity.put(merged.identity(), merged);
            if (isLoaded) {
                loaded++;
            }
        }
        for (Map.Entry<String, Map<String, Object>> source : remaining.entrySet()) {
            String manager = source.getKey();
            for (Map.Entry<String, Object> item : source.getValue().entrySet()) {
                if (FeatureGateLabStore.MANAGER_PLAYER_CONFIG.equals(manager)
                        && !FeatureGateLabRuntime.wasPlayerObserved(item.getKey())) {
                    continue;
                }
                String dynamicType = runtimeType(item.getValue());
                boolean actionable = FeatureGateLabStore.supportsOverride(manager, dynamicType);
                if (!actionable) {
                    continue;
                }
                Entry dynamic = new Entry(
                        item.getKey(),
                        titleFor(item.getKey()),
                        manager,
                        dynamicType,
                        true,
                        false,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        "",
                        "unproven",
                        true,
                        valueText(item.getValue()),
                        dynamicType
                );
                result.add(dynamic);
                byIdentity.put(dynamic.identity(), dynamic);
                loaded++;
            }
        }
        loaded += mergeStructuredObservations(result, byIdentity);
        Collections.sort(result, (left, right) ->
                left.title.toLowerCase(Locale.ROOT).compareTo(right.title.toLowerCase(Locale.ROOT)));
        return new Snapshot(
                Collections.unmodifiableList(result),
                Collections.unmodifiableMap(byIdentity),
                loaded,
                System.currentTimeMillis(),
                catalogComplete
        );
    }

    private static int mergeStructuredObservations(
            List<Entry> result,
            Map<String, Entry> byIdentity
    ) {
        JSONArray observations = SettingsManagerObservationRecorder.exportJson();
        Set<String> loadedKeys = new HashSet<>();
        int loaded = 0;
        for (int index = 0; index < observations.length(); index++) {
            org.json.JSONObject observation = observations.optJSONObject(index);
            if (observation == null || !observation.optBoolean("actionable", false)) {
                continue;
            }
            String key = observation.optString("key", "");
            String requestedClass = observation.optString("requested_class", "");
            if (key.isEmpty() || requestedClass.isEmpty() || !loadedKeys.add(key)) {
                continue;
            }
            String manager = observation.optString(
                    "manager",
                    FeatureGateLabStore.MANAGER_SETTINGS_MANAGER
            );
            String identity = manager + "\n" + key;
            Object current = observation.opt("current_value");
            Object defaultValue = observation.opt("default_value");
            List<String> defaults = new ArrayList<>();
            if (defaultValue != null && defaultValue != org.json.JSONObject.NULL) {
                defaults.add(String.valueOf(defaultValue));
            }
            Entry existing = byIdentity.get(identity);
            Entry merged = new Entry(
                    key,
                    existing == null ? titleFor(key) : existing.title,
                    manager,
                    "OBJECT",
                    true,
                    existing != null && existing.registered,
                    defaults.isEmpty() && existing != null ? existing.defaults : defaults,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    "",
                    observation.optString("caller", "runtime getter")
                            + " / "
                            + observation.optString("settings_manager_method_descriptor", ""),
                    true,
                    current == null || current == org.json.JSONObject.NULL
                            ? "null"
                            : String.valueOf(current),
                    "OBJECT",
                    requestedClass
            );
            if (existing == null) {
                result.add(merged);
            } else {
                int existingIndex = result.indexOf(existing);
                if (existingIndex >= 0) {
                    result.set(existingIndex, merged);
                }
            }
            byIdentity.put(identity, merged);
            loaded++;
        }
        return loaded;
    }

    private static List<String> jsonValues(String text) throws Exception {
        List<String> result = new ArrayList<>();
        JSONArray array = new JSONArray(text);
        for (int index = 0; index < array.length(); index++) {
            Object value = array.opt(index);
            String formatted = valueText(value);
            if (!result.contains(formatted)) {
                result.add(formatted);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static String titleFor(String key) {
        if (key == null || key.isEmpty()) {
            return "Unnamed gate";
        }
        String[] words = key.replace('-', '_').replace('.', '_').split("_+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.length() == 0 ? key : result.toString();
    }

    private static String valueText(Object value) {
        if (value == null) {
            return "null";
        }
        String text = String.valueOf(value);
        return text.length() <= 2000 ? text : text.substring(0, 1997) + "...";
    }

    private static String runtimeType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) return "INT";
        if (value instanceof Long) return "LONG";
        if (value instanceof Float) return "FLOAT";
        if (value instanceof Double) return "DOUBLE";
        if (value instanceof String) return "STRING";
        return value.getClass().getName();
    }

    public static final class Snapshot {
        public final List<Entry> entries;
        public final Map<String, Entry> byIdentity;
        public final int loadedCount;
        public final long loadedAtMs;
        public final boolean catalogComplete;

        Snapshot(
                List<Entry> entries,
                Map<String, Entry> byIdentity,
                int loadedCount,
                long loadedAtMs,
                boolean catalogComplete
        ) {
            this.entries = entries;
            this.byIdentity = byIdentity;
            this.loadedCount = loadedCount;
            this.loadedAtMs = loadedAtMs;
            this.catalogComplete = catalogComplete;
        }
    }

    public static final class Entry {
        public final String key;
        public final String title;
        public final String searchText;
        public final String manager;
        public final String type;
        public final boolean actionable;
        public final boolean registered;
        public final List<String> defaults;
        public final List<String> historical;
        public final List<String> researched;
        public final String description;
        public final String proof;
        public final boolean loaded;
        public final String currentValue;
        public final String currentType;
        public final String requestedClass;

        Entry(
                String key,
                String title,
                String manager,
                String type,
                boolean actionable,
                boolean registered,
                List<String> defaults,
                List<String> historical,
                List<String> researched,
                String description,
                String proof,
                boolean loaded,
                String currentValue,
                String currentType
        ) {
            this(
                    key, title, manager, type, actionable, registered, defaults, historical, researched,
                    description, proof, loaded, currentValue, currentType, null
            );
        }

        Entry(
                String key,
                String title,
                String manager,
                String type,
                boolean actionable,
                boolean registered,
                List<String> defaults,
                List<String> historical,
                List<String> researched,
                String description,
                String proof,
                boolean loaded,
                String currentValue,
                String currentType,
                String requestedClass
        ) {
            this.key = key;
            this.title = title;
            this.searchText = (key + "\n" + title).toLowerCase(Locale.ROOT);
            this.manager = manager;
            this.type = type;
            this.actionable = actionable;
            this.registered = registered;
            this.defaults = defaults;
            this.historical = historical;
            this.researched = researched;
            this.description = description;
            this.proof = proof;
            this.loaded = loaded;
            this.currentValue = currentValue;
            this.currentType = currentType;
            this.requestedClass = requestedClass;
        }

        Entry withCurrent(boolean loaded, Object value) {
            return new Entry(
                    key, title, manager, type, actionable, registered, defaults, historical, researched,
                    description, proof, loaded, loaded ? valueText(value) : null, loaded ? runtimeType(value) : null,
                    requestedClass
            );
        }

        public String identity() {
            return manager + "\n" + key;
        }

        public boolean unknown() {
            return "UNKNOWN".equals(type) || (!registered && !actionable);
        }

        public boolean userVisible() {
            return actionable && FeatureGateLabStore.supportsOverride(manager, type);
        }

        public String sourceLabel() {
            String prefix = sourceName() + " / ";
            if (registered && loaded) return prefix + "generated registry + current cache";
            if (registered) return prefix + "generated registry";
            if (loaded) return prefix + "current cache only";
            return prefix + "local catalog";
        }

        public String sourceName() {
            if (FeatureGateLabStore.MANAGER_PIA_ACTIVITY_CENTER.equals(manager)) return "Activity Center";
            if (FeatureGateLabStore.MANAGER_PLAYER_CONFIG.equals(manager)) return "Player Config";
            if (FeatureGateLabStore.MANAGER_LIVE.equals(manager)) return "Live Settings";
            if (FeatureGateLabStore.MANAGER_VE_CONFIG.equals(manager)) return "Media Config";
            if (FeatureGateLabStore.MANAGER_SETTINGS_MANAGER.equals(manager)) return "Structured Config";
            if (FeatureGateLabStore.MANAGER_ABMOCK.equals(manager)) return "App AB";
            return manager;
        }

        public String shortSourceName() {
            if (FeatureGateLabStore.MANAGER_PIA_ACTIVITY_CENTER.equals(manager)) return "PIA";
            if (FeatureGateLabStore.MANAGER_PLAYER_CONFIG.equals(manager)) return "PLAYER";
            if (FeatureGateLabStore.MANAGER_LIVE.equals(manager)) return "LIVE";
            if (FeatureGateLabStore.MANAGER_VE_CONFIG.equals(manager)) return "VE";
            if (FeatureGateLabStore.MANAGER_SETTINGS_MANAGER.equals(manager)) return "CONFIG";
            if (FeatureGateLabStore.MANAGER_ABMOCK.equals(manager)) return "AB";
            return manager.toUpperCase(Locale.ROOT);
        }

    }

}
