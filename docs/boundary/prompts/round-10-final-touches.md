# 第 10 輪 prompt：夜間段落 F，收尾（只改文件和測試）

- 日期：2026-11-07 早上
- 下指令的一方：主 session（監控・轉達・大綱）
- 授權：使用者的夜間授權「繼續把所有內容補齊」。指令記在 `overnight-log.md`，早上由使用者驗收。
- 執行者：架構師，在 `mod/` 的 main 上工作，起點 `9979f82`。
- 依據：審查員 review-06（`java_minecraft_model/reviews/review-06-final-main.md`，不在 repo 裡）第 4 節的 S1；review-05 的 S2、S3、S6；review-04 的 S6。

## 指令（節錄）

> 每一項一個 commit：
>
> 1. **review-06 S1**：修掉審查報告提到的死連結（review-06 第 4 節「小地方」）。
> 2. **review-05 S2**：`src/gametest/config/mobarmory/mobs/goblin.json` 的 `stats` 加 `"gametest_luck": 0.5`，`t2_goblin_from_json_only` 多檢查這一項，證明怪物可以用玩家加的數值。
> 3. **review-05 S3**：加一個遊戲內測試，用真的 HTTP PUT 改狂戰士的數值，再到遊戲裡檢查，寫法比照武器的 `web_admin_saves_through_the_game`。
> 4. **review-05 S6、review-04 S6**，都是文件：
>    - 21 號開頭的「這一輪只寫文件」「從 `061e7a9` 開」和表格裡的 `AtomicFiles`，改成實際的做法。
>    - 20 號第 5 節 D 列補上 `ComboListener` 也依賴具體類別 `StatAttributeBridge`。
>
> **不要做**：review-05 S4、S7，review-04 S3～S5（會改程式結構或行為，等使用者決定）；任何會改變遊戲行為的修改。
>
> 做完寫 SOLID／Pattern 檢查，跑 `gradlew clean build runGameTestServer` 全部通過，不要 push。

## 開始前的檢查

- 這四項都只改文件、測試，以及測試用的設定檔（`src/gametest/`），不碰 `src/main`，所以不會改變遊戲行為，也沒有和既有決定衝突。
- 第 2、3 項改的是 R4b（怪物小弟）的測試，第 4 項改的是 21 號（怪物小弟）和 20 號（連招小弟）的文件。這是主 session 的指令，所以由架構師來改：測試照原本的寫法加，文件只改審查員指出的那幾處。

## 第 1 步：死連結（review-06 S1）

- 用 `git ls-files '*.md'` 掃過 repo 裡所有的相對連結，找出指向 repo 外面的連結，結果只有一個：`docs/boundary/14-r4-behavior-report.md:271` 的 `../../../reviews/review-02-d1-r4.md`。
  - 它在本機看得到，因為 `reviews/` 就在 `mod/` 的上一層；但 `reviews/` 不在 repo 裡，所以在 GitHub 上會是死連結。
- **做法**：改成純文字，寫明這份檔案在哪裡、不在 repo 裡，和其他文件提到審查報告的寫法一樣。
- **SOLID／Pattern**：只改文件，不適用。
- **變更邊界**：14 號一行。

## 第 2 步：怪物用玩家加的數值（review-05 S2）

- **做法**：
  - `goblin.json` 的 `stats` 加上 `"gametest_luck": 0.5`。`gametest_luck` 是段落 E 的測試用數值檔，測試伺服器啟動時會一起複製進去。
  - `t2_goblin_from_json_only` 多檢查一項：生出來的 goblin，`gametest_luck` 是 0.5，不是預設值 0.25。
- **SOLID／Pattern**：只改測試資料和測試，不適用。
- **變更邊界**：`src/gametest/config/mobarmory/mobs/goblin.json`、`MobGameTests`（或 goblin 測試所在的檔案）。不碰 `src/main`。

## 第 3 步：網頁改怪物，經過真的 HTTP，在遊戲裡生效（review-05 S3）

- **做法**：
  - 在 `WebAdminGameTests` 加一個測試，形狀和武器的 `web_admin_saves_through_the_game` 一樣：
    1. 開一個真的 `AdminHttpServer`。
    2. 生一隻狂戰士。
    3. 用 `HttpClient` 送 `PUT /api/v1/mobs/brute`，把攻擊力從 4 改成 8。
    4. 等回應，再檢查世界上那隻狂戰士的攻擊力有跟著變。
  - 照段落 A 修過的做法處理時間：請求有 15 秒 timeout，`max_ticks` 放寬；用 `helper.addCleanup` 關伺服器、把 `brute.json` 還原，不管測試成敗都會執行。
- **SOLID／Pattern**：只加測試，不適用。測試沿用既有的 Adapter 邊界：網頁只認得 `MobAdmin`，遊戲裡是 `ServerMobAdmin`。
- **變更邊界**：`WebAdminGameTests`，加一個測試設定檔。不碰 `src/main`。

## 第 4 步：文件（review-05 S6、review-04 S6）

- 21 號：開頭的「這一輪只寫文件」「從 `061e7a9` 開」改成實際的做法；四件事表格裡的 `AtomicFiles` 改成實際用的類別。
- 20 號第 5 節 D 列：補上 `ComboListener` 也依賴具體類別 `StatAttributeBridge`，和 R1 的 `CombatEffectListener` 一樣。
- **SOLID／Pattern**：只改文件，不適用。

## 結果

| 項目 | commit | 驗證 |
|---|---|---|
| S1 死連結 | `3d0f20c` | 再掃一次：repo 的 markdown 沒有連到 repo 外面、也沒有連到不存在的檔案的相對連結 |
| S2 怪物用玩家加的數值 | `27765a1` | `t2_goblin_from_json_only` 檢查 goblin 的 `gametest_luck` 是 0.5，不是預設值 0.25；遊戲內 77/77 |
| S3 網頁經過 HTTP 改怪物 | `ba6c83f` | 新測試 `web_admin_saves_a_mob_through_the_game`：回 200 之後，狂戰士的基礎攻擊是 8、最大生命 50，`brute.json` 也寫進去了；連跑兩次都過，遊戲內 78/78 |
| S6 文件（21、20 號） | `2cbc833` | 21 號剩下的 `AtomicFiles` 都寫成「原本計畫…，實際沿用 `JsonFolder`」 |

最後在 `2cbc833` 跑 `gradlew clean build runGameTestServer`：**單元 505/505**，**遊戲內 78/78**（77 個我們的加原版 `always_pass`），0 失敗。

### SOLID／Pattern 檢查（這一輪）

- `src/main` 一行都沒改，所以 SOLID 的五條和既有的 pattern 都和上一輪（round-09）一樣，沒有新的判斷。
- 新的測試沿用既有的結構：
  - S3 透過 `AdminHttpServer` → `MobApi` → `MobAdmin`（port）→ `ServerMobAdmin`（Adapter），這條路和網頁實際走的一樣，測試本身不另外繞路。
  - S2 只改測試資料，證明了 `MobLoader` 依賴注入進來的 `STATS.catalog()`（D）。在這之前，這點只能看程式知道。
- **沒有做的**（照指令，等使用者決定）：
  - review-05 的 S4（統一讀檔錯誤的類別）、S7（`MobLibrary` 和 `EquipmentLibrary` 共用程式）。
  - review-04 的 S3（數值檔的 `format` 改成可以省略）、S4、S5（刪掉 `StatAttributeBridge` 的舊建構子）。
  - 這些會改程式結構或行為。
