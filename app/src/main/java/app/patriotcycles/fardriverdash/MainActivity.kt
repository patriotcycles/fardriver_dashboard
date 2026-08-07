package app.patriotcycles.fardriverdash

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.patriotcycles.fardriverdash.ui.theme.FarrdriverInfoTheme
import app.patriotcycles.fardriverdash.ui.theme.dseg7
import app.patriotcycles.fardriverdash.ui.theme.serpentine
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class Screen {
    Dashboard,
    Settings,
    Connection,
    Diagnostics
}

class MainActivity : ComponentActivity() {

    private val repository by lazy { FardriverRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                println("MainActivity: Permissions granted by user, starting autoConnect")
                repository.startLocationUpdates()
                repository.autoConnect()
            } else {
                println("MainActivity: Permissions denied by user")
            }
        }

        setContent {
            FarrdriverInfoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    MainContent(repository)
                }
            }
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            println("MainActivity: Permissions granted, starting autoConnect")
            repository.startLocationUpdates()
            repository.autoConnect()
        } else {
            println("MainActivity: Requesting permissions")
            permissionLauncher.launch(requiredPermissions)
        }
    }
}

@Composable
fun MainContent(repository: FardriverRepository) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var showConnectionResetDialog by remember { mutableStateOf(false) }
    var hasPromptedThisSession by remember { mutableStateOf(false) }

    val status by repository.connectionState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            println("MainContent: Initial autoConnect trigger with permissions")
            repository.autoConnect()
        } else {
            println("MainContent: autoConnect skipped, permissions not granted yet")
        }
    }

    LaunchedEffect(status) {
        if (status == "Connected" && !hasPromptedThisSession) {
            showConnectionResetDialog = true
            hasPromptedThisSession = true
        }
    }

    if (showConnectionResetDialog) {
        AlertDialog(
            onDismissRequest = { showConnectionResetDialog = false },
            title = { Text("Connection Successful", fontFamily = serpentine) },
            text = {
                Text(
                    "Would you like to reset the trip data for this ride?",
                    fontFamily = serpentine
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.resetTrip()
                    showConnectionResetDialog = false
                }) { Text("Reset", color = Color.Red, fontFamily = serpentine) }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionResetDialog = false }) {
                    Text(
                        "Keep Data",
                        fontFamily = serpentine
                    )
                }
            }
        )
    }

    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen(
            repository = repository,
            onOpenSettings = { currentScreen = Screen.Settings },
            onOpenConnection = { currentScreen = Screen.Connection },
            onOpenDiagnostics = { currentScreen = Screen.Diagnostics }
        )

        Screen.Settings -> SettingsScreen(
            repository = repository,
            onBack = { currentScreen = Screen.Dashboard }
        )

        Screen.Connection -> ConnectionScreen(
            repository = repository,
            onBack = { currentScreen = Screen.Dashboard }
        )

        Screen.Diagnostics -> DiagnosticsScreen(
            repository = repository,
            onBack = { currentScreen = Screen.Dashboard }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: FardriverRepository,
    onOpenSettings: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val uiState by repository.uiState.collectAsState()
    val status by repository.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fardriver Dashboard", fontFamily = serpentine) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    val iconColor = when (status) {
                        "Connected" -> Color.Blue
                        "Disconnected" -> Color.Red
                        else -> Color.Yellow
                    }
                    IconButton(onClick = onOpenConnection) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = "Connect",
                            tint = iconColor
                        )
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Gauges Section
            val settings by repository.settings.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                // AMPS Gauge (Left)
                AnalogGauge(
                    value = uiState.lineCurrent,
                    minValue = settings.ampsMin,
                    maxValue = settings.ampsMax,
                    numLabels = 5,
                    label = "AMPS",
                    sweepAngle = 135f,
                    modifier = Modifier
                        .size(190.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-10).dp)
                )

                // MPH Gauge (Right)
                AnalogGauge(
                    value = uiState.gpsSpeed,
                    minValue = settings.mphMin,
                    maxValue = settings.mphMax,
                    numLabels = 5,
                    label = "MPH",
                    gear = uiState.gear,
                    modifier = Modifier
                        .size(250.dp)
                        .align(Alignment.TopEnd)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Battery Level Meter
            BatteryLevelMeter(
                voltage = uiState.voltage,
                minV = settings.voltageMin,
                maxV = settings.voltageMax
            )

            Spacer(modifier = Modifier.weight(1f))

            // Stats Table
            StatsSection(uiState, settings)
        }
    }
}

