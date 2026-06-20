---
type: 提升方案 (Proposal)
status: 已执行 (Implemented)
created: 2026-06-20
base_version: v0.1.2
target_version: v0.1.3
sdd_ref: SDD_影_v0.1.md
note: 已按用户 2026-06-20 指令落地，并将存储/UI/行为契约回写 SDD。
---

# Medeo 功能提升方案 v0.1.3

> 基线：v0.1.2（已发布）。本方案基于对现有代码的逐处核实，而非 SDD 理想态。
> 约束遵循 `AGENTS.md` 硬红线：不触碰 `cacheDir` 外存储、不加权限、不引入下载/分享/统计/账号/云同步。

---

## 0. 前置核实结论

| 发现 | 位置 | 对方案的影响 |
|---|---|---|
| `CATEGORIES_CACHE_JSON` 键已定义但全项目从未读写 | `SettingsStore.kt:204` | 热榜持久化（A）无需新建基建，只需接线 |
| `WatchProgress` 已含 `positionMs`/`durationMs` | `WatchProgress.kt:17-18` | 「看完」判定（F）可纯计算，**无需 Room 迁移** |
| DTO 已暴露 `pagecount`/`total` | `VodListResponse.kt:11-12` | 分页（C）hasMore 判断现成可用 |

---

## 1. 方案总览

| # | 提升项 | 阶段 | 改动层 | SDD 同步 | 成本 |
|---|---|---|---|---|---|
| A | 热榜/分类树落 DataStore（冷启动兜底） | P1 | repo + local | 需更新 §5.4/§7.7 | 低 |
| B | 单源搜索软超时，避免慢源拖住 loading | P1 | repo | 否（实现澄清） | 低 |
| C | 搜索分页 / 加载更多 | P2 | repo + vm + ui | 需更新 §7.3/§8.4 | 中 |
| D | 搜索结果相关性排序 | P2 | repo | 否（实现澄清） | 低 |
| E | 进播放页首帧：先起播选中源，后台补候选 | P3 | vm + repo | 否（实现澄清） | 中 |
| F | 最近观看「看完」判定与下一集引导 | P3 | repo + ui | 需更新 §8.4.1 | 低 |
| G | 同关键词短时结果缓存 | P3 | repo | 否（实现澄清） | 低 |

> SDD 同步规则（AGENTS.md）：涉及存储/UI/行为契约的 **A、C、F 必须先确认方案 → 实现后回写 SDD**；B、D、E、G 属实现澄清，可直接落地并在 SDD 对应小节补一句说明。

---

## 2. 改造影响分层

```
┌──────────── UI 层 ────────────┐
│ SearchScreen   「加载更多」按钮 (C)  │
│ FavoritesScreen 看完标记/下一集 (F)  │
└───────────────┬───────────────┘
┌────────────── VM 层 ──────────┴───────────────┐
│ SearchViewModel   page 状态 + loadMore (C)      │
│ PlayerViewModel   两段式加载 (E)                 │
└───────────────┬───────────────────────────────┘
┌────────────── Repo 层 ────────┴───────────────┐
│ SearchRepository  软超时(B) 排序(D) 分页(C) 缓存(G)│
│ HotListRepository DataStore 读写 (A)            │
│ DetailRepository  单源优先解析 (E)               │
│ ProgressRepository 看完判定 (F)                  │
└───────────────┬───────────────────────────────┘
┌────────────── Local 层 ───────┴──────┐
│ SettingsStore  CATEGORIES_CACHE_JSON   │
│                读写 (A) — 槽位已存在     │
└───────────────────────────────────────┘
```

---

## 3. 分阶段路线图

### Phase 1 — 健壮性与合规（最先做，低风险）

- [x] **A. 热榜/分类树持久化**
  - 现状：`HotListRepository` 仅 `ConcurrentHashMap` 内存兜底（`HotListRepository.kt:25`），进程被杀后丢失；冷启动豆瓣失败 → 首页空白。
  - 目标：成功榜单 + 分类树序列化写入 `CATEGORIES_CACHE_JSON`（带 `ts`）；冷启动先渲染缓存（TTL 一周内有效，过期仍可作失败兜底）再后台刷新。
  - 改动点：`SettingsStore` 新增 `categoriesCache` 读写方法（复用已存在的 Key）；`HotListRepository.recentHot()` 失败分支回退到 DataStore 缓存。
  - 数据结构（写入 JSON）：
    ```json
    {
      "version": 1,
      "ts": 1718800000000,
      "category": "热门",
      "type": "全部",
      "items": [
        { "id": "...", "rank": 1, "title": "...", "posterUrl": "...", "rating": 8.6 }
      ],
      "categoryFilters": [],
      "typeFilters": []
    }
    ```
  - 验收：飞行模式冷启动，首页仍展示上次榜单而非空态；联网后自动刷新。
  - 测试：`HotListRepositoryTest` — 注入失败 API + 预置缓存，断言返回缓存内容。

