# R3 combo 第 2 輪 prompt：實作（夜間段落 B）

- 日期：2026-11-07（夜間）
- 下指令的一方：監控 session（主 session）
- 執行者：傷害顯示 session（Claude），worktree `mod-damage-hud`、分支 `feature/r3-combo`
- 使用的 skill：`boundary-first`（第 4～6 步：分步實作、三層測試加契約測試、行為報告）

## 使用者的授權和決定

- 使用者睡前的原話：「繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收」。
- 19 號問題使用者沒有回答。主 session 依上面的授權**採用建議值**：Q1～Q7 全選 A、O1～O12 照建議，待使用者確認。
- 同一晚第二則訊息調整了 Q2。使用者定下主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。主 session 依此把 Q2 從「Java 資料」改成「JSON 設定檔」。兩則都記在 [19 的決定紀錄](../19-r3-combo-open-questions.md#決定紀錄)。

## Prompt 原文（監控 session 的整理）

第一則：

> 【夜間段落 B：R3 combo 實作】
>
> 1. 在你的 worktree 切到 `feature/r3-combo`（目前 `74bd43b`），把 main 的 `061e7a9` 合進來。`061e7a9` 已經包含 R2b、R6、R4、D1。解完衝突跑一次測試，確認基準：單元 322、遊戲內 57＋1。
> 2. 依 17～19 和決定好的答案，照 boundary-first 分步實作。每一步都寫 SOLID／Pattern 檢查，跑測試全過才 commit：combo 定義是資料（`content/ModCombos`）、效果用 Strategy（`ComboEffect`）、出招狀態用純 Java 的 `ComboTracker`；用移動速度 ≥ 0.12 判斷是不是奔跑揮擊；兩種 combo：Lunge（走→跑）×1.5、Follow-up（跑→走）×1.3；combo 名稱接在 D1 action bar 那一行的後面。
> 3. 加單元測試、契約測試，以及 `src/gametest` 的系統測試。系統測試要證明：走→跑、跑→走依序揮擊，會觸發不同的 combo，傷害倍率也不同；超過時間窗就不觸發；拿重劍時移動速度變慢，比較難觸發奔跑揮擊。
> 4. `gradlew clean build runGameTestServer` 全部通過以後，寫行為報告 `20-r3-combo-behavior-report.md`，要包含「SOLID／Pattern 檢查」一節。
>
> 限制：不要 push，不要動 main，合併由主 session 安排。

第二則（我寫完 `core/combo` 和 Java 版 `ModCombos` 時收到）：

> combo 的方向要調整一處。……
> 1. combo 的定義改成 JSON，放在 `config/mobarmory/combos/<id>.json`。第一次啟動時自動建立 Lunge、Follow-up 兩個預設檔。玩家多放一個 JSON 就能多一種 combo，不用改 Java。讀檔、驗證、預設檔的寫法，請沿用 main 上武器那一套（`data/` 的 `WeaponLoader`、`WeaponFolder`、`DefaultWeapons` 等），不要另外寫一套。
> 2. 效果還是 Strategy，例如「傷害倍率」。JSON 只能從已經寫好的效果種類裡挑，填參數，例如 `{"effect": "damage_multiplier", "value": 1.5}`。新的效果種類才需要寫 Java，這樣符合開放封閉原則。
> 3. 出招順序（走、跑）、時間窗、顯示名稱，也都寫在 JSON 裡。
> 4. 能不能在執行中用 `/mobarmory reload` 換掉 combo 定義、要不要在網頁上編輯，照最小範圍：這次做 reload，網頁先不做，寫進報告的「之後」。
> 5. 這是主 session 依使用者主軸做的調整，請記到 19 的決定紀錄：Q2 從「Java 資料」改成「JSON 設定檔」，附上使用者原話。
>
> 測試請加上「多放一個 JSON 就多一種 combo」這一條，這就是可擴充的證明。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：合併 main `061e7a9`（commit `e1aa313`）

Prompt：`git switch feature/r3-combo`，`git merge --no-ff 061e7a9`。這個分支只有 17～19 和 prompt 紀錄，預期沒有衝突。合完跑 `clean build runGameTestServer` 確認基準。

結果：沒有衝突。基準是單元 322、遊戲內 57（伺服器報告 58，含 `always_pass`），和主 session 給的數字一樣。合併沒有改程式，不需要 SOLID 檢查。

### 第 2 步：`core/combo`、combo 檔讀取、單元測試和契約測試

Prompt：

- 先寫純 Java 的規則：`ComboInput`、`InputClassifier`、`ComboEffect`＋`DamageMultiplier`、`ComboDefinition`、`ComboCatalog`、`ComboTracker`。
- 收到第二則訊息後，combo 定義改成 JSON。
  - `ComboDefinition` 多了名稱和時間窗，每個 combo 有自己的時間窗。`ComboTracker` 改成每一下都傳入目前的清單，讓 reload 能在下一下就生效。新增 `ComboLibrary`，用整份換掉的方式保存目前的清單，和 R2b 的 `EquipmentLibrary` 同一個做法。
  - 讀檔沿用武器那一套。真的通用的部分抽出來共用，武器也改用同一份；格式不同的部分才新寫。
- `content/ModCombos` 只留「什麼算一下」的共用規則：奔跑門檻 0.12、蓄滿 0.9。這兩個不屬於任何一個 combo。

沿用和新寫的部分：

| 武器那一套 | combo 怎麼用 |
|---|---|
| `WeaponJson.parse`：嚴格 JSON、BOM、行列號 | 直接呼叫 |
| `WeaponJson.string`、`number`：欄位缺漏和型別錯誤的訊息 | 從 private 改成 package-private，直接呼叫 |
| `WeaponFormatException`（欄位路徑＋原因）、`WeaponLoadException`（所有問題一次列出） | 直接用。`WeaponLoadException` 加一個建構子，訊息可以寫「combo files」，原本的建構子不變 |
| `WeaponFolder` 的列檔和原子寫入（private） | **抽成 `JsonFolder`**，武器和 combo 都用它。`WeaponFolder` 的行為不變，`WeaponStoreContractTest` 照樣通過 |
| `DefaultWeapons.text`（讀 jar 裡的檔） | **抽成 `ShippedFiles`**，`DefaultWeapons` 和 `DefaultCombos` 都用它 |
| `DefaultWeapons.installIfMissing`（資料夾不存在才裝） | `DefaultCombos.installIfMissing` 照同一條規則寫，用的是 `JsonFolder` |
| `WeaponLoader`（每個檔讀一次、收集問題、檔名就是 id） | `ComboLoader` 照同樣的結構寫。格式不同，不能共用 |

測試：

- 新增 `InputClassifierTest`、`ComboTrackerTest`、`ComboCatalogTest`、`ComboEffectContractTest`、`ComboLoaderTest`、`JsonFolderTest`，共 67 個。
- 包含「多放一個 JSON 就多一種 combo」：`ComboLoaderTest.oneMoreFileIsOneMoreCombo`。
- 結果：單元 389/389，遊戲內 57/57（這一步沒有動 `forge/`，跑遊戲內測試是為了確認 `WeaponFolder` 改完沒壞）。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 每個類別只有一個改變的理由：`ComboTracker` 管出招狀態；`InputClassifier` 管走或跑；`ComboCatalog` 管清單合不合法；`ComboJson` 管檔案格式；`ComboLoader` 管收集每個檔的問題；`JsonFolder` 管檔案 I/O；`ComboEffectTypes` 管「效果名稱對應到哪個類別」 |
| O | 沒有 | 新 combo 是一個新檔，不改程式。新效果種類是 `core/combo` 一個新類別，加上 `ComboEffectTypes.STANDARD` 一行；契約測試會提醒補一個樣本。新輸入種類是 `ComboInput` 加一個值、`InputClassifier` 加規則。R2b 的類別只為了共用而改，行為不變 |
| L | 沒有 | `ComboEffectContractTest` 讓每一種效果和每個內附 combo 的效果都跑同一組檢查：任何傷害都不丟例外、不變負數、不變 NaN，一般傷害結果是正數、不會遞減。`WeaponFolder` 改用 `JsonFolder` 後，`WeaponStoreContractTest` 照樣通過 |
| I | 沒有 | `ComboEffect` 一個方法；`ComboEffectTypes.Reader` 一個方法；`InputClassifier` 只拿 R1 的 `StatReader`，不拿實體 |
| D | 沒有 | `core/combo` 是純 Java，不知道 JSON 和檔案（`CoreBoundaryTest` 守著）。`data/` 依賴 `core`，`core` 不依賴 `data`。`ComboTracker` 每一下拿到的是 `ComboCatalog`，不知道它從哪裡讀來 |
| Pattern | 新用和沿用 | **Strategy**：`ComboEffect`。**用名稱查的 Factory**：`ComboEffectTypes`，檔案寫 `type`，它找對應的 `Reader` 建出效果。**狀態機**：`ComboTracker`。**快照**：`ComboLibrary`，和 R2b 的 `EquipmentLibrary` 一樣整份換掉。沿用 R2b 的讀檔規則：嚴格解析、欄位路徑、一次列出所有問題、第一次啟動才裝預設檔 |

**命名的債**：`WeaponFormatException`、`WeaponLoadException` 現在也用在 combo 檔，名稱是 R2b 時取的。改名會動到架構師的網頁、`WeaponEditor` 和測試，今晚架構師也在 main 上改東西，所以這次不改名，記在報告 20 的「之後」。
