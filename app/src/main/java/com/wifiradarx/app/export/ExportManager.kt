package com.wifiradarx.app.export

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.wifiradarx.app.data.entity.SessionMetadata
import com.wifiradarx.app.data.entity.WifiScanResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ExportManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    private fun getOutputDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportCsv(scans: List<WifiScanResult>, session: SessionMetadata?): File {
        val ts = sdf.format(Date())
        val file = File(getOutputDir(), "wifiradarx_${ts}.csv")
        file.bufferedWriter().use { w ->
            w.write("id,session_id,ssid,bssid,rssi,frequency,channel,capabilities,vendor_oui," +
                    "timestamp_micros,timestamp,pos_x,pos_y,pos_z,security_score," +
                    "is_rogue_suspect,threat_score,is_5ghz,is_6ghz\n")
            scans.forEach { s ->
                w.write("${s.id},${s.sessionId},\"${s.ssid.csvEscape()}\",${s.bssid}," +
                        "${s.rssi},${s.frequency},${s.channel}," +
                        "\"${s.capabilities.csvEscape()}\",\"${s.vendorOui.csvEscape()}\"," +
                        "${s.timestampMicros},${s.timestamp}," +
                        "${s.posX},${s.posY},${s.posZ}," +
                        "${s.securityScore},${s.isRogueSuspect},${s.threatScore}," +
                        "${s.is5GHz},${s.is6GHz}\n")
            }
        }
        return file
    }

    data class JsonExport(
        val session: SessionMetadata?,
        val exportTime: String,
        val appVersion: String = "3.0.0",
        val scans: List<WifiScanResult>
    )

    fun exportJson(scans: List<WifiScanResult>, session: SessionMetadata?): File {
        val ts = sdf.format(Date())
        val file = File(getOutputDir(), "wifiradarx_${ts}.json")
        val payload = JsonExport(session, ts, "3.0.0", scans)
        file.writeText(gson.toJson(payload))
        return file
    }

    fun exportKml(scans: List<WifiScanResult>, session: SessionMetadata?): File {
        val ts = sdf.format(Date())
        val file = File(getOutputDir(), "wifiradarx_${ts}.kml")

        val grouped = scans.groupBy { sessionColor(it.securityScore) }
        file.bufferedWriter().use { w ->
            w.write("""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>
  <name>WiFiRadarX Export ${ts}</name>
""")
            // Styles per grade
            listOf(
                Triple("green", "SECURE", "ff00ff00"),
                Triple("yellow", "CAUTION", "ff00ffff"),
                Triple("red", "DANGER", "ff0000ff")
            ).forEach { (id, name, color) ->
                w.write("""
  <Style id="$id">
    <IconStyle><color>$color</color><scale>1.0</scale></IconStyle>
    <LabelStyle><color>$color</color></LabelStyle>
  </Style>
""")
            }

            val sessName = session?.sessionId ?: ts
            w.write("  <Folder><name>Session $sessName</name>\n")

            scans.forEach { s ->
                // Use posX/posZ as lon/lat proxy (not real geo — AR local coords)
                val style = when {
                    s.securityScore >= 70 -> "#green"
                    s.securityScore >= 45 -> "#yellow"
                    else -> "#red"
                }
                w.write("""
    <Placemark>
      <name>${s.ssid.xmlEscape()}</name>
      <description>BSSID: ${s.bssid} | RSSI: ${s.rssi} dBm | Ch: ${s.channel} | ${s.capabilities.xmlEscape()}</description>
      <styleUrl>$style</styleUrl>
      <Point><coordinates>${s.posX},${s.posZ},${s.posY}</coordinates></Point>
    </Placemark>
""")
            }
            w.write("  </Folder>\n</Document>\n</kml>")
        }
        return file
    }

    fun exportHtml(scans: List<WifiScanResult>, session: SessionMetadata?): File {
        val ts = sdf.format(Date())
        val file = File(getOutputDir(), "wifiradarx_${ts}.html")
        val rssiData = scans.map { it.rssi }
        val labels = scans.mapIndexed { i, _ -> i.toString() }
        val rssiJson = rssiData.joinToString(",")
        val labelJson = labels.joinToString(",") { "\"$it\"" }
        val networkRows = scans.joinToString("") { s ->
            val badge = when {
                s.securityScore >= 70 -> "<span style='color:#00ff88'>SECURE</span>"
                s.securityScore >= 45 -> "<span style='color:#ffcc00'>CAUTION</span>"
                else -> "<span style='color:#ff4444'>DANGER</span>"
            }
            val rogue = if (s.isRogueSuspect) "<span style='color:#ff4444'>⚠ ROGUE</span>" else ""
            "<tr><td>${s.ssid.htmlEscape()}</td><td>${s.bssid}</td><td>${s.rssi}</td>" +
                    "<td>${s.channel}</td><td>${s.vendorOui.ifBlank { "Unknown" }}</td>" +
                    "<td>$badge</td><td>$rogue</td></tr>"
        }
        val html = """<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>WiFiRadarX Report - $ts</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{background:#07090f;color:#e0e0e0;font-family:'Segoe UI',sans-serif;padding:20px}
h1{color:#00D4FF;margin-bottom:10px}h2{color:#7C3AED;margin:20px 0 10px}
.card{background:#0d1117;border:1px solid #1e2a38;border-radius:8px;padding:16px;margin-bottom:16px}
table{width:100%;border-collapse:collapse}
th{background:#0a0f1a;color:#00D4FF;padding:8px;text-align:left;border-bottom:2px solid #1e2a38}
td{padding:8px;border-bottom:1px solid #1a2030}
tr:hover{background:#0f1626}
.canvas-wrap{position:relative;height:300px}
</style>
</head><body>
<h1>📡 WiFiRadarX Report</h1>
<div class="card">
  <p>Generated: $ts | Session: ${session?.sessionId ?: "N/A"} | Networks: ${scans.size}</p>
</div>
<div class="card">
  <h2>RSSI Over Scan Sequence</h2>
  <div class="canvas-wrap"><canvas id="rssiChart"></canvas></div>
</div>
<div class="card">
  <h2>Network Details</h2>
  <table>
    <tr><th>SSID</th><th>BSSID</th><th>RSSI</th><th>Ch</th><th>Vendor</th><th>Security</th><th>Threat</th></tr>
    $networkRows
  </table>
</div>
<script>
// Inline Chart.js (minimal CDN-free fallback — draws a basic line chart via Canvas 2D)
(function(){
  var canvas=document.getElementById('rssiChart');
  if(!canvas)return;
  canvas.width=canvas.parentElement.clientWidth||800;
  canvas.height=280;
  var ctx=canvas.getContext('2d');
  var data=[$rssiJson];
  var labels=[$labelJson];
  if(!data.length)return;
  var min=Math.min.apply(null,data),max=Math.max.apply(null,data);
  var W=canvas.width,H=canvas.height,pad=40;
  ctx.fillStyle='#07090f';ctx.fillRect(0,0,W,H);
  ctx.strokeStyle='#1e2a38';ctx.lineWidth=1;
  for(var i=0;i<=4;i++){
    var y=pad+(H-2*pad)*i/4;
    ctx.beginPath();ctx.moveTo(pad,y);ctx.lineTo(W-pad,y);ctx.stroke();
    ctx.fillStyle='#888';ctx.font='12px sans-serif';
    ctx.fillText((max-(max-min)*i/4).toFixed(0)+'dBm',2,y+4);
  }
  ctx.strokeStyle='#00D4FF';ctx.lineWidth=2;ctx.beginPath();
  data.forEach(function(v,i){
    var x=pad+(W-2*pad)*i/(data.length-1||1);
    var y=pad+(H-2*pad)*(max-v)/(max-min||1);
    if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
  });
  ctx.stroke();
})();
</script>
</body></html>"""
        file.writeText(html)
        return file
    }

    fun exportSvg(scans: List<WifiScanResult>): File {
        val ts = sdf.format(Date())
        val file = File(getOutputDir(), "wifiradarx_floorplan_${ts}.svg")

        if (scans.isEmpty()) { file.writeText("<svg/>"); return file }

        val xs = scans.map { it.posX }
        val zs = scans.map { it.posZ }
        val xMin = xs.minOrNull() ?: 0f; val xMax = xs.maxOrNull() ?: 1f
        val zMin = zs.minOrNull() ?: 0f; val zMax = zs.maxOrNull() ?: 1f
        val W = 800f; val H = 600f; val pad = 40f
        fun mx(x: Float) = pad + (x - xMin) / (xMax - xMin + 0.001f) * (W - 2 * pad)
        fun my(z: Float) = pad + (z - zMin) / (zMax - zMin + 0.001f) * (H - 2 * pad)

        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" width="$W" height="$H" style="background:#07090f">""")
        sb.append("<title>WiFiRadarX Floor Plan</title>")

        scans.forEach { s ->
            val cx = mx(s.posX); val cy = my(s.posZ)
            val norm = ((s.rssi + 100f) / 100f).coerceIn(0f, 1f)
            val r = (255 * (1 - norm)).toInt(); val g = (255 * norm).toInt()
            val color = "rgb($r,$g,50)"
            val radius = 8 + norm * 12
            sb.append("""<circle cx="$cx" cy="$cy" r="$radius" fill="$color" fill-opacity="0.6" stroke="#fff" stroke-width="0.5"/>""")
            sb.append("""<text x="${cx + radius + 2}" y="${cy + 4}" font-size="9" fill="#aaa">${s.ssid.xmlEscape()}</text>""")
        }
        sb.append("</svg>")
        file.writeText(sb.toString())
        return file
    }

    private fun sessionColor(score: Int) = when {
        score >= 70 -> "green"
        score >= 45 -> "yellow"
        else -> "red"
    }

    private fun String.csvEscape() = replace("\"", "\"\"")
    private fun String.xmlEscape() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun String.htmlEscape() = xmlEscape().replace("\"", "&quot;")
}
