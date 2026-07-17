
package com.example.aviocalmwatch.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var uniqueWatchId: String

    private val healthPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val bodySensorsGranted = results[Manifest.permission.BODY_SENSORS] ?: false

            if (bodySensorsGranted) {
                Log.d("AvioCalm", "Permissions granted, starting service.")
                startCoreSensorService()
            } else {
                Log.e("AvioCalm", "Core permissions denied! Cannot start tracking.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        uniqueWatchId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        checkAndRequestPermissions()

        setContent {
            val currentHr by SensorService.heartRateFlow.collectAsState()
            val currentSpo2 by SensorService.spo2Flow.collectAsState()
            val currentStatus by SensorService.statusTextFlow.collectAsState()

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    BasicText(text = "AvioCalm Watch (Background Mode)")
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(text = "ID: $uniqueWatchId")
                    Spacer(modifier = Modifier.height(16.dp))

                    BasicText(
                        text = "[ Auto SpO2 is Running (Tap to force) ]",
                        modifier = Modifier.clickable {
                            // מאלץ מדידת חמצן באותו רגע
                            val serviceIntent =
                                Intent(this@MainActivity, SensorService::class.java).apply {
                                    action = "ACTION_TRIGGER_SPO2"
                                }
                            startService(serviceIntent)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    BasicText(text = "HR: ${currentHr?.toInt() ?: "Waiting..."}")
                    BasicText(text = "SpO2 Capture: ${currentSpo2 ?: "No Data"}")
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(text = "Status: $currentStatus")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }

        // הרשאת חמצן בדם
        if (ContextCompat.checkSelfPermission(
                this,
                "android.permission.health.READ_OXYGEN_SATURATION"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add("android.permission.health.READ_OXYGEN_SATURATION")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    "android.permission.health.READ_HEART_RATE"
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add("android.permission.health.READ_HEART_RATE")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            healthPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startCoreSensorService()
        }
    }

    private fun startCoreSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
}