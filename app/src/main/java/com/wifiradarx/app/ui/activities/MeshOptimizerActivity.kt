package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.viewmodel.MainViewModel
import com.wifiradarx.app.ui.viewmodel.MeshViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MeshOptimizerActivity : AppCompatActivity() {

    private val vm     : MeshViewModel by viewModels()
    private val mainVm : MainViewModel  by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mesh_optimizer)
        supportActionBar?.title = "Mesh Optimizer"

        val tvZones = findViewById<TextView>(R.id.tv_dead_zones)
        val tvRecs  = findViewById<TextView>(R.id.tv_ap_recommendations)

        lifecycleScope.launch {
            mainVm.allScans.collectLatest { scans -> vm.compute(scans) }
        }

        lifecycleScope.launch {
            vm.deadZones.collect { zones ->
                tvZones?.text = if (zones.isEmpty()) "No dead zones detected"
                else zones.joinToString("\n") { z ->
                    "${z.severity.name}: ${z.areaCells} cells at " +
                    "(${"%.1f".format(z.centroidX)}, ${"%.1f".format(z.centroidY)}) " +
                    "avg ${z.avgSignal.toInt()} dBm"
                }
            }
        }

        lifecycleScope.launch {
            vm.apRecs.collect { recs ->
                tvRecs?.text = if (recs.isEmpty()) "Scan more area for recommendations"
                else recs.joinToString("\n") { r ->
                    "#${r.priority}: Place AP at " +
                    "(${"%.1f".format(r.x)}, ${"%.1f".format(r.y)}) — ${r.reason}"
                }
            }
        }
    }
}
