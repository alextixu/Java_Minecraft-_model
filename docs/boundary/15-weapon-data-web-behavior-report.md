# 行為報告：R2b 資料驅動武器＋R6 網頁後台

> boundary-first 第 6 步的產出。邊界在 [08](08-weapon-data-web-boundary-note.md)、行為在 [09](09-weapon-data-web-given-when-then.md)、決定在 [10](10-weapon-data-web-open-questions.md)（使用者 2026-11-06「全部照建議」）。
> 11～13 號留給 D1 砍怪傷害顯示（`feature/damage-hud`），所以這份是 15（14 是 R4 的行為報告）。

## 1. 結果一覽

| 項目 | 結果 |
|---|---|
| 單元測試（`gradlew build`） | **225 / 225 通過**（R1／R2 時是 106，這次新增 119） |
| 遊戲內系統測試（`gradlew runGameTestServer`） | **25 / 25 通過**（我們的 24 個＋原版 1 個；R1／R2 的 18 個全部照舊通過） |
| T1（重劍讓玩家變慢） | 照舊通過：0.1 → 0.07 → 0.1，數字一個都沒改 |
| 加一把新武器要改的檔案 | **1 個 JSON＋1 張 PNG，Java 0 行**（runClient 實測：戟、紅寶石短刀） |
| 網頁改數值 → 手上的武器 | 遊戲內測試用真的 HTTP PUT：重劍攻擊力 7 → 17，不用重新拿劍 |
| `runClient` 手動走查 | 自訂貼圖已實測（第 5 節）；**網頁＋遊戲的完整走查還沒做**：當時電腦螢幕是鎖定狀態，不能操作遊戲畫面（見第 7 節） |

## 2. 結構摘要

```
core/equipment/      （純 Java）
  EquipmentDefinition   id、名稱、欄位、修飾、外觀；EquipmentDefinition.of(...) 舊寫法保留
  EquipmentLook         sealed：VanillaItem(item) | Texture(texture)
  EquipmentLibrary      物品清單啟動時固定；定義是不可變快照，整份換；版本號；待重開；檔案不見
  LoadoutCalculator     多一個建構子：每次計算讀最新快照
  ReloadReport, StaleVersionException
data/                （純 Java＋Gson，CoreBoundaryTest 守住不 import Minecraft）
  WeaponJson            格式第 1 版的讀寫，錯誤指出欄位
  WeaponStore           介面 ← WeaponFolder（config/mobarmory/）、InMemoryWeaponStore（測試）
  WeaponLoader          讀整個資料夾，一次列出所有問題
  WeaponEditor          唯一改武器的地方：reload、update、create、saveTexture（先寫檔才套用）
  WeaponAssets          依定義產生物品模型和貼圖檔
  DefaultWeapons, PngCheck
web/                 （純 Java，JDK HttpServer）
  WeaponAdmin           介面：網頁只認得它
  WeaponApi             API v1 的路由（不用開伺服器就能測）
  AdminHttpServer       靜態網頁＋安全檢查
  WebSettings           config/mobarmory/web.json
forge/
  WeaponStartup         模組載入時讀設定檔；common setup 檢查原版外觀
  ServerWeaponAdmin     implements WeaponAdmin：切到伺服器執行緒 → WeaponEditor → 重算持有者 → 必要時重新載入資源
  WebAdminLifecycle     跟著世界開關網頁；聊天欄給管理員連結
  ReloadCommand         /mobarmory reload
  EquipmentItem         名稱、tooltip 讀 EquipmentLibrary
  EquipmentItemFactory  從 EquipmentLibrary 註冊物品
  EquipmentStatApplier  多了 refreshHolders(server)
forge/client/
  GeneratedWeaponPack   記憶體裡的客戶端資源包（每次重新載入資源都重做）
  ClientResourceReload  單人遊戲換外觀後自動重新載入資源
resources/mobarmory/
  default_weapons/*.json   4 件預設（取代 content/ModEquipment）
  web/                     index.html、app.js、style.css
```

刪除：`content/ModEquipment`、4 個手寫的物品模型檔、語系檔裡 4 件裝備的名稱。

