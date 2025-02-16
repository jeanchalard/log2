import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLTexture
import org.khronos.webgl.WebGL2RenderingContext as GL

class IncorrectShader(msg : String) : Exception(msg)
class UnableToCreateProgram(msg : String?) : Exception(msg ?: "Unknown error")

fun String.toShader(gl : GL, type : Int) : WebGLShader {
  val shader = gl.createShader(type)!!
  gl.shaderSource(shader, this)
  gl.compileShader(shader)
  if (!(gl.getShaderParameter(shader, GL.COMPILE_STATUS) as Boolean)) {
    val error = gl.getShaderInfoLog(shader) + "\n${this}"
    try {
      throw IncorrectShader(error)
    } finally {
      gl.deleteShader(shader)
    }
  }
  return shader
}

fun String.toVertexShader(gl : GL) = toShader(gl, GL.VERTEX_SHADER)
fun String.toFragmentShader(gl : GL) = toShader(gl, GL.FRAGMENT_SHADER)

fun GL.createAndSetupTexture() : WebGLTexture? = createTexture().also { tex ->
  bindTexture(GL.TEXTURE_2D, tex)
  // Behavior for minification and magnification. Default is LINEAR.
  texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
  texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
  // Behavior for wrapping of coordinate across the s- and t-axis. Default is REPEAT.
  texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
  texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
}

fun GL.setActiveTexture(i : Int) {
  when (i) {
    0 -> activeTexture(GL.TEXTURE0)
    1 -> activeTexture(GL.TEXTURE1)
    2 -> activeTexture(GL.TEXTURE2)
    3 -> activeTexture(GL.TEXTURE3)
    4 -> activeTexture(GL.TEXTURE4)
    else -> throw RuntimeException("Add constant in setActiveTexture")
  }
}