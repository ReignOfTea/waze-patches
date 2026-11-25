package io.github.forsigh.waze

/**
 * Builders for the smali blocks injected into Waze's typed config getters.
 *
 * Each block is prepended to a getter; `p0` is the receiver ConfigValue. If it equals one of the
 * listed `CONFIG_VALUE_*` fields the block returns our forced value, otherwise it falls through
 * to the original body.
 *
 * One block per getter, built from the whole [Overrides] registry - never several. Two blocks with
 * labels in the same method leave the earlier block's branches pointing at the wrong instruction
 * (the later insert shifts it without relocating), and the verifier rejects the class at runtime.
 * `prefix` still namespaces the labels so blocks for different getters cannot collide.
 */

private const val SETTINGS = "Lio/github/forsigh/waze/extension/settings/ForsighSettings;"

/**
 * `key\u001fhasBaked\u001fbaked`, as a smali string literal. The runtime resolver returns the stored
 * preference, the baked value when nothing is stored, or null when the user picked stock — null
 * falls through to Waze's original getter.
 */
internal fun settingsSpec(key: String, baked: String?): String {
    val flag = if (baked == null) "0" else "1"
    val value = (baked ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
    return "$key\\u001f$flag\\u001f$value"
}

/**
 * One stanza per key. A null resolve result branches forward to the next key, which then fails its
 * own comparison and lands in the original getter. Targets stay a handful of instructions ahead:
 * a long jump to a shared table is what patcher 2.3.0 resolved into the middle of an instruction.
 */
internal fun forceBoolean(prefix: String, entries: Map<String, Boolean?>): String {
    val keys = entries.keys.toList()
    return buildString {
        keys.forEachIndexed { i, key ->
            val spec = settingsSpec(key, entries[key]?.toString())
            append("sget-object v0, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/b;\n")
            append("if-ne p0, v0, :${prefix}_b$i\n")
            append("const-string v0, \"$spec\"\n")
            append("invoke-static {v0}, $SETTINGS->resolveBoolean(Ljava/lang/String;)Ljava/lang/Boolean;\n")
            append("move-result-object v0\n")
            append("if-eqz v0, :${prefix}_b$i\n")
            append("return-object v0\n")
            append(":${prefix}_b$i\n")
        }
    }
}

/** smali block force-returning a Long per key. Injected into the Long getter. */
internal fun forceLong(prefix: String, entries: Map<String, Long?>): String {
    val keys = entries.keys.toList()
    return buildString {
        keys.forEachIndexed { i, key ->
            val spec = settingsSpec(key, entries[key]?.toString())
            append("sget-object v0, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/c;\n")
            append("if-ne p0, v0, :${prefix}_l$i\n")
            append("const-string v0, \"$spec\"\n")
            append("invoke-static {v0}, $SETTINGS->resolveLong(Ljava/lang/String;)Ljava/lang/Long;\n")
            append("move-result-object v0\n")
            append("if-eqz v0, :${prefix}_l$i\n")
            append("return-object v0\n")
            append(":${prefix}_l$i\n")
        }
    }
}

/** smali block force-returning a String per key. Injected into the String getter. */
internal fun forceString(prefix: String, entries: Map<String, String?>): String {
    val keys = entries.keys.toList()
    return buildString {
        keys.forEachIndexed { i, key ->
            val spec = settingsSpec(key, entries[key])
            append("sget-object v0, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/d;\n")
            append("if-ne p0, v0, :${prefix}_s$i\n")
            append("const-string v0, \"$spec\"\n")
            append("invoke-static {v0}, $SETTINGS->resolveString(Ljava/lang/String;)Ljava/lang/String;\n")
            append("move-result-object v0\n")
            append("if-eqz v0, :${prefix}_s$i\n")
            append("return-object v0\n")
            append(":${prefix}_s$i\n")
        }
    }
}

/*
 * Recorders. Patches call these to state what they want forced; nothing is injected here. The
 * blocks above are built once, by configOverridesPatch, from the collected registry.
 *
 * `patchId` is only for readability at the call site / diagnostics - the registry is keyed by
 * CONFIG_VALUE name, so a key forced by two patches would be a bug worth seeing rather than merging.
 */

/** Record that [keys] must read as [value]. */
internal fun recordBoolean(patchId: String, value: Boolean, vararg keys: String) {
    // Mirrored into the native store as well: the typed Java getter only covers Java consumers, and
    // a key whose reader lives in the native layer applied cleanly and changed nothing (issue #24).
    keys.forEach {
        Overrides.booleans[it] = value
        Overrides.nativeBooleans[it] = value
    }
}

/** Record that each key must read as its paired Long value. */
internal fun recordLong(patchId: String, vararg entries: Pair<String, Long>) {
    entries.forEach { (key, value) ->
        Overrides.longs[key] = value
        Overrides.nativeLongs[key] = value
    }
}

/** Record that each key must read as its paired String value. */
internal fun recordString(patchId: String, vararg entries: Pair<String, String>) {
    entries.forEach { (key, value) ->
        Overrides.strings[key] = value
        Overrides.nativeStrings[key] = value
    }
}

/**
 * Body of the static helper that pushes [entries] into Waze's native config store, by calling the
 * same setter Waze's own settings screen uses. Written once per sync hook, never per key.
 *
 * `ConfigManager.isReady()` is checked first: the sync callbacks also fire before the native layer
 * is up, and the setter throws there.
 */
internal fun nativeSyncBlock(entries: Map<String, Boolean?>): String = buildString {
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->isReady()Z")
    appendLine("move-result v0")
    appendLine("if-eqz v0, :native_done")
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->getInstance()Lcom/waze/ConfigManager;")
    appendLine("move-result-object v0")
    // Skip the writes when the manager or a key is absent. (An earlier block branched on if-nez,
    // which skipped the writes precisely when the references were present.)
    appendLine("if-eqz v0, :native_done")
    entries.entries.forEachIndexed { i, (key, value) ->
        val spec = settingsSpec(key, value?.toString())
        appendLine("sget-object v1, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/b;")
        appendLine("if-eqz v1, :native_b$i")
        appendLine("const-string v2, \"$spec\"")
        appendLine("invoke-static {v2}, $SETTINGS->resolveBoolean(Ljava/lang/String;)Ljava/lang/Boolean;")
        appendLine("move-result-object v2")
        appendLine("if-eqz v2, :native_b$i")
        appendLine("invoke-virtual {v2}, Ljava/lang/Boolean;->booleanValue()Z")
        appendLine("move-result v2")
        appendLine("invoke-virtual {v0, v1, v2}, Lcom/waze/ConfigManager;->setConfigValueBool(Lcom/waze/config/b;Z)V")
        appendLine(":native_b$i")
    }
    appendLine(":native_done")
    appendLine("return-void")
}

/** Registers a Boolean key that has to reach the native config store, not just the Java getter. */
internal fun recordNativeBoolean(prefix: String, value: Boolean, vararg keys: String) {
    keys.forEach { Overrides.nativeBooleans[it] = value }
    println("${prefix}Native: native config writes = ${keys.joinToString()}")
}

/**
 * smali block writing Long keys into the native config store.
 *
 * Values are absolute. Scaling Waze's own value instead would need a remembered baseline: the
 * sync callbacks fire more than once per session, so a multiplier applied to the current value
 * compounds (250% of 250% on the second sync). That needs a static baseline per key and a device
 * test, so the patch takes absolute numbers and leaves the time caps as explicit options.
 *
 * Registers: v0 = ConfigManager, v1 = key, v2/v3 = value.
 */
internal fun nativeLongSyncBlock(entries: Map<String, Long?>): String = buildString {
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->isReady()Z")
    appendLine("move-result v0")
    appendLine("if-eqz v0, :nlong_done")
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->getInstance()Lcom/waze/ConfigManager;")
    appendLine("move-result-object v0")
    appendLine("if-eqz v0, :nlong_done")
    entries.entries.forEachIndexed { i, (key, value) ->
        val spec = settingsSpec(key, value?.toString())
        appendLine("sget-object v1, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/c;")
        appendLine("if-eqz v1, :nlong_b$i")
        appendLine("const-string v2, \"$spec\"")
        appendLine("invoke-static {v2}, $SETTINGS->resolveLong(Ljava/lang/String;)Ljava/lang/Long;")
        appendLine("move-result-object v2")
        appendLine("if-eqz v2, :nlong_b$i")
        appendLine("invoke-virtual {v2}, Ljava/lang/Long;->longValue()J")
        appendLine("move-result-wide v2")
        appendLine("invoke-virtual {v0, v1, v2, v3}, Lcom/waze/ConfigManager;->setConfigValueLong(Lcom/waze/config/c;J)V")
        appendLine(":nlong_b$i")
    }
    appendLine(":nlong_done")
    appendLine("return-void")
}

/** Registers Long keys that have to reach the native config store with an absolute value. */
internal fun recordNativeLong(prefix: String, vararg entries: Pair<String, Long>) {
    entries.forEach { (key, value) -> Overrides.nativeLongs[key] = value }
    println("${prefix}NativeLong: absolute native writes = ${entries.joinToString { it.first }}")
}

/** smali block writing String keys into the native config store. v0 = ConfigManager, v1 = key, v2 = value. */
internal fun nativeStringSyncBlock(entries: Map<String, String?>): String = buildString {
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->isReady()Z")
    appendLine("move-result v0")
    appendLine("if-eqz v0, :nstr_done")
    appendLine("invoke-static {}, Lcom/waze/ConfigManager;->getInstance()Lcom/waze/ConfigManager;")
    appendLine("move-result-object v0")
    appendLine("if-eqz v0, :nstr_done")
    entries.entries.forEachIndexed { i, (key, value) ->
        val spec = settingsSpec(key, value)
        appendLine("sget-object v1, Lcom/waze/config/ConfigValues;->$key:Lcom/waze/config/d;")
        appendLine("if-eqz v1, :nstr_b$i")
        appendLine("const-string v2, \"$spec\"")
        appendLine("invoke-static {v2}, $SETTINGS->resolveString(Ljava/lang/String;)Ljava/lang/String;")
        appendLine("move-result-object v2")
        appendLine("if-eqz v2, :nstr_b$i")
        appendLine("invoke-virtual {v0, v1, v2}, Lcom/waze/ConfigManager;->setConfigValueString(Lcom/waze/config/d;Ljava/lang/String;)V")
        appendLine(":nstr_b$i")
    }
    appendLine(":nstr_done")
    appendLine("return-void")
}
