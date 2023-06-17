data class Tag(val name : String, val parents : List<Tag>) {
  companion object {
    val TOP = Tag("#", emptyList())
  }
  init {
    if ('#' != name[0]) throw IllegalArgumentException("Tag name doesn't start with # : ${name}")
  }
  fun isTopLevel() = parents.size == 1 && parents[0] === TOP
}
