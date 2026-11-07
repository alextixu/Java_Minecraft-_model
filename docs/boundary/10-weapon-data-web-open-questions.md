# 確認點的問題與決定（R2b 資料驅動武器＋R6 網頁後台）

> 狀態：**已決定**（2026-11-06）。按時間順序：「全部照建議」→「照審查建議版」→ Q7 改成新增武器表單 →「要能上傳 PNG」；最終版見下方決定總表。依 boundary-first，設計和商業規則由人決定，AI 只提案。
> R4 的 [07](07-r4-open-questions.md) 由 R4 的 session 處理，這份不影響 R4。

## 決定的經過

按時間順序：

1. **「全部照建議」**：使用者在開發 session 回「全部照建議」（記錄在 commit `18c4487`）。開發 session 照這個版本做完了 R2b 和 R6（commit `a6bd8bc`～`905554d`）。
2. **「照審查建議版」**：使用者在主 session 看了審查員的 review-01（`java_minecraft_model/reviews/review-01-requirements-gap.md`，不在 repo 裡；第 5 節風險 4、第 6 節建議 2），主 session 說明兩版的差別和一般 Forge 模組的做法後，使用者回「照審查建議版」。理由：
   - 多人同步（R5）：設定檔和貼圖只在伺服器上時，遠端客戶端的名稱、tooltip、貼圖會和伺服器不同。
   - 一般 Forge 模組把貼圖放進 jar，數值由伺服器同步給客戶端。
   開發 session 照這版重做了 R2b（commit `fba1376`）：7 張貼圖放進 jar、定義同步封包，並刪掉執行時資源包和上傳。
3. **Q7 改成新增武器表單**：使用者原話「我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點。」→ 網頁有「新增武器」表單。重點是「新增武器」和「數值」；不為武器另外加特效或規則，遇到「要不要多做某個效果」預設「不做」。
4. **「要能上傳 PNG」**：使用者原話「要能上傳 PNG」。取代第 2 點的「刪掉執行時資源包」和第 3 點的「不上傳 PNG」，其他照「照審查建議版」。開發 session 用 git 還原了執行時資源包和上傳，再和第 2 點的內建貼圖、定義同步合在一起（見 15 號報告第 2 節）。

## 決定總表（最終版，第 4 點之後）

| 題號 | 決定 | 內容 |
|---|---|---|
| Q1 | **A** | 同意 SOLID 判斷 |
| Q2 | **A** | 啟動時讀設定資料夾、每把武器一個物品；執行中只換定義；網頁走介面＋轉接器 |
| Q3 | **B（調整）** | ① 執行時產生的資源包**保留**（物品模型和上傳的貼圖）；② 補上「武器定義同步到客戶端」，**連上傳的 PNG 一起送**；③ 7 張內建貼圖放進 jar；④「不能動」加上 `content/ModStats` |
| Q4 | **A** | `config/mobarmory/weapons/`，一把一個檔；`/mobarmory reload` 和網頁按鈕；新武器重開生效 |
| Q5 | **最終版** | 外觀三種：原版物品、7 張內建貼圖（在 jar 裡）、上傳的 PNG（存在 `config/mobarmory/textures/`）。7 張內建貼圖選「放在 jar」，因為已經 commit 並附作者標註，改動最少 |
| Q6 | **A** | 只綁 `127.0.0.1`，不用密碼，檢查 `Host`／`Origin`、只收 JSON（上傳只收 PNG）；對外開放時強制 token |
| Q6（2026-11-08 改） | **A 加開關** | 使用者決定「先以ip就可以登入 因為日後可能有其他管理方式 所以目前先以ip就可以訪問就好」：web.json 寫 `"allowNetworkWithoutToken": true` 時，對外開放也可以不設 token；預設關閉，打開又沒 token 時每次啟動在 log 寫警告；`Host`／`Origin` 檢查照舊；有 token 就照 token 的規則（[prompts/round-11-web-fixes.md](prompts/round-11-web-fixes.md)） |
| Q7 | **最終版** | 網頁「新增武器」表單：id、名稱、欄位、數值、外觀（原版物品、內建貼圖、或上傳一張 PNG）；存成 `weapons/<id>.json` 和 `textures/<名稱>.png`，清單標「待重開」，重開後生效。既有武器換外觀時也可以用上傳的 PNG |
| O1～O16 | **同意**，三項例外 | **O10**：後存的蓋掉先存的，不做版本號和 409。**O11**：原版物品、內建貼圖馬上換；換成剛上傳的貼圖時，客戶端自動重新載入資源。**O15**：7 張 hanhanpx 貼圖 commit 進 repo，旁邊放作者標註；上傳的 PNG 只在遊戲資料夾，不進 repo |

