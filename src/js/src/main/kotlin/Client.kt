import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.asList
import org.w3c.dom.events.UIEvent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.atan
import kotlin.math.min

external val rules : String
external val data : String
val mainScope = MainScope()
lateinit var currentGroup : Group

class HTMLElementNotFound(msg : String) : Exception(msg)

class UncategorizedActivities(errors : List<UncategorizedActivity>) : Exception() {
  val suggestions = errors.map { "${it.activity.name.replace("+", "\\+")} = Repos" }.toSet().toList().sorted()
  val errors = errors.map { it.toString() }
}

val surface get() = el("surface") as HTMLCanvasElement

suspend fun parseData(rules : Rules, data : String, progressReporter : (Int) -> Unit) : DataSet {
  val dataSet = DataSet(rules)
  val uncategorizedActivities = mutableListOf<UncategorizedActivity>()
  val lines = data.lines()
  val total = lines.size
  var currentLine = 0
  lines.forEach {
    currentLine += 1
    val l = it.trim().replace(' ', ' ')
    if (l.isEmpty()) return@forEach
    val activity = Activity.parse(l)
    dataSet.categorize(activity)?.let { uncategorizedActivities.add(it) }
    progressReporter((100 * currentLine) / total)
    yield()
  }
  progressReporter((100 * currentLine) / total)
  if (uncategorizedActivities.isNotEmpty()) throw UncategorizedActivities(uncategorizedActivities)
  return dataSet
}

suspend fun resizeCanvas() : Pair<Int, Int> {
  val parent = el("content")
  val activityList = el("activityList")
  val siblings = parent.parentElement!!.children.asList()
  val remainingHeight = siblings.fold(parent.parentElement!!.clientHeight) {
      acc, e -> if (e.id == "content") acc else acc - e.clientHeight
  }
  activityList.style = "height:${remainingHeight}px;"
  yield()
  val remainingWidth = parent.clientWidth - activityList.clientWidth
  surface.setAttribute("width", "${remainingWidth}px")
  surface.setAttribute("height", "${remainingHeight}px")
  val sizeStyle = "width : ${remainingWidth}px; height : ${remainingHeight}px;"
  (surface as Element).style = sizeStyle
  el("camembert").style = sizeStyle
  el("overlay").style = sizeStyle

  return remainingWidth to remainingHeight
}

suspend fun <T> handleError(block : suspend () -> T) = try {
  block()
} catch (e : Exception) {
  error(e)
} catch (e : dynamic) { // Catch JS exceptions generated outside of kotlin code
  error(e)
  throw e
}
private fun CoroutineScope.launchHandlingError(context: CoroutineContext = EmptyCoroutineContext,
                                       start: CoroutineStart = CoroutineStart.DEFAULT,
                                       block : suspend CoroutineScope.() -> Unit) = launch(context, start) {
  handleError { block() }
}

private var hoveredGroup : Group? = null
private fun selectHoveredGroup(g : Group?) {
  if (hoveredGroup == g) return
  hoveredGroup = g
  console.log("HoveredGroup ${g?.canon?.name}")
}

fun main() {
  window.onload = {
    val progressRules = el("progressRules").first
    val progressData = el("progressData").first
    mainScope.launchHandlingError {
      val rules = parseRules(rules) { progressRules.style.width = "${it}%" }
      val dataSet = parseData(rules, data) { progressData.style.width = "${it}%"}
      dataSet.prune()
      currentGroup = dataSet.top
      removeLoading()
      el("main").removeClass("hidden")
      yield()
      val renderer = Renderer(surface)
      yield()
      val tree = GroupTree(dataSet.top)
      tree.render(el("activityList"))
      window.onresize = {
        mainScope.launchHandlingError {
          val (w, h) = resizeCanvas()
          renderer.sizeViewPort(w, h)
          renderer.render(currentGroup, rules.colors)
        }
      }
      // To render the graph after the first layout pass, once the size is known
      window.requestAnimationFrame { window.setTimeout({ window.onresize?.invoke(UIEvent("resize")) }) }
      val overlay = el("overlay")
      overlay.addMouseMoveListener {
        val s = min(overlay.clientWidth, overlay.clientHeight)
        val x = (it.offsetX.toFloat() - overlay.clientWidth / 2) / s
        val y = (it.offsetY.toFloat() - overlay.clientHeight / 2) / s
        val lengthSquared = x * x + y * y
        if (lengthSquared > RADIUS * RADIUS) {
          selectHoveredGroup(null)
          return@addMouseMoveListener
        }
        val group = currentGroup
        var angle = when {
          y <= 0 && x >= 0 -> atan(-y / x) / TAU
          y <= 0 && x <= 0 -> 0.5f - atan(y / x) / TAU
          y >= 0 && x <= 0 -> atan(-y / x) / TAU + 0.5f
          else -> 1f - atan(y / x) / TAU
        }
        var minute = angle * group.totalMinutes
        group.children.forEach {
          minute -= it.totalMinutes
          if (minute <= 0) {
            selectHoveredGroup(it)
            return@addMouseMoveListener
          }
        }
      }
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
