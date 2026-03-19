package com.wifiradarx.app.ui.activities

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.wifiradarx.app.ar.renderers.ArrowRenderer
import com.wifiradarx.app.ar.renderers.HeatmapMeshRenderer
import com.wifiradarx.app.ar.renderers.PillarRenderer
import com.wifiradarx.app.databinding.ActivityArMappingBinding
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArMappingActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private lateinit var binding: ActivityArMappingBinding
    private var arSession: Session? = null
    private val pillarRenderer = PillarRenderer()
    private val heatmapRenderer = HeatmapMeshRenderer()
    private val arrowRenderer = ArrowRenderer()
    private var showHeatmap = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = "AR Mapping"

        binding.surfaceView.apply {
            setEGLContextClientVersion(2)
            setRenderer(this@ArMappingActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        binding.scanButton.setOnClickListener {
            Toast.makeText(this, "Scan triggered", Toast.LENGTH_SHORT).show()
        }
        binding.predictToggle.setOnCheckedChangeListener { _, checked ->
            showHeatmap = checked
        }
    }

    override fun onResume() {
        super.onResume()
        if (arSession == null) {
            try {
                arSession = Session(this).also { s ->
                    s.configure(Config(s).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    })
                }
            } catch (e: Exception) {
                // ARCore unavailable on this device — fallback gracefully
            }
        }
        arSession?.resume()
        binding.surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.surfaceView.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
    }

    // ── GLSurfaceView.Renderer ────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.07f, 0.09f, 0.15f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        pillarRenderer.init()
        heatmapRenderer.init()
        arrowRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arSession?.setDisplayGeometry(windowManager.defaultDisplay.rotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arSession ?: return
        val frame = try { session.update() } catch (e: Exception) { return }
        val camera = frame.camera

        val proj = FloatArray(16)
        camera.getProjectionMatrix(proj, 0, 0.1f, 100.0f)
        val view = FloatArray(16)
        camera.getViewMatrix(view, 0)

        pillarRenderer.draw(view, proj)
        if (showHeatmap) heatmapRenderer.draw(view, proj)
        arrowRenderer.draw(view, proj)
    }
}
