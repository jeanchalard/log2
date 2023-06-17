import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLRenderingContext as GL

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
