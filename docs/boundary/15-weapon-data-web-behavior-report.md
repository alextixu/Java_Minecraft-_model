# 行為報告：R2b 資料驅動武器＋R6 網頁後台

> boundary-first 第 6 步的產出。邊界在 [08](08-weapon-data-web-boundary-note.md)、行為在 [09](09-weapon-data-web-given-when-then.md)、決定在 [10](10-weapon-data-web-open-questions.md)（最終版：「照審查建議版」，Q7 調整為「新增武器表單，不上傳 PNG」）。
> 編號：14 是 R4 的行為報告，這份是 15。
> 狀態：**R2b 已依最終版完成**；R6 最小版接著做，完成後更新第 9、10 節。

## 1. 結果一覽（R2b）

| 項目 | 結果 |
|---|---|
| 單元測試（`gradlew build`） | **227 / 227 通過**（R1／R2 時是 106） |
| 遊戲內系統測試（`gradlew runGameTestServer`） | **28 / 28 通過**（我們的 27 個＋原版 1 個；R1／R2 的 18 個全部照舊） |
| T1（重劍讓玩家變慢） | 照舊通過：0.1 → 0.07 → 0.1，數字一個都沒改 |
| 加一把新武器 | **1 個 JSON，Java 0 行**；外觀可以用原版物品或 jar 裡的 7 張貼圖 |
| 外觀改了 | 玩家手上的武器**下一個 tick 就換**，不用重新載入資源；其他玩家同步 |
| 遠端客戶端 | 名稱、tooltip 由同步封包更新；外觀由武器帶著的 `item_model` 更新（原版同步） |
| `runClient` 實測 | 自訂貼圖、聊天欄的網頁連結、在遊戲裡即時換外觀都看過（第 7 節，截圖 18、19） |

## 2. 第一版做了、最終版刪掉或改寫的

第一版是照「全部照建議」做的（commit `a6bd8bc`～`905554d`）。改成最終版時：

| 第一版 | 最終版 | 原因 |
|---|---|---|
| `forge/client/GeneratedWeaponPack`：執行時在記憶體裡產生客戶端資源包 | **刪除** | Q3 B：遠端客戶端沒有伺服器的設定檔，看不到貼圖 |
| `data/WeaponAssets`：依定義產生物品模型 | **刪除** | 同上；模型改成 jar 裡的固定檔案 |
| `forge/client/ClientResourceReload`：換外觀後自動重新載入資源 | **刪除** | 外觀改用武器帶著的 `item_model`，不需要重新載入資源（O11） |
| 貼圖放在 `config/mobarmory/textures/`、網頁上傳 PNG | **刪除**（`WeaponStore` 的貼圖方法、`WeaponEditor.saveTexture`、`PUT /api/v1/textures`、網頁的上傳） | Q5 B：只用 jar 裡的 7 張 |
| `data/PngCheck`（上傳時檢查 PNG） | 移到測試，用來檢查 jar 裡的 7 張 | 只剩測試用得到 |
| 4 件預設的物品模型檔被刪掉 | **放回來** | 伺服器還沒替武器標上外觀前用 |
| 版本號和 409 | 還在，R6 最小版會拿掉（O10） | 屬於 R6 |

## 3. 結構摘要

```
core/equipment/      （純 Java）
  EquipmentDefinition   id、名稱、欄位、修飾、外觀；EquipmentDefinition.of(...) 舊寫法保留
  EquipmentLook         sealed：VanillaItem(item) | Texture(texture)
  EquipmentLibrary      物品清單啟動時固定；定義是不可變快照，整份換；待重開；檔案不見
  LoadoutCalculator     多一個建構子：每次計算讀最新快照
data/                （純 Java＋Gson，CoreBoundaryTest 守住不 import Minecraft）
  WeaponJson            格式第 1 版的讀寫，錯誤指出欄位
  WeaponStore           介面 ← WeaponFolder（config/mobarmory/weapons）、InMemoryWeaponStore（測試）
  WeaponLoader          讀整個資料夾，一次列出所有問題；外觀要是原版物品或內建貼圖
  WeaponEditor          唯一改武器的地方：reload、update、create（先寫檔才套用）
  BuiltInTextures       jar 裡的 7 張貼圖
  WeaponSync            定義 ↔ id → JSON；遠端客戶端套用
  DefaultWeapons
web/                 （純 Java，JDK HttpServer）WeaponAdmin、WeaponApi、AdminHttpServer、WebSettings
forge/
  WeaponStartup         模組載入時讀設定檔；common setup 檢查原版外觀
  ServerWeaponAdmin     implements WeaponAdmin：伺服器執行緒 → WeaponEditor → 重算持有者 → 更新外觀 → 送同步封包
  WeaponLooks           外觀 → item_model；讓武器帶著目前外觀
  WeaponSyncChannel     SimpleChannel 封包：玩家登入時、每次改動後
  WebAdminLifecycle、ReloadCommand、EquipmentItem（名稱、tooltip、每 tick 對齊外觀）、EquipmentItemFactory、EquipmentStatApplier
resources/
  mobarmory/default_weapons/*.json              4 件預設
  assets/mobarmory/textures/item/*.png          7 張 hanhanpx 貼圖＋CREDITS.md（作者標註）
  assets/mobarmory/models/item/look/*.json、items/look/*.json   7 種外觀的模型
  assets/mobarmory/items/<4 件預設>.json
  mobarmory/web/                                index.html、app.js、style.css
```

