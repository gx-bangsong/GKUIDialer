<!-- 重点声明 -->
<service
    android:name=".service.SpamCallScreeningService"
    android:permission="android.permission.BIND_SCREENING_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.telecom.CallScreeningService" />
    </intent-filter>
</service>

<!-- 自定义ContactsProvider（Directory API） -->
<provider
    android:name=".provider.YellowPageContactsProvider"
    android:authorities="${applicationId}.directory"
    android:readPermission="${applicationId}.permission.READ_YELLOW_PAGE"
    android:exported="true"
    android:grantUriPermissions="true" />

<!-- 权限 -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- 自定义权限 -->
<permission android:name="${applicationId}.permission.READ_YELLOW_PAGE"
    android:protectionLevel="signature|normal" />