- [x] **B. 单源搜索软超时**
  - 现状：读超时 15s（`NetworkModule`），单个挂死源把 `loading` 维持到超时。
  - 目标：`SearchRepository.kt:62` 每源 `withTimeoutOrNull(8_000)`，超时记为该源 0 结果并推进 `completed`，不影响其余源渐进回显。
  - 验收：模拟一个永不返回的源，搜索在 ~8s 内结束 loading。
  - 测试：`SearchRepositoryTest` 用延迟 fake，断言超时源计入 `failedSources` 且整体在阈值内完成。

### Phase 2 — 功能补齐

- [x] **C. 搜索分页 / 加载更多**
  - 现状：`search(keyword, page=1)` 固定首页（`SearchRepository.kt:32`），UI 无翻页。
  - 目标：`SearchRepository` 维护 per-source `{currentPage, pagecount}`；`SearchProgress` 增 `hasMore`（任一源 `currentPage < pagecount`）。`SearchViewModel.loadMore()` 并发拉各源下一页，按 `dedupKey` 并入既有 `AggregatedResult`。`SearchScreen` 列表底部加「加载更多」。
  - 验收：常见关键词可翻到第 2/3 页，新结果追加去重，无重复条目。
  - 测试：`SearchRepositoryTest` 断言第 2 页结果按 dedupKey 合并入第 1 页。
  - SDD：§7.3/§8.4 补充分页行为。

- [x] **D. 搜索结果相关性排序**
  - 现状：`aggregate()`（`SearchRepository.kt:99`）按返回偶然顺序，组间无排序。
  - 目标：组间按「`normalize(title)` 与 `normalize(query)` 匹配度」排序：完全相等 > 前缀命中 > 包含 > 其它，次级按源数量/年份倒序。组内仍按 `playbackPriority`。
  - 验收：搜索「庆余年」时精确同名排在「庆余年 番外」之前。
  - 测试：`SearchResultSelectionTest` 增排序用例。

### Phase 3 — 体验/性能打磨

- [x] **E. 进播放页两段式加载**
  - 现状：`PlayerViewModel.load()`（`PlayerViewModel.kt:266`）`details(candidates)` 等所有候选源 `awaitAll` 才结束 loading。
  - 目标：先 `detail(选中源)` 解析成功即起播；其余候选源后台补齐后并入 `uiState.details`（仅影响「切源」可用性，不阻塞首帧）。
  - 验收：多启用源时首帧时间 ≈ 单源详情耗时，不随启用源数量线性增长。
  - 测试：`PlayerUiStateTest` 断言选中源就绪即 `loading=false`，候选后续追加不重置当前播放。

- [x] **F. 最近观看「看完」判定**
  - 现状：进度每 5s 写库，无完成态，已看完剧长期占据最近观看。
  - 目标：纯计算 `positionMs/durationMs ≥ 0.9` 视为看完（无 Room 迁移）。最近观看里看完项弱化样式 +「看完」标记；若有下一集，主操作改为「看下一集」。
  - 验收：看到 ≥90% 的条目显示完成态并引导下一集。
  - 测试：`ProgressRepository`/UI 状态映射单测覆盖 90% 边界。

- [x] **G. 同关键词短时结果缓存**
  - 目标：`SearchRepository` 加 `query + enabledSources` 维度的 5 分钟内存缓存；命中直接回放，不重打源。
  - 验收：5 分钟内重复搜索同词无网络请求（日志验证）。

---

## 4. SDD 同步清单（实现后回写）

| 小节 | 变更 |
|---|---|
| §5.4 | `categories_cache_json` 实际写入结构（A） |
| §7.3 / §8.4 | 搜索分页与「加载更多」（C） |
| §7.7 / §9 | 热榜冷启动兜底自缓存（A） |
| §8.4.1 | 最近观看「看完」态与下一集引导（F） |

---

## 5. 风险与回滚

- A/F 无 schema 迁移、无新权限，纯本地元数据，符合存储红线；回滚 = 删方法。
- C 改 `SearchProgress` 数据形状，影响 `SearchViewModel` + `SearchScreen`，需一起改；以 feature 分支隔离。
- 全程不触碰 `cacheDir` 外存储、不加权限、不引入下载/分享/统计，守住 AGENTS.md 硬约束。

---

## 6. 建议执行顺序

```
Phase 1 (A → B)  →  Phase 2 (C → D)  →  Phase 3 (E → F → G)
```

每项独立小切片、独立验收，可随时停在阶段边界发版。

---

## 7. 待决事项（需用户拍板）

- [x] A、C、F 的 SDD 回写方向是否认可。
- [x] 阶段优先级/范围是否调整。
- [x] 是否接受 B 的软超时阈值 8s、F 的看完阈值 0.9、G 的缓存窗口 5min 等默认参数。
