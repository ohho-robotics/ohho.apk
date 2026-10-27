package com.varunvaidhiya.robotcontrol

import android.graphics.BitmapFactory
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
            val contentType = response.header("Content-Type")
            val boundary = multipartBoundary(contentType)
            assertTrue(
                "Content-Type is not multipart with a boundary: $contentType",
                contentType?.contains("multipart/", ignoreCase = true) == true &&
                    !boundary.isNullOrEmpty(),
            )
            val source = response.body?.source() ?: error("missing body")
            // MjpegView.readMjpegFrame only decodes a part that follows a "--boundary" line.
            val boundaryMarker = "--$boundary"
            var sawBoundary = false
            var attempts = 0
            while (attempts < 100 && !sawBoundary) {
                val line = source.readUtf8Line() ?: break
                attempts++
                if (line.trim() == boundaryMarker) sawBoundary = true
            }
            assertTrue(
                "body does not use the Content-Type boundary $boundaryMarker",
                sawBoundary,
            )
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
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            assertTrue("part payload is not a decodable JPEG", bitmap != null)
            bitmap?.recycle()
        }
    }

    /** Boundary parameter from a multipart Content-Type, or null when absent. */
    private fun multipartBoundary(contentType: String?): String? {
        if (contentType.isNullOrBlank()) return null
        for (part in contentType.split(';')) {
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            if (!trimmed.substring(0, eq).trim().equals("boundary", ignoreCase = true)) continue
            var value = trimmed.substring(eq + 1).trim()
            if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
                value = value.substring(1, value.length - 1)
            }
            return value.ifEmpty { null }
        }
        return null
    }
}
