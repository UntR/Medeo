# Medeo

[English](README-en.md)

Medeo 是一个 Android 原生影视发现与在线播放应用，面向个人自用 sideload 场景。它提供豆瓣热榜式首页、多数据源搜索、详情页源/线路/集数切换、收藏、续看进度，以及基于 Media3/ExoPlayer 的播放体验。

> Medeo 不提供影视内容，不存储、不上传、不分发任何视频文件。搜索结果和播放地址来自用户启用的数据源；使用前请确认你拥有相应授权，并遵守所在地法律法规和版权要求。

## 截图

<p>
  <img src="docs/screenshots/home.jpeg" alt="Medeo 首页热榜" width="30%" />
  <img src="docs/screenshots/favorites.jpeg" alt="Medeo 收藏页" width="30%" />
  <img src="docs/screenshots/settings.jpeg" alt="Medeo 设置页" width="30%" />
</p>

## 功能

- 豆瓣热榜式首页，用于快速发现近期热门影视。
- 多数据源并发搜索，单个数据源失败不会影响其他结果。
- 详情页支持数据源、播放线路和集数切换。
- Media3/ExoPlayer 播放，支持 HLS、横屏沉浸式、倍速控制和长按临时 2x 播放。
- 自动保存观看进度，支持继续观看。
- 收藏夹、本地设置、Day/Night 主题切换。
- 设置页可启用或关闭数据源、查看并清理缓存。
- 首次启动会展示免责声明。

## 开源分发与安装

项目面向自用 sideload 场景，不上架应用商店。Medeo 通过开源仓库和 GitHub Releases 分发；源码用于公开审阅和追踪变更，日常安装请使用 Releases 中发布的 APK。

1. 从 GitHub Releases 下载 APK。
2. 在 Android 设备上允许当前文件管理器或浏览器安装未知来源应用。
3. 安装后首次启动，阅读并确认免责声明。

系统要求：Android 7.0 或更高版本。

当前 Android application id 为 `com.untr.medeo`。如果设备上曾安装旧包名版本，它不会被覆盖升级。

## 内容与隐私边界

- 应用只保存必要的本地元数据，例如收藏、观看进度、设置和缓存索引。
- 视频、图片和 HTTP 缓存位于应用缓存目录，可在设置页清理。
- 不包含账号系统、云同步、广告 SDK 或统计分析。
- 不提供下载、导出、保存到相册或分享视频文件功能。

## 致谢

感谢以下开源项目在产品形态、交互设计和媒体应用生态上的启发：

- [LibreTV](https://github.com/LibreSpark/LibreTV)
- [OrionTV](https://github.com/orion-lib/OrionTV)
- [LunaTV](https://github.com/MoonTechLab/LunaTV)
- [Kazumi](https://github.com/Predidit/Kazumi)

Medeo 是独立 Android 实现。以上致谢不表示本仓库打包或复用了这些项目的代码。

## License

本项目采用 [MIT License](LICENSE)。
