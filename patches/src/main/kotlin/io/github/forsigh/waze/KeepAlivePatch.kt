package io.github.forsigh.waze

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
import app.morphe.patcher.patch.stringOption
import io.github.forsigh.waze.WazeConstants.COMPATIBILITY_WAZE

/**
 * Keep Waze alive (opt-in, default off).
 *
 * One row for the three places Waze closes itself, because from the outside it is one wish:
 *
 *  - phone: `CONFIG_VALUE_POWER_BACKGROUND_TIMEOUT_MS` is Waze's own background timeout when no
 *    route is running. Waze's cap is 10 minutes; it still closes when you close it.
 *  - Android Auto projection: `CONFIG_VALUE_AAP_SHUTDOWN_DEFERRING_ENABLED` (the `deferShutdown`
 *    gate in `com.waze.car_lib.bo`) plus `CONFIG_VALUE_AAP_SHUTDOWN_DELAY_ON_MAIN_SERVICE_DESTROY_MS`,
 *    the window `com.waze.car_lib.bh.f()` waits before shutting the service down. A reconnect
 *    inside the window cancels the shutdown, so re-entering the car resumes instead of cold-starting.
 *  - Android Automotive: `CONFIG_VALUE_AAOS_KEEP_APP_ALIVE_ON_MAIN_SERVICE_DESTROY` (read in
 *    `com.waze.car_lib.bo` and `com.waze.bw.a.a.b`) survives the car unit restarting Waze's main
 *    service. Harmless on a projection-only setup.
 *
 * The `where` option picks the surface; `minutes` drives both time-based gates.
 *
 * PROVENANCE: the phone timeout is ported from bearinmindcat/morphe-patches (GPLv3, v2.5.5) -
 * see `CREDITS.md`.
 */
@Suppress("unused")
val keepAlivePatch = bytecodePatch(
    name = "Keep Waze alive",
    description = "Stops Waze closing when you leave it.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_WAZE)
    category("Experimental")

    dependsOn(configOverridesPatch)

    val where by stringOption(
        "where", "everywhere",
        mapOf(
            "Everywhere" to "everywhere",
            "Phone only" to "phone",
            "Android Auto only" to "auto",
            "Car unit only" to "car",
        ),
        title = "Where",
        description = "Which surface stays alive.",
    )

    val minutes by intOption(
        key = "minutes", default = 10,
        title = "Minutes alive",
        description = "Phone background timeout and the Android Auto shutdown delay. Waze's own cap is 10.",
    )

    execute {
        val w = where ?: "everywhere"
        val m = (minutes ?: 10).toLong().coerceIn(1, 10)
        requireChoice("keepAlivePatch", "where", w, setOf("everywhere", "phone", "auto", "car"))
        val phone = "CONFIG_VALUE_POWER_BACKGROUND_TIMEOUT_MS"
        val defer = "CONFIG_VALUE_AAP_SHUTDOWN_DEFERRING_ENABLED"
        val delay = "CONFIG_VALUE_AAP_SHUTDOWN_DELAY_ON_MAIN_SERVICE_DESTROY_MS"
        val car = "CONFIG_VALUE_AAOS_KEEP_APP_ALIVE_ON_MAIN_SERVICE_DESTROY"
        val ms = (m * 60_000L).toString()
        fun surface(phoneOn: Boolean, autoOn: Boolean, carOn: Boolean) = mapOf(
            phone to if (phoneOn) "\$experimental.minutes.alive*60000" else "stock",
            defer to if (autoOn) "true" else "stock",
            delay to if (autoOn) "\$experimental.minutes.alive*60000" else "stock",
            car to if (carOn) "true" else "stock",
        )
        InAppMenu.number(
            "Experimental", "Minutes alive",
            "Phone background timeout and the Android Auto shutdown delay. Waze's own cap is 10.",
            m, 1, 10,
        )
        InAppMenu.choice(
            "Experimental", "Keep Waze alive",
            "Which surface stays alive. Minutes alive is the row above.",
            w,
            listOf(
                "everywhere" to "Everywhere",
                "phone" to "Phone only",
                "auto" to "Android Auto only",
                "car" to "Car unit only",
            ),
            mapOf(
                "everywhere" to surface(true, true, true),
                "phone" to surface(true, false, false),
                "auto" to surface(false, true, false),
                "car" to surface(false, false, true),
            ),
            mapOf(
                phone to if (w == "everywhere" || w == "phone") ms else null,
                defer to if (w == "everywhere" || w == "auto") "true" else null,
                delay to if (w == "everywhere" || w == "auto") ms else null,
                car to if (w == "everywhere" || w == "car") "true" else null,
            ),
        )

        println("keepAlivePatch: $w, $m minute(s)")
    }
}
