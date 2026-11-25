package io.rabbitcontrol.android.session

import android.view.Surface

/**
 * Low-level JNI bridge into `librabbitcontrol_android.so`.
 * Method names and package path match Rust `#[no_mangle]` symbols strictly.
 */
internal object Core {
    init {
        System.loadLibrary("rabbitcontrol_android")
    }

    external fun initRuntime(filesDir: String, deviceName: String, codecsJson: String): String
    external fun account(requestJson: String): String
    external fun deviceId(raw: String): String?

    /** Normalized unsigned x/y, packed in the top/bottom 16 bits. */
    external fun pointer(x: Float, y: Float, width: Int, height: Int): Int

    /** The local input monitor inspects the same event bytes as rc-input. */
    external fun encodeInput(kind: Int, a: Int, b: Int): ByteArray

    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun pollSignalEvents(): String?
    external fun openSession(requestJson: String): String
    external fun disconnectSession(lockRemote: Boolean)
    external fun sendSessionCommand(commandJson: String)
    external fun pollSessionUpdates(includeMedia: Boolean): String?

    external fun sendMouseMove(x: Int, y: Int)
    external fun sendMouseButton(button: Int, pressed: Boolean)
    external fun sendMouseWheel(delta: Int, horizontal: Boolean)
    external fun sendKey(scancode: Int, pressed: Boolean, modifiers: Int)

    external fun pushClipboardLocal(text: String)
    external fun pushImeSilent(text: String)
    external fun toolCall(requestJson: String): String

    external fun setHostScreenMetrics(width: Int, height: Int)
    external fun startHost(configJson: String): String
    external fun stopHost()
    external fun hostCommand(commandJson: String): String
    external fun pollHostEvents(): String?
    external fun pushHostEncodedFrame(data: ByteArray, isKeyframe: Boolean)
}
