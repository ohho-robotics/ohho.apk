package com.varunvaidhiya.robotcontrol.ui.home

import androidx.lifecycle.ViewModel
import com.varunvaidhiya.robotcontrol.data.models.HomeTelemetry
import com.varunvaidhiya.robotcontrol.data.repository.RobotRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: RobotRepository,
) : ViewModel() {

    val connectionState = repository.connectionState
    val robotStatus = repository.robotStatus
    val homeTelemetry = repository.homeTelemetry
    val odomData = repository.odomData
    val armJointPositions = repository.armJointPositions
}
