package com.example.aviocalmwatch.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SensorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val socketManager = AvioCalmSocketManager()
    private val serverBaseUrl = "https://backdrop-felt-tip-domelike.ngrok-free.dev"

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var uniqueWatchId: String

    private var healthTrackingService: HealthTrackingService? = null
    private var hrSamsungTracker: HealthTracker? = null
    private var spo2Tracker: HealthTracker? = null

    companion object {
        private const val CHANNEL_ID = "AviocalmSensorServiceChannel"
        private const val NOTIFICATION_ID = 101

        private val ibiBuffer = mutableListOf<Int>()

        private val _heartRateFlow = MutableStateFlow<Int?>(null)
        val heartRateFlow: StateFlow<Int?> = _heartRateFlow

        private val _spo2Flow = MutableStateFlow<Int?>(null)
        val spo2Flow: StateFlow<Int?> = _spo2Flow

        private val _statusTextFlow = MutableStateFlow("Initialized")
        val statusTextFlow: StateFlow<String> = _statusTextFlow
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("AvioCalmService", "Service onCreate started")

        uniqueWatchId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        acquireLocks()

        socketManager.connectToServer(serverBaseUrl)
        connectSamsungHealthSensor()
        startAutoSendLoop()
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AvioCalm::SensorWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AvioCalm::WifiLock"
        ).apply {
            acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle explicit stop request
        if (intent?.action == "ACTION_STOP_SERVICE") {
            Log.d("AvioCalmService", "Stop service requested")

            // Remove the foreground notification
            stopForeground(STOP_FOREGROUND_REMOVE)

            // Stop the service entirely, which will trigger onDestroy()
            stopSelf()

            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Handle manual SpO2 trigger
        if (intent?.action == "ACTION_TRIGGER_SPO2") {
            Log.d("AvioCalmService", "Manual SpO2 trigger requested")
            startSamsungSpo2()
        }

        // Return START_STICKY to keep it running normally
        return START_STICKY
    }
    private fun connectSamsungHealthSensor() {
        val connectionListener = object : ConnectionListener {
            override fun onConnectionSuccess() {
                try {
                    val service = healthTrackingService ?: return

                    if (service.trackingCapability.supportHealthTrackerTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                        hrSamsungTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                        startSamsungHeartRate()
                    }

                    // מזהה תמיכה בחמצן ומתחיל לולאה אוטומטית
                    if (service.trackingCapability.supportHealthTrackerTypes.contains(HealthTrackerType.SPO2)) {
                        spo2Tracker = service.getHealthTracker(HealthTrackerType.SPO2)
                        startPeriodicSpo2()
                    } else if (service.trackingCapability.supportHealthTrackerTypes.contains(HealthTrackerType.SPO2_ON_DEMAND)) {
                        spo2Tracker = service.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
                        startPeriodicSpo2()
                    }

                } catch (e: Exception) {
                    _statusTextFlow.value = "Error: ${e.message}"
                }
            }
            override fun onConnectionEnded() {
                Log.w("AvioCalmService", "Samsung Health Tracking service disconnected")
            }
            override fun onConnectionFailed(e: HealthTrackerException) {
                Log.e("AvioCalmService", "Connection failed: ${e.message}")
            }
        }
        healthTrackingService = HealthTrackingService(connectionListener, applicationContext)
        healthTrackingService?.connectService()
    }

    private fun startSamsungHeartRate() {
        val tracker = hrSamsungTracker ?: return
        val listener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                for (dp in dataPoints) {
                    val hrValue = dp.getValue(ValueKey.HeartRateSet.HEART_RATE)
                    val ibiList = dp.getValue(ValueKey.HeartRateSet.IBI_LIST)

                    if (hrValue > 0) {
                        _heartRateFlow.value = hrValue
                        if (!ibiList.isNullOrEmpty()) {
                            val validIbis = ibiList.filter { it > 0 }
                            if (validIbis.isNotEmpty()) {
                                synchronized(ibiBuffer) {
                                    ibiBuffer.addAll(validIbis)
                                }
                            }
                        }
                    }
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) {
                Log.e("AvioCalmService", "Heart rate tracking error: $error")
            }
        }
        tracker.setEventListener(listener)
    }


    private fun startPeriodicSpo2() {
        serviceScope.launch {
            while (isActive) {
                Log.d("AvioCalmService", "Auto-triggering SpO2 measurement...")
                _statusTextFlow.value = "SpO2: Auto-measuring..."

                startSamsungSpo2()

                delay(60000)
            }
        }
    }

    private fun startSamsungSpo2() {
        val tracker = spo2Tracker ?: return
        val listener = object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: MutableList<DataPoint>) {
                for (dp in dataPoints) {
                    val status = dp.getValue(ValueKey.SpO2Set.STATUS)
                    if (status == 2) {
                        val measuredSpo2 = dp.getValue(ValueKey.SpO2Set.SPO2)
                        if (measuredSpo2 > 0) {
                            _spo2Flow.value = measuredSpo2
                            Log.d("AvioCalmService", "Successful SpO2 reading: $measuredSpo2%")
                        }
                    }
                }
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) {}
        }
        tracker.setEventListener(listener)
    }

    private fun startAutoSendLoop() {
        serviceScope.launch {
            while (isActive) {
                val hr = _heartRateFlow.value ?: 0
                val ibiChunkToSend = synchronized(ibiBuffer) {
                    val copy = ibiBuffer.toList()
                    ibiBuffer.clear()
                    copy
                }


                val currentSpo2 = _spo2Flow.value ?: 98
                _spo2Flow.value = null

                if (hr > 0) {
                    socketManager.sendVitals(
                        deviceId = uniqueWatchId,
                        heartRate = hr,
                        spo2 = currentSpo2,
                        ibiList = ibiChunkToSend
                    )
                }
                delay(3000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Biometric Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AvioCalm Tracking Active")
            .setContentText("Streaming biometrics to system dashboard...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        socketManager.disconnect()

        try {
            hrSamsungTracker?.unsetEventListener()
            spo2Tracker?.unsetEventListener()
            healthTrackingService?.disconnectService()
        } catch (_: Exception) {}

        releaseLocks()
        Log.d("AvioCalmService", "Service destroyed")
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}