# 邊界說明：R2b 武器改成資料驅動＋R6 網頁後台

> 狀態：**已確認（最終版）**。決定在 [10](10-weapon-data-web-open-questions.md)，按時間順序：「全部照建議」→「照審查建議版」→ Q7 改成新增武器表單 →「要能上傳 PNG」。前幾版的本檔在 git 歷史（commit `18c4487`、`1866695`）。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [09](09-weapon-data-web-given-when-then.md)。
> 前提：沿用 R1／R2 已確認的架構（`core/`、`content/`、`forge/` 分層，數值登記表、`LoadoutCalculator`、R2 Q6「同種裝備共用一份定義」）。

## 1. 需求翻譯

需求來源：使用者 2026-11-06 的指示（「都做」）、「我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點」、「要能上傳 PNG」。

- **A. R2b**：武器的定義（id、欄位、數值修飾、外觀）改從 JSON 設定檔讀，加武器、改數值都不用改 Java。外觀有三種：借用原版物品、jar 裡的 7 張內建貼圖、上傳的 PNG。現有 4 件裝備搬成 JSON，行為不變。
- **B. R6**：伺服器端開 API。網頁可以列出伺服器上的武器和數值、修改數值和外觀、新增武器（可以上傳 PNG，重開後生效）。改了以後，玩家手上那把照 R2 Q6 生效。怪物先在 API 留位置。
- **多人一致**（review-01 風險 4）：另一台電腦連進來，名稱、tooltip、貼圖都要和伺服器一樣。
- **示範影片**：在網頁後台改重劍的攻擊力，回遊戲砍殭屍，傷害跟著變。

### 真正的需求

- **武器的「定義」和「物品種類」是兩件事。** 物品種類只能在啟動時註冊，所以「加一把新武器」要重開遊戲；定義（名稱、數值、外觀）可以在執行中整份換掉。
- **設定檔是唯一的來源。** 遊戲、`/mobarmory reload`、網頁讀寫的都是同一份資料，驗證規則只有一套。網頁改了以後寫回檔案。
- **伺服器說了算，客戶端跟著伺服器。** 數值由原版 Attribute 同步；名稱、tooltip、上傳的 PNG 由我們的封包送給客戶端；外觀由物品堆疊帶著的 `item_model` 決定（原版同步）。
- **改了要立刻作用在所有同種武器上。**
- **網頁只是另一個「改定義的入口」。** 網頁只呼叫一個介面，由 `forge/` 的轉接器切到伺服器執行緒去做。
- **測試不能被使用者改過的設定影響。**

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下在 Forge 26.3-66.1.0 和 Minecraft 26.3 的原始碼裡確認過：

- **物品註冊表在啟動後凍結**；模組建構時就讀得到 `config/`，比物品註冊早。**datapack 加不了物品。**
- **物品用哪個模型由 `item_model` 元件決定。** 物品預設的 `item_model` 就是自己的 id，註冊時固定；但每個物品堆疊可以帶自己的 `item_model`，原版會把它同步到客戶端。所以每一種外觀有一份模型（原版物品本來就有；內建貼圖在 jar 的 `items/look/<名稱>.json`；上傳的貼圖由執行時資源包提供同名的檔案），伺服器讓武器帶著「目前外觀」的 `item_model`。
- **資源包可以在執行時加入**（`AddPackFindersEvent`），內容由程式產生。新的貼圖要重新載入資源（`Minecraft.reloadResourcePacks()`，等同 F3+T，幾秒的載入畫面）才會出現。
- **Forge 的 SimpleChannel** 可以定義伺服器 → 客戶端的封包。Minecraft 的自訂封包有大小上限，所以一張 PNG 一個封包（PNG 最大 256 KB）。
- **單人遊戲暫停時**，伺服器的工作佇列照常執行。
- **網頁伺服器**：JDK 內建 `HttpServer`，不用加相依；在 Forge 裡能用（遊戲內測試已證明）。
- **盔甲穿在身上的外觀**是註冊物品時決定的，執行中不能換。
- **多人連線時，客戶端和伺服器的物品清單必須相同**，否則連不上。兩邊的 `config/mobarmory/weapons/` 要有同一組 id；數值、名稱、外觀、上傳的 PNG 由伺服器同步。

