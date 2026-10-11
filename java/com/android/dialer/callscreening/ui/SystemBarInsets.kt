/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.ui

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Draws this activity edge to edge and pads its content by the status and navigation
 * bars, so nothing sits under them. Required on targetSdk 36, which enforces edge-to-edge.
 * Below Android 11 the window keeps the system's default layout and nothing changes.
 */
fun Activity.applySystemBarInsets() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }
    window.setDecorFitsSystemWindows(false)
    findViewById<View>(android.R.id.content).setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
