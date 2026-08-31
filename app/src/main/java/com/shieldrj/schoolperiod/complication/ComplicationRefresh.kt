package com.shieldrj.schoolperiod.complication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.time.Instant

/**
 * Wakes the complication up exactly when the bell rings.
 *
 * The countdown itself is rendered by the watch face and needs no help, but the period label
 * ("P1", "Pass", "Lunch") only changes when the data source is asked for new data. The platform's
 * own UPDATE_PERIOD_SECONDS is a throttled hint, so a bell can otherwise come and go minutes
 * before the label catches up. A single alarm per status change closes that gap.
 */
internal object ComplicationRefreshScheduler {

    private const val TAG = "PeriodComplication"
    private const val REFRESH_REQUEST_CODE = 8421

    /** Action for the self-scheduled refresh alarm. */
    const val ACTION_REFRESH = "com.shieldrj.schoolperiod.action.REFRESH_COMPLICATION"

    fun scheduleUpdateAt(context: Context, instant: Instant) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = instant.toEpochMilli()
        val pendingIntent = refreshPendingIntent(context)

        // Exact alarms are ideal but are not granted to every app on API 31+. An inexact
        // alarm still lands close to the bell, and the countdown keeps ticking either way.
        val exactAllowed =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, falling back to an inexact refresh", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /** Asks the platform to re-request data from [PeriodComplicationService]. */
    fun requestUpdateNow(context: Context) {
        ComplicationDataSourceUpdateRequester
            .create(
                context = context,
                complicationDataSourceComponent = ComponentName(
                    context,
                    PeriodComplicationService::class.java
                )
            )
            .requestUpdateAll()
    }

    private fun refreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ComplicationRefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Requests fresh complication data when a bell alarm fires, and re-arms the chain after the
 * events that invalidate it: a reboot (which clears alarms) or a clock/time-zone change.
 */
class ComplicationRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The resulting data request schedules the next alarm, so the chain re-arms itself.
        ComplicationRefreshScheduler.requestUpdateNow(context)
    }
}