## 3. 變更邊界（最終版）

### 分層

```
core/equipment/   EquipmentDefinition（名稱、外觀）、EquipmentLook（VanillaItem｜Texture）、EquipmentLibrary（不可變快照）、
                  LoadoutCalculator（新增讀最新快照的建構子）、ReloadReport
data/             （純 Java＋Gson，不 import Minecraft）
                  WeaponJson、WeaponStore／WeaponFolder（武器檔和上傳的貼圖）、WeaponLoader、WeaponEditor、
                  BuiltInTextures（jar 裡的 7 張）、PngCheck（上傳的驗證）、WeaponAssets（執行時產生的檔案）、
                  WeaponSync（定義、上傳的 PNG 和摘要）、DefaultWeapons
web/              （純 Java）WeaponAdmin、WeaponApi、AdminHttpServer、WebSettings
forge/            ServerWeaponAdmin、WeaponLooks（武器帶著目前外觀）、WeaponSyncChannel（定義＋PNG 封包）、
                  WebAdminLifecycle、ReloadCommand、EquipmentItem、EquipmentItemFactory、EquipmentStatApplier、WeaponStartup
forge/client/     GeneratedWeaponPack（執行時資源包）、ClientTextures（從伺服器收到的 PNG）、ClientResourceReload
resources/        mobarmory/default_weapons/*.json；assets/mobarmory/textures/item/（7 張＋CREDITS.md）；
                  assets/mobarmory/items/look/、models/item/look/（7 種內建外觀）；assets/mobarmory/items/<4 件預設>.json；
                  mobarmory/web/（index.html、app.js、style.css）
```

遊戲資料夾裡的設定（不進 git）：

```
config/mobarmory/
  weapons/heavy_sword.json …   一把武器一個檔，檔名就是 id
  textures/my_blade.png …      網頁上傳的貼圖
  web.json                     網頁的位址、埠、開關
```

### 給其他模組用的穩定介面（R4、D1 合併時依這個）

- 用 id 查目前的裝備定義：`MobArmoryMod.EQUIPMENT_LIBRARY.current().find(EquipmentId.of("heavy_sword"))`（`Optional<EquipmentDefinition>`），或 `.get(...)`。
- 拿到物品：`MobArmoryMod.EQUIPMENT.item(EquipmentId.of("heavy_sword"))`；要帶目前外觀時用 `WeaponLooks.stackOf(item, MobArmoryMod.EQUIPMENT_LIBRARY)`。
- `content/ModEquipment` 已經不存在；請不要再用 Java 常數引用裝備。

### 網頁 API（第一版）

