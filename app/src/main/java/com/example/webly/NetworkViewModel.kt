package com.example.webly

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    // Network Scan related LiveData
    private val _deviceList = MutableLiveData<List<ScannedDevice>>()
    val deviceList: LiveData<List<ScannedDevice>> = _deviceList

    private val _scanProgress = MutableLiveData<Boolean>()
    val scanProgress: LiveData<Boolean> = _scanProgress

    // Network Info related LiveData
    private val _ssid = MutableLiveData<String?>()
    val ssid: LiveData<String?> = _ssid

    private val _deviceIpAddress = MutableLiveData<String?>()
    val deviceIpAddress: LiveData<String?> = _deviceIpAddress

    // Speed Test related LiveData
    private val _downloadSpeedMbps = MutableLiveData<Double?>()
    val downloadSpeedMbps: LiveData<Double?> = _downloadSpeedMbps

    private val _pingMs = MutableLiveData<Long?>()
    val pingMs: LiveData<Long?> = _pingMs

    private val _speedTestProgress = MutableLiveData<Boolean>()
    val speedTestProgress: LiveData<Boolean> = _speedTestProgress

    init {
        // Initialize LiveData values
        _deviceList.value = emptyList()
        _scanProgress.value = false // Initial state: not scanning

        _ssid.value = null // Initial state: no SSID info
        _deviceIpAddress.value = null // Initial state: no IP info

        _downloadSpeedMbps.value = null // Initial state: no download speed result
        _pingMs.value = null // Initial state: no ping result
        _speedTestProgress.value = false // Initial state: not running speed test
    }

    /**
     * Scans the network for active devices and updates the device list.
     * This function should be called from the UI.
     */
    fun startScan() {
        _scanProgress.value = true
        _deviceList.value = emptyList() // Clear previous scan results before starting a new scan
        viewModelScope.launch {
            val foundDevices = mutableListOf<ScannedDevice>()
            // Collect ScannedDevice objects from the flow
            NetworkUtils.scanNetworkForDevices(getApplication()).collectLatest { device ->
                foundDevices.add(device)
                _deviceList.postValue(foundDevices.toList()) // Use postValue for background updates
            }
            _scanProgress.postValue(false) // Use postValue for background updates
        }
    }

    /**
     * Fetches current network information like SSID and device IP address.
     * This function should be called from the UI.
     */
    fun fetchNetworkInfo() {
        viewModelScope.launch {
            val currentSsid = NetworkUtils.getWifiSsid(getApplication())
            val currentIpAddress = NetworkUtils.getDeviceIpAddress(getApplication())
            _ssid.postValue(currentSsid) // Use postValue for background updates
            _deviceIpAddress.postValue(currentIpAddress) // Use postValue for background updates
        }
    }

    /**
     * Starts the speed test (download and ping).
     * Updates downloadSpeedMbps and pingMs LiveData with the results.
     */
    fun startSpeedTest() {
        _speedTestProgress.value = true // Set progress to true immediately on the main thread
        _downloadSpeedMbps.value = null // Clear previous download result on the main thread
        _pingMs.value = null // Clear previous ping result on the main thread

        viewModelScope.launch {
            // Launch both tests concurrently using async
            val speedDeferred = async { NetworkUtils.performDownloadSpeedTest() }
            val pingDeferred = async { NetworkUtils.performPingTest() }

            val speed = speedDeferred.await() // Wait for download test to complete
            val ping = pingDeferred.await() // Wait for ping test to complete

            _downloadSpeedMbps.postValue(speed) // Post download result back to main thread
            _pingMs.postValue(ping) // Post ping result back to main thread
            _speedTestProgress.postValue(false) // Set progress to false when both tests are complete
        }
    }
}