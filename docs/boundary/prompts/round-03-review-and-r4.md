# 第 3 輪 prompt：審核 N1～N5、push、開始 R4

- 日期：2026-11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：開發 session（Claude），在 `mod/` 裡工作
- 使用的 skill：`boundary-first`（R4 的第 1～3 步和確認點）

## 使用者的決定

使用者原話：「2.github目前當作儲存倉庫 先不用寫任何授權其他同意」

- N1 授權：暫不處理，`LICENSE.txt` 和 `mods.toml` 的 license 維持現狀，GitHub 目前只當儲存庫。
- N2、N3、N4、N5：照建議。
- push、開始 R4：同意。

## Prompt 原文（監控 session 的整理）

> 請依序做，每一步都要測試通過才進下一步，並在本機 commit：
>
> 1. **N5「實驗性設定」警告**：先查證原因：暫時拿掉 `test_instance` JSON 和 `forge/gametest/` 建一次，看建立和載入世界時警告還在不在。如果是測試檔造成的：把測試 JSON 和測試類別移到只有 `runGameTestServer` 才會載入的位置（例如獨立的 source set 或資料包），正式 jar 不帶。`gradlew clean build runGameTestServer` 要照樣通過，測試數量不能變少。如果是 Forge 開發環境本身會跳：不改程式，在報告裡寫清楚查證過程。不管是哪一種，最後都要用 `runClient` 建一個新世界確認結果，並截圖。截圖前先確認全螢幕不會蓋到使用者正在用的其他程式。
> 2. **N2**：玩家近戰的暴擊判定要求蓄力滿格，和原版跳劈的條件一致。改在 `CombatEffectListener`，並補上「沒蓄滿不判定暴擊」的測試。
> 3. **N3**：新增「攻擊速度」數值，對應原版 attack speed；重劍攻速 −40%。照 R1 的規則加（`ModStats`、語系檔、重劍定義），並補上測試。行為報告裡寫出實際改了幾個檔案，用來示範「加一個數值只改少數檔案」。
> 4. **N4**：不改程式，只在決定紀錄寫「不要，維持原版規則」。
> 5. 把 N1～N5 的決定和結果記到 `04-behavior-report.md` 第 7 節（N1 寫「暫不處理，GitHub 目前只當儲存庫」），prompt 記到 `prompts/`，並更新 PROGRESS.md。
> 6. **push**：上面都完成、`gradlew clean build runGameTestServer` 全過之後，`git push`（包括已經在本機的 `716c41c`）。如果權限檢查擋下就回報，不要繞過。
> 7. **R4 自訂怪物**：呼叫 `boundary-first`，寫出 R4 的邊界草案、Given/When/Then、待決定問題清單（每題附建議），檔名接著用 05、06、07。需求來源是逐字稿重點第 3 節，以及驗收測試 T2「能把不同的生物或怪物加進系統」。**使用者回答前，不要寫 R4 的功能程式。**

## 每一步給自己的 prompt

### N5：把 GameTest 搬出正式 jar

> 先讀原始碼找原因，再動檔案。
>
> - 查證結果：Forge 26.3 的 `ResourceManagerRegistryLoadTask` 把「從非原版資料包載入的資料驅動註冊表內容」一律標成 `Lifecycle.experimental()`；`test_instance` 在原版 `RegistryDataLoader` 的世界註冊表清單裡（第 141 行）。我們的 16 個 `data/mobarmory/test_instance/*.json` 是模組裡唯一的這種內容。demo 世界的 `level.dat` 有 `confirmedExperimentalSettings`，Forge 自己的 `forgeLifecycle` 則是 `stable`。
> - 做法：新增 `gametest` source set（`src/gametest/java`、`src/gametest/resources`），把 `forge/gametest/` 的 4 個類別（改 package 為 `io.github.alextixu.mobarmory.gametest`）和 16 個 JSON 搬過去。只有 `gameTestServer` 這個 run 用 `mods { mobarmory { source main; source gametest } }` 把它併進模組；`jar` 只打包 main。
> - 測試程式改用 `@Mod.EventBusSubscriber(bus = MOD)` 在 `RegisterEvent` 自己註冊到 `Registries.TEST_FUNCTION`，主程式不再引用測試。
> - 加一個單元測試守住：`src/main/resources/data` 底下不能有 `test_instance`、`test_environment`。
> - 完成條件：`gradlew clean build runGameTestServer` 單元測試不少於 101、遊戲內測試仍是 16；正式 jar 裡沒有 `test_instance`；`runClient` 建新世界不再跳警告（截圖）。

### N2：玩家暴擊要蓄滿才判定

> - 規則放在核心：`CombatContext` 多一個 `fullStrength`（這一擊是否蓄滿；生物沒有蓄力，永遠算蓄滿，原本 5 個參數的建構子預設為 true）。`CritEffect` 只有在蓄滿時才擲暴擊；原版跳劈本來就要求蓄滿，所以兩邊條件一致。
> - `CombatEffectListener`：Minecraft 在觸發 `CriticalHitEvent` 之前就把蓄力歸零（`Player.attack` 先呼叫 `onAttack()`），所以改在更早的 `AttackEntityEvent` 記下這一擊的蓄力，暴擊事件再讀出來；門檻和原版一樣是 0.9。
> - 測試：Component（`CritEffectTest`：沒蓄滿不暴擊）；System（真的伺服器端玩家用 `Player.attack` 打牛：沒蓄滿 100% 暴擊率也不暴擊；蓄滿時暴擊、跳劈、兩者同時都是 1.5 倍）。

### N3：新增「攻擊速度」數值，重劍 −40%

> - 照 R1 的規則：在 `content/ModStats` 加一筆對應原版 `minecraft:attack_speed` 的定義（預設 4.0、範圍 0～1024），並加進登記表；在 `content/ModEquipment` 的重劍加上 `percent(ATTACK_SPEED, −0.40)`。
> - 原版已有翻譯「Attack Speed」，tooltip 和 `/mobarmory stats` 用的是 Attribute 本身的名稱，所以語系檔不用改。
> - 不准動：`core/`、`forge/`。新增數值不應該需要改任何事件處理器或轉接層。
> - 測試：Module（重劍：4.0 × 0.6 = 2.4）、契約（自動涵蓋）、System（玩家拿重劍攻速 2.4，放下回 4.0）。
