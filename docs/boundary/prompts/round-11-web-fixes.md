# 第 11 輪 prompt：修網頁控制台的兩個問題（token 連結、0.0.0.0）

- 日期：2026-11-08
- 下指令的一方：主 session（監控・轉達・大綱）
- 授權：使用者核准「先修 並且寫指令表」。指令表在 `java_minecraft_model/architect-instruction-web-fixes.md`（不在 repo 裡）。
- 執行者：架構師，在 `mod/` 的 main 上工作，起點 `7c4eff0`。
- 做法：boundary-first。邊界和 Given/When/Then 已經寫在指令表，下面照表整理，再寫每一步的 SOLID／Pattern／邊界。

## 問題

1. OP 進世界時，聊天欄的網頁連結用的是 `AdminHttpServer.uri()`，沒有 token。設了 token 之後，照網頁的 401 提示點聊天欄的連結，一樣進不去。
2. web.json 的 host 寫 `0.0.0.0` 時，允許的 Host 只有 `0.0.0.0:埠`（和本機的三個），從區網用 `http://192.168.1.52:8765/` 連會被 403 擋下；log 和聊天欄的網址也是不能點的 `http://0.0.0.0:8765/`。

## 變更邊界（照指令表）

| 項目 | 內容 |
|---|---|
| 可以改 | `web/AdminHttpServer.java`、`web/WebSettings.java`、`forge/WebAdminLifecycle.java`、`app.js`、README 的網頁後台段落、對應的測試 |
| 可以新增 | `web/` 裡的小類別 |
| 不能動 | `core/`、`content/`、`data/`、其他 `forge/` 類別；封包、同步、武器和怪物的行為 |
| 規則 | `web/` 不引用 Minecraft 和 Forge（`CoreBoundaryTest`）；不加依賴；不改版本號 |

這份 prompt 紀錄是長期規則 2 要求的文件（每一步寫 SOLID、Pattern、邊界），不是程式。

## Given / When / Then（照指令表）

| 編號 | Given | When | Then |
|---|---|---|---|
| B1-1 | web.json 有 token，網頁已啟動 | OP 進世界 | 聊天欄的連結打開的是 `http://主機:埠/?token=<URL 編碼的 token>` |
| B1-2 | 沒有 token | OP 進世界 | 連結和以前一樣，沒有 `?token=` |
| B1-3 | 有 token | 任何時候 | 非 OP 收不到連結；log 的「web admin at …」沒有 token；網頁讀到 token 後用 `history.replaceState` 從網址列拿掉，sessionStorage 照存 |
| B1-4 | — | 網頁收到 401 | 提示寫出兩種方法：聊天欄的連結（OP 進世界時收到），或在網址後面加 `?token=…`（token 在伺服器的 web.json） |
| B2-1 | host 是 `0.0.0.0` 或 `::`，有 token | 區網用本機任一網卡的 IP 連進來 | Host 檢查通過；Origin 用同一份清單；帶 token 時網頁正常 |
| B2-2 | host 是 `0.0.0.0` | Host 是別的名稱（例如 `evil.example:8765`） | 一樣 403 `forbidden_host` |
| B2-3 | host 是 `0.0.0.0` | — | `uri()`、log、聊天欄顯示一個實際的 IP：先挑私有網段（site-local）的 IPv4，沒有就第一個非 loopback，都沒有就 127.0.0.1 |
| B2-4 | host 是 127.0.0.1 或指定的 IP | — | 行為和以前完全一樣；「對外開放就要 token」不變 |

## 指令表沒講、由我決定的地方（未經確認，採用建議值）