依賴方向：`web` → `WeaponAdmin`（介面）← `forge/ServerWeaponAdmin` → `data/WeaponEditor` → `core`。`web` 和 `data` 都不知道 Minecraft 的存在。

## 4. 給 R4、D1 的穩定介面

合併順序是 R2b 先進 main。R4、D1 請用下面這些，不要再用 `content/ModEquipment`（已刪除）：

| 要做的事 | 寫法 |
|---|---|
| 用 id 查目前的裝備定義 | `MobArmoryMod.EQUIPMENT_LIBRARY.current().find(EquipmentId.of("heavy_sword"))`（`Optional<EquipmentDefinition>`）；確定存在時用 `.get(...)` |
| 拿到物品／做一個物品堆疊 | `MobArmoryMod.EQUIPMENT.item(EquipmentId.of("heavy_sword"))`；要帶目前外觀時用 `WeaponLooks.stackOf(item, MobArmoryMod.EQUIPMENT_LIBRARY)` |
| 讓生物穿上裝備後數值生效 | `setItemSlot(...)` 之後原版的換裝事件會自動套用；要立刻套用就呼叫 `MobArmoryMod.EQUIPMENT_STATS.refresh(entity)` |
| 數值登記表 | `content/ModStats`（本輪沒有動，R4 加最大生命值時不會衝突） |

`EquipmentCatalog` 的 `find`、`get`、`all` 的簽名和 R2 時相同。

## 5. 各層測試數量

| 層級 | 全部 | 本輪（R2b＋R6）新增或改寫的內容 |
|---|---|---|
| Component | 101 | `WeaponJson`、`EquipmentLibrary`、`WeaponSync`、`PngCheck`、`WeaponApi` |
| Module | 44 | `WeaponLoader`（B1～B5 逐一破壞）、`WeaponEditor`、`DefaultWeapons`（遷移） |
| 契約 | 66 | 裝備契約多一條「JSON 存了再讀回來一模一樣」；`WeaponStore` 兩個實作同一組；**7 張內建貼圖**：PNG 是 2 的次方的正方形、模型和物品模型定義都在、作者標註列出每一張 |
| HTTP | 10 | `AdminHttpServer` 的安全檢查 |
| 邊界守衛 | 6 | `core`、`content`、`data`、`web` 不 import Minecraft；正式資源裡沒有測試資料 |
| System（GameTest） | 27 | 原本 18 個照舊；執行中改武器 6 個（獨立批次）；網頁存檔穿過遊戲 1 個；同步封包編碼解碼、新的武器堆疊帶著外觀 2 個 |

既有測試的修改只有「怎麼拿到重劍」（Java 常數 → 用 id 查），預期數字一個都沒改。

反向驗證（第一版時做的，最終版沒有改到這段）：拿掉「重算持有者」後，正好 3 個遊戲內測試失敗。

## 6. Given / When / Then 對照（R2b 的部分）

| # | 情境 | 怎麼驗證 | 結果 |
|---|---|---|---|
| H1 | 第一次啟動自動建立 4 件預設，T1 不變 | 遊戲內測試每次在清空的 `run-gametest/` 跑；`DefaultWeaponsTest` | 通過 |
| H2 | 只加 JSON 就多一把武器（外觀用內建貼圖） | `WeaponLoaderTest`；runClient 實測戟和紅寶石短刀（截圖 18） | 通過 |
| H3 | 手改檔案＋`/mobarmory reload`，手上的劍立刻變 | `reload_applies_file_edit`（7 → 11） | 通過 |
| H6 | 殭屍手上的也跟著變 | `live_edit_applies_to_zombie` | 通過 |
| H8 | 換外觀，手上的武器馬上換，不用重新載入資源 | `look_change_updates_held_sword`；runClient 用網頁 API 把重劍換成血鐮刀（截圖 19） | 通過 |
| H10 | 使用者改過設定，測試仍用預設 | 獨立資料夾、每次清空 | 通過 |
| B1～B5 | 設定檔寫錯 → 啟動失敗並寫出檔名、欄位；貼圖不是內建的 7 張 → 列出可以用的 | `WeaponLoaderTest`、`WeaponJsonTest` | 通過 |
| B6～B9 | 重新載入有壞檔整份不換；新檔待重開；刪檔維持到重開；欄位要重開 | 遊戲內測試、`EquipmentLibraryTest`、`WeaponEditorTest` | 通過 |
| B16 | 背包裡的武器，拿起來就是新數值和新外觀 | 換裝事件重算數值；`inventoryTick` 每 tick 對齊外觀 | 通過 |
| B18 | 遠端玩家看到的名稱、tooltip、外觀和伺服器一樣 | `WeaponSyncTest`、`sync_packet_round_trip`（真的編碼、解碼、套用到另一份定義） | 通過（兩個真的客戶端還沒測，見第 8 節） |
| B19 | 客戶端少了伺服器有的武器 → 連不上 | 平台限制，寫在 08 | 未實作 |

