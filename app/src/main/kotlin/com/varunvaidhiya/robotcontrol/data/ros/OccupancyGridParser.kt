package com.varunvaidhiya.robotcontrol.data.ros

import com.varunvaidhiya.robotcontrol.data.models.MapData

/**
 * Parses nav_msgs/OccupancyGrid JSON from ROSBridge into [MapData].
 */
object OccupancyGridParser {

    @Suppress("UNCHECKED_CAST")
    fun parse(msg: Map<String, Any>): MapData? {
        val info = msg["info"] as? Map<String, Any> ?: return null
        val width = (info["width"] as? Number)?.toInt() ?: return null
        val height = (info["height"] as? Number)?.toInt() ?: return null
        val res = (info["resolution"] as? Number)?.toFloat() ?: 0.05f
        val origin = info["origin"] as? Map<String, Any>
        val originPos = origin?.get("position") as? Map<String, Any>
        val originX = (originPos?.get("x") as? Number)?.toFloat() ?: 0f
        val originY = (originPos?.get("y") as? Number)?.toFloat() ?: 0f
        val header = msg["header"] as? Map<String, Any>
        val frameId = header?.get("frame_id") as? String ?: ""
        val rawData = msg["data"] as? List<Number> ?: return null
        if (width <= 0 || height <= 0) return null
        // Compare in Long so width*height cannot wrap (e.g. 65537*65537 -> 131073).
        val cellCount = width.toLong() * height.toLong()
        if (cellCount != rawData.size.toLong()) return null
        return MapData(
            width = width,
            height = height,
            resolution = res,
            originX = originX,
            originY = originY,
            frameId = frameId,
            cells = IntArray(rawData.size) { rawData[it].toInt() },
        )
    }
}
