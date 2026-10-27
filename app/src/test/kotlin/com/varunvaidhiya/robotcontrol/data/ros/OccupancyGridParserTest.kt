package com.varunvaidhiya.robotcontrol.data.ros

import com.varunvaidhiya.robotcontrol.data.models.MapData
import com.varunvaidhiya.robotcontrol.data.models.OdomData
import com.varunvaidhiya.robotcontrol.data.models.occupancyBitmapIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccupancyGridParserTest {

    @Test
    fun `parses occupancy grid dimensions and cells`() {
        val msg = mapOf(
            "info" to mapOf(
                "width" to 2,
                "height" to 2,
                "resolution" to 0.1,
                "origin" to mapOf(
                    "position" to mapOf("x" to 1.0, "y" to 2.0, "z" to 0.0),
                ),
            ),
            "data" to listOf(-1, 0, 100, 0),
        )

        val map = OccupancyGridParser.parse(msg)
        assertNotNull(map)
        assertEquals(2, map!!.width)
        assertEquals(2, map.height)
        assertEquals(0.1f, map.resolution, 0.0001f)
        assertEquals(1.0f, map.originX, 0.0001f)
        assertEquals(2.0f, map.originY, 0.0001f)
        assertEquals("", map.frameId)
        assertTrue(map.cells.contentEquals(intArrayOf(-1, 0, 100, 0)))
    }

    @Test
    fun `parses occupancy grid header frame id`() {
        val msg = mapOf(
            "header" to mapOf("frame_id" to "map"),
            "info" to mapOf(
                "width" to 1,
                "height" to 1,
                "resolution" to 0.05,
            ),
            "data" to listOf(0),
        )
        assertEquals("map", OccupancyGridParser.parse(msg)!!.frameId)
    }

    @Test
    fun `rejects non-positive dimensions even when the cell count matches`() {
        val zeroWidth = mapOf(
            "info" to mapOf("width" to 0, "height" to 4, "resolution" to 0.05),
            "data" to emptyList<Int>(),
        )
        assertNull(OccupancyGridParser.parse(zeroWidth))

        val negative = mapOf(
            "info" to mapOf("width" to -2, "height" to -2, "resolution" to 0.05),
            "data" to listOf(0, 0, 0, 0),
        )
        assertNull(OccupancyGridParser.parse(negative))
    }

    @Test
    fun `rejects dimensions whose product overflows Int`() {
        val width = 65537
        val height = 65537
        val wrappedProduct = width * height
        assertEquals(131073, wrappedProduct)
        val msg = mapOf(
            "info" to mapOf("width" to width, "height" to height, "resolution" to 0.05),
            "data" to List(wrappedProduct) { 0 },
        )
        assertNull(OccupancyGridParser.parse(msg))
    }

    @Test
    fun `rejects grid when data length mismatches width times height`() {
        val msg = mapOf(
            "info" to mapOf("width" to 2, "height" to 2, "resolution" to 0.05),
            "data" to listOf(0, 0),
        )
        assertNull(OccupancyGridParser.parse(msg))
    }

    @Test
    fun `metersToPixel flips y axis per map origin`() {
        val map = OccupancyGridParser.parse(
            mapOf(
                "info" to mapOf(
                    "width" to 10,
                    "height" to 10,
                    "resolution" to 0.5,
                    "origin" to mapOf("position" to mapOf("x" to 0.0, "y" to 0.0)),
                ),
                "data" to List(100) { 0 },
            ),
        )!!
        val (px, py) = map.metersToPixel(2.5f, 2.5f)
        assertEquals(5f, px, 0.01f)
        assertEquals(5f, py, 0.01f)
    }

    @Test
    fun `pose pixels require the odom frame to match the map frame`() {
        val map = MapData(
            width = 10,
            height = 10,
            resolution = 0.5f,
            frameId = "map",
            cells = IntArray(100),
        )
        val same = map.posePixelsIfSameFrame(OdomData(x = 2.5f, y = 2.5f, frameId = "/map"))
        assertNotNull(same)
        assertEquals(5f, same!!.first, 0.01f)
        assertEquals(5f, same.second, 0.01f)
        assertNull(map.posePixelsIfSameFrame(OdomData(x = 2.5f, y = 2.5f, frameId = "odom")))
        assertNull(map.posePixelsIfSameFrame(OdomData(x = 2.5f, y = 2.5f, frameId = "")))
        assertNull(map.copy(frameId = "").posePixelsIfSameFrame(OdomData(frameId = "")))
    }

    @Test
    fun `occupancy row 0 is stored on the bottom bitmap row`() {
        assertEquals(2, occupancyBitmapIndex(0, width = 2, height = 2))
        assertEquals(3, occupancyBitmapIndex(1, width = 2, height = 2))
        assertEquals(0, occupancyBitmapIndex(2, width = 2, height = 2))
        assertEquals(1, occupancyBitmapIndex(3, width = 2, height = 2))
    }
}
