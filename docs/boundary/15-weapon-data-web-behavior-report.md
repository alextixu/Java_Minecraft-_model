# 行為報告：R2b 資料驅動武器＋R6 網頁後台

> boundary-first 第 6 步的產出。邊界在 [08](08-weapon-data-web-boundary-note.md)、行為在 [09](09-weapon-data-web-given-when-then.md)、決定在 [10](10-weapon-data-web-open-questions.md)。
> 最終版的決定（按時間順序）：「全部照建議」→「照審查建議版」→ Q7 改成新增武器表單 →「要能上傳 PNG」。
> 編號：14 是 R4 的行為報告，這份是 15。

## 1. 結果一覽

| 項目 | 結果 |
|---|---|
| 單元測試（`gradlew build`） | **237 / 237 通過**（R1／R2 時是 106） |
| 遊戲內系統測試（`gradlew runGameTestServer`） | **29 / 29 通過**（我們的 28 個＋原版 1 個；R1／R2 的 18 個全部照舊） |
| T1（重劍讓玩家變慢） | 照舊通過：0.1 → 0.07 → 0.1，數字一個都沒改 |
| 加一把新武器 | **1 個 JSON（上傳貼圖就多一張 PNG），Java 0 行**；也可以在網頁「新增武器」 |
| 外觀 | 原版物品、7 張內建貼圖（jar）、上傳的 PNG（`config/mobarmory/textures/`） |
| 換外觀 | 原版物品、內建貼圖：手上的武器**下一個 tick 就換**。剛上傳的貼圖：客戶端自動重新載入資源（幾秒）後顯示 |
| 多人一致 | 名稱、tooltip、上傳的 PNG 由封包送給客戶端；外觀由武器帶著的 `item_model` 同步 |
| 兩次存檔 | 後存的蓋掉先存的（O10），不做版本號和 409 |
| `runClient` 實測 | 內建貼圖、聊天欄連結、即時換外觀：看過（截圖 18、19）。**上傳 PNG 後自動重新載入資源：還沒在遊戲畫面裡看過**（第 8 節） |

## 2. 各版本做了什麼、最後留下什麼

| 東西 | 第一版（全部照建議） | 照審查建議版 | 最終版（要能上傳 PNG） |
|---|---|---|---|
| 執行時產生的資源包 `GeneratedWeaponPack`、`WeaponAssets`、`ClientResourceReload` | 有 | 刪除 | **用 git 還原**，改成依外觀產生（見下） |
| 上傳 PNG（`WeaponStore` 的貼圖、`WeaponEditor.saveTexture`、`PUT /api/v1/textures`、網頁） | 有 | 刪除 | **用 git 還原**；名稱不能和內建貼圖相同 |
| 7 張 hanhanpx 內建貼圖 | 沒有（只放本機） | 放進 jar，附 `CREDITS.md` | **維持放在 jar**：已經 commit 並有作者標註，對現有程式改動最小 |
| 武器帶著目前外觀的 `item_model`（`WeaponLooks`） | 沒有（靠重新載入資源） | 新增 | 保留；上傳的貼圖也用 `mobarmory:look/<名稱>`，和內建貼圖走同一條路 |
| 定義同步封包（`WeaponSync`、`WeaponSyncChannel`） | 沒有 | 新增 | **多送上傳的 PNG（一張一個封包）和摘要** |
| 版本號和 409 | 有 | 有 | **拿掉**（O10） |

執行時資源包的內容改成：每把武器的 `items/<id>.json`（指向它外觀的模型），以及每張正在用的上傳貼圖的 PNG、模型、物品模型定義。內建貼圖的這些檔案在 jar 裡，不重複產生。

## 3. 多人一致怎麼做到（review-01 風險 4）

1. 玩家登入時、以及每次改動（網頁存檔、上傳、`/mobarmory reload`）後，伺服器對每位玩家送出：
   - 該玩家還沒拿到的上傳 PNG（`Texture` 封包，一張一個，避開 Minecraft 的封包大小上限）；
   - 武器定義和每張上傳 PNG 的 SHA-256（`Definitions` 封包）。
