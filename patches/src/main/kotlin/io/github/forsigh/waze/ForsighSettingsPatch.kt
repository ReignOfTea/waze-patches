package io.github.forsigh.waze

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import io.github.forsigh.waze.WazeConstants.COMPATIBILITY_WAZE

private const val CATALOG = "Lio/github/forsigh/waze/extension/settings/ForsighCatalog;"
private const val SETTINGS = "Lio/github/forsigh/waze/extension/settings/ForsighSettings;"
private const val PLACEHOLDER = "__FORSIGH_CATALOG__"

/**
 * Merges the settings extension, hands it a Context, and writes the menu catalogue.
 *
 * Hidden on purpose: it is pulled in by the patches that have a runtime option. Turning it off on
 * its own would leave those patches calling a class that is not in the APK.
 *
 * The screen is a launcher activity ("Waze patch settings"), not a row inside Waze's settings.
 * The earlier row was appended to a list the settings screen never walks, so it never rendered.
 */
@Suppress("unused")
val forsighSettingsPatch = bytecodePatch(
    name = null,
    description = "In-app settings for the patches that can be changed without patching again.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WAZE)

    extendWith("extensions/waze/forsigh-settings.mpe")

    execute {
        ApplicationOnCreateFingerprint.method.addInstructions(
            0,
            "invoke-static {p0}, $SETTINGS->init(Landroid/content/Context;)V",
        )
    }

    finalize {
        if (InAppMenu.isEmpty()) {
            println("forsighSettingsPatch: no runtime options registered")
            return@finalize
        }
        val method = mutableClassDefBy(CATALOG).methods.first { it.name == "encoded" }
        val instructions = method.implementation?.instructions?.toList().orEmpty()
        val index = instructions.indexOfFirst { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference
            reference is StringReference && reference.string == PLACEHOLDER
        }
        if (index < 0) {
            println("forsighSettingsPatch: catalogue placeholder was not found")
            return@finalize
        }
        val register = (instructions[index] as OneRegisterInstruction).registerA
        val literal = InAppMenu.encode()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\u001e", "\\u001e")
            .replace("\u001f", "\\u001f")
        method.replaceInstruction(index, "const-string v$register, \"$literal\"")
        println("forsighSettingsPatch: wrote the in-app settings catalogue")
    }
}
