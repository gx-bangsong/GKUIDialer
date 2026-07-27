/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.dialer.callscreening.R
import com.android.dialer.callscreening.ScreeningComponent
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.RulePorter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Blocking-rule management: list + enable/disable + delete, FAB to add,
 * overflow menu for import/export via SAF.
 */
class BlockRuleListActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var adapter: RuleAdapter
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ScreeningTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.block_rule_list_activity)
        title = getString(R.string.block_rules_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        emptyView = findViewById(R.id.empty_view)
        adapter = RuleAdapter()
        findViewById<RecyclerView>(R.id.rule_list).apply {
            layoutManager = LinearLayoutManager(this@BlockRuleListActivity)
            adapter = this@BlockRuleListActivity.adapter
        }
        findViewById<FloatingActionButton>(R.id.add_rule_fab).setOnClickListener {
            startActivity(Intent(this, BlockRuleEditorActivity::class.java))
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

    private fun reload() {
        scope.launch {
            val rules = withContext(Dispatchers.IO) {
                ScreeningComponent.get(this@BlockRuleListActivity).blockRuleDao().getAll()
            }
            adapter.submit(rules)
            emptyView.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // -- import / export ------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_IMPORT, 0, R.string.rules_import)
        menu.add(Menu.NONE, MENU_EXPORT, 1, R.string.rules_export)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        MENU_IMPORT -> {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }, REQUEST_IMPORT)
            true
        }
        MENU_EXPORT -> {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "dialer-block-rules.json")
                }, REQUEST_EXPORT)
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) return
        when (requestCode) {
            REQUEST_IMPORT -> importRules(uri)
            REQUEST_EXPORT -> exportRules(uri)
        }
    }

    private fun importRules(uri: Uri) {
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val json = contentResolver.openInputStream(uri)!!
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                    val result = RulePorter.parse(json)
                    val dao = ScreeningComponent.get(this@BlockRuleListActivity).blockRuleDao()
                    result.rules.forEach { dao.insert(it) }
                    getString(R.string.rules_import_success,
                        result.rules.size, result.skipped)
                } catch (e: Exception) {
                    getString(R.string.rules_import_failure)
                }
            }
            Toast.makeText(this@BlockRuleListActivity, message, Toast.LENGTH_LONG).show()
            reload()
        }
    }

    private fun exportRules(uri: Uri) {
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val dao = ScreeningComponent.get(this@BlockRuleListActivity).blockRuleDao()
                    val json = RulePorter.export(dao.getAll())
                    contentResolver.openOutputStream(uri)!!
                        .use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    getString(R.string.rules_export_success)
                } catch (e: Exception) {
                    getString(R.string.rules_export_failure)
                }
            }
            Toast.makeText(this@BlockRuleListActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    // -- list adapter -----------------------------------------------------------

    private inner class RuleAdapter : RecyclerView.Adapter<RuleHolder>() {
        private val items = ArrayList<BlockRule>()

        fun submit(rules: List<BlockRule>) {
            items.clear()
            items.addAll(rules)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RuleHolder(
            layoutInflater.inflate(R.layout.block_rule_item, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RuleHolder, position: Int) {
            val rule = items[position]
            holder.title.text = ScreeningUi.ruleTypeLabel(this@BlockRuleListActivity, rule.type)
            holder.subtitle.text = listOfNotNull(
                ScreeningUi.ruleSummary(this@BlockRuleListActivity, rule),
                rule.note?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            holder.enabled.setOnCheckedChangeListener(null)
            holder.enabled.isChecked = rule.enabled
            holder.enabled.setOnCheckedChangeListener { _, checked ->
                scope.launch(Dispatchers.IO) {
                    ScreeningComponent.get(this@BlockRuleListActivity)
                        .blockRuleDao().setEnabled(rule.id, checked)
                }
            }
            holder.delete.setOnClickListener {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        ScreeningComponent.get(this@BlockRuleListActivity)
                            .blockRuleDao().delete(rule.id)
                    }
                    Toast.makeText(this@BlockRuleListActivity,
                        R.string.block_rule_deleted, Toast.LENGTH_SHORT).show()
                    reload()
                }
            }
            holder.itemView.setOnClickListener {
                startActivity(
                    Intent(this@BlockRuleListActivity, BlockRuleEditorActivity::class.java)
                        .putExtra(BlockRuleEditorActivity.EXTRA_RULE_ID, rule.id))
            }
        }
    }

    private class RuleHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.rule_title)
        val subtitle: TextView = view.findViewById(R.id.rule_subtitle)
        val enabled: SwitchCompat = view.findViewById(R.id.rule_enabled_switch)
        val delete: ImageButton = view.findViewById(R.id.rule_delete_button)
    }

    private companion object {
        const val MENU_IMPORT = 100
        const val MENU_EXPORT = 101
        const val REQUEST_IMPORT = 200
        const val REQUEST_EXPORT = 201
    }
}