2. 遠端客戶端：
   - 把定義放進自己的 library → 名稱、tooltip 和伺服器一樣；
   - 把 PNG 放進記憶體（`ClientTextures`），執行時資源包優先用它；
   - 如果資源包裡少了某張、或內容不同（比對摘要），自動重新載入資源。
3. 單人遊戲：伺服器和客戶端共用同一份 library 和同一個資料夾，只跑「要不要重新載入資源」的比對。
4. 武器的外觀由物品堆疊帶著的 `item_model` 決定，原版會同步給所有人。

**限制**：兩邊的 `config/mobarmory/weapons/` 要有同一組武器 id（物品清單不同時 Minecraft 不讓連線）；數值、名稱、外觀、上傳的 PNG 不用一樣。

**手動驗證步驟**（還沒做，需要使用者先同意 Minecraft 的 EULA，見第 8 節）：
1. `run/eula.txt` 改成 `eula=true`（使用者自己改），`gradlew runServer` 開獨立伺服器。
2. 開兩個 `runClient`（第二個要另一個資料夾或另一台電腦，`config/mobarmory/weapons/` 和伺服器同一組 id），都連 `localhost`。
3. 在伺服器那台開 `http://127.0.0.1:8765/`，上傳一張 PNG，把重劍外觀換成它、攻擊力改成 16。
4. 兩個客戶端：tooltip 都是 +16、名稱一樣；畫面出現幾秒的載入後，手上的重劍都變成上傳的圖。

## 4. 給 R4、D1 的穩定介面

合併順序是 R2b 先進 main。請用下面這些，不要再用 `content/ModEquipment`（已刪除）：

| 要做的事 | 寫法 |
|---|---|
| 用 id 查目前的裝備定義 | `MobArmoryMod.EQUIPMENT_LIBRARY.current().find(EquipmentId.of("heavy_sword"))`（`Optional<EquipmentDefinition>`）；確定存在時用 `.get(...)` |
| 拿到物品／做一個帶外觀的物品堆疊 | `MobArmoryMod.EQUIPMENT.item(EquipmentId.of("heavy_sword"))`；`WeaponLooks.stackOf(item, MobArmoryMod.EQUIPMENT_LIBRARY)` |
| 讓生物穿上裝備後數值生效 | `setItemSlot(...)` 後原版換裝事件會套用；要立刻套用就呼叫 `MobArmoryMod.EQUIPMENT_STATS.refresh(entity)` |
| 數值登記表 | `content/ModStats`（本輪沒有動） |

`EquipmentCatalog` 的 `find`、`get`、`all` 的簽名和 R2 時相同。

## 5. 結構摘要

```
core/equipment/   EquipmentDefinition、EquipmentLook、EquipmentLibrary（不可變快照，後存蓋先存）、LoadoutCalculator、ReloadReport
data/             WeaponJson、WeaponStore／WeaponFolder（武器檔＋上傳的貼圖）、WeaponLoader、WeaponEditor、
                  BuiltInTextures、PngCheck、WeaponAssets、WeaponSync、DefaultWeapons
web/              WeaponAdmin（介面）、WeaponApi、AdminHttpServer、WebSettings
forge/            ServerWeaponAdmin、WeaponLooks、WeaponSyncChannel、WebAdminLifecycle、ReloadCommand、
                  EquipmentItem、EquipmentItemFactory、EquipmentStatApplier、WeaponStartup
forge/client/     GeneratedWeaponPack、ClientTextures、ClientResourceReload
```

依賴方向：`web` → `WeaponAdmin` ← `forge/ServerWeaponAdmin` → `data/WeaponEditor` → `core`。`web`、`data` 不 import Minecraft（`CoreBoundaryTest` 守住）。

## 6. 各層測試數量

