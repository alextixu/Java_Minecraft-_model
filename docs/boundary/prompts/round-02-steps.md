# 第 2 輪：每一步的 prompt

每一步開始前，開發 session 依 [01 第 4 節](../01-boundary-note.md#4-實作順序) 把該步的邊界寫成下面的 prompt，只在這個邊界裡動手。每一步做完都跑測試，全部通過才 commit、才進下一步。

## 第 0 步：改名、刪除 MDK 範例

> 把 MDK 的 `examplemod` 改成已確認的正式名稱，刪掉 MDK 範例內容，不加任何功能。
>
> - 可以改：`build.gradle` 的 `group`、gameTest 命名空間、data 的 `--mod` 參數；`settings.gradle` 的專案名稱；`mods.toml`；刪除 `com/example/examplemod/` 整個 package。
> - 新建：`io.github.alextixu.mobarmory.forge.MobArmoryMod`，只有 `@Mod("mobarmory")` 和 `MODID` 常數，其他什麼都不做。
> - 不准動：Forge 版本、Java 25 toolchain、JUnit 設定、`pack.mcmeta`。
> - 授權（`license`）維持 MDK 原值，要不要改是使用者的決定，最後列在待決定事項。
> - 完成條件：`gradlew build` 成功，產出 `mobarmory-1.0.0.jar`；`git grep examplemod` 只剩文件裡的歷史描述。

## 第 1 步：`core/stat`（數值登記表與疊加規則）

> 在 `core/stat/` 新增純 Java 類別，不 import 任何 Minecraft 或 Forge 類別。
>
> - `StatId`：數值代號，只允許小寫英文、數字、底線。
> - `StatDefinition`：代號、預設值、上下限、下限比例（移動速度不低於基礎值的 30%）、對應的原版 Attribute（自訂數值則沒有）。預設值不在上下限內就丟例外（B7），訊息要寫出是哪個數值。
> - `StatCatalog`：登記表，用 builder 一筆一筆加，建好後不可變。id 重複（B6）、同一個原版 Attribute 被兩個數值對應，都丟例外。
> - `ModifierOperation`（`ADD` 固定值相加、`MULTIPLY` 百分比相乘）、`StatModifier`（數值、運算、大小；不接受 NaN、無限大，`MULTIPLY` 不能 ≤ −100%）。
> - `StackingRules`：值 =（基礎值 ＋ Σ固定值）× Π(1 ＋ 百分比)，再夾在 [max(下限, 下限比例 × 基礎值), 上限]（Q4 A）。
> - `core/StatReader`：唯讀查詢介面 `double get(StatId)`。
> - 測試（Component）：登記與查詢、B6、B7、H7、B2、B3、B4，測試名稱照抄 02 的 Given / When / Then。
> - 不准動：`forge/`、`build.gradle`。

## 第 2 步：`core/equipment`（裝備定義與換裝計算）＋ `content/`（雛型內容）

> 在 `core/equipment/` 新增純 Java 類別；在 `content/` 放雛型的數值和裝備清單（純資料，也不 import Minecraft）。
>
> - `EquipmentId`、`EquipmentSlotKind`（主手、副手、頭、胸、腿、腳）。
> - `EquipmentDefinition`：id、生效欄位、修飾清單、借用的原版外觀（O8）。同一件裝備對同一個數值有兩個修飾就丟例外（B14）。
> - `EquipmentCatalog`：依 `StatCatalog` 驗證。引用不存在的數值（B5）、id 重複，都丟例外，訊息寫出是哪件裝備、哪個數值。
> - `LoadoutCalculator`：輸入「每個欄位放了哪件我們的裝備」和「每個數值的基礎值」，輸出「有被裝備改到的數值的最終值」。只有放在自己生效欄位的裝備才算（O1、B1）；不記狀態，同樣的輸入永遠得到同樣的輸出（B11、B12 不會重複疊加）。
> - `content/ModStats`：攻擊力、移動速度（下限 30%）、護甲（對應原版）、暴擊率（上限 100%）、迴避率（上限 75%）。
> - `content/ModEquipment`：O7 的四件裝備。
> - 測試：Component（`EquipmentCatalog` 的驗證）、Module（02 的 H1、H2、H4、H7、B1、B2、B3、B4、B10～B13）、契約（所有裝備跑同一組：只引用已註冊的數值、修飾在範圍內、放在生效欄位才有效、拿起再放下回到原值、重算不會疊加）。
> - 不准動：`forge/`、`core/stat` 的公開介面。

## 第 3 步：`core/combat`（暴擊、迴避效果）＋ 契約測試

> 在 `core/combat/` 新增純 Java 類別（Q7 A：雛型就要有效果）。先寫介面，再寫實作。
>
> - `StatEffect`（Strategy）：`StatId stat()`、`CombatOutcome apply(CombatContext ctx, RandomSource rng)`。
> - `CombatContext`：攻擊者和防禦者的 `StatReader`、傷害、傷害種類（近戰、投射物、環境）、這一下是否已經是暴擊（原版跳劈）。
> - `CombatOutcome`：最後的傷害、是否取消、是否暴擊。
> - `RandomSource`：`double nextDouble()`，可以用種子重現。
> - `CombatEffects`：兩組效果。「防禦」在受到傷害前執行（迴避）；「攻擊」在打中時執行（暴擊）。`CombatResolver` 依序執行一組效果，被取消就停。
> - `DodgeEffect`：只有生物造成的傷害能迴避（O3、B8）；機率夾在數值上限內。
> - `CritEffect`：原版跳劈或機率判定成功，任一成立就乘一次 1.5，不疊加（O4、B9）。
> - `content/ModStats` 加上效果清單：新增一個有效果的數值 = 一行定義、一個效果類別、清單多一行。
> - 測試：Component（H5、H6、B8、B9、機率邊界）、契約（所有效果跑同一組：數值 0 永不觸發、數值在上限且亂數最小時必定觸發、超出範圍會被夾住、NaN 或負數不丟例外、同一個種子結果一致、環境傷害永不改變、傷害不會變成負數或 NaN）。
> - 不准動：`forge/`、`core/stat`、`core/equipment` 的公開介面。

## 第 4 步：`forge/` 數值掛到所有生物身上

> 在 `forge/` 新增轉接類別，只有這一層 import Minecraft／Forge。不改 `core/` 和 `content/` 的程式。
>
> - `StatAttributeBridge`：把 `ModStats.CATALOG` 的自訂數值註冊成原版 `RangedAttribute`（同步到客戶端）；原版數值對應到原版 Attribute。啟動時檢查每個原版 Attribute 都存在，不存在就啟動失敗（O2）。
> - `EntityStatBinder`：在 `EntityAttributeModificationEvent` 把每個自訂數值加到**每一種**生物（O5、H3）。
> - `EntityStatReader`：把一隻生物包成 `core.StatReader`，給戰鬥效果和之後的 combo 讀數值。
> - `MobArmoryMod`：只負責接線。
> - 語系檔加上自訂數值的名稱。
> - **System 測試提早到這一步建好**（每一步都要能跑測試）：Forge 66 只提供 `@GameTest` 註解和 `ForgeGameTestHooks.gatherTests`，沒有任何地方自動收集（第 1 輪記下的技術風險，這一步查證後確認）。做法改成直接用原版 26.x 的機制：測試函式用 `DeferredRegister` 註冊到 `Registries.TEST_FUNCTION`，測試實例寫成 `data/mobarmory/test_instance/*.json`，結構用原版的 `minecraft:empty`，用 `gradlew runGameTestServer` 執行，失敗時行程以非 0 結束。
> - 這一步的 System 測試：玩家、殭屍、骷髏都有每個自訂數值，值等於預設值（H3 的系統層）；每個原版數值都對應得到 Attribute。

## 第 5 步：`forge/` 裝備物品，換裝時改變數值（T1）

> 在 `forge/` 新增轉接類別。不改 `core/` 的規則；`content/` 只加資源檔（物品外觀、語系）。
>
> - `EquipmentItemFactory`（Factory）：依 `ModEquipment.CATALOG` 每一筆產生並註冊一個物品，不為每件裝備寫類別。護甲類加上原版的穿戴元件，外觀借用定義裡的原版素材（O8）。
> - `EquipmentItem`：物品說明（tooltip）每次都從共用定義讀，改了定義就跟著變（Q6 A）；格式沿用原版的「主手時：+6 攻擊傷害」。
> - `EquipmentStatApplier`：`LivingEquipmentChangeEvent`（只在伺服器端）→ 讀目前每個欄位的裝備 → `LoadoutCalculator` 算出目標值 → 每個數值只放**一個**固定 id 的修飾（`mobarmory:equipment`，值 = 目標值 − 基礎值）。沒有裝備改到的數值就移除修飾。因為 id 固定、計算不記狀態，重算幾次都不會疊加（B11、B12）。
> - `StatsCommand`：`/mobarmory stats [對象]` 列出目前數值和基礎值（O6），驗收 T1 時用。
> - 創造模式分頁放全部裝備（O9）；`assets/mobarmory/items/*.json` 指向原版模型；語系檔加物品名稱。
> - 測試：Component 加「內容資源契約」（每件裝備都有物品外觀檔和名稱、每個自訂數值都有名稱，漏了就失敗）；System（GameTest）：H1、H2（T1）、H4、H7、B1、B2、B12、B13 用 mock 玩家；另外讓一隻**會 tick 的殭屍**拿重劍，驗證換裝事件真的有接上。

## 第 6 步：`forge/` 暴擊、迴避在遊戲裡生效

> 在 `forge/` 新增 `CombatEffectListener`，把 Forge 的戰鬥事件轉成 `core.combat` 的呼叫，再把結果套回去。不改 `core/` 的規則，也不在事件處理器裡判斷是哪一種數值（O 原則）。
>
> - 防禦效果（迴避）掛在 `LivingAttackEvent`：在受傷流程最前面，取消就等於完全沒打中（沒有擊退、沒有受傷動畫）。
> - 攻擊效果（暴擊）分兩個入口，避免和原版跳劈疊加（O4、B9）：
>   - 玩家近戰：`CriticalHitEvent`。把原版是否跳劈傳進去，核心判定為暴擊就讓原版的暴擊流程生效、倍率 1.5，只乘一次，也會有原版的暴擊粒子。
>   - 其他（生物近戰、所有投射物）：`LivingHurtEvent`，直接改傷害。
> - 傷害種類：有生物攻擊者，且直接造成傷害的就是攻擊者本身 → 近戰；攻擊者另有投射物 → 投射物；沒有生物攻擊者 → 環境（不能迴避，B8）。
> - 亂數用被判定那隻生物自己的亂數產生器。
> - 測試：System（GameTest）：迴避率在上限的玩家被殭屍打，多次之中有被迴避也有被打中；迴避率在上限時摔落／火焰傷害每次都照常扣血（B8）；暴擊率 0 的殭屍打人，傷害等於原版。暴擊的實際倍率在 Component 層已測，遊戲內再手動確認。

## 第 7 步：遊戲內手動驗證、行為報告

> - 跑一次完整測試：`gradlew clean build runGameTestServer`，記錄單元測試和遊戲內測試的數量與結果。
> - `gradlew runClient` 開一個創造模式的單人世界，照 T1 的劇本手動驗證並截圖：
>   1. 空手 `/mobarmory stats` → 移動速度 0.1。
>   2. 從 Mob Armory 分頁拿重劍到主手 → 0.07，tooltip 顯示「主手時：+6 攻擊傷害、−30% 速度」。
>   3. 換到副手 → 0.1（B1）；再穿上重甲、重劍回主手 → 0.0595（B2）。
>   4. 丟掉重劍 → 回到不含重劍的值（B10）。
>   5. 拿著重劍存檔離開再進入 → 仍是 0.07，不會變成 0.049（B12）。
>   6. `/mobarmory stats @e[type=zombie,limit=1,sort=nearest]` → 殭屍也有暴擊率、迴避率（H3）。
> - 產出 `docs/boundary/04-behavior-report.md`：照 boundary-first 的最後回覆格式（邊界說明、商業決策、結構摘要、測試報告、沒有覆蓋的範圍）。
>
> **執行時的調整**：啟動 `runClient` 後發現使用者正在這台電腦上使用其他全螢幕程式，所以沒有送滑鼠鍵盤輸入，只用不搶焦點的方式擷取了主選單畫面，就把 client 關掉了。手動走查改成兩件事：(1) 補兩個遊戲內系統測試，用真的伺服器端玩家（`makeMockServerPlayerInLevel`）走換裝事件，並以玩家身分執行 `/mobarmory stats`；(2) 把手動劇本寫進行為報告第 6 節，等電腦空出來再走。
>
> **補做手動走查（2026-11-06 下午）**：使用者要求「操控我電腦給我目前的 demo」並「使用影片展示」。開發 session 開 `runClient`、建立創造模式超平坦世界，用 NVIDIA 錄影（使用者說 Alt+R，實測 Alt+R 是開關效能統計，改用 NVIDIA 預設的 Alt+F9 錄影），照行為報告第 6 節的劇本走完，截圖存到 `docs/boundary/screenshots/`。
