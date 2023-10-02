import kotlinx.browser.window
import kotlinx.coroutines.yield
import kotlinx.html.TagConsumer
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.atan
import kotlin.math.min
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlinx.html.style
import org.w3c.dom.HTMLElement

// In place of a suspending constructor
suspend fun Log2(surface : HTMLCanvasElement, breadcrumbs : Element, currentGroup : Element,
                 rules : Rules, data : ActivityList, progressReporter : (Int) -> Unit) : Log2 {
  val renderer = Renderer(surface)
  yield()
  val log2 = Log2(surface, breadcrumbs, currentGroup, renderer, rules, data, progressReporter)
  yield()
  log2.setDates(data.startDate, data.endDate)

  window.onresize = {
    mainScope.launchHandlingError {
      val (w, h) = resizeCanvas()
      renderer.sizeViewPort(w, h)
      log2.startAnimation()
    }
  }

  log2.registerMouseListeners()

  return log2
}

class Log2 internal constructor(private val surface : HTMLCanvasElement, private val breadcrumbs : Element, private val currentGroupHtml : Element,
                                private val renderer : Renderer,
                                private val rules : Rules, private val data : ActivityList,
                                private val progressReporter : (Int) -> Unit) {
  val startDate : Timestamp
    get() = activities.startDate
  val endDate : Timestamp
    get() = activities.endDate

  private val groupStack = ArrayDeque<Group>()
  private var currentGroup : Group = Group(Category.TOP)
    set(g) {
      if (g === field) return
      field = g
      startAnimation()
    }

  private var hoveredGroup : Group? = null

  private lateinit var activities : ActivityList
  private lateinit var groupSet : GroupSet
  private lateinit var tree : GroupTree
  suspend fun setDates(from : Timestamp, end : Timestamp) {
    val acts = data.view(from, end)
    yield()
    val gs = GroupSet(rules, acts, progressReporter)
    yield()
    val tr = GroupTree(gs.top, rules.colors)
    yield()
    run<Unit> {
      activities = acts
      groupSet = gs
      tree = tr
    }
    tree.render(el("activityList"))
    yield()
    currentGroup = gs.top
    yield()
    renderBreadcrumbs(groupStack)
    yield()
    startAnimation()
  }

  fun startAnimation() {
    @Suppress("UNUSED_PARAMETER") // It's a callback dude
    fun step(timestamp : Double) {
      if (!renderer.render(currentGroup, rules.colors)) {
        window.requestAnimationFrame { step(it) }
      }
    }
    step(0.0)
  }

  private fun TagConsumer<HTMLElement>.breadcrumb(group : Group) {
    div(classes = "breadcrumb") {
      val color = rules.colors[group.canon.name] ?: arrayOf(1f, 1f, 1f)
      style = "background-color : rgb(${color.map{it*255}.joinToString(",")})"
      +group.canon.name
    }
  }

  private fun renderBreadcrumbs(stack : ArrayDeque<Group>) {
    breadcrumbs.innerHTML = ""
    stack.forEach { group ->
      breadcrumbs.append {
        breadcrumb(group)
      }
    }
    breadcrumbs.append { breadcrumb(currentGroup) }
    currentGroupHtml.innerHTML = "${currentGroup.canon.name} (${currentGroup.totalMinutes.renderDuration()})"
  }

  internal fun registerMouseListeners() {
    val overlay = el("overlay")
    overlay.addMouseMoveListener {
      val s = min(overlay.clientWidth, overlay.clientHeight)
      val x = (it.offsetX.toFloat() - overlay.clientWidth / 2) / s
      val y = (it.offsetY.toFloat() - overlay.clientHeight / 2) / s
      val lengthSquared = x * x + y * y
      if (lengthSquared > RADIUS * RADIUS) {
        hoveredGroup = null
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
          hoveredGroup = it
          return@addMouseMoveListener
        }
      }
    }

    overlay.addMouseClickListener {
      val target = hoveredGroup
      if (null != target) {
        groupStack.add(currentGroup)
        currentGroup = target
      } else if (groupStack.size > 0) {
        currentGroup = groupStack.removeLast()
      }
      renderBreadcrumbs(groupStack)
    }
  }
}
