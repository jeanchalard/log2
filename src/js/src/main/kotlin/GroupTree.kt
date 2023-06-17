import kotlinx.html.TagConsumer
import kotlinx.html.dom.append
import kotlinx.html.js.li
import kotlinx.html.js.span
import kotlinx.html.js.ul
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

class GroupTree(private val top : Group) {
  fun render(anchor : Element) = anchor.append { render(top) }

  private fun TagConsumer<HTMLElement>.render(group : Group) {
    span { +group.canon.name }
    span(classes = "timeRender") { +group.totalMinutes.renderDuration() }
    ul {
      if (group.activities.isNotEmpty() && group.children.isNotEmpty())
        li { span(classes = "timeRender") {+(group.activities.sumBy { it.duration }.renderDuration()) } }
      group.children.sortedByDescending { it.totalMinutes }.forEach {
        li {
          render(it)
        }
      }
    }
  }
}
