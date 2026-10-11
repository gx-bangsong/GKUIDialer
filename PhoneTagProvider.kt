【PhoneTagProvider接口 + 热更新规则JSON Schema】

interface PhoneTagProvider {
    val providerName: String
    val priority: Int
    val isEnabled: Boolean
    suspend fun query(phoneNumber: String): PhoneTagResult?
    fun getRuleVersion(): Int
}

data class PhoneTagResult(...)

// 热更新规则JSON Schema
{
  "provider": "sogou_dianhua",
  "version": 5,
  "request": { "url_template": "...", "method":"GET", ... },
  "parse_rules": {
    "type": "css_selector",
    "name": ".tel-source-name",
    "tag": ".tel-mark-type",
    ...
  },
  "fallback_provider": "baidu_dianhua"
}
