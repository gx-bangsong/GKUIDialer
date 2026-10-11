/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.numberinfo.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.dialer.callscreening.ui.applySystemBarInsets
import com.android.dialer.numberinfo.MarkType
import com.android.dialer.numberinfo.NumberInfo
import com.android.dialer.numberinfo.NumberInfoComponent
import com.android.dialer.numberinfo.NumberNormalizer
import com.android.dialer.numberinfo.R
import com.android.dialer.numberinfo.Sources
import com.android.dialer.numberinfo.incall.NumberInfoBridge
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lets the user mark a number, correct a mark (mark it as something else), or remove
 * their own mark. The mark is stored locally, takes priority over online data, and applies
 * to the call log, the in-call card and call screening straight away.
 */
class ManualMarkActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var number: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.manual_mark_activity)
        applySystemBarInsets()

        val normalized = NumberNormalizer.normalize(intent.getStringExtra(EXTRA_NUMBER))
        if (normalized == null) {
            finish()
            return
        }
        number = normalized
        findViewById<TextView>(R.id.manual_mark_number).text = number

        val options = findViewById<LinearLayout>(R.id.manual_mark_options)
        for (type in MARK_OPTIONS) {
            val label = NumberInfoBridge.formatLabel(
                this, NumberInfo(number, markType = type, source = Sources.MANUAL)) ?: type.id
            options.addView(MaterialButton(this).apply {
                text = label
                setOnClickListener { save(type) }
            })
        }
        findViewById<MaterialButton>(R.id.manual_mark_clear).setOnClickListener { clear() }
        refreshCurrent()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun repository() = NumberInfoComponent.get(applicationContext).repository()

    private fun refreshCurrent() {
        scope.launch {
            val mark = repository().manualMark(number)
            val current = mark?.let { NumberInfoBridge.formatLabel(this@ManualMarkActivity, it) }
            runOnUiThread {
                findViewById<TextView>(R.id.manual_mark_current).text =
                    if (current != null) {
                        getString(R.string.manual_mark_current, current)
                    } else {
                        getString(R.string.manual_mark_none)
                    }
            }
        }
    }

    private fun save(type: MarkType) {
        scope.launch {
            repository().setManualMark(number, type, note = null)
            runOnUiThread {
                Toast.makeText(this@ManualMarkActivity, R.string.manual_mark_saved,
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun clear() {
        scope.launch {
            repository().clearManualMark(number)
            runOnUiThread {
                Toast.makeText(this@ManualMarkActivity, R.string.manual_mark_cleared,
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"

        /** Marks that a user can choose; NONE is "clear" and CUSTOM comes from sources only. */
        private val MARK_OPTIONS = listOf(
            MarkType.FRAUD,
            MarkType.HARASSMENT,
            MarkType.TELEMARKETING,
            MarkType.AGENT,
            MarkType.DEBT_COLLECTION,
            MarkType.EXPRESS,
            MarkType.TAXI,
        )

        @JvmStatic
        fun newIntent(context: Context, number: String): Intent =
            Intent(context, ManualMarkActivity::class.java).putExtra(EXTRA_NUMBER, number)
    }
}
