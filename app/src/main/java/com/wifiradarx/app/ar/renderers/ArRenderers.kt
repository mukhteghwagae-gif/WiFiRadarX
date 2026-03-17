package com.wifiradarx.app.ar.renderers

import android.opengl.GLES20
import android.opengl.Matrix
import com.wifiradarx.app.ar.shaders.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// ── Base ──────────────────────────────────────────────────────────────────────

abstract class BaseRenderer {
    protected var program = 0
    protected var mvpHandle = 0
    protected var posHandle = 0
    protected var colorHandle = 0

    abstract fun init()
    abstract fun draw(view: FloatArray, proj: FloatArray)

    protected fun allocateFloatBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
}

// ── Pillar Renderer ───────────────────────────────────────────────────────────

class PillarRenderer : BaseRenderer() {

    data class Pillar(val x: Float, val y: Float = 0f, val z: Float, val rssi: Int, val label: String = "")

    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var drawCount = 0

    private val pillars = mutableListOf<Pillar>()

    override fun init() {
        program = ShaderUtils.createProgram(ShaderUtils.PILLAR_VERTEX, ShaderUtils.PILLAR_FRAGMENT)
        mvpHandle   = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        posHandle   = GLES20.glGetAttribLocation(program, "a_Position")
        colorHandle = GLES20.glGetAttribLocation(program, "a_Color")
    }

    fun updatePillars(newPillars: List<Pillar>) {
        pillars.clear(); pillars.addAll(newPillars)
        if (pillars.isEmpty()) return

        // Each pillar = 4 triangles forming a thin rectangular prism (8 vertices → 12 triangles → 36 verts)
        val vertList = mutableListOf<Float>()
        val colList  = mutableListOf<Float>()

        for (p in pillars) {
            val h = ((p.rssi + 100).coerceAtLeast(0) / 50f).coerceIn(0.05f, 3f)
            val hw = 0.05f // half-width
            val t  = ((p.rssi + 100f) / 70f).coerceIn(0f, 1f)
            val r = t; val g = 1f - t; val b = 0f; val a = 0.8f

            // Front face
            addQuad(vertList, colList, p.x - hw, 0f, p.z + hw, p.x + hw, h, p.z + hw, r, g, b, a)
            // Back face
            addQuad(vertList, colList, p.x + hw, 0f, p.z - hw, p.x - hw, h, p.z - hw, r, g, b, a)
            // Left face
            addQuad(vertList, colList, p.x - hw, 0f, p.z - hw, p.x - hw, h, p.z + hw, r, g, b, a)
            // Right face
            addQuad(vertList, colList, p.x + hw, 0f, p.z + hw, p.x + hw, h, p.z - hw, r, g, b, a)
        }

        drawCount = vertList.size / 3
        vertexBuffer = allocateFloatBuffer(vertList.toFloatArray())
        colorBuffer  = allocateFloatBuffer(colList.toFloatArray())
    }

    private fun addQuad(
        verts: MutableList<Float>, cols: MutableList<Float>,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        // Two triangles sharing the diagonal
        val points = arrayOf(
            floatArrayOf(x1, y1, z1), floatArrayOf(x2, y1, z2), floatArrayOf(x2, y2, z2),
            floatArrayOf(x1, y1, z1), floatArrayOf(x2, y2, z2), floatArrayOf(x1, y2, z1)
        )
        for (p in points) { verts += p[0]; verts += p[1]; verts += p[2]; cols += r; cols += g; cols += b; cols += a }
    }

    override fun draw(view: FloatArray, proj: FloatArray) {
        val vb = vertexBuffer ?: return
        val cb = colorBuffer  ?: return
        if (drawCount == 0) return

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val model = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val mvp   = FloatArray(16)
        val mv    = FloatArray(16)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        vb.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vb)

        cb.position(0)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, cb)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, drawCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}

// ── Arrow Renderer ────────────────────────────────────────────────────────────

class ArrowRenderer : BaseRenderer() {

    // Flat arrow pointing +Y in model space
    private val arrowVerts = floatArrayOf(
         0.00f,  0.30f, 0f,
        -0.10f,  0.00f, 0f,
        -0.04f,  0.00f, 0f,
        -0.04f, -0.20f, 0f,
         0.04f, -0.20f, 0f,
         0.04f,  0.00f, 0f,
         0.10f,  0.00f, 0f
    )
    // Triangle fan indices (simplified to triangle strip of the arrow shape)
    private val indices = shortArrayOf(0,1,2, 0,2,5, 0,5,6, 2,3,4, 2,4,5)

    private var vb: FloatBuffer? = null

    override fun init() {
        program = ShaderUtils.createProgram(ShaderUtils.ARROW_VERTEX, ShaderUtils.ARROW_FRAGMENT)
        mvpHandle   = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        posHandle   = GLES20.glGetAttribLocation(program, "a_Position")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
        vb = allocateFloatBuffer(arrowVerts)
    }

    fun drawArrow(
        view: FloatArray, proj: FloatArray,
        x: Float, y: Float, z: Float,
        rotY: Float,
        color: FloatArray
    ) {
        val buf = vb ?: return
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val model = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f)

        val mv = FloatArray(16); val mvp = FloatArray(16)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)

        buf.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, arrowVerts.size / 3)
        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    override fun draw(view: FloatArray, proj: FloatArray) {
        drawArrow(view, proj, 0f, 0f, -1f, 0f, floatArrayOf(0f, 1f, 0.5f, 1f))
    }
}