**多人一致的做法（review-01 風險 4）**：伺服器在玩家登入時、以及每次改動後送出 ① 該玩家還沒有的上傳 PNG（一張一個封包）② 武器定義和每張上傳 PNG 的 SHA-256。客戶端把定義放進自己的 library（名稱、tooltip），把 PNG 放進執行時資源包；如果資源包裡少了某張或內容不同，就自動重新載入資源。武器的外觀由物品堆疊帶著的 `item_model` 決定，原版會同步。

**上傳 PNG 的驗證**：只收 PNG；正方形，邊長 16、32、64、128 或 256；最大 256 KB；名稱只能用小寫英文、數字、底線（不能有路徑），也不能和內建貼圖同名。

**2026-11-07 的兩處調整**（夜間段落 A，主 session 依 review-03 下的指令；使用者睡前授權，早上驗收）：

- **O15 的作者標註**：`CREDITS.md` 從貼圖旁邊（`assets/mobarmory/textures/item/`）搬到 `src/main/resources/mobarmory/CREDITS.md`。
  - 搬的原因：大寫的檔名在資源包裡是不合法的路徑，每次啟動 log 都有 `Invalid path in pack`（review-03 P3-9）。
  - 標註仍然在同一個 repo、同一個 jar，根目錄的 README 也有「素材來源」一節。
  - 如果要標註緊鄰貼圖，可以改成小寫的 `credits.md` 放回原處，同樣不會報錯。
- **多人一致**：
  - 玩家離開伺服器時，客戶端會清掉收到的 PNG，並從本機檔案重新載入武器定義。
  - 伺服器送 PNG 前會再跑一次 PNG 驗證（review-03 P2-8）。
  - 詳見 15 號第 3 節。

以下保留當時的問題原文。題目後面的「A（建議）」是當時的建議值，最終決定以上表為準。

## 第一輪：設計（3 題）

### Q1 SOLID 判斷

我預期照最直接的寫法，會出現這些徵兆：

- 改一個數值要改 `ModEquipment.java` 再重新 build（O）。
- 網頁的程式直接拿 Minecraft 的玩家、在網頁的執行緒改屬性（S、D）。
- JSON 載入和網頁各寫一套驗證，規則慢慢變得不一樣（S）。
- 測試讀到使用者改過的設定檔，結果時好時壞（D）。

