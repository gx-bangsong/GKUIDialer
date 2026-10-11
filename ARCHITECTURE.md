【架构图（文字版，含三模块数据流）】

来电到达
  ↓
CallScreeningService.onScreenCall()
  ↓
决策链：①白名单/联系人 → 放行
        ②黑名单/前缀规则 → 拦截
        ③本地骚扰库查询 → TagType拦截判断
        ④黄页数据库查询 → Directory API注入机构名 → 放行并标注来源
        ⑤在线查询（异步，不阻塞拦截决策）
            ↓ 并发查询各Provider（Sogou/Dianhua/Baidu/360）
            ↓ 结果写入Room缓存 + 更新Directory标签（下次来电生效）

数据流：
模块A（标记）←→ Room DB（标签/缓存/用户自定义）
模块B（拦截）←→ Room DB（规则/黑白名单/日志）
模块C（黄页）←→ Room DB（黄页表/社区数据包）
在线查询（A5解耦Provider + 热更新规则JSON）
