package com.varunvaidhiya.robotcontrol.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.varunvaidhiya.robotcontrol.data.models.occupancyBitmapIndex
import timber.log.Timber

/**
 * Custom View to render 2D Occupancy Grid Map (SLAM)
 * Supports Zoom and Pan gestures
 */
class SlamMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Map Data
    private var mapBitmap: Bitmap? = null
    private var robotPoseX = 0f
    private var robotPoseY = 0f
    private var robotYaw = 0f
    private var robotVisible = false
    
    // Viewport Transformation
    private var scaleFactor = 1.0f
    private var transX = 0f
    private var transY = 0f
    
    // Paints
    private val robotPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val pathPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Gestures
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    /**
     * Update map data
     * @param width Map width in cells
     * @param height Map height in cells
     * @param data Occupancy grid data (-1: unknown, 0: free, 100: occupied)
     */
    fun updateMap(width: Int, height: Int, data: IntArray) {
        if (width <= 0 || height <= 0) return
        
        // Create or reuse bitmap
        if (mapBitmap == null || mapBitmap?.width != width || mapBitmap?.height != height) {
            mapBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        
        val pixels = IntArray(width * height)
        // Grid row 0 is the map origin row. Place it at the bottom so it matches
        // MapData.metersToPixel, which flips Y.
        val count = minOf(data.size, width * height)
        for (i in 0 until count) {
            val cell = data[i]
            pixels[occupancyBitmapIndex(i, width, height)] = when(cell) {
                -1 -> Color.GRAY         // Unknown
                100 -> Color.BLACK      // Occupied
                else -> Color.WHITE     // Free (0-99)
            }
        }
        
        mapBitmap?.setPixels(pixels, 0, width, 0, 0, width, height)
        postInvalidate()
    }
    
    /**
     * Update robot position on map (in pixels relative to map origin)
     */
    fun updateRobotPose(x: Float, y: Float, yaw: Float) {
        robotPoseX = x
        robotPoseY = y
        robotYaw = yaw
        robotVisible = true
        postInvalidate()
    }

    /** Hide the pose marker without changing the occupancy bitmap. */
    fun clearRobotPose() {
        robotVisible = false
        postInvalidate()
    }

    /** Drop the grid and pose so a new connection cannot keep the previous robot's map. */
    fun clearMap() {
        mapBitmap = null
        robotVisible = false
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        canvas.save()
        
        // Apply transformations (Pan & Zoom)
        canvas.translate(transX, transY)
        canvas.scale(scaleFactor, scaleFactor)
        
        // Draw Map
        mapBitmap?.let {
            // center the map initially if not transformed?
            // for now just draw at 0,0
            canvas.drawBitmap(it, 0f, 0f, mapPaint)
        }
        
        // Draw Robot (Triangle). Hidden until a same-frame pose is published.
        if (robotVisible) {
            canvas.save()
            canvas.translate(robotPoseX, robotPoseY)
            // ROS yaw is counterclockwise about +Z. The bitmap has +Y up, and canvas
            // rotation is clockwise, so the displayed heading is negated.
            canvas.rotate(-Math.toDegrees(robotYaw.toDouble()).toFloat())

            val path = android.graphics.Path()
            path.moveTo(10f, 0f)    // Nose
            path.lineTo(-5f, 5f)    // Left back
            path.lineTo(-5f, -5f)   // Right back
            path.close()
            canvas.drawPath(path, robotPaint)
            canvas.restore()
        }
        canvas.restore()
        
        // Debug Info
        // canvas.drawText("Scale: $scaleFactor", 50f, 50f, robotPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 10.0f)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            transX -= distanceX
            transY -= distanceY
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Reset view
            scaleFactor = 1.0f
            transX = 0f
            transY = 0f
            invalidate()
            return true
        }
    }
}
