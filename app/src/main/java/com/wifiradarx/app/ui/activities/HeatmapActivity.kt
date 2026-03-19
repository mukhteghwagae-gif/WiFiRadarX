package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.HeatmapViewModel
import com.wifiradarx.app.ui.views.HeatmapView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HeatmapActivity : AppCompatActivity() {

    private val vm: HeatmapViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heatmap)
        supportActionBar?.title = "Signal Heatmap"

        val heatmapView = findViewById<HeatmapView>(R.id.heatmap_view)
        val spinSession = findViewById<Spinner>(R.id.spin_session_filter)

        lifecycleScope.launch {
            vm.sessions.collectLatest { sessions ->
                val labels = listOf("All sessions") +
                        sessions.map { "Session ${it.sessionId.takeLast(6)}" }
                spinSession?.adapter = ArrayAdapter(
                    this@HeatmapActivity,
                    android.R.layout.simple_spinner_item,
                    labels
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinSession?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        vm.selectSession(
                            if (pos == 0) null else sessions.getOrNull(pos - 1)?.sessionId
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            vm.allScans.collectLatest { scans ->
                val sessionId = vm.selectedSession.value
                val filtered  = if (sessionId == null) scans
                                else scans.filter { it.sessionId == sessionId }
                heatmapView.points = filtered.map { s ->
                    HeatmapView.HeatPoint(s.posX, s.posZ, s.rssi)
                }
            }
        }
    }
}
