# 傷害顯示第 3 輪 prompt：把 R4 分支頭合進 `feature/damage-hud`（review-02 第 6 節）

- 日期：2026-11-07
- 下指令的一方：監控 session（轉達使用者 11/07 的決定「合併」）
- 執行者：傷害顯示 session（Claude），在 worktree `mod-damage-hud`、分支 `feature/damage-hud`
- 依據：`java_minecraft_model/reviews/review-02-d1-r4.md` 第 6-1 節（衝突）、6-3 e（D1＋R4 的組合）
- 使用的 skill：`boundary-first`（每一步做 SOLID 和 Design Pattern 檢查；跑到全過才 commit；行為報告）

## 使用者的決定

- 11/07：「合併」。D1 和 R4 都要合進 main，示範影片之後用合併後的版本一次錄完。
- R4 已經先合（`96b9262`，含 main `40ee556` 的 R2b＋R6 最終版）。架構師之後會把 main 快轉到這個分支。

## Prompt 原文（監控 session 的整理）

> 任務：把 `96b9262` 合進 `feature/damage-hud`
> - `96b9262` 是 `feature/r4-mobs` 的分支頭，裡面已經有 main `40ee556`（R2b＋R6 最終版）加上 R4，架構師會把 main 快轉到這裡。單元 268、遊戲內 48 全過。
> - 步驟：在你的 worktree 先把 `feature/r3-combo` 的工作 commit 起來，或確認工作目錄是乾淨的；`git switch feature/damage-hud`；`git merge 96b9262`。
> - 不要 rebase，不要動 main，不要 push。
> - 照 `review-02-d1-r4.md` 第 6-1 節解衝突，都是兩邊各加幾行，全部保留：`MobArmoryMod.java`；`en_us.json`（注意最後一行的逗號）；`MobArmoryGameTests.java`。
> - 6-3 e：加一個遊戲內測試，或在報告寫手動走查的步驟。內容是遊擊兵打玩家或被玩家打時，action bar 會出現 CRIT 或 Dodged／MISS。
> - 每一步都做 SOLID 和 Design Pattern 檢查，寫進 prompt 紀錄。
> - `gradlew clean build runGameTestServer` 全部通過才 commit。
> - 在 16 號行為報告加一節「合併到 main」：衝突怎麼解、測試數字。
>
> combo（19 號問題）還在等使用者回覆。在那之前不要寫 combo 的程式，`feature/r3-combo` 先放著。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：merge，解三個衝突

Prompt：工作目錄是乾淨的（`feature/r3-combo` 停在 `74bd43b`，沒有未 commit 的東西），`git switch feature/damage-hud`，`git merge --no-ff 96b9262`。三個衝突檔照 6-1 兩邊都留。`MobArmoryMod` 的裝備欄位用 R4 分支（＝main）的版本，因為 D1 那邊的寫法還是 `ModEquipment.CATALOG`，R2b 已經刪掉 `ModEquipment`。D1 只加回自己的三樣東西：`HIT_FEEDBACK` 欄位、帶 `HIT_FEEDBACK` 的 `CombatEffectListener` 建構子、`HIT_FEEDBACK.register()`。不改 R2b、R4 的任何類別。

結果：