## 7. runClient 實測（R2b，2026-11-06 晚上）

- 進世界時聊天欄出現「Mob Armory weapon admin: http://127.0.0.1:8765/」（藍色連結）。
- `/give` 戟（`halberd`，外觀 `halberd_netherite`）和紅寶石短刀（`ruby_dagger`，外觀 `dagger_ruby`）：jar 裡**沒有**這兩個 id 的模型檔，靠伺服器替武器標上外觀的 `item_model`，手上和快捷列都顯示正確貼圖；中文名稱正常（截圖 [18](screenshots/18-r2b-jar-textures-and-admin-link.png)）。
- 手上拿重劍，用網頁 API（`PUT /api/v1/weapons/heavy_sword`）把外觀換成內建貼圖 `scythe_blood`：不到一秒，手上的劍和快捷列都變成血鐮刀，沒有載入畫面（截圖 [19](screenshots/19-r2b-look-change-live.png)）。之後改回獄髓劍，`heavy_sword.json` 也寫回預設值。

## 8. 沒有覆蓋的範圍

- 兩個真的客戶端連同一台伺服器（R5）：這次只用遊戲內測試證明封包能編碼、解碼，並讓另一份定義和伺服器一致。
- 掉在地上、放在箱子或物品展示框裡的武器，要等被玩家拿進背包才會換外觀；生物手上的在每次改動時一起更新。
- 客戶端物品清單和伺服器不同時（B19）連不上，這次不處理。
- 盔甲用內建貼圖時，身上不畫盔甲（O12）。
- `run/config/mobarmory/textures/` 是第一版留下的本機資料夾，最終版不再讀它（不在 git 裡）。

## 9. R6 網頁後台（最小版，進行中）

第一版的網頁已經能列出、修改、換外觀、新增武器，也做過瀏覽器實測和安全檢查。最小版要做的：拿掉版本號和 409（O10）、確認新增武器表單（不上傳 PNG）、`runClient` 走一次。完成後更新本節。

## 10. 示範影片劇本（D2，R6 完成後定稿）

前置：
- `run/config/mobarmory/weapons/heavy_sword.json` 是預設值（攻擊力 +6）。要還原時，從 `src/main/resources/mobarmory/default_weapons/heavy_sword.json` 複製過去，再執行 `/mobarmory reload`。
- 白天（`/time set day`）、生存模式（`/gamemode survival`）。錄影用 NVIDIA 的 Alt+F9。
- D1 還沒合併時，用括號裡的 `/data` 指令看殭屍剩下的血。

| 步驟 | 畫面 | 說明／預期 |
|---|---|---|
| 1 | 進世界，聊天欄出現網頁後台的連結 | 網頁後台跟著世界一起開 |
| 2 | 主手拿重劍，`/mobarmory stats` | 攻擊力 7.0（1＋6） |
| 3 | `/summon zombie ~ ~ ~3 {NoAI:1b}`，等蓄力滿砍一下 | 傷害約 6.9（殭屍 2 點護甲）（`/data get entity @e[type=zombie,limit=1,sort=nearest] Health` → 約 13.1） |
| 4 | 點聊天欄的連結，打開網頁 | 武器清單 |
| 5 | 點 Heavy Sword，攻擊力改成 16，按「儲存並套用」 | 顯示已套用，已寫回 heavy_sword.json |
| 6 | 回遊戲（劍不用重拿），看 tooltip | 攻擊力 +16 |
| 7 | 再召喚一隻殭屍，蓄力滿砍一下 | 傷害約 16.7（`Health` 約 3.3） |
| 8 | 網頁把外觀換成內建貼圖（例如血鐮刀），儲存 | 手上的劍馬上變成鐮刀，沒有載入畫面 |
| 9 | 網頁「新增武器」：id `ruby_spear`、名稱、攻擊力、外觀選 `spear_iron` | 清單顯示「待重開」；重開遊戲後 `/give @p mobarmory:ruby_spear` |
| 10 | 收尾 | 網頁改回 +6、外觀改回獄髓劍 |
