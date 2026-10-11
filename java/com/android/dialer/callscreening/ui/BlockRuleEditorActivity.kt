/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.dialer.callscreening.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.dialer.callscreening.R
import com.android.dialer.callscreening.ScreeningComponent
import com.android.dialer.callscreening.rule.BlockRule
import com.android.dialer.callscreening.rule.CommonPrefixes
import com.android.dialer.callscreening.rule.RuleType
import com.android.dialer.callscreening.rule.RuleValues
import com.android.dialer.numberinfo.MarkType
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MD3 rule editor:
 *  - rule type: single-selection chips
 *  - PREFIX/BLACKLIST/WHITELIST: text input + quick-fill segment chips
 *    (170/171/…950/400/area codes from [CommonPrefixes])
 *  - MARK_TYPE: multi-select FilterChips, selected state tinted with the
 *    secondaryContainer color (see ScreeningFilterChip style)
 */
class BlockRuleEditorActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var typeGroup: ChipGroup
    private lateinit var valueLayout: TextInputLayout
    private lateinit var valueInput: TextInputEditText
    private lateinit var noteInput: TextInputEditText
    private lateinit var quickTitle: View
    private lateinit var quickScroll: View
    private lateinit var quickGroup: ChipGroup
    private lateinit var markTitle: View
    private lateinit var markGroup: ChipGroup

    private var editingRuleId: Long = 0L
    private var editingRule: BlockRule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.ScreeningTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.block_rule_editor)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        editingRuleId = intent.getLongExtra(EXTRA_RULE_ID, 0L)
        title = getString(
            if (editingRuleId > 0) R.string.block_rule_edit else R.string.block_rule_add)

        typeGroup = findViewById(R.id.rule_type_group)
        valueLayout = findViewById(R.id.rule_value_layout)
        valueInput = findViewById(R.id.rule_value_input)
        noteInput = findViewById(R.id.rule_note_input)
        quickTitle = findViewById(R.id.quick_prefixes_title)
        quickScroll = findViewById(R.id.quick_prefixes_scroll)
        quickGroup = findViewById(R.id.quick_prefixes_group)
        markTitle = findViewById(R.id.mark_types_title)
        markGroup = findViewById(R.id.mark_types_group)

        buildTypeChips()
        buildQuickPrefixChips()
        buildMarkTypeChips()
        findViewById<MaterialButton>(R.id.rule_save_button).setOnClickListener { save() }

        if (editingRuleId > 0) loadRule() else selectType(RuleType.PREFIX)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) { finish(); true }
        else super.onOptionsItemSelected(item)

    // -- chip construction -----------------------------------------------------

    private fun buildTypeChips() {
        for (type in RuleType.values()) {
            val chip = Chip(this).apply {
                id = TYPE_CHIP_ID_BASE + type.ordinal
                text = ScreeningUi.ruleTypeLabel(this@BlockRuleEditorActivity, type)
                isCheckable = true
                setChipBackgroundColorResource(R.color.screening_chip_background)
                setTextColor(getColorStateList(R.color.screening_chip_text))
            }
            typeGroup.addView(chip)
        }
        // (group, checkedId) listener: available across material versions.
        typeGroup.setOnCheckedChangeListener { _, _ -> updateVisibility() }
    }

    private fun buildQuickPrefixChips() {
        for ((_, prefixes) in CommonPrefixes.ALL_GROUPS) {
            for (prefix in prefixes) {
                val chip = Chip(this).apply {
                    text = prefix
                    isCheckable = false
                    setOnClickListener {
                        valueInput.setText(prefix)
                        valueInput.setSelection(prefix.length)
                    }
                }
                quickGroup.addView(chip)
            }
        }
    }

    private fun buildMarkTypeChips() {
        for (markType in ScreeningUi.BLOCKABLE_MARK_TYPES) {
            val chip = Chip(this).apply {
                id = MARK_CHIP_ID_BASE + markType.ordinal
                text = ScreeningUi.markTypeLabel(this@BlockRuleEditorActivity, markType)
                isCheckable = true
                isCheckedIconVisible = true
                setChipBackgroundColorResource(R.color.screening_chip_background)
                setTextColor(getColorStateList(R.color.screening_chip_text))
                tag = markType
            }
            markGroup.addView(chip)
        }
    }

    // -- state <-> ui ------------------------------------------------------------

    private fun selectedType(): RuleType {
        val checked = typeGroup.checkedChipId
        val ordinal = checked - TYPE_CHIP_ID_BASE
        return RuleType.values().getOrElse(ordinal) { RuleType.PREFIX }
    }

    private fun selectType(type: RuleType) {
        typeGroup.check(TYPE_CHIP_ID_BASE + type.ordinal)
        updateVisibility()
    }

    private fun updateVisibility() {
        val type = selectedType()
        val isMark = type == RuleType.MARK_TYPE
        markTitle.visibility = if (isMark) View.VISIBLE else View.GONE
        markGroup.visibility = if (isMark) View.VISIBLE else View.GONE
        valueLayout.visibility = if (isMark) View.GONE else View.VISIBLE
        val showQuick = type == RuleType.PREFIX || type == RuleType.BLACKLIST
                || type == RuleType.WHITELIST
        quickTitle.visibility = if (showQuick) View.VISIBLE else View.GONE
        quickScroll.visibility = if (showQuick) View.VISIBLE else View.GONE
        valueLayout.hint = getString(
            when (type) {
                RuleType.PREFIX -> R.string.rule_value_hint_prefix
                RuleType.LOCATION -> R.string.rule_value_hint_location
                else -> R.string.rule_value_hint_number
            })
    }

    private fun selectedMarkTypesCsv(): String =
        (0 until markGroup.childCount)
            .map { markGroup.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .mapNotNull { it.tag as? MarkType }
            .joinToString(",") { it.id }

    private fun loadRule() {
        scope.launch {
            val rule = withContext(Dispatchers.IO) {
                ScreeningComponent.get(this@BlockRuleEditorActivity)
                    .blockRuleDao().getAll().firstOrNull { it.id == editingRuleId }
            } ?: run { finish(); return@launch }
            editingRule = rule
            selectType(rule.type)
            noteInput.setText(rule.note ?: "")
            if (rule.type == RuleType.MARK_TYPE) {
                val selected = rule.value.split(',')
                    .map { MarkType.fromId(it.trim()) }.toSet()
                for (i in 0 until markGroup.childCount) {
                    val chip = markGroup.getChildAt(i) as Chip
                    chip.isChecked = chip.tag in selected
                }
            } else {
                valueInput.setText(rule.value)
            }
        }
    }

    private fun save() {
        val type = selectedType()
        val raw = if (type == RuleType.MARK_TYPE) {
            selectedMarkTypesCsv()
        } else {
            valueInput.text?.toString()?.trim().orEmpty()
        }
        if (raw.isBlank()) {
            Toast.makeText(this, R.string.block_rule_value_missing, Toast.LENGTH_SHORT).show()
            return
        }
        // Store the canonical form so the rule matches incoming numbers, which
        // are canonicalized the same way ("+86 138 0013 8000" -> "13800138000").
        val value = RuleValues.normalize(type, raw)
        if (value == null) {
            valueLayout.error = getString(R.string.block_rule_value_invalid)
            return
        }
        valueLayout.error = null
        val note = noteInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        scope.launch {
            withContext(Dispatchers.IO) {
                val dao = ScreeningComponent.get(this@BlockRuleEditorActivity).blockRuleDao()
                val existing = editingRule
                if (existing != null) {
                    dao.update(existing.copy(type = type, value = value, note = note))
                } else {
                    dao.insert(BlockRule(type = type, value = value, note = note))
                }
            }
            Toast.makeText(this@BlockRuleEditorActivity,
                R.string.block_rule_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_RULE_ID = "rule_id"
        private const val TYPE_CHIP_ID_BASE = 0x7A000
        private const val MARK_CHIP_ID_BASE = 0x7B000
    }
}
