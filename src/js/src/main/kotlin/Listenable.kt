import kotlin.reflect.KProperty

class Listenable<T>(prop : KProperty<T>) {
  private val listeners = ArrayList<(T, T) -> Unit>()
  fun listen(f : (T, T) -> Unit) {
    listeners.add(f)
  }
  fun unlisten(f : (T, T) -> Unit) {
    if (!listeners.remove(f))
      throw IllegalArgumentException("Listener not registered")
  }
  fun changed(old : T, new : T) {
    listeners.forEach { it(old, new) }
  }
}
