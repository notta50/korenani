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
import androidx.lifecycle.ViewModelProvider
import com.example.gemma4viewer.repository.InferenceRepositoryImpl
import com.example.gemma4viewer.repository.ModelRepositoryImpl
import com.example.gemma4viewer.ui.MainScreen
import com.example.gemma4viewer.ui.theme.Gemma4CameraViewerTheme
import com.example.gemma4viewer.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val modelRepo = ModelRepositoryImpl(filesDir)
        val inferenceRepo = InferenceRepositoryImpl(applicationInfo.nativeLibraryDir)
        val vmFactory = MainViewModel.Factory(modelRepo, inferenceRepo)

        setContent {
            Gemma4CameraViewerTheme {
                val vm = ViewModelProvider(this, vmFactory)[MainViewModel::class.java]
                val appState by vm.appState.collectAsState()
                val capturedBitmap by vm.capturedBitmap.collectAsState()

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

                LaunchedEffect(Unit) {
                    vm.onAppStart()
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        appState = appState,
                        capturedBitmap = capturedBitmap,
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
