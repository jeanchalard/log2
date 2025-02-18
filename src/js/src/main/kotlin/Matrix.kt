import kotlin.math.tan

val IDENTITY = arrayOf(1f, 0f, 0f, 0f,     0f, 1f, 0f, 0f,    0f, 0f, 1f, 0f,    0f, 0f, 0f, 1f)

fun perspective(FoV : Float, aspect : Float, nearDistance : Float, farDistance : Float) : Array<Float> {
  val f = 1f / tan(FoV / 2)
  val nf = 1f / (nearDistance - farDistance)
  return arrayOf(
    f / aspect, 0f,                                0f,  0f,
    0f,          f,                                0f,  0f,
    0f,         0f, (farDistance + nearDistance) * nf, -1f,
    0f,         0f,                 -2 * nearDistance,  0f
  )
}

fun translation(x : Float, y : Float, z : Float) = arrayOf(
  1f, 0f, 0f, 0f,
  0f, 1f, 0f, 0f,
  0f, 0f, 1f, 0f,
   x,  y,  z, 1f
)

fun translate(mat : Array<Float>, x : Float, y : Float, z : Float) {
  mat[12] = x * mat[0] + y + mat[4] + z *  mat[8] + mat[12]
  mat[13] = x * mat[1] + y + mat[5] + z *  mat[9] + mat[13]
  mat[14] = x * mat[2] + y + mat[6] + z * mat[10] + mat[14]
  mat[15] = x * mat[3] + y + mat[7] + z * mat[11] + mat[15]
}