1. **聊天欄顯示的文字不帶 token，點下去的連結才帶。** Minecraft 客戶端會把聊天訊息的文字寫進玩家的 `latest.log`；只把 token 放在點擊的網址裡，token 就不會出現在任何 log。
2. **網卡位址在網頁伺服器啟動時取一次**（每次伺服器或世界啟動）。之後換了網路，要重開世界才會更新。
3. **只多放行 IP 位址的寫法**（IPv4，或方括號裡的 IPv6），比對時忽略 IPv6 的 zone；不查 DNS。主機名稱只放行原本的 `localhost` 和 web.json 寫的 host。
4. **「0.0.0.0」的判斷**：host 是 IP 位址的寫法，而且是「所有位址」（`0.0.0.0`、`::`）。不查 DNS。
5. **顯示用的 IP 的順序**照 JDK 列出網卡的順序；IPv6 顯示時去掉 zone、加方括號。
6. **測試不真的綁 0.0.0.0**：`AdminHttpServer` 在套件內多一個 `start`，可以給網卡清單和實際綁定的位址。這樣測試不依賴網路環境，也不會在 Windows 跳出防火牆詢問。
7. **app.js 的兩條（B1-3 的 replaceState、B1-4）**：專案沒有 JS 測試工具，也不能加依賴，所以單元測試取回伺服器送出的 `/app.js` 檢查內容；另外用內建瀏覽器實際跑一次。

## 第 1 步：允許的 Host 抽成值物件，網卡位址可以注入（問題 2）

- **做法**：
  - 新增 `web/LocalAddresses`：「這台電腦有哪些 IP」的介面，一個方法。`system()` 用 JDK 的 `NetworkInterface` 列出啟用中的網卡位址；測試用 `of(...)` 給固定清單。
  - 新增 `web/AllowedHosts`：不可變。原本的四個 `host:埠`（127.0.0.1、localhost、[::1]、web.json 的 host）照舊用字串比對；host 是 0.0.0.0／:: 時，再加上這台電腦每個 IP 的 `位址:埠`。`allows(Host 或去掉 http:// 的 Origin)`。
  - `WebSettings` 加 `listensOnAll()`。
  - `AdminHttpServer` 改用 `AllowedHosts`，啟動時算好顯示用的主機（B2-3），`uri()` 用它。
- **SOLID**：
  - S：原本 `AdminHttpServer` 自己組允許清單，又處理請求；抽出 `AllowedHosts` 後，「誰可以連」的規則在一個地方，伺服器只管請求。改善，不違反。
  - O：加 0.0.0.0 的放行，只改 `AllowedHosts` 的建法；`handle` 只是把 `allowedHosts.contains` 換成 `allowedHosts.allows`。
  - L：`LocalAddresses` 的兩個實作守同一個約定：不丟例外，可以回空清單。
  - I：`LocalAddresses` 只有一個方法。
  - D：`AdminHttpServer` 依賴 `LocalAddresses` 介面；正式用 `system()`，測試注入固定清單。
- **Pattern**：值物件（`AllowedHosts`，不可變）、Strategy（`LocalAddresses`）、靜態工廠（`AllowedHosts.of`）。沿用：`AdminHttpServer` 的 Front Controller。
- **變更邊界**：`web/` 新增 2 個類別，改 `AdminHttpServer`、`WebSettings`；`web/` 只用 JDK 的 `java.net`。沒有超出指令表。

## 第 2 步：聊天欄的連結帶 token（問題 1）

- **做法**：
  - `AdminHttpServer` 加 `linkWithToken()`：有 token 時是 `uri()` 加 `?token=<URLEncoder 編碼>`，沒有就等於 `uri()`。`uri()` 永遠不帶 token，log 用的就是它。
  - 新增 `web/AdminGreeting`：玩家進世界時在聊天欄看到什麼。sealed interface，三種：`Link`（顯示的網址、點下去的網址）、`Failed`（沒開的原因）、`Silent`（不說話）。規則：不是 OP 就 `Silent`；網頁在跑就 `Link`；沒開成而且有原因就 `Failed`。
  - `WebAdminLifecycle.greet` 改成把 `AdminGreeting` 轉成聊天訊息。
