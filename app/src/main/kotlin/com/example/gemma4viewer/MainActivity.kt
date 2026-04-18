package com.example.gemma4viewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gemma4viewer.ui.MainScreen
import com.example.gemma4viewer.ui.theme.Gemma4CameraViewerTheme
import com.example.gemma4viewer.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Gemma4CameraViewerTheme {
                val vm: MainViewModel = viewModel()
                val appState by vm.appState.collectAsState()

                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasCameraPermission = granted
                }

                // 起動直後、権限未取得なら自動でダイアログを表示
                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        appState = appState,
                        viewModel = vm,
                        hasCameraPermission = hasCameraPermission,
                        onRequestCameraPermission = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
