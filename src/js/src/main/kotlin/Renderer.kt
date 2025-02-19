import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLProgram
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.abs
import org.khronos.webgl.WebGL2RenderingContext as GL

const val RADIUS = 0.35f
const val TAU = (PI * 2).toFloat()
const val vertexShader = """
  attribute vec4 vertexPos;
  uniform mat4 modelMat;
  uniform mat4 cameraMat;
  void main() {
    gl_Position = cameraMat * modelMat * vertexPos;
  }
"""
class Renderer(surface : HTMLCanvasElement) {
  private val gl = surface.getContext("webgl2") as GL
  // No idea what the hell is at work here, this looks like yet another web bizarre bug^H^H^Hfeature. Why can't I
  // create a uniform of uniformSize when it's reputed to be the max size ? Why can't I do it for half, but it's
  // fine with / 2 - 1 ?
  private val uniformSize = gl.getParameter(GL.MAX_FRAGMENT_UNIFORM_VECTORS) as Int / 2 - 1
  private val fragmentShader = """
  precision mediump float;

  uniform vec3 colors[${uniformSize}];
  uniform float percentiles[${uniformSize}];
  
  const float TAU = 6.28318;
  const float RADIUS = ${RADIUS};
  float LINE_THICKNESS;
  uniform vec2 resolution;

  //  Function from Iñigo Quiles
  //  www.iquilezles.org/www/articles/functions/functions.htm
  float parabola(float x, float k) {
    return pow(4.0 * x * (1.0 - x), k);
  }

  vec3 colorForPercentile(float percentile) {
    for (int i = 0; i < ${uniformSize}; i++) {
      if (percentiles[i] > percentile) return colors[i-1];
    }
    return vec3(0.);
  }

  vec2 boundsForPercentile(float percentile) {
    for (int i = 0; i < ${uniformSize}; i++) {
      if (percentiles[i] > percentile) return vec2(i == 0 ? 0. : percentiles[i - 1], percentiles[i]);
    }
    return vec2(0., 0.0001); // At least it won't crash
  }

  vec2 polarToCartesian(float r, float a) {
    return r * vec2(cos(TAU * a), sin(TAU * a));
  }

  vec3 colorForCoord(vec2 uv) {
    float r = length(uv);
    float angle = atan(uv.y, uv.x) / TAU;
    angle = mod(angle, 1.);
    vec3 color = colorForPercentile(angle);
    vec2 percentileRange = boundsForPercentile(angle);

    vec2 startPoint = polarToCartesian(r, percentileRange.x);
    vec2 endPoint = polarToCartesian(r, percentileRange.y);
    float distFromClosestBorder = min(distance(startPoint, uv), distance(endPoint, uv));

    float prop = max(parabola(0.5 + distFromClosestBorder / LINE_THICKNESS, 1.), 0.);
    return mix(color, vec3(1.), prop);
  }

  void main() {
    float zoom = 1. / min(resolution.x, resolution.y);
    LINE_THICKNESS = 2. * zoom;
    vec2 uv = (gl_FragCoord.xy - resolution * 0.5) * zoom;
    float r = length(uv);
    float distFromEdge = RADIUS - r;
    if (distFromEdge < -LINE_THICKNESS)
      gl_FragColor = vec4(0.);
    else if (distFromEdge > LINE_THICKNESS)
      gl_FragColor = vec4(colorForCoord(uv), 1.);
    else {
      vec4 source = distFromEdge < 0. ? vec4(0.) : vec4(colorForCoord(uv), 1.);
      float prop = max(parabola(0.5 + distFromEdge / LINE_THICKNESS, 1.), 0.);
      gl_FragColor = mix(source, vec4(1.), prop);
    }
  }
"""

  private val shader : WebGLProgram

  // Identify if the group has changed (different set) or not (e.g. window resize) to see if animation should start
  private var lastGroup = Group.TOP
  // The current animation values, to avoid allocating every time
  private val currentColors = Array(uniformSize * 3) { 0f }
  private val currentPercentiles = Array(uniformSize) { 0f }
  // The animation end values
  private val endColors = Array(uniformSize * 3) { 0f }
  private val endPercentiles = Array(uniformSize) { 0f }

