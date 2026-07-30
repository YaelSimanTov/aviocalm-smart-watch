
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

class MainActivity : ComponentActivity() {

    private lateinit var uniqueWatchId: String

    // Set to true when the Start button triggers permission request, so we know to
    // launch the service once permissions are granted
    private var pendingStartAfterPermission = false

    private val healthPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            // On API 34+, BODY_SENSORS was superseded by granular health permissions and
            // cannot be granted via the standard launcher; use READ_HEART_RATE as the gate.
            val corePermissionGranted = if (Build.VERSION.SDK_INT >= 34) {
                ContextCompat.checkSelfPermission(
                    this, "android.permission.health.READ_HEART_RATE"
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BODY_SENSORS
                ) == PackageManager.PERMISSION_GRANTED
            }
            if (corePermissionGranted && pendingStartAfterPermission) {
                pendingStartAfterPermission = false
                Log.d("AvioCalm", "Permissions granted, starting service.")
                startCoreSensorService()
            } else if (!corePermissionGranted) {
                pendingStartAfterPermission = false
                Log.e("AvioCalm", "Core permissions denied! Cannot start tracking.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        uniqueWatchId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        setContent {
            val isConnected by SensorService.connectionStatusFlow.collectAsState()
            val isRunning by SensorService.isRunningFlow.collectAsState()

            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        // App title
                        Text(
                            text = "AvioCalm",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Device ID in small grey font
                        Text(
                            text = uniqueWatchId,
                            fontSize = 9.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // WebSocket connection status indicator
                        val statusColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFEF5350)
                        val statusLabel = if (isConnected) "● Connected" else "● Searching..."
                        Text(
                            text = statusLabel,
                            fontSize = 13.sp,
                            color = statusColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Start / Stop buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { handleStartButtonClick() },
                                enabled = !isRunning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Start",
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = { handleStopButtonClick() },
                                enabled = isRunning,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF5350)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Stop",
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Called when the Start button is tapped.
    // Requests all required permissions first; the service is started only after they are granted.
    private fun handleStartButtonClick() {
        Log.d("AvioCalm", "STEP 1: Start button clicked")
        val permissionsToRequest = buildPermissionsList()
        if (permissionsToRequest.isEmpty()) {
            Log.d("AvioCalm", "STEP 2: All permissions granted, calling startCoreSensorService() directly")
            startCoreSensorService()
        } else {
            Log.d("AvioCalm", "STEP 2: Missing permissions, requesting: $permissionsToRequest")
            pendingStartAfterPermission = true
            healthPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Called when the Stop button is tapped.
    // Executes the strict teardown sequence:
    // 1. stopService() triggers SensorService.onDestroy() which closes the socket,
    //    unregisters all sensor listeners, cancels coroutines, and releases locks.
    // 2. finishAffinity() closes the UI and exits the app completely.
    private fun handleStopButtonClick() {
        stopService(Intent(this, SensorService::class.java))
        finishAffinity()
    }

    // Returns the list of permissions that have not yet been granted.
    private fun buildPermissionsList(): List<String> {
        val permissionsToRequest = mutableListOf<String>()

        // On API 34+, BODY_SENSORS was superseded by granular health permissions;
        // checkSelfPermission always returns denied for it on modern Wear OS builds.
        if (Build.VERSION.SDK_INT < 34) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this, "android.permission.health.READ_OXYGEN_SATURATION"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add("android.permission.health.READ_OXYGEN_SATURATION")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            if (ContextCompat.checkSelfPermission(
                    this, "android.permission.health.READ_HEART_RATE"
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add("android.permission.health.READ_HEART_RATE")
            }
        }

        return permissionsToRequest
    }

    private fun startCoreSensorService() {
        Log.d("AvioCalm", "STEP 3: startCoreSensorService() called")
        val serviceIntent = Intent(this, SensorService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d("AvioCalm", "STEP 3a: Calling startForegroundService()")
                startForegroundService(serviceIntent)
            } else {
                Log.d("AvioCalm", "STEP 3a: Calling startService() (pre-O device)")
                startService(serviceIntent)
            }
        } catch (e: SecurityException) {
            Log.e("AvioCalm", "STEP 3 FAILED: SecurityException starting foreground service", e)
        } catch (e: Exception) {
            Log.e("AvioCalm", "STEP 3 FAILED: ${e.javaClass.simpleName} starting foreground service", e)
        }
    }
}