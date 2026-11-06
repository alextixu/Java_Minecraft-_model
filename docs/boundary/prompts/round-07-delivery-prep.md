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