| 層級 | 全部 | 本輪（R2b＋R6）的內容 |
|---|---|---|
| Component | 105 | `WeaponJson`、`EquipmentLibrary`（含後存蓋先存）、`WeaponAssets`、`WeaponSync`（含上傳 PNG、摘要、要不要重新載入）、`PngCheck`、`WeaponApi` |
| Module | 46 | `WeaponLoader`（B1～B5；內建貼圖不用上傳）、`WeaponEditor`（存檔、後存蓋先存、上傳、和內建貼圖同名被拒）、`DefaultWeapons`（遷移） |
| 契約 | 70 | 裝備契約＋JSON 存讀一致；`WeaponStore` 兩個實作（含貼圖）；7 張內建貼圖完整、有作者標註 |
| HTTP | 10 | `AdminHttpServer` 的安全檢查 |
| 邊界守衛 | 6 | `core`、`content`、`data`、`web` 不 import Minecraft；正式資源裡沒有測試資料 |
| System（GameTest） | 28 | 原本 18 個；執行中改武器 7 個（含換外觀、**上傳的貼圖變成手上的劍的外觀並出現在要送給玩家的封包裡**）；網頁存檔穿過遊戲 1 個；同步封包（定義、PNG）編碼解碼、新的武器堆疊帶著外觀 2 個 |

既有測試只改了「怎麼拿到重劍」，預期數字一個都沒改。

## 7. Given / When / Then 對照

| # | 情境 | 怎麼驗證 | 結果 |
|---|---|---|---|
| H1 | 第一次啟動自動建立 4 件預設，T1 不變 | 遊戲內測試在清空的 `run-gametest/` 跑；`DefaultWeaponsTest` | 通過 |
| H2 | 只加 JSON 就多一把武器 | `WeaponLoaderTest`；runClient（截圖 18） | 通過 |
| H3 | 手改檔案＋`/mobarmory reload` | `reload_applies_file_edit`（7 → 11） | 通過 |
| H4 | 網頁列出武器 | `WeaponApiTest`、`AdminHttpServerTest`、第一版的瀏覽器實測 | 通過 |
| H5 | 網頁存檔，手上的劍立刻變，寫回檔案 | `live_edit_applies_to_sword_in_hand`（7 → 17）、`web_admin_saves_through_the_game`（真的 HTTP） | 通過 |
| H6 | 殭屍手上的也跟著變 | `live_edit_applies_to_zombie` | 通過 |
| H7 | 重開後仍是新數值 | `WeaponEditorTest` | 通過 |
| H8 | 換外觀 | 內建貼圖：`look_change_updates_held_sword`、runClient（截圖 19）。上傳的貼圖：`uploaded_texture_reaches_players`（伺服器端）；客戶端重新載入**還沒在畫面裡看過** | 部分 |
| H9 | 新增武器（可上傳 PNG），待重開 | `WeaponEditorTest`、`WeaponApiTest`；網頁表單在第一版的瀏覽器實測過 | 通過 |
| H10 | 測試不受使用者設定影響 | 獨立資料夾 | 通過 |
| B1～B9 | 設定檔寫錯、重新載入、新檔、刪檔、欄位 | `WeaponLoaderTest`、`WeaponJsonTest`、`EquipmentLibraryTest`、遊戲內測試 | 通過 |
| B10 | 輸入錯 → 400 標出欄位 | `WeaponApiTest`、第一版的瀏覽器實測 | 通過 |
| B11 | 兩邊同時改 → 後存的蓋掉先存的 | `EquipmentLibraryTest`、`WeaponEditorTest`、`WeaponApiTest` | 通過 |
| B12 | 寫檔失敗 → 遊戲不變 | `WeaponEditorTest` | 通過 |
| B13、B14 | 別的網站的請求 403／415；埠被佔用 | `AdminHttpServerTest` | 通過 |
| B15 | 遊戲暫停時存檔照樣套用 | 讀原始碼確認 | 依原始碼 |
| B17 | 怪物 API → 501 | `WeaponApiTest` | 通過 |
| B18 | 遠端玩家和伺服器一樣 | `WeaponSyncTest`、`sync_packet_round_trip`、`uploaded_texture_reaches_players` | 通過（兩台真的客戶端還沒測） |
| B19 | 物品清單不同連不上 | 平台限制 | 未實作 |
| B20 | 上傳的不是可用的 PNG、名稱有路徑或和內建同名 → 400 | `WeaponEditorTest`、`WeaponApiTest`、`PngCheckTest` | 通過 |

