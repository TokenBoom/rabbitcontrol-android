package io.rabbitcontrol.android

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

@Suppress("DEPRECATION")
internal fun applyInsets(root: View) {
    root.setOnApplyWindowInsetsListener { view, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val padding = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
        }
        insets
    }
}

@Suppress("DEPRECATION")
internal fun applyInsetsWithBottomBar(root: View, bottomBar: View) {
    root.setOnApplyWindowInsetsListener { view, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val padding = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(padding.left, padding.top, padding.right, 0)
            bottomBar.setPadding(0, 0, 0, padding.bottom)
        } else {
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, 0)
            bottomBar.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
        }
        insets
    }
}

@Suppress("DEPRECATION")
internal fun styleSystemBars(activity: Activity, dark: Boolean = false) {
    val color = activity.getColor(if (dark) R.color.rc_chrome else R.color.rc_canvas)
    activity.window.statusBarColor = color
    activity.window.navigationBarColor = color
    val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val lightContent = !dark && !night
    if (Build.VERSION.SDK_INT >= 30) {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        activity.window.insetsController?.setSystemBarsAppearance(if (lightContent) mask else 0, mask)
    } else {
        var flags = activity.window.decorView.systemUiVisibility
        flags = flags and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
        if (lightContent) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        activity.window.decorView.systemUiVisibility = flags
    }
}
