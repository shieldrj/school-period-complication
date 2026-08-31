package com.shieldrj.schoolperiod.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.shieldrj.schoolperiod.engine.ScheduleEngine
import com.shieldrj.schoolperiod.model.BellPeriod
import com.shieldrj.schoolperiod.model.PeriodStatus
import com.shieldrj.schoolperiod.model.ScheduleType
import com.shieldrj.schoolperiod.ui.theme.SchoolPeriodTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SchoolPeriodTheme {
                SchoolPeriodApp()
            }
        }
    }
}

@Composable
fun SchoolPeriodApp() {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedScheduleType by remember { mutableStateOf<ScheduleType?>(null) }

    // Live clock ticker, re-aligned to the top of each second so it cannot drift behind
    // the countdown the watch face is drawing.
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            currentTime = now
            currentDate = LocalDate.now()
            delay(1000L - (now.nano / 1_000_000L))
        }
    }

    val currentStatus = remember(currentDate, currentTime) {
        ScheduleEngine.getStatus(currentDate.dayOfWeek, currentTime)
    }

    val effectiveScheduleType = selectedScheduleType ?: ScheduleEngine.getScheduleType(currentDate.dayOfWeek)
    val displayPeriods = remember(effectiveScheduleType) {
        ScheduleEngine.getSchedule(
            if (effectiveScheduleType == ScheduleType.WEEKEND) ScheduleType.REGULAR else effectiveScheduleType
        )
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header & Live Status Card
            item {
                StatusHeroCard(status = currentStatus)
            }

            // Schedule Mode Selector Button
            item {
                ScheduleModeButton(
                    currentType = effectiveScheduleType,
                    onToggle = {
                        selectedScheduleType = when (effectiveScheduleType) {
                            ScheduleType.REGULAR -> ScheduleType.FRIDAY
                            ScheduleType.FRIDAY -> ScheduleType.REGULAR
                            ScheduleType.WEEKEND -> ScheduleType.REGULAR
                        }
                    }
                )
            }

            // Schedule Section Header
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (effectiveScheduleType == ScheduleType.FRIDAY) "Friday Schedule" else "Mon-Thu Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Full Bell Schedule Timeline
            items(displayPeriods) { period ->
                val isCurrent = period.contains(currentTime) &&
                        (selectedScheduleType == null || selectedScheduleType == ScheduleEngine.getScheduleType(currentDate.dayOfWeek))

                PeriodRowCard(
                    period = period,
                    isCurrentPeriod = isCurrent
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun StatusHeroCard(status: PeriodStatus) {
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is PeriodStatus.Active -> MaterialTheme.colorScheme.surfaceContainer
                is PeriodStatus.Passing -> Color(0xFF332A15) // Warm amber container
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (status) {
                is PeriodStatus.Active -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = status.period.shortName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${status.minutesRemaining}m left",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = status.period.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { status.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

                is PeriodStatus.Passing -> {
                    Text(
                        text = "Passing Period",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Text(
                        text = "${status.minutesRemaining}m until ${status.nextPeriod.shortName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { status.progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }

                is PeriodStatus.BeforeSchool -> {
                    Text(
                        text = "Before School",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Starts ${status.firstPeriod.startTime} (${status.firstPeriod.shortName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                is PeriodStatus.AfterSchool -> {
                    Text(
                        text = "School Dismissed",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Ended at ${status.dismissalTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PeriodStatus.Weekend -> {
                    Text(
                        text = "Weekend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Enjoy your days off!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ScheduleModeButton(
    currentType: ScheduleType,
    onToggle: () -> Unit
) {
    Button(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        colors = ButtonDefaults.filledTonalButtonColors()
    ) {
        Text(
            text = when (currentType) {
                ScheduleType.FRIDAY -> "Switch: Mon-Thu"
                else -> "Switch: Friday"
            },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm")

@Composable
fun PeriodRowCard(
    period: BellPeriod,
    isCurrentPeriod: Boolean
) {
    val startStr = period.startTime.format(timeFormatter)
    val endStr = period.endTime.format(timeFormatter)

    val background = if (isCurrentPeriod) {
        Color(0xFF0C4A6E) // Deep Sky Highlight
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = period.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentPeriod) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrentPeriod) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (isCurrentPeriod) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "NOW",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
                Text(
                    text = "$startStr – $endStr (${period.durationMinutes}m)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

