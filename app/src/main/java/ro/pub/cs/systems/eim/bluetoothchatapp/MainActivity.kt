package ro.pub.cs.systems.eim.bluetoothchatapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var chatArrayAdapter: ArrayAdapter<String>
    private val chatMessages = mutableListOf<String>()

    private var selectedDevice: BluetoothDevice? = null

    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var listDevicesButton: Button

    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO 1: Initialize views
        initViews()

        // TODO 2: Initialize Bluetooth
        initBluetooth()

        // TODO 3: Request permissions
        checkPermissions()
    }

    // TODO 1: Implement the method that initializes the views
    private fun initViews() {
        val chatListView: ListView = findViewById(R.id.chatListView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        listDevicesButton = findViewById(R.id.listDevicesButton)

        chatArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, chatMessages)
        chatListView.adapter = chatArrayAdapter

        listDevicesButton.setOnClickListener { listPairedDevices() }

        // TODO 7: Send message using ConnectedThread
        sendButton.setOnClickListener { sendMessage() }
        messageEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }
    }

    // TODO 2: Implement the method that initializes Bluetooth
    private fun initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_unavailable, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // TODO 3: Implement the method that checks permissions
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            onBluetoothPermissionsGranted()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun onBluetoothPermissionsGranted() {
        val adapter = bluetoothAdapter ?: return

        if (adapter.isEnabled) {
            startServer()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
    }

    // TODO 4: Handle permission results and activate Bluetooth programmatically
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_PERMISSIONS) {
            return
        }

        if (hasBluetoothPermissions()) {
            onBluetoothPermissionsGranted()
        } else {
            Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // TODO 4: Handle Bluetooth enable result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (bluetoothAdapter?.isEnabled == true) {
                startServer()
            } else {
                Toast.makeText(this, R.string.bluetooth_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (::chatArrayAdapter.isInitialized &&
            hasBluetoothPermissions() &&
            bluetoothAdapter?.isEnabled == true &&
            connectedThread == null
        ) {
            startServer()
        }
    }

    // TODO 6: Implement server socket to listen for incoming connections
    private fun startServer() {
        val adapter = bluetoothAdapter ?: return

        if (!adapter.isEnabled || !hasBluetoothPermissions()) {
            return
        }
        if (acceptThread != null || connectedThread != null) {
            return
        }

        val thread = AcceptThread(this, adapter, MY_UUID)
        acceptThread = thread
        thread.start()

        if (thread.isServerSocketReady) {
            Toast.makeText(this, R.string.server_listening, Toast.LENGTH_SHORT).show()
        } else {
            acceptThread = null
            Toast.makeText(this, R.string.server_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // TODO 5: Implement the method that displays a dialog for selecting a paired device
    private fun listPairedDevices() {
        val adapter = bluetoothAdapter ?: return

        if (!hasBluetoothPermissions()) {
            checkPermissions()
            return
        }

        val pairedDevices: Set<BluetoothDevice> = try {
            adapter.bondedDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot read bonded devices: ${e.message}")
            emptySet()
        }

        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, R.string.no_paired_devices, Toast.LENGTH_LONG).show()
            return
        }

        val deviceList = mutableListOf<String>()
        val devices = mutableListOf<BluetoothDevice>()

        for (device in pairedDevices) {
            deviceList.add("${device.name}\n${device.address}")
            devices.add(device)
        }

        val deviceArrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            deviceList
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.select_device)
            .setAdapter(deviceArrayAdapter) { _, which ->
                selectedDevice = devices[which]

                val name = selectedDevice?.name ?: selectedDevice?.address.orEmpty()
                Toast.makeText(
                    this,
                    getString(R.string.connecting_to, name),
                    Toast.LENGTH_SHORT
                ).show()

                connectThread?.cancel()
                connectThread = ConnectThread(
                    this,
                    adapter,
                    selectedDevice,
                    MY_UUID
                ).also { it.start() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // TODO 7: Implement the method that sends a message to the connected device
    private fun sendMessage() {
        val message = messageEditText.text.toString()

        if (message.isEmpty()) {
            return
        }

        val thread = connectedThread
        if (thread == null) {
            Toast.makeText(this, R.string.no_connected_device, Toast.LENGTH_SHORT).show()
            return
        }

        thread.write(message.toByteArray())
        messageEditText.text.clear()
        addChatMessage(getString(R.string.chat_me, message))
    }

    // Update the UI with a new message
    fun addChatMessage(message: String) {
        runOnUiThread {
            chatMessages.add(message)
            chatArrayAdapter.notifyDataSetChanged()
        }
    }

    fun manageConnectedSocket(socket: BluetoothSocket) {
        runOnUiThread {
            val deviceName = try {
                socket.remoteDevice?.name ?: socket.remoteDevice?.address
            } catch (e: SecurityException) {
                null
            } ?: getString(R.string.app_name)

            connectedThread?.cancel()
            connectedThread = ConnectedThread(this, socket).also { it.start() }

            acceptThread?.cancel()
            acceptThread = null
            connectThread = null

            Toast.makeText(
                this,
                getString(R.string.connected_to, deviceName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun onConnectionFailed() {
        runOnUiThread {
            Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun onConnectionLost(thread: ConnectedThread) {
        runOnUiThread {
            if (isFinishing || isDestroyed) {
                return@runOnUiThread
            }
            if (connectedThread !== thread) {
                return@runOnUiThread
            }

            connectedThread = null
            Toast.makeText(this, R.string.connection_lost, Toast.LENGTH_SHORT).show()

            startServer()
        }
    }

    // TODO 8: Clean up threads on activity destroy
    override fun onDestroy() {
        super.onDestroy()

        val accept = acceptThread
        acceptThread = null
        accept?.cancel()

        val connect = connectThread
        connectThread = null
        connect?.cancel()

        val connected = connectedThread
        connectedThread = null
        connected?.cancel()
    }

    companion object {
        val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        private const val TAG = "BluetoothChat"
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
    }
}