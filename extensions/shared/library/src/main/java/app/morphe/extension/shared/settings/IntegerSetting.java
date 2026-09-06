package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class IntegerSetting extends Setting<Integer> {

    public IntegerSetting(String key, Integer defaultValue) {
        super(key, defaultValue);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public IntegerSetting(String key, Integer defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public IntegerSetting(String key, Integer defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public IntegerSetting(@NonNull String key, @NonNull Integer defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    private int minimum = Integer.MIN_VALUE;
    private int maximum = Integer.MAX_VALUE;

    /**
     * Declares the range this setting is allowed to hold, and returns the setting so it can
     * be written on the end of the declaration. The number preference reads the range from
     * here rather than being told it a second time at the call site.
     */
    public IntegerSetting withRange(int min, int max) {
        if (min > max) throw new IllegalArgumentException(key + ": " + min + " is above " + max);
        minimum = min;
        maximum = max;
        // A default outside its own range would mean the setting could never rest at it.
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(key + ": default " + defaultValue
                    + " is outside " + min + " to " + max);
        }
        return this;
    }

    public boolean hasRange() {
        return minimum != Integer.MIN_VALUE || maximum != Integer.MAX_VALUE;
    }

    public int minimum() {
        return minimum;
    }

    public int maximum() {
        return maximum;
    }

    @NonNull
    @Override
    protected Integer coerce(@NonNull Integer newValue) {
        return Math.max(minimum, Math.min(maximum, newValue));
    }

    @Override
    protected void load() {
        // Coerced on the way in as well: a value saved before the range existed is already
        // sitting on disk, and nothing else would ever bring it back inside.
        value = coerce(preferences.getIntegerString(key, defaultValue));
    }

    @Override
    protected Integer readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return json.getInt(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = coerce(Integer.valueOf(Objects.requireNonNull(newValue)));
    }

    @Override
    public void saveToPreferences() {
        preferences.saveIntegerString(key, value);
    }

    @NonNull
    @Override
    public Integer get() {
        return value;
    }
}
