package io.github.forsigh.waze

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import io.github.forsigh.waze.WazeConstants.COMPATIBILITY_WAZE

@Suppress("unused")
val laneGuidancePatch = bytecodePatch(
    name = "Lane guidance",
    description = "Shows lane guidance, including straight-ahead hints.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WAZE)
    category("Map & Alerts")

    // The emitter patch is what actually writes the keys recorded below. Depending on it means this
    // patch can be ticked alone and still produce a build where the override exists — without the
    // dependency the key is collected and never written, which looks exactly like a working patch.
    dependsOn(configOverridesPatch)

    execute {
        InAppMenu.switch(
            "Map & Alerts", "Lane guidance",
            "Shows lane guidance, including straight-ahead hints.",
            true,
            mapOf(
                "CONFIG_VALUE_LANE_GUIDANCE_ENABLED" to "true",
                "CONFIG_VALUE_LANE_GUIDANCE_CONTINUE_STRAIGHT_ENABLED" to "true",
                "CONFIG_VALUE_LANE_GUIDANCE_USER_ENABLED" to "true",
            ),
        )
    }
}