## 8. 沒有覆蓋的範圍

- **上傳 PNG → 客戶端自動重新載入資源 → 畫面顯示**：程式路徑有單元測試（要不要重新載入的判斷、資源包的內容）和遊戲內測試（伺服器端），但遊戲畫面還沒看過。要做時電腦螢幕是鎖定狀態，依規則不操作。第一版曾在遊戲裡看過「執行時資源包裡的上傳貼圖」能正常顯示。
- **兩台真的客戶端**（R5）：手動步驟寫在第 3 節。開獨立伺服器要先同意 Minecraft 的 EULA，這要使用者自己做。
- 掉在地上、放在箱子或展示框裡的武器，要等被拿進背包才換外觀。
- 盔甲用貼圖時，身上不畫盔甲（O12）。
- 網頁上的錯誤原因是伺服器的英文訊息；欄位位置和提示是中文。

小決定（選最簡單的做法）：
- 上傳的貼圖一律做成「像劍一樣拿」的模型（盔甲的圖示也一樣）。
- 上傳的貼圖不能和內建貼圖同名，避免同一個名稱有兩張圖。
- 沒有刪除上傳貼圖的功能；不用的 PNG 留在資料夾裡。
- 遠端客戶端收到的 PNG 只放在記憶體，不寫進它自己的 `config/`。

## 9. runClient 實測紀錄

- **最終版之前（照審查建議版，程式路徑相同）**：聊天欄出現網頁連結；只靠 JSON 定義的戟、紅寶石短刀顯示 jar 裡的貼圖；用網頁 API 把手上的重劍換成血鐮刀，不到一秒就換（截圖 [18](screenshots/18-r2b-jar-textures-and-admin-link.png)、[19](screenshots/19-r2b-look-change-live.png)）。
- **第一版**：執行時資源包裡、從 `config/mobarmory/textures/` 來的戟和紅寶石短刀在遊戲裡顯示正確；網頁在瀏覽器實測過列表、存檔、換外觀、新增武器、錯誤標示、怪物 501、別的網站被擋。

## 10. 示範影片劇本（D2）

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
| 5 | 點 Heavy Sword，攻擊力改成 16，按「儲存並套用」 | 顯示已套用、已寫回 heavy_sword.json |
| 6 | 回遊戲（劍不用重拿），看 tooltip | 攻擊力 +16 |
| 7 | 再召喚一隻殭屍，蓄力滿砍一下 | 傷害約 16.7（`Health` 約 3.3） |
| 8 | 網頁把外觀換成內建貼圖（例如血鐮刀），儲存 | 手上的劍馬上變成鐮刀 |
| 9 | 網頁上傳一張 PNG，把外觀換成它，儲存 | 幾秒的載入畫面後，手上的劍變成上傳的圖 |
| 10 | 網頁「新增武器」：id、名稱、攻擊力、外觀（可上傳 PNG） | 清單顯示「待重開」；重開遊戲後 `/give @p mobarmory:<id>` |
| 11 | 收尾 | 網頁改回 +6、外觀改回獄髓劍 |

## 11. SOLID／Pattern 檢查

這一節是 2026-11-07 補的，依長期規定第 3 點：每份行為報告都要有這一節。內容逐條對照 R4、D1 合併後 main 上的程式（`d2382b9`）。R2b＋R6 的程式在合併時沒有被改過。

### SOLID

