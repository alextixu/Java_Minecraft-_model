# 邊界說明：R2b 武器改成資料驅動＋R6 網頁後台

> 狀態：**已確認（最終版）**。決定在 [10](10-weapon-data-web-open-questions.md)：使用者先回「全部照建議」，之後改為「照審查建議版」，Q7 再調整為「新增武器表單，不上傳 PNG」。第一版的本檔在 git 歷史（commit `18c4487`）。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [09](09-weapon-data-web-given-when-then.md)。
> 前提：沿用 R1／R2 已確認的架構（`core/`、`content/`、`forge/` 分層，數值登記表、`LoadoutCalculator`、R2 Q6「同種裝備共用一份定義」）。

## 1. 需求翻譯

需求來源：使用者 2026-11-06 的指示（「都做」），以及「我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點」。

- **A. R2b**：武器的定義（id、欄位、數值修飾、外觀）改從 JSON 設定檔讀。加武器、改數值都不用改 Java。外觀可以借用原版物品，或用放在 jar 裡的 7 張貼圖。現有 4 件裝備搬成 JSON，行為不變。
- **B. R6**：伺服器端開 API。網頁可以列出伺服器上的武器和數值、修改數值、從內建清單換外觀、新增武器（重開後生效）。改了以後，玩家手上那把照 R2 Q6 生效。怪物先在 API 留位置。
- **示範影片**：在網頁後台改重劍的攻擊力，回遊戲砍殭屍，傷害跟著變。

### 真正的需求

- **武器的「定義」和「物品種類」是兩件事。** 物品種類（`mobarmory:heavy_sword`）只能在啟動時註冊，所以「加一把新武器」要重開遊戲；定義（名稱、數值、外觀）可以在執行中整份換掉，「改一把既有武器」不用重開。
- **設定檔是唯一的來源。** 遊戲、`/mobarmory reload`、網頁讀寫的都是同一份資料，驗證規則只有一套。網頁改了以後寫回檔案。
- **伺服器說了算，客戶端跟著伺服器。** 數值本來就由原版 Attribute 同步；名稱、tooltip、外觀要另外把定義送給客戶端（封包）。貼圖放在 jar 裡，每個客戶端都有。這樣兩個客戶端看到的一樣（R5）。
- **改了要立刻作用在所有同種武器上。** 換定義之後，重算正拿著這種武器的玩家和生物，並把新定義送給所有玩家。
- **網頁只是另一個「改定義的入口」。** 網頁只呼叫一個介面，由 `forge/` 的轉接器切到伺服器執行緒去做。網頁的程式可以不開遊戲就測試。
- **測試不能被使用者改過的設定影響。**

### 這次要做／未來可能變

| 這次要做 | 未來會變，所以要在這裡切邊界 |
|---|---|
| 4 件裝備搬成 JSON；7 張 hanhanpx 貼圖放進 jar | **武器會一直加** → 一把一個檔，加武器不改程式；加新貼圖要放進 jar 再 build |
| 網頁改數值、名稱、外觀（從內建清單選），新增武器 | **怪物也要能改**（R4）→ API 路徑和「改定義的入口」介面先留好 |
| 定義同步到客戶端 | **多人連線**（R5）→ 名稱、tooltip、外觀跟伺服器一致；物品清單本身仍要兩邊相同 |
| 設定檔格式第 1 版 | **格式會改** → 每個檔案寫 `"format": 1` |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下在 Forge 26.3-66.1.0 和 Minecraft 26.3 的原始碼裡確認過：

- **物品註冊表在啟動後凍結**，新的物品種類只能在模組載入時加入。模組建構時就讀得到 `config/`，比物品註冊早。
- **datapack 加不了物品**（進世界時物品註冊表已經凍結），也是每個世界各自一份。
- **物品用哪個模型由 `item_model` 元件決定。** 物品預設的 `item_model` 就是自己的 id，而且註冊時固定（`Item.Properties` 最後一定會把它設成 id，Forge 也沒有修改預設元件的掛勾）。但每個物品堆疊可以帶自己的 `item_model`，原版會把它同步到客戶端。所以：
  - jar 裡為每一種外觀放一份模型（原版物品本來就有；7 張貼圖各放一份 `items/look/<名稱>.json`）。
  - 伺服器讓每一把武器帶著「目前外觀」的 `item_model`。外觀改了，伺服器在下一個 tick 更新玩家身上的武器，客戶端隨原版同步看到新外觀，**不用重新載入資源**。
