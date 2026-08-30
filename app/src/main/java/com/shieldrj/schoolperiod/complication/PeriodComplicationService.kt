package com.shieldrj.schoolperiod.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.GoalProgressComplicationData
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.shieldrj.schoolperiod.engine.ScheduleEngine
import com.shieldrj.schoolperiod.model.PeriodStatus
import com.shieldrj.schoolperiod.ui.MainActivity
import java.time.LocalDate
import java.time.LocalTime

class PeriodComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val previewStatus = PeriodStatus.Active(
            period = ScheduleEngine.regularSchedule[1], // Period 1
            minutesRemaining = 24,
            totalMinutes = 55,
            nextPeriod = ScheduleEngine.regularSchedule[2]
        )
        return createComplicationData(type, previewStatus, null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val status = ScheduleEngine.getCurrentStatus(LocalDate.now(), LocalTime.now())
        val tapIntent = createTapIntent()
        return createComplicationData(request.complicationType, status, tapIntent)
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

    private fun createComplicationData(
        type: ComplicationType,
        status: PeriodStatus,
        tapIntent: PendingIntent?
    ): ComplicationData? {
        val primaryText = status.complicationPrimaryText
        val secondaryText = status.complicationSecondaryText
        val description = status.fullDescription

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(secondaryText).build(),
                    contentDescription = PlainComplicationText.Builder(description).build()
                )
                    .setTitle(PlainComplicationText.Builder(primaryText).build())
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    value = status.progressFraction * 100f,
                    min = 0f,
                    max = 100f,
                    contentDescription = PlainComplicationText.Builder(description).build()
                )
                    .setText(PlainComplicationText.Builder(primaryText).build())
                    .setTitle(PlainComplicationText.Builder(secondaryText).build())
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(description).build(),
                    contentDescription = PlainComplicationText.Builder(description).build()
                )
                    .setTitle(PlainComplicationText.Builder(primaryText).build())
                    .setTapAction(tapIntent)
                    .build()
            }

            ComplicationType.GOAL_PROGRESS -> {
                GoalProgressComplicationData.Builder(
                    value = status.progressFraction * 100f,
                    targetValue = 100f,
                    contentDescription = PlainComplicationText.Builder(description).build()
                )
                    .setText(PlainComplicationText.Builder(primaryText).build())
                    .setTitle(PlainComplicationText.Builder(secondaryText).build())
                    .setTapAction(tapIntent)
                    .build()
            }

            else -> null
        }
    }
}

