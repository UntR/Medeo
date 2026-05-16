# medeo

[English](README-en.md)

medeo 是一个个人自用的 Android 影视发现与在线播放应用。它提供豆瓣热榜式首页、多源搜索、详情页源/线路/集数切换、收藏、续看进度，以及基于 Media3/ExoPlayer 的在线播放体验。

本项目面向自用 sideload 分发，不面向应用商店发布。

## 应用截图

<p>
  <img src="docs/screenshots/home.jpeg" alt="medeo 首页热榜" width="30%" />
  <img src="docs/screenshots/favorites.jpeg" alt="medeo 收藏页" width="30%" />
  <img src="docs/screenshots/settings.jpeg" alt="medeo 设置页" width="30%" />
</p>

## 当前版本

- 应用名：`medeo`
- Release 版本：`n0.1`
- Android application id：`com.czpn7.ying`
- 最低 SDK：24
- Target / compile SDK：36

产品已更名为 `medeo`，但 package id 暂时保留 `com.czpn7.ying`，用于保持安装和升级兼容。

## 功能

- 使用公开热榜接口展示豆瓣热榜式排行首页。
- 支持苹果 CMS V10 多源并发搜索，单源失败不影响整体结果。
- 详情页包含海报、简介、数据源 Tab、线路 Tab、集数列表、收藏和继续观看。
- Media3/ExoPlayer 播放，支持 HLS、缓存、播放进度保存、切源、切线路、切集、横屏沉浸式、倍速控制和长按临时 2x 播放。
- 支持 Day / Night 主题切换，Night 使用 Tokyo Night 风格配色。
- 设置页支持数据源开关、真实缓存占用、清空缓存和仅 Wi-Fi 自动播放。
- 首次启动展示免责声明。

## 法律与内容边界

medeo 不内置、不存储、不上传、不分发任何影视内容。搜索结果和播放地址来自用户自行启用的数据源。

本项目仅用于技术学习、个人研究和合法的个人使用。访问任何媒体内容前，请确认你拥有相应授权，并遵守所在地法律法规和版权要求。

本应用不得添加下载、导出、保存到相册、分享视频文件等功能。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Hilt
- Retrofit + OkHttp + Moshi
- Room
- DataStore
- Coil
- AndroidX Media3 / ExoPlayer
- Gradle 8.13 wrapper

## 项目结构

```text
app/
  src/main/java/com/czpn7/ying/
    data/          API DTO、Repository、Room/DataStore 模型
    di/            Hilt 模块
    player/        Media3 播放页与缓存 Provider
    ui/            Compose 页面、组件、主题
    MainActivity.kt
docs/
  medeo-logo-preview.svg
  screenshots/
gradle/
  wrapper/
```

## 开发环境

1. 安装支持 JDK 17 的 Android Studio。
2. 安装 Android SDK 36。
3. 用 Android Studio 打开项目根目录。
4. 使用仓库内置 Gradle wrapper 同步项目。

常用命令：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK 输出路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release 构建

Release 签名读取 `keystore.properties` 或环境变量。

推荐本地配置文件：

```properties
storeFile=keystore/medeo-release.jks
storePassword=change-me
keyAlias=medeo
keyPassword=change-me
```

`keystore.properties` 和真实 keystore 文件已被 Git 忽略。可以使用 `keystore.properties.example` 作为模板。

环境变量方式：

```bash
export MEDEO_RELEASE_STORE_FILE=keystore/medeo-release.jks
export MEDEO_RELEASE_STORE_PASSWORD=change-me
export MEDEO_RELEASE_KEY_ALIAS=medeo
export MEDEO_RELEASE_KEY_PASSWORD=change-me
```

构建命令：

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

Release APK 输出路径：

```text
app/build/outputs/apk/release/app-release.apk
```

## 上传前检查

推送到 GitHub 前，确认以下文件没有被 Git 跟踪：

- `keystore.properties`
- `keystore/*.jks`
- `local.properties`
- `app/build/`
- 任何生成的 `.apk` 或 `.aab`

分发 APK 前，建议至少完成以下验证：

1. 启动应用并确认免责声明弹出。
2. 确认首页热榜和海报正常加载。
3. 搜索一个片名并打开详情页。
4. 至少播放一集。
5. 确认收藏和继续观看正常。
6. 确认设置页可以清空缓存。
7. 确认应用没有向外部存储写入视频文件。

## 致谢

感谢以下开源项目在产品、UX 和媒体应用生态上提供参考：

- [LibreTV](https://github.com/LibreSpark/LibreTV)
- [OrionTV](https://github.com/orion-lib/OrionTV)
- [LunaTV](https://github.com/MoonTechLab/LunaTV)
- [Kazumi](https://github.com/Predidit/Kazumi)

medeo 是独立的 Android 实现。以上致谢不表示本仓库打包或复用了这些项目的代码。

## License

当前尚未选择开源许可证。在添加许可证前，默认保留所有权利。
