package com.varunvaidhiya.robotcontrol.data.ros

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
        assertTrue(map.cells.contentEquals(intArrayOf(-1, 0, 100, 0)))
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
}
