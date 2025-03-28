import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.*
import org.w3c.dom.events.UIEvent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

external val rules : String
external val data : String
val mainScope = MainScope()
private lateinit var log2 : Log2
private lateinit var topView : TopView

class HTMLElementNotFound(msg : String) : Exception(msg)

class UncategorizedActivities(errors : List<UncategorizedActivity>) : Exception() {
  val suggestions = errors.map { "${it.activity.name.replace("+", "\\+").replace("(", "\\(").replace(")", "\\)")} = Repos" }.toSet().toList().sorted()
  val errors = errors.map { it.toString() }
}

val surface get() = el("surface") as HTMLCanvasElement
val overlay get() = el("overlay") as HTMLCanvasElement
val startDateInput get() = el("startDate") as HTMLInputElement
val endDateInput get() = el("endDate") as HTMLInputElement
val pitchInput get() = el("pitch") as HTMLInputElement

suspend fun parseData(data : String, progressReporter : (Int) -> Unit) : ActivityList {
  fun tackActivitiesToPreviousDay(activities : MutableList<Activity>) {
    check(activities.isNotEmpty()) { "Trying to re-tack activities with an empty list" }
    val temp = ArrayDeque<Activity>()
    val day = activities.last().dayStart
    while (activities.isNotEmpty() && activities.last().dayStart == day)
      temp.addFirst(activities.removeLast().tackToPreviousDay())
    activities.addAll(temp)
  }

  val lines = data.lines()
  val total = lines.size
  var currentLine = 0
  val activities = mutableListOf<Activity>()
  lines.forEach { line ->
    currentLine += 1
    val l = line.trim().replace(' ', ' ')
    if (l.isEmpty() || (l.length < 32 && l.matches(emptyActivityRegexp))) return@forEach
    val act = Activity.parse(l)
    activities.add(act)
    // Fix dayOffset if necessary, for computing sleep stats. See comments on the Activity class.
    if (act.name == ZZZ && (act.start - act.dayStart) < LAST_TIME_FOR_FINISHING_THE_NIGHT) {
      tackActivitiesToPreviousDay(activities)
    }
    progressReporter((100 * currentLine) / total)
    yield()
  }
  progressReporter((100 * currentLine) / total)
  return ActivityList(activities)
}

suspend fun resizeCanvas() : Pair<Int, Int> {
  val parent = el("content") as HTMLElement
  val rightPane = el("rightPane") as HTMLElement
  val siblings = parent.parentElement!!.children.asList().map { it as HTMLElement }
  val remainingHeight = siblings.fold((parent.parentElement!! as HTMLElement).offsetHeight) {
      acc, e -> if (e.id == "content") acc else acc - e.offsetHeight
  }
  val canvasHeight = remainingHeight - (el("currentGroup") as HTMLElement).offsetHeight
  rightPane.styleString = "height : ${remainingHeight}px;"
  yield()
  val remainingWidth = parent.clientWidth - rightPane.offsetWidth
  val sizeStyle = "width : ${remainingWidth}px; height : ${remainingHeight}px;"
  el("camembert").styleString = sizeStyle
  val canvasStyle = "width : ${remainingWidth}px; height : ${canvasHeight}px;"
  surface.setAttribute("width", "${remainingWidth}px")
  surface.setAttribute("height", "${canvasHeight}px")
  overlay.setAttribute("width", "${remainingWidth}px")
  overlay.setAttribute("height", "${canvasHeight}px")
  (surface as Element).styleString = canvasStyle
  (overlay as Element).styleString = canvasStyle

  return remainingWidth to remainingHeight
}

suspend fun <T> handleError(block : suspend () -> T) = try {
  block()
} catch (e : CancellationException) {
  // Cancelled, do nothing
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

fun updatePitch() {
  mainScope.launchHandlingError { log2.setPitch((pitchInput.valueAsNumber / 100).toFloat()) }
}

fun main() {
  window.onload = {
    val progressRules = el("progressRules").first
    val progressData = el("progressData").first
    val progressGroup = el("progressGroup").first
    mainScope.launchHandlingError {
      val rules = parseRules(rules) { progressRules.style.width = "${it}%" }
      val activities = parseData(data) { progressData.style.width = "${it}%"}
      log2 = Log2(surface, el("overlay") as HTMLCanvasElement, rules, activities) { progressGroup.style.width = "${it}%" }
      startDateInput.value = log2.startDate.toReadableString()
      endDateInput.value = log2.endDate.toReadableString()
      startDateInput.addOnInputListener { tryUpdateDate() }
      endDateInput.addOnInputListener { tryUpdateDate() }
      pitchInput.addOnInputListener { updatePitch() }

      val topView = TopView(log2, el("main"))

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
    is Exception -> listOf("Error : " + e::class.toString()) + e.toString().split("\n") + listOf(e.stackTraceToString())
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
