package app.morphe.extension.shared.settings;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Parses settings without rounding numeric tokens before type validation. */
public final class SettingsJson {
    private SettingsJson() {}

    public static JSONObject parseObject(String text) throws IOException, JSONException {
        if (text == null || text.indexOf('\0') >= 0) throw new IOException("Invalid settings JSON");
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            Object value = read(reader, 0);
            if (!(value instanceof JSONObject) || reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("Expected one settings object");
            }
            return (JSONObject) value;
        }
    }

    private static Object read(JsonReader reader, int depth) throws IOException, JSONException {
        if (depth > 64) throw new IOException("Settings JSON is nested too deeply");
        switch (reader.peek()) {
            case BEGIN_OBJECT: {
                reader.beginObject();
                JSONObject object = new JSONObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) throw new IOException("Duplicate setting: " + name);
                    object.put(name, read(reader, depth + 1));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY: {
                reader.beginArray();
                JSONArray array = new JSONArray();
                while (reader.hasNext()) array.put(read(reader, depth + 1));
                reader.endArray();
                return array;
            }
            case STRING: return reader.nextString();
            case BOOLEAN: return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return JSONObject.NULL;
            case NUMBER: {
                String token = reader.nextString();
                try {
                    long value = Long.parseLong(token);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return Integer.valueOf((int) value);
                    return Long.valueOf(value);
                } catch (NumberFormatException decimal) {
                    return new BigDecimal(token);
                }
            }
            default: throw new IOException("Invalid settings value");
        }
    }
}
