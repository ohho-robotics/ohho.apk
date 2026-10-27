package com.varunvaidhiya.robotcontrol.data.repository

import com.google.gson.Gson
import com.varunvaidhiya.robotcontrol.network.ROSBridgeListener
import com.varunvaidhiya.robotcontrol.network.ROSBridgeManager
import com.varunvaidhiya.robotcontrol.utils.Constants
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentGoalPublishTest {

    @Test
    fun `connected manager publishes the rosbridge goal envelope`() {
        val socket = RecordingWebSocket()
        val manager = ROSBridgeManager("ws://127.0.0.1:1", noopListener)
        manager.attachSocketForTest(socket, ROSBridgeManager.ConnectionState.CONNECTED)

        val repo = RobotRepository()
        repo.attachRosManagerForTest(manager)
        repo.sendAgentGoal("pick up the red cup")

        assertEquals(1, socket.sent.size)
        @Suppress("UNCHECKED_CAST")
        val envelope = Gson().fromJson(socket.sent.single(), Map::class.java) as Map<String, Any>
        assertEquals("publish", envelope["op"])
        assertEquals(Constants.TOPIC_AGENT_GOAL, envelope["topic"])
        val msg = envelope["msg"] as Map<*, *>
        assertEquals("pick up the red cup", msg["data"])
    }

    @Test
    fun `disconnected manager sends nothing`() {
        val socket = RecordingWebSocket()
        val manager = ROSBridgeManager("ws://127.0.0.1:1", noopListener)
        manager.attachSocketForTest(socket, ROSBridgeManager.ConnectionState.DISCONNECTED)

        val repo = RobotRepository()
        repo.attachRosManagerForTest(manager)
        repo.sendAgentGoal("pick up the red cup")

        assertTrue(socket.sent.isEmpty())
    }

    private class RecordingWebSocket : WebSocket {
        val sent = mutableListOf<String>()

        override fun request(): Request = Request.Builder().url("ws://127.0.0.1:1").build()

        override fun queueSize(): Long = 0

        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun send(bytes: ByteString): Boolean = false

        override fun close(code: Int, reason: String?): Boolean = true

        override fun cancel() = Unit
    }

    private companion object {
        val noopListener = object : ROSBridgeListener {
            override fun onConnected() = Unit
            override fun onDisconnected() = Unit
            override fun onError(error: String) = Unit
            override fun onMessageReceived(topic: String, message: Map<String, Any>) = Unit
        }
    }
}
