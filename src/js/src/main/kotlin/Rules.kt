data class Assoc(val regexp : Regex, val categories : List<WeightedCategory>, val tags : List<Tag>)
data class Rules(val name : String, val rules : List<Assoc>, val colors : Map<String, Array<Float>>) {
  val categories : Set<Category>
  val tags = rules.flatMap { it.tags }.toSet()

  init {
    val l = ArrayList<Category>()
    rules.flatMapTo(l) { assoc -> assoc.categories.map { it.category } }
    l.add(Category.TOP)
    categories = l.toSet()
  }

  fun categorize(s : String) : Assoc? = rules.find { it.regexp.matches(s) }
}