處方：武器是設定檔裡的資料；驗證只有一套（R2 的 `EquipmentCatalog`）；定義是不可變的快照，整份驗證通過才換；網頁只認得 `WeaponAdmin` 介面，由 `forge/` 的轉接器切到伺服器執行緒；遊戲內測試用獨立資料夾。詳見 [08 第 3 節](08-weapon-data-web-boundary-note.md#solid-判斷第一輪-q1-要確認)。

- **A（建議）** 同意
- B 不是這個問題，是……（請說明）
- C 這次不要重構，直接加

### Q2 Pattern 選擇

會變的是：武器清單和數值、外觀、改定義的入口（指令、網頁，之後還有怪物）。

- **A（建議）** 啟動時讀設定資料夾，每把武器註冊一個物品（沿用 R2）；執行中只換**定義**；網頁用「介面＋轉接器」接到遊戲；外觀由執行時產生的資源包提供。每把武器有自己的 `/give mobarmory:<id>`。**新武器要重開遊戲才生效**（平台限制）；改既有武器不用重開。
- B 只註冊一個通用武器物品，每個物品身上記「是哪一把武器」。網頁可以在遊戲中直接建立新武器、不用重開，但 `/give` 要寫成 `/give @p mobarmory:weapon[mobarmory:weapon="halberd"]`，R2 的物品和測試要改寫，約多 1 天。
- C 直接寫：網頁直接改 `ModEquipment` 的靜態欄位和玩家屬性。今天最快，但有執行緒問題、網頁沒辦法不開遊戲測、重開就不見。

### Q3 變更邊界

**打算動的檔案**（不同意的請劃掉）：

- 新建 `data/`（讀寫 JSON 和 PNG）、`web/`（網頁伺服器和 API）、`forge/` 的 `ServerWeaponAdmin`、`WebAdminLifecycle`、`ReloadCommand`、`forge/client/GeneratedWeaponPack`、`resources/mobarmory/`（預設武器 JSON、網頁的 HTML／JS／CSS）
- 修改 `EquipmentDefinition`（加名稱、外觀改成「原版物品或自訂貼圖」）、`LoadoutCalculator`（只**新增**一個建構子）、`EquipmentItem`、`EquipmentItemFactory`、`EquipmentStatApplier`、`MobArmoryMod`
- 刪除 `content/ModEquipment`、`assets/mobarmory/items/*.json`、語系檔裡 4 件裝備的名稱（改由 JSON 提供）
- `build.gradle`：遊戲內測試改在 `run-gametest/` 跑，每次先清空
- 既有測試：只改「怎麼拿到重劍」（從 Java 常數改成用 id 查），**預期的數字一個都不改**；`ContentResourcesTest` 改成檢查「每把武器都產生得出模型和名稱」

**認為不能動的**（還有要補的請加上）：

- `core/stat`、`core/combat` 的公開介面和規則
- `StatAttributeBridge`、`EntityStatBinder`、`CombatEffectListener` 的行為
- `EquipmentCatalog` 的驗證規則（JSON 和網頁都經過它）
- 不加外部相依（網頁用 JDK 內建的 HttpServer）；不用 Mixin
- R4 的 05～07；`mod-damage-hud/` 資料夾

**新功能放的位置**：新武器放 `config/mobarmory/weapons/<id>.json`（加貼圖就多放一張 PNG 到 `textures/`），重開遊戲。

- **A（建議）** 同意
- B 要調整（請寫要劃掉或補上的項目）

## 第二輪：商業規則（4 題，最影響實作的）

### Q4 設定檔放哪、改了怎麼生效

- **A（建議）** 放在遊戲資料夾的 `config/mobarmory/weapons/`，一把武器一個檔。第一次啟動時自動建立 4 件預設武器。網頁改的立刻生效；手動改了檔案，用 `/mobarmory reload` 指令或網頁的「重新載入」按鈕生效。新增的武器檔要重開遊戲。
- B 同 A，再加上**自動偵測**檔案變動（存檔就生效）。比較方便，但編輯器存到一半的檔案會觸發錯誤訊息，多約 2 小時。
- C 放在 datapack（`data/<命名空間>/mobarmory/weapons/`），用原版 `/reload` 生效，每個世界可以不同。**但 datapack 加不了新武器**（物品種類只能在啟動時註冊），新武器還是要另外放一份清單。
- D 放在模組 jar 裡（`src/main/resources`）。改任何東西都要重新 build。

### Q5 自訂貼圖怎麼進遊戲

- **A（建議）** 執行時產生資源包：PNG 放在 `config/mobarmory/textures/`，模組在客戶端自己產生物品模型。加貼圖不用重新 build；網頁換外觀後，單人遊戲會自動重新載入資源（幾秒的載入畫面）。貼圖不用進 git，所以 hanhanpx 素材的授權問題不會卡住 repo。
- B 把 PNG 放進模組 jar（`src/main/resources/assets/mobarmory/textures/item/`）。最簡單，多人連線時每個客戶端都有；但每加一張圖要重新 build，而且素材要 commit 進 repo（hanhanpx 的授權還沒確認）。
- C 只能選原版外觀，不支援自訂貼圖。

### Q6 網頁的存取控制

- **A（建議）** 只綁本機 `127.0.0.1`，不用密碼。擋掉「別的網站偷偷送請求」：檢查 `Host` 和 `Origin`、只收 JSON。如果之後把位址改成對外開放，就**強制**要 token，否則網頁不啟動。
- B 永遠要 token：進世界時，聊天欄顯示一個帶 token 的連結，點了才能用。比較安全，示範時多一步。
- C 不做任何檢查。只有自己的電腦連得到，但任何網站都能叫你的瀏覽器偷改數值。

### Q7 上傳新武器（貼圖＋數值）

- **A（建議）** 這次做。網頁表單填 id、名稱、欄位、數值，上傳 PNG；伺服器驗證後存成 `weapons/<id>.json` 和 `textures/<名稱>.png`；清單顯示「待重開」，重開遊戲後就能用。排在「改數值」「換外觀」之後做，約 2～3 小時，週日前來不及就延後。
- B 這次只設計 API、不做畫面。新武器手動放 JSON 和 PNG 一樣能加。
- C 照 Q2 的 B 做通用武器物品，上傳後不用重開就能用。約多 1 天，週日前風險高。

## 其他決定（附建議值，同意就不用回）

O1 的 JSON 格式（建議）：

```json
{
  "format": 1,
  "name": "Heavy Sword",
  "slot": "mainhand",
  "look": { "item": "minecraft:netherite_sword" },
  "modifiers": [
    { "stat": "attack_damage", "add": 6 },
    { "stat": "movement_speed", "percent": -30 },
    { "stat": "attack_speed", "percent": -40 }
  ]
}
```

- 檔名就是 id（`heavy_sword.json` → `/give @p mobarmory:heavy_sword`），所以檔案裡不再寫 id。
- 自訂貼圖寫 `"look": { "texture": "halberd_netherite" }`，對應 `config/mobarmory/textures/halberd_netherite.png`。
- `add` 是加一個固定值，`percent` 是百分比（`-30` 就是 −30%）。機率類數值（暴擊率、迴避率）用 `add`，寫 0～1（`0.25` 是 25%），網頁上會顯示成百分比。
- `slot` 可以是 `mainhand`、`offhand`、`head`、`chest`、`legs`、`feet`。

| # | 問題 | 建議 | 其他選擇 |
|---|---|---|---|
| O1 | JSON 格式 | 如上：一把一個檔、檔名是 id、百分比寫 `-30`、有 `format` 版本號 | 全部放一個 `weapons.json`；百分比照程式寫 `-0.30` |
| O2 | 網頁改的要不要寫回 JSON | **寫回**：先寫暫存檔再換名（寫一半斷電不會留下壞檔），寫成功才套用到遊戲 | 只改記憶體，重開就還原 |
| O3 | 數值輸入錯了（B3、B10） | **拒絕**，標出哪一格錯；規則和讀檔時一樣。合法的數值照 R1 規則在最後夾在範圍內（例如攻擊力最多 2048） | 自動夾到範圍內 |
| O4 | 網頁技術 | JDK 內建 `HttpServer`＋純 HTML／JS（不用框架、不連外部 CDN），檔案放在模組 jar 裡 | Minecraft 內附的 Netty；加網頁框架 |
| O5 | 位址和埠 | `127.0.0.1:8765`，可以在 `config/mobarmory/web.json` 改；埠被佔用時遊戲照常，聊天欄提示（B14） | 其他埠 |
| O6 | 網頁什麼時候開 | 進世界（伺服器啟動）時開，離開世界時關；進世界時聊天欄顯示網址 | 遊戲一開就開 |
| O7 | 設定檔寫錯（B1～B5） | **啟動失敗**，log 寫出檔名、行號和原因（同 R1 的 O2）。略過的話，那種武器會從所有世界消失 | 略過那一筆、印警告 |
| O8 | 重新載入時有檔案寫錯（B6） | **整份不換**，舊數值保留，列出所有錯誤 | 只略過寫錯的那個檔 |
| O9 | 執行中武器檔被刪（B8） | 維持舊定義到重開；重開後那種武器會從所有世界消失（和移除模組物品一樣），網頁這次不做刪除 | 刪了立刻失效 |
| O10 | 兩個分頁同時改（B11） | 每把武器有版本號，過期就拒絕（409），提示重新整理 | 後存的蓋掉先存的 |
| O11 | 換外觀後畫面怎麼更新（H8） | 單人遊戲自動重新載入資源（幾秒的載入畫面） | 自己按 F3+T |
| O12 | 盔甲穿在身上的外觀 | 只能選原版材質（鐵、鎖鏈、鑽石…）；自訂 PNG 只用在物品圖示 | 也支援自訂穿戴貼圖（另外要一組貼圖，約多半天） |
| O13 | 網頁的語言 | 繁體中文介面；武器名稱照 JSON 顯示（中英文都可以） | 英文介面 |
| O14 | 遊戲內測試和設定檔（H10） | 測試在獨立資料夾 `run-gametest/` 跑，每次先清空，所以永遠從內建預設開始，也順便測到「第一次啟動自動建立設定」 | 測試改讀內建預設、略過設定檔 |
| O15 | hanhanpx 素材 | 不進 repo；只在本機測試時複製到 `run/config/mobarmory/textures/`。示範影片和報告裡寫上作者標註 `Weapon textures by HanhanPx — https://hanhanpx.itch.io/minecraft-weapon-pack` | 授權確認後 commit 幾張當範例 |
| O16 | 怪物的 API | 路徑 `/api/v1/mobs` 先回 501「R4 尚未實作」；R4 恢復後照武器的形狀加 | 先不留 |