依賴方向：`web` → `WeaponAdmin`（介面）← `forge/ServerWeaponAdmin` → `data/WeaponEditor` → `core`。`web` 和 `data` 都不知道 Minecraft 的存在。

## 3. 各層測試數量

| 層級 | 全部（本輪新增） | 這次新增的內容 |
|---|---|---|
| Component | 101（+65） | `WeaponJson`、`PngCheck`、`EquipmentLibrary`、`WeaponAssets`、`WeaponApi`（每個路徑的狀態碼） |
| Module | 45（+27） | `WeaponLoader`（B1～B5 逐一破壞）、`WeaponEditor`（存檔、409、欄位、外觀、寫檔失敗、重新載入、上傳）、`DefaultWeapons`（遷移） |
| 契約 | 63（+15） | R2 的裝備契約多一條「JSON 存了再讀回來一模一樣」；`WeaponStore` 兩個實作跑同一組 |
| HTTP | 10（+10） | `AdminHttpServer`：網頁和安全標頭、別的網站 403、Host 403、非 JSON 415、太大 413、token 401、埠被佔用 |
| 邊界守衛 | 6（+2） | `core`、`content`、`data`、`web` 不 import Minecraft；正式資源裡沒有測試資料 |
| System（GameTest） | 24（+6） | 原本 18 個照舊；新增 5 個「執行中改武器」（獨立批次 `mobarmory:live_edit`）＋1 個「網頁存檔穿過遊戲」（獨立批次 `mobarmory:web_admin`） |

既有測試的修改：只把「怎麼拿到重劍」從 Java 常數改成用 id 查（`DefaultWeapons`、`Weapons`），用 `git diff` 確認預期數字一個都沒改。

**反向驗證**：暫時拿掉「重算持有者」，正好 3 個遊戲內測試失敗（預期 17 實際 7、預期 0.115 實際 0.161、預期 11 實際 7），還原後全過。

## 4. Given / When / Then 對照

| # | 情境 | 怎麼驗證 | 結果 |
|---|---|---|---|
| H1 | 第一次啟動自動建立 4 件預設，T1 不變 | 遊戲內測試每次都在清空的 `run-gametest/` 跑，log 顯示讀的是 `run-gametest/config/mobarmory/weapons`；`DefaultWeaponsTest` | 通過 |
| H2 | 只加 JSON＋PNG 就多一把武器 | `WeaponLoaderTest`、`WeaponAssetsTest`；runClient 實測戟和紅寶石短刀（第 5 節） | 通過 |
| H3 | 手改檔案＋`/mobarmory reload`，手上的劍立刻變 | `reload_applies_file_edit`（7 → 11）、`WeaponEditorTest` | 通過 |
| H4 | 網頁列出武器和數值 | `WeaponApiTest`、`AdminHttpServerTest`、瀏覽器實測（第 6 節） | 通過 |
| H5 | 網頁存檔，手上的劍立刻變，檔案寫回 | `live_edit_applies_to_sword_in_hand`（7 → 17，檔案有 16）、`web_admin_saves_through_the_game`（真的 HTTP） | 通過 |
| H6 | 殭屍手上的也跟著變 | `live_edit_applies_to_zombie`（速度 ×0.7 → ×0.5） | 通過 |
| H7 | 重開遊戲後仍是新數值 | `WeaponEditorTest.savedValueSurvivesARestart`（重新讀檔） | 通過 |
| H8 | 換外觀後單人遊戲自動重新載入資源 | 網頁換外觀在瀏覽器實測（預覽圖、版本 +1）；遊戲端呼叫 `reloadResourcePacks` 的路徑**還沒在遊戲畫面裡看過** | 部分 |
| H9 | 上傳新武器，待重開 | `WeaponEditorTest`、`WeaponApiTest`、瀏覽器實測（長矛顯示「待重開遊戲」） | 通過 |
| H10 | 使用者改過設定，測試仍用預設 | 遊戲內測試用獨立資料夾、每次清空 | 通過 |
| B1～B5 | 設定檔寫錯 → 啟動失敗並寫出檔名、欄位 | `WeaponLoaderTest`、`WeaponJsonTest` | 通過（遊戲啟動失敗的畫面沒有實際看過） |
| B6 | 重新載入時有壞檔 → 整份不換 | `reload_with_broken_file_changes_nothing`、`WeaponEditorTest`、`EquipmentLibraryTest` | 通過 |
| B7、B8 | 新檔待重開、刪檔維持到重開 | `reload_new_and_deleted_files_wait_for_restart`、`EquipmentLibraryTest` | 通過 |
| B9 | 欄位不能在執行中改 | `EquipmentLibraryTest`、`WeaponEditorTest`、`WeaponApiTest` | 通過 |
| B10 | 數值輸入錯 → 400 並標出欄位 | `WeaponApiTest`、瀏覽器實測（空白的格子變紅框，下方寫出 `modifiers[0].add`） | 通過 |
| B11 | 兩邊同時改 → 409 | `WeaponApiTest`、瀏覽器實測（另一個用戶端先存，網頁顯示「剛剛被改過」） | 通過 |
| B12 | 寫檔失敗 → 遊戲數值不變 | `WeaponEditorTest.failedWriteChangesNothing` | 通過 |
| B13 | 別的網站送請求 → 403／415 | `AdminHttpServerTest`、curl 實測（`Origin: http://evil.example` → 403） | 通過 |
| B14 | 埠被佔用 → 遊戲照常 | `AdminHttpServerTest.portInUse`（丟 IOException）；`WebAdminLifecycle` 只記警告 | 通過（遊戲端訊息沒有實際看過） |
| B15 | 遊戲暫停時網頁存檔照樣套用 | 讀原始碼確認（`waitUntilNextTick` 每圈都跑工作佇列）；沒有自動測試 | 依原始碼 |
| B16 | 背包裡沒拿著的，拿起來時就是新數值 | 換裝事件本來就會重算（R2 的 T1 測試） | 通過 |
| B17 | 怪物 API → 501 | `WeaponApiTest`、瀏覽器實測 | 通過 |
| B18 | 遠端玩家的 tooltip 和貼圖不會更新 | 已知限制，R5 處理 | 未實作 |

