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
  - commit `012a2de`。

## 第 3 步：多人的兩個小修正（review-03 P2-8）

### 做法

1. **登出或斷線時還原**：
   - 新增 `data/ClientWeaponState`，記住客戶端的 library 現在放的是不是伺服器的定義。
   - 收到伺服器的定義時，經由 `applyFromServer`，再用既有的 `WeaponSync.apply` 套用。
   - 登出時，如果剛才用的是伺服器的定義，就用同一個 `WeaponLoader` 從本機的 `config/mobarmory/weapons/` 重新讀一次，放回 library。
   - 單人遊戲從來不套用伺服器的定義，所以登出時什麼都不做。
   - 同時清掉 `ClientTextures`。
   - 監聽 Forge 的 `ClientPlayerNetworkEvent.LoggingOut`。這是客戶端才有的類別，所以放在新的 `forge/client/WeaponClientEvents`，只在客戶端註冊，和 R4 的 `MobArmoryClient` 一樣。
2. **送 PNG 前跑 `PngCheck`**：
   - `WeaponSync.uploadedTexturesInUse` 從磁碟讀到圖之後，先跑 `PngCheck`。不合格的不送、也不列進摘要，並回報給呼叫的一方，由 `forge/` 寫一行警告。
   - `sendToAll` 改成每次修改只讀一次磁碟，所有玩家共用，警告也只寫一次。

### 考慮過、沒採用的做法

連線前先把本機的定義存一份，登出時放回去。沒採用，因為 08 號規定設定檔是唯一的來源，review-03 也建議從檔案重新載入。

### 檢查

- **SOLID**：
  - S：
    - `ClientWeaponState` 只管「客戶端現在用的是誰的定義、怎麼回到本機的」。
    - `WeaponSyncChannel` 仍然只是封包的轉接器。
    - `WeaponClientEvents` 只接一個客戶端事件。
    - PNG 的規則仍然只在 `PngCheck` 一處。
  - O：沒有新增型別判斷。
  - L：沒有新的介面實作；`WeaponStore` 的兩個實作照舊。
  - I：沒有改介面。
  - D：
    - `ClientWeaponState` 依賴 `WeaponStore` 介面和 `WeaponLoader`，是純 Java，可以直接做單元測試。
    - `forge/` 只負責接線。
  - 結論：不違反。
- **Pattern**：
  - Observer：Forge 的事件匯流排，`LoggingOut` 監聽器。
  - Adapter：`WeaponClientEvents` 把 Forge 事件轉成 `WeaponSyncChannel.leftServer()`。
  - 不可變快照：沿用 `EquipmentLibrary.replaceAll`，整份換掉。
- **變更邊界**：
  - 新增：`data/ClientWeaponState`、`forge/client/WeaponClientEvents`。
  - 修改：`data/WeaponSync`、`forge/WeaponSyncChannel`、`forge/client/ClientTextures`（加 `clear()`）、`forge/MobArmoryMod`（建構參數、客戶端註冊各一行）。
  - 測試：`ClientWeaponStateTest`、`WeaponSyncTest`、`ClientTexturesTest`，以及遊戲內一個新測試，放在會改重劍的 `live_edit` 批次。
  - 文件：15 號第 3 節的手動驗證步驟、README 的已知限制。
  - 不動：`core/`、`content/`、R4 和 D1 的類別、封包格式（協定版本維持 2）。
  - 在 08 號允許的 `data/`、`forge/`、`forge/client/` 範圍內。

### 結果

- 單元測試 332/332：325 加上 `ClientWeaponStateTest` 5 個、`WeaponSyncTest` 1 個、`ClientTexturesTest` 1 個。
- 遊戲內 59/59：多了 `left_server_restores_local_weapons`。log 有「left the server; weapons are read from this game's files again (changed: [heavy_sword])」。
- `LoggingOut` 事件和畫面上的結果沒有自動測試，手動步驟寫在 15 號第 3 節。
- commit `6c01cfd`。

