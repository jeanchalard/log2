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

class GroupTree(private val model : Log2, private val anchor : Element, private val top : Group, private val colors : Map<String, Array<Float>>) {
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
      if (!oldEl.ancestorOf(newEl))
        close(oldEl)
    }
  }

  private fun Element.ancestorOf(descendant : Element?) : Boolean = when (descendant) {
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
    val header = span(classes = if (leaf) "" else "handCursor") { +group.canon.name }
    if ("" == header.parentElement!!.id) header.parentElement!!.id = group.id
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
    val color = colors[group.canon.name] ?: arrayOf(1f, 1f, 1f)
    (header as Element).style["color"] = "rgb(${color.map{it*255}.joinToString(",")})"

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
        if (li.hasClass("open"))
          close(li)
        else
          open(li)
      }

      if (depth < 1) {
        header.parentElement!!.addClass("open")
      } else {
        header.parentElement!!.addClass("closed")
        (children as Element).style["display"] = "none"
      }
    }
  }

  private fun inactive(el : Element) = el.removeClass("active")
  private fun active(el : Element) = el.addClass("active")

  private fun close(el : Element) {
    el.removeClass("open")
    el.addClass("closed")
    el.getElementsByTagName("ul").item(0)?.run { style["display"] = "none" }
  }

  private fun open(el : Element) {
    el.removeClass("closed")
    el.addClass("open")
    el.getElementsByTagName("ul").item(0)?.run { style["display"] = null }
  }
}
