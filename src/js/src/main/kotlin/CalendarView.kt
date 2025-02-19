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
}
