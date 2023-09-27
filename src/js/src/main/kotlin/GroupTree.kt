import kotlinx.atomicfu.atomic
import kotlinx.dom.addClass
import kotlinx.dom.hasClass
import kotlinx.dom.removeClass
import kotlinx.html.TagConsumer
import kotlinx.html.dom.append
import kotlinx.html.js.li
import kotlinx.html.js.span
import kotlinx.html.js.ul
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

class GroupTree(private val top : Group) {
  companion object {
    private val uniqueId = atomic(1)
    private fun nextId() = "tree${uniqueId.getAndIncrement()}"
  }
  fun render(anchor : Element) {
    anchor.innerHTML = ""
    anchor.append { render(top, null, id = nextId(), depth = 0) }
  }

  private fun Group.parentWeight(parent : Group?) =
    parents.find { it.group == parent }?.weight ?: 1.0f

  private fun TagConsumer<HTMLElement>.render(group : Group, parent : Group?, id : String, depth : Int) {
    val header = span { +group.canon.name }
    span(classes = "timeRender") {
      val parentWeight = group.parentWeight(parent)
      val realDuration = (group.totalMinutes * parentWeight).toInt().renderDuration()
      if (parentWeight < 1f)
        +"${realDuration} (${group.totalMinutes.renderDuration()} * ${!parentWeight})"
      else
        +realDuration
    }

    val leaf = group.children.isEmpty()

    val children = if (leaf) null else ul {
      if (group.activities.isNotEmpty()) {
        val li = li { span(classes = "timeRender-single") { +(group.activities.sumBy { it.duration }.renderDuration()) } }
        li.addClass("leaf")
      }
      group.children.sortedByDescending { it.totalMinutes }.forEach {
        val li = li {
          render(it, group, nextId(), depth + 1)
        }
        if (it.children.isEmpty()) li.addClass("leaf")
      }
    }
    children?.id = id

    if (leaf) {
      // It's a leaf
      children?.addClass("leaf")
    } else {
      children?.addClass("branch")
      header.onclick = {
        val li = (it.target as HTMLElement).parentElement!!
        if (li.hasClass("open")) {
          li.removeClass("open")
          li.addClass("closed")
          el(id).style["display"] = "none"
        } else {
          li.removeClass("closed")
          li.addClass("open")
          el(id).style["display"] = null
        }
      }

      if (depth < 1) {
        header.parentElement!!.addClass("open")
      } else {
        header.parentElement!!.addClass("closed")
        (children as Element).style["display"] = "none"
      }
    }
  }
}
