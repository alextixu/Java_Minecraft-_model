# 第 8 輪 prompt：段落 E，數值改成 JSON 定義

- 日期：2026-11-07 清晨
- 下指令的一方：主 session（監控・轉達・大綱）
- 授權：
  - 使用者睡前授權：「繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收」。
  - 使用者定下的主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。
  - 這則指令沒有先經過使用者審核，主 session 已記在 `overnight-log.md`。
- 執行者：架構師，在 `mod/` 的 main 上工作（起點 `a448156`）
- 使用的 skill：`boundary-first`。依指令，問題清單採用建議值，不等回答。

## 指令（節錄）

> 1. 段落 A 的 README 開頭先寫這個主軸，說明玩家怎麼自己新增東西：武器（JSON 或網頁，可以上傳 PNG）、怪物（之後由怪物小弟改成 JSON 加網頁）、combo（之後由連招小弟改成 JSON）、數值（段落 E）。還在做的寫「進行中」。
> 2. 段落 E，數值也改成 JSON：
>    - 照 R2b 武器的做法改成 `config/mobarmory/stats/<id>.json`。第一次啟動時寫出 7 個預設檔；玩家多放一個 JSON（代號、預設值、上下限、要不要同步），重開遊戲就多一個數值，所有生物、所有武器都能用。
>    - 平台限制：屬性登記表啟動後凍結，新數值一定要重開。既有數值的預設值能不能執行中改，照最小範圍處理，寫進報告。
>    - 有戰鬥效果的數值（暴擊、迴避）還是要用 Java 的 `StatEffect`；只有「純數值」可以從 JSON 新增，寫進 README。
>    - `core/stat` 的公開介面和規則不變。這次要動 `content/ModStats`（之前列為不能動），在 SOLID 判斷裡說明為什麼。
>    - 邊界文件 24～26，問題清單第一個選項是建議值；寫完就照建議值直接實作，在 26 號註明「主 session 依夜間授權採用建議值，待使用者確認」。
>    - 全部測試通過才 commit，不要 push。行為報告是 27 號。

## 開始前的衝突檢查

- **`content/ModStats` 之前列為不能動**：
  - 08 號第 3 節、段落 A 的限制都這樣寫。
  - 這次指令明確要求改它，並要我說明理由，見 24 號「SOLID 判斷」。
  - 做法是常數、型別、`CATALOG`、`COMBAT_EFFECTS` 全部保留，只改說明文字，所以引用它的程式都不用改。
- **其他分支的介面**：
  - `feature/r3-combo` 用 `ModStats.MOVEMENT_SPEED.id()`，也用 `StatAttributeBridge(StatCatalog, String)`、`WeaponStartup.load(..., ModStats.CATALOG)`。
  - 這些全部保留可用。合併時，只有 `MobArmoryMod` 開頭幾行會有文字上的衝突，24 號第 3 節已寫明。
- **`core/stat` 的公開介面**：不改。名稱和同步這兩個 `StatDefinition` 沒有的欄位，放在 `data/StatEntry`，不加進 core。
- **結論**：沒有需要停下來的衝突。

## 第 0 步：README 的主軸（已完成）

- commit `a448156`。
- SOLID、Pattern：只改文件，不適用。

## 第 1 步：`data/` 讀數值檔

- **SOLID**：
  - S：
    - `StatJson` 只管一個檔案的格式。
    - `StatFolder` 只管檔案。
    - `StatLoader` 只管安裝預設檔、讀全部、檢查內建數值的種類、補上缺的內建值。
    - `StatSetup` 只是讀完的結果，並且產生語系內容。
  - O：加數值是加資料。
  - L：`StatStore` 的資料夾版、記憶體版跑同一組契約測試。
  - I：`StatStore` 只有 `exists`、`statFiles`、`writeStat` 三個方法。
  - D：`StatLoader` 只依賴 `StatStore` 介面和 `core/stat`，是純 Java，`CoreBoundaryTest` 會守住。
- **Pattern**：
  - Strategy：`StatStore` 加兩種實作，沿用 `WeaponStore`。
  - Builder：用 `StatCatalog.builder()` 建登記表，沿用 R1。
  - 不可變快照：`StatSetup`。
