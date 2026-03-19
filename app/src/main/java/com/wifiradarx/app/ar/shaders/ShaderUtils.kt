package com.wifiradarx.app.ar.shaders

import android.opengl.GLES20
import android.util.Log

object ShaderUtils {
    private const val TAG = "ShaderUtils"

    // ── Pillar shaders ────────────────────────────────────────────────────────
    const val PILLAR_VERTEX = """
        uniform mat4 u_ModelViewProjection;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        varying vec4 v_Color;
        void main() {
            gl_Position = u_ModelViewProjection * a_Position;
            v_Color = a_Color;
        }
    """
    const val PILLAR_FRAGMENT = """
        precision mediump float;
        varying vec4 v_Color;
        void main() {
            gl_FragColor = v_Color;
        }
    """

    // ── Heatmap shaders ───────────────────────────────────────────────────────
    const val HEATMAP_VERTEX = """
        uniform mat4 u_ModelViewProjection;
        attribute vec4 a_Position;
        attribute float a_Rssi;
        varying vec4 v_Color;
        void main() {
            gl_Position = u_ModelViewProjection * a_Position;
            float t = clamp((a_Rssi + 100.0) / 70.0, 0.0, 1.0);
            v_Color = vec4(t, 1.0 - abs(t - 0.5) * 2.0, 1.0 - t, 0.6);
        }
    """
    const val HEATMAP_FRAGMENT = """
        precision mediump float;
        varying vec4 v_Color;
        void main() {
            gl_FragColor = v_Color;
        }
    """

    // ── Arrow shaders ─────────────────────────────────────────────────────────
    const val ARROW_VERTEX = """
        uniform mat4 u_ModelViewProjection;
        attribute vec4 a_Position;
        void main() {
            gl_Position = u_ModelViewProjection * a_Position;
        }
    """
    const val ARROW_FRAGMENT = """
        precision mediump float;
        uniform vec4 u_Color;
        void main() {
            gl_FragColor = u_Color;
        }
    """

    // ── Pillar with gradient ──────────────────────────────────────────────────
    const val SIGNAL_PILLAR_VERTEX = """
        uniform mat4 u_ModelViewProjection;
        uniform float u_SignalStrength;
        attribute vec4 a_Position;
        varying vec4 v_Color;
        void main() {
            gl_Position = u_ModelViewProjection * a_Position;
            float t = clamp((u_SignalStrength + 100.0) / 70.0, 0.0, 1.0);
            float alpha = 0.4 + a_Position.y * 0.4;
            v_Color = vec4(t, 1.0 - t, 0.0, alpha);
        }
    """

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
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

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) Log.e(TAG, "$op: glError $error")
    }
}
