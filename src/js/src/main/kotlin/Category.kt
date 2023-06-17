data class Category(val name : String, val parents : List<WeightedCategory>) {
  companion object {
    val TOP = Category("Everything", emptyList())
  }
  fun isTopLevel() = parents.size == 1 && parents[0].category === TOP
  override fun toString() = "${name} (${parents})"
}

data class WeightedCategory(val category : Category, val weight : Float) {
  override fun toString() = "${category.name}:${weight}"
}