- **Forge 的 SimpleChannel** 可以定義伺服器 → 客戶端的封包（`ChannelBuilder.named(...).simpleChannel().play().clientbound().addMain(...)`），用 `PacketDistributor.PLAYER`／`ALL` 送出。
- **單人遊戲按 Esc 或切到瀏覽器時，內建伺服器會暫停**，但工作佇列照常執行（`MinecraftServer.waitUntilNextTick()` 每一圈都跑 `runAllTasks()`）。
- **網頁伺服器**：JDK 內建 `com.sun.net.httpserver.HttpServer`，不用加相依；在 Forge 裡能用（遊戲內測試已證明）。Minecraft 的 JSON-RPC 管理伺服器只有獨立伺服器有，不採用。
- **物品名稱**可以由 `Item.getName(ItemStack)` 回傳，不一定要寫在語系檔。
- **盔甲穿在身上的外觀**是註冊物品時決定的（`Equippable` 元件），執行中不能換。
- **多人連線時，客戶端和伺服器的物品清單必須相同**，否則連不上。所以兩邊的 `config/mobarmory/weapons/` 要有同一組 id（數值、名稱、外觀不用一樣，會由封包同步）。

## 3. 變更邊界（最終版）

### 分層（新增和修改的部分）

```
core/equipment/   EquipmentDefinition   加「名稱」；外觀改成 EquipmentLook（原版物品｜內建貼圖）
                  EquipmentLook         VanillaItem(item)、Texture(texture)
                  EquipmentLibrary      目前生效的整份定義（不可變快照）；整份驗證通過才換
                  LoadoutCalculator     只新增一個建構子，改讀 EquipmentLibrary 的最新快照
data/             （純 Java＋Gson，不 import Minecraft）
                  WeaponJson            JSON ↔ EquipmentDefinition，錯誤寫出檔名和欄位
                  WeaponStore／WeaponFolder   讀整個資料夾、原子寫入單一檔案
                  WeaponLoader          讀檔並檢查，一次列出所有問題
                  WeaponEditor          唯一改武器的地方：reload、update、create
                  BuiltInTextures       jar 裡的 7 張貼圖清單
                  WeaponSync            定義 ↔ 封包內容（id → JSON），客戶端套用
                  DefaultWeapons        模組內建的 4 件預設；第一次啟動時複製到設定資料夾
web/              （純 Java，不 import Minecraft）
                  WeaponAdmin           介面：網頁只認得它
                  WeaponApi             HTTP ↔ WeaponAdmin，統一的錯誤格式
                  AdminHttpServer       JDK HttpServer：靜態網頁、路由、安全檢查
forge/            ServerWeaponAdmin     實作 WeaponAdmin：切到伺服器執行緒 → WeaponEditor → 重算持有者 → 送同步封包
                  WeaponLooks           外觀 → item_model；讓武器帶著目前外觀
                  WeaponSyncChannel     同步封包（進世界時、每次改動後）
                  WebAdminLifecycle、ReloadCommand、EquipmentItem、EquipmentItemFactory、EquipmentStatApplier
resources/        mobarmory/default_weapons/*.json           4 件預設武器
                  assets/mobarmory/textures/item/*.png       7 張 hanhanpx 貼圖＋CREDITS.md
                  assets/mobarmory/items/look/*.json、models/item/look/*.json   7 種外觀的模型
                  assets/mobarmory/items/<4 件預設>.json      預設外觀（武器還沒被伺服器更新前用）
                  mobarmory/web/        index.html、app.js、style.css
```

遊戲資料夾裡的設定（不進 git）：

