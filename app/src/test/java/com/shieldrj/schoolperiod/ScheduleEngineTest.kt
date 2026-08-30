package com.shieldrj.schoolperiod

import com.shieldrj.schoolperiod.engine.ScheduleEngine
import com.shieldrj.schoolperiod.model.PeriodStatus
import com.shieldrj.schoolperiod.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class ScheduleEngineTest {

    @Test
    fun testScheduleTypeResolution() {
        assertEquals(ScheduleType.REGULAR, ScheduleEngine.getScheduleType(DayOfWeek.MONDAY))
        assertEquals(ScheduleType.REGULAR, ScheduleEngine.getScheduleType(DayOfWeek.TUESDAY))
        assertEquals(ScheduleType.REGULAR, ScheduleEngine.getScheduleType(DayOfWeek.WEDNESDAY))
        assertEquals(ScheduleType.REGULAR, ScheduleEngine.getScheduleType(DayOfWeek.THURSDAY))
        assertEquals(ScheduleType.FRIDAY, ScheduleEngine.getScheduleType(DayOfWeek.FRIDAY))
        assertEquals(ScheduleType.WEEKEND, ScheduleEngine.getScheduleType(DayOfWeek.SATURDAY))
        assertEquals(ScheduleType.WEEKEND, ScheduleEngine.getScheduleType(DayOfWeek.SUNDAY))
    }

    @Test
    fun testMondayRegularScheduleProgression() {
        val monday = DayOfWeek.MONDAY

        // 7:00 AM - Before School
        val before = ScheduleEngine.getStatus(monday, LocalTime.of(7, 0))
        assertTrue(before is PeriodStatus.BeforeSchool)
        assertEquals("P0", (before as PeriodStatus.BeforeSchool).firstPeriod.shortName)

        // 7:35 AM - Period 0
        val p0 = ScheduleEngine.getStatus(monday, LocalTime.of(7, 35))
        assertTrue(p0 is PeriodStatus.Active)
        assertEquals("P0", (p0 as PeriodStatus.Active).period.shortName)
        assertEquals(50L, p0.minutesRemaining)

        // 8:27 AM - Passing to Period 1
        val passToP1 = ScheduleEngine.getStatus(monday, LocalTime.of(8, 27))
        assertTrue(passToP1 is PeriodStatus.Passing)
        assertEquals("P1", (passToP1 as PeriodStatus.Passing).nextPeriod.shortName)
        assertEquals(3L, passToP1.minutesRemaining)

        // 8:30 AM - Period 1 start
        val p1 = ScheduleEngine.getStatus(monday, LocalTime.of(8, 30))
        assertTrue(p1 is PeriodStatus.Active)
        assertEquals("P1", (p1 as PeriodStatus.Active).period.shortName)
        assertEquals(55L, p1.minutesRemaining)

        // 9:35 AM - Period 2
        val p2 = ScheduleEngine.getStatus(monday, LocalTime.of(9, 35))
        assertTrue(p2 is PeriodStatus.Active)
        assertEquals("P2", (p2 as PeriodStatus.Active).period.shortName)

        // 10:28 AM - Nutrition Break
        val ntr = ScheduleEngine.getStatus(monday, LocalTime.of(10, 28))
        assertTrue(ntr is PeriodStatus.Active)
        assertEquals("Ntr", (ntr as PeriodStatus.Active).period.shortName)
        assertEquals(7L, ntr.minutesRemaining)

        // 10:38 AM - Passing to Period 3
        val passToP3 = ScheduleEngine.getStatus(monday, LocalTime.of(10, 38))
        assertTrue(passToP3 is PeriodStatus.Passing)
        assertEquals("P3", (passToP3 as PeriodStatus.Passing).nextPeriod.shortName)

        // 11:00 AM - Period 3
        val p3 = ScheduleEngine.getStatus(monday, LocalTime.of(11, 0))
        assertTrue(p3 is PeriodStatus.Active)
        assertEquals("P3", (p3 as PeriodStatus.Active).period.shortName)

        // 12:00 PM - Period 4
        val p4 = ScheduleEngine.getStatus(monday, LocalTime.of(12, 0))
        assertTrue(p4 is PeriodStatus.Active)
        assertEquals("P4", (p4 as PeriodStatus.Active).period.shortName)

        // 12:45 PM - Advisory
        val adv = ScheduleEngine.getStatus(monday, LocalTime.of(12, 45))
        assertTrue(adv is PeriodStatus.Active)
        assertEquals("Adv", (adv as PeriodStatus.Active).period.shortName)
        assertEquals(15L, adv.minutesRemaining)

        // 1:15 PM - Lunch
        val lunch = ScheduleEngine.getStatus(monday, LocalTime.of(13, 15))
        assertTrue(lunch is PeriodStatus.Active)
        assertEquals("Lunch", (lunch as PeriodStatus.Active).period.shortName)
        assertEquals(15L, lunch.minutesRemaining)

        // 1:32 PM - Passing to Period 5
        val passToP5 = ScheduleEngine.getStatus(monday, LocalTime.of(13, 32))
        assertTrue(passToP5 is PeriodStatus.Passing)
        assertEquals("P5", (passToP5 as PeriodStatus.Passing).nextPeriod.shortName)

        // 2:00 PM - Period 5
        val p5 = ScheduleEngine.getStatus(monday, LocalTime.of(14, 0))
        assertTrue(p5 is PeriodStatus.Active)
        assertEquals("P5", (p5 as PeriodStatus.Active).period.shortName)

        // 2:32 PM - Passing to Period 6
        val passToP6 = ScheduleEngine.getStatus(monday, LocalTime.of(14, 32))
        assertTrue(passToP6 is PeriodStatus.Passing)
        assertEquals("P6", (passToP6 as PeriodStatus.Passing).nextPeriod.shortName)

        // 3:00 PM - Period 6
        val p6 = ScheduleEngine.getStatus(monday, LocalTime.of(15, 0))
        assertTrue(p6 is PeriodStatus.Active)
        assertEquals("P6", (p6 as PeriodStatus.Active).period.shortName)
        assertEquals(30L, p6.minutesRemaining)

        // 3:30 PM - School Dismissal
        val after = ScheduleEngine.getStatus(monday, LocalTime.of(15, 30))
        assertTrue(after is PeriodStatus.AfterSchool)
        assertEquals(LocalTime.of(15, 30), (after as PeriodStatus.AfterSchool).dismissalTime)
    }

    @Test
    fun testFridayScheduleProgression() {
        val friday = DayOfWeek.FRIDAY

        // 7:15 AM - Period 0
        val p0 = ScheduleEngine.getStatus(friday, LocalTime.of(7, 15))
        assertTrue(p0 is PeriodStatus.Active)
        assertEquals("P0", (p0 as PeriodStatus.Active).period.shortName)

        // 8:20 AM - Teacher Collaboration
        val collab = ScheduleEngine.getStatus(friday, LocalTime.of(8, 20))
        assertTrue(collab is PeriodStatus.Active)
        assertEquals("Collab", (collab as PeriodStatus.Active).period.shortName)

        // 8:57 AM - Passing to Period 1
        val passToP1 = ScheduleEngine.getStatus(friday, LocalTime.of(8, 57))
        assertTrue(passToP1 is PeriodStatus.Passing)
        assertEquals("P1", (passToP1 as PeriodStatus.Passing).nextPeriod.shortName)
        assertEquals(3L, passToP1.minutesRemaining)

        // 9:10 AM - Period 1 (48m class: 9:00 - 9:48)
        val p1 = ScheduleEngine.getStatus(friday, LocalTime.of(9, 10))
        assertTrue(p1 is PeriodStatus.Active)
        assertEquals("P1", (p1 as PeriodStatus.Active).period.shortName)
        assertEquals(38L, p1.minutesRemaining)

        // 9:50 AM - Passing to Period 2 (9:48 - 9:53)
        val passToP2 = ScheduleEngine.getStatus(friday, LocalTime.of(9, 50))
        assertTrue(passToP2 is PeriodStatus.Passing)
        assertEquals("P2", (passToP2 as PeriodStatus.Passing).nextPeriod.shortName)

        // 10:00 AM - Period 2 (9:53 - 10:41)
        val p2 = ScheduleEngine.getStatus(friday, LocalTime.of(10, 0))
        assertTrue(p2 is PeriodStatus.Active)
        assertEquals("P2", (p2 as PeriodStatus.Active).period.shortName)

        // 10:45 AM - Nutrition (10:41 - 10:51)
        val ntr = ScheduleEngine.getStatus(friday, LocalTime.of(10, 45))
        assertTrue(ntr is PeriodStatus.Active)
        assertEquals("Ntr", (ntr as PeriodStatus.Active).period.shortName)
        assertEquals(6L, ntr.minutesRemaining)

        // 11:00 AM - Period 3 (10:56 - 11:44)
        val p3 = ScheduleEngine.getStatus(friday, LocalTime.of(11, 0))
        assertTrue(p3 is PeriodStatus.Active)
        assertEquals("P3", (p3 as PeriodStatus.Active).period.shortName)

        // 12:00 PM - Period 4 (11:49 - 12:37)
        val p4 = ScheduleEngine.getStatus(friday, LocalTime.of(12, 0))
        assertTrue(p4 is PeriodStatus.Active)
        assertEquals("P4", (p4 as PeriodStatus.Active).period.shortName)

        // 12:50 PM - Lunch (12:37 - 1:07)
        val lunch = ScheduleEngine.getStatus(friday, LocalTime.of(12, 50))
        assertTrue(lunch is PeriodStatus.Active)
        assertEquals("Lunch", (lunch as PeriodStatus.Active).period.shortName)
        assertEquals(17L, lunch.minutesRemaining)

        // 1:30 PM - Period 5 (1:12 - 2:00)
        val p5 = ScheduleEngine.getStatus(friday, LocalTime.of(13, 30))
        assertTrue(p5 is PeriodStatus.Active)
        assertEquals("P5", (p5 as PeriodStatus.Active).period.shortName)

        // 2:02 PM - Passing to Period 6 (2:00 - 2:05)
        val passToP6 = ScheduleEngine.getStatus(friday, LocalTime.of(14, 2))
        assertTrue(passToP6 is PeriodStatus.Passing)
        assertEquals("P6", (passToP6 as PeriodStatus.Passing).nextPeriod.shortName)

        // 2:10 PM - Period 6 (2:05 - 2:53)
        val p6 = ScheduleEngine.getStatus(friday, LocalTime.of(14, 10))
        assertTrue(p6 is PeriodStatus.Active)
        assertEquals("P6", (p6 as PeriodStatus.Active).period.shortName)
        assertEquals(43L, p6.minutesRemaining)

        // 2:53 PM - Dismissal on Friday!
        val after = ScheduleEngine.getStatus(friday, LocalTime.of(14, 53))
        assertTrue(after is PeriodStatus.AfterSchool)
        assertEquals(LocalTime.of(14, 53), (after as PeriodStatus.AfterSchool).dismissalTime)
    }

    @Test
    fun testWeekendStatus() {
        val sat = ScheduleEngine.getStatus(DayOfWeek.SATURDAY, LocalTime.of(12, 0))
        assertEquals(PeriodStatus.Weekend, sat)

        val sun = ScheduleEngine.getStatus(DayOfWeek.SUNDAY, LocalTime.of(8, 0))
        assertEquals(PeriodStatus.Weekend, sun)
    }
}

