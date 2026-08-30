package com.shieldrj.schoolperiod.model

import java.time.DayOfWeek
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
 * Represents the current status at any given time of day.
 */
sealed class PeriodStatus {
    abstract val complicationPrimaryText: String
    abstract val complicationSecondaryText: String
    abstract val complicationShortTitle: String
    abstract val fullDescription: String
    abstract val progressFraction: Float

    data class Active(
        val period: BellPeriod,
        val minutesRemaining: Long,
        val totalMinutes: Long,
        val nextPeriod: BellPeriod?
    ) : PeriodStatus() {
        override val complicationPrimaryText: String
            get() = period.shortName

        override val complicationSecondaryText: String
            get() = "${minutesRemaining}m"

        override val complicationShortTitle: String
            get() = period.shortName

        override val fullDescription: String
            get() = "${period.name} · ${minutesRemaining}m left"

        override val progressFraction: Float
            get() = if (totalMinutes > 0) {
                ((totalMinutes - minutesRemaining).toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }

    data class Passing(
        val nextPeriod: BellPeriod,
        val minutesRemaining: Long,
        val totalPassingMinutes: Long
    ) : PeriodStatus() {
        override val complicationPrimaryText: String
            get() = "Pass"

        override val complicationSecondaryText: String
            get() = "${minutesRemaining}m"

        override val complicationShortTitle: String
            get() = "Pass"

        override val fullDescription: String
            get() = "Passing to ${nextPeriod.shortName} (${minutesRemaining}m left)"

        override val progressFraction: Float
            get() = if (totalPassingMinutes > 0) {
                ((totalPassingMinutes - minutesRemaining).toFloat() / totalPassingMinutes.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }

    data class BeforeSchool(
        val firstPeriod: BellPeriod,
        val minutesUntilStart: Long
    ) : PeriodStatus() {
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
    }
}

