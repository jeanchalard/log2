import kotlinx.atomicfu.atomic
import kotlinx.browser.window
import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import kotlinx.html.DIV
import kotlinx.html.TagConsumer
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.dom.append
import kotlinx.html.dom.prepend
import kotlinx.html.id
import kotlinx.html.js.onMouseMoveFunction
import kotlinx.html.js.onMouseOutFunction
import kotlinx.html.js.onMouseUpFunction
import kotlinx.html.p
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.set

private const val DAY_START = 5
private const val DAY_END = DAY_START + 24
private val WEEKDAYS = arrayOf("月", "火", "水", "木", "金", "土", "日")

// Returns a Timestamp for that day at DAY_START. Activity.start is also a Timestamp (minutes since 1970 local time)
private fun Activity.getDayStart() : Timestamp = start - ((start - DAY_START * 60) % 1440)
private fun Activity.getDayEnd() : Timestamp = end - ((end - DAY_START * 60) % 1440)

class CalendarView(private val model : Log2) {
  private var tooltip = el("tooltip")
  private var hoveredElement : HTMLElement? = null
  private var pinnedElement : Group? = null

  init {
    /* Draw the grid (lines per the hour, alternating solid and dash) and the corresponding legend on the left. */
    val leftPane = el("calendarHourLegend")
    val hourLines = el("calendarHourLines")
    for (i in DAY_END downTo (DAY_START + 1)) { // + 1 because the line would go at y = 0, so don't put a div there
      if (0 == i % 2) {
        leftPane.prepend {
          div(classes = "hourLegend") {
            p(classes = "centered") { +i.dg() }
          }.also {
            it.addMouseOverListener {
              hourLines.querySelector("div[data-hour=\"${i}\"]")?.addClass("gridHourSelected")
            }
            it.addMouseOutListener {
              hourLines.querySelector("div[data-hour=\"${i}\"]")?.removeClass("gridHourSelected")
            }
          }
        }
      }
      hourLines.prepend {
        when {
          i == DAY_END -> div(classes = "gridHourLast")
          0 == i % 2 -> div(classes = "gridHourSolid")
          else -> div(classes = "gridHourDashed")
        }.also {
          it.dataset["hour"] = i.toString()
        }
      }
    }

    render(model)

    model.groupSetProp.listen { old, new ->
      mainScope.launchHandlingError {
        render(model)
      }
    }
    model.currentGroupProp.listen { old, new ->
      pinnedElement?.run {
        stopPulse(ancestorsString)
        if (new.parents.isEmpty()) {
          pinnedElement = null
        } else {
          startPulse(new.ancestorsString)
          pinnedElement = new
        }
      }
    }
  }

  companion object {
    private val uniqueId = atomic(1)
    private fun nextId() = "tree${uniqueId.getAndIncrement()}"
  }

  private fun render(model : Log2) {
    addDayLegend(el("calendarDayLegend"), model)
    renderActivities(el("calendarData"), model)
  }

  fun addDayLegend(dayLegend : HTMLElement, model : Log2) {
    /* Add the days at the bottom */
    var currentDay = DAY_START * 60 // 1970-01-01 at DAY_START, to create a day immediately
    dayLegend.innerHTML = ""
    model.forEachActivity {
      val day = it.getDayStart()
      if (currentDay != day) {
        dayLegend.append {
          val d = day.getDayAndWeekday()
          val colorClass = if (d.second >= 5) "holiday" else "weekday"
          div(classes = "day ${colorClass}") {
            +d.first.dg()
            br
            +WEEKDAYS[d.second]
          }
        }
        currentDay = day
      }
    }
  }

  private fun forAllGroupsStartingWith(name : String, what : (HTMLElement) -> Unit) {
    el("calendarData").querySelectorAll("div[data-group^=\"${name}\"]").asList().forEach { what(it as HTMLElement) }
  }
  private fun startPulse(actName : String) = forAllGroupsStartingWith(actName) {
    it.addClass("pulse")
    it.offsetHeight // This restarts the animation if it's running, because accessing this "triggers a reflow". Javascript...
  }
  private fun stopPulse(actName : String) = forAllGroupsStartingWith(actName) { it.removeClass("pulse") }