```
config/mobarmory/
  weapons/heavy_sword.json …   一把武器一個檔，檔名就是 id
  web.json                     網頁的位址、埠、開關
```

### 給其他模組用的穩定介面（R4、D1 合併時依這個）

- 用 id 查目前的裝備定義：`MobArmoryMod.EQUIPMENT_LIBRARY.current().find(EquipmentId.of("heavy_sword"))`（`Optional<EquipmentDefinition>`），或 `.get(...)`（找不到時丟例外）。
- 拿到物品：`MobArmoryMod.EQUIPMENT.item(EquipmentId.of("heavy_sword"))`。
- `content/ModEquipment` 已經不存在；請不要再用 Java 常數引用裝備。

### 網頁 API（第一版）

| 方法與路徑 | 用途 | 回應 |
|---|---|---|
| `GET /` | 網頁本身 | 靜態檔 |
| `GET /api/v1/stats` | 數值登記表（表單用） | 200 |
| `GET /api/v1/looks` | 可選的原版外觀和 7 張內建貼圖 | 200 |
| `GET /api/v1/textures/{name}` | 內建貼圖的預覽 | 200／404 |
| `GET /api/v1/weapons` | 所有武器：目前的定義、狀態（生效中／待重開／檔案已刪除） | 200 |
| `GET /api/v1/weapons/{id}` | 一把武器 | 200／404 |
| `PUT /api/v1/weapons/{id}` | 改名稱、數值、外觀（後存的蓋掉先存的） | 200／400／404 |
| `POST /api/v1/weapons` | 新增武器（外觀從內建清單選） | 202（待重開）／400 |
| `POST /api/v1/reload` | 從檔案重新載入 | 200／400 |
| `GET /api/v1/mobs` | **預留給 R4** | 501 |

錯誤一律回 `{"error": "...", "message": "...", "fields": {"modifiers[0].add": "..."}}`。

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `data/`、`web/`、`forge/` 的上述新類別、`resources/` 的上述檔案<br>修改 `core/equipment` 的 `EquipmentDefinition`、`LoadoutCalculator`（只新增建構子）、`forge/` 的 `EquipmentItem`、`EquipmentItemFactory`、`EquipmentStatApplier`、`MobArmoryMod`（接線）<br>刪除 `content/ModEquipment`、語系檔裡 4 件裝備的名稱（改由 JSON 提供）<br>`build.gradle`：遊戲內測試改在 `run-gametest/` 跑，每次先清空<br>測試：只改「怎麼拿到重劍」，**預期的數字一個都不改** |
| 不准動的相依 | `core/stat`、`core/combat` 的公開介面和規則；**`content/ModStats`**（R4 會加最大生命值）；`StatAttributeBridge`、`EntityStatBinder`、`CombatEffectListener` 的行為；`EquipmentCatalog` 的驗證規則；Minecraft 和 Forge 本身（不用 Mixin、不用 Access Transformer）；不加外部相依；`mod-damage-hud/`、`mod-r4-mobs/` 資料夾 |
| 保留或新建的抽象介面 | 保留：`StatReader`、`LoadoutCalculator`（舊建構子留著）、`EquipmentDefinition.of(...)`<br>新建：`EquipmentLook`（sealed，兩種外觀）；`WeaponAdmin`（web 層唯一認得的介面，之後的 `MobAdmin` 照同樣的形狀）；`WeaponStore`（資料夾版給遊戲、記憶體版給測試） |
| 新功能放哪、用什麼形狀 | 新武器：`config/mobarmory/weapons/` 加一個 JSON，或在網頁新增，重開遊戲<br>改既有武器：改 JSON 後 `/mobarmory reload`，或在網頁改<br>新的內建貼圖：PNG＋兩個小 JSON 放進 jar，`BuiltInTextures` 加一行，重新 build |
| 改一處會牽動幾個檔案 | 新增一把武器：**1 個 JSON，0 行 Java**<br>改一把武器的數值：**1 個 JSON**，或網頁上改<br>新增一張內建貼圖：**4 個檔案**（PNG、2 個模型 JSON、`BuiltInTextures` 一行） |

