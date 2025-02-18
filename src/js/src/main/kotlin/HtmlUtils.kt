import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

/**
 * Kotlin/JS is pitifully underfurnished for any real HTML work :(
 */

fun el(s : String) = (document.getElementById(s) ?: throw HTMLElementNotFound("Can't find element with id ${s}")) as HTMLElement
fun elOrNull(s : String) = document.getElementById(s)
val Element.first get() = firstElementChild as HTMLElement
var Element.styleString : String?
  get() = getAttribute("style")
  set(s) = if (null == s) removeAttribute("style") else setAttribute("style", s)

value class Style(private val e : Element) {
  companion object {
    private val attrFinder = Regex("\\b(\\S+)\\b\\s*:")
  }
  private fun List<String>.toPair() = this[0].trim() to this[1].trim()
  private fun toPairs() = e.styleString?.splitToSequence(";")?.map { it.split(":") }?.mapNotNull { if (it.size != 2) null else it.toPair() }
  operator fun get(key : String) = toPairs()?.find { it.first == key }?.second
  operator fun set(key : String, value : String?) {
    val l = toPairs()?.filter { it.first != key } ?: emptySequence()
    e.styleString = (if (null == value) l else l + (key to value)).joinToString(";") { "${it.first}:${it.second}" } + ";"
  }
  fun getAndSet(key : String, f : (String?) -> String) {
    val l = (toPairs() ?: emptySequence()).toMutableList()
    val attrIndex = l.indexOfFirst { it.first == key }
    l[attrIndex] = key to f(if (attrIndex < 0) null else l[attrIndex].second)
    e.styleString = l.joinToString(";") { "${it.first}:${it.second}" } + ";"
  }
}
val Element.styles : Style
  get() = Style(this)

var Element.alpha : Float
  get() = styles["alpha"]?.toFloat() ?: 1.0f
  set(a) { styles["alpha"] = a.toString() }
fun Element.addMouseOverListener(handler : (MouseEvent) -> Unit) = addEventListener("mouseover", { handler(it as MouseEvent) })
fun Element.addMouseOutListener(handler : (MouseEvent) -> Unit) = addEventListener("mouseout", { handler(it as MouseEvent) })
fun Element.addMouseMoveListener(handler : (MouseEvent) -> Unit) = addEventListener("mousemove", { handler(it as MouseEvent) })
fun Element.addMouseClickListener(handler : (MouseEvent) -> Unit) = addEventListener("click", { handler(it as MouseEvent) })
fun Element.addOnInputListener(handler : (Event) -> Unit) = addEventListener("input", { handler(it) })
