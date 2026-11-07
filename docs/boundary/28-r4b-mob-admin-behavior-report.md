# 行為報告：R4b 怪物改成資料檔，網頁可以設定和新增怪物（A6、A7，T2）

> boundary-first 第 6 步的產出。邊界在 [21](21-r4b-mob-admin-boundary-note.md)，行為在 [22](22-r4b-mob-admin-given-when-then.md)，決定在 [23](23-r4b-mob-admin-open-questions.md)，prompt 和每一步的 SOLID／Pattern 檢查在 [prompts/round-r4b-01-boundary.md](prompts/round-r4b-01-boundary.md)、[prompts/round-r4b-02-implementation.md](prompts/round-r4b-02-implementation.md)。
> **決定的狀態**：主 session 依使用者夜間授權採用建議值（Q1～Q8 都是 A，O1～O21 照建議），**使用者 2026-11-08 確認：全部照建議**（原話：「全部照建議」）。
> 方向：使用者 11/07 的主軸「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。
> **合併到 main**：依 review-05 F1，把 combo（`3c4eb42`）合進來，merge commit `23246ed`。合併後單元 505/505、遊戲內 77/77（含原版 1 個），之後由架構師快轉 main，見[第 9 節](#9-合併到-main2026-11-07-夜間review-05-f1)。第 1～8 節是合併前的數字和內容。

## 1. 結果一覽

| 項目 | 結果 |
|---|---|
| 分支 | `feature/r4b-mob-admin`，從 main `061e7a9` 開，合進 main `6a3e1fc`（merge `403d80b`）。沒有 push，沒有動 main |
| commit | `7eb563f`（邊界文件 21～23）→ `403d80b`（merge main）→ `949597d`（實作）→ 本報告 |
| 單元測試 | **430 / 430 通過**（main `6a3e1fc` 是 375；新增 55） |
| 遊戲內系統測試 | **69 / 69 通過**（68 個我們的加原版 `always_pass`；main 是 61，新增 8） |
| 玩家加一種怪物（T2） | **1 個 JSON 檔、0 行 Java、0 個資源檔**，重開遊戲。遊戲內測試用只存在於 `config/mobarmory/mobs/goblin.json` 的 goblin 證明 |
| 網頁改怪物（A6、A7） | 名稱、外觀（身體、大小）、數值、裝備、行為都能改；名稱、數值、大小立刻作用在世界上的同種怪物，行為和裝備等下次生成，身體等重開 |
| 網頁新增怪物 | 填表單 → 寫出 `mobs/<id>.json` → 標「待重開」，和新增武器同一個流程 |
| 行為 | 攻擊方式三選一（主動攻擊玩家／中立：被打才反擊／不攻擊）；奔跑＝追擊速度倍率；衝刺＝原版撲向目標；閒晃開關。全部是原版 AI goal，沒有新 AI、沒有新特效 |
| 既有行為 | 不變：兩個預設檔讀出來的狂戰士、遊擊兵和 R4 一模一樣，R4 的單元和遊戲內測試只改了「怎麼拿到狂戰士」，預期數字一個都沒改 |
| 網頁實測 | 在預覽環境（測試用的武器庫、怪物庫，都在記憶體裡）用瀏覽器實際操作過：編輯存檔、欄位錯誤、新增怪物、重新載入。**沒有開 `runClient`**（任務限制：其他 session 也在用這台電腦） |

## 2. 改了什麼

| 層 | 檔案 | 內容 |
|---|---|---|
| `core/mob/` | `MobLibrary`、`MobReloadReport`（新） | 和 `EquipmentLibrary` 同形狀：啟動時決定有哪些生物種類；定義可以整份換；待重開；檔案已刪除；身體要重開 |
| | `NeutralBehavior`、`LeapBehavior`、`SpeedBehavior`（新） | 中立、衝刺；有速度的三種行為共用的小介面 |
| | `Behavior` | 速度範圍改成大於 0、不超過 3（O7）；`choosesTargets()`、`needsTarget()` 讓行為組合的規則不用寫死名稱 |
| | `MobDefinition` | 多 `name`（同武器的名稱規則）；攻擊和中立只能選一種、衝刺要搭配其中一種；`withLook` |
| `data/` | `MobFileJson`（新） | 怪物檔格式 v1；每個錯誤都標出欄位；行為的讀寫查對照表 |
| | `MobStore`、`MobFolder`（新） | 怪物檔放哪裡；資料夾版沿用 combo 合併後的 `JsonFolder`（列出、原子寫檔），見第 9 節 |
| | `MobLoader`、`MobLoadException`、`MobEditor`、`MobSetup`、`DefaultMobs`、`MobAssets`（新） | 讀全部、改檔再改遊戲、啟動時的規則、jar 裡的預設檔、生怪蛋外觀 |
| `web/` | `MobApi`（新） | `/api/v1/mobs` 的列出、讀、存、新增、重新載入，`/api/v1/mob-options` |
| | `MobAdmin`、`MobJson` | 架構師的唯讀版擴充成可以存檔；清單原本的欄位全部保留，多 `name`、`status`、`mob` |
| | `WeaponApi`、`AdminHttpServer` | 怪物的路由從 `WeaponApi` 搬到 `MobApi`；伺服器依路徑分給兩個 API |
| `forge/` | `ServerMobAdmin`、`MobSyncChannel`、`MobNames`、`MobStartup`、`NeutralGoalBinder`、`LeapGoalBinder`、`ServerThread`（新） | 接上遊戲；同步名稱；兩個新行為；共用的伺服器執行緒工具 |
| | `ArmoryMob` | 用 id 向 `MobLibrary` 拿現在的定義；生成和載入時套用基礎數值和大小；名稱讀定義 |
| | `MobEntityFactory`、`ArmorySpawnEggItem`、`BehaviorGoals`、`MobArmoryMod`、`MobArmoryClient` | 依 `MobLibrary` 註冊；生怪蛋名稱讀定義；登記兩個 binder；接線；離開伺服器時清掉收到的名稱 |
| | `ReloadCommand`、`ServerWeaponAdmin`、`client/GeneratedWeaponPack` | 指令改成一串 section；執行緒程式搬到 `ServerThread`；執行時資源包改收一組固定檔案（語系檔＋生怪蛋外觀） |
| `resources/` | `mobarmory/default_mobs/brute.json`、`skirmisher.json`（新） | 預設怪物 |
| | 刪除 `content/ModMobs.java`、`items/brute_spawn_egg.json`、`items/skirmisher_spawn_egg.json`、語系檔兩個怪物名稱 | 怪物不再寫在 Java |
| | `mobarmory/web/` | 怪物分頁可以點、編輯；新增怪物分頁；重新載入一次載兩種 |
| 建置 | `build.gradle` | 遊戲內測試前，把兩個預設怪物檔也放進 `run-gametest/config/mobarmory/mobs/`（旁邊有 goblin.json） |

## 3. 結構摘要

```
web/MobApi ──只認得──▶ web/MobAdmin（介面）
                         ├─ forge/ServerMobAdmin（遊戲：ServerThread → MobEditor → 套到世界上的怪物 → MobSyncChannel）
                         └─ test/TestMobAdmin（記憶體）
data/MobEditor ──▶ data/MobStore（介面：MobFolder／InMemoryMobStore）
               ──▶ core/MobLibrary（快照）──▶ core/MobCatalog ──▶ core/MobDefinition
forge/ArmoryMob ──用 id 查──▶ core/MobLibrary.current()
               ──▶ BehaviorGoals ──▶ GoalBinder：Wander、AttackPlayers、Neutral（新）、Leap（新）
core/Behavior ◀── SpeedBehavior ◀── WanderBehavior、AttackPlayersBehavior、NeutralBehavior
             ◀── LeapBehavior
ReloadCommand ──▶ [Section：weapons(WeaponAdmin)、mobs(MobAdmin)]
```

沒有新的繼承層級；`ArmoryMob` 還是唯一的怪物類別。

## 4. 測試報告（Given／When／Then 對照）

每一列的詳細文字在 [22](22-r4b-mob-admin-given-when-then.md)。「遊戲內」是 GameTest 的名稱，其他是 JUnit 類別和方法。

### H：正常情況

| # | 情境 | 測試 | 結果 |
|---|---|---|---|
| H1 | 第一次啟動放入兩個預設檔，和 R4 一樣 | `MobFilesTest.defaultsOnlyOnTheFirstStart`（資料夾版、記憶體版都跑）；`MobSetupTest.firstStartAndAPlayersFile`；`DefaultMobsTest`（狂戰士 10／0.175 等 R4 的數字）；遊戲內 R4 的全部怪物測試 | 通過 |
| H2 | **T2**：只放 `goblin.json` 就多一種怪物 | 遊戲內 `t2_goblin_from_json_only`（名稱 Goblin、生命 12、攻擊 3＋2＝5、暴擊 0.25、速度 0.3×1.1＝0.33、大小 0.8、拿短刀、生怪蛋「Goblin Spawn Egg」借屍殼蛋）；遊戲內 `t2_every_mob_spawns_as_defined`、`h7_custom_mobs_have_every_stat`（goblin 也在裡面）；`MobContractTest`（契約跑過三個檔）；`MobSetupTest.firstStartAndAPlayersFile` | 通過 |
| H3 | 網頁新增怪物 → 寫檔、待重開 | `MobApiTest.newMobWaitsForARestart`；`MobFilesTest.create`；`MobLibraryTest.newMobsWaitForARestart`；網頁預覽實測 | 通過 |
| H4 | 網頁把狂戰士攻擊 4 改 8，世界上那隻立刻 14 | 遊戲內 `mob_save_changes_stats_at_once`；`MobApiTest.saveChangesTheMob`；`AdminHttpServerTest.mobsOverHttp`（真的 HTTP PUT）；網頁預覽實測 | 通過 |
| H5 | 行為改成中立：世界上那隻照舊，新生成的才中立 | 遊戲內 `mob_save_changes_behaviors_for_new_mobs` | 通過 |
| H6 | 中立：不主動攻擊，被打才追 | 遊戲內 `neutral_mob_waits_until_hit`（生存模式玩家在 3 格外，60 tick 沒事；打一下就鎖定那位玩家） | 通過 |
| H7 | 奔跑：追擊速度 1.3 | 遊戲內 `t2_goblin_from_json_only`（近戰 goal 的速度倍率是 1.3） | 通過 |
| H8 | 衝刺：多了原版撲擊 | 遊戲內 `t2_goblin_from_json_only`（有 `LeapAtTargetGoal`）；`mob_behaviors_all_have_binders`（binder 契約：每種行為至少加一個 goal） | 通過（真的跳起來沒有測，見第 7 節） |
| H9 | 手改檔案再 `reload`，世界上那隻跟著變 | 遊戲內 `mob_reload_reads_hand_edits`；`MobApiTest.reload`；`MobFilesTest.reload` | 通過 |
| H10 | 改名稱：立刻顯示，送給玩家的封包是新名稱 | 遊戲內 `mob_save_changes_name_and_size_at_once`（實體名稱、生怪蛋名稱、`MOB_SYNC.current()`） | 通過（真的第二台電腦沒有測） |
| H11 | 改大小：立刻變 | 遊戲內 `mob_save_changes_name_and_size_at_once` | 通過 |
| H12 | 存檔離開期間改了攻擊力，載入後照現在的定義、不疊加 | 遊戲內 `mob_loads_with_current_stats`（14、速度 0.175）；R4 的 `mob_reload_keeps_stats_once` | 通過 |
| H13 | 網頁列出怪物，架構師清單的欄位都在 | `MobApiTest.listsMobsAsDefined`、`mobWithNothingListsEmpty`；`AdminHttpServerTest.mobsOverHttp` | 通過 |

### B：成功條件被破壞時

| # | 情境 | 測試 | 結果 |
|---|---|---|---|
| B1 | 語法錯：啟動失敗、寫出檔名和行 | `MobFileJsonTest.brokenJsonNamesTheLine`；`MobSetupTest.wrongFileStopsTheStart`（`MobStartup` 把它變成啟動失敗） | 通過 |
| B2 | 不存在的數值、超出範圍 | `MobFileJsonTest.everyMistakeNamesItsField`；`MobApiTest.wrongStatsAreRefused`；網頁預覽實測（迴避率 0.9 標在那一格） | 通過 |
| B3 | 身體 creeper | 同上兩個；`MobFilesTest.loaderListsEveryProblem` | 通過 |
| B4 | 行為 fly | `MobFileJsonTest`；`MobApiTest.wrongLookBehaviorOrSpeedIsRefused` | 通過 |
| B5 | 攻擊和中立同時寫 → 錯誤 | `MobLibraryTest.behaviorCombinations`；`MobApiTest.behaviorCombinationsAreChecked`；`MobFileJsonTest` | 通過 |
| B6 | 只有衝刺 → 錯誤 | 同上；`MobFileJsonTest.leapNeedsATarget` | 通過 |
| B7 | 速度 0、負數、10 → 錯誤 | `MobLibraryTest.behaviorCombinations`；`MobFileJsonTest`；`MobApiTest` | 通過 |
| B8 | 武器不存在 → 照常生成、不拿那件 | R4 的 `mob_spawns_without_deleted_weapon`、`mob_equipment_from_startup_weapons`（沒改） | 通過 |
| B9 | 檔名不是合法 id | `MobFilesTest.loaderListsEveryProblem` | 通過 |
| B10 | 生怪蛋 id 撞到武器 id | `MobSetupTest.eggIdTakenByAWeapon` | 通過 |
| B11 | 網頁換身體 → 400；手改後 reload → 等重開 | `MobApiTest.bodyChangeWaitsForARestart`；`MobLibraryTest.bodyWaitsForARestart` | 通過 |
| B12 | 刪檔再 reload → 保留到重開 | `MobLibraryTest.reloadReportsEveryCase` | 通過 |
| B13 | 刪檔後重開，世界上那種怪物消失 | 平台行為，**沒有測**（遊戲內測試不能重開） | — |
| B14 | 新檔 reload → 待重開 | `MobApiTest.reload`；`MobLibraryTest.reloadReportsEveryCase` | 通過 |
| B15 | 有一個壞檔就什麼都不改；武器不受影響 | `MobApiTest.reload`；`MobFilesTest.reload`；`MobLibraryTest.reloadIsAllOrNothing` | 通過（「武器不受影響」靠兩個 section 各自執行，沒有組合測試） |
| B16 | 新增已有的 id → 400 | `MobApiTest.newMobWithTakenIdFails`；`MobFilesTest.create` | 通過 |
| B17 | 改最大生命不補血 | 遊戲內 `mob_save_never_heals`（25／40 → 25／30 → 25／60） | 通過（「滿血 40 改成 30 會降到 30」是原版在下一個 tick 做的，查過原始碼，沒有測） |
| B18 | 寫檔失敗 → 500，遊戲不變 | `MobApiTest.failedWriteChangesNothing`；`MobFilesTest.failedWriteChangesNothing` | 通過 |
| B19 | 後存的贏 | `MobFilesTest.saveWritesTheFileFirst`；遊戲內 `mob_save_changes_stats_at_once` | 通過 |
| B20 | 遊戲暫停時存檔 | 和武器共用 `ServerThread`，**沒有另外測怪物** | — |
| B21 | 沒有行為 → 木樁 | `MobApiTest.mobWithNothingListsEmpty`；R4 的測試 | 通過 |
| B22 | 拿重劍的狂戰士改速度 → 0.21 | 遊戲內 `mob_save_changes_stats_at_once` | 通過 |
| B23 | 另一台電腦少了怪物檔 → 連不上 | 平台限制，**沒有測** | — |

### 測試真的抓得到錯

- 故意拿掉 `ServerMobAdmin` 裡「把新數值套到世界上的怪物」那一行，4 個遊戲內測試失敗（`mob_save_changes_stats_at_once`、`mob_save_changes_name_and_size_at_once`、`mob_save_never_heals`、`mob_reload_reads_hand_edits`），改回後全過。
- 開發中抓到兩個真的錯：
  - 怪物加進世界時太早重算裝備，原版還沒記下手上的重劍。測試在第一個 tick 前把劍拿掉，−30% 就一直留著（R4 的 `brute_heavy_sword_slows_it` 抓到）。
  - 網頁「從檔案重新載入」按鈕在 main 上就送不出去（415），在網頁預覽實測時發現。

## 5. SOLID／Pattern 檢查

每一步的表格在 [prompts/round-r4b-02-implementation.md](prompts/round-r4b-02-implementation.md)。整體：

| 原則 | 結論 | 說明 |
|---|---|---|
| S 單一職責 | 沒有違反，修掉兩個 | `WeaponApi` 原本也處理怪物（架構師的唯讀清單），搬到 `MobApi`；切執行緒的程式原本在 `ServerWeaponAdmin` 裡，抽成 `ServerThread`。格式、讀檔、改檔、存放、啟動規則各一個類別 |
| O 開放封閉 | 沒有違反，修掉三個 | 加怪物從「改 Java」變成「放 JSON」；加一種資料要重新載入，只在 `ReloadCommand` 的 section 清單多一項；行為的讀寫和組合規則都不寫死行為名稱（對照表、`choosesTargets()`／`needsTarget()`） |
| L 里氏替換 | 沒有違反 | `MobStore`、`MobAdmin` 各兩個實作跑同一組測試；三個怪物檔跑同一組契約；四個 binder 跑同一組 binder 契約 |
| I 介面隔離 | 沒有違反 | `MobStore` 不繼承 `WeaponStore`（沒有貼圖）；`ArmoryMob` 只拿 `Parts`，看不到編輯器或網頁；同步只送名稱 |
| D 依賴反轉 | 沒有違反，修掉一個 | 原本定義在註冊時包死在 `ArmoryMob` 裡；現在依賴 `MobLibrary`。規則都在 `core/`、`data/`（純 Java，`CoreBoundaryTest` 守著 `core`）；`forge/` 只做接線 |
| Pattern | 沿用 | Factory（`MobEntityFactory`）、Strategy（`Behavior`＋`GoalBinder`，新增兩個）、快照式 Library（`MobLibrary`）、Adapter（`ServerMobAdmin`）。重新載入的 section 清單算小型的 Composite。考慮過泛型的 `DefinitionLibrary<I, D>`，照 23 的 Q2 A 這次不抽 |

## 6. 改一處會牽動幾個檔案（實測）

| 動作 | 估計（21） | 實際 |
|---|---|---|
| 新增一種怪物 | 1 個 JSON | **1 個 JSON**：goblin 只有 `src/gametest/config/mobarmory/mobs/goblin.json`，其餘 goblin 相關的檔案都是測試 |
| 改一種怪物的數值 | 1 個 JSON 或網頁一次存檔 | 同左（遊戲內和網頁預覽實測） |
| 新增一種行為 | 5 | 這次加了兩種，各動到：record、binder、`BehaviorGoals` 一行、`MobFileJson` 對照表一行、`app.js` 一個控制項，**5 個**；另外 `MobGameTests` 的 binder 樣本表多一行 |

## 7. 沒有覆蓋的範圍

- **`runClient`／`runServer` 走查沒有做**（任務限制）。所以下面這些只靠推論或原始碼，沒有在畫面上看過：
  - 生怪蛋的外觀（執行時資源包產生的 `items/<id>_spawn_egg.json`）。
  - 名稱在第二台電腦上的顯示。
  - 怪物真的撲起來的樣子。
  - 網頁在真的遊戲裡存檔：只在預覽環境和遊戲內測試（直接呼叫 `MOB_ADMIN`、HTTP 測試）驗證過。
- **多人**：兩台電腦的 `mobs/*.json` 不一樣時，Forge 的註冊表比對結果沒有驗證（推論和物品相同：連不上）。
- **刪掉怪物檔後重開**，世界上那種怪物會被略過（平台行為），沒有測。
- **`/mobarmory reload` 指令本身**沒有遊戲內測試；它呼叫的 `MOB_ADMIN.reload()` 有測。
- **從存檔載入的怪物拿的是存檔裡的裝備**（R4 原本的規則）。所以改了怪物檔的裝備，只有之後生成的怪物會換；記在 23 文末。
- **根目錄 `README.md` 已經過時**：第 13、91、178～206、236 行還寫「怪物要改 Java」「網頁只能看」。README 是架構師的文件，這次沒有改，要請架構師更新（新怪物＝放一個 JSON；網頁可以改和新增；多人時大家要有同一組 `mobs/*.json`）。

## 8. 動到其他人的檔案

| 檔案 | 原作者 | 改了什麼 | 行為 |
|---|---|---|---|
| `forge/ServerWeaponAdmin` | 架構師（R2b） | 切執行緒的程式搬到 `ServerThread` | 不變，武器測試全過 |
| `forge/ReloadCommand` | 架構師（R2b） | 改成一串 section；武器那段的訊息一字不改 | 武器不變，多了怪物 |
| `forge/client/GeneratedWeaponPack` | 架構師（R2b、段落 E） | `String languageFile` 改成一組固定檔案 | 語系檔照舊，多了生怪蛋外觀 |
| `web/WeaponApi`、`web/AdminHttpServer` | 架構師（R6、段落 A） | 怪物路由搬到 `MobApi`；依路徑分流 | 武器路由不變 |
| `web/MobAdmin`、`web/MobJson` | 架構師（段落 A） | 擴充成可以存檔；清單欄位全部保留 | 唯讀清單的欄位不變 |
| `mobarmory/web/*` | 架構師（R6、段落 A） | 怪物分頁、新增怪物分頁、標題改「Mob Armory 後台」；重新載入改送 `{}` | 武器分頁不變；修掉重新載入的 415 |
| 測試：`WeaponApiTest`、`AdminHttpServerTest`、`WebAdminGameTests`、`ContentResourcesTest` | 架構師、R4 | 怪物的測試搬到 `MobApiTest`；唯讀的 405 測試改成「從網頁存狂戰士」；標題字串；接 `MOB_ADMIN`；怪物名稱改在 JSON | 武器的預期數字一個都沒改 |

## 9. 合併到 main（2026-11-07 夜間，review-05 F1）

依 review-05 第 5-2 節和 F1，把 combo 的分支頭 `3c4eb42` 合進 `feature/r4b-mob-admin`。這就是架構師正在快轉的 main（main `56423bd`＋combo）。prompt 和每一步的 SOLID／Pattern 檢查在 [prompts/round-r4b-03-merge.md](prompts/round-r4b-03-merge.md)。

### commit

| commit | 內容 | 測試 |
|---|---|---|
| `23246ed` | merge `3c4eb42`：解 3 個衝突、`MobFolder` 改用 `JsonFolder`、`DefaultMobs` 的 Javadoc、新增遊戲內測試 `reload_command_prints_weapons_combos_mobs` | 單元 505、遊戲內 77 |
| 本節所在的 commit | 本節和 prompt 紀錄（只改文件） | — |

### 衝突怎麼解

| 檔案 | 衝突 | 怎麼解 |
|---|---|---|
| `forge/MobArmoryMod` | import 區 | 兩邊都留，拿掉 `ModMobs`（R4b 已刪） |
| 同上 | 註冊 reload 指令 | `List.of(ReloadCommand.weapons(WEAPON_ADMIN), ReloadCommand.combos(COMBO_FILES), ReloadCommand.mobs(MOB_ADMIN))` |
| 同上 | 啟動 log | 合成一行：`{} stats from {}, {} pieces of equipment from {}, {} mobs from {}, {} combos from {}`，數量用 `STATS.catalog().size()`、`MOB_LIBRARY.registered().size()` |
| `forge/ReloadCommand` | 4 處 | 用 R4b 的 section 版本。combo 的 `reloadCombos` 改寫成 `static Section combos(ComboFiles)`，訊息鍵照 combo 原本的（`reload.combos`、`reload.combos_failed`、`reload.combos_io`），失敗回 `-1` |
| `lang/en_us.json` | 1 處 | combo 的 3 行和怪物的 6 行都留 |
| `data/MobFolder` | **git 抓不到的語意衝突**：combo 合併時刪了 `ConfigFiles` | 改成持有 `new JsonFolder(root.resolve("mobs"))`，讀用 `files()`，寫用 `write(id.value(), json)`，和 `StatFolder` 一樣 |

自動合併、看過沒問題的：`FeedbackGameTests`、`MobArmoryGameTests`、`ComboGameTests`、`GeneratedWeaponPack`（和 review-05 的試算一樣）。

### 完成的標準

| 標準 | 結果 |
|---|---|
| `gradlew clean build runGameTestServer` 全過 | **單元 505/505、遊戲內 77/77**（76 個我們的加 `always_pass`），0 failure |
| 預期是單元 504、遊戲內 76 | 單元多 1：combo 合進 main 後的實際數字是 450，不是 review-05 估的 449，所以 450＋55＝505。遊戲內多 1：新增的 `reload_command_prints_weapons_combos_mobs` |
| `git grep -n "ConfigFiles\|content.ModMobs"` 沒有結果 | **`src/` 沒有結果**（Javadoc 已改）。全 repo 還有命中，都在文件裡：`README.md` 2 處是 F2（架構師）；其餘是 R4、R2b、R6、段落 E、combo 的歷史紀錄，寫的是當時怎麼做，沒改（combo 合併時也是這樣處理）。列表在 [round-r4b-03-merge.md](prompts/round-r4b-03-merge.md) 第 3 步 |
| `/mobarmory reload` 依序印出武器、combo、怪物三段 | 遊戲內 `reload_command_prints_weapons_combos_mobs`：執行真的指令，收集輸出，訊息鍵的順序是 `reload.done` → `reload.combos` → `reload.mobs.done`。另外 combo 的 `combo_new_file_adds_a_combo` 也執行真的 `/mobarmory reload`，證明 combo 那段有接上 |
| R4b 的行為不變 | 第 4 節的 H／B 測試全部照舊通過 |

### 還沒做的

- README 的怪物段落和多人規則（F2），交給架構師。
- 網頁的「從檔案重新載入」只載入武器和怪物，**不包括 combo**（review-05 S5）；combo 只能用 `/mobarmory reload`。這次沒改，照「預設不做」。
- `runClient` 走查（review-05 S1）還沒做。
