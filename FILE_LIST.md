【文件改动清单（按模块分组）】

模块A（号码标记）：
- PhoneTagProvider.kt（接口+热更新规则）
- PhoneTagResult.kt / TagType.kt
- Room：SpamTagDao / SpamTagEntity
- CallScreeningService.kt（拦截轨道）
- YellowPageContactsProvider.kt（Directory API展示轨道）
- MarkNumberBottomSheet.kt

模块B（骚扰拦截）：
- BlockStrategy.kt / PrefixRule.kt / TagTypeRule.kt
- Room：PrefixRuleDao / BlockLogDao / AllowListDao / BlackListDao
- PrefixRuleListFragment.kt / AddPrefixRuleBottomSheet.kt
- TagTypeRuleFragment.kt
- BlockLogActivity.kt

模块C（黄页）：
- YellowPageCategory.kt
- Room：YellowPageDao / YellowPageEntity
- YellowPageBrowseActivity.kt / YellowPageDetailActivity.kt
- SubmitYellowPageFragment.kt

通用：
- AndroidManifest.xml（重点声明）
- SettingsFragment.kt
- AppDatabase.kt（完整Schema）
- PhoneTagQueryManager.kt（查询调度器）
