# R3 combo 第 3 輪 prompt：合併 main（review-04 X1）

- 日期：2026-11-07（夜間段落 B）
- 下指令的一方：監控 session（主 session），依使用者的夜間授權
- 執行者：連招小弟（傷害顯示 session），worktree `mod-damage-hud`、分支 `feature/r3-combo`
- 依據：`java_minecraft_model/reviews/review-04-overnight.md` 第 3 節、第 5 節 X1
- 使用的 skill：`boundary-first`（每一步做 SOLID 和 Design Pattern 檢查；跑到全過才 commit）

## Prompt 原文（監控 session 的整理）

> 第 4 次審查認為你的 combo 程式沒有要修的地方。接下來要把 combo 合進 main。使用者已在夜間授權，直接做就好。
>
> 1. 在 `mod-damage-hud/` 切到 `feature/r3-combo`，把 main `56423bd` 合進來（`6a3e1fc` 段落 E 加上架構師修的文件）。不要 rebase。
> 2. 照 review-04 第 3 節和第 5 節 X1 處理：
>    - a. `G/MobArmoryGameTests.java`：兩邊的註冊都留。
>    - b. `M/forge/MobArmoryMod.java`：兩邊的 import 都留。啟動 log 併成一行，數值數量用 `STATS.catalog().size()`。
>    - c. `WeaponFolder` 用你分支的版本。`StatFolder` 改用 `JsonFolder` 的 `files()` 和 `write()`，不要用靜態的 `list`，因為資料夾不存在時要回空的。刪掉 `ConfigFiles`。`JsonFolder.write` 的 64 字上限拿掉，或改成和 id 一樣的規則 `[a-z][a-z0-9_]*`。
>    - d. 照第 3-3 節更新文件：README、`B/README.md` 加 R3 一節、20 號開頭和第 8 節、16 號第 9 節 I 列、27 號 S 列改成 `JsonFolder`。
>    - 改到架構師的檔案（`StatFolder`、README、24～27 號）時，在 prompt 紀錄寫 SOLID／Pattern 檢查。
> 3. 完成的標準：`gradlew clean build runGameTestServer` 全過，預期單元 449、遊戲內 68（67＋1）、0 failure。數字不同也沒關係，但要說明為什麼。`git grep -n "ConfigFiles"` 沒有任何結果。README 裡不再出現「combo 進行中」或「還沒進 main」。
>
> 不要 push，不要動 main。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：merge，解 3 個檔案的文字衝突

Prompt：`git merge --no-ff --no-commit 56423bd`。照 review-04 3-1 解衝突：

- `MobArmoryGameTests`：兩行註冊都留。
- `MobArmoryMod`：import 全留；啟動 log 合成一行，數值數量用 `STATS.catalog().size()`。
- `WeaponFolder`：用分支的版本。

結果：衝突和 review-04 試算的一樣，3 個檔案、5 處。自動合併的檔案和 3-1 列的一致。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `MobArmoryMod` 還是只做接線，啟動 log 只是把兩邊要印的合在一起 |
| O | 沒有 | combo 和數值檔兩邊的類別，合併時都沒有改；只有接線和註冊 |
| L | 沒有 | — |
| I | 沒有 | — |
| D | 沒有 | 啟動 log 改讀 `STATS.catalog()`（所有數值，包括玩家加的），不讀 `ModStats.CATALOG`（只有內建的），跟著 main 的抽象走 |
| Pattern | 沿用 | 兩邊各自的 pattern 都不變 |

### 第 2 步：兩份寫檔程式收成一份（架構師的 `StatFolder`）

Prompt：

- 照 review-04 3-2：`StatFolder` 改成持有 `new JsonFolder(root.resolve("stats"))`，讀用 `files()`，寫用 `write(id.value(), json)`。刪掉 main 抽出的 `ConfigFiles`。
- `JsonFolder.write` 的 id 規則改成和 `EquipmentId`、`StatId` 一樣的 `[a-z][a-z0-9_]*`。
- 要注意的行為：資料夾不存在時列出來是空的。`files()` 本來就會這樣；靜態的 `list` 也改成這樣，和原本的 `ConfigFiles.list` 一致，`WeaponFolder.textureNames` 也不會再因為資料夾不存在而丟例外。
- 補一個測試守住這個行為。
- combo id 也改成同一條規則：寫檔規則比讀檔規則嚴，就會有 combo 讀得進來、卻寫不出去的 id。

