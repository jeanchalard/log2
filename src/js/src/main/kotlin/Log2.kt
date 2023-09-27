import kotlinx.browser.window
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.atan
import kotlin.math.min

// In place of a suspending constructor
suspend fun Log2(surface : HTMLCanvasElement, rules : Rules, data : ActivityList, progressReporter : (Int) -> Unit) : Log2 {
  val renderer = Renderer(surface)
  yield()
  val log2 = Log2(surface, renderer, rules, data, progressReporter)
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

class Log2 internal constructor(private val surface : HTMLCanvasElement, private val renderer : Renderer,
                                private val rules : Rules, private val data : ActivityList,
                                private val progressReporter : (Int) -> Unit) {
  val startDate : Timestamp
    get() = activities.startDate
  val endDate : Timestamp
    get() = activities.endDate

  private lateinit var currentGroup : Group

  private var hoveredGroup : Group? = null
    set(g) {
      if (field === g) return
      field = g
      console.log("HoveredGroup ${g?.canon?.name}")
    }

  private lateinit var activities : ActivityList
  private lateinit var groupSet : GroupSet
  private lateinit var tree : GroupTree
  suspend fun setDates(from : Timestamp, end : Timestamp) {
    val acts = data.view(from, end)
    yield()
    val gs = GroupSet(rules, acts, progressReporter)
    yield()
    val tr = GroupTree(gs.top)
    yield()
    run<Unit> {
      activities = acts
      groupSet = gs
      tree = tr
    }
    tree.render(el("activityList"))
    yield()
    currentGroup = gs.top
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

//    overlay.addMouseClickListener {
//      val target = hoveredGroup
//      if (null == hoveredGroup && currentGroup.pa)
//
//            hoveredGroup?.let { currentGroup = it }
//    }
  }
}
