# 邊界說明：R2b 武器改成資料驅動＋R6 網頁後台

> 狀態：**已確認**（2026-11-06，使用者「全部照建議」）。決定紀錄在 [10-weapon-data-web-open-questions.md](10-weapon-data-web-open-questions.md)。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [09-weapon-data-web-given-when-then.md](09-weapon-data-web-given-when-then.md)。
> 前提：沿用 R1／R2 已確認的架構（`core/`、`content/`、`forge/` 分層，數值登記表、`LoadoutCalculator`、R2 Q6「同種裝備共用一份定義」）。R4 暫停，這裡不動怪物。

## 1. 需求翻譯

需求來源：使用者 2026-11-06 的指示（「都做」），由監控 session 轉達。

- **A. R2b**：武器的定義（id、欄位、數值修飾、外觀）改從 JSON 設定檔讀。加武器、改數值都不用改 Java；支援自訂貼圖（PNG）；現有 4 件裝備搬成 JSON，行為不變。
- **B. R6**：伺服器端開 API，網頁可以列出伺服器上的武器和目前的數值，也可以修改數值和外觀。改了以後，玩家手上那把照 R2 Q6 生效。怪物先在 API 留位置。也要設計「上傳新武器（貼圖＋數值）」。
- **示範影片**：在網頁後台改重劍的攻擊力，回遊戲砍殭屍，傷害跟著變。傷害數字由 `damage-hud`（D1）顯示。

### 真正的需求

- **武器的「定義」和「物品種類」是兩件事。**
  - 物品種類（`mobarmory:heavy_sword` 這個 id）只能在啟動時註冊。這是平台限制，和 R1 的數值種類一樣。
  - 定義（名稱、數值、外觀）可以在執行中整份換掉。
  - 所以「加一把新武器」要重開遊戲，「改一把既有武器」不用。
- **設定檔是唯一的來源。** 遊戲、`/mobarmory reload` 指令、網頁讀寫的都是同一份資料，驗證規則也只有一套。網頁改了以後寫回檔案，重開遊戲後還在（[10](10-weapon-data-web-open-questions.md) 的 O2）。
- **改了要立刻作用在所有同種武器上。** R2 已經做到「同種裝備共用一份定義、tooltip 每次讀定義」。現在多了一個動作：換定義之後，主動重算正拿著這種武器的玩家和生物。
- **網頁只是另一個「改定義的入口」。** 網頁不直接碰 Minecraft 的實體和執行緒，只呼叫一個介面，由 `forge/` 的轉接器切到伺服器執行緒去做。所以網頁的程式可以不開遊戲就測試。
- **測試不能被使用者改過的設定影響。** 使用者在網頁把重劍改成 +10 以後，T1 和既有測試仍要用預設值（+6）跑。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [10](10-weapon-data-web-open-questions.md)：

1. 設定檔放哪：遊戲資料夾的 `config/mobarmory/`、datapack，還是模組 jar 裡？
2. JSON 長什麼樣子：一把武器一個檔，還是全部放一個檔？百分比寫 `-30` 還是 `-0.30`？
3. 改了檔案怎麼生效：指令、網頁按鈕，還是自動偵測？
4. 自訂貼圖怎麼進遊戲：放進模組 jar（要重新 build），還是執行時產生資源包？
5. 網頁用什麼做、綁哪個位址和埠、要不要密碼或 token？
6. 網頁改的數值要不要寫回 JSON？
7. 數值輸入錯了怎麼辦：拒絕，還是自動夾到範圍內？
8. 上傳新武器這次要不要做？要不要重開遊戲？
9. 設定檔寫錯（語法錯、引用不存在的數值）時，遊戲要不要照樣啟動？
10. 武器檔被刪掉，世界裡那把武器怎麼辦？
11. 兩個分頁同時改同一把武器時，誰的算數？

### 這次要做／未來可能變

