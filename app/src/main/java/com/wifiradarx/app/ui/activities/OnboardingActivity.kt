package com.wifiradarx.app.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.utils.AppSettings
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private var currentStep = 0

    private val steps = listOf(
        Triple(R.drawable.ic_wifi_scan, "Welcome to WiFiRadarX",
            "Visualise, analyse, and secure your wireless environment in real time."),
        Triple(R.drawable.ic_ar_view, "AR Signal Mapping",
            "Walk around your space and watch 3-D signal pillars grow in augmented reality. IDW heatmaps reveal coverage gaps instantly."),
        Triple(R.drawable.ic_security, "Threat Detection",
            "Rogue AP scoring, channel congestion analysis, and EMF fingerprinting keep you informed and protected.")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            AppSettings.getSettingsFlow(this@OnboardingActivity).collect { s ->
                if (!s.firstLaunch) {
                    goToMain()
                    return@collect
                }
            }
        }

        setContentView(R.layout.activity_onboarding)
        updateStep()

        findViewById<Button>(R.id.btn_next).setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateStep()
            } else {
                lifecycleScope.launch {
                    AppSettings.setFirstLaunch(this@OnboardingActivity, false)
                    goToMain()
                }
            }
        }

        findViewById<Button>(R.id.btn_skip).setOnClickListener {
            lifecycleScope.launch {
                AppSettings.setFirstLaunch(this@OnboardingActivity, false)
                goToMain()
            }
        }
    }

    private fun updateStep() {
        val (iconRes, title, desc) = steps[currentStep]
        findViewById<ImageView>(R.id.iv_onboard_icon).setImageResource(iconRes)
        findViewById<TextView>(R.id.tv_onboard_title).text = title
        findViewById<TextView>(R.id.tv_onboard_desc).text = desc

        val btn = findViewById<Button>(R.id.btn_next)
        btn.text = if (currentStep == steps.size - 1) "Get Started" else "Next"

        // Dot indicators
        val dots = listOf(R.id.dot1, R.id.dot2, R.id.dot3)
        dots.forEachIndexed { i, id ->
            findViewById<View>(id).alpha = if (i == currentStep) 1f else 0.3f
        }

        // Hide skip on last
        findViewById<Button>(R.id.btn_skip).visibility =
            if (currentStep == steps.size - 1) View.INVISIBLE else View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
