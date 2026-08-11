package app.patriotcycles.fardriverdash

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

data class FardriverData(
    val voltage: Float = 0f,
    val lineCurrent: Float = 0f,
    val power: Float = 0f,
    val gear: Int = 0,
    val speed: Float = 0f,
    val gpsSpeed: Float = 0f,
    val controllerTemp: Float = 0f,
    val motorTemp: Float = 0f,
    val soc: Int = 0,
    val isRegenFromCurrent: Boolean = false,
    val odometerMiles: Double = 0.0,
    val tripMiles: Double = 0.0,
    val tripSeconds: Double = 0.0,
    val usedAh: Double = 0.0,
    val maxSpeed: Float = 0f,
    val totalSpeedSum: Double = 0.0,
    val speedCount: Long = 0
)

val FardriverData.avgSpeed: Float
    get() = if (speedCount > 0) (totalSpeedSum / speedCount).toFloat() else 0f

data class FardriverSettings(
    val batteryCapacityAh: Float = 40.0f,
    val odometerOffset: Float = 0.0f,
    val ampsMin: Float = 0f,
    val ampsMax: Float = 50f,
    val mphMin: Float = 0f,
    val mphMax: Float = 50f,
    val voltageMin: Float = 42f,
    val voltageMax: Float = 68f,
    val tempMin: Float = 32f,
    val tempMax: Float = 212f
)

