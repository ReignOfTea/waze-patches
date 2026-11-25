package io.github.forsigh.waze

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import io.github.forsigh.waze.WazeConstants.COMPATIBILITY_WAZE

/**
 * Resume drive prompt.
 *
 * Waze offers to pick the last trip back up when it comes back to life - on the phone start screen
 * and on the Android Auto card ("Resume drive" / "No"). The offer is driven from native: Waze's
 * native layer calls `ResumeDriveNativeManagerBase.handleResumeDriveCallbackResultJNI([B)`, the
 * parsed place lands in `mutableResumeDriveFlow`, and the start-screen view model
 * (`com.waze.main_screen.u.n`) and the car presenter (`com.waze.car_lib.m.c.k`) turn it into the
 * card.
 *
 * `hide` returns from that callback before it parses anything, so the flow never emits and no
 * surface has a card to draw. Manual navigation is untouched: the user picks the destination as
 * usual.
 *
 * Known limitation: the same flow feeds Waze's own "this place is in recents, resume it quietly"
 * attempt, so hiding the prompt also drops that silent resume. Nothing else changes.
 */
@Suppress("unused")
val resumeDrivePromptPatch = bytecodePatch(
    name = "Resume drive prompt",
    description = "Stops Waze asking to resume the last trip.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_WAZE)
    category("Driving & Voice")
    dependsOn(forsighSettingsPatch)

    val mode by stringOption(
        "mode", "hide",
        mapOf(
            "Never ask (hide the prompt)" to "hide",
            "Leave it alone (stock)" to "stock",
        ),
        title = "When Waze offers to resume the last trip",
        description = "Hide: the resume card never appears. Stock: Waze behaves as shipped.",
    )

    execute {
        val chosen = mode ?: "hide"
        requireChoice("resumeDrivePromptPatch", "mode", chosen, setOf("hide", "stock"))
        val hide = chosen == "hide"
        InAppMenu.gate(
            "Driving & Voice", "Hide resume drive prompt",
            "On hides the resume card. Off lets Waze offer the last trip again.",
            hide, "gate.resume",
        )

        val fingerprint = Fingerprint(
            definingClass = "Lcom/waze/resume_drive/ResumeDriveNativeManagerBase;",
            name = "handleResumeDriveCallbackResultJNI",
            returnType = "V",
        )
        val method = fingerprint.method
        val fallback = if (hide) "1" else "0"
        method.addInstructions(
            0,
            """
            const-string v0, "gate.resume|$fallback"
            invoke-static {v0}, Lio/github/forsigh/waze/extension/settings/ForsighSettings;->enabled(Ljava/lang/String;)Z
            move-result v0
            if-eqz v0, :resume_stock
            return-void
            :resume_stock
            """.trimIndent(),
        )
        println("resumeDrivePromptPatch: resume callback gated, default ${if (hide) "hidden" else "stock"}")
    }
}
