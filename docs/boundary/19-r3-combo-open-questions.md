# 待使用者決定的問題（R3 combo 最小版）

> 狀態：**主 session 依使用者夜間授權採用建議值（2026-11-07），待使用者確認**。依 boundary-first，設計和商業規則由人決定，AI 只提案。

## 決定紀錄

使用者 11/07 睡前的原話：

> 繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收

使用者沒有回答下面的問題。主 session 依上面的授權**全部採用建議值**，早上會請使用者確認。這是可以改回的決定：任何一題改選別的，要回頭改 17、18 和程式。

| 題號 | 採用 | 內容 |
|---|---|---|
| Q1 | A | 同意 SOLID 判斷：combo 是資料、`ComboTracker` 是純 Java 狀態機、效果用 `ComboEffect`、自己的 `ComboListener`，不改 R1 |
| Q2 | A | `content/ModCombos`（Java 資料）＋ Strategy（`ComboEffect`）＋ 每個玩家一份的 `ComboTracker`，存在伺服器記憶體 |
| Q3 | A | 照列出的變更邊界。前提已處理：開始實作前把 main `061e7a9`（含 R2b、R6、R4、D1）合進這個分支 |
| Q4 | A | 揮擊開始時從 `StatReader` 讀移動速度，≥ 0.12 算奔跑揮擊 |
| Q5 | A | Lunge（走→跑）第二下 ×1.5；Follow-up（跑→走）第二下 ×1.3 |
| Q6 | A | 打中主目標、而且蓄滿的近戰揮擊才算一下 |
| Q7 | A | 所有武器和空手共用同一組 combo |
| O1～O12 | 照建議 | 只有玩家；不限目標；和暴擊相乘；只套第二下；時間窗 30 tick（含邊界）；不連鎖；W→W、R→R 不是 combo；action bar 兩邊都加「· 名稱」；被迴避的那一下不算也不消耗；只在記憶體；英文名稱；門檻 0.12 |

### 同日調整：Q2 改成 JSON 設定檔

使用者 11/07 定下主軸，原話：

> 主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器

主 session 依這個主軸把 **Q2 從「A：Java 資料（`content/ModCombos`）」改成「JSON 設定檔」**，接近原本的 B。這也是可以改回的決定，待使用者確認。

| 項目 | 調整後 |
|---|---|
| combo 定義 | 一個 combo 一個檔：`config/mobarmory/combos/<id>.json`，檔名就是 id。第一次啟動時自動建立 `lunge.json`、`follow_up.json` 兩個預設檔。玩家多放一個 JSON 就多一種 combo，不用改 Java |
| JSON 裡寫什麼 | 顯示名稱、出招順序（`walk`／`run`）、時間窗、效果 |
| 效果 | 還是 Strategy。JSON 只能從已經寫好的效果種類裡挑一種，填參數，例如 `"effect": {"type": "damage_multiplier", "value": 1.5}`。要新的效果種類才寫 Java（開放封閉） |
| 讀檔、驗證、預設檔 | 沿用 main 上武器那一套（`data/` 的嚴格 JSON 解析、「所有問題一次列出、每條寫檔名」、資料夾不存在才裝預設檔），不另外寫一套 |
| 執行中換 combo | 這次做：`/mobarmory reload` 連 combo 檔一起重讀，全有或全無。網頁編輯 combo 這次不做，寫進報告 20 的「之後」 |
| 留在 Java 的 | 判斷「這一下算不算」的規則：奔跑門檻 0.12（Q4、O12）、蓄滿 0.9（Q6）。這兩個不是某個 combo 的資料，是所有 combo 共用的輸入規則 |

Q3 的變更邊界跟著調整：多了 `data/` 的 combo 讀檔類別、`ReloadCommand` 要連 combo 一起重讀、預設檔放在 `src/main/resources/mobarmory/default_combos/`。O5 的「30 tick」和 O11 的「英文名稱」變成預設檔裡的值，玩家可以改。
> 每題的第一個選項是建議值。回覆時寫題號和選項就好，例如「Q1 A、Q4 B、Q5 A、O3 同意」，或直接回「全部照建議」。
> 第一輪（Q1～Q3）如果有任何一題不是選 A，[17](17-r3-combo-boundary-note.md) 和 [18](18-r3-combo-given-when-then.md) 要依答案重寫，第二輪的題目也可能跟著改。
> 範圍依審查報告 P1-3 和使用者的原則：學長要求的才做，效果先用最簡單的傷害倍率。

## 第一輪：設計（3 題）

### Q1 SOLID 判斷

我預期照最直接的寫法（在 `CombatEffectListener` 裡記「上一下」，用 if 判斷「上一下走路、這一下奔跑就乘 1.5」），會出現三個徵兆：

- 每加一個 combo 就多一個 if，順序和倍率寫死在程式裡（O）。
- R1 的戰鬥處理器同時管暴擊、迴避和 combo，改 combo 會動到已經確認的程式（S）。
- 順序比對、時間窗直接用 Minecraft 類別寫，不開遊戲沒辦法測（D）。

