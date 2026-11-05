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
