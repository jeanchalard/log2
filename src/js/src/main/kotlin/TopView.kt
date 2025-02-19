import kotlinx.dom.addClass
import kotlinx.dom.removeClass
import org.w3c.dom.Element
import org.w3c.dom.asList

class TabTargetNotSpecified(m : String) : Exception(m)

open class Tab(protected val model : Log2, protected val topView : TopView, protected val tabElement : Element) {
  protected val tabContents : Element
  init {
    tabElement.addMouseClickListener { topView.click(this) }
    val targetName = tabElement.getAttribute("data-target") ?: throw TabTargetNotSpecified("tab ${tabElement.id} doesn't have a 'data-target' attribute referring to it's associated content view")
    tabContents = el(targetName)
    if ("activeTab" in tabElement.classList.asList())
      activate()
    else
      deactivate()
  }

  open fun activate() {
    tabElement.removeClass("inactiveTab")
    tabElement.addClass("activeTab")
    tabContents.styles["visibility"] = "visible"
  }
  open fun deactivate() {
    tabElement.removeClass("activeTab")
    tabElement.addClass("inactiveTab")
    tabContents.styles["visibility"] = "hidden"
  }
}

class TimeUseTab(model : Log2, topView : TopView, tabElement : Element) : Tab(model, topView, tabElement) {
  override fun activate() {
    super.activate()
    model.currentGroup = model.groupSet.top
  }
  override fun deactivate() {
    super.deactivate()
  }
}

class CalendarTab(model : Log2, topView : TopView, tabElement : Element) : Tab(model, topView, tabElement) {
  private val view : CalendarView = CalendarView(model)
  override fun activate() {
    super.activate()
    model.currentGroup = model.groupSet.top
  }
  override fun deactivate() {
    super.deactivate()
  }
}



class TopView(model : Log2, private val main : Element) {
  private val breadcrumbView : BreadcrumbView = BreadcrumbView(model, el("breadcrumbs"), el("currentGroup"))
  private val tabs : List<Tab>

  init {

    val tabsElements = main.run {
      getElementsByClassName("inactiveTab").asList() + getElementsByClassName("activeTab").asList()
    }
    tabs = tabsElements.map {
      when (it.getAttribute("data-target")) {
        "timeUse" -> TimeUseTab(model,this, it)
        "calendar" -> CalendarTab(model, this, it)
        else -> Tab(model, this, it)
      }
    }
  }

  fun click(tab : Tab) {
    tabs.forEach {
      if (tab == it)
        it.activate()
      else
        it.deactivate()
    }
  }
}
