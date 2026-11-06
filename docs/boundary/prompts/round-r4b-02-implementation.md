# R4b 第 2 輪 prompt：實作怪物資料檔和網頁設定怪物

- 日期：2026-11-07 夜間
- 下指令的一方：監控 session（依使用者夜間授權，採用 23 的全部建議值）
- 執行者：R4 開發 session（Claude），在 worktree `mod-r4-mobs/`，分支 `feature/r4b-mob-admin`
- 使用的 skill：`boundary-first` 第 4～6 步（分步實作、三層測試加契約測試、跑到全過、行為報告）
- 依據：[21](../21-r4b-mob-admin-boundary-note.md)、[22](../22-r4b-mob-admin-given-when-then.md)、[23](../23-r4b-mob-admin-open-questions.md)（含文末「合併 main `6a3e1fc` 後的調整」）
- 產出：行為報告 [28](../28-r4b-mob-admin-behavior-report.md)

## Prompt 原文

監控 session 第三則訊息的原文記在 [round-r4b-01-boundary.md](round-r4b-01-boundary.md) 的「決定」一節。重點：

- 在 23 記下決定，commit 21～23。
- 把 main `6a3e1fc` 合進來，接在唯讀怪物清單和 JSON 數值上面；21～23 要調整的，在 23 記一行。
- 分步實作，每一步寫 SOLID／Pattern 檢查。`clean build runGameTestServer` 全過才 commit。T2 照 O20：只放一個 JSON 檔就能生出 goblin。
- 行為報告 28，要有「SOLID／Pattern 檢查」一節。不要 push，不要動 main。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 0 步：記決定、合併 main

Prompt：23 的狀態改成「主 session 依使用者夜間授權採用建議值（2026-11-07），待使用者確認」，附原話；21、22 跟著改狀態；commit。再 `git merge main`（`6a3e1fc`）。

結果：

- 文件 commit `7eb563f`；merge commit `403d80b`，沒有衝突（R4b 分支上只有文件）。
- 讀架構師的段落 A、E 後，有 8 處要照現況調整（`ConfigFiles` 已抽好、`web/MobAdmin`／`web/MobJson` 已存在、數值改成 JSON 等），記在 23 文末。

這一步沒有寫程式，SOLID 不適用。

### 第 1 步：抽共用零件 `ServerThread`

Prompt：把 `ServerWeaponAdmin` 裡「切到伺服器執行緒、等 10 秒、還原例外」搬到 `forge/ServerThread`，`ServerWeaponAdmin` 改成呼叫它，行為一行都不變。`AtomicFiles` 不用做，架構師已經抽成 `ConfigFiles`。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有，修掉一個 | 容易錯的執行緒程式只有一份，`ServerWeaponAdmin` 只管「改武器以後要做什麼」 |
| O | 沒有 | 之後第三種資料的 admin 直接呼叫，不用再抄 |
| L | 沒有 | `ServerWeaponAdmin` 對外的行為和例外種類不變；既有的武器測試（`WebAdminGameTests` 等）守著 |
| I | 沒有 | `ServerThread.Action` 只有一個方法 |
| D | 沒有 | 兩個 admin 依賴 `ServerThread` 這個小零件，不依賴彼此 |
| Pattern | 沿用 | 沿用 R2b 的 Adapter（`ServerWeaponAdmin`），只是把它的一部分抽成共用 |

### 第 2 步：`core/mob`

Prompt：

- `MobDefinition` 加 `name`。
- 行為加上「攻擊玩家和中立只能選一種、衝刺要搭配其中一種」的規則（O3、O4）。
- 速度上限 3（O7）。
- 新增 `NeutralBehavior`、`LeapBehavior`、`MobLibrary`（和 `EquipmentLibrary` 同形狀）、`MobReloadReport`。

純 Java，不碰 Minecraft。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `MobLibrary` 只管「現在的定義是哪一份」；檢查規則留在 `MobCatalog`、`MobDefinition` |
| O | 沒有 | 行為組合的規則不寫死行為名稱，而是問行為本身：`choosesTargets()`、`needsTarget()`。之後多一種「決定打誰」的行為，只要在它的 record 回答 true，規則不用改 |
| L | 沒有 | 三種有速度的行為都實作 `SpeedBehavior`；任何一種放進檔案格式或 binder 都一樣 |
| I | 沒有 | `SpeedBehavior` 只多一個 `speed()`；沒有速度的 `LeapBehavior` 不用實作 |
| D | 沒有 | `core/mob` 不 import Minecraft、Gson，`CoreBoundaryTest` 守著 |
| Pattern | 沿用 | Strategy（`Behavior`）；快照式 Library（同 `EquipmentLibrary`，整份換掉，讀的人拿一份快照） |

