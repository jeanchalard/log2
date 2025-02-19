import kotlin.math.max
import kotlin.math.min

val activityRegexp = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d):(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d)\\s*(.*)")
val emptyActivityRegexp = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d):(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d)\\s*$")

class IllegalDataFormatException(m : String) : Exception(m)
class SuspiciousDataException(m : String) : Exception(m)

fun MatchResult.i(i : Int) = this.groupValues[i].toInt()

// dayStart represents the start of the *symbolic* day for this activity, and is used only for the purpose of
// computing sleep stats. Normally it's just 00:00 on the date (and that's what #parse does), but this is set
// to the previous day by calling #tackToPreviousDay by the caller of #parse for those activities that should
// be tacked to the previous days, that is activities that happen before actually waking up for the day, i.e.
// activities that precede a ZZZ still early enough in the day that it's the end of the night.
class Activity(val name : String, val start : Timestamp, val end : Timestamp, val dayStart : Timestamp) {
  val duration : Minute get() = end - start
  // See the comment on the top of the class : dayOffset shares the characteristics of dayStart in that it
  // can only be used in relation to the symbolic day, for the purposes of computing sleep stats.
  val dayOffset : Minute get() = start - dayStart
  companion object {
    fun parse(s : String) : Activity {
      val match = activityRegexp.matchEntire(s) ?: throw IllegalDataFormatException("Malformed line : \"${s}\"")
      val name = match.groupValues[11]
      val start = makeTimestamp(match.i(1), match.i(2), match.i(3), match.i(4), match.i(5))
      val end = makeTimestamp(match.i(6), match.i(7), match.i(8), match.i(9), match.i(10))
      val dayStart = makeTimestamp(match.i(1), match.i(2), match.i(3), 0, 0)
      if (start + 2880 < end) throw SuspiciousDataException("Activity lasts more than 48h, are you sure ?")
      return Activity(name, start, end, dayStart)
    }
  }
  fun view(s : Timestamp, e : Timestamp) : Activity {
    if (s < start && e > end) return this
    return Activity(name, max(s, start), min(e, end), dayStart)
  }
  fun tackToPreviousDay() = Activity(name, start, end, dayStart - 1440)
}
