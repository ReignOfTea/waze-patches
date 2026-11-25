package io.github.forsigh.waze

import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import io.github.forsigh.waze.WazeConstants.COMPATIBILITY_WAZE

/**
 * Speed limits — one row, four gears.
 *
 *   alwaysOn   - the sign stays on screen instead of only while over the limit
 *   decrease   - warns when the limit drops, 1000 m ahead
 *   report     - lets you report the limit yourself
 *   style      - forces the sign design; "stock" leaves Waze's country choice alone
 *
 * Each gear keeps the default of the row it replaces, so nothing changes for existing users.
 */
@Suppress("unused")
val speedLimitsPatch = bytecodePatch(
    name = "Speed limits",
    description = "Sign always shown, limit-drop warnings, reporting and sign style.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_WAZE)
    category("Map & Alerts")

    dependsOn(configOverridesPatch)

    val alwaysOn by booleanOption(
        "alwaysOn", true,
        title = "Sign always shown",
        description = "Keeps the limit sign on screen when you are under the limit. Stock: hides.",
    )

    val decrease by booleanOption(
        "decrease", true,
        title = "Limit-drop warnings",
        description = "Warns when the limit falls, 1000 m ahead. Stock: on.",
    )

    val report by booleanOption(
        "report", false,
        title = "Report the speed limit",
        description = "Adds the report-limit option. Stock: off.",
    )

    val style by stringOption(
        "style", "stock",
        mapOf(
            "Stock (by country)" to "stock",
            "US (white rectangle)" to "us",
            "World (red circle)" to "world",
        ),
        title = "Sign style",
        description = "Waze picks one by country; this forces it.",
    )

    execute {
        val chosen = style ?: "stock"
        requireChoice("speedLimitsPatch", "style", chosen, setOf("stock", "us", "world"))
        InAppMenu.switch(
            "Map & Alerts", "Speed limit sign always shown",
            "Keeps the limit sign on screen when you are under the limit.",
            alwaysOn == true,
            mapOf(
                "CONFIG_VALUE_MAP_SPEEDOMETER_SPEED_LIMIT_ENABLED" to "true",
                "CONFIG_VALUE_MAP_SPEEDOMETER_SPEED_LIMIT_USER_ENABLED" to "always",
            ),
        )
        InAppMenu.switch(
            "Map & Alerts", "Limit-drop warnings",
            "Warns when the limit falls, 1000 m ahead.",
            decrease == true,
            mapOf(
                "CONFIG_VALUE_NOTIFICATIONS_ON_ROUTE_SPEED_LIMIT_DECREASE" to "true",
                "CONFIG_VALUE_ALERTS_SPEED_LIMIT_DECREASE_ALERT_DISTANCE_METERS" to "1000",
            ),
        )
        InAppMenu.switch(
            "Map & Alerts", "Report the speed limit",
            "Adds the report-limit option.",
            report == true,
            mapOf("CONFIG_VALUE_MAP_SPEEDOMETER_SPEED_LIMIT_OVERRIDE_ENABLED" to "true"),
        )
        InAppMenu.choice(
            "Map & Alerts", "Speed limit sign style",
            "Waze picks one by country. Stock leaves that choice alone.",
            chosen,
            listOf("stock" to "Stock (by country)", "us" to "US (white rectangle)", "world" to "World (red circle)"),
            mapOf(
                "stock" to mapOf("CONFIG_VALUE_MAP_SPEEDOMETER_STYLE" to "stock"),
                "us" to mapOf("CONFIG_VALUE_MAP_SPEEDOMETER_STYLE" to "us"),
                "world" to mapOf("CONFIG_VALUE_MAP_SPEEDOMETER_STYLE" to "world"),
            ),
            mapOf("CONFIG_VALUE_MAP_SPEEDOMETER_STYLE" to chosen.takeUnless { it == "stock" }),
        )
        println("speedLimitsPatch: alwaysOn = ${alwaysOn == true}, decrease = ${decrease == true}, report = ${report == true}, style = $chosen")
    }
}
