import kotlin.js.Date
import kotlin.math.abs

fun Any?.isNull() = null === this
fun Any?.notNull() = null !== this

// Javascript doesn't even have String.format and Kotlin doesn't provide it ?!
fun Int.dg() = if (this in 0..9) "0${this}" else this.toString()

fun Collection<Int>.sum() = fold(0) { acc, v -> acc + v }
fun Collection<Long>.sum() = fold(0L) { acc, v -> acc + v }
fun Collection<Float>.sum() = fold(0f) { acc, v -> acc + v }
fun <T> Collection<T>.sumBy(summer : (T) -> Int) = fold(0) { acc, v -> acc + summer(v) }

// Minutes since 1970-01-01 00:00 in the local timezone.
typealias Timestamp = Int
typealias Minute = Int

fun Timestamp.toMinute() = (this % 1440)
fun Timestamp.toReadableString() : String {
  val d = Date(this.toLong() * 60_000)
  return "${d.getFullYear()}-${(d.getMonth()+1).dg()}-${d.getDate().dg()} ${d.getHours().dg()}:${d.getMinutes().dg()}"
}
fun makeTimestamp(year : Int, month : Int, day : Int, hour : Int, minute : Int) : Timestamp {
  val d = Date(year, month - 1, day, hour, minute)
  return (d.getTime().toLong() / 60_000).toInt()
}

private operator fun Int.not() = when {
  this < 0 -> "-0${abs(this)}"
  this < 10 -> "0${this}"
  else -> toString()
}
fun Minute.renderDuration() : String = "${!(this / 60)}:${!(this % 60)}"

operator fun Float.not() = toString().let {
  val dotIndex = it.indexOf('.')
  when {
    dotIndex <= it.length + 4 -> it
    else -> it.substring(0..(dotIndex + 3))
  }
}

fun Float.renderPercent() = "${toInt()}.${(10 * this).toInt() % 10}%"