@SuppressLint("UseKtx")
@Composable
fun AnalogGauge(
    value: Float,
    minValue: Float = 0f,
    maxValue: Float = 50f,
    numLabels: Int,
    label: String,
    modifier: Modifier = Modifier,
    startAngle: Float = 140f,
    sweepAngle: Float = 260f,
    gear: Int? = null,
    warningValue: Float? = null
) {
    val displayWarningValue = warningValue ?: (minValue + (maxValue - minValue) * 0.8f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2
            val innerRadius = radius * 0.85f
            val tickOuterRadius = radius * 0.88f
            val tickInnerRadius = radius * 0.82f
            val labelRadius = radius * 0.98f

            // Background Arc
            drawArc(
                color = Color.White.copy(alpha = 0.1f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = center - Offset(innerRadius, innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 8f, cap = StrokeCap.Round)
            )

            // Warning Arc (Red segment)
            if (displayWarningValue < maxValue) {
                val warningStartAngle = startAngle + ((displayWarningValue - minValue) / (maxValue - minValue)) * sweepAngle
                val warningSweep = sweepAngle - (warningStartAngle - startAngle)
                drawArc(
                    color = Color.Red.copy(alpha = 0.8f),
                    startAngle = warningStartAngle,
                    sweepAngle = warningSweep,
                    useCenter = false,
                    topLeft = center - Offset(innerRadius, innerRadius),
                    size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                    style = Stroke(width = 8f, cap = StrokeCap.Butt)
                )
            }

            // Value Arc (White)
            val valueSweep = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) * sweepAngle
            drawArc(
                color = Color.White,
                startAngle = startAngle,
                sweepAngle = valueSweep,
                useCenter = false,
                topLeft = center - Offset(innerRadius, innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 10f, cap = StrokeCap.Round)
            )

            // Ticks and Labels
            val totalTicks = (numLabels - 1) * 5
            for (i in 0..totalTicks) {
                val angle = startAngle + (i.toFloat() / totalTicks) * sweepAngle
                val rad = Math.toRadians(angle.toDouble())
                val isMajor = i % 5 == 0
                val tickLen = if (isMajor) 15f else 8f
                val color = if (isMajor) Color.White else Color.White.copy(alpha = 0.5f)

                val start = Offset(
                    center.x + (tickOuterRadius * cos(rad)).toFloat(),
                    center.y + (tickOuterRadius * sin(rad)).toFloat()
                )
                val end = Offset(
                    center.x + ((tickOuterRadius - tickLen) * cos(rad)).toFloat(),
                    center.y + ((tickOuterRadius - tickLen) * sin(rad)).toFloat()
                )

                drawLine(
                    color = color,
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 3f else 1.5f,
                    cap = StrokeCap.Round
                )
            }

            // Needle
            val needleAngle = startAngle + ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f) * sweepAngle
            val needleRad = Math.toRadians(needleAngle.toDouble())
            val needleLength = innerRadius * 0.95f

            drawLine(
                color = Color.White,
                start = center,
                end = Offset(
                    center.x + (needleLength * cos(needleRad)).toFloat(),
                    center.y + (needleLength * sin(needleRad)).toFloat()
                ),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            // Needle Center
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = center
            )
            drawCircle(
                color = Color.Black,
                radius = 3f,
                center = center
            )
        }

        // Labels
        for (i in 0 until numLabels) {
            val angle = startAngle + (i.toFloat() / (numLabels - 1)) * sweepAngle
            val rad = Math.toRadians(angle.toDouble())
            val labelValue = minValue + (i.toFloat() / (numLabels - 1)) * (maxValue - minValue)
            val offsetRadius = 0.85f // Normalized radius for labels

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val radiusPx = sizeOfBox() / 2
                Text(
                    text = labelValue.toInt().toString(),
                    color = if (labelValue >= (warningValue ?: (maxValue * 0.8f))) Color.Red else Color.White,
                    fontSize = 12.sp,
                    fontFamily = serpentine,
                    modifier = Modifier.offset(
                        x = (radiusPx * 1.1f * cos(rad)).toFloat().dp,
                        y = (radiusPx * 1.1f * sin(rad)).toFloat().dp
                    )
                )
            }
        }

        // Center Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (20).dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", value),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = serpentine
            )
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 12.sp,
                fontFamily = serpentine
            )
            if (gear != null) {
                Text(
                    text = "GEAR $gear",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = serpentine,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun BatteryLevelMeter(voltage: Float, minV: Float = 42f, maxV: Float = 68f) {
    val percentage = ((voltage - minV) / (maxV - minV)).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = String.format(Locale.US, "%.1f V", voltage),
            color = Color.White,
            fontSize = 20.sp,
            fontFamily = serpentine,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF212121), Color(0xFF424242))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val totalBlocks = 12
                val filledBlocks = (percentage * totalBlocks).toInt()

                for (i in 1..totalBlocks) {
                    val blockColor = when {
                        i <= totalBlocks * 0.25f -> Color(0xFFD32F2F)
                        i <= totalBlocks * 0.5f -> Color(0xFFF57C00)
                        i <= totalBlocks * 0.75f -> Color(0xFFFBC02D)
                        else -> Color(0xFF388E3C)
                    }

                    val isFilled = i <= filledBlocks
                    val currentBrush = if (isFilled) {
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(blockColor.copy(alpha = 0.9f), blockColor)
                        )
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .then(
                                if (currentBrush != null) Modifier.background(currentBrush)
                                else Modifier.background(Color.White.copy(alpha = 0.05f))
                            )
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height(24.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(Color(0xFFBDBDBD), Color(0xFF757575))
                        ),
                        RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                    )
            )
        }
    }
}