- **SOLID**：
  - S：「誰看到什麼」的規則從 `WebAdminLifecycle` 移到 `AdminGreeting`；`WebAdminLifecycle` 只管開關網頁、把結果轉成 Minecraft 的訊息（Adapter）。
  - O：以後多一種情況，就多一個 record；`switch` 是窮舉的，漏寫會編譯失敗。
  - L、I：不適用。
  - D：`forge` 依賴 `web`，方向和原本一樣；`web` 不認得 Minecraft。
- **Pattern**：sealed interface 加 record（代數資料型別，取代旗標）；沿用 `WebAdminLifecycle` 的 Adapter 和 Forge 事件的 Observer。
- **變更邊界**：`web/` 新增 1 個類別，改 `AdminHttpServer`、`forge/WebAdminLifecycle.java`。OP 的判斷照舊用 `Commands.hasPermission(LEVEL_GAMEMASTERS)`，沒有改。

## 第 3 步：網頁（問題 1 的 B1-3、B1-4）

- **做法**：`app.js` 的 `readToken()` 讀到網址上的 token 後，存進 sessionStorage，再用 `history.replaceState` 把 `token` 參數從網址列拿掉（其他參數和 `#` 保留）。401 的提示改成寫出兩種方法。
- **SOLID／Pattern**：沒有新類別；token 仍然只在 `readToken()` 一個地方讀。
- **變更邊界**：`app.js`。

## 第 4 步：文件

- README 的網頁後台段落：0.0.0.0 可以從區網連、聊天欄的連結帶 token、網址怎麼加 token。

## 結果

### 改了什麼

| 檔案 | 改動 |
|---|---|
| `web/LocalAddresses.java`（新） | 這台電腦的 IP：`system()` 讀網卡，`of(...)` 給測試 |
| `web/AllowedHosts.java`（新） | 允許的 Host 和 Origin；0.0.0.0 時多放行本機每個 IP 加埠 |
| `web/AdminGreeting.java`（新） | 玩家進世界時在聊天欄看到什麼：`Link`、`Failed`、`Silent` |
| `web/WebSettings.java` | 加 `listensOnAll()` |
| `web/AdminHttpServer.java` | 改用 `AllowedHosts`；啟動時算好顯示的主機（`shownHost`）；加 `linkWithToken()`；套件內多一個可以注入網卡和綁定位址的 `start` |
| `forge/WebAdminLifecycle.java` | `greet` 改成把 `AdminGreeting` 轉成聊天訊息；log 照舊用 `uri()` |
| `app.js` | 讀到網址上的 token 後用 `history.replaceState` 拿掉；401 提示寫兩種方法 |
| `README.md` | 網頁後台段落：token 連結、0.0.0.0、`?token=` |
| 測試 | 新增 `AllowedHostsTest`（7）、`AdminGreetingTest`（4）；`AdminHttpServerTest` 多 6 個 |

### Given / When / Then 對照

| 編號 | 測試 | 結果 |
|---|---|---|
| B1-1 | `AdminGreetingTest.gameMasterGetsTheLinkWithTheToken`、`AdminHttpServerTest.linkCarriesTheToken`（用連結裡解出的 token 打 API 回 200） | 通過 |
| B1-2 | `AdminGreetingTest.noTokenNoQuery`、`AdminHttpServerTest.linkWithoutToken` | 通過 |
| B1-3 | `AdminGreetingTest.othersGetNothing`（非 OP）、`AdminHttpServerTest.linkCarriesTheToken`（`uri()` 不含 token）、`AdminHttpServerTest.pageHandlesTheToken`（replaceState、sessionStorage） | 通過 |
| B1-4 | `AdminHttpServerTest.pageHandlesTheToken`（401 提示有兩種方法） | 通過 |
| B2-1 | `AllowedHostsTest.everyAddressOfThisComputer`、`AdminHttpServerTest.lanAddressWorksWhenListeningOnAll`（Host、Origin、token） | 通過 |
| B2-2 | `AllowedHostsTest.anythingElseIsStillRefused`、`AdminHttpServerTest.otherNamesStillRejectedWhenListeningOnAll` | 通過 |
| B2-3 | `AllowedHostsTest.shownAddress`、`AdminHttpServerTest.shownAddressWhenListeningOnAll` | 通過 |
| B2-4 | `AllowedHostsTest.otherSettingsAreUnchanged`、`shownAddressOfOtherSettings`、`listensOnAll`；原有的 `AdminHttpServerTest` 11 個照過 | 通過 |