## 5. runClient 實測：自訂貼圖（A4）

- 設定資料夾：`run/config/mobarmory/`，多放了 `halberd.json`（攻擊力 +9、速度 −20%、貼圖 `halberd_netherite`）和 `ruby_dagger.json`（名稱「紅寶石短刀」、貼圖 `dagger_ruby`）。兩張 PNG 從 hanhanpx 素材複製進來，**不進 git**。
- log：資源包順序是 `vanilla, mod:mobarmory, mod:forge, mobarmory_weapons`（我們的在最上層），`Mob Armory: 6 stats, 6 pieces of equipment`，沒有缺模型、缺貼圖的警告。
- 遊戲裡 `/give @s mobarmory:halberd`、`mobarmory:ruby_dagger`：手上和快捷列都是正確貼圖；中文名稱正常顯示；拿戟時 `/mobarmory stats` 攻擊力 10.0（1＋9）、速度 0.068（0.1 × 0.8 × 0.85，身上還穿著之前的重甲）。
- 截圖有 hanhanpx 的圖，授權確認前不放進 repo，先放在共用筆記資料夾（`r2b-a4-custom-texture-halberd.png`）。

## 6. 瀏覽器實測：網頁（B4～B6）

當時螢幕鎖定、不能操作遊戲，所以用一個暫存的小程式（不在 repo 裡）把**同一份** `WeaponApi`、`AdminHttpServer`、`WeaponEditor` 接到 `run/config/mobarmory` 的複本上，用 Claude 的內建瀏覽器實際操作：

