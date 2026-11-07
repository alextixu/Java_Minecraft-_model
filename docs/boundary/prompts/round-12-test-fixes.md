# 第 12 輪 prompt：修 12 個失敗的單元測試，讓 Gradle 認得測試讀的檔，更正 505

- 日期：2026-11-08
- 下指令的一方：主 session（監控・轉達・大綱）
- 授權：使用者回「請架構師修」。選項說明是「另開一個 commit 修這 12 個測試，只動測試，必要時動 build.gradle 讓 Gradle 知道要重跑，並更正 README 和文件裡寫錯的 505；正式的武器、怪物不會變」。
- 執行者：架構師，在 `mod/` 的 main 上工作，起點 `40e2051`。

## 問題

- 第 10 輪的 `27765a1`（review-05 S2）在 `src/gametest/config/mobarmory/mobs/goblin.json` 加了 `"gametest_luck": 0.5`。`gametest_luck` 是 `src/gametest/config/mobarmory/stats/` 裡的數值檔，只有遊戲內測試的伺服器有。
- 有 4 個單元測試類別用 7 個內建數值讀這個檔，所以讀檔失敗：`MobContractTest` 6、`DefaultMobsTest` 3、`ContentResourcesTest` 2、`MobSetupTest` 1，共 12 個。
- 第 10 輪沒發現，因為 `:test` 是 FROM-CACHE。Gradle 不知道這幾個測試會在執行時讀 `src/gametest/config/`，改了那個檔也不會重跑。同樣的情況還有：`ShippedDataTest` 讀 `src/gametest/resources/data/`、`CoreBoundaryTest` 讀 `src/main/java/` 的原始碼、幾個測試讀 `src/main/resources/`。

## 實際的數字（用 `gradlew test --rerun` 量）

| commit | 單元：執行 | 失敗 | 說明 |
|---|---|---|---|
| `3d0f20c`（`27765a1` 的前一個） | 505 | 0 | 第 9 輪、R4b 合併（`23246ed`）、28 號報告寫的 505/505 是對的 |
| `7c4eff0`（第 10 輪結束） | 489 | 12 | 第 10 輪寫的 505/505 是錯的。少 16 個，是因為參數化測試初始化失敗時，每個方法只算 1 個（8 個方法，每個本來是 3 隻怪物） |
| `40e2051`（第 11 輪結束） | 512 | 12 | 多了 23 個網頁測試，失敗的還是那 12 個 |

## 變更邊界（照這次的指令）

| 項目 | 內容 |
|---|---|
| 可以改 | `src/test`、測試會讀到的測試資料、`build.gradle` 裡讓測試認得輸入檔的設定、README 和文件裡寫錯的 505 |
| 不能動 | `src/main`；正式的預設武器、怪物、數值、combo |
| 要保留 | `goblin.json` 的 `gametest_luck`：`27765a1` 要證明「只用 JSON 加的數值，怪物也能用」；遊戲內 78 個繼續全過 |
| 不用動 | PROGRESS.md、morning-checklist.md（總管改） |

## Given / When / Then

| 編號 | Given | When | Then |
|---|---|---|---|
| T1 | 出貨的怪物（brute、skirmisher） | 跑怪物的契約測試 | 用 7 個內建數值檢查，和全新安裝一樣嚴格 |
| T2 | 測試用的 goblin（用了 `gametest_luck`） | 跑怪物的契約測試、讀 goblin 的測試 | 用遊戲內測試伺服器的數值檢查：7 個內建，加上 `src/gametest/config/mobarmory/stats/` 的數值檔，用遊戲同一個 `StatLoader` 讀 |
| T3 | 玩家多放一個用自己數值的怪物檔（`MobSetupTest`） | 啟動時讀怪物 | 認得玩家加的數值，goblin 變成一種怪物 |
| T4 | 改了 `src/gametest/config/` 或其他測試直接讀的資料夾 | 再跑 `gradlew test` | Gradle 不會拿快取，會真的重跑 |
| T5 | — | `gradlew test --rerun` 和遊戲內測試 | 單元全過；遊戲內 78/78 |

## 第 1 步：每個怪物檔配上它所在地方的數值

- **做法**：
  - 新增測試輔助類別 `src/test/java/.../data/GameTestConfig.java`：遊戲內測試伺服器的設定資料夾（`src/gametest/config/mobarmory/`）、goblin 檔的路徑，以及那裡的數值清單。數值清單用正式的 `StatLoader` 讀，和遊戲一樣。放在 `data` 套件、宣告成 public，`content` 和 `data` 的測試都能用。
  - `MobTestCatalog`：goblin 改用 `GameTestConfig.stats()` 讀。`everyMobFile()` 改成回傳 `MobFile(mob, stats)`：出貨的怪物配內建的 7 個數值，goblin 配遊戲內測試伺服器的數值。
  - `MobContractTest`：每個檢查改用 `file.stats()`，不再寫死 `ModStats.CATALOG`。
  - `ContentResourcesTest.allMobs()`：只取 `MobFile.mob()`，檢查內容不變。
  - `MobSetupTest`：第二次讀（多了 goblin）用 `GameTestConfig.stats()`。
