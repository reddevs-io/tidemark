// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.format

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.DateInterval
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.TimeZone
import android.icu.util.ULocale
import android.provider.Settings
import io.reddevs.tidemark.R
import io.reddevs.tidemark.data.model.ChartRange
import io.reddevs.tidemark.data.model.ChartWindow
import io.reddevs.tidemark.data.model.Metric
import io.reddevs.tidemark.settings.UnitPreference
import java.text.FieldPosition
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * [MeasurementFormatter] built on ICU ([MeasureFormat], [NumberFormat], skeleton date formats) and
 * java.time. Units follow [resolveUnitSystem]. Energy is always kilocalories.
 *
 * [steps] returns the grouped number alone ("12,345", "12 345"); callers add their own label.
 * Durations read "7 h 32 min", rounded to the nearest minute.
 *
 * Instances are immutable apart from ICU format caches, which are guarded by a lock, so one
 * instance can be shared across threads. AppContainer builds a new one whenever the locale or the
 * unit preference changes.
 *
 * @param clock gives "today" for [rangeLabel] and the zone for [time] and [dateTime]. When null,
 *   the system clock and zone are read on every call, so a time-zone change shows up without a new
 *   instance.
 */
class LocaleMeasurementFormatter(
    context: Context,
    override val locale: Locale,
    unitPreference: UnitPreference,
    private val clock: Clock? = null,
) : MeasurementFormatter {

    /** The resolved unit system, exposed so settings can describe what "follow region" means. */
    val unitSystem: UnitSystem = resolveUnitSystem(locale, unitPreference)

    private val appContext: Context = context.applicationContext ?: context

    // Resources in this formatter's locale. AppContainer always passes the device's current locale,
    // so Play's language splits already hold them; the configuration context only matters when a
    // caller (a test) picks another locale. Not inside Configuration.apply: there, "locale" would
    // mean Configuration's own deprecated field.
    @SuppressLint("AppBundleLocaleChanges")
    private val resources = run {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.createConfigurationContext(configuration).resources
    }

    private val uLocale = ULocale.forLocale(locale)
    private val lock = Any()

    private val integerFormat = NumberFormat.getIntegerInstance(uLocale)
    private val measureFormats = mutableMapOf<Int, MeasureFormat>()

    private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
    private val mediumDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    private val longDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    private val dayMonth = skeletonFormat(SKELETON_DAY_MONTH)
    private val weekdayDayMonth = skeletonFormat(SKELETON_WEEKDAY_DAY_MONTH)
    private val weekdayDayMonthYear = skeletonFormat(SKELETON_WEEKDAY_DAY_MONTH_YEAR)
    private val monthYear = skeletonFormat(SKELETON_MONTH_YEAR)
    private val year = skeletonFormat(SKELETON_YEAR)
    private val weekInterval = DateIntervalFormat.getInstance(SKELETON_DAY_MONTH_YEAR, uLocale).apply {
        timeZone = TimeZone.GMT_ZONE
    }
    private val firstDayOfWeek: DayOfWeek = WeekFields.of(locale).firstDayOfWeek

    // ICU formats rather than java.time patterns: ICU skeletons can yield letters (such as B for
    // day periods) that java.time on API 28 can't parse. Keyed by hour cycle: j, h or H.
    private val timeFormats = mutableMapOf<Char, DateFormat>()
    private val dateTimeFormats = mutableMapOf<Char, DateFormat>()

    override fun steps(count: Long): String = synchronized(lock) { integerFormat.format(count) }

    override fun distance(meters: Double): String {
        val value = toDisplayDistance(meters)
        val digits = when {
            abs(value) < 1 -> 2
            abs(value) < 100 -> 1
            else -> 0
        }
        val unit = when (unitSystem.distance) {
            DistanceUnit.KILOMETER -> MeasureUnit.KILOMETER
            DistanceUnit.MILE -> MeasureUnit.MILE
        }
        return measure(value, unit, digits)
    }

    override fun energy(kilocalories: Double): String = measure(kilocalories, MeasureUnit.KILOCALORIE, 0)

    override fun mass(kilograms: Double): String {
        val unit = when (unitSystem.mass) {
            MassUnit.KILOGRAM -> MeasureUnit.KILOGRAM
            MassUnit.POUND -> MeasureUnit.POUND
        }
        return measure(toDisplayMass(kilograms), unit, 1)
    }

    override fun heartRate(bpm: Double): String =
        resources.getString(R.string.format_heart_rate, steps(bpm.roundToLong()))

    override fun duration(duration: Duration): String {
        val totalMinutes = ((duration.seconds.coerceAtLeast(0) + 30) / 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L -> resources.getString(R.string.format_duration_minutes, steps(minutes))
            minutes == 0L -> resources.getString(R.string.format_duration_hours, steps(hours))
            else -> resources.getString(R.string.format_duration_hours_minutes, steps(hours), steps(minutes))
        }
    }

    override fun metricValue(metric: Metric, value: Double): String = when (metric) {
        Metric.STEPS -> steps(value.roundToLong())
        Metric.ACTIVE_CALORIES -> energy(value)
        Metric.DISTANCE -> distance(value)
        Metric.SLEEP -> duration(Duration.ofSeconds(value.roundToLong()))
        Metric.RESTING_HEART_RATE -> heartRate(value)
        Metric.WEIGHT -> mass(value)
    }

    override fun date(date: LocalDate, style: DateStyle): String = when (style) {
        DateStyle.SHORT -> shortDate.format(date)
        DateStyle.MEDIUM -> mediumDate.format(date)
        DateStyle.LONG -> longDate.format(date)
        DateStyle.DAY_MONTH -> formatSkeleton(dayMonth, date)
    }

    /** Follows the device's 12 or 24-hour setting when the user set one, else the locale's convention. */
    override fun time(instant: Instant): String = formatInstant(timeFormats, "", instant)

    override fun dateTime(instant: Instant): String = formatInstant(dateTimeFormats, SKELETON_DAY_MONTH_YEAR, instant)

    override fun rangeLabel(window: ChartWindow): String {
        val anchor = window.anchor
        return when (window.range) {
            ChartRange.DAY -> {
                val today = LocalDate.now(currentClock())
                when (anchor) {
                    today -> resources.getString(R.string.range_today)
                    today.minusDays(1) -> resources.getString(R.string.range_yesterday)
                    else -> formatSkeleton(if (anchor.year == today.year) weekdayDayMonth else weekdayDayMonthYear, anchor)
                }
            }
            ChartRange.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                val end = start.plusDays(6)
                synchronized(lock) {
                    weekInterval.format(DateInterval(noonUtcMillis(start), noonUtcMillis(end)), StringBuffer(), FieldPosition(0))
                        .toString()
                }
            }
            ChartRange.MONTH -> formatSkeleton(monthYear, anchor)
            ChartRange.YEAR -> formatSkeleton(year, anchor)
        }
    }

    override fun distanceUnitLabel(): String = resources.getString(
        when (unitSystem.distance) {
            DistanceUnit.KILOMETER -> R.string.unit_kilometer
            DistanceUnit.MILE -> R.string.unit_mile
        },
    )

    override fun massUnitLabel(): String = resources.getString(
        when (unitSystem.mass) {
            MassUnit.KILOGRAM -> R.string.unit_kilogram
            MassUnit.POUND -> R.string.unit_pound
        },
    )

    override fun toDisplayDistance(meters: Double): Double = meters / unitSystem.distance.metersPerUnit

    override fun toDisplayMass(kilograms: Double): Double = kilograms / unitSystem.mass.kilogramsPerUnit

    private fun measure(value: Double, unit: MeasureUnit, maxFractionDigits: Int): String = synchronized(lock) {
        val format = measureFormats.getOrPut(maxFractionDigits) {
            val number = NumberFormat.getInstance(uLocale).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = maxFractionDigits
            }
            MeasureFormat.getInstance(uLocale, MeasureFormat.FormatWidth.SHORT, number)
        }
        format.format(Measure(value, unit))
    }

    private fun skeletonFormat(skeleton: String): DateFormat =
        DateFormat.getInstanceForSkeleton(skeleton, uLocale).apply { timeZone = TimeZone.GMT_ZONE }

    private fun formatSkeleton(format: DateFormat, date: LocalDate): String =
        synchronized(lock) { format.format(Date(noonUtcMillis(date))) }

    private fun formatInstant(cache: MutableMap<Char, DateFormat>, dateSkeleton: String, instant: Instant): String {
        val hourCycle = hourCycle()
        return synchronized(lock) {
            val format = cache.getOrPut(hourCycle) { DateFormat.getInstanceForSkeleton("$dateSkeleton${hourCycle}m", uLocale) }
            format.timeZone = icuTimeZone(currentClock().zone)
            format.format(Date(instant.toEpochMilli()))
        }
    }

    /**
     * 'H' or 'h' when the user picked 24 or 12-hour time in system settings, otherwise 'j' (the
     * locale's own preference). Not DateFormat.is24HourFormat: its fallback uses the context's locale,
     * which isn't always this formatter's.
     */
    private fun hourCycle(): Char {
        val setting = runCatching {
            Settings.System.getString(appContext.contentResolver, Settings.System.TIME_12_24)
        }.getOrNull()
        return when (setting) {
            "24" -> 'H'
            "12" -> 'h'
            else -> 'j'
        }
    }

    private fun currentClock(): Clock = clock ?: Clock.systemDefaultZone()

    private companion object {
        const val SKELETON_DAY_MONTH = "MMMd"
        const val SKELETON_DAY_MONTH_YEAR = "yMMMd"
        const val SKELETON_WEEKDAY_DAY_MONTH = "MMMEd"
        const val SKELETON_WEEKDAY_DAY_MONTH_YEAR = "yMMMEd"
        const val SKELETON_MONTH_YEAR = "yMMMM"
        const val SKELETON_YEAR = "y"
        const val MILLIS_PER_DAY = 86_400_000L

        /** ICU doesn't know bare offset IDs such as "+02:00" or "Z", only "GMT+02:00". */
        fun icuTimeZone(zone: ZoneId): TimeZone = when (val normalized = zone.normalized()) {
            is ZoneOffset -> TimeZone.getTimeZone("GMT" + if (normalized == ZoneOffset.UTC) "" else normalized.id)
            else -> TimeZone.getTimeZone(normalized.id)
        }

        /** Midday UTC of [date], so formatting in [TimeZone.GMT_ZONE] can never slip a day. */
        fun noonUtcMillis(date: LocalDate): Long = date.toEpochDay() * MILLIS_PER_DAY + MILLIS_PER_DAY / 2
    }
}
