package com.android.dialer.callscreening.model

enum class BlockStrategy {
    SILENT_REJECT,
    HANG_UP,
    MUTE_RING,
    MARK_ONLY
}

data class BlockResult(
    val strategy: BlockStrategy,
    val tag: String?,
    val allowCall: Boolean
) {
    companion object {
        val ALLOW = BlockResult(
            strategy = BlockStrategy.MARK_ONLY,
            tag = null,
            allowCall = true
        )
    }
}
