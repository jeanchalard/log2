import kotlinx.coroutines.yield
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Sleep starting before this time is a nap. Sleep starting at or after this time is that day's sleep.
const val NIGHT_START : Minute = 19 * 60
// 11h is the last you can fall back asleep and still have this ZZZ considered the end of the night
const val LAST_TIME_FOR_FINISHING_THE_NIGHT : Minute = 11 * 60

data class Stats(val average : Int, val deviation : Int) // avg and dev are in minutes
data class SleepStats(val duration : Stats, val time : Stats)

class ActivityList private constructor(private val list : ArrayList<Activity>, private val start : Timestamp?, private val end : Timestamp?) {
  constructor(l : List<Activity>) : this(ArrayList(l), null, null)

  val first get() = list[firstIndex]
  val last get() = list[lastIndex]

  val startDate : Timestamp = start ?: list.first().start
  val endDate = when {
    end == null -> list.last().end
    end < startDate -> startDate
    else -> end
  }

  private val firstIndex get() = if (null == start) 0 else {
    val s = startDate
    val insertionPoint = list.binarySearch { it.end.compareTo(s) }
    if (insertionPoint > 0) insertionPoint else -insertionPoint - 1
  }
  private val lastIndex get() = if (null == end) list.lastIndex else {
    val e = endDate
    val insertionPoint = list.binarySearch { it.start.compareTo(e) }
    if (insertionPoint > 0) insertionPoint else -insertionPoint - 2
  }

  val size get() = lastIndex - firstIndex
  fun forEach(what : (Activity) -> Unit) {
    (firstIndex..lastIndex).forEach {
      what(list[it].view(startDate, endDate))
    }
  }
  suspend fun forEachSuspending(what : (Activity) -> Unit) {
    (firstIndex..lastIndex).forEach {
      what(list[it].view(startDate, endDate))
      yield()
    }
  }

  private fun List<Int>.computeStats() : Stats {
    val average = this.average().ifNaNThen(0.0)
    val variance = this.map { (it - average) * (it - average) }.average().ifNaNThen(0.0)
    return Stats(average.roundToInt(), sqrt(variance).roundToInt())
  }

  val sleepStats : SleepStats get() {
    // Only the activities for the considered period. Note that as opposed to most other places, this does not
    // clamp activities sliced by the start or end time (e.g. Zzz from 22:30 to 06:00 with a start time at 00:00 that
    // day), because this would mess with the stats in a way that does not make statistical sense for the averages
    // and deviations (in the example above, it would count a six-hour night starting at midnight).
    val sleeps = list.subList(firstIndex, lastIndex)
     // Only sleep periods.
     .filter { it.name == ZZZ || it.name == SIESTE }
     // Group by dayStart to average by day. This means naps are counted toward the next night for the purposes
     // of the averages. Also see the comment above class Activity for how sleeping 2 hours in the morning after
     // a short wake-up period is tacked to the previous day so that it is part of the sleep for that previous day,
     // and not counted toward the sleep of that evening.
     .groupBy { it.dayStart }
     // Remove days with only naps. This can happen on the last day of the data, where time for the end of ZZZ is
     // not known and therefore the data is not present. If left in the data, this will skew the stats. Drawback being
     // that this will remove any day with a nap but an all-nighter, see below.
     .filter { day -> day.value.any { it.name == ZZZ } }
     // Now the data is grouped as wanted, the keys are no longer needed. Note that this disallows checking for
     // continuity so a day with an all-nighter will not be counted at all, so 8h sleep on day 1, zero sleep on
     // day 2 and 6h sleep on day 3 is an average of 7h, because day 2 is discounted and doesn't have a list at
     // all.
     .values
    // All zzz for length avg and deviation
    val lengths = sleeps.map { dayList -> dayList.sumOf { it.duration } }
    // Only nights for sleep hour avg and deviation
    val nights = sleeps.mapNotNull { dayList -> dayList.firstOrNull { it.dayOffset >= NIGHT_START }?.dayOffset }
    return SleepStats(duration = lengths.computeStats(), time = nights.computeStats())
  }

  fun view(start : Timestamp, end : Timestamp) = ActivityList(list, start, end)
}