- `MobArmoryMod` 3 個衝突區塊：import、欄位、接線。
- `en_us.json` 1 個：審查的試算是自動合併，但 R2b 的 reload 和網頁字串也加在檔案最後，所以實際上有衝突。照 6-1 補了逗號，用 JSON 解析器確認合法。
- `MobArmoryGameTests` 1 個。
- `CombatEffectListener` 自動合併，內容就是 D1 的版本，因為 main 和 R4 都沒動過。D1 的 `core/feedback`、`forge/feedback` 合併後一行都沒變（`git diff ed05c6b` 是空的）。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `MobArmoryMod` 還是只做接線。D1 加的是一個欄位和兩行註冊，沒有加邏輯 |
| O | 沒有 | 為了讓 D1 在 R2b、R4 上運作，R2b 和 R4 的類別一行都沒改。D1 的類別合併後也沒改（`git diff ed05c6b` 是空的）。兩邊只靠 `CombatObserver` 和接線接在一起 |
| L | 沒有 | `CombatEffectListener` 收 `HIT_FEEDBACK` 和收 `CombatObserver.NONE` 時規則一樣。R1 的遊戲內測試（暴擊、迴避）和 R4 的 `skirmisher_dodges_and_crits` 在合併後都照樣通過 |
| I | 沒有 | `CombatObserver` 還是兩個有預設值的方法，`HitDisplay` 一個方法。R4 不用實作任何 D1 的介面 |
| D | 沒有 | `core/feedback` 不 import Minecraft（`CoreBoundaryTest` 合併後照樣守著）。R4 的 `core/mob` 不知道有 D1。D1 不知道有 R4：顯示只看 `LivingEntity` 和傷害來源 |
| Pattern | 沿用 | D1 的 Observer（`CombatObserver` → `HitFeedbackListener` → `HitDisplay`）；R4 的 Factory（`MobEntityFactory`）、Strategy（`GoalBinder`）、Adapter（`MobEquipmentSource`）；R2b 的快照式 `EquipmentLibrary`。合併沒有新增 pattern |

### 第 2 步：6-3 e，遊擊兵的 CRIT／MISS／Dodged 遊戲內測試

Prompt：選「加遊戲內測試」，報告裡也附手動走查步驟，方便錄影時照著做。

- 用真的遊擊兵：經由 `MobArmoryMod.MOBS` 生成，和生怪蛋走同一條路，會拿到短刀。用的是它自己的數值，不覆寫：暴擊 0.5、迴避 0.30、攻擊 4。
- 三個情境：
  - 遊擊兵打玩家：出現 CRIT，數字是一般那一下的 1.5 倍。
  - 玩家打遊擊兵被迴避：出現 MISS。
  - 玩家迴避調到上限、被遊擊兵打：出現 Dodged。
- 隨機的部分照 D1 既有寫法：最多試 60 次，直到看到為止。
- 不寫死難度造成的數字，用「暴擊 = 一般 × 1.5」來比。
- 不改正式程式。如果需要改正式程式才能通過，就表示 D1 對 R4 的怪物有特例，要先停下來回報。

結果：

- 新增 `hud_skirmisher_crit_and_dodge`，放在 D1 的 `FeedbackGameTests`。
- 沒有改任何正式程式，一次就通過。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 一個測試方法負責「D1＋R4 的組合」，三個情境各拆成一個 private 方法。放在 D1 的測試類別，因為驗的是 D1 的顯示行為；R4 的 `MobGameTests` 不用動 |
| O | 沒有 | 正式程式零修改就通過，表示加新的怪物不用改 D1。這正是這個測試要證明的 |
| L | 沒有 | 這個測試本身就是一個 LSP 檢查：遊擊兵（`ArmoryMob`）放在攻擊者和被打者的位置，D1 給的結果和原版殭屍、牛一樣（同一組語系字串、顏色、「結果 = 實際少掉的血」） |
| I | 沒有 | — |
| D | 沒有 | 測試經由 `MobArmoryMod.MOBS.type(id)` 取得怪物種類（和遊戲同一個 Factory），不直接 new `ArmoryMob`。數值經由 `stats().holder(id)` 讀，不寫死屬性名稱 |
| Pattern | 沿用 | 測試替身的 Observer：既有的 `Recorder`，接在 `HIT_FEEDBACK` 上。R4 的等待寫法：`thenWaitUntil` 等短刀的數值套上去 |

### 第 3 步：跑全部測試、寫文件、commit

- `gradlew clean build runGameTestServer`：單元 322/322、遊戲內 57/57，伺服器報告「All 58 required tests passed」，多的一個是原版的 `always_pass`。
- 16 號行為報告：開頭加合併結果，新增第 8 節「合併到 main」，第 7 節註明已處理。
- 本檔。
- 沒有改 `docs/boundary/README.md`（由架構師維護），沒有碰 `feature/r3-combo`。
