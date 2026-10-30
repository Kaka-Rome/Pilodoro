package com.pomodorofocus.app.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodorofocus.app.model.ChartBarItem
import com.pomodorofocus.app.model.StatsPeriod

@Composable
fun FocusBarChart(
    chartItems: List<ChartBarItem>,
    period: StatsPeriod,
    modifier: Modifier = Modifier
) {
    val maxMinutes = (chartItems.maxOfOrNull { it.durationMinutes } ?: 0L).coerceAtLeast(1L)
    val maxBarHeight = 130.dp

    val scrollState = rememberScrollState()
    val isScrollable = period == StatsPeriod.MONTHLY

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("focus_bar_chart")
    ) {
        val rowModifier = if (isScrollable) {
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp)
        }

        Row(
            modifier = rowModifier,
            horizontalArrangement = if (isScrollable) Arrangement.spacedBy(6.dp) else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            chartItems.forEachIndexed { index, item ->
                val barWeight = if (isScrollable) Modifier.width(28.dp) else Modifier.weight(1f)
                val duration = item.durationMinutes
                val hasDuration = duration > 0

                val barHeight = if (hasDuration) {
                    val fraction = (duration.toFloat() / maxMinutes.toFloat()).coerceIn(0.08f, 1f)
                    maxBarHeight * fraction
                } else {
                    4.dp
                }

                val barColor = when {
                    item.isHighlighted && hasDuration -> MaterialTheme.colorScheme.primary
                    item.isHighlighted && !hasDuration -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    hasDuration -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }

                Column(
                    modifier = barWeight.padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Duration label on top of bar
                    Text(
                        text = if (hasDuration) "${duration}m" else "",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isScrollable) 8.sp else 10.sp,
                            fontWeight = if (item.isHighlighted) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (item.isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bar element
                    Box(
                        modifier = Modifier
                            .height(maxBarHeight)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isScrollable) 0.85f else 0.7f)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor)
                                .testTag("chart_bar_$index")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bottom axis label (e.g., "Mon", "Tue" or "1", "2")
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isScrollable) 9.sp else 11.sp,
                            fontWeight = if (item.isHighlighted) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (item.isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