處方：combo 是資料；順序比對是純 Java 的小狀態機 `ComboTracker`；效果用 `ComboEffect`；combo 有自己的 `ComboListener`，不改 R1 的程式。詳見 [17 第 3 節](17-r3-combo-boundary-note.md#solid-判斷第一輪-q1-要確認)。

- **A（建議）** 同意
- B 不是這個問題，是……（請說明）
- C 這次不要重構，直接加

### Q2 Pattern 選擇

會變的是：combo 清單和數字、效果種類、判斷奔跑的規則。

- **A（建議）** combo 是資料（`content/ModCombos`，和 R1 的 `ModStats` 一樣用 Java 寫、重開遊戲生效）；效果用 Strategy（`ComboEffect`）；每個玩家的出招狀態是純 Java 的 `ComboTracker`，存在伺服器記憶體；combo 名稱經 D1 的 action bar 顯示。若 Q5 選 A，`ComboEffect` 這次只有「傷害倍率」一個實作，保留介面是因為學長和審查員要求效果用 Strategy，而且擊退、緩速是已知的下一個候選。
- B 同 A，但 combo 定義放 JSON（`config/mobarmory/combos/*.json`，比照 R2b 的武器），之後 R6 網頁可以改。多一套讀檔、驗證、錯誤處理，約多半天；R6 這次沒有要改 combo。
- C 直接寫、不抽象：在 `CombatEffectListener` 裡用兩個 if 判斷順序，倍率寫死。約 1～2 小時，但第三個 combo 就要再改，R1 的戰鬥程式也會被改。

### Q3 變更邊界

**打算動的檔案**（不同意的請劃掉）：

- 新建 `core/combo/`（`ComboInput`、`InputClassifier`、`ComboDefinition`、`ComboCatalog`、`ComboTracker`、`ComboEffect`、`DamageMultiplier`）、`content/ModCombos`、`forge/ComboListener`
- D1 的小擴充：`HitReport` 加 combo 欄位、action bar 在同一行最後加 combo 名稱、`CombatObserver` 加一個預設什麼都不做的 `comboTriggered`、`HitFeedbackListener` 收這個通知
- `forge/MobArmoryMod`（**R2b 合併後的版本**）接線兩三行；`lang/en_us.json` 加 combo 名稱
- `src/test` 加 `core/combo` 的測試，D1 測試跟著小調整；`src/gametest` 加 `ComboGameTests`、`MobArmoryGameTests` 註冊一行、`test_instance/*.json`

**認為不能動的**（還有要補的請加上）：

- R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat` 的公開介面和規則；`CombatEffectListener` 的規則（暴擊 1.5 倍、迴避只閃生物造成的傷害、暴擊要蓄滿）
- `StatReader` 介面本身（combo 只是用它讀移動速度）
- R2b 的 `data/`（武器 JSON）、`web/`、`EquipmentLibrary`、`EquipmentStatApplier`（Q7 選 B 才要動）
- `build.gradle`、Minecraft 和 Forge 本身（不用 Mixin、不用 Access Transformer）
- `docs/boundary/README.md`、`mod/`、`mod-r4-mobs/`、`PROGRESS.md`、`transcripts/`

**新功能放的位置**：新 combo 放 `content/ModCombos`；新效果種類是一個新的 `ComboEffect` 實作；新輸入種類加在 `ComboInput` 和 `InputClassifier`。

**開始實作前的前提**：這個分支是從 D1（`ed05c6b`）分出來的，還沒有 R2b。實作要接到 R2b 合併後的 `MobArmoryMod`，所以開始寫程式前，要先把合併後的 main 合進這個分支（或從合併後的 main 重開分支）。要不要合、什麼時候合，由使用者決定。

- **A（建議）** 同意
- B 要調整（請寫要劃掉或補上的項目）

## 第二輪：商業規則（4 題，最影響實作的）

### Q4 「奔跑揮擊」怎麼判斷

學長說「combo 要和數值結合…所以要讀得到移動速度」。各種裝備下的移動速度：空手走路 0.10／疾跑 0.13；短刀 0.11／0.143；重劍 0.07／0.091；重甲 0.085／0.11。

- **A（建議）** **讀數值**：揮擊開始那一刻，從 `StatReader` 讀移動速度，**≥ 0.12**（預設 0.1 的 1.2 倍）算奔跑揮擊。直接對應學長的要求，數值也真的會影響 combo：拿重劍或重甲疾跑也只有 0.09～0.11，**打不出奔跑揮擊**；喝速度藥水走路（0.12）就算奔跑。門檻是資料，可以改。
- B **看原版的疾跑狀態**（`isSprinting`）：有在疾跑就算奔跑揮擊，和裝備無關。最直覺、拿什麼武器都打得出兩種 combo；但 combo 沒有讀數值，和學長「要讀得到移動速度」的說法對不上，被問到時要說明。
- C 兩個都要：在疾跑**而且**移動速度 ≥ 門檻才算奔跑。結果和 A 幾乎一樣（只差「喝藥水走路」不算奔跑），多一條規則。

### Q5 combo 清單和效果

- **A（建議）** 兩個 combo，都只改傷害，倍率不同，看數字就分得出來：**Lunge**（走→跑）第二下 ×1.5；**Follow-up**（跑→走）第二下 ×1.3。
- B 兩個 combo，效果種類不同：Lunge（走→跑）第二下 ×1.3；Follow-up（跑→走）第二下把對方擊退，加 2 秒緩速。示範時比較有感，也真的有兩種 Strategy；擊退和緩速要碰 Minecraft，約多 1～2 小時，多兩條要測的規則。
- C 兩個 combo 都是 ×1.3，只有名稱不同。最簡單，但「A→B 和 B→A 不同」只差在名字。

### Q6 什麼算「一下」

- **A（建議）** **打中主目標、而且蓄滿**（≥ 0.9，和 R1 的 N2 暴擊一樣）的近戰揮擊才算。空揮、沒蓄滿、被迴避、在無敵時間內完全沒扣血、橫掃到的旁邊生物、射箭都不算；不算的那一下**不會打斷**時間窗，也不會取代上一下。避免狂點滑鼠就刷出 combo。
- B 打中就算，不用蓄滿。比較容易觸發，但狂點就能一直刷 combo。
- C 空揮也算（伺服器收得到揮手的封包）。可以先對空氣做出第一步，但「打兩下」變成「揮兩下」，和學長的描述不太一樣。

### Q7 combo 屬於誰

學長的原話是「武器有 combo：出招順序不同就是不同 combo」。

- **A（建議）** **所有武器和空手共用**同一組 combo（定義在 `content/ModCombos`）。不用改 R2b 的武器 JSON 格式和 R6 網頁；和 Q4 A 搭配時，武器透過「移動速度」影響 combo（重武器跑不出奔跑揮擊）。
- B **每把武器各自的 combo**：combo 寫在武器的 JSON 裡（例如短刀有 Lunge、重劍有另一個 combo），原版物品和空手沒有。最貼近「武器有 combo」的字面意思，但要改 R2b 的 JSON 格式、讀檔和驗證，R6 網頁也要能顯示或編輯，和開發 session 的檔案重疊，約多半天到一天。
- C 共用一組，再加「某些武器不能 combo」的開關。介於兩者之間，多一個欄位要放進武器 JSON。

## 其他決定（附建議值，同意就不用回）

| # | 問題 | 建議 | 其他選擇 |
|---|---|---|---|
| O1 | 誰能 combo（B18） | 只有玩家；怪物沒有「走路／奔跑揮擊」的輸入 | R4 怪物也能 combo（要另外定義怪物的出招） |
| O2 | 第二下要不要打同一個目標（B14） | 不限目標：出招狀態只看玩家自己 | 一定要同一個目標 |
| O3 | combo 和暴擊（B13） | 相乘：短刀 3 × 暴擊 1.5 × Lunge 1.5 = 6.75 | combo 那一下不能暴擊；兩者取大的 |
| O4 | 效果套在哪一下 | 只套在完成 combo 的第二下 | 第二下和之後一段時間的每一下 |
| O5 | 時間窗（B1、B2） | 1.5 秒（30 tick），剛好 30 tick 也算；用遊戲時間，伺服器卡頓時跟著遊戲時間走 | 1.0 秒；2.0 秒 |
| O6 | 觸發後能不能接著連（B11） | 不連鎖：觸發後重新開始，第二下不會當成下一個 combo 的第一下 | 可以連鎖（W→R→W 依序觸發 Lunge、Follow-up） |
| O7 | 同一種輸入連打兩下（B3、B4） | W→W、R→R 不是 combo，只把「上一下」換成新的這一下 | 另外定義成 combo |
| O8 | 顯示（D1 的 action bar） | 攻擊者那一行最後加「· combo 名稱」，例如 `Cow -1.5 ❤ 7.5/10 · Lunge`；被玩家 combo 打中的玩家那一行也加 | 只給攻擊者；另外用聊天訊息 |
| O9 | 完成 combo 的那一下被迴避（B8） | 不算這一下：combo 不觸發、也不消耗，第一下仍在時間窗內 | 消耗掉，action bar 顯示「Lunge MISS」 |
| O10 | 狀態保存（B16） | 只在伺服器記憶體，不存檔；死亡、登出後重新開始 | 存在玩家資料裡 |
| O11 | 名稱和語言 | 英文「Lunge」「Follow-up」，放 `en_us.json`（和模組其他文字一樣只做英文） | 另取名稱；加繁體中文 |
| O12 | 奔跑門檻（若 Q4 選 A） | 0.12（預設移動速度 0.1 的 1.2 倍），寫在 `ModCombos` 旁邊的資料裡 | 其他數字，例如 0.125 |
