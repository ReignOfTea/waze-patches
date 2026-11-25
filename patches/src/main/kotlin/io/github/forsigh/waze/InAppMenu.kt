package io.github.forsigh.waze

/**
 * Rows the in-app settings screen shows, and the config hooks behind them.
 *
 * Each row is recorded only when its patch runs, so the screen lists options that were actually
 * patched in. A stored "stock" makes the hook fall through to Waze; until the user changes
 * anything, the baked value (the Morphe option) is what the hook returns.
 */
internal object InAppMenu {

    private val rows = mutableListOf<String>()
    private val seen = mutableSetOf<String>()

    fun isEmpty(): Boolean = rows.isEmpty()

    fun encode(): String = rows.joinToString("\u001e")

    /** On writes [writes]; off writes [off] ("stock" leaves Waze's own value). */
    fun switch(
        group: String,
        title: String,
        summary: String,
        on: Boolean,
        writes: Map<String, String>,
        off: String = "stock",
        gate: String? = null,
    ) {
        writes.forEach { (key, value) ->
            val baked = when {
                on -> value
                off == "stock" -> null
                else -> off
            }
            hook(key, value, baked)
        }
        val id = remember(group, title) ?: return
        add("switch", group, title, summary, if (on) "1" else "0", id, off, encodeWrites(writes), gate ?: "")
    }

    /**
     * [options] is token to label. [effects] is token to the config writes for that token.
     * [baked] is the value patched in now; null hooks the key with no baked value.
     */
    fun choice(
        group: String,
        title: String,
        summary: String,
        defaultToken: String,
        options: List<Pair<String, String>>,
        effects: Map<String, Map<String, String>>,
        baked: Map<String, String?>,
    ) {
        baked.forEach { (key, value) ->
            val candidates = ArrayList<String>()
            if (value != null) candidates += value
            effects.values.forEach { effect -> effect[key]?.let { candidates += it } }
            val sample = candidates.firstOrNull { it != "stock" && !it.startsWith("$") } ?: "0"
            hook(key, sample, value)
        }
        val id = remember(group, title) ?: return
        val optionField = options.joinToString("|") { (token, label) -> "$token:$label" }
        val effectField = effects.entries.joinToString(";") { (token, writes) ->
            "$token>${encodeWrites(writes)}"
        }
        add("choice", group, title, summary, defaultToken, id, optionField, effectField)
    }

    /** [scale] is multiplied into the stored config value. 60000 stores minutes as milliseconds. */
    fun number(
        group: String,
        title: String,
        summary: String,
        default: Long,
        min: Long,
        max: Long,
        keys: List<String> = emptyList(),
        scale: Long = 1,
        zeroClears: Boolean = false,
        baked: Long? = null,
    ) {
        val id = remember(group, title) ?: return
        if (keys.isNotEmpty()) {
            val stored = baked?.toString()
            keys.forEach { key -> hook(key, (baked ?: default * scale).toString(), stored) }
        }
        add(
            "number", group, title, summary,
            default.toString(), min.toString(), max.toString(), id,
            keys.joinToString(","), scale.toString(), if (zeroClears) "1" else "0",
        )
    }

    /** A bytecode gate. The injected code calls [ForsighSettings.enabled] with `key|1` or `key|0`. */
    fun gate(
        group: String,
        title: String,
        summary: String,
        on: Boolean,
        key: String,
    ) {
        val id = remember(group, title) ?: return
        add("gate", group, title, summary, if (on) "1" else "0", id, "false", "", key)
    }

    private fun remember(group: String, title: String): String? {
        val id = slug("$group $title")
        return if (seen.add(id)) id else null
    }

    private fun add(vararg fields: String) {
        rows += fields.joinToString("\u001f") { clean(it) }
    }
}

private fun encodeWrites(writes: Map<String, String>): String =
    writes.entries.joinToString(",") { (key, value) -> "$key=$value" }

private fun slug(raw: String): String =
    raw.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.')

private fun clean(raw: String): String =
    raw.replace("\u001e", " ").replace("\u001f", " ")

/**
 * [sample] decides the config type (boolean, long or string). [baked] is what the hook returns
 * until the menu stores something; null means fall through to Waze.
 * Keys that are not Waze config fields (speedometer scale, refresh rate) are read by the extension
 * directly and are not hooked here.
 */
private fun hook(key: String, sample: String, baked: String?) {
    if (!key.startsWith("CONFIG_VALUE_")) return
    when {
        sample == "true" || sample == "false" -> {
            val value = when (baked) {
                "true" -> true
                "false" -> false
                else -> null
            }
            Overrides.booleans[key] = value
            Overrides.nativeBooleans[key] = value
        }
        sample.matches(Regex("-?\\d+")) -> {
            val value = baked?.toLong()
            Overrides.longs[key] = value
            Overrides.nativeLongs[key] = value
        }
        else -> {
            Overrides.strings[key] = baked
            Overrides.nativeStrings[key] = baked
        }
    }
}
