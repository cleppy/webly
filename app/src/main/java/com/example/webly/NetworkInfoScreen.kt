package com.example.webly

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider // Divider'ı kullanmıyorsunuz, isterseniz kaldırabilirsiniz.
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
fun NetworkInfoScreen(viewModel: NetworkViewModel) {
    val ssid by viewModel.ssid.observeAsState(initial = "N/A")
    val deviceIpAddress by viewModel.deviceIpAddress.observeAsState(initial = "N/A")
    val downloadSpeed by viewModel.downloadSpeedMbps.observeAsState(initial = null)
    val pingMs by viewModel.pingMs.observeAsState(initial = null)
    val speedTestProgress by viewModel.speedTestProgress.observeAsState(initial = false)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Network Information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                text = "SSID: $ssid",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Device IP: $deviceIpAddress",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { viewModel.fetchNetworkInfo() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Refresh Network Info")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Internet Speed Test",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { viewModel.startSpeedTest() },
                enabled = !speedTestProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (speedTestProgress) "Testing..." else "Start Speed Test")
            }

            if (speedTestProgress) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(text = "Running speed test, please wait...", style = MaterialTheme.typography.bodyMedium)
            } else {
                // Her iki değer de null değilse göster
                if (downloadSpeed != null && pingMs != null) {
                    Text(
                        text = "Download Speed: %.2f Mbps".format(downloadSpeed),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Ping: ${pingMs} ms",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (downloadSpeed != null) { // Sadece indirme hızı varsa (Ping testi başarısız olmuş olabilir)
                    Text(
                        text = "Download Speed: %.2f Mbps".format(downloadSpeed),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Ping: N/A (Test failed)", // Ping sonucu alınamadığında bilgi ver
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (pingMs != null) { // Sadece ping varsa (İndirme testi başarısız olmuş olabilir)
                    Text(
                        text = "Download Speed: N/A (Test failed)", // İndirme sonucu alınamadığında bilgi ver
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Ping: ${pingMs} ms",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else { // Hiçbir sonuç yoksa veya test henüz yapılmadıysa
                    Text(
                        text = "Press 'Start Speed Test' to measure your internet speed.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}