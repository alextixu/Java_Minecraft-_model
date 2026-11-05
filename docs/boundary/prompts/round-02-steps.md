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
