package com.pomodorofocus.app.data

import com.pomodorofocus.app.model.ChartBarItem
import com.pomodorofocus.app.model.FocusSessionRecord
import com.pomodorofocus.app.model.StatsPeriod
import com.pomodorofocus.app.model.StatsPeriodData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Calculates daily, weekly, and monthly focus statistics from completed session history.
 * Respects local date, time zone, and defines the week consistently starting on Monday.
 */
object FocusStatisticsCalculator {

    /** Jumlah sesi Focus yang selesai pada hari ini (tanggal lokal). */
    fun countSessionsToday(
        records: List<FocusSessionRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Int {
        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis
        return records.count { it.timestamp in start until end }
    }

    fun calculate(
        period: StatsPeriod,
        offset: Int,
        records: List<FocusSessionRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): StatsPeriodData {
        val safeOffset = offset.coerceAtMost(0)

        return when (period) {
            StatsPeriod.DAILY -> calculateDaily(safeOffset, records, nowMillis, timeZone, locale)
            StatsPeriod.WEEKLY -> calculateWeekly(safeOffset, records, nowMillis, timeZone, locale)
            StatsPeriod.MONTHLY -> calculateMonthly(safeOffset, records, nowMillis, timeZone, locale)
        }
    }

    private fun calculateDaily(
        offset: Int,
        records: List<FocusSessionRecord>,
        nowMillis: Long,
        timeZone: TimeZone,
        locale: Locale
    ): StatsPeriodData {
        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, offset)
        }

        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val dayRecords = records
            .filter { it.timestamp in startOfDay..endOfDay }
            .sortedBy { it.timestamp }

        val totalDurationMillis = dayRecords.sumOf { it.durationMillis }
        val completedCount = dayRecords.size

        val title = when (offset) {
            0 -> "Today, " + SimpleDateFormat("MMM d", locale).format(cal.time)
            -1 -> "Yesterday, " + SimpleDateFormat("MMM d", locale).format(cal.time)
            else -> SimpleDateFormat("EEE, MMM d, yyyy", locale).format(cal.time)
        }