| 這次要做 | 未來會變，所以要在這裡切邊界 |
|---|---|
| 4 件裝備搬成 JSON，加上 hanhanpx 貼圖的測試武器 | **武器會一直加**（貼圖包有 60 張）→ 一把一個檔，加武器不改程式 |
| 網頁改武器的數值、名稱、外觀 | **怪物也要能改**（R4 恢復後）→ API 路徑和「改定義的入口」介面先留好，怪物再加一個實作 |
| 只綁本機，單人遊戲示範 | **多人連線**（R5）→ 遠端客戶端的 tooltip 和貼圖要同步，這次標成已知限制 |
| 自訂貼圖只用在物品圖示和手持 | **盔甲穿在身上的自訂外觀**→ 這次只能選原版材質 |
| 設定檔格式第 1 版 | **格式會改**→ 每個檔案寫 `"format": 1`，之後可以自動升級舊檔 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下在 Forge 26.3-66.1.0 和 Minecraft 26.3 的原始碼裡確認過：

- **物品註冊表在啟動後凍結**，新的物品種類只能在模組載入時加入。模組建構時就讀得到設定資料夾（`FMLPaths.CONFIGDIR`），比物品註冊早，所以「啟動時讀 JSON → 每筆註冊一個物品」可行。
- **datapack 加不了物品**：datapack 在進世界時才載入，那時物品註冊表已經凍結；而且每個世界有自己的 datapack，物品清單卻是全遊戲共用。datapack 只適合放「數值」，不適合放「有哪些武器」。
- **資源包可以在執行時加入**：Forge 的 `AddPackFindersEvent` 讓模組加一個資源包來源，內容可以由程式產生。物品外觀要三樣檔案：物品模型定義（`items/<id>.json`）、模型（`models/item/<id>.json`）、貼圖（`textures/item/<名稱>.png`）。26.x 的物品貼圖有自己的圖集（`minecraft:items`），會掃所有命名空間的 `textures/item/`。換外觀以後要重新載入資源（`Minecraft.reloadResourcePacks()`，等同 F3+T，會出現幾秒的載入畫面）。
- **單人遊戲按 Esc 或切到瀏覽器時，內建伺服器會暫停**，但伺服器的工作佇列照常執行（`MinecraftServer.waitUntilNextTick()` 每一圈都跑 `runAllTasks()`，暫停只略過世界的 tick）。所以遊戲暫停時，網頁的修改也會套用。
- **網頁伺服器**：JDK 內建 `com.sun.net.httpserver.HttpServer`（模組 `jdk.httpserver`），不用加相依。Minecraft 本身也有 JSON-RPC 管理伺服器，但只有獨立伺服器（`DedicatedServer`）會啟動，單人遊戲用不到，所以不採用。**技術風險**：Forge 的模組層能不能存取 `jdk.httpserver` 還沒實測，B 的第一步先驗證；不行就改用 Minecraft 已經內附的 Netty。
- **物品名稱**可以由 `Item.getName(ItemStack)` 回傳，不一定要寫在語系檔，所以 JSON 裡的名稱可以直接顯示，改了立刻生效。
- **盔甲穿在身上的外觀**是註冊物品時決定的（`Equippable` 元件），執行中不能換；物品圖示可以換。
- **多人連線時，客戶端和伺服器的物品清單必須相同**，否則連不上。所以客戶端也要有同一份武器清單（R5 再處理同步）。

## 3. 變更邊界（已確認）

### 分層（新增和修改的部分）

```
core/equipment/   EquipmentDefinition   加「名稱」；外觀改成 EquipmentLook（原版物品｜自訂貼圖）
                  EquipmentLook         新增：VanillaItemLook(item)、TextureLook(texture)
                  EquipmentLibrary      新增：目前生效的整份定義（不可變快照）；整份驗證通過才換
                  LoadoutCalculator     只新增一個建構子，改讀 EquipmentLibrary 的最新快照
data/             （新的一層，純 Java＋Gson，不 import Minecraft）
                  WeaponJson            JSON ↔ EquipmentDefinition，錯誤訊息寫出檔名和欄位
                  WeaponFolder          讀整個資料夾、原子寫入單一檔案、檢查 PNG
                  DefaultWeapons        模組內建的 4 件預設 JSON；第一次啟動時複製到設定資料夾
web/              （新的一層，純 Java，不 import Minecraft）
                  WeaponAdmin           介面：列出、讀取、修改、新增、重新載入（網頁只認得它）
                  AdminHttpServer       JDK HttpServer：靜態網頁、路由、安全檢查
                  WeaponApi             HTTP ↔ WeaponAdmin，統一的錯誤格式
forge/            ServerWeaponAdmin     實作 WeaponAdmin：切到伺服器執行緒、寫檔、換快照、重算持有者
                  WebAdminLifecycle     伺服器啟動時開網頁，關閉時停
                  ReloadCommand         /mobarmory reload
                  EquipmentItem         名稱從定義讀
                  EquipmentItemFactory  啟動時從 EquipmentLibrary 註冊物品
                  EquipmentStatApplier  加「重算所有拿著某種武器的實體」
forge/client/     GeneratedWeaponPack   執行時產生的資源包：物品模型定義、模型、貼圖
resources/        mobarmory/default_weapons/*.json   4 件預設武器（取代 content/ModEquipment）
                  mobarmory/web/        index.html、app.js、style.css（不用外部 CDN）
```

