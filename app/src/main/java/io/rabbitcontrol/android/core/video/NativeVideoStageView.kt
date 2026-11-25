package io.rabbitcontrol.android.core.video

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import kotlin.math.roundToInt

/**
 * Pure native FrameLayout hosting a hardware-composited SurfaceView.
 * Supports 120Hz display mode selection, Surface.setFrameRate(120),
 * zero-copy aspect-preserving scaling, pinch zoom, and cursor-following pan.
 */
class NativeVideoStageView(
    context: Context,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onSurfaceLost: () -> Unit,
) : FrameLayout(context), SurfaceHolder.Callback {

    val surfaceView: SurfaceView = SurfaceView(context)
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var targetFps = 120f

    var zoomScale: Float = 1f
        private set
    var panX: Float = 0f
        private set
    var panY: Float = 0f
        private set

    var baseStageWidth: Int = 1
        private set
    var baseStageHeight: Int = 1
        private set

    init {
        setBackgroundColor(0xFF07090D.toInt())
        clipChildren = true
        clipToPadding = true
        addView(surfaceView, LayoutParams(1, 1, Gravity.CENTER))
        surfaceView.holder.addCallback(this)
    }

    fun updateStreamGeometry(width: Int, height: Int, fps: Int) {
        val w = width.coerceAtLeast(2)
        val h = height.coerceAtLeast(2)
        val f = fps.coerceAtLeast(30).toFloat()
        val sizeChanged = w != videoWidth || h != videoHeight
        val fpsChanged = f != targetFps
        videoWidth = w
        videoHeight = h
        targetFps = f
        if (sizeChanged) {
            post {
                if (this.width > 0 && this.height > 0) {
                    layoutVideoSurface(this.width, this.height)
                }
                requestLayout()
                setZoomAndPan(zoomScale, panX, panY)
            }
        }
        if (fpsChanged) {
            applySurfaceFrameRate()
        }
    }

    fun setZoomAndPan(scale: Float, newPanX: Float, newPanY: Float) {
        val clampedScale = scale.coerceIn(1f, 4f)
        zoomScale = clampedScale
        val maxPanX = ((baseStageWidth * (clampedScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxPanY = ((baseStageHeight * (clampedScale - 1f)) / 2f).coerceAtLeast(0f)
        panX = if (clampedScale <= 1.01f) 0f else newPanX.coerceIn(-maxPanX, maxPanX)
        panY = if (clampedScale <= 1.01f) 0f else newPanY.coerceIn(-maxPanY, maxPanY)
        surfaceView.scaleX = zoomScale
        surfaceView.scaleY = zoomScale
        surfaceView.translationX = panX
        surfaceView.translationY = panY
    }

    fun resetZoom() {
        setZoomAndPan(1f, 0f, 0f)
    }

    /**
     * When zoomed in (>1x), keeps the remote cursor `(normX, normY)` comfortably inside the
     * visible viewport with a 15% safety margin by smoothly panning the zoomed stage.
     */
    fun ensureCursorVisible(normX: Int, normY: Int) {
        if (zoomScale <= 1.02f || width <= 0 || height <= 0 || baseStageWidth <= 1 || baseStageHeight <= 1) {
            return
        }
        val relX = (normX / 65535f - 0.5f) * baseStageWidth
        val relY = (normY / 65535f - 0.5f) * baseStageHeight
        val screenCursorX = width * 0.5f + relX * zoomScale + panX
        val screenCursorY = height * 0.5f + relY * zoomScale + panY

        val marginX = width * 0.14f
        val marginY = height * 0.14f
        var dx = 0f
        var dy = 0f
        if (screenCursorX < marginX) dx = marginX - screenCursorX
        else if (screenCursorX > width - marginX) dx = (width - marginX) - screenCursorX
        if (screenCursorY < marginY) dy = marginY - screenCursorY
        else if (screenCursorY > height - marginY) dy = (height - marginY) - screenCursorY

        if (dx != 0f || dy != 0f) {
            setZoomAndPan(zoomScale, panX + dx, panY + dy)
        }
    }

    /**
     * Maps touch `(viewX, viewY)` in this container's coordinates through the inverse zoom/pan
     * transform onto normalized `0..65535` remote screen coordinates.
     */
    fun mapViewPointToNormalized(viewX: Float, viewY: Float): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || baseStageWidth <= 1 || baseStageHeight <= 1) return null
        val localFromCenterX = (viewX - width * 0.5f - panX) / zoomScale
        val localFromCenterY = (viewY - height * 0.5f - panY) / zoomScale
        val surfaceX = localFromCenterX + baseStageWidth * 0.5f
        val surfaceY = localFromCenterY + baseStageHeight * 0.5f
        val nx = ((surfaceX / baseStageWidth) * 65535f).roundToInt().coerceIn(0, 65535)
        val ny = ((surfaceY / baseStageHeight) * 65535f).roundToInt().coerceIn(0, 65535)
        return nx to ny
    }

    /** Maps remote normalized `(normX, normY)` back to screen pixel coordinates inside this stage. */
    fun mapNormalizedToViewPoint(normX: Int, normY: Int): Pair<Float, Float> {
        val relX = (normX / 65535f - 0.5f) * baseStageWidth
        val relY = (normY / 65535f - 0.5f) * baseStageHeight
        val vx = width * 0.5f + relX * zoomScale + panX
        val vy = height * 0.5f + relY * zoomScale + panY
        return vx to vy
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val containerW = MeasureSpec.getSize(widthMeasureSpec)
        val containerH = MeasureSpec.getSize(heightMeasureSpec)
        if (containerW > 0 && containerH > 0) {
            layoutVideoSurface(containerW, containerH)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val prevW = baseStageWidth
        val prevH = baseStageHeight
        layoutVideoSurface(w, h)
        if ((prevW <= 1 || prevH <= 1) && w > 0 && h > 0) {
            post { requestLayout() }
        }
        setZoomAndPan(zoomScale, panX, panY)
    }

    private fun layoutVideoSurface(containerW: Int, containerH: Int) {
        if (containerW <= 0 || containerH <= 0) return
        val factor = minOf(
            containerW.toDouble() / videoWidth.toDouble(),
            containerH.toDouble() / videoHeight.toDouble(),
        )
        val w = (factor * videoWidth).roundToInt().coerceAtLeast(2)
        val h = (factor * videoHeight).roundToInt().coerceAtLeast(2)
        baseStageWidth = w
        baseStageHeight = h
        val lp = surfaceView.layoutParams as? LayoutParams
        if (lp == null || lp.width != w || lp.height != h) {
            surfaceView.layoutParams = LayoutParams(w, h, Gravity.CENTER)
        }
        setZoomAndPan(zoomScale, panX, panY)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(
            "RC_DIAG",
            "NativeVideoStageView surfaceCreated size=${surfaceView.width}x${surfaceView.height} stage=${baseStageWidth}x${baseStageHeight}",
        )
        applyPreferredDisplayRefreshRate()
        applySurfaceFrameRate()
        onSurfaceReady(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.i("RC_DIAG", "NativeVideoStageView surfaceChanged ${width}x${height}")
        applySurfaceFrameRate()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        onSurfaceLost()
    }

    private fun applySurfaceFrameRate() {
        val surface = surfaceView.holder.surface
        if (Build.VERSION.SDK_INT >= 30 && surface.isValid) {
            runCatching {
                surface.setFrameRate(
                    targetFps,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS,
                )
            }
        }
    }

    private fun applyPreferredDisplayRefreshRate() {
        val activity = context as? Activity ?: return
        val window = activity.window ?: return
        val display = if (Build.VERSION.SDK_INT >= 30) activity.display else @Suppress("DEPRECATION") window.windowManager.defaultDisplay
        display ?: return
        val cur = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate }
        if (best != null && best.refreshRate >= 119f && window.attributes.preferredDisplayModeId != best.modeId) {
            val attrs = window.attributes
            attrs.preferredDisplayModeId = best.modeId
            window.attributes = attrs
        }
    }
}