private fun sizeOfBox(): Float = 200f
private fun radiusToDp(px: Float): Float = px

@Composable
fun StatsSection(data: FardriverData, settings: FardriverSettings) {
    val ahMi = if (data.tripMiles > 0.1) data.usedAh / data.tripMiles else 0.0
    val range = if (ahMi > 0) (settings.batteryCapacityAh - data.usedAh) / ahMi else 0.0
    val totalOdo = data.odometerMiles + settings.odometerOffset

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF424242)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "TRIP", String.format(Locale.US, "%.1f", data.tripMiles), "mi")
                StatItem(Modifier.weight(1f), "TIME", formatSeconds(data.tripSeconds), "")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "AVG SPD", String.format(Locale.US, "%.1f", data.avgSpeed), "mph")
                StatItem(Modifier.weight(1f), "MAX SPD", String.format(Locale.US, "%.1f", data.maxSpeed), "mph")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "USED AH", String.format(Locale.US, "%.1f", data.usedAh), "ah")
                StatItem(Modifier.weight(1f), "AH/MI", String.format(Locale.US, "%.1f", ahMi), "")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "RANGE EST", String.format(Locale.US, "%.1f", range), "mi")
                StatItem(Modifier.weight(1f), "ODO", String.format(Locale.US, "%.1f", totalOdo), "mi")
            }
        }
    }
}

