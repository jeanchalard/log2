import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.MouseEvent

/**
 * Kotlin/JS is pitifully underfurnished for any real HTML work :(
 */

fun el(s : String) = document.getElementById(s) ?: throw HTMLElementNotFound("Can't find element with id ${s}")
fun elOrNull(s : String) = document.getElementById(s)
val Element.first get() = firstElementChild as HTMLElement
var Element.style : String?
  get() = getAttribute("style")
  set(s) = if (null == s) removeAttribute("style") else setAttribute("style", s)
fun Element.addMouseMoveListener(handler : (MouseEvent) -> Unit) = addEventListener("mousemove", { handler(it as MouseEvent) })