遊戲資料夾裡的設定（不進 git）：

```
config/mobarmory/
  weapons/heavy_sword.json …   一把武器一個檔，檔名就是 id
  textures/halberd_netherite.png …   自訂貼圖
  web.json                     網頁的位址、埠、開關
```

### 網頁 API（第一版）

| 方法與路徑 | 用途 | 回應 |
|---|---|---|
| `GET /` | 網頁本身 | 靜態檔 |
| `GET /api/v1/stats` | 數值登記表：id、名稱、範圍、是不是機率（表單用） | 200 |
| `GET /api/v1/looks` | 可選的原版外觀和已上傳的貼圖 | 200 |
| `GET /api/v1/weapons` | 所有武器：目前生效的定義、版本號、狀態（生效中／待重開） | 200 |
| `GET /api/v1/weapons/{id}` | 一把武器 | 200／404 |
| `PUT /api/v1/weapons/{id}` | 改名稱、數值、外觀（要帶版本號） | 200／400／404／409 |
| `POST /api/v1/weapons` | 上傳新武器（數值＋貼圖名稱） | 202（待重開）／400／409 |
| `PUT /api/v1/textures/{name}` | 上傳 PNG | 201／400 |
| `POST /api/v1/reload` | 從檔案重新載入 | 200（套用了哪些、哪些待重開）／400 |
| `GET /api/v1/mobs` | **預留給 R4**，這次不實作 | 501 |

錯誤一律回 `{"error": "...", "message": "...", "fields": {"modifiers[0].add": "不是數字"}}`。

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `data/`、`web/`、`forge/` 的上述新類別、`forge/client/`、`resources/mobarmory/`<br>修改 `core/equipment` 的 `EquipmentDefinition`、`LoadoutCalculator`（只新增建構子）、`forge/` 的 `EquipmentItem`、`EquipmentItemFactory`、`EquipmentStatApplier`、`MobArmoryMod`（接線）<br>刪除 `content/ModEquipment`、`assets/mobarmory/items/*.json`、語系檔裡 4 件裝備的名稱（改由 JSON 提供）<br>`build.gradle`：遊戲內測試改在獨立資料夾 `run-gametest/` 跑，每次先清空<br>測試：只改「怎麼拿到重劍」（從 `ModEquipment.HEAVY_SWORD` 改成用 id 查），**預期的數字一個都不改** |
| 不准動的相依 | `core/stat`、`core/combat` 的公開介面和規則；`StatAttributeBridge`、`EntityStatBinder`、`CombatEffectListener` 的行為；`EquipmentCatalog` 的驗證規則（JSON 和網頁都經過它）；Minecraft 和 Forge 本身（不用 Mixin）；**不加外部相依**；R4 的 05～07；`mod-damage-hud/` 資料夾 |
| 保留或新建的抽象介面 | 保留：`StatReader`、`LoadoutCalculator`（舊建構子留著，舊測試不用改）、`EquipmentDefinition.of(...)`<br>新建：`EquipmentLook`（sealed，兩種外觀）<br>新建：`WeaponAdmin`（web 層唯一認得的介面；之後的 `MobAdmin` 照同樣的形狀）<br>新建：`WeaponStore`（讀寫武器檔；資料夾版給遊戲用、記憶體版給測試用） |
| 新功能放哪、用什麼形狀 | 新武器：`config/mobarmory/weapons/` 加一個 JSON（加貼圖就多放一張 PNG），重開遊戲<br>改既有武器：改 JSON 後 `/mobarmory reload`，或在網頁改<br>新的外觀種類（例如自訂 3D 模型）：`EquipmentLook` 加一種，`GeneratedWeaponPack` 加一個產生方式 |
| 改一處會牽動幾個檔案 | 新增一把武器：**1～2 個檔案，0 行 Java**（JSON＋PNG）<br>改一把武器的數值：**1 個 JSON**，或網頁上改<br>新增一個數值讓武器使用：照 R1，**1～2**（`ModStats`、語系檔），網頁的表單自動出現新數值 |

