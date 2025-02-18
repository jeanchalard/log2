import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.*

// The w/h ratio to aim for when trying to find the best way to break a label into multiple lines
private const val IDEAL_RATIO = 4.5

private data class LabelMetrics(val s : String, val lineCount : Int, val w : Float, val h : Float) {
  val ratio get() = w / h
  val deviationFromIdealRatio = abs(IDEAL_RATIO - ratio)
}

private inline fun CanvasRenderingContext2D.fix() {
  font = "${surface.clientWidth / 50}px Noto Sans"
  fillStyle = "rgb(255, 255, 255)"
  strokeStyle = "rgb(0, 0, 0)"
  lineWidth = 3.0
}

class LegendView(model : Log2, private val surface : HTMLCanvasElement, private val bottomLegend : Element) {
  private val surfaceContext = surface.getContext("2d") as CanvasRenderingContext2D
  init {
    labelGeneration += 1
    model.currentGroupProp.listen { _, new ->
      //surface.alpha = 0f
      if (surface.clientWidth > 0)
        mainScope.launchHandlingError { drawText(new) }
    }
  }

  companion object {
    private var labelGeneration = 0

    private var ID = 0
  }

  private inner class Label(val metrics : LabelMetrics,
                      centerX : Float, centerY : Float,
                      val gravX : Float, val gravY : Float) {
    val w get() = metrics.w
    val h get() = metrics.h
    var x = centerX
      set(v) {
        field = if (v < w / 2) w / 2
        else if (v + w / 2 > surface.clientWidth) surface.clientWidth - w / 2
        else v
      }
    var y = centerY
      set(v) {
        field = if (v < h / 2) h / 2
        else if (v + h / 2 > surface.clientHeight - bottomLegend.clientHeight) surface.clientHeight - bottomLegend.clientHeight - h / 2
        else v
      }
    inline val left get() = x - w / 2
    inline val right get() = x + h / 2
    inline val top get() = y - h / 2
    inline val bottom get() = y + h / 2
  }

  private fun findBestLabelMetrics(s : String) : LabelMetrics {
    fun String.getMetrics() : LabelMetrics {
      var w = 0.0
      var h = 0.0
      val lines = split("\n")
      lines.forEach {
        val m = surfaceContext.measureText(it)
        val wd = m.actualBoundingBoxLeft + m.actualBoundingBoxRight
        val ht = m.actualBoundingBoxAscent + m.actualBoundingBoxDescent
        if (wd > w) w = wd
        h += ht
      }
      return LabelMetrics(this, lines.size, w.toFloat(), h.toFloat())
    }

    val q = s.toCharArray()
    val spaces = q.foldIndexed(ArrayList<Int>()) { i, list, c -> if (c == ' ') list.also{it.add(i)} else list }
    val strings = sequenceOf(s) + generateSequence {
      for (it in spaces) {
        if (q[it] == ' ') {
          q[it] = '\n'
          return@generateSequence q.concatToString()
        } else {
          q[it] = ' '
        }
      }
      return@generateSequence null
    }

    val metrics = strings.map { it.getMetrics() }.toList()
    val bestRatio = metrics.minBy { it.deviationFromIdealRatio }
    val best = metrics.filter { it.lineCount == bestRatio.lineCount }.minBy { it.w }

    return best
  }

  private fun getLabels(g : Group) : ArrayList<Label> {
    surfaceContext.fix()
    var startAngle = 0f
    val centerX = surface.clientWidth / 2
    val centerY = surface.clientHeight / 2
    val result = ArrayList<Label>()
    g.children.forEach {
      val angle = it.totalMinutes.toFloat() / g.totalMinutes
      val centerAngle = startAngle + angle / 2
      startAngle += angle
      val base = min(surface.clientWidth, surface.clientHeight)
      val c = cos(2 * PI * centerAngle).toFloat() * RADIUS * base
      val s = -sin(2 * PI * centerAngle).toFloat() * RADIUS * base
      val label = findBestLabelMetrics(it.canon.name)
      result.add(Label(label, centerX + c, centerY + s, centerX + c, centerY + s))
    }
    return result
  }

  private fun overlap(l1 : Label, l2 : Label) : Boolean {
    if (l1.right < l2.left) return false
    if (l2.right < l1.left) return false
    if (l1.bottom < l2.top) return false
    if (l2.bottom < l1.top) return false
    return true
  }

  // Counterintuitively, mass can be negative to repulse, it's fine
  private fun gravitate(it : Label, centerX : Float, centerY : Float, mass : Float) {
    val distX = centerX - it.x
    val distY = centerY - it.y
    val distanceSq = distX * distX + distY * distY
    if (distanceSq <= 0.0001f) return
    val strength = (mass / distanceSq).coerceIn(-1f, 1f)
    it.x += distX * strength
    it.y += distY * strength
  }

  // Canvas doesn't even know to render multi-line strings so write it manually
  private fun CanvasRenderingContext2D.renderText(l : Label) {
    // y is increasing downward, but fillText renders from (x,y) up...
    var y = l.y.toDouble() - l.metrics.h.toDouble() / 2
    l.metrics.s.split("\n").forEach { s ->
      val m = surfaceContext.measureText(s)
      // Center this line in x
      val x = l.x - (m.actualBoundingBoxLeft + m.actualBoundingBoxRight) / 2
      y += m.actualBoundingBoxAscent + m.actualBoundingBoxDescent
      strokeText(s, x, y)
      fillText(s, x, y)
    }
  }

