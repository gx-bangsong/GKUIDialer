/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.record

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.dialer.callscreening.R
import com.android.dialer.callscreening.ScreeningComponent
import com.android.dialer.callscreening.ui.applySystemBarInsets
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RuleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Blocked-call history: view details, call back, whitelist ("恢复/加白"),
 * remove entries or clear all.
 */
class BlockedCallsActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var adapter: RecordAdapter
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ScreeningTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blocked_calls_activity)
        applySystemBarInsets()
        title = getString(R.string.blocked_calls_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        emptyView = findViewById(R.id.empty_view)
        adapter = RecordAdapter()
        findViewById<RecyclerView>(R.id.blocked_list).apply {
            layoutManager = LinearLayoutManager(this@BlockedCallsActivity)
            adapter = this@BlockedCallsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_CLEAR_ALL, 0, R.string.blocked_call_clear_all)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_CLEAR_ALL -> {
            scope.launch {
                withContext(Dispatchers.IO) {
                    ScreeningComponent.get(this@BlockedCallsActivity)
                        .blockedCallRecordDao().deleteAll()
                }
                reload()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun reload() {
        scope.launch {
            val records = withContext(Dispatchers.IO) {
                ScreeningComponent.get(this@BlockedCallsActivity)
                    .blockedCallRecordDao().getAll()
            }
            adapter.submit(records)
            emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun whitelist(record: BlockedCallRecord) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ScreeningComponent.get(this@BlockedCallsActivity).blockRuleDao()
                    .insert(BlockRule(
                        type = RuleType.WHITELIST,
                        value = record.number,
                        note = getString(R.string.blocked_calls_title)))
            }
            Toast.makeText(this@BlockedCallsActivity,
                R.string.blocked_call_whitelisted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun callBack(record: BlockedCallRecord) {
        startActivity(Intent(Intent.ACTION_DIAL,
            Uri.fromParts("tel", record.number, null)))
    }

    private fun remove(record: BlockedCallRecord) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ScreeningComponent.get(this@BlockedCallsActivity)
                    .blockedCallRecordDao().delete(record.id)
            }
            reload()
        }
    }

    /** "疑似诈骗 · 被 1283 人标记" style detail from the stored snapshot. */
    private fun detailLine(record: BlockedCallRecord): String {
        val time = DateUtils.getRelativeTimeSpanString(record.blockedAt)
        val reason = record.ruleSummary
            ?.let { getString(R.string.blocked_reason_prefix, it) }
        val snapshot = record.numberInfoJson?.let {
            try {
                val o = JSONObject(it)
                listOfNotNull(
                    o.optString("bizName").takeIf { s -> s.isNotBlank() },
                    o.optString("location").takeIf { s -> s.isNotBlank() },
                ).joinToString(" ")
            } catch (e: Exception) {
                null
            }
        }?.takeIf { it.isNotBlank() }
        return listOfNotNull(time, reason, snapshot).joinToString(" · ")
    }

    private inner class RecordAdapter : RecyclerView.Adapter<RecordHolder>() {
        private val items = ArrayList<BlockedCallRecord>()

        fun submit(records: List<BlockedCallRecord>) {
            items.clear()
            items.addAll(records)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RecordHolder(
            layoutInflater.inflate(R.layout.blocked_call_item, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecordHolder, position: Int) {
            val record = items[position]
            holder.number.text = record.number
            holder.detail.text = detailLine(record)
            holder.overflow.setOnClickListener { v ->
                PopupMenu(this@BlockedCallsActivity, v).apply {
                    menu.add(Menu.NONE, ITEM_CALL_BACK, 0, R.string.blocked_call_call_back)
                    menu.add(Menu.NONE, ITEM_WHITELIST, 1, R.string.blocked_call_whitelist)
                    menu.add(Menu.NONE, ITEM_DELETE, 2, R.string.blocked_call_delete)
                    setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            ITEM_CALL_BACK -> callBack(record)
                            ITEM_WHITELIST -> whitelist(record)
                            ITEM_DELETE -> remove(record)
                        }
                        true
                    }
                }.show()
            }
        }
    }

    private class RecordHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.blocked_number)
        val detail: TextView = view.findViewById(R.id.blocked_detail)
        val overflow: ImageButton = view.findViewById(R.id.blocked_overflow)
    }

    private companion object {
        const val MENU_CLEAR_ALL = 100
        const val ITEM_CALL_BACK = 1
        const val ITEM_WHITELIST = 2
        const val ITEM_DELETE = 3
    }
}
