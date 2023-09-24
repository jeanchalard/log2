import kotlinx.browser.window
import kotlinx.coroutines.yield
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.atan
import kotlin.math.min

// In place of a suspending constructor
suspend fun Log2(surface : HTMLCanvasElement, rules : Rules, dataSet : DataSet) : Log2 {
  val renderer = Renderer(surface)
  yield()
  val tree = GroupTree(dataSet.top)
  yield()
  tree.render(el("activityList"))
  yield()

  val log2 = Log2(surface, rules, dataSet, renderer, tree)

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

class Log2 internal constructor(private val surface : HTMLCanvasElement, private val rules : Rules,
                               private val dataSet : DataSet, private val renderer : Renderer,
                               private val tree : GroupTree) {
  private val currentGroup = dataSet.top

  fun startAnimation() {
    @Suppress("UNUSED_PARAMETER") // It's a callback dude
    fun step(timestamp : Double) {
      if (!renderer.render(currentGroup, rules.colors)) {
        window.requestAnimationFrame { step(it) }
      }
    }
    step(0.0)
  }

  private var hoveredGroup : Group? = null
  private fun selectHoveredGroup(g : Group?) {
    if (hoveredGroup == g) return
    hoveredGroup = g
    console.log("HoveredGroup ${g?.canon?.name}")
  }

  internal fun registerMouseListeners() {
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