結果：`StatStoreContractTest`、`WeaponStoreContractTest`、`JsonFolderTest` 全過。`git grep ConfigFiles -- src` 沒有結果。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 原本有，這次修掉 | 合併前，「先寫暫存檔再改名」有兩份，修原子寫入要改兩個地方，等於兩個類別各自帶著同一個改變的理由。現在只剩 `JsonFolder` 一份。`StatFolder` 只管「數值檔放在哪個資料夾、用什麼型別交出去」 |
| O | 沒有 | `StatStore` 介面沒變，`StatLoader`、`StatStartup` 一行沒改。之後再多一種資料夾（例如怪物 JSON），一樣持有一個 `JsonFolder` 就好 |
| L | 沒有 | `StatFolder` 換了內部實作，`StatStoreContractTest` 對資料夾版和記憶體版跑同一組檢查，照樣通過。資料夾不存在時回空的，和原本一樣 |
| I | 沒有 | `StatFolder` 用到的只有 `JsonFolder` 的 `exists`、`files`、`write`、`path` |
| D | 沒有 | `StatLoader` 依賴 `StatStore` 介面，不知道底下是 `JsonFolder` |
| Pattern | 沿用 | **組合**：`WeaponFolder`、`StatFolder`、`ComboFiles` 都持有一個 `JsonFolder`，不繼承。id 規則統一成一條 |

### 第 3 步：跑完整測試

- `gradlew clean build runGameTestServer`：單元 450/450，遊戲內 68/68（我們的 67 個，加原版 `always_pass`），0 failure。
- review-04 預估單元 449。多的 1 個是第 2 步新增的 `JsonFolderTest.aMissingFolderListsNothing`。

### 第 4 步：文件（README、`B/README.md`、16、20、24、27 號）

Prompt：照 review-04 3-3 改。README 另外加一節「加一個 combo」，讓主軸表那一列能連過去，和武器、數值兩列一樣。

| 檔案 | 改了什麼 |
|---|---|
| `README.md` | 主軸表 combo 列「已完成」；「現在有什麼」加 combo；`/mobarmory reload` 包含 combo；網頁的重新載入只載入武器；新增「加一個 combo」一節；新數值那段改寫 reload 的範圍；多人規則加「combo 不用準備」；已知限制刪掉「還沒進 main」、加「combo 不能在網頁改」；測試數字 450、68；索引連到 17～20 |
| `docs/boundary/README.md` | 加 R3 一節（17～20、prompt round-01～03、合併 commit） |
| 16 號第 9 節 | I 列：`CombatObserver` 改成三個方法 |
| 20 號 | 開頭寫合併的 commit 和數字；第 6-1 節註明 main `3789ffb` 已修；第 8 節改成實際的合併紀錄 |
| 24 號 | 第 3 節、第 4 節提到抽出寫檔程式的地方，補「合併 combo 後共用的是 `JsonFolder`」 |
| 27 號 | 結構表和第 7 節 S 列改成 `JsonFolder`；第 3 節的合併說明補「已完成」 |

文件的 SOLID／Pattern 檢查：

- 文件沒有程式結構。這裡檢查的是**文件寫的結構和程式一致**：
  - 27 號 S 列的「寫檔細節的唯一一處」，現在指向真的唯一一份 `JsonFolder`。
  - 16 號 I 列的方法數和 `CombatObserver` 一致（三個 default）。
  - README「加一個 combo」寫的規則，都對應 20 號第 4 節有測試的列：J1～J9、B11、B13、B14、O1。
- **沒改的**：架構師的 prompt 紀錄 `round-08-stat-files.md` 還有 2 處寫到 `ConfigFiles`，那是段落 E 當時做了什麼的歷史紀錄，改掉就不是當時的紀錄了。所以全 repo 的 `git grep ConfigFiles` 剩這 2 行，程式碼（`src/`）裡沒有。
