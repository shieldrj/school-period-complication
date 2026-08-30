package com.shieldrj.schoolperiod.engine

import com.shieldrj.schoolperiod.model.BellPeriod
import com.shieldrj.schoolperiod.model.PeriodStatus
import com.shieldrj.schoolperiod.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

object ScheduleEngine {

    /**
     * Regular Monday - Thursday Schedule.
     */
    val regularSchedule: List<BellPeriod> = listOf(
        BellPeriod("Period 0", "P0", LocalTime.of(7, 30), LocalTime.of(8, 25)),
        BellPeriod("Period 1", "P1", LocalTime.of(8, 30), LocalTime.of(9, 25)),
        BellPeriod("Period 2", "P2", LocalTime.of(9, 30), LocalTime.of(10, 25)),
        BellPeriod("Nutrition Break", "Ntr", LocalTime.of(10, 25), LocalTime.of(10, 35), isInstructional = false),
        BellPeriod("Period 3", "P3", LocalTime.of(10, 40), LocalTime.of(11, 35)),
        BellPeriod("Period 4", "P4", LocalTime.of(11, 40), LocalTime.of(12, 35)),
        BellPeriod("Advisory", "Adv", LocalTime.of(12, 35), LocalTime.of(13, 0), isInstructional = false),
        BellPeriod("Lunch", "Lunch", LocalTime.of(13, 0), LocalTime.of(13, 30), isInstructional = false),
        BellPeriod("Period 5", "P5", LocalTime.of(13, 35), LocalTime.of(14, 30)),
        BellPeriod("Period 6", "P6", LocalTime.of(14, 35), LocalTime.of(15, 30))
    )

    /**
     * Friday Collaboration & 48-minute class schedule (Dismissal at 2:53 PM).
     */
    val fridaySchedule: List<BellPeriod> = listOf(
        BellPeriod("Period 0", "P0", LocalTime.of(7, 6), LocalTime.of(7, 54)),
        BellPeriod("Teacher Collaboration", "Collab", LocalTime.of(8, 0), LocalTime.of(8, 55), isInstructional = false),
        BellPeriod("Period 1", "P1", LocalTime.of(9, 0), LocalTime.of(9, 48)),
        BellPeriod("Period 2", "P2", LocalTime.of(9, 53), LocalTime.of(10, 41)),
        BellPeriod("Nutrition Break", "Ntr", LocalTime.of(10, 41), LocalTime.of(10, 51), isInstructional = false),
        BellPeriod("Period 3", "P3", LocalTime.of(10, 56), LocalTime.of(11, 44)),
        BellPeriod("Period 4", "P4", LocalTime.of(11, 49), LocalTime.of(12, 37)),
        BellPeriod("Lunch", "Lunch", LocalTime.of(12, 37), LocalTime.of(13, 7), isInstructional = false),
        BellPeriod("Period 5", "P5", LocalTime.of(13, 12), LocalTime.of(14, 0)),
        BellPeriod("Period 6", "P6", LocalTime.of(14, 5), LocalTime.of(14, 53))
    )

    /**
     * Resolves the schedule type for a given day of week.
     */
    fun getScheduleType(dayOfWeek: DayOfWeek): ScheduleType {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY -> ScheduleType.REGULAR
            DayOfWeek.FRIDAY -> ScheduleType.FRIDAY
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY -> ScheduleType.WEEKEND
        }
    }

    /**
     * Returns the list of periods for the given schedule type.
     */
    fun getSchedule(scheduleType: ScheduleType): List<BellPeriod> {
        return when (scheduleType) {
            ScheduleType.REGULAR -> regularSchedule
            ScheduleType.FRIDAY -> fridaySchedule
            ScheduleType.WEEKEND -> emptyList()
        }
    }

    /**
     * Determines current period status for a given day and time.
     */
    fun getStatus(dayOfWeek: DayOfWeek, time: LocalTime): PeriodStatus {
        val scheduleType = getScheduleType(dayOfWeek)
        if (scheduleType == ScheduleType.WEEKEND) {
            return PeriodStatus.Weekend
        }

        val periods = getSchedule(scheduleType)
        if (periods.isEmpty()) {
            return PeriodStatus.Weekend
        }

        val firstPeriod = periods.first()
        val lastPeriod = periods.last()

        if (time.isBefore(firstPeriod.startTime)) {
            val minutesUntilStart = ChronoUnit.MINUTES.between(time, firstPeriod.startTime)
            return PeriodStatus.BeforeSchool(firstPeriod, minutesUntilStart)
        }

        if (time == lastPeriod.endTime || time.isAfter(lastPeriod.endTime)) {
            return PeriodStatus.AfterSchool(lastPeriod.endTime)
        }

        // Check if inside an active period
        for (i in periods.indices) {
            val period = periods[i]
            if (period.contains(time)) {
                val minutesRemaining = ChronoUnit.MINUTES.between(time, period.endTime)
                val totalMinutes = period.durationMinutes
                val nextPeriod = if (i + 1 < periods.size) periods[i + 1] else null
                return PeriodStatus.Active(
                    period = period,
                    minutesRemaining = minutesRemaining,
                    totalMinutes = totalMinutes,
                    nextPeriod = nextPeriod
                )
            }
        }

        // If not in an active period and between first and last period, it's a passing period
        for (i in 0 until periods.size - 1) {
            val currentPeriod = periods[i]
            val nextPeriod = periods[i + 1]

            if ((time == currentPeriod.endTime || time.isAfter(currentPeriod.endTime)) &&
                time.isBefore(nextPeriod.startTime)
            ) {
                val minutesRemaining = ChronoUnit.MINUTES.between(time, nextPeriod.startTime)
                val totalPassingMinutes = ChronoUnit.MINUTES.between(currentPeriod.endTime, nextPeriod.startTime)
                return PeriodStatus.Passing(
                    nextPeriod = nextPeriod,
                    minutesRemaining = minutesRemaining,
                    totalPassingMinutes = totalPassingMinutes
                )
            }
        }

        return PeriodStatus.AfterSchool(lastPeriod.endTime)
    }

    /**
     * Convenience method using current system date & time.
     */
    fun getCurrentStatus(
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now()
    ): PeriodStatus {
        return getStatus(date.dayOfWeek, time)
    }
}

