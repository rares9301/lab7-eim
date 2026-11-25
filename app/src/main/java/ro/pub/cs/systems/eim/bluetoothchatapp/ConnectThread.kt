package ro.pub.cs.systems.eim.bluetoothchatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.UUID

class ConnectThread(
    private val mainActivity: MainActivity,
    private val bluetoothAdapter: BluetoothAdapter,
    device: BluetoothDevice?,
    uuid: UUID
) : Thread() {

    private var socket: BluetoothSocket? = null

    init {
        try {
            if (hasConnectPermission()) {
                socket = device?.createRfcommSocketToServiceRecord(uuid)
            } else {
                Log.d(TAG, "BLUETOOTH_CONNECT permission not granted.")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error creating client socket: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception creating client socket: ${e.message}")
        }
    }

    override fun run() {
        val currentSocket = socket
        if (currentSocket == null) {
            Log.d(TAG, "Socket is null, cannot connect.")
            return
        }

        if (!hasConnectPermission()) {
            return
        }

        if (hasScanPermission()) {
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: SecurityException) {
                Log.d(TAG, "Cannot cancel discovery: ${e.message}")
            }
        }

        try {
            currentSocket.connect()
            Log.d(TAG, "Connected to ${currentSocket.remoteDevice?.address}")

            socket = null
            mainActivity.manageConnectedSocket(currentSocket)
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            mainActivity.onConnectionFailed()
            closeSocket(currentSocket)
        } catch (e: SecurityException) {
            Log.e(TAG, "Connection refused by the framework: ${e.message}")
            mainActivity.onConnectionFailed()
            closeSocket(currentSocket)
        }
    }

    fun cancel() {
        val currentSocket = socket
        socket = null
        if (currentSocket != null) {
            Log.d(TAG, "Canceling ConnectThread.")
            closeSocket(currentSocket)
        }
    }

    private fun closeSocket(target: BluetoothSocket) {
        try {
            target.close()
        } catch (e: IOException) {
            Log.d(TAG, "Error closing client socket: ${e.message}")
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(
                mainActivity,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(
                mainActivity,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "ConnectThread"
    }
}