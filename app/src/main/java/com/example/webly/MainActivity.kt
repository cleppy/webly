package com.example.webly

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.webly.ui.theme.WeblyTheme
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.webly.ui.theme.WeblyTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Location permissions granted.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Location permissions denied. Some features may not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permissions
        requestLocationPermissions()

        setContent {
            WeblyTheme {
                MyApp()
            }
        }
    }

    private fun requestLocationPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (packageManager.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, packageName) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (packageManager.checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION, packageName) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Permissions are already granted, you might want to log this
            Log.d("MainActivity", "Location permissions already granted.")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApp() {
    val navController = rememberNavController()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Device List", "Network Info & Speed Test")

    // Get the ViewModel instance
    val networkViewModel: NetworkViewModel = viewModel() // This will create or provide existing ViewModel

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Network Scanner", color = Color.White) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                selectedTabIndex = index
                                // Navigate to the corresponding route
                                when (index) {
                                    0 -> navController.navigate("device_list") {
                                        popUpTo("device_list") { inclusive = true }
                                    }
                                    1 -> navController.navigate("network_info") {
                                        popUpTo("network_info") { inclusive = true }
                                    }
                                }
                            },
                            text = { Text(text = title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "device_list",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("device_list") { DeviceListScreen(networkViewModel) } // Pass ViewModel
            composable("network_info") { NetworkInfoScreen(networkViewModel) } // Pass ViewModel
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WeblyTheme {
        MyApp()
    }
}