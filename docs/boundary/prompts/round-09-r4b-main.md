# 第 9 輪 prompt：R4b 合進 main，README 改成「怪物也是 JSON」（F2、F3）

- 日期：2026-11-07 清晨
- 下指令的一方：主 session（監控・轉達・大綱）。依使用者的夜間授權直接做，全文記在 `overnight-log.md`，早上由使用者驗收。
- 執行者：架構師，在 `mod/` 的 main 上工作。起點 `3c4eb42`（combo 已合進 main）。
- 依據：審查員 review-05（`java_minecraft_model/reviews/review-05-r4b.md`，不在 repo 裡）第 6 節、第 7 節的 F2、F3、第 8 節的 S5。

## 指令（節錄）

> 1. 在 `mod/` 執行 `git merge --ff-only feature/r4b-mob-admin`，不能快轉就停下來回報。
> 2. 照 review-05 的 F2 改 README：
>    - 怪物改成「已完成」。
>    - `/mobarmory reload` 依序重新載入武器、combo、怪物。
>    - 網頁的怪物分頁可以改、可以新增。
>    - 「加一隻怪物」改成「1 個 JSON」，附 `brute.json` 範例和欄位表（body、scale、stats、equipment、behaviors 的四種）。
>    - 多人規則加第 4 條。
>    - 刪掉「網頁上的怪物只能看」。
>    - 更新測試數字。
>    - 索引加 R4b（21～23、28、round-r4b 的 prompt）。
>    - `B/README.md` 加一節 R4b。
> 3. F3：確認網頁的「從檔案重新載入」送的是 `{}`；如果還是回 415 就修掉。
> 4. 在 main 上跑完整的 `gradlew clean build runGameTestServer`。
> 5. 寫 SOLID／Pattern 檢查，commit，不要 push。

## 第 1 步：main 快轉到 `feature/r4b-mob-admin`

`git merge --ff-only feature/r4b-mob-admin`：`3c4eb42` → `ce7beaa`（R4b 的合併 commit 是 `23246ed`）。

- **變更邊界**：架構師負責的範圍裡，R4b 改到的地方。
  - `content/`：
    - `ModMobs` 整個刪掉，怪物改成 jar 裡的 `mobarmory/default_mobs/*.json` 和 `config/mobarmory/mobs/`。
    - `ModStats` 只改一行說明。
  - `forge/MobArmoryMod`：
    - 多了 `MOB_FILES`、`MOB_LIBRARY`、`MOB_SYNC`、`MOB_ADMIN`。
    - 怪物用 **`STATS.catalog()`** 驗證數值，有照 review-04 的提醒。
    - reload 改成 `List.of(weapons, combos, mobs)`。
    - 執行時資源包改收一組固定檔案：數值名稱的語系檔，加上怪物的生怪蛋模型。
  - `forge/ServerWeaponAdmin`：「切到伺服器執行緒並等結果」抽到 `ServerThread`，和 `ServerMobAdmin` 共用，武器的行為不變。
  - `forge/client/GeneratedWeaponPack`：`register` 的第三個參數從一個語系檔改成一組固定檔案。
  - `web/`：
    - `MobAdmin` 從唯讀的一個方法，擴充成 stats、view、update、create、reload。
    - 怪物的路由從 `WeaponApi` 搬到新的 `MobApi`，`AdminHttpServer` 依路徑分給兩個 API。
  - `core/mob`：
    - `Behavior` 多了兩個 default 方法和速度上限。
    - 新增 `LeapBehavior`、`NeutralBehavior`、`SpeedBehavior`、`MobLibrary`、`MobReloadReport`。
    - 沒有 import Minecraft。
  - `core/stat`、`core/combat`、`core/combo`、`core/equipment`：沒有動。
- **SOLID**：
  - S：
    - `WeaponApi` 回到只管武器。第 7 輪記的「`WeaponApi` 也管怪物，名字不貼切」因此解決。
    - 切執行緒的細節只在 `ServerThread` 一處。
  - O：
    - 加一隻怪物：一個 JSON 檔，0 行 Java。
    - 加一種行為：寫 Java，共 5 個檔案，28 號第 6 節實測過。
  - L：
    - `MobAdmin` 有兩個實作：`ServerMobAdmin`（正式）和 `TestMobAdmin`（測試）。
    - `MobStore` 有兩個實作：資料夾版和記憶體版。
  - I：`MobAdmin` 現在 5 個方法，只有網頁一個使用者，每個方法都用到，和 `WeaponAdmin` 同一個形狀。這是第 7 輪就預告的擴充方式（「之後要讓網頁改怪物，在 `MobAdmin` 加方法」）。
  - D：
    - `web/` 只認得 `MobAdmin`。
    - `core/mob` 是純 Java。
    - 怪物的數值清單從組裝根傳進來（`STATS.catalog()`），不是寫死的 `ModStats.CATALOG`。
  - 結論：不違反。
- **Pattern**：
  - 沿用武器的整套做法：不可變快照（`MobLibrary`，和 `EquipmentLibrary` 同形狀）、Strategy（`MobStore`、`Behavior`＋`GoalBinder`）、Adapter（`ServerMobAdmin`、`MobSyncChannel`）、port（`MobAdmin`）。
  - Factory：`MobEntityFactory`，沿用。
- **已知、留到交付後**：
  - `MobLibrary` 和 `EquipmentLibrary` 有約 170 行平行的程式（review-05 S7）。
  - 讀檔錯誤有三種類別（review-05 S4）。
- **結論**：沒有衝突。

## 第 2 步：README 和索引（F2、S5）

- **SOLID**：只改文件，不適用。
- **Pattern**：不適用。
- **變更邊界**：
  - `README.md`：主軸表、現在有什麼、怪物表、指令表、網頁、加一隻怪物、數值段落裡寫到怪物的地方、多人規則第 4 條、已知限制、跑測試、文件索引。
  - `docs/boundary/README.md`：加一節 R4b，更新文件編號表。
  - 本檔。
  - 不改程式。

## 第 3 步：F3，網頁的「從檔案重新載入」

- 合進來的 `app.js` 送的已經是 `{}`：`api('POST', '/api/v1/reload', {})` 和 `api('POST', '/api/v1/mobs/reload', {})`，R4b 修好了。
- 用一次性的本機預覽（記憶體裡的武器和怪物，不寫檔）在瀏覽器實際按一次，確認回 200、畫面顯示結果。在真的遊戲裡按，留到早上的手動驗收。
- **SOLID／Pattern**：不改程式，不適用。
- **結果**：
  - 按下按鈕後，`POST /api/v1/reload` 回 200，`POST /api/v1/mobs/reload` 也回 200。
  - 畫面顯示「已從檔案重新載入，沒有變動。」，console 沒有錯誤。
  - 不用改程式。

## 第 4 步：完整測試

在 main `ce7beaa` 上跑 `gradlew clean build runGameTestServer`：

- **單元 505/505**，0 失敗、0 略過。
- **遊戲內 77/77**：我們的 76 個，加上原版的 `always_pass`，0 失敗。

第 2 步只改文件，所以 commit 之後的程式和這次測試的完全一樣。