和 `damage-hud`（`feature/damage-hud`）可能衝突的檔案：`MobArmoryMod`（接線）、`lang/en_us.json`、`build.gradle`。合併時再處理，這邊不碰它的資料夾。

### SOLID 判斷（第一輪 Q1 要確認）

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 改一個數值要改 `ModEquipment.java` 再重新 build | O 開放封閉 | 武器是設定檔裡的資料；Java 只寫「怎麼讀」和「規則」 |
| 網頁的 handler 直接拿 Minecraft 的實體、在網頁執行緒改屬性 | S、D | 網頁只依賴 `WeaponAdmin` 介面；切執行緒、重算實體由 `forge/` 的轉接器做 |
| JSON 載入和網頁各寫一套驗證，規則慢慢不一樣 | S 單一職責 | 只有一套：`EquipmentDefinition` 和 `EquipmentCatalog`（R2 已有）；JSON 和網頁都把資料交給它 |
| 執行中改一個共用、可變的定義物件，網頁、伺服器、畫面三條執行緒同時讀寫 | S | 定義是不可變的快照；改的時候建一份新的、驗證通過才整份換掉 |
| 測試讀到使用者改過的設定檔，結果時好時壞 | D 依賴反轉 | 讀檔經過 `WeaponStore`；遊戲內測試用獨立資料夾，每次從內建預設開始 |

L（里氏替換）由契約測試保證：所有武器定義跑同一組契約（R2 已有 6 條，再加「JSON 存了再讀回來一模一樣」）；`WeaponStore` 的兩個實作跑同一組契約。

### Pattern 選擇（第一輪 Q2 要確認）

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 啟動時讀設定資料夾，每把武器註冊一個物品（沿用 R2 的 Factory）；執行中只換**定義**（不可變快照，Copy-on-write）；網頁用「介面＋轉接器」接到遊戲；外觀由執行時產生的資源包提供 | 和原版一樣，每把武器有自己的 `/give mobarmory:<id>`；R2 的程式和測試幾乎不用改；網頁可以不開遊戲就測 | **新武器要重開遊戲**才生效（平台限制）；網頁上傳的新武器顯示「待重開」 |
| B | 只註冊**一個**通用武器物品，每個物品身上記「是哪一把武器」（資料元件）；外觀也用元件指定 | 網頁可以在遊戲執行中直接建立新武器，不用重開；多人連線時物品清單不會對不上 | `/give` 要帶元件，例如 `/give @p mobarmory:weapon[mobarmory:weapon="halberd"]`；R2 的物品和測試要改寫；約多 1 天 |
| C | 直接寫：網頁的 handler 直接改 `ModEquipment` 的靜態欄位、直接改玩家屬性 | 今天最快 | 有執行緒問題；網頁沒辦法不開遊戲測；數值沒寫回檔案，重開就不見 |

## 4. 實作順序

先做 A 再做 B。每一步做完都跑 `gradlew clean build runGameTestServer`，全過才進下一步。示範影片需要的路徑（改數值 → 砍怪）排在前面，外觀和上傳排在後面。

**A. 資料驅動**

