package io.rabbitcontrol.android

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import io.rabbitcontrol.android.core.i18n.AppLocaleManager
import io.rabbitcontrol.android.core.i18n.AppStrings
import io.rabbitcontrol.android.input.TouchPad
import io.rabbitcontrol.android.session.Core
import io.rabbitcontrol.android.video.MotionClip
import io.rabbitcontrol.android.video.PreviewPlayer
import kotlin.math.roundToInt

class PreviewActivity : Activity(), SurfaceHolder.Callback {
    private lateinit var video: SurfaceView
    private lateinit var touch: TouchPad
    private lateinit var codecStatus: TextView
    private var player: PreviewPlayer? = null
    private var visible = false
    private var generation = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preview)
        styleSystemBars(this, dark = true)
        applyInsets(findViewById(R.id.root))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        codecStatus = findViewById(R.id.codecStatus)
        val inputStatus = findViewById<TextView>(R.id.inputStatus)
        val stage = findViewById<FrameLayout>(R.id.stage)
        val viewport = FrameLayout(this)
        stage.addView(viewport, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        stage.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val factor = minOf((r - l).toDouble() / MotionClip.WIDTH, (b - t).toDouble() / MotionClip.HEIGHT)
            val w = (factor * MotionClip.WIDTH).roundToInt()
            val h = (factor * MotionClip.HEIGHT).roundToInt()
            if (w > 0 && h > 0 && (viewport.layoutParams.width != w || viewport.layoutParams.height != h)) {
                viewport.layoutParams = FrameLayout.LayoutParams(w, h, Gravity.CENTER)
            }
        }
        video = SurfaceView(this)
        viewport.addView(video, ViewGroup.LayoutParams(-1, -1))
        video.holder.addCallback(this)
        var lastAction = ""
        touch = TouchPad(this) { kind, a, b ->
            // This monitor exercises the wire encoder locally. A live sink will enqueue
            // typed InputEvents directly on SessionHandle's urgent input path.
            if (kind != 0) {
                val eventBytes = Core.encodeInput(kind, a, b)
                val action = if (kind == 2) {
                    AppStrings.get(R.string.preview_action_scroll, "滚动 $a 行", a)
                } else {
                    val name = if (a == 0) {
                        AppStrings.get(R.string.preview_btn_left, "左键")
                    } else {
                        AppStrings.get(R.string.preview_btn_right, "右键")
                    }
                    val dir = if (b == 1) {
                        AppStrings.get(R.string.preview_btn_down, "按下")
                    } else {
                        AppStrings.get(R.string.preview_btn_up, "松开")
                    }
                    "$name$dir"
                }
                if (action != lastAction) {
                    inputStatus.text = getString(R.string.input_result, action, eventBytes.size)
                    lastAction = action
                }
            }
        }
        viewport.addView(touch, ViewGroup.LayoutParams(-1, -1))
    }

    override fun onResume() {
        super.onResume()
        visible = true
        startIfReady()
    }

    override fun onPause() {
        visible = false
        touch.cancelGesture()
        stopPlayer()
        super.onPause()
    }

    override fun surfaceCreated(holder: SurfaceHolder) = startIfReady()
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
    override fun surfaceDestroyed(holder: SurfaceHolder) = stopPlayer()

    private fun startIfReady() {
        if (!visible || !video.holder.surface.isValid || player != null) return
        val scope = ++generation
        player = PreviewPlayer(resources, video.holder.surface) { status ->
            runOnUiThread { if (scope == generation) codecStatus.text = status }
        }
    }

    private fun stopPlayer() {
        generation++
        player?.close()
        player = null
    }
}
