package com.varunvaidhiya.robotcontrol.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.varunvaidhiya.robotcontrol.data.repository.RobotRepository
import com.varunvaidhiya.robotcontrol.network.ROSBridgeManager
import com.varunvaidhiya.robotcontrol.utils.WebVideoServerUrls
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: RobotRepository
) : ViewModel() {

    val connectionState = repository.connectionState

    // In a real app, this might come from a "camera_info" topic or settings
    // Defaulting to standard ros_web_video_server URL pattern
    private val _streamUrl = MutableStateFlow("")
    val streamUrl: StateFlow<String> = _streamUrl.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.connectionState, repository.robotStatus) { state, status ->
                Pair(state, status)
            }.collect { (state, status) ->
                _streamUrl.value = when {
                    state == ROSBridgeManager.ConnectionState.CONNECTED &&
                        status.ipAddress.isNotEmpty() ->
                        WebVideoServerUrls.mjpegStreamUrl(status.ipAddress)
                    else -> ""
                }
            }
        }
    }
}
