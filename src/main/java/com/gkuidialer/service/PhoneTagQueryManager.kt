package com.gkuidialer.service

class PhoneTagQueryManager {
    // 并发查询所有已启用Provider（async/await）
    // 每个Provider独立timeout=3000ms
    // 结果合并：取markCount最高为主结果，其余作为辅助展示
    // 缓存：Room DB缓存24小时
}
