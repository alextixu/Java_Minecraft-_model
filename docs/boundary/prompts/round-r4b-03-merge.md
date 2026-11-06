# R4b 第 3 輪 prompt：把 combo（R3）合進 `feature/r4b-mob-admin`，準備進 main（review-05 F1）

- 日期：2026-11-07 夜間
- 下指令的一方：監控 session（使用者夜間授權）
- 執行者：R4 開發 session（Claude），在 worktree `mod-r4-mobs/`，分支 `feature/r4b-mob-admin`
- 依據：`java_minecraft_model/reviews/review-05-r4b.md` 第 5-2 節、第 7 節 F1
- 結果：merge commit `23246ed`（父 commit：`73c9b19`、`3c4eb42`），單元 505/505、遊戲內 77/77（76＋`always_pass`）

## Prompt 原文（監控 session，排版略整理，內容不變）

> 第 5 次審查（review-05-r4b.md）認為 R4b 的程式沒有要修的地方。接下來把 R4b 合進 main，使用者夜間已授權，直接做就好。
>
> 1. combo 已經合好了，分支頭是 `3c4eb42`（`feature/r3-combo`）。架構師正在把 main 快轉到這個 commit。在 `mod-r4-mobs/` 的 `feature/r4b-mob-admin` 上直接 `git merge 3c4eb42`，不用等 main，兩者是同一個 commit。不要 rebase。
> 2. 照 review-05 第 5-2 節和第 7 節的 F1 處理：
>    - `MobArmoryMod`：拿掉 `ModMobs` 的 import；reload 改成 `List.of(weapons, combos, mobs)`；啟動 log 合成一行，數值數量用 `STATS.catalog().size()`。
>    - `ReloadCommand`：用你的版本，再加 `static Section combos(ComboFiles)`。
>    - `en_us.json`：兩邊的內容都留。
>    - `MobFolder`：改用 `JsonFolder` 的 `files()` 和 `write()`。`ConfigFiles` 在 combo 合併時已經刪掉了。
>    - 每一步寫 SOLID／Pattern 檢查。
> 3. 完成的標準：`gradlew clean build runGameTestServer` 全部通過，預期單元 504、遊戲內 76（75＋1）、0 failure，數字不一樣要說明原因；`git grep -n "ConfigFiles\|content.ModMobs"` 沒有任何結果，Javadoc 也要一起改；`/mobarmory reload` 依序印出武器、combo、怪物三段；28 號報告補一節「合併到 main」。
> 4. README 的怪物段落（F2）交給架構師改，你不用動。
>
> 不要 push，也不要動 main。做完回報分支頭的 commit 和測試數字。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：`git merge 3c4eb42 --no-commit`，解 3 個文字衝突

Prompt：衝突照 review-05 5-2 的表解。

- `MobArmoryMod`：兩邊的 import 都留，只拿掉 `ModMobs`。reload 用 R4b 的 section 清單，多一項 combo。啟動 log 合成一行，怪物數用 `MOB_LIBRARY.registered().size()`。
- `ReloadCommand`：用 R4b 的版本，把 combo 的 `reloadCombos` 改寫成 `static Section combos(ComboFiles)`。訊息鍵照 combo 原本的，失敗回 `-1`。
- `en_us.json`：兩組都留。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | `ReloadCommand` 只管指令；每一種資料怎麼重新載入、印什麼，各自在一個 section |
| O | 沒有，而且證明了 | R3 是 R4b 之後第一個「新的一種資料」。加進重新載入只多一個 `combos(...)` 和接線清單的一項，`reload()` 本身一行沒改。combo 原本的寫法是在建構子多一個參數、`reload()` 多呼叫一次，每多一種資料就要改指令 |
| L | 沒有 | 三個 section 都遵守同一個約定：印自己的訊息，回傳改了幾個，失敗回 `-1` |
| I | 沒有 | `Section` 只有一個方法 |
| D | 沒有 | `ReloadCommand` 只依賴 `Section`，不依賴 `WeaponAdmin`、`ComboFiles`、`MobAdmin`；三個工廠方法各接一個 |
| Pattern | 沿用 | R4b 的 section 清單（小型 Composite）；Command 的形狀不變 |

