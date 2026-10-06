package org.fikohc.espkbd.ble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class BleScanState(
    val devices: List<BleDevice> = emptyList(),
    // Why the last scan attempt failed; cleared by the next device list
    val error: BleException? = null,
)

// Scanning for keyboard devices and connecting to them. All work runs in [scope].
class BleManager(
    private val ble: BleClient,
    private val scope: CoroutineScope,
    private val scanRetryDelay: Duration = 3.seconds,
) {
    private val scanState = MutableStateFlow(BleScanState())
    private var scanJob: Job? = null

    fun observeDevices(): StateFlow<BleScanState> = scanState.asStateFlow()

    // Scan until stopped. A scan that fails or ends starts again after scanRetryDelay.
    fun startScanJob() {
        if (scanJob != null) return
        launchScan(Duration.ZERO)
    }

    fun stopScanJob() {
        val job = scanJob ?: return
        scanJob = null
        job.cancel()
    }

    fun connect(device: BleDevice): KbdConnection = BleKbdConnection(ble, device.id, scope)

    private fun launchScan(after: Duration) {
        // Lazy: scanJob must be set before the scan can run (and possibly fail) on an immediate dispatcher
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(after)
            try {
                ble.scan().collect { devices -> scanState.value = BleScanState(devices) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the last device list; the next successful scan replaces it
                val error = e as? BleException ?: BleException.BluetoothUnavailable(e)
                scanState.update { it.copy(error = error) }
            }
        }
        job.invokeOnCompletion { cause ->
            // Stopped (or replaced) scans stay stopped
            if (cause !is CancellationException && scanJob === job) launchScan(scanRetryDelay)
        }
        scanJob = job
        job.start()
    }
}
