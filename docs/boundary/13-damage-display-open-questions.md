# 確認點的問題與決定（砍怪傷害顯示）

> 狀態：**已決定**（2026-11-06）。使用者照審查員的建議（`reviews/review-01-requirements-gap.md` 的 X3：「只做 action bar，之後 combo 名稱也可以顯示在同一行」）縮小範圍，由監控 session 轉達。
> 依 boundary-first，設計和商業規則由人決定，AI 只提案。
> Q3、Q4 不是 A，所以 [11](11-damage-display-boundary-note.md) 和 [12](12-damage-display-given-when-then.md) 已依答案重寫：拿掉浮字和 `HitJournal`，只做 action bar。

## 決定總表

| 題號 | 決定 | 內容 |
|---|---|---|
| Q1 | **A** | 同意 SOLID 判斷 |
| Q2 | **A** | Observer：`CombatEffectListener` 只加通知掛勾，不改行為 |
| Q3 | **B（調整）** | **劃掉 `FloatingTextDisplay` 和 `HitJournal`**，其他同意 |
| Q4 | **C** | **只做 action bar** |
| Q5 | **A 的 action bar 部分** | action bar 只給相關的玩家：攻擊的玩家和被打的玩家 |
| Q6 | **A 的 action bar 部分** | 目標名稱、最終傷害、CRIT 或 MISS、對方剩餘血量／最大血量，例如「Zombie −6.9 ❤ 13.1/20」 |
| Q7 | **A** | 所有生物造成的傷害（近戰、投射物），環境傷害不顯示 |
| Q8 | **A** | 伺服器判定，用原版的系統訊息封包送出，不寫自訂封包和客戶端程式 |
| O1～O10 | **照建議** | 只和浮字有關的部分不適用（O7 整題）。O3 保留「打死時剩餘血量顯示 0」 |

### 決定帶來的改變

- **拿掉浮字**：Q4 C。11 的 `FloatingTextDisplay`、技術風險 T1、T2（`text_display` 的 NBT、存檔殘留），12 的 B13～B15（浮字消失、存檔、疊字）都刪掉。
- **拿掉 `HitJournal`**：Q3 B。「暴擊標記只屬於這一下」（12 的 B8、B9）是正確性要求，仍然要做到、要測。改成 `HitFeedbackListener` 自己記「每個目標最近一次的攻擊結算」：每一次攻擊結算（不論有沒有暴擊）都會蓋掉上一次，所以舊的暴擊標記不會留給下一下。不另外做成類別。
- **一次只看得到一行**：action bar 下一則會蓋掉上一則。橫掃打到好幾隻、或很多箭同時射中時，畫面上只會留下最後一下（12 的 B10、B13 改寫成這個結果）。這是 Q4 C 的取捨。
- **怪物互打沒有人看得到**：Q5 只給相關的玩家，所以沒有玩家參與的攻擊不顯示（12 的 B6）。

以下保留當時的問題原文，每題都已標上決定。

## 第一輪：設計（3 題）

### Q1 SOLID 判斷

我預期照最直接的寫法（在 `CombatEffectListener` 算完傷害就順手生成浮字），會出現三個徵兆：

- 戰鬥規則和畫面混在一起，改浮字顏色也要動 R1／R2 的戰鬥程式（S）。
- 每多一種顯示方式（action bar、聊天、網頁紀錄）就多一段 `if`（O）。
- 「誰看得到」「小數幾位」這些規則直接用 Minecraft 類別寫，不開遊戲沒辦法測（D）。