        // Daily chart: 6 time blocks of 4 hours (0-4, 4-8, 8-12, 12-16, 16-20, 20-24)
        val blockLabels = listOf("12a", "4a", "8a", "12p", "4p", "8p")
        val chartItems = mutableListOf<ChartBarItem>()
        for (i in 0..5) {
            val blockStartCal = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, i * 4)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val blockEndCal = (cal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, (i * 4) + 3)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val blockRecords = dayRecords.filter { it.timestamp in blockStartCal.timeInMillis..blockEndCal.timeInMillis }
            val blockMinutes = blockRecords.sumOf { it.durationMillis } / 60000L
            chartItems.add(
                ChartBarItem(
                    label = blockLabels[i],
                    fullDateLabel = "${blockLabels[i]} – ${blockLabels.getOrElse(i + 1) { "12a" }}",
                    durationMinutes = blockMinutes,
                    sessionCount = blockRecords.size,
                    isHighlighted = false
                )
            )
        }

        return StatsPeriodData(
            period = StatsPeriod.DAILY,
            title = title,
            completedCount = completedCount,
            totalDurationMillis = totalDurationMillis,
            chartItems = chartItems,
            canGoForward = offset < 0,
            sessions = dayRecords
        )
    }

    private fun calculateWeekly(
        offset: Int,
        records: List<FocusSessionRecord>,
        nowMillis: Long,
        timeZone: TimeZone,
        locale: Locale
    ): StatsPeriodData {
        val nowCal = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
        val todayDow = nowCal.get(Calendar.DAY_OF_WEEK)
        val todayDaysSinceMonday = if (todayDow == Calendar.SUNDAY) 6 else todayDow - Calendar.MONDAY

        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, offset * 7)
        }

        // Align to Monday 00:00:00.000
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val mondayCal = cal.clone() as Calendar

        val dayFormat = SimpleDateFormat("EEE", locale)
        val fullDateFormat = SimpleDateFormat("EEE, MMM d", locale)
        val monthDayFormat = SimpleDateFormat("MMM d", locale)
        val yearFormat = SimpleDateFormat("yyyy", locale)

        val chartItems = mutableListOf<ChartBarItem>()
        var weekTotalDuration = 0L
        var weekTotalCount = 0

        for (dayIndex in 0..6) {
            val dayStartCal = (mondayCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, dayIndex)
            }
            val dayEndCal = (dayStartCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val dayRecords = records.filter { it.timestamp in dayStartCal.timeInMillis..dayEndCal.timeInMillis }
            val dayDurationMinutes = dayRecords.sumOf { it.durationMillis } / 60000L
            val dayCount = dayRecords.size

            weekTotalDuration += dayRecords.sumOf { it.durationMillis }
            weekTotalCount += dayCount

            chartItems.add(
                ChartBarItem(
                    label = dayFormat.format(dayStartCal.time),
                    fullDateLabel = fullDateFormat.format(dayStartCal.time),
                    durationMinutes = dayDurationMinutes,
                    sessionCount = dayCount,
                    isHighlighted = (offset == 0 && dayIndex == todayDaysSinceMonday)
                )
            )
        }

        val sundayCal = (mondayCal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 6) }
        val title = if (mondayCal.get(Calendar.YEAR) == sundayCal.get(Calendar.YEAR)) {
            "${monthDayFormat.format(mondayCal.time)} – ${monthDayFormat.format(sundayCal.time)}, ${yearFormat.format(sundayCal.time)}"
        } else {
            "${monthDayFormat.format(mondayCal.time)}, ${yearFormat.format(mondayCal.time)} – ${monthDayFormat.format(sundayCal.time)}, ${yearFormat.format(sundayCal.time)}"
        }

        return StatsPeriodData(
            period = StatsPeriod.WEEKLY,
            title = title,
            completedCount = weekTotalCount,
            totalDurationMillis = weekTotalDuration,
            chartItems = chartItems,
            canGoForward = offset < 0
        )
    }

    private fun calculateMonthly(
        offset: Int,
        records: List<FocusSessionRecord>,
        nowMillis: Long,
        timeZone: TimeZone,
        locale: Locale
    ): StatsPeriodData {
        val nowCal = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
        val currentMonthDay = nowCal.get(Calendar.DAY_OF_MONTH)

        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = nowMillis
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthFormat = SimpleDateFormat("MMMM yyyy", locale)
        val fullDateFormat = SimpleDateFormat("EEE, MMM d", locale)
        val title = monthFormat.format(cal.time)

        val chartItems = mutableListOf<ChartBarItem>()
        var monthTotalDuration = 0L
        var monthTotalCount = 0

        for (day in 1..maxDays) {
            val dayStartCal = (cal.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayEndCal = (dayStartCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val dayRecords = records.filter { it.timestamp in dayStartCal.timeInMillis..dayEndCal.timeInMillis }
            val dayDurationMinutes = dayRecords.sumOf { it.durationMillis } / 60000L
            val dayCount = dayRecords.size

            monthTotalDuration += dayRecords.sumOf { it.durationMillis }
            monthTotalCount += dayCount

            chartItems.add(
                ChartBarItem(
                    label = day.toString(),
                    fullDateLabel = fullDateFormat.format(dayStartCal.time),
                    durationMinutes = dayDurationMinutes,
                    sessionCount = dayCount,
                    isHighlighted = (offset == 0 && day == currentMonthDay)
                )
            )
        }

        return StatsPeriodData(
            period = StatsPeriod.MONTHLY,
            title = title,
            completedCount = monthTotalCount,
            totalDurationMillis = monthTotalDuration,
            chartItems = chartItems,
            canGoForward = offset < 0
        )
    }
}