## 第 4 步：文件和小瑕疵（review-03 P3-9）

### 要做的事

1. 04、14、16 號報告各補一節「SOLID／Pattern 檢查」，逐條對照現在 main 上的程式。
   - 14、16 是其他 session 寫的報告。這次由架構師補，是因為主 session 的指令這樣交代；原本的內容一個字都不改，只在文末加一節。
2. 15 號：
   - 第 10 節：過時的「D1 還沒合併時……」。
   - 第 11 節：review-03 指出兩處不精確。
     - 加第三種外觀時，漏算了 `EquipmentLook` 本身和網頁的外觀選單。
     - 加一種數值時，網頁要顯示中文名稱，`app.js` 也要加一行。
   - 新增第 12 節，記錄這一輪的追加和它們的 SOLID／Pattern 檢查。
3. `app.js` 的 `max_health` 中文名稱：已在第 2 步做了（`012a2de`），這一步不再改。
4. `CREDITS.md`：
   - 從 `assets/mobarmory/textures/item/` 搬到 `src/main/resources/mobarmory/CREDITS.md`。這個位置在 jar 裡，但不在 `assets/`、`data/` 底下，不會被當成資源讀取。
   - 大寫的檔名在資源包裡是不合法的路徑，所以每次啟動 log 都有 `Invalid path in pack`。
   - `ContentResourcesTest` 改讀新位置，另外加一個契約測試：`assets/` 底下每個檔案的路徑都是 Minecraft 允許的資源路徑（小寫英文、數字和 `_-./`），以後放錯檔案會在單元測試就被擋下。
   - **和 O15 的張力**：O15 寫的是「旁邊放作者標註」。搬家之後，標註仍然在同一個 repo、同一個 jar，README 也有素材來源一節，作者標註的目的沒有變，只是不再和 PNG 放在同一個資料夾。所以照指令搬，並在 10 號記一行。如果使用者要求標註一定要緊鄰貼圖，可以改成小寫的 `credits.md` 放回原處，同樣不會報錯。

### 檢查

- **SOLID**：除了 `ContentResourcesTest`，這一步只改文件和資源的位置，不改程式，不適用。
- **Pattern**：新的資源路徑測試是契約測試：`assets/` 底下的每一個檔案，都跑同一條規則。
- **變更邊界**：
  - 文件：04、14、15、16 號（14、16 只在文末加一節）、10 號（O15 加一行）、08 號（檔案位置）、README。
  - 資源：`git mv` 搬 `CREDITS.md`。
  - 測試：`ContentResourcesTest`。
  - 不改 `core/`、`content/` 的程式。

### 結果

- **第一次全跑**：clean build 時遊戲內有 3 個失敗。
  - 根本原因是 `web_admin_saves_through_the_game` 原本就有的時間問題：測試伺服器 200 tick 大約只要 0.7 秒，網頁存檔要等真實的時間。
  - 它失敗時沒有還原重劍，所以後面兩個怪物測試讀到攻擊 +16，也跟著失敗。
- **修正**（不在原本的範圍，但會讓「全部通過」不可靠，所以在這一步處理）：
  - 請求加上 15 秒的 timeout。
  - `max_ticks` 改成 6000。
  - 收尾改用 `helper.addCleanup`，通過或失敗都會還原。
  - 這是 R6 的測試程式碼，屬於架構師負責的範圍，沒有動到正式程式。
- **修正後**：連跑兩次 `clean build runGameTestServer` 都全過，單元 333/333、遊戲內 59/59。
- **`Invalid path in pack`**：
  - 搬家前的舊 log 有「Invalid path in pack: mobarmory:textures/item/CREDITS.md」。
  - 搬家後開一次 `runClient`，資源載入完成，log 沒有這一行。只看了 log，沒有對遊戲做任何操作，看完就關掉。
- **另外看到**：有一個 04:23 開的 Minecraft 視窗，是別的 session 從 `demo/builds/main-061e7a9` 開的快照，和 `mod/` 是不同的資料夾，沒有去動它。
