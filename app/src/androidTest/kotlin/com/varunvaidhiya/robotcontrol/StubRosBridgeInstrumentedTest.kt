package com.varunvaidhiya.robotcontrol

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Manual smoke test against [tools/rosbridge_stub.py] on the host.
 *
 * Human TODO (OHH-61): On an emulator with Settings → 10.0.2.2:9090, run:
 *   pip install -r tools/requirements-stub.txt
 *   python tools/rosbridge_stub.py
 * Then remove @Ignore and run this class from Android Studio or:
 *   ./gradlew connectedDebugAndroidTest
 */
class StubRosBridgeInstrumentedTest {

    private val client = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Ignore("Human TODO OHH-61: start rosbridge_stub on host, run on emulator")
    @Test
    fun rosbridgeStubPublishesOdomWithinFiveSeconds() {
        val latch = CountDownLatch(1)
        var sawOdom = false
        val ws = client.newWebSocket(
            Request.Builder().url("ws://10.0.2.2:9090").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    webSocket.send(
                        """{"op":"subscribe","topic":"/odom","type":"nav_msgs/Odometry"}""",
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("\"topic\":\"/odom\"") || text.contains("\"topic\": \"/odom\"")) {
                        sawOdom = true
                        latch.countDown()
                    }
                }
            },
        )
        assertTrue("Timed out waiting for /odom from stub", latch.await(5, TimeUnit.SECONDS))
        assertTrue(sawOdom)
        ws.close(1000, "test done")
    }

    @Ignore("Human TODO OHH-61: start rosbridge_stub on host, run on emulator")
    @Test
    fun mjpegStubReturnsMultipartStream() {
        val request = Request.Builder()
            .url("http://10.0.2.2:8080/stream?topic=/camera/front/image_raw&type=mjpeg")
            .build()
        client.newCall(request).execute().use { response ->
            assertTrue(response.isSuccessful)
            val source = response.body?.source() ?: error("missing body")
            val partHeaders = StringBuilder()
            var contentLength: Int? = null
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty()) {
                    if (contentLength != null) break
                    continue
                }
                partHeaders.append(line).append('\n')
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull()
                }
            }
            val length = contentLength
            assertTrue("first MJPEG part missing Content-Length", length != null && length > 0)
            val jpeg = source.readByteArray(length!!.toLong())
            assertTrue(partHeaders.contains("Content-Type: image/jpeg"))
            assertTrue(
                "part payload is not a JPEG",
                jpeg.size >= 2 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte(),
            )
        }
    }
}