  init {
    setupSurface()
    shader = setupShaders()
    setupScene()
    sizeViewPort(surface.clientWidth, surface.clientHeight)
  }

  private inline fun <reified T> Array<T>.fill(src : List<T>, default : T) = indices.forEach { i -> this[i] = if (i >= src.size) default else src[i] }

  private fun interpolate(out : Array<Float>, end : Array<Float>) : Boolean {
    var finish = true
    out.indices.forEach { i ->
      val diff = end[i] - out[i]
      out[i] += 0.1f * diff
      if (abs(diff) > 0.001) finish = false
    }
    return finish
  }

  // To avoid re-allocating for each render
  private val percentiles = mutableListOf<Float>()
  private val colorVals = mutableListOf<Float>()
  fun render(group : Group, colors : Map<String, Array<Float>>) : Boolean {
    if (group !== lastGroup) {
      lastGroup = group
      percentiles.clear()
      colorVals.clear()
      percentiles.add(0f)
      group.children.forEach { child ->
        percentiles.add(percentiles.last() + child.totalMinutes.toFloat() / group.totalMinutes)
        val c = colors[child.canon.name] ?: arrayOf(1f, 1f, 1f)
        c.forEach { colorVals.add(it) }
      }
      percentiles.drop(1)
      endColors.fill(colorVals, 0f)
      endPercentiles.fill(percentiles, 10f)
    }

    var finished = true
    finished = finished and interpolate(currentColors, endColors)
    finished = finished and interpolate(currentPercentiles, endPercentiles)
    gl.uniform3fv(shader["colors"], currentColors)
    gl.uniform1fv(shader["percentiles"], currentPercentiles)
    gl.drawArrays(GL.TRIANGLE_STRIP, first = 0, count = 4)
    return finished
  }

  private fun setupSurface() {
    gl.enable(GL.DEPTH_TEST)
    gl.depthFunc(GL.LEQUAL)
  }

  private fun setupShaders() : WebGLProgram {
    val program = gl.createProgram()!!
    gl.attachShader(program, vertexShader.toVertexShader(gl))
    gl.attachShader(program, fragmentShader.toFragmentShader(gl))
    gl.linkProgram(program)
    if (!(gl.getProgramParameter(program, GL.LINK_STATUS) as Boolean)) {
      throw UnableToCreateProgram(gl.getProgramInfoLog(program))
    }
    gl.useProgram(program)
    return program
  }

  private operator fun WebGLProgram.get(s : String) = gl.getUniformLocation(this, s)

  fun sizeViewPort(width : Int, height : Int) {
    val projectionMat = perspective(TAU / 4, width.toFloat() / height, 0.001f, 2f)
    gl.uniformMatrix4fv(shader["cameraMat"], transpose = false, projectionMat)
    gl.viewport(0, 0, width, height)
    gl.uniform2fv(shader["resolution"], arrayOf(width.toFloat(), height.toFloat()))
  }

  private fun setupSquareBuffer() {
    val posBuffer = gl.createBuffer()!!
    gl.bindBuffer(GL.ARRAY_BUFFER, posBuffer)
    val square = Float32Array(arrayOf(1f, 1f, 0f, -1f, 1f, 0f, 1f, -1f, 0f, -1f, -1f, 0f))
    gl.bufferData(GL.ARRAY_BUFFER, square, GL.STATIC_DRAW)
    val programAttribLocation = gl.getAttribLocation(shader, "vertexPos")
    gl.vertexAttribPointer(
      programAttribLocation,
      size = 3, type = GL.FLOAT, normalized = false, stride = 0, offset = 0
    )
    gl.enableVertexAttribArray(programAttribLocation)
  }

  private fun setupScene() {
    setupSquareBuffer()
    val translationMat = translation(0f, 0f, -0.002f)
    gl.uniformMatrix4fv(shader["modelMat"], transpose = false, translationMat)
  }

  fun setPitch(pitch : Float) {
  }
}
