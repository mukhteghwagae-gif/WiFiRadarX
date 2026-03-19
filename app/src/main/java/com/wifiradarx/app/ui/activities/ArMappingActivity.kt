package com.wifiradarx.app.ui.activities

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.wifiradarx.app.R
import com.wifiradarx.app.ar.ArRenderer
import com.wifiradarx.app.ar.DisplayRotationHelper
import com.wifiradarx.app.ar.ThreatBlip
import com.wifiradarx.app.ar.WifiBlip
import com.wifiradarx.app.intelligence.ChannelAnalyzer
import com.wifiradarx.app.intelligence.IdwInterpolator
import com.wifiradarx.app.ui.viewmodel.ArViewModel
import kotlinx.coroutines.launch

class ArMappingActivity : AppCompatActivity() {

    private val vm: ArViewModel by viewModels()
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: ArRenderer
    private lateinit var rotationHelper: DisplayRotationHelper
    private var arSession: Session? = null
    private var arSupported = false

    // Current AR camera pose
    private var currentPosX = 0f
    private var currentPosY = 0f
    private var currentPosZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_mapping)

        glView = findViewById(R.id.gl_surface_view)
        renderer = ArRenderer(this)
        rotationHelper = DisplayRotationHelper(this)
        renderer.setDisplayRotationHelper(rotationHelper)

        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        setupArCore()
        setupControls()
        observeViewModel()
    }

    private fun setupArCore() {
        arSupported = try {
            when (ArCoreApk.getInstance().checkAvailability(this)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> true
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    try {
                        ArCoreApk.getInstance().requestInstall(this, true)
                        true
                    } catch (e: UnavailableUserDeclinedInstallationException) { false }
                }
                else -> false
            }
        } catch (e: Exception) { false }

        if (arSupported) {
            try {
                arSession = Session(this)
                val config = Config(arSession).apply {
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
                arSession?.configure(config)
                renderer.setSession(arSession!!)
            } catch (e: Exception) {
                showArUnsupported()
            }
        } else {
            showArUnsupported()
        }
    }

    private fun showArUnsupported() {
        findViewById<View>(R.id.tv_ar_fallback)?.visibility = View.VISIBLE
        glView.visibility = View.GONE
    }

    private fun setupControls() {
        // Scan button
        findViewById<Button>(R.id.btn_ar_scan)?.setOnClickListener {
            vm.saveScan(currentPosX, currentPosY, currentPosZ)
            Toast.makeText(this, "Scan captured at (${currentPosX.format(1)}, ${currentPosZ.format(1)})", Toast.LENGTH_SHORT).show()
        }

        // Predict mode toggle
        findViewById<ToggleButton>(R.id.toggle_predict)?.setOnCheckedChangeListener { _, checked ->
            vm.togglePredictMode()
            renderer.enableHeatmap = checked
        }

        // Heatmap toggle
        findViewById<CheckBox>(R.id.cb_heatmap)?.apply {
            isChecked = true
            setOnCheckedChangeListener { _, checked -> renderer.enableHeatmap = checked }
        }

        // Voxel toggle
        findViewById<CheckBox>(R.id.cb_voxels)?.setOnCheckedChangeListener { _, checked ->
            renderer.enableVoxels = checked
        }

        // Arrow toggle
        findViewById<CheckBox>(R.id.cb_arrow)?.apply {
            isChecked = true
            setOnCheckedChangeListener { _, checked -> renderer.enableArrow = checked }
        }

        // Height slice
        findViewById<SeekBar>(R.id.seek_height_slice)?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, user: Boolean) {
                    renderer.ySlice = p / 100f * 3f - 1.5f
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            }
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            vm.scanResults.collect { results ->
                val blips = results.mapIndexed { i, sr ->
                    WifiBlip(
                        x = currentPosX + (i % 3) * 0.3f - 0.3f,
                        y = currentPosY,
                        z = currentPosZ - 0.5f - i * 0.1f,
                        rssi = sr.level,
                        ssid = sr.SSID ?: "",
                        bssid = sr.BSSID ?: ""
                    )
                }
                renderer.blips = blips

                // Feed IDW
                val samples = blips.map {
                    IdwInterpolator.Sample(it.x, it.z, it.y, it.rssi.toFloat())
                }
                renderer.idwSamples = samples
            }
        }
        lifecycleScope.launch {
            vm.rogues.collect { rogues ->
                renderer.threats = rogues.map { r ->
                    ThreatBlip(r.posX, r.posY, r.posZ, r.ssid)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationHelper.onResume()
        try { arSession?.resume() } catch (_: Exception) {}
        glView.onResume()
    }

    override fun onPause() {
        super.onPause()
        glView.onPause()
        rotationHelper.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        arSession?.close()
        arSession = null
    }

    private fun Float.format(d: Int) = "%.${d}f".format(this)
}
