package com.example.chargingmonitor

import android.app.Activity
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.view.Gravity
import android.widget.*
import java.io.File
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var tvWatt: TextView
    private lateinit var tvBadge: TextView
    private lateinit var tvDetails: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val updater = object : Runnable {
        override fun run() {
            updateBatteryInfo()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply {
            text = "⚡ CHARGING MONITOR"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        tvWatt = TextView(this).apply {
            text = "0.00 W"
            textSize = 52f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }
        tvBadge = TextView(this).apply {
            text = "MEMUAT..."
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#00E676"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        tvDetails = TextView(this).apply {
            text = "Memuat sensor..."
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setLineSpacing(14f, 1f)
        }
        layout.addView(title)
        layout.addView(tvWatt)
        layout.addView(tvBadge)
        layout.addView(tvDetails)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        handler.post(updater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updater)
    }

    private fun getCycleCount(intent: Intent?): String {
        if (Build.VERSION.SDK_INT >= 34) {
            val cycles = intent?.getIntExtra("android.os.extra.CYCLE_COUNT", -1) ?: -1
            if (cycles >= 0) return "$cycles kali"
        }
        val paths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count"
        )
        for (path in paths) {
            try {
                val valStr = File(path).readText().trim()
                if (valStr.isNotEmpty() && valStr.toIntOrNull() != null) {
                    return "$valStr kali"
                }
            } catch (_: Exception) {}
        }
        return "Tidak didukung perangkat"
    }

    private fun updateBatteryInfo() {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val voltMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

        val currMicro = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val volt = voltMv / 1000.0
        val amp = abs(currMicro) / 1_000_000.0
        val watt = volt * amp
        val temp = tempTenths / 10.0

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            else -> "Not Charging"
        }
        val sourceStr = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Baterai"
        }
        val speedBadge = when {
            !isCharging -> "MODE BATERAI ($statusStr)"
            watt >= 25.0 -> "⚡⚡ SUPER / TURBO CHARGE"
            watt >= 14.0 -> "⚡ QUICK / FAST CHARGE"
            else -> "NORMAL CHARGING"
        }

        val cycleStr = getCycleCount(intent)

        tvWatt.text = String.format("%.2f W", watt)
        tvWatt.setTextColor(if (isCharging) Color.parseColor("#00E676") else Color.parseColor("#FFAB40"))
        tvBadge.text = speedBadge
        tvBadge.setTextColor(if (isCharging) Color.parseColor("#00E676") else Color.parseColor("#AAAAAA"))

        tvDetails.text = """
            Tegangan : ${String.format("%.2f", volt)} V
            Arus     : ${String.format("%.2f", amp)} A
            Suhu     : ${String.format("%.1f", temp)} °C
            Baterai  : $level% ($statusStr)
            Sumber   : $sourceStr
            Cycles   : $cycleStr
        """.trimIndent()
    }
}