處方：戰鬥只「交出結果」；顯示規則和格式放 `core/feedback`（純 Java）；每種顯示方式一個 `HitDisplay` 實作。詳見 [11 第 3 節](11-damage-display-boundary-note.md#solid-判斷第一輪-q1已確認)。

- **A（建議）** 同意　← **決定**
- B 不是這個問題，是……（請說明）
- C 這次不要重構，直接加

### Q2 Pattern 選擇

會變的是：顯示方式、顯示規則和格式。傷害怎麼算不變。

- **A（建議）** Observer：`CombatEffectListener` 只多一個「結果通知」掛勾（不改規則）；新的 `HitFeedbackListener` 在 `LivingDamageEvent` 拿扣完護甲的最終傷害，通知每個 `HitDisplay`（浮字、action bar）。怪物和投射物的暴擊也標得出來；之後 R6 網頁要戰鬥紀錄，也是多一個 `HitDisplay`。　← **決定**
- B 完全不碰 `CombatEffectListener`，只用 Forge 的監看式監聽推斷。既有檔案零改動，但**怪物和投射物的暴擊看不出來**（我們是直接把數字乘 1.5），其他原因取消的攻擊也會被當成 MISS。
- C 直接寫、不抽象：顯示程式寫進 `CombatEffectListener`。今天最快，但戰鬥和畫面混在一起。

### Q3 變更邊界

**打算動的檔案**（不同意的請劃掉）：

- 新建 `core/feedback/`（`HitReport`、`HitFilter`、`HitText`、~~`HitJournal`~~）、`forge/feedback/`（`HitFeedbackListener`、`HitDisplay`、~~`FloatingTextDisplay`~~、`ActionBarDisplay`）、`forge/CombatObserver`
- `forge/CombatEffectListener`：只加「通知 observer」的幾行，不傳 observer 時行為和現在完全一樣
- `forge/MobArmoryMod` 加接線；`lang/en_us.json` 加 MISS、CRIT 和 action bar 的字串
- `src/test` 加 `core/feedback` 的單元測試；`src/gametest` 加一個測試類別、`MobArmoryGameTests` 註冊一行、`test_instance/*.json`

**認為不能動的**（還有要補的請加上）：

- R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat` 的公開介面和規則（暴擊 1.5 倍、迴避只閃生物造成的傷害、暴擊要蓄滿）
- `content/ModStats`、`content/ModEquipment`（開發 session 正在改成 JSON）、`EquipmentStatApplier`、`StatsCommand`
- `build.gradle`、Minecraft 和 Forge 本身（不用 Mixin、不用 Access Transformer）
- `mod/` 資料夾、`PROGRESS.md`、`transcripts/`

**新功能放的位置**：新的顯示方式放 `forge/feedback`（一個 `HitDisplay` 實作）；顯示規則和格式放 `core/feedback`。

- **A（建議）** 同意
- B 要調整（請寫要劃掉或補上的項目）　← **決定**：劃掉 `FloatingTextDisplay` 和 `HitJournal`，其他同意

監控 session 另外提醒（2026-11-06）：開發 session 的 R2b 會先合進 main，會刪掉 `content/ModEquipment`、修改 `MobArmoryMod`；`docs/boundary/README.md` 由開發 session 維護。所以本功能盡量不碰 `content/` 和裝備相關的程式，系統測試也不引用 `ModEquipment`，README 不改。

## 第二輪：商業規則（5 題）

### Q4 顯示方式

- **A（建議）** 兩種都要：**被打的生物頭上冒浮字**（原版的文字顯示實體，約 1 秒往上飄後消失），加上**畫面下方的 action bar** 一行詳細資訊。浮字讓影片一眼看出數字；action bar 寫出目標名稱和剩餘血量，而且被打的玩家在第一人稱看不到自己頭上的字，只能靠 action bar 看到 MISS。
- B 只有浮字。最簡單；但玩家穿迴避胸甲被打時，自己看不到 MISS。
- C 只有 action bar。一次只看得到最後一下，打多隻怪時看不出是哪隻。　← **決定**
- D 聊天訊息（每一下一行）。可以往回看，但影片畫面會被洗版。

### Q5 誰看得到

- **A（建議）** 浮字：**附近所有玩家都看得到**（約 64 格內，和原版的暴擊粒子一樣）。action bar：**只給相關的玩家**，也就是攻擊的玩家和被打的玩家。　← **決定**（只有 action bar 的部分適用）
- B 只有攻擊者看得到浮字和 action bar。要改成自訂網路封包加客戶端自己畫浮字（Q8 B），多約半天到一天，客戶端畫面只能手動驗證。
- C 所有人都看得到全部資訊（action bar 也送給附近所有玩家）。適合用另一個帳號在旁邊錄影，但一般遊玩時別人的戰鬥資訊會蓋掉自己的。

### Q6 顯示什麼

- **A（建議）**　← **決定**（只有 action bar 的部分適用）
  - 浮字：**最終傷害**（扣完護甲、抗性後真正扣的血）。暴擊用金色、放大並加「!」（例如「10.3!」）；被迴避顯示灰色「MISS」；被打的是玩家時數字用紅色。
  - action bar：目標名稱、最終傷害、CRIT 或 MISS、**對方剩餘血量／最大血量**，例如「Zombie −6.9 ❤ 13.1/20」。
- B 只顯示最終傷害數字，不分暴擊、不顯示 MISS、不顯示血量。
- C 同 A，再加上**扣護甲前的傷害**，例如 action bar「Zombie −5.2（7.0）」。示範「重甲減傷」時看得出護甲擋掉多少，但一行字比較長。

### Q7 哪些傷害要顯示

- **A（建議）** 所有「**生物造成的傷害**」：近戰和投射物，攻擊者是玩家或怪物都算（和 R1 的 O3「哪些傷害可以迴避」同一個定義）。環境傷害（摔落、著火、溺水、中毒、岩漿）不顯示，避免著火時每秒跳數字。　← **決定**
- B 只顯示**玩家造成的傷害**（玩家近戰、玩家射的箭）。怪物打玩家、怪物互打都不顯示，所以玩家穿迴避胸甲閃掉殭屍攻擊時**看不到 MISS**。
- C 所有傷害，包括環境傷害。

### Q8 多人時怎麼同步

- **A（建議）** 伺服器判定、用原版機制送出：浮字是原版的文字顯示實體，由原版自動同步給附近玩家；action bar 用原版的系統訊息封包。不需要自訂網路封包，也不用寫客戶端程式；傷害只在伺服器算，客戶端不會各算各的。單人遊戲（內建伺服器）走同一條路。　← **決定**（只有 action bar 的部分適用）
- B 自訂網路封包：伺服器把「這一下的結果」送給該看的玩家，客戶端自己畫浮字。可以做到只有攻擊者看得到、動畫更自由；要多寫網路和客戶端程式（本模組目前沒有任何客戶端程式），約多半天到一天，客戶端畫面只能手動驗證。

## 其他決定（附建議值，同意就不用回）　← **照建議**（O7 不適用；O3 保留「打死時剩餘血量顯示 0」）

| # | 問題 | 建議 | 其他選擇 |
|---|---|---|---|
| O1 | 數字格式（B12） | 小數 1 位、四捨五入；有扣血就最少顯示 0.1，不會出現 0.0 | 整數；小數 2 位 |
| O2 | 單位 | 生命值點數（原版 1 顆心 = 2 點），和 `/mobarmory stats`、`/attribute` 一致 | 換算成心（例如「3.5 ❤」） |
| O3 | 打死時的溢出傷害（B11） | 顯示這一下的完整傷害（看得出武器差異），剩餘血量顯示 0 | 只顯示實際扣掉的血（例如只剩 3 血就顯示 3.0） |
| O4 | 無敵時間內的追加攻擊（B1、B2） | 照實顯示：只扣差額就顯示差額；完全沒扣血就不顯示 | 沒扣血時顯示「0」 |
| O5 | 對方本來就不會受傷：創造模式、盾牌完全擋下（B3、B4） | 不顯示，連 MISS 也不顯示 | 盾牌擋下時顯示「BLOCK」 |
| O6 | 吸收心（金蘋果）擋掉的部分（B7） | 不算進數字，數字 = 實際扣的生命值 | 算進去（顯示扣護甲後、扣吸收心前的值） |
| ~~O7~~ | ~~浮字的停留和動畫~~ | **不適用**（沒有浮字） | — |
| O8 | 文字語言 | 和模組其他文字一樣只做英文（`en_us`）：「MISS」「CRIT」「Zombie hit you」；怪物名稱由客戶端依玩家的語言顯示 | 另外加繁體中文（`zh_tw`），整個模組的物品名稱、tooltip 一起翻 |
| O9 | 要不要開關 | 雛型一律開啟，不做開關 | 指令 `/mobarmory hud on\|off`（每個玩家各自開關 action bar）或遊戲規則 |
| O10 | 原版跳劈暴擊和暴擊率暴擊要不要分開標示 | 不分開：兩者倍率一樣（1.5，且不疊加，R1 的 O4），都顯示成暴擊 | 分開標示 |
