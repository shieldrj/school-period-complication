package com.shieldrj.schoolperiod.complication

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.GoalProgressComplicationData
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shieldrj.schoolperiod.engine.ScheduleEngine
import com.shieldrj.schoolperiod.model.PeriodStatus
import com.shieldrj.schoolperiod.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Publishes the current period and a live countdown to the watch face.
 *
 * The countdown is handed over as a *time reference* rather than as pre-rendered text: the watch
 * face re-renders "24m" -> "23m" -> ... itself every minute, including in ambient, without waking
 * this service. Only a change of period needs new data from here, and that is scheduled precisely
 * by [ComplicationRefreshScheduler]. Snapshot text would instead sit frozen until the platform
 * decided to grant the next (heavily throttled) update.
 */
class PeriodComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val previewStatus = PeriodStatus.Active(
            period = ScheduleEngine.regularSchedule[1], // Period 1
            secondsRemaining = TimeUnit.MINUTES.toSeconds(24),
            nextPeriod = ScheduleEngine.regularSchedule[2]
        )
        // A preview is a still image in a picker, so it uses plain snapshot text.
        return createComplicationData(type, snapshotCopy(previewStatus), tapIntent = null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val date = now.toLocalDate()
        val time = now.toLocalTime()
        val status = ScheduleEngine.getStatus(date.dayOfWeek, time)

        // Come back exactly on the next bell; until then the data below stays correct on its own.
        ComplicationRefreshScheduler.scheduleUpdateAt(
            this,
            ScheduleEngine.nextStatusChange(date, time).atZone(zone).toInstant()
        )

        return createComplicationData(
            request.complicationType,
            liveCopy(status, date, zone),
            createTapIntent()
        )
    }

    private fun createTapIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * The rendered pieces of a complication: a period label, a value that is usually a live
     * countdown, a description, and the window a progress ring should sweep.
     */
    private class Copy(
        val label: ComplicationText,
        val value: ComplicationText,
        val description: ComplicationText,
        val progress: ProgressWindow?,
        val fallbackFraction: Float
    )

    /** The span a ranged/goal complication fills over, so the ring can advance by itself. */
    private class ProgressWindow(val start: Instant, val end: Instant)

    /** Builds self-updating copy anchored to real instants. */
    private fun liveCopy(status: PeriodStatus, date: LocalDate, zone: ZoneId): Copy {
        val end = status.windowEnd?.let { instantOf(date, it, zone) }
        val start = status.windowStart?.let { instantOf(date, it, zone) }

        if (end == null) {
            // Dismissed or weekend: nothing is counting down.
            return snapshotCopy(status)
        }

        val value = countdownText(end)
        val description = when (status) {
            is PeriodStatus.Active -> countdownText(end, "${status.period.name} · ^1 left")
            is PeriodStatus.Passing -> countdownText(end, "^1 until ${status.nextPeriod.name}")
            is PeriodStatus.BeforeSchool -> countdownText(end, "^1 until ${status.firstPeriod.name}")
            else -> plainText(status.fullDescription)
        }

        return Copy(
            label = plainText(status.complicationPrimaryText),
            value = value,
            description = description,
            progress = if (start != null && start.isBefore(end)) ProgressWindow(start, end) else null,
            fallbackFraction = status.progressFraction
        )
    }

    /** Plain, non-updating copy: used for previews and for states with no countdown. */
    private fun snapshotCopy(status: PeriodStatus): Copy = Copy(
        label = plainText(status.complicationPrimaryText),
        value = plainText(status.complicationSecondaryText),
        description = plainText(status.fullDescription),
        progress = null,
        fallbackFraction = status.progressFraction
    )

    private fun createComplicationData(
        type: ComplicationType,
        copy: Copy,
        tapIntent: PendingIntent?
    ): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = copy.value,
                    contentDescription = copy.description
                )
                    .setTitle(copy.label)
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.RANGED_VALUE -> {
                rangedValueBuilder(copy)
                    .setText(copy.value)
                    .setTitle(copy.label)
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = copy.description,
                    contentDescription = copy.description
                )
                    .setTitle(copy.label)
                    .setTapAction(tapIntent)
                    .build()
            }

            // Goal progress is a Wear OS 4 (API 33) type, so even naming it has to be guarded.
            else -> if (supportsDynamicValues() && type == ComplicationType.GOAL_PROGRESS) {
                goalProgressData(copy, tapIntent)
            } else {
                null
            }
        }
    }

    private fun rangedValueBuilder(copy: Copy): RangedValueComplicationData.Builder {
        val window = copy.progress
        if (window != null && supportsDynamicValues()) {
            return RangedValueComplicationData.Builder(
                dynamicValue = elapsedFraction(window),
                fallbackValue = copy.fallbackFraction,
                min = 0f,
                max = 1f,
                contentDescription = copy.description
            )
        }
        return RangedValueComplicationData.Builder(
            value = copy.fallbackFraction,
            min = 0f,
            max = 1f,
            contentDescription = copy.description
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun goalProgressData(copy: Copy, tapIntent: PendingIntent?): ComplicationData {
        val window = copy.progress
        val builder = if (window != null) {
            GoalProgressComplicationData.Builder(
                dynamicValue = elapsedFraction(window),
                fallbackValue = copy.fallbackFraction,
                targetValue = 1f,
                contentDescription = copy.description
            )
        } else {
            GoalProgressComplicationData.Builder(
                value = copy.fallbackFraction,
                targetValue = 1f,
                contentDescription = copy.description
            )
        }
        return builder
            .setText(copy.value)
            .setTitle(copy.label)
            .setTapAction(tapIntent)
            .build()
    }

    /** Clock-driven complication values arrived with Wear OS 4. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun supportsDynamicValues(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Progress as an expression of the platform clock, so the ring sweeps continuously on
     * watch faces that support dynamic values. Watch faces that do not evaluate them fall
     * back to the static value computed when this data was built.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun elapsedFraction(window: ProgressWindow): DynamicFloat {
        val totalSeconds = (window.end.epochSecond - window.start.epochSecond).coerceAtLeast(1L)
        return DynamicInstant.withSecondsPrecision(window.start)
            .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
            .toIntSeconds()
            .asFloat()
            .div(totalSeconds.toFloat())
    }

    /**
     * A countdown the watch face renders and refreshes itself.
     *
     * [pattern] may embed the countdown with `^1`; without it the text is just the remaining time.
     */
    private fun countdownText(target: Instant, pattern: String? = null): ComplicationText =
        TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            CountDownTimeReference(target)
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .apply { pattern?.let { setText(it) } }
            .build()

    private fun plainText(text: String): ComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun instantOf(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        time.atDate(date).atZone(zone).toInstant()
}
