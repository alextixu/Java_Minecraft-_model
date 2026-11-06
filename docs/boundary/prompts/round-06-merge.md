# 第 6 輪 prompt：R4、D1 合進 main

- 日期：2026-11-07
- 下指令的一方：主 session（監控・轉達・大綱）。指令經使用者審核，使用者 11/07 回覆「合併」。
- 執行者：架構師（原 `mc-dev`），在 `mod/` 的 main 上工作。負責 `core/`、`forge/`、`content/` 和 main 分支。
- 範圍：只快轉和補文件，不改程式。

## 指令（節錄）

> **長期規定**：每個實作步驟，寫程式前先在 prompt 紀錄寫下 SOLID、Design Pattern、變更邊界三件事，會違反就停下來回報；每份行為報告都要有一節「SOLID／Pattern 檢查」；指令和既有決定、邊界文件、其他分支的介面、SOLID／pattern 準則衝突時，停下來回報衝突在哪裡，不要自己挑一個做。
>
> **合併任務**：兩個分支各自把 main 合進去、測試全過以後，把 main 快轉到合併結果；補 `docs/boundary/README.md` 的索引（05～07、11～13、14、16）；重跑 `clean build runGameTestServer`。每一步都做 SOLID 和 pattern 檢查，有衝突就停下來回報。不能快轉也停下來回報。不要 push。

## 第 1 步：main 快轉到 `feature/r4-mobs`

`git merge --ff-only feature/r4-mobs`：`40ee556` → `96b9262`。分支那邊的結果：單元 268/268、遊戲內 48/48（主 session 核對過 XML）。照指示這一步沒有重跑測試。

- **變更邊界**：
  - R4 新增 `core/mob`、`forge/` 的怪物類別、`forge/client/ArmoryMobRenderer`、`content/ModMobs`。
  - R4 改到的既有檔案：
    - `content/ModStats`：加 `MAX_HEALTH`，是 R4 的決定 O9。08 的「不能動 `ModStats`」是 R2b 自己的邊界，不限制 R4。
    - `forge/MobArmoryMod`：組裝根。
    - `lang/en_us.json`。
    - 3 個測試檔：`WeaponApiTest` 的數值個數改成 `ModStats.CATALOG.size()`；`LiveEditGameTests` 的 3 個成員改成 package-private，給 R4 的測試重用。
  - `core/equipment`、`data/`、`web/` 的正式程式沒有動。
  - R4 拿武器只用 08 §3 列的穩定介面：`EquipmentLibrary.view()`、`catalog().find(id)`、`fileMissing()`、`EQUIPMENT::item`、`WeaponLooks.stackOf`。
- **SOLID**：
  - S：定義（`core/mob`）、行為對到 AI goal（`BehaviorGoals`）、發裝備（`MobEquipmentSource`）、建實體（`MobEntityFactory`）、畫面各自分開。
  - O：加一種行為只要一個 record、一個 binder、一行註冊；加一隻怪物只改 `ModMobs`。
  - L：每個 `GoalBinder<B>` 只處理自己那種行為，`register` 的型別配對保證這件事，啟動時 `verify` 再檢查一次。
  - I：`Behavior` 只有 `name()`，`GoalBinder` 只有 `bind`。
  - D：`core/mob` 只 import `core/stat`、`core/equipment` 和 JDK。
- **Pattern**：
  - Strategy：`Behavior` 加 `GoalBinder`，由 `BehaviorGoals` 對照表選。
  - Factory：`MobEntityFactory`，沿用 `EquipmentItemFactory` 的形狀。
  - Adapter：`MobEquipmentSource` 把 core 的檢查結果轉成 `ItemStack`。
  - 不可變快照：讀 `EquipmentLibrary.View`。
- **結論**：沒有衝突。

## 第 2 步：main 快轉到 `feature/damage-hud`

`git merge --ff-only feature/damage-hud`：`96b9262` → `d2382b9`。分支那邊的結果：單元 322/322、遊戲內 57 個加原版 1 個，0 失敗。

- **變更邊界**：
  - D1 新增 `core/feedback`、`forge/feedback`、`forge/CombatObserver`。
  - D1 改到的既有檔案：
    - `forge/CombatEffectListener`：三處通知觀察者；舊的建構子保留，預設 `CombatObserver.NONE`；`kindOf` 改成 public。
    - `forge/MobArmoryMod`：加 `HIT_FEEDBACK`，接到 `CombatEffectListener`。
    - `lang/en_us.json`：6 行。
    - `MobArmoryGameTests`：註冊一行。
  - 08 的「不准動 `CombatEffectListener` 的行為」沒有被違反：
    - 觀察者只聽不改，傷害、暴擊、閃避的判斷照舊。
    - 顯示壞掉時，`HitFeedbackListener` 會接住例外，不會影響戰鬥。
    - 原有的遊戲內測試全部照舊通過。
  - `core/equipment`、`data/`、`web/`、`content/` 沒有動。
- **SOLID**：
  - S：哪些傷害要顯示由 `HitFilter` 決定，顯示的文字由 `HitText` 組，畫面由 `ActionBarDisplay` 負責。
  - O：加一種顯示方式只要實作 `HitDisplay`，再加一行註冊。
  - L：用 `CombatObserver.NONE` 時，`CombatEffectListener` 的行為和 D1 之前一樣。
  - I：`CombatObserver` 有兩個 default 方法，`HitDisplay` 只有一個方法。
  - D：`CombatEffectListener` 依賴 `CombatObserver` 這個介面，不知道 action bar 存在；`core/feedback` 只 import `core/combat` 和 JDK。
- **Pattern**：
  - Observer：`CombatObserver`、`HitDisplay`。
  - 規則和顯示分開：`HitFilter` 是純 Java 的規則。
- **結論**：沒有衝突。

## 第 3 步：補索引、15 號報告補「SOLID／Pattern 檢查」

- **變更邊界**：只改文件。
  - `docs/boundary/README.md`：R4、D1 的索引，合併結果，維護者改名。
  - `15-weapon-data-web-behavior-report.md`：照長期規定第 3 點，新增第 11 節。
  - 本檔。
  - 14、16 是其他 session 的報告，這一輪不動。
- **SOLID／Pattern**：不改程式，不適用。15 號第 11 節是逐條對照現有的程式寫的。
- 照主 session 的指示，這是 main 上唯一的 commit。之後 main 先不再改，留給腳本匯出快照錄影。

## 第 4 步：在 main 重跑測試

`gradlew clean build runGameTestServer`，跑的是 `d2382b9` 的程式；第 3 步只改文件，所以 commit 之後程式一樣：

| 項目 | 結果 |
|---|---|
| 單元測試（`build/test-results/test/*.xml`） | **322 / 322**，0 失敗、0 錯誤、0 略過 |
| 遊戲內系統測試（`run-gametest/gametest-report.xml`） | **58 / 58**：我們的 57 個加原版 `minecraft:always_pass`，0 失敗；伺服器紀錄寫「All 58 required tests passed」 |
| 數字對照 | 單元：R1／R2 106 → R2b＋R6 237 → 加 R4 268 → 加 D1 322。遊戲內（不含 `always_pass`）：18 → 28 → 47 → 57 |
