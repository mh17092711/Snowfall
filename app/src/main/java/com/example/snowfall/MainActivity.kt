package com.example.snowfall

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()

        statusText = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }

        toggleButton = Button(this).apply {
            textSize = 18f
            setOnClickListener { onToggleClicked() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
            addView(
                statusText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pad }
            )
            addView(
                toggleButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(root)

        requestNotificationPermissionIfNeeded()

        // Ilova ochilganda ruxsatni tekshirish
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun onToggleClicked() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Avval \"Boshqa ilovalar ustida ko'rsatish\" ruxsatini bering",
                Toast.LENGTH_LONG
            ).show()
            openOverlaySettings()
            return
        }

        if (SnowfallService.isRunning) {
            stopService(Intent(this, SnowfallService::class.java))
            SnowfallService.isRunning = false
        } else {
            val intent = Intent(this, SnowfallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            SnowfallService.isRunning = true
        }
        updateUi()
    }

    private fun updateUi() {
        val granted = Settings.canDrawOverlays(this)
        when {
            !granted -> {
                statusText.text = "Ruxsat berilmagan.\nTugmani bosib sozlamalarni oching."
                toggleButton.text = "Ruxsat berish"
            }
            SnowfallService.isRunning -> {
                statusText.text = "Qor yog'moqda ❄"
                toggleButton.text = "O'chirish"
            }
            else -> {
                statusText.text = "Qor yog'ishi o'chiq"
                toggleButton.text = "Yoqish"
            }
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