另外用內建瀏覽器實際跑過（本機預覽，token 是 `s3cret token&x`）：
- 沒帶 token：顯示新的 401 提示。
- 用 `linkWithToken()` 的網址打開：網址列變成 `http://127.0.0.1:8767/#weapons`（token 拿掉、`#` 保留），sessionStorage 存的是解碼後的 token，武器清單載入。
- 重新整理：照常載入。

### 測試數字（`gradlew clean build runGameTestServer --continue`）

- 遊戲內：**78/78** 通過，和之前一樣。
- 單元：執行 **506** 個，**12 個失敗**。12 個都不是這次的改動造成的：
  - 失敗的是 `ContentResourcesTest`（2）、`DefaultMobsTest`（3）、`MobContractTest`（6）、`MobSetupTest`（1），原因都是 `src/gametest/config/mobarmory/mobs/goblin.json` 的 `gametest_luck`：這幾個單元測試只用 7 個內建數值讀這個檔。
  - 這是第 10 輪 `27765a1`（review-05 S2）造成的。在乾淨的 `7c4eff0` 上跑一樣失敗（content 套件 32 個裡 11 個失敗）。
  - 第 10 輪回報的「單元 505/505」是錯的：那次 `:test` 是 **FROM-CACHE**，Gradle 直接拿了快取的舊結果。這幾個測試在執行時才去讀 `goblin.json`，但 test 這個 task 沒有把它列成輸入，所以改了檔案也不會重跑。
  - 參數化測試初始化失敗時，每個方法只算 1 個，所以總數是 506，不是 505 加上新的 17 個。
- 網頁相關的 5 個測試類別共 52 個全過；`CoreBoundaryTest` 通過。
- 修 `MobTestCatalog` 等測試、補 build 設定、更正 README 和索引裡的 505，都不在這份指令的範圍，留給使用者決定。

### SOLID／Pattern 檢查（實際的程式）

- S：`AllowedHosts` 只管「誰可以連」，`AdminGreeting` 只管「誰看到什麼」，`LocalAddresses` 只管「這台電腦有哪些 IP」；`AdminHttpServer` 照舊處理請求，`WebAdminLifecycle` 照舊開關網頁，再把 `AdminGreeting` 轉成聊天訊息。
- O：`handle` 的檢查流程沒變，只是改問 `AllowedHosts`；聊天訊息多一種情況，就多一個 record，`switch` 會逼著處理。
- L：`LocalAddresses.system()` 和 `of(...)` 都守「不丟例外、可以是空清單」；`AllowedHostsTest.systemAddressesNeverThrow` 檢查 `system()`。
- I：`LocalAddresses` 一個方法；`AdminGreeting` 三個 record 各只帶需要的欄位。
- D：`AdminHttpServer` 依賴 `LocalAddresses` 介面，測試注入固定清單；`forge` 依賴 `web`，`web` 不引用 Minecraft（`CoreBoundaryTest` 通過）。
- Pattern：值物件（`AllowedHosts`）、Strategy（`LocalAddresses`）、sealed interface 加 record（`AdminGreeting`）、靜態工廠（`AllowedHosts.of`、`AdminGreeting.forPlayer`）；沿用 Front Controller（`AdminHttpServer.handle`）、Adapter（`WebAdminLifecycle`）、Observer（Forge 事件）。
- 沒有違反。測試用的 `start(…, LocalAddresses, InetSocketAddress)` 是套件內的，正式程式只走原本的 `start(admin, mobs, settings)`。

### 範圍

- 只改了指令表列的檔案，加上這份 prompt 紀錄（長期規則 2 要求的文件）。
- 沒有加依賴，沒有改版本號，沒有動 `core/`、`content/`、`data/`、其他 `forge/` 類別，封包、同步、武器和怪物的行為都沒變。
- 問題 1、2 的 commit：`e021a9a`。

