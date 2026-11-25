package ro.pub.cs.systems.eim.bluetoothchatapp

import android.Manifest
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class ConnectedThread(
    private val mainActivity: MainActivity,
    private val socket: BluetoothSocket
) : Thread() {

    private val inputStream: InputStream? = try {
        socket.inputStream
    } catch (e: IOException) {
        Log.d(TAG, "Failed to get input stream: ${e.message}")
        null
    }

    private val outputStream: OutputStream? = try {
        socket.outputStream
    } catch (e: IOException) {
        Log.d(TAG, "Failed to get output stream: ${e.message}")
        null
    }

    override fun run() {
        val input = inputStream
        val output = outputStream

        if (input == null || output == null) {
            mainActivity.onConnectionLost(this)
            return
        }

        if (!hasConnectPermission()) {
            return
        }

        val remoteDeviceName = try {
            socket.remoteDevice?.name
        } catch (e: SecurityException) {
            null
        } ?: socket.remoteDevice?.address ?: "Remote device"

        val buffer = ByteArray(1024)

        while (true) {
            val bytes = try {
                input.read(buffer)
            } catch (e: IOException) {
                Log.d(TAG, "Connection lost: ${e.message}")
                break
            }

            if (bytes <= 0) {
                break
            }

            val incomingMessage = String(buffer, 0, bytes)
            mainActivity.addChatMessage(
                mainActivity.getString(
                    R.string.chat_remote,
                    remoteDeviceName,
                    incomingMessage
                )
            )
        }

        mainActivity.onConnectionLost(this)
    }

    fun write(bytes: ByteArray) {
        val output = outputStream ?: return

        try {
            output.write(bytes)
        } catch (e: IOException) {
            Log.d(TAG, "Failed to write to output stream: ${e.message}")
            mainActivity.runOnUiThread {
                Toast.makeText(
                    mainActivity,
                    R.string.connection_lost,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun cancel() {
        try {
            socket.close()
        } catch (e: IOException) {
            Log.d(TAG, "Failed to close socket: ${e.message}")
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
        private const val TAG = "ConnectedThread"
    }
}