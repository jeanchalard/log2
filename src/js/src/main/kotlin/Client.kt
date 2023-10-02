import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.asList
import org.w3c.dom.events.UIEvent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

external val rules : String
external val data : String
val mainScope = MainScope()
lateinit var log2 : Log2

class HTMLElementNotFound(msg : String) : Exception(msg)

class UncategorizedActivities(errors : List<UncategorizedActivity>) : Exception() {
  val suggestions = errors.map { "${it.activity.name.replace("+", "\\+")} = Repos" }.toSet().toList().sorted()
  val errors = errors.map { it.toString() }
}

val surface get() = el("surface") as HTMLCanvasElement
val startDateInput get() = el("startDate") as HTMLInputElement
val endDateInput get() = el("endDate") as HTMLInputElement

suspend fun parseData(data : String, progressReporter : (Int) -> Unit) : ActivityList {
  val lines = data.lines()
  val total = lines.size
  var currentLine = 0
  val activities = mutableListOf<Activity>()
  lines.forEach { line ->
    currentLine += 1
    val l = line.trim().replace(' ', ' ')
    if (l.isEmpty()) return@forEach
    activities.add(Activity.parse(l))
    progressReporter((100 * currentLine) / total)
    yield()
  }
  progressReporter((100 * currentLine) / total)
  return ActivityList(activities)
}

suspend fun resizeCanvas() : Pair<Int, Int> {
  val parent = el("content")
  val activityList = el("activityList")
  val siblings = parent.parentElement!!.children.asList()
  val remainingHeight = siblings.fold(parent.parentElement!!.clientHeight) {
      acc, e -> if (e.id == "content") acc else acc - e.clientHeight
  }
  activityList.styleString = "height:${remainingHeight}px;"
  yield()
  val remainingWidth = parent.clientWidth - activityList.clientWidth
  surface.setAttribute("width", "${remainingWidth}px")
  surface.setAttribute("height", "${remainingHeight}px")
  val sizeStyle = "width : ${remainingWidth}px; height : ${remainingHeight}px;"
  (surface as Element).styleString = sizeStyle
  el("camembert").styleString = sizeStyle
  el("overlay").styleString = sizeStyle
  el("currentGroup").styleString = "width : ${remainingWidth}px;"

  return remainingWidth to remainingHeight
}

suspend fun <T> handleError(block : suspend () -> T) = try {
  block()
} catch (e : Exception) {
  error(e)
} catch (e : dynamic) { // Catch JS exceptions generated outside of kotlin code
  error(e)
  throw e as Throwable
}
fun CoroutineScope.launchHandlingError(context: CoroutineContext = EmptyCoroutineContext,
                                       start: CoroutineStart = CoroutineStart.DEFAULT,
                                       block : suspend CoroutineScope.() -> Unit) = launch(context, start) {
  handleError { block() }
}

val DATE_PATTERN = Regex("(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d) (\\d\\d):(\\d\\d)")
fun tryUpdateDate() {
  fun timestamp(d : MatchResult) = makeTimestamp(d.groupValues[1].toInt(), d.groupValues[2].toInt(), d.groupValues[3].toInt(),
      d.groupValues[4].toInt(), d.groupValues[5].toInt())

  val startMatch = DATE_PATTERN.matchEntire(startDateInput.value) ?: return
  val endMatch = DATE_PATTERN.matchEntire(endDateInput.value) ?: return
  val start = timestamp(startMatch)
  val end = timestamp(endMatch)
  mainScope.launchHandlingError { log2.setDates(start, end) }
}

fun main() {
  window.onload = {
    val progressRules = el("progressRules").first
    val progressData = el("progressData").first
    val progressGroup = el("progressGroup").first
    mainScope.launchHandlingError {
      val rules = parseRules(rules) { progressRules.style.width = "${it}%" }
      val activities = parseData(data) { progressData.style.width = "${it}%"}
      log2 = Log2(surface, el("breadcrumbs"), el("currentGroup"), rules, activities) { progressGroup.style.width = "${it}%" }
      startDateInput.value = log2.startDate.toReadableString()
      endDateInput.value = log2.endDate.toReadableString()
      startDateInput.addOnInputListener { tryUpdateDate() }
      endDateInput.addOnInputListener { tryUpdateDate() }
      // To render the graph after the first layout pass, once the size is known
      window.requestAnimationFrame { window.setTimeout({ window.onresize?.invoke(UIEvent("resize")) }) }
      removeLoading()
      el("main").removeClass("hidden")
    }
  }
}

fun removeLoading() {
  elOrNull("loading")?.remove()
}

fun error(e : Any?) {
  removeLoading()
  val st = when (e) {
    is UncategorizedActivities -> e.suggestions + " " + e.errors
    is Exception -> listOf("Error : " + e::class.toString()) + e.toString().split("\n")
    is List<*> -> e.map { it.toString() }
    else -> listOf("Javascript exception, check console", e.toString())
  }
  el("main").addClass("hidden")
  el("banner").append {
    div(classes = "matchParent errorBox") {
      st.forEach {
        +it
        br
      }
    }
  }
}