## 追加：問題 3，區網可以不用 token

指令表在 04:15 追加了這一節（我讀指令表是在那之前，做完問題 1、2 並 commit 後才看到），同一輪接著做。

- **使用者的決定**：總管說明這會推翻 Q6 A，使用者回「先以ip就可以登入 因為日後可能有其他管理方式 所以目前先以ip就可以訪問就好」。所以這不是我自己改 Q6，是使用者改的；10 號的 Q6 補一行。

| 編號 | Given | When | Then |
|---|---|---|---|
| B3-1 | host 不是本機、token 是空的、`"allowNetworkWithoutToken": true` | 網頁啟動、從區網用 IP 開 | 照常啟動，不用 token |
| B3-2 | 開關沒寫或是 false | host 不是本機、沒有 token | 和現在一樣：不啟動 |
| B3-3 | 開關打開、沒有 token、host 不是本機 | 每次網頁啟動 | log 寫一行警告 |
| B3-4 | 開關打開 | Host 或 Origin 是別的名稱 | 一樣 403 |
| B3-5 | 開關打開、有 token | 沒帶 token | 一樣 401：照 token 的規則 |

### 第 5 步：開關（問題 3）

- **做法**：
  - `WebSettings` 多一個欄位 `allowNetworkWithoutToken`，web.json 沒寫就是 false。保留原本 4 個參數的建構子（等於開關關閉），所以既有的呼叫和遊戲內測試都不用改。
  - `checkSafe()`：對外開放、沒有 token、開關也沒打開，才拒絕啟動。
  - `WebSettings.securityWarning()`：開關打開、沒有 token、host 不是本機時，回一句警告；`WebAdminLifecycle.start` 每次啟動把它寫進 log。
  - 預設的 web.json 多寫一行 `"allowNetworkWithoutToken": false`，讓人看得到這個開關。
  - Host、Origin 的檢查和 token 的檢查都不動：token 是空的就不檢查 token（原本的邏輯），有 token 就照舊要 token。
- **SOLID**：
  - S：「這個設定能不能對外開」本來就在 `WebSettings.checkSafe()`，開關和警告也放在這裡，規則只有一個地方；`WebAdminLifecycle` 只負責把警告寫進 log。
  - O：多一個欄位，原本的 4 參數建構子保留，呼叫的地方不用改。
  - L、I、D：不適用；`forge` 依賴 `web` 的方向不變。
- **Pattern**：值物件（`WebSettings` record，不可變）；明確打開的開關（預設關）。
- **變更邊界**：`web/WebSettings.java`、`web/AdminHttpServer.java`（只改說明）、`forge/WebAdminLifecycle.java`、README、10 號的 Q6 一行、測試。

### 問題 3 的結果

| 編號 | 測試（`AdminHttpServerTest`） | 結果 |
|---|---|---|
| B3-1 | `lanWithoutTokenWhenAllowed`（不帶 token 讀和存都 200）、`switchInTheFile` | 通過 |
| B3-2 | `switchOffStillRefusesToStart`、`switchInTheFile`（沒寫就是關）；原有的 `openAddressNeedsAToken` 照過 | 通過 |
| B3-3 | `warningOnlyWhenOpenWithoutToken` | 通過 |
| B3-4 | `hostAndOriginStillCheckedWithoutToken` | 通過 |
| B3-5 | `tokenStillWinsWhenSet` | 通過 |

- **SOLID／Pattern 檢查（實際的程式）**：開關和警告都在 `WebSettings`，`checkSafe()` 和 `securityWarning()` 用同一組條件；`WebAdminLifecycle` 只多一行把警告寫進 log；`AllowedHosts`、`AdminGreeting`、token 的檢查都沒動。4 個參數的建構子保留，既有呼叫（含遊戲內測試）不用改。沒有違反。
- 測試數字見 commit 訊息和回報。
