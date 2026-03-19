package com.wifiradarx.app.ui.activities

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wifiradarx.app.R
import com.wifiradarx.app.ui.adapters.NetworkAdapter
import com.wifiradarx.app.ui.viewmodel.NetworkListViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkListActivity : AppCompatActivity() {

    private val vm: NetworkListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_list)
        supportActionBar?.title = "Networks"

        val rv = findViewById<RecyclerView>(R.id.rv_networks)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = NetworkAdapter()
        rv.adapter = adapter

        // Band filter tab buttons
        val tabContainer = findViewById<LinearLayout>(R.id.ll_band_tabs)
        listOf("ALL", "2.4", "5", "6").forEachIndexed { i, label ->
            val btn = Button(this).apply {
                text = label
                setBackgroundColor(if (i == 0) 0xFF00D4FF.toInt() else 0xFF1A1A2E.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { vm.setFilter(label) }
            }
            tabContainer?.addView(
                btn,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        lifecycleScope.launch {
            vm.allDbScans.collectLatest { scans ->
                val filter   = vm.filterBand.value
                val filtered = when (filter) {
                    "2.4" -> scans.filter { !it.is5GHz && !it.is6GHz }
                    "5"   -> scans.filter { it.is5GHz }
                    "6"   -> scans.filter { it.is6GHz }
                    else  -> scans
                }.distinctBy { it.bssid }
                adapter.submitList(filtered)
                findViewById<TextView>(R.id.tv_network_count)?.text =
                    "${filtered.size} networks"
            }
        }
    }
}
