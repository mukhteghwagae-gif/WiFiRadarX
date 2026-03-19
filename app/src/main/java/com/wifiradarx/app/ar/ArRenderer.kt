package com.wifiradarx.app.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.*
import com.wifiradarx.app.intelligence.IdwInterpolator
import com.wifiradarx.app.intelligence.WallMaterialInferencer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class WifiBlip(
    val x: Float, val y: Float, val z: Float,
    val rssi: Int, val ssid: String, val bssid: String
)

data class ThreatBlip(val x: Float, val y: Float, val z: Float, val ssid: String)

class ArRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var session: Session? = null
    private var displayRotationHelper: DisplayRotationHelper? = null

    // Programs
    private var backgroundProgram = 0
    private var pillarProgram = 0
    private var heatmapProgram = 0
    private var voxelProgram = 0
    private var arrowProgram = 0
    private var threatProgram = 0

    // ARCore camera texture
    private val cameraTextureId = IntArray(1)
    private var cameraTexture = 0

    // Screen geometry
    private var screenWidth = 1
    private var screenHeight = 1

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Data
    @Volatile var blips: List<WifiBlip> = emptyList()
    @Volatile var threats: List<ThreatBlip> = emptyList()
    @Volatile var idwSamples: List<IdwInterpolator.Sample> = emptyList()
    @Volatile var voxelSlice: List<IdwInterpolator.VoxelCell> = emptyList()
    @Volatile var enableHeatmap = true
    @Volatile var enableVoxels = false
    @Volatile var enableArrow = true
    @Volatile var enableThreat = true
    @Volatile var ySlice = 0f

    private var startTimeMs = System.currentTimeMillis()

    fun setSession(s: Session) { session = s }
    fun setDisplayRotationHelper(h: DisplayRotationHelper) { displayRotationHelper = h }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Camera background texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTexture = textures[0]

        backgroundProgram = ShaderUtils.createProgram(
            ShaderUtils.BACKGROUND_VERTEX_SHADER,
            ShaderUtils.BACKGROUND_FRAGMENT_SHADER
        )
        pillarProgram = ShaderUtils.createProgram(
            ShaderUtils.PILLAR_VERTEX_SHADER,
            ShaderUtils.PILLAR_FRAGMENT_SHADER
        )
        heatmapProgram = ShaderUtils.createProgram(
            ShaderUtils.HEATMAP_MESH_VERTEX_SHADER,
            ShaderUtils.HEATMAP_MESH_FRAGMENT_SHADER
        )
        voxelProgram = ShaderUtils.createProgram(
            ShaderUtils.VOXEL_VERTEX_SHADER,
            ShaderUtils.VOXEL_FRAGMENT_SHADER
        )
        arrowProgram = ShaderUtils.createProgram(
            ShaderUtils.DIRECTION_ARROW_VERTEX_SHADER,
            ShaderUtils.DIRECTION_ARROW_FRAGMENT_SHADER
        )
        threatProgram = ShaderUtils.createProgram(
            ShaderUtils.THREAT_ALERT_VERTEX_SHADER,
            ShaderUtils.THREAT_ALERT_FRAGMENT_SHADER
        )
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenWidth = width; screenHeight = height
        displayRotationHelper?.onDisplayChanged()
        Matrix.perspectiveM(projMatrix, 0, 67f, width.toFloat() / height.toFloat(), 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val sess = session ?: return
        val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000f

        try {
            sess.setCameraTextureName(cameraTexture)
            val frame = sess.update()
            val camera = frame.camera

            if (camera.trackingState != TrackingState.TRACKING) return

            camera.getViewMatrix(viewMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

            drawBackground(frame)

            val currentBlips = blips
            if (currentBlips.isNotEmpty()) {
                drawPillars(currentBlips, elapsedSec)
                if (enableArrow) drawDirectionArrow(currentBlips, elapsedSec)
            }
            if (enableHeatmap) drawHeatmapMesh()
            if (enableVoxels) drawVoxels(elapsedSec)
            if (enableThreat) drawThreats(elapsedSec)

        } catch (e: Exception) {
            Log.e("ArRenderer", "Error in onDrawFrame", e)
        }
    }

    private fun drawBackground(frame: Frame) {
        if (backgroundProgram == 0) return
        // Simple quad covering screen
        val quadVertices = floatArrayOf(
            -1f, -1f, 0f, 1f,
             1f, -1f, 1f, 1f,
            -1f,  1f, 0f, 0f,
             1f,  1f, 1f, 0f
        )
        val buf = createFloatBuffer(quadVertices)
        GLES20.glUseProgram(backgroundProgram)
        val posLoc = GLES20.glGetAttribLocation(backgroundProgram, "a_Position")
        val texLoc = GLES20.glGetAttribLocation(backgroundProgram, "a_TexCoord")
        buf.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        buf.position(2)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, buf)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glDepthMask(false)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDepthMask(true)
    }

    private fun drawPillars(blips: List<WifiBlip>, time: Float) {
        if (pillarProgram == 0) return
        GLES20.glUseProgram(pillarProgram)
        val mvpLoc = GLES20.glGetUniformLocation(pillarProgram, "u_MVP")
        val pulseLoc = GLES20.glGetUniformLocation(pillarProgram, "u_Pulse")
        val posLoc = GLES20.glGetAttribLocation(pillarProgram, "a_Position")
        val colorLoc = GLES20.glGetAttribLocation(pillarProgram, "a_Color")

        val pulse = (sin(time * 3f) * 0.5f + 0.5f)
        GLES20.glUniform1f(pulseLoc, pulse)

        for (blip in blips) {
            val height = ((blip.rssi + 100f) / 100f).coerceIn(0.05f, 1f) * 0.8f
            val color = ShaderUtils.rssiToColor(blip.rssi)

            // Pillar: 4 vertices from (x,0,z) to (x,height,z)
            val verts = floatArrayOf(
                blip.x - 0.05f, 0f, blip.z,
                blip.x + 0.05f, 0f, blip.z,
                blip.x - 0.05f, height, blip.z,
                blip.x + 0.05f, height, blip.z
            )
            val colors = buildColorArray(color, 4)
            val model = ShaderUtils.translationMatrix(0f, 0f, 0f)
            val mvp = ShaderUtils.multiplyMM(mvpMatrix, model)
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)

            val vBuf = createFloatBuffer(verts)
            val cBuf = createFloatBuffer(colors)
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vBuf)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, 0, cBuf)
            GLES20.glEnableVertexAttribArray(colorLoc)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawHeatmapMesh() {
        val samples = idwSamples
        if (heatmapProgram == 0 || samples.size < 3) return
        GLES20.glUseProgram(heatmapProgram)
        val mvpLoc = GLES20.glGetUniformLocation(heatmapProgram, "u_MVP")
        val posLoc = GLES20.glGetAttribLocation(heatmapProgram, "a_Position")
        val colorLoc = GLES20.glGetAttribLocation(heatmapProgram, "a_Color")
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)

        val verts = mutableListOf<Float>()
        val colors = mutableListOf<Float>()
        for (s in samples) {
            val c = ShaderUtils.rssiToColor(s.value.toInt())
            verts.addAll(listOf(s.x, 0f, s.y))
            colors.addAll(c.toList())
        }
        val vBuf = createFloatBuffer(verts.toFloatArray())
        val cBuf = createFloatBuffer(colors.toFloatArray())
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, 0, cBuf)
        GLES20.glEnableVertexAttribArray(colorLoc)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, samples.size)
    }

    private fun drawVoxels(time: Float) {
        val voxels = voxelSlice
        if (voxelProgram == 0 || voxels.isEmpty()) return
        GLES20.glUseProgram(voxelProgram)
        val mvpLoc = GLES20.glGetUniformLocation(voxelProgram, "u_MVP")
        val posLoc = GLES20.glGetAttribLocation(voxelProgram, "a_Position")
        val colorLoc = GLES20.glGetAttribLocation(voxelProgram, "a_Color")
        val sizeLoc = GLES20.glGetAttribLocation(voxelProgram, "a_Size")
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)

        for (v in voxels) {
            val c = ShaderUtils.rssiToColor(v.value.toInt())
            val verts = floatArrayOf(v.x, v.y, v.z)
            val sz = floatArrayOf(18f + (v.value + 100f) / 100f * 12f)
            val vBuf = createFloatBuffer(verts)
            val cBuf = createFloatBuffer(c)
            val sBuf = createFloatBuffer(sz)
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vBuf)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, 0, cBuf)
            GLES20.glEnableVertexAttribArray(colorLoc)
            GLES20.glVertexAttribPointer(sizeLoc, 1, GLES20.GL_FLOAT, false, 0, sBuf)
            GLES20.glEnableVertexAttribArray(sizeLoc)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        }
    }

    private fun drawDirectionArrow(blips: List<WifiBlip>, time: Float) {
        if (arrowProgram == 0) return
        val strongest = blips.maxByOrNull { it.rssi } ?: return
        GLES20.glUseProgram(arrowProgram)
        val mvpLoc = GLES20.glGetUniformLocation(arrowProgram, "u_MVP")
        val timeLoc = GLES20.glGetUniformLocation(arrowProgram, "u_Time")
        val colorLoc = GLES20.glGetUniformLocation(arrowProgram, "u_Color")
        val posLoc = GLES20.glGetAttribLocation(arrowProgram, "a_Position")

        val angle = atan2(strongest.x.toDouble(), strongest.z.toDouble()).toFloat()
        val model = ShaderUtils.rotationMatrix(
            Math.toDegrees(angle.toDouble()).toFloat(), 0f, 1f, 0f
        )
        val mvp = ShaderUtils.multiplyMM(mvpMatrix, model)
        GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
        GLES20.glUniform1f(timeLoc, time)
        GLES20.glUniform3f(colorLoc, 0f, 0.83f, 1f)

        // Arrow geometry: shaft + head
        val arrowVerts = floatArrayOf(
            0f, 0f, 0f,
            0f, 0f, -0.5f,
            -0.05f, 0f, -0.4f,
            0f, 0f, -0.5f,
            0.05f, 0f, -0.4f
        )
        val buf = createFloatBuffer(arrowVerts)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 5)
    }

    private fun drawThreats(time: Float) {
        val thrList = threats
        if (threatProgram == 0 || thrList.isEmpty()) return
        GLES20.glUseProgram(threatProgram)
        val mvpLoc = GLES20.glGetUniformLocation(threatProgram, "u_MVP")
        val timeLoc = GLES20.glGetUniformLocation(threatProgram, "u_Time")
        val posLoc = GLES20.glGetAttribLocation(threatProgram, "a_Position")
        GLES20.glUniform1f(timeLoc, time)

        for (t in thrList) {
            val model = ShaderUtils.translationMatrix(t.x, t.y, t.z)
            val mvp = ShaderUtils.multiplyMM(mvpMatrix, model)
            GLES20.glUniformMatrix4fv(mvpLoc, 1, false, mvp, 0)
            val verts = floatArrayOf(0f, 0f, 0f)
            val buf = createFloatBuffer(verts)
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        }
    }

    private fun buildColorArray(color: FloatArray, count: Int): FloatArray {
        val arr = FloatArray(count * 4)
        for (i in 0 until count) {
            arr[i * 4] = color[0]; arr[i * 4 + 1] = color[1]
            arr[i * 4 + 2] = color[2]; arr[i * 4 + 3] = color[3]
        }
        return arr
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        return buf
    }
}
