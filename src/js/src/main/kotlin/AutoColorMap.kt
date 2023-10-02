class AutoColorMap(private val underlying : Map<String, Array<Float>>) : Map<String, Array<Float>> by underlying {
  override fun get(key : String) : Array<Float> = underlying.getOrElse(key) {
    val h = key.hashCode()
    arrayOf(
      ((h          and 0xFF).toFloat() / 0xFF),
      (((h shr 8)  and 0xFF).toFloat() / 0xFF),
      (((h shr 16) and 0xFF).toFloat() / 0xFF),
    )
  }
}