| 方法與路徑 | 用途 | 回應 |
|---|---|---|
| `GET /` | 網頁本身 | 靜態檔 |
| `GET /api/v1/stats` | 數值登記表（表單用） | 200 |
| `GET /api/v1/looks` | 原版外觀、所有貼圖（內建＋上傳）、哪些是內建的 | 200 |
| `GET /api/v1/textures/{name}` | 貼圖預覽 | 200／404 |
| `PUT /api/v1/textures/{name}` | 上傳 PNG（`image/png`） | 201／400 |
| `GET /api/v1/weapons` | 所有武器：定義、狀態（生效中／待重開／檔案已刪除） | 200 |
| `GET /api/v1/weapons/{id}` | 一把武器 | 200／404 |
| `PUT /api/v1/weapons/{id}` | 改名稱、數值、外觀（後存的蓋掉先存的） | 200／400／404 |
| `POST /api/v1/weapons` | 新增武器 | 202（待重開）／400 |
| `POST /api/v1/reload` | 從檔案重新載入 | 200／400 |
| `GET /api/v1/mobs` | **預留給 R4** | 501 |

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `data/`、`web/`、`forge/` 的上述類別、`forge/client/`、`resources/` 的上述檔案<br>修改 `core/equipment`（只新增）、`forge/` 的 `EquipmentItem`、`EquipmentItemFactory`、`EquipmentStatApplier`、`MobArmoryMod`<br>刪除 `content/ModEquipment`、語系檔裡 4 件裝備的名稱<br>`build.gradle`：遊戲內測試在 `run-gametest/` 跑<br>測試：只改「怎麼拿到重劍」，預期的數字一個都不改 |
| 不准動的相依 | `core/stat`、`core/combat` 的公開介面和規則；`content/ModStats`；`StatAttributeBridge`、`EntityStatBinder`、`CombatEffectListener` 的行為；`EquipmentCatalog` 的驗證規則；Minecraft 和 Forge 本身（不用 Mixin、不用 Access Transformer）；不加外部相依；`mod-damage-hud/`、`mod-r4-mobs/` |
| 保留或新建的抽象介面 | 保留：`StatReader`、`LoadoutCalculator`（舊建構子留著）、`EquipmentDefinition.of(...)`<br>新建：`EquipmentLook`；`WeaponAdmin`（web 層唯一認得的介面）；`WeaponStore`（資料夾版、記憶體版） |
| 新功能放哪、用什麼形狀 | 新武器：`config/mobarmory/weapons/` 加一個 JSON，或在網頁新增，重開遊戲<br>改既有武器：改 JSON 後 `/mobarmory reload`，或在網頁改<br>新貼圖：網頁上傳；要內建的話放進 jar（PNG、2 個模型 JSON、`BuiltInTextures` 一行） |
| 改一處會牽動幾個檔案 | 新增一把武器：**1 個 JSON（上傳貼圖就多一張 PNG），0 行 Java** |

### SOLID 判斷、Pattern

與前一版相同：武器是資料（O）；網頁只依賴 `WeaponAdmin`，切執行緒、重算、送封包由轉接器做（S、D）；驗證只有一套（S）；定義是不可變快照（S）；讀檔經過 `WeaponStore`、測試用獨立資料夾（D）。Pattern 採 Q2 A。

## 4. 實作順序（最終版）

前幾版已經做完的部分，這一版只做差異：

1. 用 git 還原執行時資源包（`GeneratedWeaponPack`、`WeaponAssets`、`ClientResourceReload`）和上傳（`WeaponStore` 的貼圖、`WeaponEditor.saveTexture`、`PUT /api/v1/textures`、網頁的上傳）。
2. 和「照審查建議版」的部分合在一起：內建貼圖、`WeaponLooks`、定義同步。上傳的貼圖用和內建貼圖一樣的 `look/<名稱>` 模型名稱，所以兩種走同一條路。
3. 同步封包多送上傳的 PNG 和摘要；客戶端收到後放進資源包，缺圖時自動重新載入資源。
4. O10：拿掉版本號和 409。
5. 測試全過、`runClient` 走一次、行為報告 [15](15-weapon-data-web-behavior-report.md)。

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `WeaponJson`、`EquipmentLibrary`、`WeaponAssets`、`WeaponSync`（定義、上傳的 PNG、摘要、要不要重新載入）、`PngCheck`、`WeaponApi` |
| Module | `WeaponLoader`、`WeaponEditor`（存檔、後存蓋先存、上傳、新增）、`DefaultWeapons` |
| 契約 | 裝備契約＋JSON 存讀一致；`WeaponStore` 兩個實作；遷移；7 張內建貼圖 |
| System | GameTest：既有 18 個；執行中改武器；換外觀；上傳的貼圖變成手上的劍的外觀、並出現在要送給玩家的封包裡；同步封包（定義、PNG）編碼解碼；網頁存檔穿過遊戲 |
| HTTP | 安全檢查、錯誤格式、靜態網頁 |
| 手動 | `runClient`＋瀏覽器：上傳 PNG、換外觀、自動重新載入資源；兩台客戶端的手動步驟寫在 15 |
