package com.gkuidialer.service

class SpamCallScreeningService : CallScreeningService() {
    override fun onScreenCall(request: Call.Details) {
        // 决策链执行：白名单→黑名单→本地骚扰库→黄页→在线查询（异步）
    }
}
