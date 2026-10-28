# 第三方 SDK 放这里（高德定位，M0 第 4 项）

## 放什么

从高德开放平台下载的**定位 SDK AAR**：

- 下载页：https://lbs.amap.com/api/android-location-sdk/download
- 选 **AAR 包下载**（不要选 jar 那个）—— AAR 里自带 `armeabi-v7a/arm64-v8a` 的 `.so`，
  放这一个文件就够；jar 版还得手工摆 `jniLibs`，容易漏。
- 文件名形如 `AMap_Location_V11.3.101.aar`
- 直接放在本目录下即可（`app/libs/`），gradle 已配好从 `libs` 加载 `*.aar`

## 为什么不在 Maven 里

`com.amap.api:location` 当年发布在 jcenter，而 **jcenter 已于 2022 年关闭**，
现在 google()/mavenCentral() 都解析不到 —— `app/build.gradle` 里那行 `// implementation 'com.amap.api:location:6.4.5'`
是历史遗留，**不要再打开它**，否则构建会因为解析失败而失败。

高德官方现在只提供 zip / AAR 下载。

## Key 怎么配

Key 绑定「包名 + 签名 SHA1」，debug APK 用的是：

- 包名：`com.ruyue.alarm`
- debug keystore：`D:\Android_Data\.android\debug.keystore`
- SHA1：`96:14:FF:CE:85:2E:C7:3F:E2:CB:63:6C:C7:E3:26:E3:C9:F0:F8:D7`

Key 写进仓库外的 `local.properties`（已被 .gitignore 排除）：

```
AMAP_KEY=你的key
```

⚠️ 高德控制台里「发布版安全码」和「调试版安全码」是**两个字段**，装的是 debug APK
就必须填**调试版**那个 —— 之前 key 一直不成功，最可能就是这个填错了。
