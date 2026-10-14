package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentGoalPublishTest {

    @After
    fun tearDown() {
        RobotRepository.testPublishSink = null
    }

    @Test
    fun `sendAgentGoal publishes std_msgs String on agent goal topic`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        RobotRepository.testPublishSink = { topic, msg -> published.add(topic to msg) }

        val repo = RobotRepository()
        repo.sendAgentGoal("pick up the red cup")

        assertEquals(1, published.size)
        assertEquals(Constants.TOPIC_AGENT_GOAL, published[0].first)
        assertEquals("pick up the red cup", published[0].second["data"])
    }
}