class FardriverRepository(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("fardriver_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<FardriverSettings> = _settings

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var lastActiveTime = 0L

    private val _uiState = MutableStateFlow(
        FardriverData(
            odometerMiles = sharedPrefs.getFloat("odometer", 0f).toDouble(),
            tripMiles = sharedPrefs.getFloat("trip", 0f).toDouble(),
            usedAh = sharedPrefs.getFloat("used_ah", 0f).toDouble(),
            tripSeconds = try {
                sharedPrefs.getFloat("trip_seconds", 0f).toDouble()
            } catch (e: Exception) {
                sharedPrefs.getLong("trip_seconds", 0L).toDouble()
            }
        )
    )
    val uiState: StateFlow<FardriverData> = _uiState

    private val _connectionState = MutableStateFlow("Disconnected")
    val connectionState: StateFlow<String> = _connectionState

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BluetoothDevice>> = _scannedDevices

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val flashReadAddr = intArrayOf(
        0xE2, 0xE8, 0xEE, 0xE4, 0x06, 0x0C, 0x12, 0xE2, 0xE8, 0xEE, 0x18, 0x1E, 0x24, 0x2A,
        0xE2, 0xE8, 0xEE, 0x30, 0x5D, 0x63, 0x69, 0xE2, 0xE8, 0xEE, 0x7C, 0x82, 0x88, 0x8E,
        0xE2, 0xE8, 0xEE, 0x94, 0x9A, 0xA0, 0xA6, 0xE2, 0xE8, 0xEE, 0xAC, 0xB2, 0xB8, 0xBE,
        0xE2, 0xE8, 0xEE, 0xC4, 0xCA, 0xD0, 0xE2, 0xE8, 0xEE, 0xD6, 0xDC, 0xF4, 0xFA
    )

    private var lastPacketTime = 0L

    private fun loadSettings(): FardriverSettings {
        return FardriverSettings(
            batteryCapacityAh = sharedPrefs.getFloat("battery_capacity", 40.0f),
            odometerOffset = sharedPrefs.getFloat("odometer_offset", 0.0f),
            ampsMin = sharedPrefs.getFloat("amps_min", 0f),
            ampsMax = sharedPrefs.getFloat("amps_max", 50f),
            mphMin = sharedPrefs.getFloat("mph_min", 0f),
            mphMax = sharedPrefs.getFloat("mph_max", 50f),
            voltageMin = sharedPrefs.getFloat("voltage_min", 42f),
            voltageMax = sharedPrefs.getFloat("voltage_max", 68f),
            tempMin = sharedPrefs.getFloat("temp_min", 32f),
            tempMax = sharedPrefs.getFloat("temp_max", 212f)
        )
    }

    fun updateSettings(newSettings: FardriverSettings) {
        sharedPrefs.edit {
            putFloat("battery_capacity", newSettings.batteryCapacityAh)
            putFloat("odometer_offset", newSettings.odometerOffset)
            putFloat("amps_min", newSettings.ampsMin)
            putFloat("amps_max", newSettings.ampsMax)
            putFloat("mph_min", newSettings.mphMin)
            putFloat("mph_max", newSettings.mphMax)
            putFloat("voltage_min", newSettings.voltageMin)
            putFloat("voltage_max", newSettings.voltageMax)
            putFloat("temp_min", newSettings.tempMin)
            putFloat("temp_max", newSettings.tempMax)
        }
        _settings.value = newSettings
    }

    fun resetTrip() {
        sharedPrefs.edit {
            putFloat("trip", 0f)
            putFloat("used_ah", 0f)
            putFloat("trip_seconds", 0f)
        }
        lastActiveTime = 0L
        _uiState.value = _uiState.value.copy(
            tripMiles = 0.0,
            usedAh = 0.0,
            tripSeconds = 0.0,
            maxSpeed = 0f,
            totalSpeedSum = 0.0,
            speedCount = 0
        )
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun startScanning() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        _connectionState.value = "Scanning..."
        _scannedDevices.value = emptyList()

        val settings =
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, scanCallback)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
    fun stopScanning() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        if (bluetoothAdapter?.isEnabled != true) {
            _connectionState.value = "Bluetooth is OFF"
            return
        }

        // Clean up any existing connection before starting a new one
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
        }
        bluetoothGatt = null

        println("FardriverRepository: Connecting to ${device.address}")
        sharedPrefs.edit { putString("last_device_address", device.address) }
        _connectionState.value = "Connecting..."
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    @RequiresPermission(allOf = [android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT])
    fun autoConnect() {
        val currentState = _connectionState.value
        if (currentState == "Connected" || currentState.startsWith("Connecting") || currentState == "Discovering Services...") {
            return
        }

        val lastAddress = sharedPrefs.getString("last_device_address", null) ?: return
        if (bluetoothAdapter?.isEnabled != true) return

        val device = try {
            bluetoothAdapter?.getRemoteDevice(lastAddress)
        } catch (e: Exception) {
            null
        } ?: return

        _connectionState.value = "Auto-connecting..."

        // Use autoConnect = true so Android OS waits for the device to appear
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
        }
        bluetoothGatt = device.connectGatt(context, true, gattCallback)
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                500L,
                0f,
                locationListener
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val speedMph = location.speed * 2.23694f
            _uiState.value = _uiState.value.copy(gpsSpeed = speedMph)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_SCAN)
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
            result?.device?.let { device ->
                if (!_scannedDevices.value.contains(device)) {
                    _scannedDevices.value += device
                }
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = "Discovering Services..."
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = "Disconnected"
                bluetoothGatt = null
                lastPacketTime = 0L
            }
        }

        @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = "Connected"
                gatt?.services?.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            gatt.setCharacteristicNotification(characteristic, true)
                            characteristic.descriptors.forEach { descriptor ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(
                                        descriptor,
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value =
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            }
                        }
                    }
                }
            }
        }

        @Deprecated("Used for compatibility")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            @Suppress("DEPRECATION")
            characteristic?.value?.let { processPacket(it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            processPacket(value)
        }
    }

    private fun verifyCRC(data: ByteArray): Boolean {
        if (data.size != 16) return false
        var crc = 0x7F3C
        for (i in 0 until 14) {
            val byteVal = data[i].toInt() and 0xFF
            crc = crc xor byteVal
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc shr 1) xor 0xA001 else crc shr 1
            }
        }
        val packetCrc = ((data[15].toInt() and 0xFF) shl 8) or (data[14].toInt() and 0xFF)
        return crc == packetCrc
    }

    private fun processPacket(packet: ByteArray) {
        if (!verifyCRC(packet)) return

        val id = packet[1].toInt() and 0x3F
        if (id >= flashReadAddr.size) return
        val address = flashReadAddr[id]

        val pData = packet.copyOfRange(2, packet.size)
        var currentData = _uiState.value

        fun getUShort(b1: Byte, b2: Byte): Int =
            ((b2.toInt() and 0xFF) shl 8) or (b1.toInt() and 0xFF)

        fun getShort(b1: Byte, b2: Byte): Short =
            (((b2.toInt() and 0xFF) shl 8) or (b1.toInt() and 0xFF)).toShort()

        val currentTime = System.currentTimeMillis()
        val timeDeltaMs = if (lastPacketTime > 0) currentTime - lastPacketTime else 0
        lastPacketTime = currentTime

        when (address) {
            0xE2 -> {
                val gear = ((pData[0].toInt() shr 2) and 0x03) + 1
                currentData = currentData.copy(gear = gear)
            }

            0xE8 -> {
                val newVoltage = getUShort(pData[0], pData[1]) / 10.0f
                var lineCurrent = currentData.lineCurrent
                var isRegen = currentData.isRegenFromCurrent

                if (newVoltage in 0f..100f) {
                    currentData = currentData.copy(voltage = newVoltage)
                }

                val newCurrent = getShort(pData[4], pData[5]) / 4.0f
                if (newCurrent in -100f..300f) {
                    lineCurrent = newCurrent
                    isRegen = newCurrent < 0f
                }
                currentData =
                    currentData.copy(lineCurrent = lineCurrent, isRegenFromCurrent = isRegen)
            }

            0xD6 -> {
                val rawC = getShort(pData[10], pData[11]).toFloat()
                if (rawC in -20f..150f) {
                    val tempF = rawC * 9f / 5f + 32f
                    currentData = currentData.copy(controllerTemp = tempF)
                }
            }

            0xF4 -> {
                val rawC = getShort(pData[0], pData[1]).toFloat()
                if (rawC in -20f..250f) {
                    val tempF = rawC * 9f / 5f + 32f
                    currentData = currentData.copy(motorTemp = tempF)
                }
                val soc = pData[3].toInt() and 0xFF
                currentData = currentData.copy(soc = soc)
            }
        }

        val speed = currentData.gpsSpeed
        val power =
            if (currentData.voltage > 0) currentData.voltage * currentData.lineCurrent else 0f

        val deltaSeconds = (timeDeltaMs / 1000.0).coerceAtMost(2.0)
        val distanceMiles = (speed / 3600.0) * deltaSeconds

        val newOdo = currentData.odometerMiles + distanceMiles
        val newTrip = currentData.tripMiles + distanceMiles
        val usedAhDelta = (currentData.lineCurrent * deltaSeconds) / 3600.0
        val newUsedAh = currentData.usedAh + usedAhDelta
        
        // Start timer if moving > 5.0mph OR drawing > 1.0A of current
        val isBikeMovingOrDrawing = speed > 5.0f || abs(currentData.lineCurrent) > 1.0f
        if (isBikeMovingOrDrawing) {
            lastActiveTime = currentTime
        }

        // Keep timer going if active OR if stopped for less than 2 minutes (120,000 ms)
        val shouldTimerRun = isBikeMovingOrDrawing || (lastActiveTime > 0 && currentTime - lastActiveTime < 120000)
        val newTripSeconds = currentData.tripSeconds + (if (shouldTimerRun) deltaSeconds else 0.0)

        if (abs(newOdo - (sharedPrefs.getFloat("odometer", 0f)).toDouble()) > 0.05) {
            sharedPrefs.edit {
                putFloat("odometer", newOdo.toFloat())
                putFloat("trip", newTrip.toFloat())
                putFloat("used_ah", newUsedAh.toFloat())
                putFloat("trip_seconds", newTripSeconds.toFloat())
            }
        }

        val newMaxSpeed = if (speed > currentData.maxSpeed) speed else currentData.maxSpeed
        val newTotalSpeedSum = currentData.totalSpeedSum + speed
        val newSpeedCount = currentData.speedCount + 1

        _uiState.value = currentData.copy(
            speed = speed,
            power = power,
            odometerMiles = newOdo,
            tripMiles = newTrip,
            usedAh = newUsedAh,
            tripSeconds = newTripSeconds,
            voltage = currentData.voltage,
            lineCurrent = currentData.lineCurrent,
            isRegenFromCurrent = currentData.isRegenFromCurrent,
            controllerTemp = currentData.controllerTemp,
            motorTemp = currentData.motorTemp,
            soc = currentData.soc,
            maxSpeed = newMaxSpeed,
            totalSpeedSum = newTotalSpeedSum,
            speedCount = newSpeedCount
        )
    }
}
