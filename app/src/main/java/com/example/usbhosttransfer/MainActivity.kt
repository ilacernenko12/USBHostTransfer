package com.example.usbhosttransfer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.usbhosttransfer.databinding.ActivityMainBinding
import com.example.usbhosttransfer.databinding.BottomSheetDeviceListBinding
import com.example.usbhosttransfer.databinding.DeviceListItemBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var usbManager: UsbManager
    private var bytes: ByteArray = byteArrayOf(0, 1, 2, 3, 4)
    private val timeout = 0
    private val forceClaim = true

    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            // call method to set up device communication
                            val permissionIntent = PendingIntent.getBroadcast(
                                this@MainActivity,
                                0,
                                Intent(ACTION_USB_PERMISSION),
                                PendingIntent.FLAG_IMMUTABLE
                            )
                            usbManager.requestPermission(device, permissionIntent)
                        }
                    } else {
                        Log.d(TAG, "permission denied for device $device")
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.apply {
                    // call your method that cleans up and closes communication with the device
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        binding.vBtnSearchDevice.setOnClickListener { view ->
            showDevicesDialog(usbManager.deviceList, view)
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private fun showDevicesDialog(deviceList: HashMap<String, UsbDevice>, view: View) {
        if (deviceList.isEmpty()) {
            Snackbar.make(view, "USB устройства не найдены", Snackbar.LENGTH_SHORT).show()
        } else {
            val bottomSheetView = BottomSheetDeviceListBinding.inflate(layoutInflater)
            val devicesLayout = bottomSheetView.devicesLayout

            for (device in deviceList.values) {
                val deviceItemBinding =
                    DeviceListItemBinding.inflate(LayoutInflater.from(view.context), null, false)

                deviceItemBinding.deviceNameTextView.text =
                    "1. Device Name: ${device.deviceName} \n Устройств: ${deviceList.size}"
                deviceItemBinding.vendorIdTextView.text = "2. Vendor ID: ${device.vendorId}"
                deviceItemBinding.manufacturerTextView.text =
                    "3. Manufacturer Name: ${device.manufacturerName}"
                deviceItemBinding.productNameTextView.text =
                    "4. Product Name: ${device.productName}"
                deviceItemBinding.emptyTV.text = "5. Product ID: ${device.productId}"
                deviceItemBinding.deviceClass.text = "6. Device class: ${device.deviceClass}"
                deviceItemBinding.deviceProtocol.text =
                    "7. Device protocol: ${device.deviceProtocol}"
                deviceItemBinding.configurationCount.text =
                    "8. Configuration count: ${device.configurationCount}"
                deviceItemBinding.deviceId.text = "9. Device ID: ${device.deviceId}"
                deviceItemBinding.version.text = "10. Version: ${device.version}"

                deviceItemBinding.root.setOnClickListener {
                    Snackbar.make(
                        deviceItemBinding.root,
                        "Device Selected: ${device.deviceName} (Vendor ID: ${device.vendorId})",
                        Snackbar.LENGTH_SHORT
                    ).show()

                }

                deviceItemBinding.vBtnSend.setOnClickListener {
                    sendDataToDevice(device)
                }
                deviceItemBinding.vBtnGet.setOnClickListener {
                    receiveDataFromDevice(device)
                }
                devicesLayout.addView(deviceItemBinding.root)
            }

            val bottomSheetDialog = BottomSheetDialog(this)
            bottomSheetDialog.setContentView(bottomSheetView.root)
            bottomSheetDialog.show()
        }
    }

    private fun sendDataToDevice(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            device.getInterface(0).also { usbInterface ->
                usbInterface.getEndpoint(0)?.also { endpoint ->
                    usbManager.openDevice(device)?.apply {
                        claimInterface(usbInterface, forceClaim)
                        bulkTransfer(endpoint, bytes, bytes.size, timeout) //do in another thread
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Данные отправлены",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun receiveDataFromDevice(device: UsbDevice) {
        CoroutineScope(Dispatchers.IO).launch {
            device.getInterface(0).also { usbInterface ->
                usbInterface.getEndpoint(0)?.also { endpoint ->
                    usbManager.openDevice(device)?.apply {
                        claimInterface(usbInterface, true)
                        val buffer = ByteArray(endpoint.maxPacketSize)
                        val bytesRead = bulkTransfer(endpoint, buffer, buffer.size, timeout)
                        if (bytesRead >= 0) {
                            val receivedData = buffer.copyOf(bytesRead)
                            val receivedString = String(receivedData)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Received data: $receivedString",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            // Делайте что-то с полученными данными
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to receive data",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }


    companion object {
        private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        private const val TAG = "USB_HOST"
    }
}
