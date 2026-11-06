# 傷害顯示第 2 輪 prompt：使用者的決定與實作

- 日期：2026-11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：傷害顯示 session（Claude），在 worktree `mod-damage-hud`、分支 `feature/damage-hud` 裡工作
- 使用的 skill：`boundary-first`（第 4～6 步：分步實作、三層測試、行為報告）

## 使用者的決定

使用者決定照審查員的建議（`reviews/review-01-requirements-gap.md` 的 X3、P3-6）縮小 D1，只做畫面下方的 action bar。

| 題號 | 決定 |
|---|---|
| Q1 | A |
| Q2 | A：用 Observer，`CombatEffectListener` 只加通知掛勾，不改行為 |
| Q3 | B（調整）：劃掉 `FloatingTextDisplay` 和 `HitJournal`，其他同意 |
| Q4 | C：只做 action bar |
| Q5 | action bar 只給相關的玩家：攻擊的玩家和被打的玩家 |
| Q6 | 照 A 的 action bar 內容：目標名稱、最終傷害、CRIT 或 MISS、對方剩餘血量／最大血量，例如「Zombie −6.9 ❤ 13.1/20」 |
| Q7 | A |
| Q8 | A |
| O1～O10 | 照建議。只跟浮字有關的部分不適用（O7）。O3 保留「打死時剩餘血量顯示 0」 |

## Prompt 原文（監控 session 的整理）

> 先做這三件，再開始實作：
>
> - 把答案記到 13，狀態改成「已決定」。
> - 11、12 依答案修改。這次 Q3、Q4 不是 A，所以要重寫有關的部分。
> - prompt 記在 `prompts/damage-display-round-02-decisions.md`。
>
> 完成條件：
>
> - 單元測試和 `src/gametest` 系統測試都要有。
> - `gradlew clean build runGameTestServer` 要全部通過。
> - 行為報告寫成 `docs/boundary/16-damage-display-behavior-report.md`。
> - commit 在 `feature/damage-hud`。
>
> 做完就停下來回報。要不要送審查員，由監控 session 先問使用者。
>
> 注意：
>
> - 合併順序：開發 session 的 R2b 會先合進 main。它會刪掉 `content/ModEquipment`、修改 `MobArmoryMod`，D1 之後要照新的介面調整，所以盡量不要動 `content/` 和裝備相關的程式。
> - 不要改 `docs/boundary/README.md`，索引由開發 session 維護。
> - 新 session `r4-mobs` 在 `java_minecraft_model/mod-r4-mobs` 做 R4，不要碰那個資料夾。
>
> 下一個工作（等通知才開始）：R3 combo 的 boundary-first，最小範圍見審查報告第 3 節 P1-3，combo 名稱顯示在同一個 action bar，文件編號 17～19。

## 執行者給自己的 prompt（實作時照這個順序）

> 1. `core/feedback`：`HitReport`（結果）、`HitFilter`（Q5、Q7、O4、O6）、`HitText`（Q6、O1～O3、O8、O10），純 Java，`CoreBoundaryTest` 要繼續通過。先寫單元測試和 `HitText` 的契約測試。
> 2. `forge/CombatObserver`：`attackResolved`（每一次攻擊結算，不論有沒有暴擊）和 `hitCancelled`（迴避）。`CombatEffectListener` 加通知，不傳 observer 時行為完全不變。跑既有的 106 個單元測試和 18 個遊戲內測試。
> 3. `forge/feedback`：`HitFeedbackListener` 在 `LivingDamageEvent` 用監看式監聽拿最終傷害；用 `WeakHashMap` 記每個目標最近一次的攻擊結算來配對暴擊（取代被劃掉的 `HitJournal`），迴避時清掉；迴避只在對方本來會受傷時才回報（O5）。`ActionBarDisplay` 可以注入送出方式，給測試用。
> 4. `src/gametest`：不引用 `ModEquipment`，攻擊力直接設成 1、3、7。測 H1～H8、B1～B3、B5～B9、B11、B14～B16。
> 5. `gradlew clean build runGameTestServer` 全過，寫 16 號行為報告，commit。

## 結果

- 第 3 步的「監看式監聽」在實作時改成 `Priority.LOWEST` 的一般監聽：EventBus 7.0.6 在事件沒有一般監聽時不會呼叫監看式監聽，第一次跑遊戲內測試時 7 個新測試因此收不到結果。查證過程見 [16 號報告第 6 節](../16-damage-display-behavior-report.md#6-實作時發現的問題)。
- 第 4 步另外加了 H10、B16 的系統測試，共 9 個遊戲內測試；B4（盾牌）、B10、B13（action bar 一次一行的取捨）沒有寫測試。
- 最終：單元 160/160、遊戲內 27/27（伺服器報告 28，含原版的 `always_pass`）。
