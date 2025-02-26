import kotlinx.atomicfu.atomic
import kotlinx.dom.addClass
import kotlinx.dom.hasClass
import kotlinx.dom.removeClass
import kotlinx.html.TagConsumer
import kotlinx.html.dom.append
import kotlinx.html.js.div
import kotlinx.html.js.li
import kotlinx.html.js.span
import kotlinx.html.js.ul
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

class GroupTree(private val model : Log2, private val anchor : Element, private val top : Group) {
  companion object {
    private val uniqueId = atomic(1)
    private fun nextId() = "tree${uniqueId.getAndIncrement()}"
  }
  private val updater = this::updateCurrentGroup
  init {
    model.currentGroupProp.listen(updater)
    render()
  }
  fun delete() {
    model.currentGroupProp.unlisten(updater)
  }

  private fun updateCurrentGroup(oldGroup : Group, newGroup : Group) {
    val newEl = elOrNull(newGroup.id)
    if (null != newEl) {
      active(newEl)
      open(newEl)
    }
    val oldEl = elOrNull(oldGroup.id)
    if (null != oldEl) {
      inactive(oldEl)
      if (!oldEl.getElementToOpen().ancestorOf(newEl?.getElementToOpen()))
        close(oldEl)
    }
  }

  private fun Element?.ancestorOf(descendant : Element?) : Boolean =
    if (null == this) false
    else when (descendant) {
      null -> false
      this -> true
      else -> ancestorOf(descendant.parentElement)
    }

  private fun render() {
    anchor.innerHTML = ""
    anchor.append { render(top, null, id = nextId(), depth = 0) }
  }

  private fun Group.parentWeight(parent : Group?) =
    parents.find { it.group == parent }?.weight ?: 1.0f

  private fun TagConsumer<HTMLElement>.render(group : Group, parent : Group?, id : String, depth : Int) {
    val leaf = group.children.isEmpty()
    val colorHtml = "rgb(${group.color.map { it * 255 }.joinToString(",")})"
    val row = div(classes = if (leaf) "leaf" else "") {
      val activityName = span { +group.canon.name }
      if ("" == activityName.parentElement!!.id) activityName.parentElement!!.id = group.id
      val parentTime = parent?.totalMinutes
      span(classes = "timeRender") {
        val parentWeight = group.parentWeight(parent)
        val realDuration = (group.totalMinutes * parentWeight).toInt()
        val percent = if (null == parentTime) " " else (100f * realDuration / parentTime).renderPercent() + " "
        if (parentWeight < 1f)
          +"${percent}${realDuration.renderDuration()} (${group.totalMinutes.renderDuration()} * ${!parentWeight})"
        else
          +"${percent}${realDuration.renderDuration()}"
      }
      activityName.styles["color"] = colorHtml
    }

    val children = if (leaf) null else ul {
      if (group.activities.isNotEmpty()) {
        val li = li {
          div(classes = "leaf") {
            span(classes = "timeRender-single") { +(group.activities.sumBy { it.duration }.renderDuration()) }
          }
        }
        li.addClass("leaf")
        li.styles["color"] = colorHtml
      }
      group.children.sortedByDescending { it.totalMinutes }.forEach {
        li {
          render(it, group, nextId(), depth + 1)
        }
      }
    }
    children?.id = id

    if (leaf) {
      // It's a leaf
      children?.addClass("leaf")
    } else {
      children?.addClass("branch")
      row.onclick = {
        if (row.hasClass("open"))
          close(row)
        else
          open(row)
      }

      if (depth < 1) {
        row.addClass("open")
      } else {
        row.addClass("closed")
        children?.run { styles["display"] = "none" }
      }
    }
  }

  private fun inactive(el : Element) = el.removeClass("active")
  private fun active(el : Element) = el.addClass("active")

  private inline fun Element.getElementToOpen() = nextElementSibling

  private fun close(el : Element) {
    el.removeClass("open")
    el.addClass("closed")
    el.getElementToOpen()?.run { styles["display"] = "none" }
  }

  private fun open(el : Element) {
    el.removeClass("closed")
    el.addClass("open")
    el.getElementToOpen()?.run { styles["display"] = null }
  }
}
