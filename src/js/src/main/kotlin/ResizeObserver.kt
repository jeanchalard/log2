import org.w3c.dom.Element

external class BoxSize {
  val blockSize: Number
  val inlineSize: Number
}
external class ResizeObserverEntry {
  val target: Element
  val borderBoxSize: Array<BoxSize>
  val contentBoxSize: Array<BoxSize>
  val devicePixelContentBoxSize: Array<BoxSize>
}
external class ResizeObserver(callback: (Array<ResizeObserverEntry>, ResizeObserver) -> Unit) {
  fun observe(target: Element, options: dynamic = definedExternally)
  fun unobserve(target: Element)
  fun disconnect()
}

fun Element.observeResize(observer : (x : Float, y : Float) -> Unit) {
  val proxy = ResizeObserver { elements, _ ->
    if (elements.size != 1) console.log("Resize observer with multiple elements ?")
    val x = elements[0].devicePixelContentBoxSize[0].inlineSize.toFloat()
    val y = elements[0].devicePixelContentBoxSize[0].blockSize.toFloat()
    observer(x, y)
  }
  proxy.observe(this)
}