  // Where |originalActivity| is the actual activity for this box, as such it can span multiple columns.
  // And |act| is the split up activity per column. So e.g. originalActivity will be from 23:00 to 08:00, but
  // act will be 23:00~29:00 and there will be a second call with the same originalActivity and act will be 05:00~08:00
  private fun DIV.setupActivity(act : Activity, originalActivity : Activity, group : Group) {
    val color = group.color.toColorString()
    val ancestors = group.ancestors
    val ancestorsString = group.ancestorsString
    id = "act_" + nextId()
    style = "flex : ${act.duration} 1 auto; background-color : ${color}; --bg-color : ${color};"
    attributes["data-group"] = ancestorsString
    onMouseUpFunction = {
      model.stack = ancestors
      startPulse(ancestorsString)
      pinnedElement = group
    }
    fun onMouseMove(event : Event) {
      val element = event.target as? HTMLElement ?: return
      if (element != hoveredElement) {
        hoveredElement = element
        if (null == pinnedElement) {
          model.stack = ancestors
          startPulse(ancestorsString)
        }

        // Setup tooltip
        val blockPos = element.getBoundingClientRect()
        tooltip.innerHTML =
         "${originalActivity.start.toReadableTimeWithoutDate()} ~ ${originalActivity.end.toReadableTimeWithoutDate()} (${originalActivity.duration.renderDuration()})<br>${originalActivity.name}"
        tooltip.styles["display"] = "block"
        val ttSize = tooltip.getBoundingClientRect()
        val ttWidth = ttSize.right - ttSize.left
        val x = ((blockPos.left + blockPos.right - ttWidth) / 2).coerceIn(5.0, window.innerWidth - ttWidth - 5)
        tooltip.styles["left"] = "${x}px"
      }
      val y = (event as MouseEvent).clientY + 20
      tooltip.styles["top"] = "${y}px"
    }
    onMouseMoveFunction = ::onMouseMove
    onMouseOutFunction = { event ->
      if (null == pinnedElement) stopPulse(ancestorsString)
      if (event.target == hoveredElement) {
        tooltip.styles["display"] = "none"
        hoveredElement = null
      }
    }
  }

  private fun renderActivities(root : HTMLElement, model : Log2) {
    inline fun <T : HTMLElement, C : TagConsumer<T>> C.activity(act : Activity, originalActivity : Activity, group : Group, crossinline block : DIV.() -> Unit = {}) : T {
      return if (group.parents.size == 1)
        div(classes = "activity") {
          setupActivity(act, originalActivity, group)
          block()
        }
      else {
        div(classes = "activity columns") {
          group.parents.forEach { g ->
            div(classes = "activity") {
              setupActivity(act, originalActivity, g.group)
              block()
            }
          }
        }
      }
    }
    inline fun <T, C : TagConsumer<T>> C.spacer(duration : Minute, crossinline block : DIV.() -> Unit = {}) : T {
      return div(classes = "activity", block).also { (it as HTMLElement).styles["flex"] = "${duration} 1 auto" }
    }

    root.innerHTML = ""
    tooltip.styles["display"] = "none"
    if (model.size <= 0) return
    val first = model.first
    val firstDay = first.getDayEnd()
    var currentDay = firstDay
    console.log("First day ${currentDay.toReadableString()}")
    root.append { div(classes = "day rows") }
    var currentDayElement = root.lastChild!!
    // If the first activity doesn't start immediately at the start of this day, then add an empty div with the
    // right size to pad
    if (first.start != currentDay)
      currentDayElement.append { spacer(first.start - currentDay) }

    model.forEachActivity { act ->
      val group = model.groupSet.groupForActivity(act) ?: throw UncategorizedActivity(act, "While rendering calendar")
      val dayStart = act.getDayStart()
      val dayEnd = act.getDayEnd()
      var start = dayStart
      console.log("Start ${start.toReadableString()}")
      while (start <= dayEnd) {
        if (currentDay < start) {
          root.append { div(classes = "day separatorLeft rows") }
          currentDayElement = root.lastChild!!
          currentDay = start
        }
        start += 1440
        val brokenDownAct = if (start <= dayEnd) act.view(currentDay, start) else act.view(currentDay, act.end)
        if (brokenDownAct.start >= firstDay) { // If the start time is right through a block that starts yesterday, don't render the part for yesterday
          currentDayElement.append {
            activity(brokenDownAct, act, group)
          }
        }
      }
    }
    val last = model.last
    val extraDuration = last.getDayEnd() + 1440 - last.end
    if (extraDuration > 0)
      currentDayElement.append { spacer(extraDuration) }
  }
}
