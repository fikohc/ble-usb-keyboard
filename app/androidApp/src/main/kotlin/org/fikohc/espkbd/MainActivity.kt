package org.fikohc.espkbd

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

private val blePermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            var granted by remember { mutableStateOf(hasBlePermissions()) }
            val enableBluetooth = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) {}
            val requestPermissions = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { result -> granted = result.values.all { it } }

            LaunchedEffect(Unit) {
                if (!granted) requestPermissions.launch(blePermissions)
            }
            LaunchedEffect(granted) {
                if (granted && !bluetoothEnabled()) {
                    enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }

            App(
                blePermissionGranted = granted,
                onRequestBlePermission = { requestPermissions.launch(blePermissions) },
            )
        }
    }

    private fun hasBlePermissions() = blePermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun bluetoothEnabled() =
        getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
}