| 動作 | 結果 |
|---|---|
| 打開 `http://127.0.0.1:8765/` | 6 把武器，中文欄位和數值摘要（例如「攻擊力 +6、移動速度 -30%、攻擊速度 -40%」），console 沒有錯誤 |
| 重劍攻擊力 6 → 16，按「儲存並套用」 | 「已套用（版本 2）」；清單變 +16；`heavy_sword.json` 寫成 `"add": 16` |
| 外觀改成自訂貼圖 `halberd_netherite` | 版本 3，出現像素風預覽圖 |
| 另一個用戶端（curl）先存成 +12，網頁再用舊版本存 | 「沒有儲存：這把武器剛剛被改過……請按『重新整理』」；重新整理後顯示 +12、版本 4 |
| 攻擊力格子清空後儲存 | 格子紅框，下方 `modifiers[0].add：must be a number` |
| 新增武器「長矛」（`spear`、貼圖 `dagger_ruby`、攻擊力 +5） | 「已存成 spear.json，重開遊戲後就能用 /give」；清單顯示「待重開遊戲」 |
| 「從檔案重新載入」 | 成功；長矛仍是待重開 |
| 怪物分頁「查詢怪物 API」 | `501：怪物的設定要等 R4……` |
| curl 用 `Origin: http://evil.example` 送修改 | 403 |

實測時找到並修好一個問題：訊息框下方多出一行「null」。

## 7. 沒有覆蓋的範圍

- **網頁＋遊戲一起的手動走查還沒做**：要做的時候螢幕是鎖定狀態（沒有前景視窗），依規則不操作。HTTP → 伺服器執行緒 → 手上武器這條路徑，已由遊戲內測試 `web_admin_saves_through_the_game` 自動驗證；聊天欄的連結、換外觀後遊戲自動重新載入資源、遊戲暫停時存檔（B15），要在下次 runClient 時看一次。
- 傷害數字要等 D1（`damage-hud`）合併才看得到；在那之前可以用 `/data get entity <殭屍> Health` 看剩下的血。
- 遠端玩家（獨立伺服器）的 tooltip 和貼圖同步：R5。
- 網頁上的錯誤原因（例如 `must be a number`）是伺服器的英文訊息；欄位位置和整體提示是中文。
- 盔甲用自訂貼圖時，身上不畫盔甲（O12：穿在身上只能用原版材質）。

## 8. 示範影片劇本（D2）

前置：
- `run/config/mobarmory/weapons/heavy_sword.json` 是預設值（攻擊力 +6）。要還原時，從 `src/main/resources/mobarmory/default_weapons/heavy_sword.json` 複製過去，再執行 `/mobarmory reload`。
- 用「Mob Armory Demo」世界，或新建一個開作弊的世界；白天（`/time set day`）；生存模式（`/gamemode survival`）比較像真的戰鬥。
- 錄影用 NVIDIA 的 Alt+F9。
- 如果 D1 已經合併，砍怪時畫面上會有傷害數字；還沒合併的話，用下面括號裡的 `/data` 指令看殭屍剩下的血。

| 步驟 | 畫面 | 說明／預期 |
|---|---|---|
| 1 | 進世界，聊天欄出現「Mob Armory weapon admin: http://127.0.0.1:8765/」 | 網頁後台跟著世界一起開 |
| 2 | 主手拿重劍，`/mobarmory stats` | 攻擊力 7.0（1＋6） |
| 3 | `/summon zombie ~ ~ ~3 {NoAI:1b}`，等蓄力滿砍一下 | 傷害約 6.9（殭屍 2 點護甲）（`/data get entity @e[type=zombie,limit=1,sort=nearest] Health` → 約 13.1） |
| 4 | 切到瀏覽器，點聊天欄的連結或打開 `http://127.0.0.1:8765/` | 武器清單 |
| 5 | 點 Heavy Sword，攻擊力改成 16，按「儲存並套用」 | 「已套用（版本 2）……已寫回 heavy_sword.json」 |
| 6 | 回遊戲（劍不用重拿），把滑鼠移到劍上看 tooltip | 攻擊力 +16 |
| 7 | 再召喚一隻殭屍，蓄力滿砍一下 | 傷害約 16.7（`Health` 約 3.3） |
| 8 | （可選）網頁把外觀換成自訂貼圖（例如戟），儲存 | 遊戲出現幾秒的載入畫面，手上的劍變成戟 |
| 9 | （可選）打開 `config/mobarmory/weapons/heavy_sword.json` | 看到 `"add": 16` |
| 10 | 收尾 | 網頁改回 +6（或照前置步驟還原），下次錄影從頭開始 |
