# R4 第 3 輪 prompt：把 main 合進 `feature/r4-mobs`（review-02 第 6 節）

- 日期：2026-11-07
- 下指令的一方：監控 session（轉達使用者 11/07 的決定「合併」）
- 執行者：R4 開發 session（Claude），在 worktree `mod-r4-mobs/`、分支 `feature/r4-mobs`
- 依據：`java_minecraft_model/reviews/review-02-d1-r4.md` 第 6-1～6-3 節
- 使用的 skill：`boundary-first`（每一步做 SOLID 和 Design Pattern 檢查；跑到全過才 commit；行為報告）

## 使用者的決定

- 11/07：「合併」。先把 R4 合進 main，之後用合併後的版本一次錄完示範影片。
- 11/07：review-02 6-3 a 選 **B**：怪物引用的武器不存在或欄位對不上時，不讓遊戲啟動失敗，改成 log 警告，怪物生成時不拿那件裝備。這條改了 07 已確認的 O2，記在 [07](../07-r4-open-questions.md) 第四輪。

## Prompt 原文（監控 session 的整理）

> 使用者 11/07 決定「合併」：先把 R4 合進 main，之後用合併後的版本一次錄完示範影片。合併的第一步交給你。
>
> 任務：在你的 worktree 把 main 合進 `feature/r4-mobs`
> - main 現在在 `40ee556`，是架構師（原 mc-dev）的 R2b＋R6 最終版，單元測試 237、遊戲內測試 29 全過。合併期間 main 不會再動。
> - 在 `java_minecraft_model/mod-r4-mobs/` 執行 `git merge main`。不要 rebase。不要動 main 分支本身。不要 push。
> - 照 `review-02-d1-r4.md` 第 6-1、6-2 節處理：解衝突；改 6-2 表格裡的介面點，包括第 6 點的建議；`en_us.json`：刪掉 4 件裝備的名稱，保留你新增的 3 行。
> - 6-3 d：你在 `ContentResourcesTest` 加的怪物檢查要保留，改接到 main 的新結構上。
> - 6-3 a，使用者選 B：怪物定義引用的武器不存在（檔案被刪），或欄位對不上時：不要讓遊戲啟動失敗，改成 log 警告，怪物生成時不拿那件裝備；重開遊戲和 `/mobarmory reload` 都要照這個規則；加單元測試，加至少一個遊戲內測試；這條改了 07 已確認的 O2，請在 07 記下：「使用者 11/07 選 B（review-02 6-3 a 的建議）」。
> - 6-3 b：加一個遊戲內測試。換掉重劍的定義（例如經由 `EQUIPMENT_LIBRARY` 替換）以後，拿重劍的狂戰士移動速度要跟著變。
> - 每一步都做 SOLID 和 Design Pattern 檢查，寫進 prompt 紀錄：S、O、L、I、D 各有沒有違反，用了或沿用哪個 pattern。
> - `gradlew clean build runGameTestServer` 全部通過才 commit。merge commit 加上後續修正的 commit 都可以。
> - 在 14 號行為報告加一節「合併到 main」：衝突怎麼解、改了哪些地方、測試數字。
>
> 做完就停下來回報 commit hash 和測試數字。main 會由架構師快轉到你的分支，你不用碰 main。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：merge，解衝突加 6-2 的介面點（commit `eecabeb`）

Prompt：`git merge main --no-commit`。四個衝突檔照 6-1 解：`MobArmoryMod` 用 main 的裝備接線，`MOBS` 放在 `EQUIPMENT_LIBRARY` 之後；`en_us.json` 刪 4 件裝備名稱；`ContentResourcesTest`、`MobArmoryGameTests` 兩邊都留。再改 6-2 的 1～6 點。這一步**不改 O2**（還是啟動失敗），讓 merge commit 只有「合併」本身，方便審查。跑 `clean build runGameTestServer`，全過才 commit。

結果：第一次跑，`WeaponApiTest` 失敗。它寫死 6 個數值，但 R4 加了 `max_health`，變成 7 個，是 git 抓不到的語意衝突。改成 `ModStats.CATALOG.size()` 後全過：單元 264、遊戲內 45。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `MobArmoryMod` 還是只做接線，沒有加邏輯 |
| O | 沒有，另外修掉一個 | 合併只改接線，R2b 的類別一行都沒改。`WeaponApiTest` 原本寫死數值個數，每加一個數值就要改測試，是 O 的徵兆；改成跟著登記表後，之後加數值不用再動它 |
| L | 沒有 | `EQUIPMENT_LIBRARY.current()` 回傳的就是 `EquipmentCatalog`，放進原本收 `EquipmentCatalog` 的地方，行為一樣 |
| I | 沒有 | `MobEntityFactory` 還是只拿「用 id 查物品」這一個函式（`EQUIPMENT::item`），沒有拿整個 `EquipmentItemFactory` |
| D | 沒有 | `core/mob` 只認 `EquipmentCatalog` 這個抽象，不知道武器是從 JSON 檔來的；換資料來源只動 `forge` 的接線 |
| Pattern | 沿用 | Factory（`MobEntityFactory`、main 的 `EquipmentItemFactory`）、Strategy（`GoalBinder`）、main 的快照式 `EquipmentLibrary`（整份換掉、讀的人拿一份快照） |