| 原則 | 對照的程式 | 判斷 |
|---|---|---|
| S 單一職責 | `WeaponJson` 只管 JSON 格式；`WeaponLoader` 只管讀整個資料夾和檢查外觀；`WeaponEditor` 只管「先寫檔，再換定義」；`EquipmentLibrary` 只管目前的定義快照；`ServerWeaponAdmin` 只管切到伺服器執行緒，改完後重算數值、換外觀、送封包；`WeaponApi` 只管 HTTP 路由和 JSON；`AdminHttpServer` 只管安全檢查 | 符合 |
| S（注意） | `MobArmoryMod` 是組裝根，R2b、R4、D1 都在這裡加欄位和註冊（現在有 10 個共用元件欄位，從 `STATS` 到 `HIT_FEEDBACK`）。它只負責組裝、沒有規則，所以不算違反；再加功能時可以考慮按功能拆成幾個註冊類別 | 注意，沒有違反 |
| O 開放封閉 | 加一把武器：1 個 JSON，0 行 Java。加一張內建貼圖：PNG、2 個模型 JSON、`BuiltInTextures.NAMES` 一行。加一種數值：`ModStats` 一行，武器 JSON 和網頁表單會自動出現（R4 的 `max_health` 就是這樣） | 符合 |
| O（注意） | 外觀種類是 sealed 的 `EquipmentLook`（原版物品、貼圖，Q5 決定的封閉集合）。`WeaponJson`、`WeaponLoader`、`WeaponAssets`、`WeaponLooks` 用窮舉的 `switch`，`WeaponAssets`、`WeaponSync`、`EquipmentItemFactory` 用 `instanceof`。加第三種外觀要改這 6 個檔案，但窮舉 `switch` 漏改時編譯器會報錯。外觀種類是使用者決定的封閉集合，所以這裡選 sealed 加 switch，不另外抽 Strategy | 刻意的取捨 |
| L 替換 | `WeaponStore` 有兩個實作：`WeaponFolder`（正式）和 `InMemoryWeaponStore`（測試），用同一組參數化契約測試（`WeaponStoreContractTest`）。`WeaponAdmin` 有兩個實作：`ServerWeaponAdmin`（正式）和 `TestWeaponAdmin`（`WeaponApiTest` 用） | 符合 |
| I 介面隔離 | `WeaponAdmin` 有 9 個方法，只有網頁一個使用者，而且每個方法都用到；`WeaponStore` 只有讀寫武器檔和貼圖；`EquipmentLook` 沒有方法，只是資料 | 符合 |
| D 依賴反轉 | `web` 只認得 `WeaponAdmin`，不 import `forge` 或 Minecraft；`data`、`web`、`core` 不 import Minecraft，由 `CoreBoundaryTest` 守住；`WeaponEditor` 依賴 `WeaponStore` 介面；`LoadoutCalculator` 依賴 `Supplier<EquipmentCatalog>`，不依賴 library 本身 | 符合 |

### Pattern

| Pattern | 在哪裡 | 為什麼 |
|---|---|---|
| 不可變快照 | `EquipmentLibrary`：`current()`、`view()` 每次都是新的不可變物件，`update`、`replaceAll` 整份換掉 | 網頁執行緒、伺服器執行緒、R4 的怪物同時讀，不用鎖也不會讀到改到一半的定義 |
| Adapter | `ServerWeaponAdmin`（`WeaponAdmin` → 伺服器執行緒＋Minecraft）、`GeneratedWeaponPack`（`WeaponAssets` 的輸出 → Minecraft 的 `PackResources`）、`WeaponSyncChannel`（`WeaponSync` → Forge 的封包） | 純 Java 的 `data`、`web` 不用知道 Minecraft |
| Strategy（介面＋可替換實作） | `WeaponStore`：資料夾版、記憶體版 | 測試不碰使用者的設定資料夾（O14） |
| Factory | `EquipmentItemFactory`：每個定義做一個物品 | 啟動時從 JSON 決定有哪些物品，Java 不寫死 |
| Builder | 沿用 R1 的 `StatCatalog` builder（`ModStats`） | 本輪沒有新增 |
| 沒用的 | Observer：改完後要做的三件事（重算數值、換外觀、送封包）直接寫在 `ServerWeaponAdmin.afterChange`。只有一個地方會改定義，用 Observer 只是多一層 | 刻意不抽象 |

### 結論

沒有發現違反。兩個「注意」是已知的取捨，不需要現在改：組裝根變大、外觀種類用 sealed switch。
