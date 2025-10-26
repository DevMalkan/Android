package com.guide.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.guide.app.camera.CameraAnalyzer
import com.guide.app.ui.theme.GuideAppTheme
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isServiceRunning by mutableStateOf(false)
    private var showDebugScreen by mutableStateOf(false)
    private var hasOnboarded by mutableStateOf(false)

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startGuidanceService()
        } else {
            tts?.speak("Camera permission is required for guidance", TextToSpeech.QUEUE_FLUSH, null, "permission")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        setContent {
            GuideAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showDebugScreen) {
                        DebugScreen(
                            onBack = { showDebugScreen = false }
                        )
                    } else {
                        MainScreen(
                            isServiceRunning = isServiceRunning,
                            onToggle = { toggleService() },
                            onTitleLongPress = { showDebugScreen = true }
                        )
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            if (!hasOnboarded) {
                hasOnboarded = true
                tts?.speak(
                    "Welcome to Guide. Use the large toggle button to start or stop guidance.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "onboarding"
                )
            }
        }
    }

    private fun toggleService() {
        if (isServiceRunning) {
            stopGuidanceService()
        } else {
            checkPermissionsAndStart()
        }
    }

    private fun checkPermissionsAndStart() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startGuidanceService()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startGuidanceService() {
        val intent = Intent(this, GuidanceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        CameraAnalyzer.resetStats()
        tts?.speak("Guidance started", TextToSpeech.QUEUE_FLUSH, null, "start")
    }

    private fun stopGuidanceService() {
        val intent = Intent(this, GuidanceService::class.java)
        stopService(intent)
        isServiceRunning = false
        tts?.speak("Guidance stopped", TextToSpeech.QUEUE_FLUSH, null, "stop")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    isServiceRunning: Boolean,
    onToggle: () -> Unit,
    onTitleLongPress: () -> Unit
) {
    val buttonLabel = if (isServiceRunning) {
        "Pause Guidance"
    } else {
        "Start Guidance"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Guide",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = onTitleLongPress
                )
                .padding(bottom = 48.dp)
        )

        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics {
                    contentDescription = buttonLabel
                }
        ) {
            Text(
                text = buttonLabel,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val frameCount by remember {
        derivedStateOf { CameraAnalyzer.frameCount }
    }
    val stopCount by remember {
        derivedStateOf { CameraAnalyzer.stopCount }
    }
    val veerCount by remember {
        derivedStateOf { CameraAnalyzer.veerCount }
    }
    val lastToken by remember {
        derivedStateOf { CameraAnalyzer.lastToken }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Debug Info",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text("Frames: $frameCount", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Stops: $stopCount", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Veers: $veerCount", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Last Token: $lastToken", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}
