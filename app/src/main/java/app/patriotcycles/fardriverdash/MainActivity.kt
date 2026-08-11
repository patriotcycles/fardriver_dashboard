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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
            ActivityResultContracts.RequestMultiplePermissions(),
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
                Manifest.permission.ACCESS_FINE_LOCATION,
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
    var currentScreen by remember { mutableStateOf(value = Screen.Dashboard) }
    var showConnectionResetDialog by remember { mutableStateOf(value = false) }
    var hasPromptedThisSession by remember { mutableStateOf(value = false) }

    val status by repository.connectionState.collectAsState()

    LaunchedEffect(status) {
        if ((status == "Connected") && !hasPromptedThisSession) {
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
                    fontFamily = serpentine,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.resetTrip()
                        showConnectionResetDialog = false
                    },
                ) {
                    Text("Reset", color = Color.Red, fontFamily = serpentine)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConnectionResetDialog = false }) {
                    Text(
                        "Keep Data",
                        fontFamily = serpentine,
                    )
                }
            },
        )
    }

    when (currentScreen) {
        Screen.Dashboard -> DashboardScreen(
            repository = repository,
            onOpenSettings = { currentScreen = Screen.Settings },
            onOpenConnection = { currentScreen = Screen.Connection },
        ) { currentScreen = Screen.Diagnostics }

        Screen.Settings -> SettingsScreen(repository = repository) {
            currentScreen = Screen.Dashboard
        }

        Screen.Connection -> ConnectionScreen(repository = repository) {
            currentScreen = Screen.Dashboard
        }

        Screen.Diagnostics -> DiagnosticsScreen(repository = repository) {
            currentScreen = Screen.Dashboard
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: FardriverRepository,
    onOpenSettings: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val uiState by repository.uiState.collectAsState()
    val status by repository.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fardriver Dashboard", fontFamily = serpentine) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = iconColor,
                        )
                    }
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Default.Info, contentDescription = "Diagnostics")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // Top Gauges Section
            val settings by repository.settings.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            ) {
                // AMPS Gauge (Left)
                AnalogGauge(
                    value = uiState.lineCurrent,
                    minValue = settings.ampsMin,
                    maxValue = settings.ampsMax,
                    label = "AMPS",
                    isBoostActive = uiState.lineCurrent >= (settings.ampsMax + 5f),
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.BottomStart)
                        .offset(x = (-20).dp),
                )

                // MPH Gauge (Right)
                AnalogGauge(
                    value = uiState.gpsSpeed,
                    minValue = settings.mphMin,
                    maxValue = settings.mphMax,
                    label = "MPH",
                    gear = uiState.gear,
                    topspeed = uiState.maxSpeed,
                    avgspeed = uiState.avgSpeed,
                    modifier = Modifier
                        .size(240.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (20).dp),
                )
            }

            // Battery Level Meter (Moved up)
            BatteryLevelMeter(
                voltage = uiState.voltage,
                minV = settings.voltageMin,
                maxV = settings.voltageMax,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Temp Gauges Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnalogGauge(
                    value = uiState.controllerTemp,
                    minValue = settings.tempMin,
                    maxValue = settings.tempMax,
                    label = "CTRL\nTEMP",
                    showDigitalValue = false,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    modifier = Modifier.weight(1f).height(80.dp),
                )
                AnalogGauge(
                    value = uiState.motorTemp,
                    minValue = settings.tempMin,
                    maxValue = settings.tempMax,
                    label = "MOTR\nTEMP",
                    showDigitalValue = false,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    modifier = Modifier.weight(1f).height(80.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

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
    label: String,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    startAngle: Float = 140f,
    sweepAngle: Float = 260f,
    gear: Int? = null,
    topspeed: Float? = null,
    avgspeed: Float? = null,
    warningValue: Float? = null,
    showDigitalValue: Boolean = true,
    isBoostActive: Boolean = false,
) {
    val displayWarningValue = warningValue ?: (minValue + ((maxValue - minValue) * 0.8f))
    val baseColor = if (isBoostActive) Color.Red else Color.White
    val accentColor = if (isBoostActive) Color.Red else Color(0xFF00E676) // Modern Green
    val isSemiCircle = sweepAngle <= 181f

    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val widthDp = maxWidth
        val heightDp = maxHeight
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = if (isSemiCircle) Offset(size.width / 2, size.height * 0.9f) else Offset(size.width / 2, size.height / 2)
            val radius = if (isSemiCircle) size.height * 0.85f else size.width / 2
            val innerRadius = radius * 0.82f
            val outerRadius = radius * 0.96f
            val tickInnerRadius = radius * 0.88f
            val tickOuterRadius = radius * 0.94f

            // 1. Outer subtle border arc
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = startAngle - 2f,
                sweepAngle = sweepAngle + 4f,
                useCenter = false,
                topLeft = center - Offset(outerRadius, outerRadius),
                size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = 2f)
            )

            // 2. Track Background (Dim glow)
            drawArc(
                color = accentColor.copy(alpha = 0.1f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = center - Offset(innerRadius, innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 14f, cap = StrokeCap.Butt)
            )

            // Warning Segment (Red part of the background track)
            val warningStartAngle = startAngle + (((displayWarningValue - minValue) / (maxValue - minValue)) * sweepAngle)
            val warningSweep = sweepAngle - (warningStartAngle - startAngle)
            drawArc(
                color = Color.Red.copy(alpha = 0.3f),
                startAngle = warningStartAngle,
                sweepAngle = warningSweep,
                useCenter = false,
                topLeft = center - Offset(innerRadius, innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 14f, cap = StrokeCap.Butt)
            )

            // 3. Ticks
            val range = maxValue - minValue
            val tickStep = if (range > 150) 2f else 1f // Adjust density based on range
            val numTicks = (range / tickStep).toInt()
            
            for (i in 0..numTicks) {
                val tickValue = minValue + (i * tickStep)
                val angle = startAngle + (((i.toFloat() * tickStep) / range) * sweepAngle)
                val rad = Math.toRadians(angle.toDouble())
                
                val isMajor = (tickValue.toInt() % 10) == 0
                val isMid = ((tickValue.toInt() % 5) == 0) && !isMajor
                
                val tStart = if (isMajor) tickInnerRadius - 10f else if (isMid) tickInnerRadius - 6f else tickInnerRadius

                val color = if (isMajor) Color.White else Color.White.copy(alpha = 0.4f)
                val stroke = if (isMajor) 2.5f else 1f

                drawLine(
                    color = color,
                    start = Offset(
                        center.x + (tStart * cos(rad)).toFloat(),
                        center.y + (tStart * sin(rad)).toFloat()
                    ),
                    end = Offset(
                        center.x + (tickOuterRadius * cos(rad)).toFloat(),
                        center.y + (tickOuterRadius * sin(rad)).toFloat()
                    ),
                    strokeWidth = stroke
                )
            }

            // 4. Progress Arc
            val valuePercentage = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1.1f)
            val valueSweep = valuePercentage * sweepAngle
            
            drawArc(
                color = accentColor,
                startAngle = startAngle,
                sweepAngle = valueSweep,
                useCenter = false,
                topLeft = center - Offset(innerRadius, innerRadius),
                size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = 14f, cap = StrokeCap.Butt)
            )

            // 5. Indicator Marker (Peripheral only)
            val needleAngle = startAngle + valueSweep
            val needleRad = Math.toRadians(needleAngle.toDouble())
            
            drawLine(
                color = Color.Red,
                start = Offset(
                    center.x + (innerRadius * cos(needleRad)).toFloat(),
                    center.y + (innerRadius * sin(needleRad)).toFloat()
                ),
                end = Offset(
                    center.x + (outerRadius * 1.05f * cos(needleRad)).toFloat(),
                    center.y + (outerRadius * 1.05f * sin(needleRad)).toFloat()
                ),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        // Labels inside the ring
        val labelRadius = if (isSemiCircle) (heightDp / 0.85f) * 0.76f
        else (widthDp / 2) * 0.72f
  //      val labelRadius = (widthDp / 2) * 0.90f
        val labelStep = if (maxValue > 100) 20f else 10f
        val numLabels = ((maxValue - minValue) / labelStep).toInt()
        
        for (i in 0..numLabels) {
            val labelValue = minValue + (i * labelStep)
            val angle = startAngle + (((i.toFloat() * labelStep) / (maxValue - minValue)) * sweepAngle)
            val rad = Math.toRadians(angle.toDouble())

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isSemiCircle) Alignment.BottomCenter
                else Alignment.Center
            ) {
                Text(
                    text = labelValue.toInt().toString(),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontFamily = serpentine,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(
                        x = (labelRadius.value * cos(rad)).dp,
                        y = (labelRadius.value * sin(rad)).dp - (if (isSemiCircle) 2.dp else 0.dp)
                    )
                )
            }
        }

        if (showDigitalValue) {
            // Digital Readout (Top Half)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = (-widthDp.value * 0.05f).dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f", value),
                    color = baseColor,
                    fontSize = (widthDp.value * 0.14f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = serpentine
                )
            }
        }

        // Label and Gear (Bottom or Mid-Top)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(if (isSemiCircle) Alignment.BottomCenter else Alignment.Center)
                .offset(
                    y = if (showDigitalValue) (widthDp.value * 0.26f).dp 
                        else if (isSemiCircle) (2).dp
                        else (-widthDp.value * 0.12f).dp
                )
        ) {
            Text(
                text = if (isBoostActive) "BOOST" else label,
                color = if (isBoostActive) Color.Red else Color.Gray,
                fontSize = (widthDp.value * (if (showDigitalValue) 0.06f else 0.08f)).sp,
                fontFamily = serpentine,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (gear != null) {
                Text(
                    text = gear.toString(),
                    fontSize = (widthDp.value * 0.09f).sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = serpentine
                )
                topspeed?.let {
                    Text(
                        text = String.format(Locale.US, "MAX %.1f", it),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = (widthDp.value * 0.045f).sp,
                        fontFamily = serpentine,
                        fontWeight = FontWeight.Bold
                    )
                }
                avgspeed?.let {
                    Text(
                        text = String.format(Locale.US, "AVG %.1f", it),
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = (widthDp.value * 0.045f).sp,
                        fontFamily = serpentine,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                        i <= 2 -> Color(0xFFD32F2F) // Red (2 blocks)
                        i <= 4 -> Color(0xFFF57C00) // Orange (2 blocks)
                        i <= 5 -> Color(0xFFFBC02D) // Yellow (1 block)
                        else -> Color(0xFF388E3C)   // Green (Rest)
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

@Composable
fun StatsSection(data: FardriverData, settings: FardriverSettings) {
    val ahMi = if (data.tripMiles > 0.1) data.usedAh / data.tripMiles else 0.0
    val range = if (ahMi > 0) (settings.batteryCapacityAh - data.usedAh) / ahMi else 0.0
    val totalOdo = data.odometerMiles + settings.odometerOffset

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF424242)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "TRIP", String.format(Locale.US, "%.1f", data.tripMiles), "mi")
                StatItem(Modifier.weight(1f), "TIME", formatSeconds(data.tripSeconds), "")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "USED AH", String.format(Locale.US, "%.1f", data.usedAh), "ah")
                StatItem(Modifier.weight(1f), "ODO", String.format(Locale.US, "%.0f", totalOdo), "mi")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(Modifier.weight(1f), "RANGE EST", String.format(Locale.US, "%.1f", range), "mi")
                StatItem(Modifier.weight(1f), "AH/MI", String.format(Locale.US, "%.1f", ahMi), "")
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
            fontSize = 11.sp,
            fontFamily = serpentine,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .width(110.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0D0D0D))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background "shadow" for LCD segments
            Text(
                text = value.replace(Regex("[0-9]"), "8"),
                color = Color(0xFF1A1A1A),
                fontSize = 20.sp,
                fontFamily = dseg7,
                textAlign = TextAlign.Center
            )
            // Active segments
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = Color(0xFF00E676), // Classic green LCD look
                    fontSize = 20.sp,
                    fontFamily = dseg7,
                    textAlign = TextAlign.Center
                )
                if (unit.isNotEmpty()) {
                    Text(
                        text = " $unit",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontFamily = serpentine,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

fun formatSeconds(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
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

            if (devices.isEmpty() && (status == "Scanning...")) {
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
    var tempMin by remember(settings) { mutableStateOf(settings.tempMin.toString()) }
    var tempMax by remember(settings) { mutableStateOf(settings.tempMax.toString()) }

    var showTripResetDialog by remember { mutableStateOf(value = false) }

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
                TextButton(
                    onClick = {
                        repository.resetTrip()
                        showTripResetDialog = false
                    },
                ) {
                    Text("Reset", color = Color.Red, fontFamily = serpentine)
                }
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
                "Battery & Odometer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = serpentine
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = battCapacity,
                    onValueChange = { battCapacity = it },
                    label = { Text("Battery (Ah)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = odoOffset,
                    onValueChange = { odoOffset = it },
                    label = { Text("Odo Offset") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

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

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = tempMin,
                    onValueChange = { tempMin = it },
                    label = { Text("Temp Min (°F)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = tempMax,
                    onValueChange = { tempMax = it },
                    label = { Text("Temp Max (°F)") },
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
                        voltageMax = voltageMax.toFloatOrNull() ?: settings.voltageMax,
                        tempMin = tempMin.toFloatOrNull() ?: settings.tempMin,
                        tempMax = tempMax.toFloatOrNull() ?: settings.tempMax
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
            item { DiagnosticRow("Gear", uiState.gear.toString()) }
            item { DiagnosticRow("Speed", String.format(Locale.US, "%.1f mph", uiState.speed)) }
            item { DiagnosticRow("Controller Temp", String.format(Locale.US, "%.1f °F", uiState.controllerTemp)) }
            item { DiagnosticRow("Motor Temp", String.format(Locale.US, "%.1f °F", uiState.motorTemp)) }
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
