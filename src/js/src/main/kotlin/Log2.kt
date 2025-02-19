import kotlinx.browser.window
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import kotlinx.html.TagConsumer
import kotlinx.html.dom.append
import kotlinx.html.js.br
import kotlinx.html.js.div
import kotlinx.html.style
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent
import kotlin.math.atan
import kotlin.math.min
import kotlin.math.roundToInt

typealias ColorMap = Map<String, Array<Float>>

var resizeJob : Job? = null

// In place of a suspending constructor
suspend fun Log2(surface : HTMLCanvasElement, overlay : HTMLCanvasElement,
                 rules : Rules, data : ActivityList, progressReporter : (Int) -> Unit) : Log2 {
  val renderer = Renderer(surface)
  yield()
  val model = Log2(rules, data, renderer, progressReporter)
  // setDates must be called immediately to initialize lateinit properties. Ideally this would be done in the
  // constructor for Log2 but setDates is suspend so it has to be done here. This function is the public constructor
  // for Log2 anyway.
  model.setDates(data.startDate, data.endDate)
  yield()
  val camembertView = CamembertView(model, renderer, rules.colors)
  yield()
  val legendView = LegendView(model, overlay, el("currentGroup"))

  fun resize() {
    resizeJob?.cancel()
    resizeJob = mainScope.launchHandlingError {
      val (w, h) = resizeCanvas()
      renderer.sizeViewPort(w, h)
      camembertView.startAnimation(model.currentGroup)
      legendView.drawText(model.currentGroup)
    }
  }
  el("activityList").observeResize { _,_ -> resize() }
  window.onresize = { resize() }

  return model
}

class CamembertView(val model : Log2, private val renderer : Renderer, private val colorMap : ColorMap) {
  init {
    model.currentGroupProp.listen { old, new ->
      startAnimation(new)
    }
    registerMouseListeners()
  }

  private var hoveredGroup : Group? = null

  private var destGroup : Group = model.currentGroup
  fun startAnimation(g : Group) {
    destGroup = g
    @Suppress("UNUSED_PARAMETER") // It's a callback dude
    fun step(timestamp : Double) {
      if (!renderer.render(destGroup, colorMap)) {
        window.requestAnimationFrame { step(it) }
      }
    }
    step(0.0)
  }

  private fun registerMouseListeners() {
    val overlay = el("overlay")
    fun mouseMoveListener(it : MouseEvent) {
      val s = min(overlay.clientWidth, overlay.clientHeight)
      val x = (it.offsetX.toFloat() - overlay.clientWidth / 2) / s
      val y = (it.offsetY.toFloat() - overlay.clientHeight / 2) / s
      val lengthSquared = x * x + y * y
      if (lengthSquared > RADIUS * RADIUS) {
        hoveredGroup = null
        return
      }
      val group = model.currentGroup
      val angle = when {
        y <= 0 && x >= 0 -> atan(-y / x) / TAU
        y <= 0 && x <= 0 -> 0.5f - atan(y / x) / TAU
        y >= 0 && x <= 0 -> atan(-y / x) / TAU + 0.5f
        else -> 1f - atan(y / x) / TAU
      }
      var minute = angle * group.totalMinutes
      group.children.forEach {
        minute -= it.totalMinutes
        if (minute <= 0) {
          hoveredGroup = it
          return
        }
      }
    }

    overlay.addMouseMoveListener(::mouseMoveListener)
    overlay.addMouseClickListener {
      val target = hoveredGroup
      if (null == target)
        model.popStack()
      else {
        model.currentGroup = target
        mouseMoveListener(it)
      }
    }
  }

}

class BreadcrumbView(private val model : Log2,
                     private val breadcrumbs : Element, private val currentGroupHtml : Element) {
  init {
    model.currentGroupProp.listen { _, _ -> render() }
  }

  private fun render() {
    val top = model.stack.last()
    breadcrumbs.innerHTML = ""
    model.stack.forEach { group ->
      breadcrumbs.append {
        breadcrumb(group)
      }
    }
    currentGroupHtml.innerHTML = "${top.canon.name} (${top.totalMinutes.renderDuration()})"
  }

  private fun TagConsumer<HTMLElement>.breadcrumb(group : Group) {
    div(classes = "breadcrumb handCursor") {
      val color = group.color
      style = "background-color : rgb(${color.map{it*255}.joinToString(",")})"
      +group.canon.name
      br {}
      +"${group.totalMinutes.renderDuration()} (${(1440f * group.totalMinutes / (model.endDate - model.startDate)).ifNaNThen(0f).roundToInt().renderDuration()}/d)"
    }.addMouseClickListener {
      model.currentGroup = group
    }
  }
}

class Log2 internal constructor(
  private val rules : Rules, private val data : ActivityList, private val renderer : Renderer, private val progressReporter : (Int) -> Unit
) {
  val startDate : Timestamp
    get() = activities.startDate
  val endDate : Timestamp
    get() = activities.endDate

  private val groupStack = ArrayDeque<Group>()
  var currentGroup : Group = Group.TOP
    set(g) {
      if (g === field) return
      if (groupStack.contains(g)) {
        while (groupStack.last() != g) groupStack.removeLast()
      } else {
        groupStack.add(g)
      }
      currentGroupProp.changed(field, g)
      field = g
    }
  val currentGroupProp = Listenable(this::currentGroup)
  var stack : List<Group>
    get() = groupStack
    set(g) {
      groupStack.clear()
      groupStack.addAll(g)
      currentGroup = groupStack.last()
    }
  fun popStack() {
    if (groupStack.size <= 1) return
    groupStack.removeLast()
    currentGroup = groupStack.last()
  }

  var groupSet : GroupSet = GroupSet.EMPTY
    set(gs) {
      if (gs === field) return
      groupSetProp.changed(field, gs)
      field = gs
    }
  val groupSetProp = Listenable(this::groupSet)

  private lateinit var activities : ActivityList
  private var tree : GroupTree? = null
  suspend fun setDates(from : Timestamp, end : Timestamp) {
    val acts = data.view(from, end)
    yield()
    val gs = GroupSet(rules, acts, progressReporter)
    yield()
    val tr = GroupTree(this, el("activityList"), gs.top, rules.colors)
    yield()
    val sleepStats = acts.sleepStats
    el("sleepStats").innerHTML = "Zzz duration avg = ${sleepStats.duration.average.renderDuration()} σ = ${sleepStats.duration.deviation.renderDuration()}<br>Zzz time avg = ${sleepStats.time.average.renderDuration()} σ = ${sleepStats.time.deviation.renderDuration()}"
    yield()
    run<Unit> {
      activities = acts
      val oldTree = tree
      tree = tr
      oldTree?.delete()
    }
    yield()
    groupStack.clear()
    currentGroup = gs.top
    groupSet = gs
    yield()
  }

  suspend fun setPitch(pitch : Float) = renderer.setPitch(pitch)

  val size : Int get() = activities.size
  val first : Activity get() = activities.first
  val last : Activity get() = activities.last
  fun forEachActivity(f : (Activity) -> Unit) {
    activities.forEach(f)
  }
}
