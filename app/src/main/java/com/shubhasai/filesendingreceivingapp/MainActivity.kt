package com.shubhasai.filesendingreceivingapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri

import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var txtSelectedFile: TextView
    private var selectedFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnSelectFile = findViewById<Button>(R.id.btnSelectFile)
        val btnConvertAndSend = findViewById<Button>(R.id.btnConvertAndSend)
        val btnReceiveAndConvert = findViewById<Button>(R.id.btnReceiveAndConvert)
        txtSelectedFile = findViewById(R.id.txtSelectedFile)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbPermissionReceiver, filter)
        btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(intent, 1000)
        }

        btnConvertAndSend.setOnClickListener {
            Log.d("Called","Here 6")
            selectedFilePath?.let {
                //val data = fileToBinary(it)
                // Implement USB send functionality here
                convertAndSend()
            }
        }

        btnReceiveAndConvert.setOnClickListener {
            readFromArduino()
            // Implement USB receive and convert functionality here
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbPermissionReceiver)
    }
    private fun readFromArduino() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
            return
        }

        val driver = availableDrivers.first()
        val connection = usbManager.openDevice(driver.device) ?: return

        val port = driver.ports[0] // Most devices have just one port (port 0)
        try {
            port.open(connection)
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            val buffer = ByteArray(256)
            var len = port.read(buffer, 1000)
            while (len > 0) {
                val data = String(buffer, 0, len)
                Log.d("Serial", "Read data: $data")
                len = port.read(buffer, 1000) // Keep reading data
            }
        } catch (e: IOException) {
            Log.e("Serial", "Error reading: ${e.message}")
        } finally {
            port.close()
            connection.close()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            uri?.let {
                txtSelectedFile.text = getFileName(it)
                selectedFilePath = it.toString()
            }
        }
    }

    private fun getFileName(uri: android.net.Uri): String {
        var name = ""
        val returnCursor = contentResolver.query(uri, null, null, null, null)
        returnCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
    private fun convertAndSend() {
        Log.d("Called","Here 1")
        selectedFilePath?.let {
            val data = fileToBinary(it)
            sendViaUSB(data)
        } ?: run {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fileToBinary(filePath: String): ByteArray {
        Log.d("Called","Here 2")
        val uri = Uri.parse(filePath)
        val inputStream = contentResolver.openInputStream(uri)
        return inputStream?.readBytes() ?: byteArrayOf()
    }
    private fun sendViaUSB(data: ByteArray) {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
            return
        }
        val driver = availableDrivers.first()
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Toast.makeText(this, "Opening device failed", Toast.LENGTH_SHORT).show()
            return
        }
        // Most devices have just one port (port 0)
        val port = driver.ports[0]
        try {
            port.open(connection)
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

            port.write(data, 1000)
            Toast.makeText(this, "Data sent successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error in sending data: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            port.close()
            connection.close()
        }
    }

//    private fun sendViaUSB(data: ByteArray) {
//        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
//        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
//
//        if (deviceList.isEmpty()) {
//            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Log or show the number of devices found
//        Toast.makeText(this, "USB Devices found: ${deviceList.size}", Toast.LENGTH_SHORT).show()
//
//        val device = deviceList.values.firstOrNull()  // Just grabbing the first device for demonstration
//        device?.let { usbDevice ->
//            sendToUSBDevice(usbDevice, data, usbManager)
//        }
//    }

    private fun sendToUSBDevice(usbDevice: UsbDevice, data: ByteArray, usbManager: UsbManager) {
        val permissionIntent = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (usbManager.hasPermission(usbDevice)) {
            try {
                val connection = usbManager.openDevice(usbDevice)
                val usbInterface = usbDevice.getInterface(0)
                val endpoint = usbInterface.getEndpoint(0)
                connection?.claimInterface(usbInterface, true)

                Thread {
                    val transferLength = connection.bulkTransfer(endpoint, data, data.size, 5000)
                    runOnUiThread {
                        if (transferLength > 0) {
                            Toast.makeText(this, "Data sent successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to send data", Toast.LENGTH_SHORT).show()
                        }
                        connection?.close()
                    }
                }.start()
            } catch (e: Exception) {
                Toast.makeText(this, "Error communicating with USB device: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            usbManager.requestPermission(usbDevice, permissionIntent)
        }
    }

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(this) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            // call method to set up device communication
                            //sendViaUSB(currentData) // Assuming you've stored the data you want to send
                        }
                    } else {
                        Log.d("USB", "permission denied for device $device")
                    }
                }
            }
        }
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.shubhasai.filesendingreceivingapp.USB_PERMISSION"
    }

}
