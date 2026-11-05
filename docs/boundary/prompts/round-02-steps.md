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