- **SOLID**：
  - S：「這個怪物檔在哪裡被讀、那裡有哪些數值」原本散在每個測試裡（都寫死 `ModStats.CATALOG`），現在集中在 `MobTestCatalog.MobFile` 和 `GameTestConfig`。
  - O：以後多一個測試用的怪物檔，只在 `everyMobFile()` 多一行，契約測試本身不用改。
  - L：契約測試對每個 `MobFile` 跑同一組檢查；出貨的怪物仍然用內建 7 個數值，沒有因為 goblin 而放寬。
  - I、D：不適用（只有測試）。
- **Pattern**：契約測試（參數化，每個怪物檔跑同一組檢查）；測試資料配對（怪物和它的環境一起傳）；沿用正式的 `StatLoader`，不另寫一套。
- **變更邊界**：只動 `src/test`。不動 `src/main`、`goblin.json`、出貨的檔案。

## 第 2 步：讓 Gradle 知道測試讀了哪些檔

- **做法**：`build.gradle` 的 `test` task 加四個輸入資料夾：`src/gametest/config`、`src/gametest/resources/data`、`src/main/java`、`src/main/resources`。這些是單元測試在執行時直接讀的資料夾。檔案一改，test 就不是最新的，也不會從快取拿結果。
- **SOLID／Pattern**：不適用（建置設定）。
- **變更邊界**：`build.gradle` 只加 test task 的輸入，不改任何 task 的行為、依賴或版本。

## 第 3 步：更正寫錯的 505

- 只改第 10 輪之後寫的：`README.md` 的測試段落、`docs/boundary/README.md` 的「目前 main」、`prompts/round-10-final-touches.md` 的結果。每處都寫出實際數字，並註明更正。
- 第 9 輪、R4b 合併記錄、28 號報告寫的 505/505，在 `3d0f20c` 用 `--rerun` 重跑過，是對的，不改。
- `7c4eff0` 的 commit 訊息也寫了 505，但 commit 訊息不能改，在這份紀錄說明。

## 結果

### 改了什麼

| 檔案 | 改動 |
|---|---|
| `src/test/.../data/GameTestConfig.java`（新） | 遊戲內測試伺服器的設定資料夾、goblin 的路徑、那裡的數值清單（用正式的 `StatLoader` 讀） |
| `src/test/.../content/MobTestCatalog.java` | goblin 用遊戲內測試伺服器的數值讀；`everyMobFile()` 回傳 `MobFile(mob, stats)` |
| `src/test/.../content/MobContractTest.java` | 6 個檢查都改用 `file.stats()` |
| `src/test/.../content/ContentResourcesTest.java` | `allMobs()` 取 `MobFile.mob()` |
| `src/test/.../data/MobSetupTest.java` | 多了 goblin 的那次讀取，用 `GameTestConfig.stats()` |
| `build.gradle` | test task 多 4 個輸入資料夾 |
| `README.md`、`docs/boundary/README.md`、`prompts/round-10-final-touches.md` | 更正第 10 輪的 505 |

`src/main`、`goblin.json`、出貨的武器、怪物、數值、combo 都沒動。

### Given / When / Then 對照

| 編號 | 證明 | 結果 |
|---|---|---|
| T1 | `MobContractTest` 的 brute、skirmisher 用 `ModStats.CATALOG` 跑 6 個檢查 | 通過 |
| T2 | `MobContractTest` 的 goblin 用遊戲內測試伺服器的數值跑同樣 6 個檢查；`ContentResourcesTest`、`DefaultMobsTest` 不再初始化失敗 | 通過 |
| T3 | `MobSetupTest.firstStartAndAPlayersFile`：goblin 變成一種怪物 | 通過 |
| T4 | 什麼都沒改：`:test UP-TO-DATE`；在 goblin.json 後面加一個空白：`:test` 真的重跑（不是 UP-TO-DATE，也不是 FROM-CACHE）；之後用 git 還原 | 通過 |
| T5 | `gradlew test --rerun`：**528 個，0 失敗**；遊戲內：**78/78** | 通過 |

528 是 512 加上 16：參數化測試不再初始化失敗，8 個方法又各自跑 3 隻怪物。

### SOLID／Pattern 檢查（實際的程式）

- S：「怪物檔在哪裡被讀、那裡有哪些數值」集中在 `MobTestCatalog.MobFile` 和 `GameTestConfig`，契約測試不再自己寫死 `ModStats.CATALOG`。
- O：多一個測試用的怪物檔，只在 `everyMobFile()` 多一行。
- L：每個怪物檔跑同一組契約；出貨的怪物仍然用內建 7 個數值，沒有放寬。
- I、D：不適用。
- Pattern：契約測試（參數化）、測試資料配對（`MobFile`）；數值清單沿用正式的 `StatLoader`，沒有另寫一套。
- 沒有違反，沒有超出範圍。
