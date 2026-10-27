package com.varunvaidhiya.robotcontrol.utils

/**
 * web_video_server-style MJPEG URLs (default port 8080).
 */
object WebVideoServerUrls {

    const val DEFAULT_PORT = 8080

    fun mjpegStreamUrl(
        host: String,
        topic: String = "/camera/front/image_raw",
        port: Int = DEFAULT_PORT,
        quality: Int = 50,
    ): String =
        "http://$host:$port/stream?topic=$topic&type=mjpeg&quality=$quality"
}
