package io.github.forsigh.waze.extension.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The patch settings screen. Opened from its own launcher icon ("Waze patch settings"), because
 * Waze's settings list is obfuscated and a row appended there has already shipped once without
 * ever rendering.
 *
 * Text is literal English on purpose: Waze's own translation path is not used.
 */
public final class ForsighSettingsActivity extends Activity {

    private static final char ROW = '\u001e';
    private static final char FIELD = '\u001f';
    private static final String RESTART_MESSAGE =
            "Restart Waze to apply this. Some of these are read again on the next screen; " +
                    "the ones Waze caches are applied by the restart.";

    private final List<Row> rows = new ArrayList<>();
    private boolean changed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ForsighSettings.init(this);

        final int pad = dp(16);
        final LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, pad, pad, pad);

        final TextView intro = text(16, true);
        intro.setText("Settings for the patches included in this build. " +
                "Icon, name, splash, themes and the Play Store lock stay as they were patched.");
        column.addView(intro);

        final String encoded = ForsighCatalog.encoded();
        if (encoded == null || encoded.equals(ForsighCatalog.PLACEHOLDER) || encoded.isEmpty()) {
            final TextView empty = text(15, false);
            empty.setText("No runtime settings were included. Enable a patch that has options, then patch again.");
            column.addView(empty);
        } else {
            parse(encoded);
            String lastGroup = "";
            for (Row row : rows) {
                if (!row.group.equals(lastGroup)) {
                    lastGroup = row.group;
                    final TextView header = text(18, true);
                    header.setPadding(0, dp(18), 0, dp(4));
                    header.setText(row.group);
                    column.addView(header);
                }
                column.addView(render(row));
            }
            final Button reset = new Button(this);
            reset.setText("Reset to patched defaults");
            reset.setOnClickListener(v -> {
                ForsighSettings.clear();
                changed = true;
                ForsighUi.showRestartDialog(this, "Patched defaults restored. " + RESTART_MESSAGE);
                recreate();
            });
            column.addView(reset);
        }

        final ScrollView scroll = new ScrollView(this);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    public void onBackPressed() {
        if (!changed) {
            super.onBackPressed();
            return;
        }
        ForsighUi.showRestartDialog(this, RESTART_MESSAGE);
        changed = false;
    }

    private ViewGroup render(Row row) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));

        if ("switch".equals(row.kind) || "gate".equals(row.kind)) {
            final Switch toggle = new Switch(this);
            toggle.setText(row.title);
            toggle.setChecked(currentOn(row));
            toggle.setOnCheckedChangeListener((button, checked) -> {
                applySwitch(row, checked);
                changed = true;
            });
            box.addView(toggle);
        } else if ("choice".equals(row.kind)) {
            final Button button = new Button(this);
            button.setText(row.title + ": " + choiceLabel(row));
            button.setOnClickListener(v -> showChoice(row, button));
            box.addView(button);
        } else if ("number".equals(row.kind)) {
            final Button button = new Button(this);
            button.setText(row.title + ": " + currentNumber(row));
            button.setOnClickListener(v -> showNumber(row, button));
            box.addView(button);
        }

        if (!row.summary.isEmpty()) {
            final TextView summary = text(13, false);
            summary.setText(row.summary);
            box.addView(summary);
        }
        return box;
    }

    private void showChoice(Row row, Button button) {
        final String[] labels = new String[row.options.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = row.options.get(i).label;
        }
        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setItems(labels, (dialog, which) -> {
                    final String token = row.options.get(which).token;
                    ForsighSettings.put(menuKey(row), token);
                    applyChoice(row, token);
                    button.setText(row.title + ": " + row.options.get(which).label);
                    changed = true;
                })
                .show();
    }

    private void showNumber(Row row, Button button) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentNumber(row)));
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setMessage(row.min + " to " + row.max)
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    long value;
                    try {
                        value = Long.parseLong(input.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                        return;
                    }
                    if (value < row.min) {
                        value = row.min;
                    }
                    if (value > row.max) {
                        value = row.max;
                    }
                    ForsighSettings.put(menuKey(row), String.valueOf(value));
                    applyNumber(row, value);
                    button.setText(row.title + ": " + value);
                    changed = true;
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean currentOn(Row row) {
        final String stored = ForsighSettings.get(menuKey(row));
        if (stored == null) {
            return row.defaultOn;
        }
        return "1".equals(stored) || "true".equals(stored);
    }

    private String choiceLabel(Row row) {
        final String token = currentToken(row);
        for (Option option : row.options) {
            if (option.token.equals(token)) {
                return option.label;
            }
        }
        return token;
    }

    private String currentToken(Row row) {
        final String stored = ForsighSettings.get(menuKey(row));
        return stored == null ? row.defaultToken : stored;
    }

    private long currentNumber(Row row) {
        final String stored = ForsighSettings.get(menuKey(row));
        if (stored == null) {
            return row.defaultNumber;
        }
        try {
            return Long.parseLong(stored);
        } catch (NumberFormatException ignored) {
            return row.defaultNumber;
        }
    }

    private void applySwitch(Row row, boolean on) {
        final String off = row.off == null ? ForsighSettings.STOCK : row.off;
        for (Map.Entry<String, String> write : row.writes.entrySet()) {
            ForsighSettings.put(write.getKey(), on ? write.getValue() : off);
        }
        if (row.gate != null) {
            ForsighSettings.put(row.gate, on ? "true" : "false");
        }
    }

    private void applyChoice(Row row, String token) {
        final Map<String, String> selected = row.effects.get(token);
        for (String key : row.effectKeys) {
            final String value = selected == null ? null : selected.get(key);
            ForsighSettings.put(key, expand(value == null ? ForsighSettings.STOCK : value));
        }
    }

    private void applyNumber(Row row, long value) {
        final String stored = String.valueOf(value * row.scale);
        for (String key : row.numberKeys) {
            if (value == 0 && row.zeroClears) {
                ForsighSettings.remove(key);
            } else {
                ForsighSettings.put(key, stored);
            }
        }
        for (Row other : rows) {
            if ("choice".equals(other.kind) && other.mentions(row.id)) {
                applyChoice(other, currentToken(other));
            }
        }
    }

    private String expand(String value) {
        if (value == null || !value.startsWith("$")) {
            return value;
        }
        final int star = value.indexOf('*');
        final String id = star < 0 ? value.substring(1) : value.substring(1, star);
        final long scale = star < 0 ? 1L : Long.parseLong(value.substring(star + 1));
        long number = 0L;
        for (Row row : rows) {
            if (row.id.equals(id)) {
                number = currentNumber(row);
                break;
            }
        }
        return String.valueOf(number * scale);
    }

    private static String menuKey(Row row) {
        return "menu." + row.id;
    }

    private void parse(String encoded) {
        for (String raw : encoded.split(String.valueOf(ROW), -1)) {
            if (raw.isEmpty()) {
                continue;
            }
            final String[] f = raw.split(String.valueOf(FIELD), -1);
            if (f.length < 5) {
                continue;
            }
            final Row row = new Row();
            row.kind = f[0];
            row.group = f[1];
            row.title = f[2];
            row.summary = f[3];
            if ("switch".equals(row.kind) || "gate".equals(row.kind)) {
                row.defaultOn = "1".equals(f[4]);
                row.id = f[5];
                row.off = f.length > 6 ? f[6] : ForsighSettings.STOCK;
                row.writes = parseWrites(f.length > 7 ? f[7] : "");
                row.gate = f.length > 8 && !f[8].isEmpty() ? f[8] : null;
            } else if ("choice".equals(row.kind)) {
                row.defaultToken = f[4];
                row.id = f[5];
                row.options = parseOptions(f.length > 6 ? f[6] : "");
                row.effects = parseEffects(f.length > 7 ? f[7] : "");
                for (Map<String, String> effect : row.effects.values()) {
                    for (String key : effect.keySet()) {
                        if (!row.effectKeys.contains(key)) {
                            row.effectKeys.add(key);
                        }
                    }
                }
            } else if ("number".equals(row.kind)) {
                row.defaultNumber = Long.parseLong(f[4]);
                row.min = Long.parseLong(f[5]);
                row.max = Long.parseLong(f[6]);
                row.id = f[7];
                row.numberKeys = splitList(f.length > 8 ? f[8] : "");
                row.scale = f.length > 9 && !f[9].isEmpty() ? Long.parseLong(f[9]) : 1L;
                row.zeroClears = f.length > 10 && "1".equals(f[10]);
            }
            rows.add(row);
        }
    }

    private static Map<String, String> parseWrites(String raw) {
        final Map<String, String> writes = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return writes;
        }
        for (String pair : raw.split(",", -1)) {
            final int eq = pair.indexOf('=');
            if (eq > 0) {
                writes.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return writes;
    }

    private static List<Option> parseOptions(String raw) {
        final List<Option> options = new ArrayList<>();
        if (raw.isEmpty()) {
            return options;
        }
        for (String pair : raw.split("\\|", -1)) {
            final int colon = pair.indexOf(':');
            if (colon > 0) {
                options.add(new Option(pair.substring(0, colon), pair.substring(colon + 1)));
            }
        }
        return options;
    }

    private static Map<String, Map<String, String>> parseEffects(String raw) {
        final Map<String, Map<String, String>> effects = new LinkedHashMap<>();
        if (raw.isEmpty()) {
            return effects;
        }
        for (String tokenBlock : raw.split(";", -1)) {
            final int gt = tokenBlock.indexOf('>');
            if (gt < 0) {
                continue;
            }
            effects.put(tokenBlock.substring(0, gt), parseWrites(tokenBlock.substring(gt + 1)));
        }
        return effects;
    }

    private static List<String> splitList(String raw) {
        final List<String> keys = new ArrayList<>();
        if (raw.isEmpty()) {
            return keys;
        }
        for (String key : raw.split(",", -1)) {
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private TextView text(int sp, boolean bold) {
        final TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) {
            view.setGravity(Gravity.START);
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private static final class Option {
        final String token;
        final String label;

        Option(String token, String label) {
            this.token = token;
            this.label = label;
        }
    }

    private static final class Row {
        String kind = "";
        String group = "";
        String title = "";
        String summary = "";
        String id = "";
        boolean defaultOn;
        String off = ForsighSettings.STOCK;
        String gate;
        Map<String, String> writes = new LinkedHashMap<>();
        String defaultToken = "";
        List<Option> options = new ArrayList<>();
        Map<String, Map<String, String>> effects = new LinkedHashMap<>();
        final List<String> effectKeys = new ArrayList<>();
        long defaultNumber;
        long min;
        long max;
        long scale = 1L;
        boolean zeroClears;
        List<String> numberKeys = new ArrayList<>();

        boolean mentions(String numberId) {
            final String needle = "$" + numberId;
            for (Map<String, String> effect : effects.values()) {
                for (String value : effect.values()) {
                    if (value != null && value.startsWith(needle)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