  private suspend fun positionLabels(labels : ArrayList<Label>) {
    val gen = labelGeneration
    (0..1000).forEach { i ->
      if (gen != labelGeneration) return
      val positions = labels.map { Point(it.x, it.y) }
      positionLabels(i, labels)
      surfaceContext.clearRect(0.0, 0.0, surface.width.toDouble(), surface.height.toDouble())
      labels.forEach {
        surfaceContext.renderText(it)
      }
      var moved = false
      labels.forEachIndexed { j, it ->
        if (abs(it.x - positions[j].x) > 0.1) moved = true
        if (abs(it.y - positions[j].y) > 0.1) moved = true
        if (moved) return@forEachIndexed
      }
      if (!moved) return
    }
  }

  private enum class Direction {
    HORIZONTAL, VERTICAL
  }

  // returns 1 for the left half and 2 for the right half
  private fun Label.screenHalf() = if (this.x < surface.width / 2) 1 else 2

  private fun positionLabels(iteration : Int, srcLabels : ArrayList<Label>) {
    val labels = srcLabels.sortedWith { a, b ->
      if (a.screenHalf() == 1) {
        if (b.screenHalf() == 2) -1
        else compareValues(a.y, b.y)
      } else {
        if (b.screenHalf() == 1) 1
        else -compareValues(a.y, b.y)
      }
    }

    // Attract to gravity center (middle of the relevant part of the camembert)
    labels.forEach {
      gravitate(it, it.gravX, it.gravY, mass = 2_000.0f)
      //l(it)
    }

    // Repulse from all other labels
    labels.forEach {
      labels.forEach inner@{ repulser ->
        if (repulser === it) return@inner
        gravitate(it, repulser.x + repulser.w / 2, repulser.y + repulser.h / 2, mass = -50f)
      }
    }

    // Don't overlap other labels
    labels.forEach {
      labels.forEachIndexed inner@{ index, blocker ->
        if (blocker === it) return@inner
        if (overlap(blocker, it)) {
          // Initialize with move down (blocker.top = it.bottom when blocker.y += amount, and amount > 0)
          var dir = Direction.VERTICAL
          var amount = it.bottom - blocker.top
          if (blocker.bottom - it.top < amount) {
            // Moving up is better (blocker.bottom = it.top when blocker.y += amount, and amount < 0)
            amount = it.top - (blocker.bottom)
          }
          if (it.right - (blocker.left) < abs(amount)) {
            // Moving right moves less (blocker.left = it.right when blocker.x += amount, and amount > 0)
            dir = Direction.HORIZONTAL
            amount = it.right - (blocker.left)
          }
          if (blocker.right - it.left < abs(amount)) {
            // Moving left moves less (blocker.x + blocker.w / 2 = it.x - it.w / 2 when blocker.x += amount, and amount < 0)
            dir = Direction.HORIZONTAL
            amount = it.left - blocker.right
          }
          when (dir) {
            Direction.HORIZONTAL -> blocker.x += amount
            Direction.VERTICAL -> blocker.y += amount
          }
          if (index < labels.lastIndex)
            pushItems(it, blocker, labels.subList(index + 1, labels.lastIndex), dir, amount)
        }
      }
    }

    val maxSqDist = (surface.clientHeight / 8) * (surface.clientHeight / 8)
    val removed = mutableSetOf<Label>()
    srcLabels.forEach {
      if (sqDist(it.x, it.y, it.gravX, it.gravY) > maxSqDist) removed.add(it)
    }
    srcLabels.removeAll(removed)
  }

  private fun sqDist(x1 : Float, y1 : Float, x2 : Float, y2 : Float) : Float {
    val dx = x1 - x2
    val dy = y1 - y2
    return dx * dx + dy * dy
  }

  private fun pushItems(l1 : Label, l2 : Label, labels : List<Label>, dir : Direction, amount : Float) {
    data class Rect(var left  : Float, var top : Float, var right : Float, var bottom : Float) {
      fun union(label : Label) {
        left = min(left, label.left)
        top = min(top, label.top)
        right = max(right, label.right)
        bottom = max(bottom, label.bottom)
      }
      fun overlap(label : Label) : Boolean {
        if (label.right < left) return false
        if (right < label.left) return false
        if (label.bottom < top) return false
        if (bottom < label.top) return false
        return true
      }
    }
    val mask = Rect(l1.left, l1.top, l1.right, l1.bottom)
    mask.union(l2)
    labels.forEach {
      if (mask.overlap(it)) {
        when (dir) {
          Direction.HORIZONTAL -> it.x += amount
          Direction.VERTICAL -> it.y += amount
        }
        mask.union(it)
      }
    }
  }

  suspend fun drawText(g : Group) {
    labelGeneration += 1
    val labels = getLabels(g)
    surfaceContext.fix()
    surfaceContext.clearRect(0.0, 0.0, surface.width.toDouble(), surface.height.toDouble())
    positionLabels(labels)
  }
}
