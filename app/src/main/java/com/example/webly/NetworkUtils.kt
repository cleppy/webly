package com.example.webly

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.util.Collections
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.math.roundToLong

// Data class to hold device information
data class ScannedDevice(
    val ipAddress: String,
    val hostname: String?,
    val macAddress: String = "N/A" // MAC address is often not available for other devices due to Android restrictions
)

object NetworkUtils {

    private const val TAG = "NetworkUtils"
    private const val TIMEOUT_MS = 1000 // Ping timeout in milliseconds
    private val httpClient = OkHttpClient() // OkHttp client instance

    private const val SPEED_TEST_FILE_URL = "http://speedtest.tele2.net/100MB.zip" // 100 MB test file
    private const val PING_TEST_HOST = "8.8.8.8" // Google DNS server for ping test

    /**
     * Gets the current Wi-Fi SSID (network name).
     * Requires ACCESS_FINE_LOCATION permission on Android 8.1+
     * @param context The application context.
     * @return The SSID of the connected Wi-Fi network, or null if not connected or permission denied.
     */
    fun getWifiSsid(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork ?: return null
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = wifiManager.connectionInfo
                val ssid = wifiInfo.ssid
                return if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
            }
        } else {
            @Suppress("DEPRECATION") // For older Android versions
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && wifiInfo.networkId != -1) {
                val ssid = wifiInfo.ssid
                return if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                    ssid.substring(1, ssid.length - 1)
                } else {
                    ssid
                }
            }
        }
        return null
    }

    /**
     * Gets the current device's IPv4 address.
     * Tries WifiManager.connectionInfo first, then iterates through NetworkInterfaces.
     * @param context The application context.
     * @return The IP address as a String, or null if not found.
     */
    fun getDeviceIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val ipAddressInt = dhcpInfo.ipAddress
        val formattedIpFromWifiInfo = formatIpAddress(ipAddressInt)

        if (ipAddressInt != 0 && formattedIpFromWifiInfo != "0.0.0.0" && formattedIpFromWifiInfo != "127.0.0.1") {
            Log.d(TAG, "Device IP from WifiInfo: $formattedIpFromWifiInfo")
            return formattedIpFromWifiInfo
        }

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isLoopback && intf.isUp) {
                    val addresses = Collections.list(intf.inetAddresses)
                    for (addr in addresses) {
                        if (!addr.isLoopbackAddress && addr.hostAddress != null) {
                            if (addr.hostAddress!!.indexOf(':') < 0) { // Check for IPv4
                                Log.d(TAG, "Device IP from NetworkInterface (${intf.displayName}): ${addr.hostAddress}")
                                return addr.hostAddress
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP from NetworkInterfaces: ${ex.message}")
        }
        Log.e(TAG, "Could not get a valid device IP address.")
        return null
    }

    /**
     * Formats an integer IP address (little-endian from dhcpInfo) into a standard string format (e.g., "192.168.1.1").
     * @param ip The IP address as an integer.
     * @return The formatted IP address string.
     */
    private fun formatIpAddress(ip: Int): String {
        return ((ip ushr 0 and 0xFF).toString() + "." +
                (ip ushr 8 and 0xFF).toString() + "." +
                (ip ushr 16 and 0xFF).toString() + "." +
                (ip ushr 24 and 0xFF).toString())
    }

    /**
     * Converts a dotted-decimal IP string to a little-endian integer.
     * This is the reverse of formatIpAddress.
     * This function is primarily for internal consistency with dhcpInfo's integer representation.
     */
    private fun ipStringToInt(ipAddress: String): Int {
        val parts = ipAddress.split(".").map { it.toInt() }
        if (parts.size != 4) {
            throw IllegalArgumentException("Invalid IP address format: $ipAddress")
        }
        // Construct the little-endian integer: (byte4 << 24) | (byte3 << 16) | (byte2 << 8) | byte1
        return (parts[0] and 0xFF) or
                ((parts[1] and 0xFF) shl 8) or
                ((parts[2] and 0xFF) shl 16) or
                ((parts[3] and 0xFF) shl 24)
    }

    /**
     * Scans the local network for active devices.
     * This function should be called from a Coroutine scope.
     * @param context The application context.
     * @return A Flow of found ScannedDevice objects.
     */
    fun scanNetworkForDevices(context: Context): Flow<ScannedDevice> = flow {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val ipAddressInt = dhcpInfo.ipAddress
        var netMaskInt = dhcpInfo.netmask

        Log.d(TAG, "Raw dhcpInfo.ipAddress: $ipAddressInt")
        Log.d(TAG, "Raw dhcpInfo.netmask: $netMaskInt")
        val deviceIpString = formatIpAddress(ipAddressInt)
        val netMaskString = formatIpAddress(netMaskInt) // This is the string representation of the little-endian netmask
        Log.d(TAG, "Formatted dhcpInfo.ipAddress: $deviceIpString")
        Log.d(TAG, "Formatted dhcpInfo.netmask: $netMaskString")


        if (ipAddressInt == 0) {
            Log.e(TAG, "IP address is zero. Cannot scan network.")
            return@flow
        }

        var actualNetmaskString = netMaskString
        if (netMaskInt == 0 || netMaskString == "0.0.0.0") {
            Log.w(TAG, "Netmask is zero or 0.0.0.0. Falling back to default 255.255.255.0 for network scan.")
            actualNetmaskString = "255.255.255.0" // Standard /24 netmask
        }

        try {
            val deviceInetAddress = InetAddress.getByName(deviceIpString)
            val netmaskInetAddress = InetAddress.getByName(actualNetmaskString) // Use the potentially corrected netmask string

            // Convert IP and Netmask to byte arrays (these will be big-endian)
            val ipBytes = deviceInetAddress.address
            val netmaskBytes = netmaskInetAddress.address

            if (ipBytes.size != 4 || netmaskBytes.size != 4) {
                Log.e(TAG, "Invalid IP or Netmask byte array size. Must be IPv4.")
                return@flow
            }

            val networkBytes = ByteArray(4)
            val broadcastBytes = ByteArray(4)

            // Calculate network address (IP AND Netmask)
            // Calculate broadcast address (IP OR (NOT Netmask))
            // These operations need to be done byte by byte on big-endian byte arrays
            for (i in 0..3) {
                networkBytes[i] = (ipBytes[i] and netmaskBytes[i])
                broadcastBytes[i] = (ipBytes[i] or netmaskBytes[i].inv())
            }

            val networkAddress = InetAddress.getByAddress(networkBytes)
            val broadcastAddress = InetAddress.getByAddress(broadcastBytes)

            // Convert network and broadcast addresses back to integers for iteration
            // We need to convert them to an integer representation that allows simple increment/decrement
            // For this, we'll convert them to "big-endian" long for safe iteration.
            val startIpLong = ipToLong(networkAddress) + 1
            val endIpLong = ipToLong(broadcastAddress) - 1

            // Ensure startIp is not greater than endIp and handle edge cases for small subnets
            val actualStartIpLong = if (startIpLong <= endIpLong) startIpLong else ipToLong(deviceInetAddress)
            val actualEndIpLong = if (endIpLong >= startIpLong) endIpLong else ipToLong(deviceInetAddress)

            Log.d(TAG, "Scanning from ${longToIpString(actualStartIpLong)} to ${longToIpString(actualEndIpLong)}")

            // Iterate through all possible IPs in the subnet
            for (i in actualStartIpLong..actualEndIpLong) {
                val hostIp = longToIpString(i)
                try {
                    val isReachable = withContext(Dispatchers.IO) {
                        try {
                            val inetAddress = InetAddress.getByName(hostIp)
                            // Use a small timeout for reachability check
                            inetAddress.isReachable(TIMEOUT_MS)
                        } catch (e: UnknownHostException) {
                            false // Should not happen with direct IP strings
                        } catch (e: IOException) {
                            false // Network issues
                        } catch (e: Exception) {
                            Log.e(TAG, "isReachable check failed for $hostIp: ${e.message}")
                            false
                        }
                    }

                    if (isReachable) {
                        val hostname = withContext(Dispatchers.IO) {
                            try {
                                InetAddress.getByName(hostIp).canonicalHostName // Get hostname
                            } catch (e: Exception) {
                                hostIp // Fallback to IP if hostname resolution fails
                            }
                        }
                        val scannedDevice = ScannedDevice(hostIp, hostname, "N/A")
                        Log.d(TAG, "Found device: $scannedDevice")
                        emit(scannedDevice)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "General exception during host scan for $hostIp: ${e.message}")
                }
            }
            Log.d(TAG, "Network scan completed.")

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating network/broadcast addresses or during scan setup: ${e.message}")
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Converts an InetAddress to a long for easier iteration.
     * Handles big-endian conversion correctly.
     */
    private fun ipToLong(ip: InetAddress): Long {
        val bytes = ip.address
        var result: Long = 0
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }

    /**
     * Converts a long back to an IP string.
     * Handles big-endian conversion correctly.
     */
    private fun longToIpString(ipLong: Long): String {
        return ((ipLong shr 24) and 0xFF).toString() + "." +
                ((ipLong shr 16) and 0xFF).toString() + "." +
                ((ipLong shr 8) and 0xFF).toString() + "." +
                (ipLong and 0xFF).toString()
    }


    /**
     * Performs a download speed test.
     * @return The download speed in Mbps (Megabits per second), or null if the test fails.
     */
    suspend fun performDownloadSpeedTest(): Double? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(SPEED_TEST_FILE_URL).build()
                val startTime = System.nanoTime()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download test file: ${response.code} ${response.message}")
                    return@withContext null
                }

                val contentLength = response.body?.contentLength() ?: -1L
                if (contentLength == -1L || contentLength == 0L) {
                    Log.e(TAG, "Content length not available or zero.")
                    return@withContext null
                }

                var totalBytesRead = 0L
                val buffer = ByteArray(8192) // 8KB buffer
                response.body?.byteStream()?.use { inputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        totalBytesRead += bytesRead
                    }
                }

                val endTime = System.nanoTime()
                val durationSeconds = (endTime - startTime) / 1_000_000_000.0 // Convert nanoseconds to seconds

                if (durationSeconds == 0.0) {
                    Log.e(TAG, "Duration was zero, cannot calculate speed.")
                    return@withContext null
                }

                val bytesPerSecond = totalBytesRead / durationSeconds
                val megaBitsPerSecond = (bytesPerSecond * 8) / 1_000_000.0

                Log.d(TAG, "Download Speed: ${"%.2f".format(megaBitsPerSecond)} Mbps")
                return@withContext megaBitsPerSecond

            } catch (e: IOException) {
                Log.e(TAG, "IOException during speed test: ${e.message}")
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "General exception during speed test: ${e.message}")
                return@withContext null
            }
        }
    }

    /**
     * Performs a simple ping test to a specified host.
     * @return The average round-trip time (RTT) in milliseconds, or null if ping fails.
     */
    suspend fun performPingTest(): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val pingCommand = "ping -c 4 $PING_TEST_HOST" // Ping 4 times
                val process = Runtime.getRuntime().exec(pingCommand)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                var totalPingTime = 0L
                var pingCount = 0

                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Ping output: $line")
                    val regex = "time=(\\d+\\.?\\d*)\\s?ms".toRegex()
                    val matchResult = regex.find(line!!)
                    if (matchResult != null) {
                        val pingTime = matchResult.groupValues[1].toDouble()
                        totalPingTime += pingTime.roundToLong()
                        pingCount++
                    }
                }
                process.waitFor()

                if (pingCount > 0) {
                    val averagePing = totalPingTime / pingCount
                    Log.d(TAG, "Average Ping: $averagePing ms")
                    return@withContext averagePing
                } else {
                    Log.e(TAG, "Could not parse ping results.")
                    return@withContext null
                }

            } catch (e: IOException) {
                Log.e(TAG, "IOException during ping test: ${e.message}")
                return@withContext null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Ping test interrupted: ${e.message}")
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "General exception during ping test: ${e.message}")
                return@withContext null
            }
        }
    }
}