### 第 2 步：6-3 a，O2 的裝備部分改 B

Prompt：怪物引用的武器不存在或欄位對不上時，不要啟動失敗，改成 log 警告，生成時不拿。規則放在 `core`（純 Java、可單元測試），判斷「現在給得出去哪些武器」放在 `forge`（要看 `EquipmentLibrary` 和物品），`ArmoryMob` 只拿結果。重開和 reload 都適用。不改 R2b 的類別。加單元測試和遊戲內測試。

做法：

- `MobCatalog` 拿掉裝備驗證。
- 新增 `core/mob/MobEquipmentCheck` 和 `forge/MobEquipmentSource`。
- `ArmoryMob`、`MobEntityFactory` 改收「定義 → 要穿的物品」的函式。
- `MobArmoryMod` 加 `MOB_EQUIPMENT`，啟動時呼叫 `warnAll`。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 原本有，這次修掉 | `MobCatalog` 原本有兩個改變的理由：「定義寫得對不對」（程式碼，靜態）和「武器現在在不在」（檔案，會變）。現在分成三個類別：`MobCatalog` 只管定義；`MobEquipmentCheck` 只管規則；`MobEquipmentSource` 只管接上遊戲（目前的武器、做物品、寫 log） |
| O | 沒有 | 之後多一種「武器不能用」的來源（例如網頁加刪除），只改 `MobEquipmentSource.available` 一處，`ArmoryMob`、`MobEntityFactory`、`core` 都不用動 |
| L | 沒有 | `ArmoryMob` 拿到的是一個函式；遊戲用全域的 `MOB_EQUIPMENT`，測試用自己建的 `MobEquipmentSource`（不同的武器庫），`ArmoryMob` 不用知道是哪一個 |
| I | 沒有 | `ArmoryMob` 只拿「定義 → 物品」一個函式；`MobEquipmentCheck` 只拿「id → 定義」一個函式，不拿整個 `EquipmentLibrary` |
| D | 沒有 | 規則（`core`）不依賴檔案、物品或 Minecraft，`CoreBoundaryTest` 守著；`ArmoryMob` 依賴函式，不依賴 `EquipmentLibrary` |
| Pattern | 新用和沿用 | 新用 **Adapter**：`MobEquipmentSource` 把 R2b 的 `EquipmentLibrary.View` 接到 `core` 的規則。用函式注入當 **Strategy**：`ArmoryMob` 要穿什麼由外面決定。沿用 Factory。考慮過在 R2b 的 `ServerWeaponAdmin` 加 **Observer**（reload 時通知），讓「改欄位再 reload」立刻生效、警告也在 reload 當下印出；這會改 R2b 的類別，照「預設不做」沒有加，記在 07 第四輪 |

### 第 3 步：6-3 b、6-3 d 和測試

Prompt：

- 6-3 b：用網頁存檔同一個呼叫改重劍，拿重劍的狂戰士速度要跟著變。
- 6-3 a 的遊戲內測試：刪檔再 reload；重開時欄位不對或檔案被刪。
- 改共用武器的測試照 main 的規則，放進 `live_edit` 批次、一個 tick 內做完、最後放回內附檔案。不要另抄一份，沿用 `LiveEditGameTests` 的工具。
- 6-3 d：確認 R4 的兩條資源檢查在 main 的新結構下照樣跑。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 怪物和武器檔的測試放在新的 `MobWeaponGameTests`，不塞進原本的 `MobGameTests` 或 main 的 `LiveEditGameTests` |
| O | 沒有 | 契約測試 `getsAllItsEquipmentFromTheShippedWeapons` 是參數化的，加怪物不用改測試 |
| L | 沒有 | 沒有新的子類別 |
| I | 沒有 | — |
| D | 沒有 | 測試經由 `MobArmoryMod` 公開的同一批物件操作，和遊戲走同一條路 |
| Pattern | 沿用 | 契約測試（參數化）；main 的「批次內一個 tick 做完再還原」測試規則（`LiveEditGameTests.inBatch`，只把可見度從 private 放寬到 package） |

結果：`clean build runGameTestServer` 單元 268、遊戲內 48，全過。故意拿掉「檔案被刪就不給」的判斷時，只有 `mob_spawns_without_deleted_weapon` 失敗，改回後全過。

### 第 4 步：文件

- 07：決定總表加一列，O2 那列註明 11/07 改了；新增「第四輪」，內容是使用者的決定、理由、實作時的解讀。
- 06：B3 改成新行為。
- 14：開頭加合併結果；結構摘要、B3、B14 這幾列跟著改；新增第 8 節「合併到 main」。
- 本檔。
