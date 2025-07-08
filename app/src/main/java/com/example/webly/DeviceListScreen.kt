package com.example.webly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DeviceListScreen(viewModel: NetworkViewModel) {
    val deviceList by viewModel.deviceList.observeAsState(initial = emptyList())
    val scanProgress by viewModel.scanProgress.observeAsState(initial = false)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Button(
                onClick = { viewModel.startScan() },
                enabled = !scanProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (scanProgress) "Scanning..." else "Scan Devices")
            }

            if (scanProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(text = "Scanning network, please wait...", style = MaterialTheme.typography.bodyMedium)
            }

            if (deviceList.isEmpty() && !scanProgress) {
                Text(text = "No devices found yet. Tap 'Scan Devices' to start.", modifier = Modifier.padding(top = 16.dp))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp)
                ) {
                    items(deviceList) { device ->
                        DeviceCard(device = device)
                        // No need for a separate Divider if using Card's default padding/margin effectively
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: ScannedDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "IP: ${device.ipAddress}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (device.hostname != null && device.hostname.isNotBlank() && device.hostname != device.ipAddress) {
                Text(text = "Hostname: ${device.hostname}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = "MAC: ${device.macAddress}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}