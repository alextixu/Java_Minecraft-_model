# 第 7 輪 prompt：交付準備（夜間段落 A）

- 日期：2026-11-07 清晨
- 下指令的一方：主 session（監控・轉達・大綱）
- 授權：使用者睡前說「繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收」。這則指令**沒有先經過使用者審核**；主 session 已把全文記在 `java_minecraft_model/overnight-log.md`，早上由使用者驗收。
- 執行者：架構師，在 `mod/` 的 main 上工作（起點 `061e7a9`）
- 依據：審查員 review-03（`java_minecraft_model/reviews/review-03-merged-main.md`，不在 repo 裡）第 7 節的 P0-3、P1-6、P2-8、P3-9

## 指令（節錄）

> 1. 根目錄 `README.md`（繁體中文）：
>    - 這是什麼
>    - 安裝
>    - 玩法：`/give` 的 id、生怪蛋、`/mobarmory stats`、網頁網址和聊天欄的連結
>    - 加一把武器：JSON 範例和 PNG 規則
>    - 加一隻怪物：`cbb38b5` 的 3 個檔案
>    - 多人規則：每位玩家都要有同一組 `weapons/*.json`，以及它們用到的上傳 PNG
>    - 已知限制、跑測試的指令
>    - 文件索引：需求 → 邊界 → Given/When/Then → prompt → 行為報告
>    - 素材作者標註
> 2. 網頁的怪物：
>    - 刪掉「要等 R4」的過時文字。
>    - `GET /api/v1/mobs` 改成唯讀，列出 `MOBS.catalog()` 的怪物、數值、裝備、行為。
>    - 「怪物」分頁顯示這份清單。
>    - 不做修改，也不做 POST/PUT。
>    - 加單元測試和 API 測試。
> 3. 多人的兩個小修正（review-03 P2-8）：
>    - 登出或斷線時清掉 `ClientTextures`，從本機檔案重新載入武器定義。
>    - 送 PNG 前先跑 `PngCheck`。
>    - 都要有測試；客戶端畫面的部分寫手動驗證步驟。
> 4. 文件小瑕疵：
>    - 14、16、04 補「SOLID／Pattern 檢查」。
>    - 15 號第 10 節的過時文字。
>    - `app.js` 加 `max_health` 的中文名稱。
>    - `CREDITS.md` 搬到不會被當成資源讀取的位置，`ContentResourcesTest` 一起改，啟動時不能再出現 `Invalid path in pack`。
>
> 限制：
> - 每項各自 commit，不要 push。
> - 不要動 `feature/r3-combo`。
> - `content/ModStats`、`core/` 的公開介面和規則不改。
> - 全部做完跑 `clean build runGameTestServer`。
> - 長期規定照舊：每一步先寫 SOLID、Pattern、變更邊界，有衝突就停下來回報。

## 開始前的衝突檢查

- **錄影**：上一輪說 main 在 `061e7a9` 之後先不要改，留給腳本小弟匯出快照。`overnight-log.md` 寫明錄影要等使用者醒來，所以這一輪改 main 不影響錄影。腳本小弟如果要錄合併時的版本，可以直接用 `061e7a9` 這個 commit。
- **08 號的 API 表**：08 號把 `GET /api/v1/mobs` 寫成「預留給 R4，回 501」。這一輪把它改成唯讀清單，是 R6 原本的需求（「列出伺服器上的武器和怪物」）在 R4 合進 main 之後的補完，不是推翻決定。08 號的表會一起改。
- **O15 和 `CREDITS.md`**：10 號的 O15 寫「7 張貼圖 commit 進 repo，旁邊放作者標註」，這一輪要把標註搬離貼圖資料夾，兩者有一點張力。處理方式見第 4 步。
- **其他**：沒有發現和既有決定或介面衝突的地方。

## 第 1 步：根目錄 README

- **SOLID**：只寫文件，不改程式，不適用。
- **Pattern**：不適用。
- **變更邊界**：
  - 新增 `README.md`。
  - 內容整理自既有的文件：`docs/boundary/README.md`、15 號第 3 節、14 號第 1 節、`cbb38b5`、`content/ModMobs`、預設武器 JSON、`PngCheck`、`WebSettings`。
  - 不改程式。
- 結果：commit `d50f14f`。

## 第 2 步：網頁的怪物（唯讀）

09 號文末已經寫好之後的做法：「照 `WeaponAdmin` 的形狀加 `MobAdmin`，路徑 `/api/v1/mobs` 已預留」。這一步照這個做。

- **SOLID**：
  - S：
    - `MobAdmin` 只回答「有哪些怪物」。
    - JSON 的樣子由新的 `MobJson` 負責。
    - `WeaponApi` 只多一條路由。`WeaponApi` 這個名字現在也管怪物，名字不完全貼切；但改名要動 `AdminHttpServer`、測試和文件，這次不改，記在報告。
  - O：之後要讓網頁改怪物（review-03 的 A6、A7）時，在 `MobAdmin` 加方法、由 `forge/` 實作，`web/` 的其他部分不用動。
  - L：`MobAdmin` 有兩個實作，正式的是 `MobArmoryMod.MOBS::catalog`，測試用的是 `ModMobs::catalog`，回傳同一種不可變的 `MobCatalog`。
  - I：`MobAdmin` 只有一個方法，`WeaponAdmin` 不加任何怪物的方法。
  - D：
    - `web/` 依賴自己的 `MobAdmin` 介面和 `core/mob` 的資料型別，不 import Minecraft，`CoreBoundaryTest` 會檢查。
    - `forge/` 在組裝根把 `MOBS::catalog` 接上。
  - 結論：不違反。
- **Pattern**：
  - Adapter／port：沿用 `WeaponAdmin` 的形狀，web 只認得自己的介面，由組裝根接上實作。
  - 不可變快照：`MobCatalog` 在啟動時建好、之後不變，所以網頁執行緒可以直接讀，不用切到伺服器執行緒。這點和 `WeaponAdmin` 不同。
- **變更邊界**：
  - 新增：`web/MobAdmin`、`web/MobJson`。
  - 修改：
    - `web/WeaponApi`：路由，刪掉「要等 R4」的文字。
    - `web/AdminHttpServer`、`forge/WebAdminLifecycle`：建構時多傳 `MobAdmin`。
    - `forge/MobArmoryMod`：接線一行。
    - 網頁的 `index.html`、`app.js`、`style.css`：「怪物」分頁改成清單，`STAT_LABELS` 加 `max_health`，因為怪物清單會顯示它。這原本是第 4 步的小項，提前到這一步做。
  - 測試：`WeaponApiTest`、`AdminHttpServerTest`、`WebAdminGameTests`，只改建構方式，再加新的測試。
  - 文件：08 號的 API 表、09 號的 B17 和文末、15 號的 B17 列、README。
  - 不動：`core/`、`content/`（`ModMobs` 只是被讀）、R4 的 `forge/` 怪物類別。
  - 沒有超出 08 號第 3 節允許的範圍：`web/`、`forge/` 的 R6 類別、網頁資源。
- **結果**：
  - 單元測試 325/325：原本 322，刪 1 個 501 的測試，加 4 個。遊戲內 58/58。
  - 用一次性的本機預覽（記憶體裡的武器，不寫檔）在瀏覽器打開「怪物」分頁：狂戰士、遊擊兵的外觀、數值（含「最大生命」、百分比）、裝備名稱、行為都正確顯示，console 沒有錯誤。
