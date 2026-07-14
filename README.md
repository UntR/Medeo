# Medeo

[English](README-en.md)

一个给自己用的 Android 影视发现与播放工具：看热榜、跨源搜索、选集播放，收藏和观看进度都保存在本机。

**[下载最新版 APK](https://github.com/UntR/Medeo/releases/latest)**

> Medeo 不提供或托管影视内容；搜索结果和播放地址来自用户启用的数据源。

## 截图

<p>
  <img src="docs/screenshots/home.jpeg" alt="Medeo 首页热榜" width="30%" />
  <img src="docs/screenshots/favorites.jpeg" alt="Medeo 收藏页" width="30%" />
  <img src="docs/screenshots/settings.jpeg" alt="Medeo 设置页" width="30%" />
</p>

## 能做什么

- 浏览热门影视，也可以同时搜索多个数据源；某个源不可用时，其他结果仍会正常显示。
- 在详情页切换数据源、播放线路和集数。
- 使用 Media3/ExoPlayer 播放 HLS 视频，支持横屏、倍速和长按临时 2 倍速。
- 在本机保存收藏和观看进度；设置页可以管理数据源、主题和缓存。

## 安装

1. 从 [GitHub Releases](https://github.com/UntR/Medeo/releases/latest) 下载最新 APK。
2. 在 Android 设备上允许当前文件管理器或浏览器安装未知来源应用。
3. 安装后首次启动，阅读并确认免责声明。

系统要求：Android 7.0 或更高版本。

当前 Android application id 为 `com.untr.medeo`。如果设备上曾安装旧包名版本，它不会被覆盖升级。

## 数据与内容说明

- 使用数据源前，请确认你拥有相应授权，并遵守所在地法律法规和版权要求。
- 收藏、观看进度、设置和缓存都保存在本机；缓存可以在设置页清理。
- 不包含账号、云同步、广告或统计分析，也不提供视频下载、导出和分享功能。

## 致谢

Medeo 的产品和交互参考了以下开源项目：

- [LibreTV](https://github.com/LibreSpark/LibreTV)
- [OrionTV](https://github.com/orion-lib/OrionTV)
- [LunaTV](https://github.com/MoonTechLab/LunaTV)
- [Kazumi](https://github.com/Predidit/Kazumi)

Medeo 是独立的 Android 实现，没有打包或复用这些项目的代码。

## License

本项目采用 [MIT License](LICENSE)。