- **變更邊界**：
  - 新增 `data/StatJson`、`StatEntry`、`StatStore`、`StatFolder`、`StatLoader`、`StatSetup`。
  - 測試：`StatJsonTest`、`StatLoaderTest`、`StatStoreContractTest`、`InMemoryStatStore`。
  - 不動 `core/`。
  - **寫的時候追加的一項**：`WeaponFolder` 裡「先寫暫存檔再改名」和「列出資料夾裡的檔案」這兩個 private 方法，抽到 package-private 的 `data/ConfigFiles`，給 `StatFolder` 共用，避免複製。`WeaponFolder` 的行為不變，由 `WeaponStoreContractTest` 守住（S：寫檔的細節只在一處）。

## 第 2 步：`forge/` 接線

- **SOLID**：
  - S：
    - `StatStartup` 只管「啟動時讀，寫錯就停」，和 `WeaponStartup` 同一個形狀。
    - `StatAttributeBridge` 多一個參數，決定哪些自訂屬性要同步；舊的建構子保留。
    - `GeneratedWeaponPack` 多交給 Minecraft 一個語系檔，內容由 `StatSetup` 產生。這個資源包原本只放武器的模型和貼圖，現在也放數值的名稱：它的角色變成「玩家內容的執行時資源包」，名字沒有改，記在報告。
  - O：之後要加別種玩家內容的名稱，例如怪物，同一個語系檔多幾行就好。
  - L：讀檔產生的 `StatCatalog` 和 Java 建的是同一種型別，下游不用分辨。
  - I：不變。
  - D：`MobArmoryMod` 只是組裝根。武器、怪物、橋接都改吃 `STATS.catalog()`，不知道數值從哪裡來。
- **Pattern**：
  - Adapter：`StatAttributeBridge`、`GeneratedWeaponPack`，沿用。
  - 組裝根：`MobArmoryMod` 多兩個欄位，`STAT_FILES`、`STAT_SETUP`。共用元件欄位從 10 個變成 12 個，15 號第 11 節「組裝根變大」的注意事項更明顯，記在報告。
- **變更邊界**：
  - 新增 `forge/StatStartup`。
  - 修改 `forge/StatAttributeBridge`、`forge/MobArmoryMod`、`forge/client/GeneratedWeaponPack`。
  - `content/ModStats` 只改說明文字。

## 第 3 步：遊戲內測試

- **SOLID**：只改測試，不適用。
- **Pattern**：不適用。
- **變更邊界**：
  - 新增 `src/gametest/config/mobarmory/stats/` 的兩個測試用數值檔。
  - `build.gradle` 加一個複製的 task，在 `cleanGameTestRun` 之後、遊戲內測試之前執行。
  - 新增 `StatFileGameTests` 和它的測試設定檔。
  - 測試伺服器的數值資料夾裡只有這兩個檔，所以 7 個內建數值會走「檔案不在就用內建值」（B4）。這正好讓遊戲內測試也涵蓋這條規則。

## 第 4 步：文件

- README 的「加一個數值」、多人規則、已知限制。
- 27 號行為報告，含「SOLID／Pattern 檢查」。
- 文件索引。

## 結果

- 第 1 步之後：單元 375/375（新增 42）。
- 第 2 步之後：遊戲內 59/59，原有的測試全部照舊。測試伺服器第一次啟動寫出 7 個內建數值檔。
- 第 3 步之後：遊戲內 61/61。測試伺服器讀到兩個測試用的數值檔，7 個內建數值用內建值，log 有警告。
- `runClient`：
  - 在 `run/config/mobarmory/stats/` 寫出 7 個檔，log 寫「7 stats from …」。
  - 執行時資源包（含語系檔）載入，沒有錯誤。
  - 沒有進世界、沒有操作遊戲，看完 log 就關掉。
- 新數值的名稱是否真的顯示在 tooltip，沒有在畫面上確認，手動步驟寫在 27 號第 6 節。
- 最後在 commit 前跑一次 `clean build runGameTestServer`，數字見 27 號第 1 節。
