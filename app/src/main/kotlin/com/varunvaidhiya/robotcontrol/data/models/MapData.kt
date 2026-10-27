package com.varunvaidhiya.robotcontrol.data.models

/**
 * Occupancy grid map from /map (nav_msgs/OccupancyGrid)
 * Cell values: -1 = unknown, 0 = free, 100 = occupied
 */
data class MapData(
    val width: Int = 0,
    val height: Int = 0,
    val resolution: Float = 0.05f,  // meters per cell
    val originX: Float = 0f,        // map origin in meters
    val originY: Float = 0f,
    /** header.frame_id. Empty until an occupancy grid for this connection arrives. */
    val frameId: String = "",
    val cells: IntArray = IntArray(0),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Convert a pose already expressed in this grid's frame to pixel coordinates.
     * Y is flipped so row 0 of the grid (the origin row) is the bottom of the bitmap.
     */
    fun metersToPixel(worldX: Float, worldY: Float): Pair<Float, Float> {
        val px = (worldX - originX) / resolution
        val py = height - (worldY - originY) / resolution  // flip Y axis
        return Pair(px, py)
    }

    /**
     * Pixel pose of [odom], or null when it must not be drawn.
     *
     * This app does not subscribe to TF. Odometry meters are in [OdomData.frameId]
     * and grid meters are in [frameId]. The pose is projected only when both frames
     * are non-empty and equal after dropping a single leading slash (identity map←odom).
     */
    fun posePixelsIfSameFrame(odom: OdomData): Pair<Float, Float>? {
        if (cells.isEmpty() || resolution <= 0f) return null
        if (!sameRosFrame(frameId, odom.frameId)) return null
        return metersToPixel(odom.x, odom.y)
    }
}

/** Bitmap index for occupancy cell [cellIndex], with grid row 0 at the bottom. */
internal fun occupancyBitmapIndex(cellIndex: Int, width: Int, height: Int): Int {
    val row = cellIndex / width
    val col = cellIndex % width
    return (height - 1 - row) * width + col
}

private fun sameRosFrame(mapFrame: String, odomFrame: String): Boolean {
    fun norm(id: String) = id.trim().removePrefix("/")
    val left = norm(mapFrame)
    val right = norm(odomFrame)
    return left.isNotEmpty() && left == right
}
