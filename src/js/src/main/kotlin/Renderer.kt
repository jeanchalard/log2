import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLProgram
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import org.khronos.webgl.WebGLRenderingContext as GL

const val MAX_SUBCATS = 100
const val TAU = (PI * 2).toFloat()
const val vertexShader = """
  attribute vec4 vertexPos;
  uniform mat4 modelMat;
  uniform mat4 cameraMat;
  void main() {
    gl_Position = cameraMat * modelMat * vertexPos;
  }
"""
const val fragmentShader = """
  precision mediump float;

  uniform vec3 colors[${MAX_SUBCATS * 3}];
  uniform float percentiles[${MAX_SUBCATS}];
  
  const float TAU = 6.28318;
  const float RADIUS = 0.45;
  float LINE_THICKNESS;
  uniform vec2 resolution;

  //  Function from Iñigo Quiles
  //  www.iquilezles.org/www/articles/functions/functions.htm
  float parabola(float x, float k) {
    return pow(4.0 * x * (1.0 - x), k);
  }

  vec3 colorForPercentile(float percentile) {
    for (int i = 0; i < ${MAX_SUBCATS}; i++) {
      if (percentiles[i] > percentile) return colors[i];
    }
    return vec3(0.);
  }

  vec2 boundsForPercentile(float percentile) {
    for (int i = 0; i < ${MAX_SUBCATS}; i++) {
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

class Renderer(surface : HTMLCanvasElement) {
  private val gl = surface.getContext("webgl") as GL
  private val shader : WebGLProgram

  init {
    setupSurface()
    shader = setupShaders()
    setupScene()
    sizeViewPort(surface.clientWidth, surface.clientHeight)
  }

  private inline fun <reified T> List<T>.fill(size : Int, default : T) = Array(size) { i -> if (i >= this.size) default else this[i] }
  private inline fun <reified T> Array<T>.fill(size : Int, default : T) = Array(size) { i -> if (i >= this.size) default else this[i] }

  fun render(group : Group, colors : Map<String, Array<Float>>) {
    val percentiles = mutableListOf(0f)
    val colorVals = mutableListOf(0f)
    group.children.forEach { child ->
      percentiles.add(percentiles.last() + child.totalMinutes.toFloat() / group.totalMinutes)
      val c = colors[child.canon.name] ?: arrayOf(1f, 1f, 1f)
      c.forEach { colorVals.add(it) }
    }
    percentiles.drop(1)

    gl.uniform3fv(shader["colors"], colorVals.fill(MAX_SUBCATS * 3, 1f))
    gl.uniform1fv(shader["percentiles"], percentiles.fill(MAX_SUBCATS, 10f))
    gl.drawArrays(GL.TRIANGLE_STRIP, first = 0, count = 4)
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
}
