package com.varunvaidhiya.robotcontrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varunvaidhiya.robotcontrol.data.preferences.AppPreferences
import com.varunvaidhiya.robotcontrol.data.repository.RobotRepository
import com.varunvaidhiya.robotcontrol.network.ROSBridgeManager.ConnectionState
import com.varunvaidhiya.robotcontrol.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Global app ViewModel, tied to MainActivity lifecycle.
 * Opens ROSBridge from the IP and port saved in Settings, and again whenever they change.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: RobotRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val robotStatus = repository.robotStatus

    init {
        viewModelScope.launch {
            combine(preferences.robotIp, preferences.robotPort) { ip, port -> ip to port }
                .distinctUntilChanged()
                .collect { (ip, port) ->
                    if (ip.isNotBlank() && port in 1..65535) {
                        repository.connect(ip, port)
                    }
                }
        }
    }

    fun connectToRobot(ip: String, port: Int = Constants.DEFAULT_ROSBRIDGE_PORT) {
        viewModelScope.launch {
            repository.connect(ip, port)
        }
    }

    fun disconnect() {
        repository.disconnect()
    }
}
