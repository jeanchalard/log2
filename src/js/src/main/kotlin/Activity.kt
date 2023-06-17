private val activityRegexp = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d):(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d):(\\d\\d)(\\d\\d)\\s*(.*)")

class IllegalDataFormatException(m : String) : Exception(m)

fun MatchResult.i(i : Int) = this.groupValues[i].toInt()

class Activity(val name : String, val start : Timestamp, val end : Timestamp) {
  val duration : Minute get() = end - start
  companion object {
    fun parse(s : String) : Activity {
      val match = activityRegexp.matchEntire(s) ?: throw IllegalDataFormatException("Malformed line \"${s}\"")
      val name = match.groupValues[11]
      val start = makeTimestamp(match.i(1), match.i(2), match.i(3), match.i(4), match.i(5))
      val end = makeTimestamp(match.i(6), match.i(7), match.i(8), match.i(9), match.i(10))
      return Activity(name, start, end)
    }
  }
}