### 第 2 步：`MobFolder` 改用 `JsonFolder`（git 抓不到的語意衝突）

Prompt：`ConfigFiles` 在 combo 合併時刪掉了，`MobFolder` 還在用，文字上會自動合併，但會編譯失敗。改成和 `StatFolder` 一樣：持有 `new JsonFolder(root.resolve("mobs"))`，讀用 `files()`，寫用 `write(id.value(), json)`。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 列檔案、原子寫檔只在 `JsonFolder` 一處；`MobFolder` 只管「怪物檔在 `mobs/`」 |
| O | 沒有 | 第四種資料夾（武器、數值、combo、怪物）不用改 `JsonFolder` |
| L | 沒有 | `MobStore` 的資料夾版和記憶體版照樣跑同一組契約（`MobFilesTest`）。資料夾不存在時還是回空的（`files()` 的規則），和原本的 `ConfigFiles.list` 一樣 |
| I | 沒有 | `MobStore` 不變 |
| D | 沒有 | `MobEditor`、`MobSetup` 還是只依賴 `MobStore` |
| Pattern | 沿用 | 和 `StatFolder` 同一個做法（委派給 `JsonFolder`） |

另外 `JsonFolder.write` 只收 `[a-z][a-z0-9_]*` 的 id，和 `MobId` 的規則相同，所以任何合法的怪物 id 都寫得進去。

### 第 3 步：`git grep`、Javadoc、文件

Prompt：`git grep -n "ConfigFiles\|content.ModMobs"` 要沒有結果，Javadoc 一起改。

結果：

- `src/` 裡只剩 `DefaultMobs` 的 Javadoc 一處，已改寫。現在 `git grep -n "ConfigFiles\|content.ModMobs" -- src` 沒有結果。
- 我自己寫的、描述現況的兩處（28 第 2 節、23 文末）已更新。
- **全 repo 還有命中，這次沒改**：
  - `README.md` 2 處，是 F2，交給架構師。
  - 歷史紀錄：R4 的 05～07、14；架構師的 09、15、`round-06`～`08`；combo 的 `r3-combo-round-03-merge.md`；我自己的 `round-r4-02`、`round-r4b-02`。這些寫的是「當時怎麼做」，改掉就不是當時的紀錄了。combo 合併時也是同樣的處理（`r3-combo-round-03-merge.md` 第 90 行）。

這一步沒有改程式結構，SOLID 不適用。

### 第 4 步：證明 `/mobarmory reload` 依序印出三段

Prompt：加一個遊戲內測試。用真的指令，把輸出接到一個收集訊息的 `CommandSource`，檢查訊息鍵的順序是武器、combo、怪物。放在 `live_edit` 批次，一個 tick 內做完再還原。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 放在 `MobAdminGameTests`，和其他「改怪物檔、重新載入」的測試放一起 |
| O、L、I、D | 沒有 | 只加測試 |
| Pattern | 沿用 | main 的「批次內一個 tick 做完再還原」 |

### 第 5 步：完整測試

`gradlew clean build runGameTestServer`：**單元 505/505、遊戲內 77/77**（76 個我們的加 `always_pass`），0 failure。

和預期（504／76）不同的原因：

- **單元多 1**：review-05 用「main 375＋combo 74＋R4b 55＝504」估計。但 combo 合進 main 後的實際數字是 450，不是 449（見 20 號開頭）。所以是 450＋55＝505。
- **遊戲內多 1**：就是第 4 步新增的 `reload_command_prints_weapons_combos_mobs`。75＋1＝76 個我們的，加 `always_pass` 是 77。