### SOLID 判斷

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 改一個數值要改 `ModEquipment.java` 再重新 build | O | 武器是設定檔裡的資料 |
| 網頁的 handler 直接拿 Minecraft 的實體、在網頁執行緒改屬性 | S、D | 網頁只依賴 `WeaponAdmin`；切執行緒、重算實體、送封包由 `forge/` 的轉接器做 |
| JSON 載入和網頁各寫一套驗證 | S | 只有一套：`WeaponJson`＋`EquipmentDefinition`＋`EquipmentCatalog` |
| 多條執行緒同時讀寫共用的定義 | S | 定義是不可變的快照，整份換掉 |
| 測試讀到使用者改過的設定檔 | D | 讀檔經過 `WeaponStore`；遊戲內測試用獨立資料夾 |

L（里氏替換）由契約測試保證：所有武器定義跑同一組契約；`WeaponStore` 的兩個實作跑同一組契約。

### Pattern 選擇

採用 Q2 A：啟動時讀設定資料夾，每把武器註冊一個物品（Factory）；執行中只換**定義**（不可變快照）；網頁用「介面＋轉接器」接到遊戲；外觀和名稱由伺服器同步給客戶端。

## 4. 實作順序

先做 A（R2b），完成後停下來回報；再做 B（R6 最小版），完成後停下來回報。每一步都跑 `gradlew clean build runGameTestServer`。

第一版（「全部照建議」）已經做完 A 和 B。改成最終版要做的事：

**A. R2b**

1. 7 張貼圖、外觀模型、`CREDITS.md` 放進 jar；`BuiltInTextures`；`EquipmentLook.Texture` 只能用這 7 張。
2. 刪掉執行時資源包（`GeneratedWeaponPack`、`WeaponAssets`、`ClientResourceReload`）和上傳貼圖（`WeaponStore` 的貼圖方法、`WeaponEditor.saveTexture`、網頁的上傳）；放回 4 件預設的物品模型檔。
3. `WeaponLooks`：武器帶著目前外觀的 `item_model`（玩家背包每個 tick 對齊、創造模式分頁、重算持有者時）。
4. `WeaponSync`＋`WeaponSyncChannel`：進世界時、每次改動後把定義送給客戶端；單人遊戲的客戶端和伺服器共用同一份，不重複套用。

**B. R6 最小版**

1. 拿掉版本號和 409（O10：後存的蓋掉先存的）。
2. 網頁：列出、改數值、從內建清單換外觀、新增武器表單（不上傳 PNG）、`/api/v1/mobs` 回 501。
3. `runClient` 走一次、示範影片劇本、行為報告（[15](15-weapon-data-web-behavior-report.md)）。

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `WeaponJson`、`EquipmentLibrary`、`WeaponSync`（定義 → 封包內容 → 套用）、`WeaponApi`（每個路徑的狀態碼） |
| Module | 「一把資料驅動的武器能正常運作」的成功條件逐一破壞，見 [09](09-weapon-data-web-given-when-then.md) 的 B 系列 |
| 契約 | (1) 所有內建預設武器跑 R2 的契約，再加「JSON 存了再讀回來一模一樣」<br>(2) `WeaponStore` 的兩個實作跑同一組<br>(3) 遷移：JSON 預設值 = 舊的 Java 定義<br>(4) 7 張內建貼圖：每張都有 PNG（正方形、2 的次方）、模型、物品模型定義 |
| System | GameTest：既有 18 個全部照舊；執行中改武器（手上的劍、殭屍、reload、壞檔、新檔／刪檔）；網頁存檔穿過遊戲；外觀改了以後武器帶著新的 `item_model`；同步封包編碼、解碼 |
| HTTP | 安全檢查（別的網站的 `Origin`、錯的 `Host`、不是 JSON）、錯誤格式、靜態網頁 |
| 手動 | `runClient`＋瀏覽器：照 15 的劇本走一次 |
