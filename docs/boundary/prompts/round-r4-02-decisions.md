# R4 第 2 輪 prompt：記下決定、實作 R4 自訂怪物（驗收 T2）

- 日期：2026-11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：R4 開發 session（Claude），在 worktree `mod-r4-mobs/`、分支 `feature/r4-mobs` 工作（從 main `d8d6753` 分出）
- 使用的 skill：`boundary-first`（第 4～6 步：分步實作、三層測試加契約測試、行為報告）

## 使用者的決定

使用者 11/06：**07 全部照建議**，Q1～Q7 都選 A，O1～O10 都同意。紀錄在 [07-r4-open-questions.md](../07-r4-open-questions.md) 的決定總表。

## Prompt 原文（監控 session 的整理）

> 任務：實作 R4 自訂怪物，讓驗收 T2「能把不同的生物／怪物加進系統」通過。
>
> 工作位置：
> - `java_minecraft_model/mod/`（main，`mc-dev` 在做 R2b＋R6）和 `java_minecraft_model/mod-damage-hud/` 都不要碰。
> - 建立自己的 worktree：`git -C java_minecraft_model/mod worktree add ../mod-r4-mobs -b feature/r4-mobs`，從 main 的 `d8d6753` 分出。之後所有修改、build、測試都在 `mod-r4-mobs/` 做。
> - 只在本機 commit 到 `feature/r4-mobs`，不要 push、不要合併。
> - 不要改 `PROGRESS.md`、`transcripts/`、`docs/boundary/README.md`。
>
> 步驟：
> 1. 把決定記下來：07 改成「已決定」，寫上「使用者 11/06：全部照建議」；05、06 改成「已確認」；prompt 記在本檔。
> 2. 照 05 第 4 節的順序分步實作。每一步都跑測試，通過了才做下一步。
> 3. **跨分支約束**：mc-dev 的 R2b 會刪掉 `content/ModEquipment`，改用 JSON 定義裝備，而且會先合進 main。所以怪物定義要用**字串 id 引用裝備**（例如 `"heavy_sword"`），執行時再從 `EquipmentCatalog` 查；不要直接用 `ModEquipment.HEAVY_SWORD` 這類常數；查裝備的地方集中在一處，行為報告裡寫清楚合併時要改哪裡。
> 4. 系統測試放在 `src/gametest`，至少要證明：新怪物有自訂數值（包括新加的最大生命值）、會攻擊附近生存模式的玩家、拿重劍會變慢、用生怪蛋和 `/summon` 生得出來。
> 5. T2 的證明：完成後實際示範一次「加第 3 種怪物只要加一筆定義」。像 N3 那次一樣記下改了幾個檔案、幾行；跑測試證明第 3 種也有效；示範完可以保留也可以移除，報告裡寫清楚是哪一種。
> 6. `gradlew clean build runGameTestServer` 要全部通過。目前的基準是單元測試 106 個、遊戲內測試 18 個。不要開 `runClient` 操作畫面或截圖（其他 session 也在用這台電腦），手動走查和截圖之後和示範影片一起做。
> 7. 寫行為報告 `docs/boundary/14-r4-behavior-report.md`，格式參考 `04-behavior-report.md`。
>
> 做到一半遇到 07 沒涵蓋的設計決定，就在 07 新增「第三輪」，把問題寫進去，停下來回報，不要自己決定。
>
> 回報：用繁體中文，包含 commit hash、測試數字、T2 證明（改了幾個檔案、幾行）、合併到 main 時要注意的地方。目標是週六晚上以前完成。

## 每一步給自己的 prompt

依 [05 第 4 節](../05-r4-boundary-note.md#4-實作順序)：

1. **`max_health` 數值**：照 R1 的規則，在 `content/ModStats` 加一筆對應原版 `minecraft:max_health` 的定義並加進登記表（O9）。原版已有翻譯，語系檔不用改。完成條件：既有單元測試和契約測試全過，`/mobarmory stats` 自動多一行。
2. **`core/mob`**：`MobId`、`MobDefinition`（數值 id → 基礎值、欄位 → 裝備 id 字串、行為清單、外觀）、`Behavior`（sealed 介面，只放資料：`WanderBehavior`、`AttackPlayersBehavior`）、`MobLook`、`MobCatalog`（Builder，用 `StatCatalog` 和 `EquipmentCatalog` 驗證：B1～B5 啟動失敗，訊息寫出怪物和出錯的地方）。不 import Minecraft（`CoreBoundaryTest` 守住）。附 Component 測試。
3. **`content/ModMobs`**：O1 的狂戰士、遊擊兵，裝備只寫字串 id。附「所有怪物定義跑同一組」的契約測試。
4. **`forge/` 生物種類**：`MobEntityFactory` 每筆定義註冊一個 `EntityType`、基礎屬性（`EntityAttributeCreationEvent`）、生怪蛋；`ArmoryMob extends Monster` 從共用定義讀行為和裝備，生成時穿上裝備，交給 R2 的 `EquipmentStatApplier`。裝備 id → 物品的查詢只在一處。
5. **`forge/` 行為**：`GoalBinder<B extends Behavior>`、閒晃、攻擊玩家（含被打反擊、16 格追擊距離），附 binder 的契約測試。
6. **`forge/client/`**：依 `MobLook` 選原版人形渲染器和貼圖，套用大小倍率。
7. **系統測試**（GameTest）、T2 示範（加第 3 種怪物）、`gradlew clean build runGameTestServer`、行為報告 14。

## 第三輪 Q8 的回覆（同日稍晚）

R4 開發 session 實作時發現 07 沒涵蓋的問題，寫進 07 第三輪 Q8（怪物的攻擊間隔要不要跟著攻擊速度，建議 A），請監控 session 轉達。監控 session 回覆：

> 07 第三輪 Q8 的答案：**B，不要**。怪物維持原版每秒打一下，攻擊速度只影響玩家。
>
> 使用者的原話：「這些是為了這個武器去做出來的特效，目前先不用做到。我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點。」
>
> 1. Q8 記為「B（使用者 11/06）」，原話也一起記下。06 有關的前提要改：狂戰士拿重劍時，攻擊速度數值會變成 2.4，但怪物的揮擊間隔不變。
> 2. R4 只做 07 已確認的最小範圍：數值可以設定（包括最大生命值）、閒晃、攻擊玩家、生怪蛋和 `/summon`、T2 的證明。
> 3. 不要為了武器或怪物另外加特效或額外規則。之後實作時再遇到「要不要多做某個效果」的問題，預設選「不做」，在報告裡記一行就好，不用停下來問。只有會影響數值或 T2 的設計問題，才停下來問。

處理：程式本來就是原版行為（等於 B），不用改；07 記下決定和原話，06 的數字前提改寫，14 的報告更新，並列出照新原則沒有做的效果。
