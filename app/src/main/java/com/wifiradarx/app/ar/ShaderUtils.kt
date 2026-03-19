package com.wifiradarx.app.ar

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log

object ShaderUtils {

    private const val TAG = "ShaderUtils"

    // ─── Shader Sources ────────────────────────────────────────────────────────

    const val PILLAR_VERTEX_SHADER = """
        uniform mat4 u_MVP;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        varying vec4 v_Color;
        void main() {
            v_Color = a_Color;
            gl_Position = u_MVP * a_Position;
        }
    """

    const val PILLAR_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;
        uniform float u_Pulse;
        void main() {
            float alpha = 0.7 + 0.3 * u_Pulse;
            gl_FragColor = vec4(v_Color.rgb, v_Color.a * alpha);
        }
    """

    const val HEATMAP_MESH_VERTEX_SHADER = """
        uniform mat4 u_MVP;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        varying vec4 v_Color;
        void main() {
            v_Color = a_Color;
            gl_Position = u_MVP * a_Position;
        }
    """

    const val HEATMAP_MESH_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;
        void main() {
            gl_FragColor = vec4(v_Color.rgb, 0.55);
        }
    """

    const val VOXEL_VERTEX_SHADER = """
        uniform mat4 u_MVP;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        attribute float a_Size;
        varying vec4 v_Color;
        void main() {
            v_Color = a_Color;
            gl_Position = u_MVP * a_Position;
            gl_PointSize = a_Size;
        }
    """

    const val VOXEL_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = dot(coord, coord);
            if (dist > 0.25) discard;
            float alpha = 1.0 - smoothstep(0.15, 0.25, dist);
            gl_FragColor = vec4(v_Color.rgb, v_Color.a * alpha);
        }
    """

    const val DIRECTION_ARROW_VERTEX_SHADER = """
        uniform mat4 u_MVP;
        attribute vec4 a_Position;
        varying float v_Height;
        void main() {
            v_Height = a_Position.y;
            gl_Position = u_MVP * a_Position;
        }
    """

    const val DIRECTION_ARROW_FRAGMENT_SHADER = """
        precision mediump float;
        varying float v_Height;
        uniform float u_Time;
        uniform vec3 u_Color;
        void main() {
            float pulse = 0.5 + 0.5 * sin(u_Time * 3.0 + v_Height * 4.0);
            gl_FragColor = vec4(u_Color * (0.7 + 0.3 * pulse), 0.9);
        }
    """

    const val RADAR_VERTEX_SHADER = """
        attribute vec4 a_Position;
        uniform mat4 u_MVP;
        uniform float u_PointSize;
        void main() {
            gl_Position = u_MVP * a_Position;
            gl_PointSize = u_PointSize;
        }
    """

    const val RADAR_FRAGMENT_SHADER = """
        precision mediump float;
        uniform vec4 u_Color;
        uniform float u_Time;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float dist = length(coord) * 2.0;
            if (dist > 1.0) discard;
            float glow = exp(-dist * 3.0);
            float pulse = 0.6 + 0.4 * sin(u_Time * 4.0);
            gl_FragColor = vec4(u_Color.rgb * glow * pulse, u_Color.a * glow);
        }
    """

    const val THREAT_ALERT_VERTEX_SHADER = """
        uniform mat4 u_MVP;
        attribute vec4 a_Position;
        uniform float u_Time;
        varying float v_Dist;
        void main() {
            v_Dist = length(a_Position.xyz);
            gl_Position = u_MVP * a_Position;
            gl_PointSize = 20.0 + 10.0 * sin(u_Time * 6.0);
        }
    """

    const val THREAT_ALERT_FRAGMENT_SHADER = """
        precision mediump float;
        uniform float u_Time;
        varying float v_Dist;
        void main() {
            vec2 coord = gl_PointCoord - vec2(0.5);
            float d = length(coord) * 2.0;
            if (d > 1.0) discard;
            float pulse = 0.5 + 0.5 * sin(u_Time * 8.0);
            float emission = exp(-d * 2.0) * pulse;
            gl_FragColor = vec4(1.0, 0.0 + 0.2 * pulse, 0.0, emission);
        }
    """

    const val BACKGROUND_VERTEX_SHADER = """
        attribute vec4 a_Position;
        attribute vec2 a_TexCoord;
        varying vec2 v_TexCoord;
        void main() {
            gl_Position = a_Position;
            v_TexCoord = a_TexCoord;
        }
    """

    const val BACKGROUND_FRAGMENT_SHADER = """
        precision mediump float;
        uniform samplerExternalOES u_Texture;
        varying vec2 v_TexCoord;
        void main() {
            gl_FragColor = texture2D(u_Texture, v_TexCoord);
        }
    """

    // ─── Shader Compilation ────────────────────────────────────────────────────

    fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $type")
            return 0
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        if (vs == 0) return 0
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fs == 0) { GLES20.glDeleteShader(vs); return 0 }

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        if (status[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun checkGlError(op: String) {
        var error: Int
        while (GLES20.glGetError().also { error = it } != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
        }
    }

    // ─── Matrix Math Helpers ───────────────────────────────────────────────────

    fun perspectiveMatrix(fovY: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val m = FloatArray(16)
        Matrix.perspectiveM(m, 0, fovY, aspect, near, far)
        return m
    }

    fun lookAtMatrix(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        centerX: Float, centerY: Float, centerZ: Float,
        upX: Float, upY: Float, upZ: Float
    ): FloatArray {
        val m = FloatArray(16)
        Matrix.setLookAtM(m, 0, eyeX, eyeY, eyeZ, centerX, centerY, centerZ, upX, upY, upZ)
        return m
    }

    fun translationMatrix(tx: Float, ty: Float, tz: Float): FloatArray {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, tx, ty, tz)
        return m
    }

    fun rotationMatrix(angle: Float, x: Float, y: Float, z: Float): FloatArray {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.rotateM(m, 0, angle, x, y, z)
        return m
    }

    fun multiplyMM(a: FloatArray, b: FloatArray): FloatArray {
        val result = FloatArray(16)
        Matrix.multiplyMM(result, 0, a, 0, b, 0)
        return result
    }

    fun identityMatrix(): FloatArray {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        return m
    }

    /** Convert RSSI (-100..0) to a color (blue→cyan→green→yellow→red). */
    fun rssiToColor(rssi: Int): FloatArray {
        val norm = ((rssi + 100f) / 100f).coerceIn(0f, 1f)
        return when {
            norm < 0.25f -> floatArrayOf(0f, 0f, 1f, 1f)                             // blue (dead)
            norm < 0.5f -> floatArrayOf(0f, norm * 4f - 1f, 2f - norm * 4f, 1f)     // blue->cyan
            norm < 0.75f -> floatArrayOf(norm * 4f - 2f, 1f, 0f, 1f)                // cyan->green->yellow
            else -> floatArrayOf(1f, 4f - norm * 4f, 0f, 1f)                        // yellow->red (strong)
        }
    }
}
