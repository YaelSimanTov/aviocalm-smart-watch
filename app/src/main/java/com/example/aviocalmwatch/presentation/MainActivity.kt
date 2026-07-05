//package com.example.aviocalmwatch.presentation
//
//import android.Manifest
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Build
//import android.os.Bundle
//import android.provider.Settings
//import android.util.Log
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.text.BasicText
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import androidx.core.content.ContextCompat
//import androidx.compose.foundation.background
//import androidx.compose.ui.unit.sp
//class MainActivity : ComponentActivity() {
//
//    private lateinit var uniqueWatchId: String
//
//    private val healthPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
//            val bodySensorsGranted = results[Manifest.permission.BODY_SENSORS] ?: false
//
//            if (bodySensorsGranted) {
//                Log.d("AvioCalm", "Permissions granted, starting service.")
//                startCoreSensorService()
//            } else {
//                Log.e("AvioCalm", "Core permissions denied! Cannot start tracking.")
//            }
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//
//        uniqueWatchId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//
//        checkAndRequestPermissions()
//
//        setContent {
//            val currentHr by SensorService.heartRateFlow.collectAsState()
//            val currentSpo2 by SensorService.spo2Flow.collectAsState()
//            val currentStatus by SensorService.statusTextFlow.collectAsState()
//
//            // Clean, dark UI optimized for wearable screens
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(androidx.compose.ui.graphics.Color.Black)
//                    .padding(8.dp),
//                contentAlignment = Alignment.Center
//            ) {
//                Column(
//                    horizontalAlignment = Alignment.CenterHorizontally,
//                    verticalArrangement = Arrangement.SpaceEvenly,
//                    modifier = Modifier.fillMaxSize()
//                ) {
//                    // App title
//                    BasicText(
//                        text = "AvioCalm",
//                        style = androidx.compose.ui.text.TextStyle(
//                            color = androidx.compose.ui.graphics.Color.White,
//                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
//                            fontSize = 18.sp
//                        )
//                    )
//
//                    // Friendly status translations for patients
//                    val friendlyStatus = when {
//                        currentStatus.contains("Error") -> "Reconnecting..."
//                        currentStatus == "Initialized" -> "Waiting for sensors..."
//                        else -> "Active"
//                    }
//
//                    BasicText(
//                        text = friendlyStatus,
//                        style = androidx.compose.ui.text.TextStyle(
//                            color = if (currentStatus.contains("Error"))
//                                androidx.compose.ui.graphics.Color(0xFFFFA500)
//                            else
//                                androidx.compose.ui.graphics.Color(0xFF4CAF50),
//                            fontSize = 12.sp
//                        )
//                    )
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    // Symmetric vitals layout
//                    Row(
//                        horizontalArrangement = Arrangement.SpaceEvenly,
//                        modifier = Modifier.fillMaxWidth()
//                    ) {
//                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                            BasicText(
//                                text = "HR",
//                                style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
//                            )
//                            BasicText(
//                                text = "${currentHr ?: "--"}",
//                                style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp)
//                            )
//                        }
//                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                            BasicText(
//                                text = "SpO2",
//                                style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
//                            )
//                            BasicText(
//                                text = "${currentSpo2 ?: "--"}%",
//                                style = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp)
//                            )
//                        }
//                    }
//
//                    Spacer(modifier = Modifier.height(8.dp))
//
//                    // Custom styled button (Using Box and BasicText)
//                    Box(
//                        modifier = Modifier
//                            .background(
//                                color = androidx.compose.ui.graphics.Color(0xFFB00020), // Dark red
//                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
//                            )
//                            .clickable {
//                                val stopIntent = Intent(this@MainActivity, SensorService::class.java).apply {
//                                    action = "ACTION_STOP_SERVICE"
//                                }
//                                startService(stopIntent)
//                                finish()
//                            }
//                            .padding(horizontal = 24.dp, vertical = 8.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        BasicText(
//                            text = "End Session",
//                            style = androidx.compose.ui.text.TextStyle(
//                                color = androidx.compose.ui.graphics.Color.White,
//                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
//                            )
//                        )
//                    }
//
//                    // Displaying the full Android ID
//                    BasicText(
//                        text = "ID: $uniqueWatchId",
//                        style = androidx.compose.ui.text.TextStyle(
//                            color = androidx.compose.ui.graphics.Color.DarkGray,
//                            fontSize = 10.sp
//                        )
//                    )
//                }
//            }
//        }
//    }
//
//    private fun checkAndRequestPermissions() {
//        val permissionsToRequest = mutableListOf<String>()
//
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
//            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
//        }
//
//        if (ContextCompat.checkSelfPermission(this, "android.permission.health.READ_OXYGEN_SATURATION") != PackageManager.PERMISSION_GRANTED) {
//            permissionsToRequest.add("android.permission.health.READ_OXYGEN_SATURATION")
//        }
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
//            }
//        }
//
//        if (Build.VERSION.SDK_INT >= 34) {
//            if (ContextCompat.checkSelfPermission(this, "android.permission.health.READ_HEART_RATE") != PackageManager.PERMISSION_GRANTED) {
//                permissionsToRequest.add("android.permission.health.READ_HEART_RATE")
//            }
//        }
//
//        if (permissionsToRequest.isNotEmpty()) {
//            healthPermissionLauncher.launch(permissionsToRequest.toTypedArray())
//        } else {
//            startCoreSensorService()
//        }
//    }
//
//    private fun startCoreSensorService() {
//        val serviceIntent = Intent(this, SensorService::class.java)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(serviceIntent)
//        } else {
//            startService(serviceIntent)
//        }
//    }
//}




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