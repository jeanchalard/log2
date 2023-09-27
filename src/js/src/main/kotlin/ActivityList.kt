import kotlinx.coroutines.yield

class ActivityList private constructor(private val list : ArrayList<Activity>, private val start : Timestamp?, private val end : Timestamp?) {
  constructor(l : List<Activity>) : this(ArrayList(l), null, null)

  val startDate = start ?: list.first().start
  val endDate = end ?: list.last().end

  private val startIndex get() = if (null == start) 0 else {
    val s = startDate
    val insertionPoint = list.binarySearch { it.end.compareTo(s) }
    if (insertionPoint > 0) insertionPoint else -insertionPoint - 1
  }
  private val endIndex get() = if (null == end) list.lastIndex else {
    val e = endDate
    val insertionPoint = list.binarySearch { it.start.compareTo(e) }
    if (insertionPoint > 0) insertionPoint else -insertionPoint - 1
  }

  val size get() = endIndex - startIndex
  suspend fun forEach(what : (Activity) -> Unit) {
    (startIndex..<endIndex).forEach {
      what(list[it])
      yield()
    }
  }

  fun view(start : Timestamp, end : Timestamp) = ActivityList(list, start, end)
}