### 第 3 步：`data/`

Prompt：

- 新增 `MobFileJson`（檔案格式 v1）、`MobStore`／`MobFolder`、`MobLoader`、`MobLoadException`、`MobEditor`、`DefaultMobs`、`MobAssets`。
- 兩個預設檔的數字和 R4 一模一樣。
- 行為的讀寫查對照表，不寫 switch。
- 錯誤要標出欄位，網頁才能標在那一格。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 格式（`MobFileJson`）、讀全部檔案（`MobLoader`）、改檔再改遊戲（`MobEditor`）、存放（`MobStore`）、產生客戶端資源（`MobAssets`）各一個類別 |
| O | 沒有 | 行為種類 → 讀寫方式的對照表；加一種行為只加一行 |
| L | 沒有 | `MobStore` 的資料夾版和記憶體版跑同一組契約（`MobFilesTest`） |
| I | 沒有 | `MobStore` 不繼承 `WeaponStore`：怪物沒有貼圖 |
| D | 沒有 | `MobEditor` 依賴 `MobStore` 介面，測試換成記憶體版 |
| Pattern | 沿用 | 和 R2b 的 `WeaponJson`／`WeaponLoader`／`WeaponEditor`／`DefaultWeapons` 同形狀；直接呼叫 `WeaponJson.parse`（嚴格 JSON）和 `ConfigFiles`（原子寫檔），沒有抄 |

### 第 4 步：`forge/` 改成從檔案來

Prompt：

- `MobStartup` 在模組建構時讀檔，寫錯就啟動失敗並寫出檔名（O2）。
- `MobEntityFactory` 依 `MobLibrary.registered()` 註冊。
- `ArmoryMob` 用 id 向 `MobLibrary` 拿現在的定義：生成和載入時套用基礎數值和大小；行為照「建立時那一份」。
- 名稱讀定義，生怪蛋名稱跟著改。
- 刪掉 `content/ModMobs`、兩行語系、兩個生怪蛋外觀檔。
- R4 的測試只改「怎麼拿到狂戰士」。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `ArmoryMob` 多的只有「把定義套到自己身上」（`applyDefinitionStats`）；裝備的重算還是交給 R2 的 `EquipmentStatApplier`（用注入的 `statsChanged`） |
| O | 修掉一個 | 原本加怪物要改 `ModMobs`（Java）；現在加一個 JSON 檔，Java 0 行 |
| L | 沒有 | 每隻 `ArmoryMob` 不管定義從哪裡來，行為都一樣；R4 的 48 個遊戲內測試照舊通過 |
| I | 沒有 | `ArmoryMob.Parts` 把怪物需要的五樣東西包成一個參數，`ArmoryMob` 看不到 `MobEditor`、檔案或網頁 |
| D | 修掉一個 | 原本註冊時把定義包死在建構子裡；現在依賴 `MobLibrary`，網頁改了就到得了 |
| Pattern | 沿用 | Factory（`MobEntityFactory`）；Strategy（行為 binder） |

另外查證了兩件事（Minecraft 26.3 原始碼）：

- `Item.Properties` 的 `item_model` 是在最後依 id 設定的，用 `component(...)` 會被蓋掉，所以生怪蛋外觀改由執行時資源包產生。
- 最大生命值變小時，原版在下一個 tick 把目前血量降到上限（`LivingEntity.onAttributeUpdated`）。

### 第 5 步：中立、衝刺，網頁 API，同步

Prompt：

