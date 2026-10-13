package com.cactusbyte.scouttrace

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Date-only advisory: Android security patch level is not a live vulnerability scan. */
object SecurityPatchReview {
    enum class Status { UNAVAILABLE, UNRECOGNIZED, FUTURE_DATED, RECENT_DATE, OLDER_DATE }

    data class Result(val status: Status, val ageDays: Long?, val reviewRecommended: Boolean, val detail: String)

    fun assess(patchLevel: String?, today: LocalDate = LocalDate.now()): Result {
        if (patchLevel.isNullOrBlank()) return Result(Status.UNAVAILABLE, null, true,
            "Android did not provide a security patch date; verify it in system settings.")
        val parsed = try { LocalDate.parse(patchLevel) } catch (_: Exception) { null }
        if (parsed == null) return Result(Status.UNRECOGNIZED, null, true,
            "The reported patch level is not a recognized date. Check Android system settings.")
        val days = ChronoUnit.DAYS.between(parsed, today)
        if (days < 0) return Result(Status.FUTURE_DATED, null, false,
            "Android reports a patch date later than today's device date. Check the clock and system update information.")
        return if (days >= 180) Result(Status.OLDER_DATE, days, true,
            "Android reports a security patch date at least 180 days old. Check for manufacturer updates; this does not prove a vulnerability or infection.")
        else Result(Status.RECENT_DATE, days, false,
            "Android reports a security patch date within the last 180 days. This does not establish that every vulnerability is patched.")
    }
}
