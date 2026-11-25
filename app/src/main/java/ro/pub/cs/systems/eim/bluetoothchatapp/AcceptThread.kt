package ro.pub.cs.systems.eim.bluetoothchatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class AcceptThread(
    private val mainActivity: MainActivity,
    adapter: BluetoothAdapter,
    uuid: UUID
) : Thread() {

    private var serverSocket: BluetoothServerSocket? = null

    val isServerSocketReady: Boolean
        get() = serverSocket != null

    init {
        try {
            if (hasConnectPermission()) {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME,
                    uuid
                )
            } else {
                Log.d(TAG, "BLUETOOTH_CONNECT permission not granted.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating server socket: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception creating server socket: ${e.message}")
        }
    }

    override fun run() {
        val socket = serverSocket
        if (socket == null) {
            Log.d(TAG, "ServerSocket is null, cannot accept connections.")
            return
        }

        try {
            val connectedSocket = socket.accept()
            Log.d(TAG, "Connection accepted.")
            mainActivity.manageConnectedSocket(connectedSocket)
        } catch (e: IOException) {
            Log.d(TAG, "Error while accepting connection: ${e.message}")
        } finally {
            closeServerSocket()
        }
    }

    fun cancel() {
        Log.d(TAG, "Canceling AcceptThread.")
        closeServerSocket()
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing server socket: ${e.message}")
        } finally {
            serverSocket = null
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(
                mainActivity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "AcceptThread"
        private const val SERVICE_NAME = "BluetoothChatApp"
    }
}