import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.MouseEvent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

external val rules : String
external val data : String
val mainScope = MainScope()

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
fun CoroutineScope.launchHandlingError(context: CoroutineContext = EmptyCoroutineContext,
                                       start: CoroutineStart = CoroutineStart.DEFAULT,
                                       block : suspend CoroutineScope.() -> Unit) = launch(context, start) {
  handleError { block() }
}

fun Element.addMouseMoveListener(handler : (MouseEvent) -> Unit) = addEventListener("mousemove", { handler(it as MouseEvent) })

fun main() {
  window.onload = {
    val progressRules = el("progressRules").first
    val progressData = el("progressData").first
    mainScope.launchHandlingError {
      val rules = parseRules(rules) { progressRules.style.width = "${it}%" }
      val dataSet = parseData(rules, data) { progressData.style.width = "${it}%"}
      dataSet.prune()
      removeLoading()
      el("main").removeClass("hidden")
      yield()
      resizeCanvas()
      val renderer = Renderer(surface)
      yield()
      val tree = GroupTree(dataSet.top)
      tree.render(el("activityList"))
      renderer.render(dataSet.top, rules.colors)
      window.onresize = {
        mainScope.launchHandlingError {
          val (w, h) = resizeCanvas()
          renderer.sizeViewPort(w, h)
          renderer.render(dataSet.top, rules.colors)
        }
      }
      el("overlay").addMouseMoveListener{
        console.log(it)
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
