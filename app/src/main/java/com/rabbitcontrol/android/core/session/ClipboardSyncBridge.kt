package com.rabbitcontrol.android.core.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.rabbitcontrol.android.session.Core

class ClipboardSyncBridge(context: Context) {
    private val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var enabled = false
    private var lastAppliedFromPeer: String? = null
    private var registered = false

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!enabled) return@OnPrimaryClipChangedListener
        val text = readPrimaryText() ?: return@OnPrimaryClipChangedListener
        if (text == lastAppliedFromPeer) {
            return@OnPrimaryClipChangedListener
        }
        lastAppliedFromPeer = null
        Core.pushClipboardLocal(text)
    }

    fun start() {
        enabled = true
        mainHandler.post {
            if (!registered) {
                manager.addPrimaryClipChangedListener(listener)
                registered = true
            }
        }
    }

    fun stop() {
        enabled = false
        mainHandler.post {
            if (registered) {
                manager.removePrimaryClipChangedListener(listener)
                registered = false
            }
        }
    }

    fun setEnabled(on: Boolean) {
        enabled = on
    }

    fun applyPeerText(text: String) {
        mainHandler.post {
            lastAppliedFromPeer = text
            runCatching {
                manager.setPrimaryClip(ClipData.newPlainText("RabbitControl", text))
            }
        }
    }

    fun pushCurrentClipboardNow(): Boolean {
        val text = readPrimaryText() ?: return false
        lastAppliedFromPeer = null
        Core.pushClipboardLocal(text)
        return true
    }

    fun readPrimaryText(): String? {
        val clip = runCatching { manager.primaryClip }.getOrNull() ?: return null
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).text?.toString()
    }
}
