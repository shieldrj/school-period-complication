package com.shieldrj.schoolperiod.model

import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Represents a single period, break, or event in the school day.
 */
data class BellPeriod(
    val name: String,
    val shortName: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val isInstructional: Boolean = true
) {
    val durationMinutes: Long
        get() = ChronoUnit.MINUTES.between(startTime, endTime)

    fun contains(time: LocalTime): Boolean {
        return (time == startTime || time.isAfter(startTime)) && time.isBefore(endTime)
    }
}

/**
 * Schedule type for a given school day.
 */
enum class ScheduleType(val displayName: String) {
    REGULAR("Regular Schedule (Mon-Thu)"),
    FRIDAY("Friday Collaboration Schedule"),
    WEEKEND("Weekend")
}

/**
 * Rounds a remaining-seconds value up to whole minutes, so a countdown reads
 * "1m" for the whole final minute and only reaches "0m" when the bell rings.
 * This matches how the watch face renders [java.util.concurrent.TimeUnit.MINUTES]
 * time-difference text, keeping the app and the complication in agreement.
 */
internal fun ceilMinutes(seconds: Long): Long =
    if (seconds <= 0L) 0L else (seconds + 59L) / 60L

/**
 * Represents the current status at any given time of day.
 */
sealed class PeriodStatus {
    abstract val complicationPrimaryText: String
    abstract val complicationSecondaryText: String
    abstract val complicationShortTitle: String
    abstract val fullDescription: String
    abstract val progressFraction: Float

    /**
     * Start of the window this status is counting through, or null when nothing is
     * counting down. Together with [windowEnd] this lets the complication hand the
     * watch face a live countdown instead of a text snapshot.
     */
    abstract val windowStart: LocalTime?

    /**
     * The exact time this status stops being true — the moment the complication
     * needs to be rebuilt. Null when no change is pending today.
     */
    abstract val windowEnd: LocalTime?

    data class Active(
        val period: BellPeriod,
        val secondsRemaining: Long,
        val nextPeriod: BellPeriod?
    ) : PeriodStatus() {
        val minutesRemaining: Long
            get() = ceilMinutes(secondsRemaining)

        val totalSeconds: Long
            get() = ChronoUnit.SECONDS.between(period.startTime, period.endTime)

        val totalMinutes: Long
            get() = period.durationMinutes

        override val complicationPrimaryText: String
            get() = period.shortName

        override val complicationSecondaryText: String
            get() = "${minutesRemaining}m"

        override val complicationShortTitle: String
            get() = period.shortName

        override val fullDescription: String
            get() = "${period.name} · ${minutesRemaining}m left"

        override val progressFraction: Float
            get() = if (totalSeconds > 0) {
                ((totalSeconds - secondsRemaining).toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
            } else 0f

        override val windowStart: LocalTime?
            get() = period.startTime

        override val windowEnd: LocalTime?
            get() = period.endTime
    }

    data class Passing(
        val nextPeriod: BellPeriod,
        val secondsRemaining: Long,
        val totalPassingSeconds: Long
    ) : PeriodStatus() {
        val minutesRemaining: Long
            get() = ceilMinutes(secondsRemaining)

        val totalPassingMinutes: Long
            get() = ceilMinutes(totalPassingSeconds)

        override val complicationPrimaryText: String
            get() = "Pass"

        override val complicationSecondaryText: String
            get() = "${minutesRemaining}m"

        override val complicationShortTitle: String
            get() = "Pass"

        override val fullDescription: String
            get() = "Passing to ${nextPeriod.shortName} (${minutesRemaining}m left)"

        override val progressFraction: Float
            get() = if (totalPassingSeconds > 0) {
                ((totalPassingSeconds - secondsRemaining).toFloat() / totalPassingSeconds.toFloat())
                    .coerceIn(0f, 1f)
            } else 0f

        override val windowStart: LocalTime?
            get() = nextPeriod.startTime.minusSeconds(totalPassingSeconds)

        override val windowEnd: LocalTime?
            get() = nextPeriod.startTime
    }

    data class BeforeSchool(
        val firstPeriod: BellPeriod,
        val secondsUntilStart: Long
    ) : PeriodStatus() {
        val minutesUntilStart: Long
            get() = ceilMinutes(secondsUntilStart)

        override val complicationPrimaryText: String
            get() = "Off"

        override val complicationSecondaryText: String
            get() = firstPeriod.shortName

        override val complicationShortTitle: String
            get() = "Off"

        override val fullDescription: String
            get() = "Starts at ${firstPeriod.startTime} (${firstPeriod.name})"

        override val progressFraction: Float
            get() = 0f

        // No progress window: the wait since midnight is not meaningful to draw.
        override val windowStart: LocalTime?
            get() = null

        override val windowEnd: LocalTime?
            get() = firstPeriod.startTime
    }

    data class AfterSchool(
        val dismissalTime: LocalTime
    ) : PeriodStatus() {
        override val complicationPrimaryText: String
            get() = "Out"

        override val complicationSecondaryText: String
            get() = "End"

        override val complicationShortTitle: String
            get() = "Out"

        override val fullDescription: String
            get() = "School dismissed at $dismissalTime"

        override val progressFraction: Float
            get() = 1f

        override val windowStart: LocalTime?
            get() = null

        override val windowEnd: LocalTime?
            get() = null
    }

    data object Weekend : PeriodStatus() {
        override val complicationPrimaryText: String
            get() = "Off"

        override val complicationSecondaryText: String
            get() = "Wknd"

        override val complicationShortTitle: String
            get() = "Off"

        override val fullDescription: String
            get() = "Weekend / Off Duty"

        override val progressFraction: Float
            get() = 0f

        override val windowStart: LocalTime?
            get() = null

        override val windowEnd: LocalTime?
            get() = null
    }
}