- `NeutralGoalBinder`（原版反擊＋近戰）、`LeapGoalBinder`（原版撲擊），在 `BehaviorGoals` 登記。
- 擴充 `MobAdmin`，新增 `MobApi`、`ServerMobAdmin`、`MobSyncChannel`（只送名稱）、`MobNames`。
- `ReloadCommand` 改成一串 section。
- `AdminHttpServer` 依路徑分給 `MobApi` 或 `WeaponApi`。
- 架構師唯讀清單的欄位全部保留。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 修掉一個 | 架構師把怪物的路由放在 `WeaponApi`；搬進 `MobApi` 後，`WeaponApi` 只管武器。`MobJson` 只管清單的樣子，`MobFileJson` 只管檔案格式 |
| O | 修掉一個 | `/mobarmory reload` 原本只認得武器；改成一串 `ReloadCommand.Section`，加一種資料只在接線多一項，指令本身不改 |
| L | 沒有 | `MobAdmin` 的兩個實作：網頁 API 測試用 `TestMobAdmin`，遊戲用 `ServerMobAdmin`，`MobApi` 不知道是哪一個 |
| I | 沒有 | `MobApi` 只依賴 `MobAdmin`；`MobSyncChannel` 只送客戶端需要的名稱 |
| D | 沒有 | web 層不 import Minecraft；`ServerMobAdmin` 是唯一接到遊戲的地方 |
| Pattern | 新用和沿用 | 沿用 Adapter（`ServerMobAdmin`）、Strategy（兩個新 binder）。重新載入的 section 清單算小型的 Composite／Command。**考慮過**把兩種重新載入合成一個 `/api/v1/reload`，要改 R2b 的回應格式，所以沒做，改成網頁按鈕依序呼叫兩個位址（記在 23） |

### 第 6 步：網頁、遊戲內測試

Prompt：

- 怪物分頁：架構師的清單改成可以點，右邊是編輯表單。
- 另一個「新增怪物」分頁。
- 「從檔案重新載入」一次載入兩種。
- 遊戲內測試：
  - T2 goblin 只靠 JSON。
  - 存檔後的立即生效：數值、名稱、大小。
  - 行為等下次生成。
  - 不補血、重新載入、從存檔載入。
  - 中立被打才反擊。
- 改共用狂戰士的測試照 `live_edit` 批次的規則：一個 tick 內做完，最後放回內附檔案。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 怪物和網頁的系統測試放新的 `MobAdminGameTests`；中立放 `MobGameTests`（它是行為測試，需要玩家） |
| O | 沒有 | binder 契約測試的樣本表多兩行，測試本身不改 |
| L | 沒有 | 契約測試跑過三個怪物檔（兩個預設、goblin） |
| I | 沒有 | — |
| D | 沒有 | 測試經由 `MobArmoryMod.MOB_ADMIN` 操作，和網頁、指令走同一條路 |
| Pattern | 沿用 | 參數化契約測試；main 的「批次內一個 tick 做完再還原」 |

## 結果

- 每一步寫完先編譯，`core`、`data`、`web` 寫完時跑單元測試，427 個全過；之後寫遊戲內測試和網頁，最後跑 `clean build runGameTestServer`。
- 中間的狀態沒有各自跑完整的遊戲內測試，所以實作合成一個 commit，沒有拆成每一步一個。
- 第一次完整執行有 2 個失敗，都修好了：
  1. `brute_heavy_sword_slows_it`：怪物加進世界時，我馬上用 R2 重算裝備。這時原版還沒記下牠手上拿著重劍，測試又在第一個 tick 前把劍拿掉，原版就不會發「裝備改變」事件，−30% 一直留著。改成：生成和載入時只設基礎數值，裝備照 R4 原本的做法在第一個 tick 由原版事件套上；只有網頁或 reload 改到「已經在世界上的怪物」時，才立刻重算裝備。
  2. `AdminHttpServerTest.servesThePage`：網頁標題從「武器後台」改成「後台」，測試跟著改。
- 為啟動時的規則（寫錯就停、生怪蛋 id 撞到武器）加單元測試時，發現測試會在專案根目錄留下 `logs/`：只要載入有 log4j logger 的 Forge 類別就會建立。所以把規則搬到純 Java 的 `data/MobSetup`，`forge/MobStartup` 只剩「放預設檔、寫 log、停止啟動」。這也符合 D：規則不依賴 Forge。
- 最後一次：**單元 430/430、遊戲內 69/69**（68 個我們的加原版 `always_pass`）。main `6a3e1fc` 是 375／61。
- 故意拿掉 `ServerMobAdmin` 裡「把新數值套到世界上的怪物」那一行，有 4 個遊戲內測試失敗：
  - `mob_save_changes_stats_at_once`
  - `mob_save_changes_name_and_size_at_once`
  - `mob_save_never_heals`
  - `mob_reload_reads_hand_edits`

  改回後全過。
- 網頁在預覽環境實測過：測試用的武器庫和怪物庫，都在記憶體裡，不寫檔、不開遊戲。
  - 怪物分頁的編輯表單：存檔成功；迴避率 0.9 時，錯誤標在那一格。
  - 新增怪物分頁：goblin 標「待重開」。
  - 「從檔案重新載入」：發現這顆按鈕在 main 上就送不出去（415），改成送 `{}` 後兩種都能載入。