@Composable
fun StatItem(modifier: Modifier, label: String, value: String, unit: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFFBDBDBD),
            fontSize = 12.sp,
            fontFamily = serpentine,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(140.dp) // Fixed width for all boxes
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0D0D0D))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background "shadow" for LCD segments
            Text(
                text = value.replace(Regex("[0-9]"), "8"),
                color = Color(0xFF1A1A1A),
                fontSize = 28.sp,
                fontFamily = dseg7,
                textAlign = TextAlign.Center
            )
            // Active segments
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color(0xFF00E676), // Classic green LCD look
                    fontSize = 28.sp,
                    fontFamily = dseg7,
                    textAlign = TextAlign.Center
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        fontFamily = serpentine,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(repository: FardriverRepository, onBack: () -> Unit) {
    val devices by repository.scannedDevices.collectAsState()
    val status by repository.connectionState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            repository.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bluetooth Devices", fontFamily = serpentine) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { repository.startScanning() },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("SCAN", fontFamily = serpentine)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            Text(
                text = "Status: $status",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                fontFamily = serpentine
            )

            if (devices.isEmpty() && status == "Scanning...") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.connectToDevice(device)
                                onBack()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = device.name ?: "Unknown Device",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = serpentine
                            )
                            Text(
                                text = device.address,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontFamily = serpentine
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: FardriverRepository, onBack: () -> Unit) {
    val settings by repository.settings.collectAsState()

    var battCapacity by remember(settings) { mutableStateOf(settings.batteryCapacityAh.toString()) }
    var odoOffset by remember(settings) { mutableStateOf(settings.odometerOffset.toString()) }

    var ampsMin by remember(settings) { mutableStateOf(settings.ampsMin.toString()) }
    var ampsMax by remember(settings) { mutableStateOf(settings.ampsMax.toString()) }
    var mphMin by remember(settings) { mutableStateOf(settings.mphMin.toString()) }
    var mphMax by remember(settings) { mutableStateOf(settings.mphMax.toString()) }
    var voltageMin by remember(settings) { mutableStateOf(settings.voltageMin.toString()) }
    var voltageMax by remember(settings) { mutableStateOf(settings.voltageMax.toString()) }

    var showTripResetDialog by remember { mutableStateOf(false) }

    if (showTripResetDialog) {
        AlertDialog(
            onDismissRequest = { showTripResetDialog = false },
            title = { Text("Reset Trip Meter", fontFamily = serpentine) },
            text = {
                Text(
                    "Are you sure you want to reset the trip meter, time, and used Ah?",
                    fontFamily = serpentine
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.resetTrip()
                    showTripResetDialog = false
                }) { Text("Reset", color = Color.Red, fontFamily = serpentine) }
            },
            dismissButton = {
                TextButton(onClick = { showTripResetDialog = false }) {
                    Text(
                        "Cancel",
                        fontFamily = serpentine
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration Settings", fontFamily = serpentine) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "Battery Config",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = serpentine
            )

            OutlinedTextField(
                value = battCapacity,
                onValueChange = { battCapacity = it },
                label = { Text("Battery Capacity (Ah)") },
                placeholder = { Text("e.g. 40") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Total rated capacity of your battery pack") }
            )

            HorizontalDivider()

            Text(
                "Odometer Settings",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = serpentine
            )

            OutlinedTextField(
                value = odoOffset,
                onValueChange = { odoOffset = it },
                label = { Text("Odometer Start Point (mi)") },
                placeholder = { Text("e.g. 500") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Initial mileage of the vehicle") }
            )

            HorizontalDivider()

            Text(
                "Gauge Calibration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = serpentine
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = ampsMin,
                    onValueChange = { ampsMin = it },
                    label = { Text("Amps Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ampsMax,
                    onValueChange = { ampsMax = it },
                    label = { Text("Amps Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = mphMin,
                    onValueChange = { mphMin = it },
                    label = { Text("MPH Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mphMax,
                    onValueChange = { mphMax = it },
                    label = { Text("MPH Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = voltageMin,
                    onValueChange = { voltageMin = it },
                    label = { Text("Voltage Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = voltageMax,
                    onValueChange = { voltageMax = it },
                    label = { Text("Voltage Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Text(
                "Trip Meter",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = serpentine
            )

            Button(
                onClick = { showTripResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) {
                Text("Reset Trip Data", fontFamily = serpentine)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val newSettings = settings.copy(
                        batteryCapacityAh = battCapacity.toFloatOrNull()
                            ?: settings.batteryCapacityAh,
                        odometerOffset = odoOffset.toFloatOrNull()
                            ?: settings.odometerOffset,
                        ampsMin = ampsMin.toFloatOrNull() ?: settings.ampsMin,
                        ampsMax = ampsMax.toFloatOrNull() ?: settings.ampsMax,
                        mphMin = mphMin.toFloatOrNull() ?: settings.mphMin,
                        mphMax = mphMax.toFloatOrNull() ?: settings.mphMax,
                        voltageMin = voltageMin.toFloatOrNull() ?: settings.voltageMin,
                        voltageMax = voltageMax.toFloatOrNull() ?: settings.voltageMax
                    )
                    repository.updateSettings(newSettings)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "Apply & Save Settings",
                    modifier = Modifier.padding(8.dp),
                    fontFamily = serpentine
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(repository: FardriverRepository, onBack: () -> Unit) {
    val uiState by repository.uiState.collectAsState()
    val status by repository.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Diagnostics", fontFamily = serpentine) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("Connection Status: $status", color = Color.White, fontFamily = serpentine, fontSize = 18.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item { DiagnosticRow("Voltage", String.format(Locale.US, "%.2f V", uiState.voltage)) }
            item { DiagnosticRow("Line Current", String.format(Locale.US, "%.2f A", uiState.lineCurrent)) }
            item { DiagnosticRow("Power", String.format(Locale.US, "%.1f W", uiState.power)) }
            item { DiagnosticRow("RPM", String.format(Locale.US, "%.0f", uiState.rpm)) }
            item { DiagnosticRow("Raw RPM", uiState.rawRpm.toString()) }
            item { DiagnosticRow("Gear", uiState.gear.toString()) }
            item { DiagnosticRow("Speed (Controller)", String.format(Locale.US, "%.1f mph", uiState.speed)) }
            item { DiagnosticRow("Speed (GPS)", String.format(Locale.US, "%.1f mph", uiState.gpsSpeed)) }
            item { DiagnosticRow("Controller Temp", "${uiState.controllerTemp} °C") }
            item { DiagnosticRow("Motor Temp", "${uiState.motorTemp} °C") }
            item { DiagnosticRow("State of Charge (SOC)", "${uiState.soc} %") }
            item { DiagnosticRow("Regen Active", uiState.isRegenFromCurrent.toString()) }
            item { DiagnosticRow("Odometer", String.format(Locale.US, "%.3f mi", uiState.odometerMiles)) }
            item { DiagnosticRow("Trip Distance", String.format(Locale.US, "%.3f mi", uiState.tripMiles)) }
            item { DiagnosticRow("Trip Time", formatSeconds(uiState.tripSeconds)) }
            item { DiagnosticRow("Used Capacity", String.format(Locale.US, "%.3f Ah", uiState.usedAh)) }
            item { DiagnosticRow("Max Speed", String.format(Locale.US, "%.1f mph", uiState.maxSpeed)) }
            item { DiagnosticRow("Average Speed", String.format(Locale.US, "%.1f mph", uiState.avgSpeed)) }
            item { DiagnosticRow("Speed Samples", uiState.speedCount.toString()) }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray, fontFamily = serpentine)
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = serpentine)
    }
}
