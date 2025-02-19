import kotlinx.atomicfu.atomic

class UncategorizedActivity(val activity : Activity, m : String) : Exception(m)

data class WeightedGroup(val group : Group, val weight : Float)
class Group(val canon : Category, val color : Array<Float>) {
  companion object {
    private val uniqueId = atomic(1)
    private fun nextId() = "group${uniqueId.getAndIncrement()}"
    val TOP = Group(Category.TOP, arrayOf(0f, 0f, 0f))
  }
  val id = nextId()
  val activities = mutableListOf<Activity>()
  val parents = mutableListOf<WeightedGroup>()
  val children = mutableListOf<Group>()
  val totalMinutes : Minute get() = mutableMinutes.toInt()
  private var mutableMinutes = 0f
  fun addParent(group : WeightedGroup) {
    parents.add(group)
  }
  fun addChild(group : Group) {
    children.add(group)
  }
  fun addActivity(act : Activity) {
    activities.add(act)
    addTime(act.duration.toFloat())
  }
  private fun addTime(minutes : Float) {
    mutableMinutes += minutes
    parents.forEach { it.group.addTime(minutes * it.weight) }
  }
  val ancestors : List<Group> by lazy {
    val list = ArrayDeque<Group>()
    var g = this
    while (g.parents.size > 0) {
      list.addFirst(g)
      if (g.parents.size != 1) console.log("Group ${g.canon.name} has ${g.parents.size} parents")
      g = g.parents[0].group
    }
    list.addFirst(g)
    list
  }
  val ancestorsString : String by lazy {
    ancestors.map { it.canon.name.replace(" ", "_") }.joinToString(" ")
  }
}
class TagGroup(val canon : Tag) {
  private val activities = mutableListOf<Activity>()
  var totalMinutes : Minute = 0; private set
  fun addActivity(act : Activity) {
    activities.add(act)
    totalMinutes += act.duration
  }
}

suspend fun GroupSet(rules : Rules, activities : ActivityList, progressReporter : (Int) -> Unit) : GroupSet {
  val groupSet = GroupSet(rules)
  val uncategorizedActivities = mutableListOf<UncategorizedActivity>()
  val total = activities.size.ifZeroThen(1)
  var currentLine = 0
  activities.forEach { act ->
    currentLine += 1
    groupSet.categorize(act)?.let { uncategorizedActivities.add(it) }
    progressReporter((100 * currentLine) / total)
  }
  progressReporter((100 * currentLine) / total)
  if (uncategorizedActivities.isNotEmpty()) throw UncategorizedActivities(uncategorizedActivities)
  groupSet.prune()
  return groupSet
}

class GroupSet(private val rules : Rules) {
  val top : Group
  private val groupMap = rules.categories.map { Group(it, rules.colors[it.name] ?: arrayOf(0f, 0f, 0f)) }
    .groupBy { it.canon.name.lowercase() }
    .mapValues { if (it.value.size != 1) throw IllegalRuleFormatException("Category ${it.key} associated with multiple values ${it.value.size} ${it.value.joinToString(",")}") else it.value[0] }
    .toMutableMap()
  private val tags = rules.tags.map { TagGroup(it) }
    .groupBy { it.canon.name }
    .mapValues { if (it.value.size != 1) throw IllegalRuleFormatException("Tag ${it.key} associated with multiple values ${it.value.joinToString(",")}") else it.value[0] }
    .toMutableMap()

  init {
    groupMap.values.forEach { group ->
      group.canon.parents.forEach { parent ->
        val parentGroup = groupMap[parent.category.name.lowercase()]
          ?: throw IllegalRuleFormatException("Can't find parent group for name ${parent.category.name}")
        group.addParent(WeightedGroup(parentGroup, parent.weight))
        parentGroup.addChild(group)
      }
    }
    top = groupMap[Category.TOP.name.lowercase()]!!
  }

  val size : Int get() = groupMap.size

  fun categorize(activity : Activity) : UncategorizedActivity? {
    var assoc : Assoc? = null
    var group = groupMap[activity.name.lowercase()]
    if (null == group) {
      assoc = rules.categorize(activity.name) ?: return UncategorizedActivity(activity, "Unknown category for activity \"${activity.name}\" at ${activity.start.toReadableString()}")
      group = Group(Category(activity.name, assoc.categories), rules.colors[activity.name] ?: arrayOf(0f, 0f, 0f))
      assoc.categories.forEach {
        val parentGroup = groupMap[it.category.name.lowercase()]!!
        group.addParent(WeightedGroup(parentGroup, it.weight))
        parentGroup.addChild(group)
      }
      groupMap[activity.name.lowercase()] = group
    }
    group.addActivity(activity)

    val tag = tags[activity.name]
    if (null == tag) {
      if (null == assoc) assoc = rules.categorize(activity.name) ?: throw UncategorizedActivity(activity, "Unknown category for activity \"${activity.name}\" at ${activity.start.toReadableString()}")
      assoc.tags.forEach {
        @Suppress("NAME_SHADOWING") val tag = tags[it.name] ?: TagGroup(it)
        tag.addActivity(activity)
      }
    }
    return null
  }

  fun prune() {
    top.pruneChildren()
    top.sort()
    groupMap.filter { it.value.totalMinutes == 0 }.forEach {
      groupMap.remove(it.key)
    }
  }

  private fun Group.pruneChildren() {
    children.filter { it.totalMinutes == 0 }.forEach { children.remove(it) }
    children.forEach { it.pruneChildren() }
  }

  private fun Group.sort() {
    children.sortByDescending { it.totalMinutes }
    children.forEach { it.sort() }
  }

  fun findGroupsWithParent(cat : Category) : List<Group> {
    fun List<WeightedCategory>.contains(c : Category) = any { it.category == c }
    return groupMap.values.filter { it.canon.parents.contains(cat) }
  }

  fun groupForActivity(act : Activity) = groupMap[act.name.lowercase()]

  companion object {
    val EMPTY = GroupSet(Rules.EMPTY)
  }
}
