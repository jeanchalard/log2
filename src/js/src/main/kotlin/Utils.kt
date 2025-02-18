import kotlin.js.Date
import kotlin.math.round

const val TZ_OFFSET = 60 * 9 // Japan time
const val ZZZ = "Zzz"
const val SIESTE = "Sieste"

fun Any?.isNull() = null === this
fun Any?.notNull() = null !== this

fun <T : Number> T.ifZeroThen(other : T) = if (this == 0) other else this
fun <T : Float> T.ifNaNThen(other : T) = if (this.isNaN()) other else this
fun <T : Double> T.ifNaNThen(other : T) = if (this.isNaN()) other else this

// Javascript doesn't even have String.format and Kotlin doesn't provide it ?!
fun Int.dg() = if (this in 0..9) "0${this}" else this.toString()
fun Int.toHexString(digits : Int) : String {
  val s = StringBuilder()
  var i = this
  while (i > 0) {
    val q = i % 16
    s.insert(0, if (q >= 10) 'A' + q - 10 else '0' + q)
    i /= 16
  }
  while (s.length < digits) {
    s.insert(0, '0')
  }
  return s.toString()
}

fun Collection<Int>.sum() = fold(0) { acc, v -> acc + v }
fun Collection<Long>.sum() = fold(0L) { acc, v -> acc + v }
fun Collection<Float>.sum() = fold(0f) { acc, v -> acc + v }
fun <T> Collection<T>.sumBy(summer : (T) -> Int) = fold(0) { acc, v -> acc + summer(v) }

operator fun String.times(i : Int) = repeat(i)

data class Point<T : Number>(val x : T, val y : T)

// Minutes since 1970-01-01 00:00 in the local timezone.
typealias Timestamp = Int
typealias Minute = Int

fun Timestamp.toMinute() = (this % 1440)
fun Timestamp.toReadableString() : String {
  val d = Date((this.toLong() - TZ_OFFSET) * 60_000)
  return "${d.getFullYear()}-${(d.getMonth()+1).dg()}-${d.getDate().dg()} ${d.getHours().dg()}:${d.getMinutes().dg()}"
}
fun Timestamp.toReadableTimeWithoutDate() : String {
  val d = Date((this.toLong() - TZ_OFFSET) * 60_000)
  return "${d.getHours().dg()}:${d.getMinutes().dg()}"
}
// Ad-hoc function returning the day of month (01~31) and the weekday (0~6, where 0 is Monday)
fun Timestamp.getDayAndWeekday() : Pair<Int, Int> {
  val d = Date(this.toLong() * 60_000)
  return d.getDate() to ((d.getDay() + 6) % 7) // Javascript Date API is American-centric, fix this
}
class InvalidDateException(m : String) : Exception(m)
fun makeTimestamp(year : Int, month : Int, day : Int, hour : Int, minute : Int) : Timestamp {
  if (month !in 1..12 || day !in 1..31 || hour !in -23..48 || minute !in -59..119)
    throw InvalidDateException("Date is invalid even by my lax standards ${year}-${month.dg()}-${day.dg()} ${hour.dg()}:${minute.dg()}")
  val d = Date(year, month - 1, day, hour, minute)
  return (d.getTime().toLong() / 60_000).toInt() + TZ_OFFSET
}

fun Minute.renderDuration() : String = "${(this / 60).dg()}:${(this % 60).dg()}"

operator fun Float.not() = toString().let {
  val dotIndex = it.indexOf('.')
  when {
    dotIndex <= it.length + 4 -> it
    else -> it.substring(0..(dotIndex + 3))
  }
}

fun Float.renderPercent() = "${toInt()}.${(10 * this).toInt() % 10}%"
fun Float.renderByte() : String = round(this * 255).toInt().toHexString(2)
// If the array is not exactly 3 floats 0.0~1.0 this will return a nonsensical result.
fun Array<Float>.toColorString() : String = "#${this[0].renderByte()}${this[1].renderByte()}${this[2].renderByte()}"

// Completely useless since it prints a minified JS stacktrace :(
fun printStackTrace() {
  try {
    throw RuntimeException()
  } catch (e : RuntimeException) {
    e.printStackTrace()
  }
}
