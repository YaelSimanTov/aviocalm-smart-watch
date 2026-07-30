package com.example.aviocalmwatch.presentation

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONObject

class AvioCalmSocketManager {

    private var mSocket: Socket? = null
    private var currentServerUrl: String? = null
    private var onConnectionStateChange: ((Boolean) -> Unit)? = null

    companion object {
        private const val TAG = "AvioCalmWatch"
        private const val EVENT_WATCH_VITALS = "watch_vitals_update"
    }

    fun connectToServer(serverUrl: String, connectionStateCallback: ((Boolean) -> Unit)? = null) {
        try {
            currentServerUrl = serverUrl
            onConnectionStateChange = connectionStateCallback

            if (mSocket != null && mSocket?.connected() == true) {
                return
            }

            // Clean up stale or disconnected socket before creating a new one to prevent leaks
            if (mSocket != null) {
                mSocket?.off()
                mSocket = null
            }

            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 1000
                timeout = 10000
            }

            mSocket = IO.socket(serverUrl, options)

            mSocket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected successfully: ${mSocket?.id()}")
                onConnectionStateChange?.invoke(true)
            }

            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connection error: ${args.joinToString()}")
                onConnectionStateChange?.invoke(false)
            }

            mSocket?.on(Socket.EVENT_DISCONNECT) {
                Log.w(TAG, "Socket disconnected")
                onConnectionStateChange?.invoke(false)
            }

            mSocket?.connect()

        } catch (e: Exception) {
            Log.e(TAG, "Exception during connection setup", e)
        }
    }

    fun sendVitals(deviceId: String, heartRate: Int, spo2: Int?, ibiList: List<Int>) {
        // Check connection before attempting to send
        if (mSocket?.connected() != true) {
            Log.w(TAG, "Socket disconnected. Dropping packet. Socket.io will auto-reconnect.")
            return
        }

        try {
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("vitals", JSONObject().apply {
                    put("heartRate", heartRate)
                    put("spo2", spo2 ?: JSONObject.NULL) // Safe null handling for missing SpO2
                    put("ibiData", JSONArray(ibiList))
                })
                put("timestamp", System.currentTimeMillis())
            }

            mSocket?.emit(EVENT_WATCH_VITALS, payload)
            Log.d(TAG, "Vitals sent: $payload")

        } catch (e: Exception) {
            Log.e(TAG, "JSON serialization or emission error", e)
        }
    }
    fun disconnect() {
        try {
            mSocket?.disconnect()
            mSocket?.off()
            mSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error executing disconnect routine", e)
        }
    }

    fun isConnected(): Boolean {
        return mSocket?.connected() == true
    }
}