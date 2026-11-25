package io.github.forsigh.waze.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Runtime values for the Forsigh patches.
 *
 * A patch-time force returns a constant, and a constant cannot be changed from a settings screen.
 * The injected smali calls the resolve methods here. A stored "stock" falls through to Waze; a
 * missing preference keeps the value the patch baked in.
 */
public final class ForsighSettings {

    public static final String PREFS_NAME = "ForsighSettings";
    static final String STOCK = "stock";
    private static final char SEP = '\u001f';

    private static volatile SharedPreferences preferences;

    private ForsighSettings() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        final Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void put(String key, String value) {
        final SharedPreferences current = preferences;
        if (current == null || key == null) {
            return;
        }
        current.edit().putString(key, value).apply();
    }

    public static void remove(String key) {
        final SharedPreferences current = preferences;
        if (current == null || key == null) {
            return;
        }
        current.edit().remove(key).apply();
    }

    public static void clear() {
        final SharedPreferences current = preferences;
        if (current != null) {
            current.edit().clear().apply();
        }
    }

    public static String get(String key) {
        return raw(key);
    }

    /**
     * @param spec {@code key|1} or {@code key|0}. The digit is the value used when nothing is stored:
     *             1 applies the patch, 0 leaves Waze's own code.
     */
    public static boolean enabled(String spec) {
        final int bar = spec.lastIndexOf('|');
        final String key = bar < 0 ? spec : spec.substring(0, bar);
        final boolean fallback = bar >= 0 && "1".equals(spec.substring(bar + 1));
        final String value = raw(key);
        if (value == null) {
            return fallback;
        }
        return "true".equals(value) || "1".equals(value);
    }

    /** Refresh-rate ceiling in Hz. A stored 60, or "stock", restores Waze's own ceiling. */
    public static double refreshHz(int fallback) {
        final String value = raw("ui.refresh");
        if (value == null) {
            return fallback;
        }
        if (STOCK.equals(value) || "60".equals(value)) {
            return 60d;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** {@code key|fallback} as a float, used by the speedometer scale. "stock" is 1. */
    public static float scaleOf(String spec) {
        final int bar = spec.indexOf('|');
        final String key = spec.substring(0, bar);
        final float fallback = Float.parseFloat(spec.substring(bar + 1));
        final String value = raw(key);
        if (value == null) {
            return fallback;
        }
        if (STOCK.equals(value) || "1".equals(value) || "1.0".equals(value)) {
            return 1f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /** Null means the getter should run Waze's original body. */
    public static Boolean resolveBoolean(String spec) {
        final String[] parts = split(spec);
        final String value = raw(parts[0]);
        if (value == null) {
            if (!"1".equals(parts[1])) {
                return null;
            }
            return "true".equals(parts[2]) ? Boolean.TRUE : Boolean.FALSE;
        }
        if (STOCK.equals(value)) {
            return null;
        }
        return "true".equals(value) ? Boolean.TRUE : Boolean.FALSE;
    }

    /** Null means the getter should run Waze's original body. */
    public static Long resolveLong(String spec) {
        final String[] parts = split(spec);
        final String value = raw(parts[0]);
        if (value == null) {
            if (!"1".equals(parts[1]) || parts[2].isEmpty()) {
                return null;
            }
            return Long.valueOf(parts[2]);
        }
        if (STOCK.equals(value)) {
            return null;
        }
        return Long.valueOf(value);
    }

    /** Null means the getter should run Waze's original body. */
    public static String resolveString(String spec) {
        final String[] parts = split(spec);
        final String value = raw(parts[0]);
        if (value == null) {
            if (!"1".equals(parts[1])) {
                return null;
            }
            return parts[2];
        }
        if (STOCK.equals(value)) {
            return null;
        }
        return value;
    }

    private static String raw(String key) {
        final SharedPreferences current = preferences;
        if (current == null || key == null || !current.contains(key)) {
            return null;
        }
        return current.getString(key, null);
    }

    private static String[] split(String spec) {
        final int first = spec.indexOf(SEP);
        final int second = spec.indexOf(SEP, first + 1);
        if (first < 0 || second < 0) {
            return new String[] {spec, "0", ""};
        }
        return new String[] {
                spec.substring(0, first),
                spec.substring(first + 1, second),
                spec.substring(second + 1),
        };
    }
}