1. `core/equipment`：`EquipmentDefinition` 加名稱和 `EquipmentLook`；`EquipmentLibrary`。既有測試全過。
2. `data/`：`WeaponJson`、`WeaponFolder`、4 件預設 JSON。**先寫一個遷移測試**：從 JSON 讀出的 4 件和舊的 `ModEquipment` 完全相同，通過後才刪掉 `ModEquipment`。
3. `forge/`：啟動時讀 `config/mobarmory/weapons/`（第一次啟動複製預設）、從快照註冊物品、名稱從定義讀；刪掉 `ModEquipment` 和舊的物品模型檔；遊戲內測試改在 `run-gametest/` 跑。**單元 106、遊戲內 18 要全過，T1 不變。**
4. `forge/client/`：執行時資源包。用 hanhanpx 的戟測自訂貼圖（PNG 複製到 `run/config/mobarmory/textures/`，不進 repo）。
5. `/mobarmory reload`：重讀檔案 → 整份驗證 → 換快照 → 重算拿著這些武器的實體。加遊戲內測試：改定義後，手上的重劍立刻變成新數值。

**B. 網頁後台**

1. 先驗證 `jdk.httpserver` 在 Forge 裡能用；`WebAdminLifecycle`（跟著世界開關）；安全檢查；空白網頁。
2. 讀取 API：數值、外觀、武器清單；`/api/v1/mobs` 回 501。
3. 修改 API：驗證 → 寫檔 → 在伺服器執行緒換快照 → 重算持有者；版本號（409）。
4. 網頁畫面：武器清單、編輯表單。
5. 換外觀：重新產生資源包，單人遊戲自動重新載入資源。
6. 上傳新武器（依 Q7 的答案）。
7. `runClient` 實際走一次、示範影片劇本、行為報告。

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `WeaponJson`：每個欄位的讀寫、錯誤訊息有檔名和欄位；`WeaponFolder`：原子寫入、PNG 檢查（格式、正方形、2 的次方、大小上限）；`EquipmentLibrary`：整份換、驗證失敗不換、回報「待重開」；`WeaponApi`：用假的 `WeaponAdmin` 測每個路徑的狀態碼（200／400／403／404／409／501） |
| Module | 「一把資料驅動的武器能正常運作」的成功條件逐一破壞，見 [09](09-weapon-data-web-given-when-then.md) 的 B 系列 |
| 契約 | (1) 所有內建預設武器跑 R2 的 6 條契約，再加「JSON 存了再讀回來一模一樣」<br>(2) `WeaponStore` 的資料夾版和記憶體版跑同一組：讀到剛寫的、寫一半失敗不留下壞檔、不存在的 id 回空<br>(3) 遷移：JSON 預設值 = 舊的 Java 定義（刪掉 `ModEquipment` 前跑） |
| System | GameTest（在 `run-gametest/`，從內建預設開始）：既有 18 個全部照舊通過（T1 不變）；換定義後手上的重劍立刻變成新數值；殭屍手上的也一樣；重新載入時有一個壞檔，整份不換、舊數值還在 |
| HTTP | 單元測試裡在隨機埠開真的 `HttpServer`，用 JDK 的 `HttpClient` 打：安全檢查（別的網站的 `Origin`、錯的 `Host`、不是 JSON）、錯誤格式、靜態網頁 |
| 手動 | `runClient` ＋瀏覽器：照第 6 節的劇本走一次並截圖 |

## 6. 示範影片劇本（草稿）

> 定稿版在 [14 第 8 節](14-weapon-data-web-behavior-report.md#8-示範影片劇本d2)。

1. 遊戲：單人世界，生存模式，主手拿重劍，面前有一隻殭屍。等蓄力滿再砍一下，看傷害（D1 顯示）。攻擊力是 1＋6＝7，殭屍有 2 點護甲，實際傷害約 6.9。
2. 切到瀏覽器，打開 `http://127.0.0.1:8765/`（網址會在進世界時顯示在聊天欄）。武器清單列出 4 件裝備和自訂武器。
3. 點重劍，攻擊力從 +6 改成 +16，按儲存。網頁顯示「已套用」。
4. 回遊戲（不用重新拿劍）：tooltip 已經是 +16；再砍殭屍，攻擊力 17，傷害約 16.7。
5. （有時間的話）在網頁把重劍外觀換成上傳的戟貼圖，遊戲自動重新載入資源，手上的劍變成戟。
6. 打開 `config/mobarmory/weapons/heavy_sword.json`，看到攻擊力已經寫成 16；重開遊戲後仍是 +16。
