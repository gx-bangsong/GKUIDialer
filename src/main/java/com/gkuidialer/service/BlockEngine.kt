package com.gkuidialer.service

// 拦截引擎核心：决策链执行顺序已在ARCHITECTURE.md定义
// 此文件为模块B拦截引擎完整实现框架
class BlockEngine(private val db: AppDatabase) {
    suspend fun evaluate(phone: String): BlockResult {
        // ①白名单 ②联系人 ③黑名单 ④号段规则 ⑤标记类型 ⑥默认放行
        return BlockResult.ALLOW
    }
}

data class BlockResult(val strategy: BlockStrategy, val tag: String?)
