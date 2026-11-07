# 邊界說明：R3 combo（最小版）

> 狀態：**使用者 2026-11-08 確認：全部照建議**（原話：「全部照建議」）。2026-11-07 主 session 依使用者夜間授權先採用建議值：[19](19-r3-combo-open-questions.md) 的 Q1～Q7 全選 A、O1～O12 照建議，Q2 改成 JSON。實作照這份邊界進行，結果見 [20](20-r3-combo-behavior-report.md)。
> 前提已處理：實作前已把 main `061e7a9`（含 R2b、R6、R4、D1）合進這個分支。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [18-r3-combo-given-when-then.md](18-r3-combo-given-when-then.md)。
> 工作位置：worktree `java_minecraft_model/mod-damage-hud`，分支 `feature/r3-combo`（從 `feature/damage-hud` 的 `ed05c6b` 分出，所以包含 D1 的 action bar）。
> 前提：開發 session 的 **R2b 會先合進 main**：`content/ModEquipment` 換成 `config/mobarmory/weapons/*.json` ＋ `EquipmentLibrary`（裝備用 id 查），`MobArmoryMod` 也改了。這份邊界照合併後的樣子寫。

## 0. 11/07 調整：combo 定義改成 JSON 檔（先讀這一節）

使用者 11/07 的主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。主 session 依此把 Q2 改成 JSON 設定檔（[19 的決定紀錄](19-r3-combo-open-questions.md#決定紀錄)）。下面第 3 節的分層照這個表調整，其他各節不變。

```
core/combo/          純 Java
  ComboInput、InputClassifier、ComboEffect、DamageMultiplier   同第 3 節
  ComboDefinition    多了 name（顯示名稱）、windowTicks（每個 combo 自己的時間窗）
  ComboCatalog       只檢查 id 和出招順序不重複；可以是空的
  ComboTracker       每一下都傳入目前的 ComboCatalog，reload 後下一下就生效
  ComboLibrary       目前生效的那一份，reload 時整份換掉（同 R2b 的 EquipmentLibrary）
data/                讀檔，沿用武器那一套
  JsonFolder         從 WeaponFolder 抽出來的「一個資料夾的 <id>.json」：列檔、讀、原子寫入；武器和 combo 共用
  ShippedFiles       從 DefaultWeapons 抽出來的「讀 jar 裡內附的檔」；武器和 combo 共用
  ComboJson          combo 檔格式 1；用 WeaponJson 的嚴格解析和欄位錯誤訊息
  ComboEffectTypes   檔案裡 effect.type 的名稱 → 建出哪個 ComboEffect（新效果種類只在這裡加一行）
  ComboLoader        讀所有 combo 檔、一次列出所有問題（同 WeaponLoader 的結構）
  DefaultCombos      內附的 lunge.json、follow_up.json；資料夾不存在才裝（同 DefaultWeapons）
  ComboFiles         執行中的 combo 檔：啟動時讀、/mobarmory reload 時全有或全無地換掉
content/ModCombos    只留所有 combo 共用的輸入規則：奔跑門檻 0.12、蓄滿 0.9
forge/
  ComboListener      同第 3 節，每一下讀 ComboLibrary 的目前那一份
  ReloadCommand      R2b 的指令，多重讀 combo 檔（和武器各自全有或全無）
src/main/resources/mobarmory/default_combos/   lunge.json、follow_up.json
```

| 項目 | 調整 |
|---|---|
| 可以改的模組（多的） | `data/` 新增上面的類別；R2b 的 `WeaponFolder`、`DefaultWeapons` 改成用抽出來的共用類別（行為不變）；`WeaponJson` 的 `string`、`number` 從 private 改成 package-private；`WeaponLoadException` 多一個建構子；`ReloadCommand` 多重讀 combo |
| 不准動的相依（沒變） | R1 的戰鬥規則、`StatReader`；R2b 的 `web/`、`EquipmentLibrary`、`WeaponEditor`、武器檔格式 |
| 改一處會牽動幾個檔案 | 新增一個 combo（用既有的效果種類）：**1**（一個 JSON 檔，不用重開，`/mobarmory reload`）<br>改倍率、時間窗、名稱：**1**（那個 JSON 檔）<br>新增一種效果：**2**（`core/combo` 一個類別、`ComboEffectTypes` 一行；契約測試的樣本一行）<br>改奔跑門檻：**1**（`ModCombos`） |

## 1. 需求翻譯

### 學長的原話（來源：審查報告 `reviews/review-01-requirements-gap.md` 的 B2、A2）

- 業務需求 B2：「武器有 combo：**出招順序不同就是不同 combo**」。
- 架構需求 A2：「combo 要和數值結合，例如**奔跑攻擊和走路攻擊是不同 combo**，所以要**讀得到移動速度**」。
- 審查員建議的最小範圍（P1-3）：兩種輸入（走路揮擊、奔跑揮擊），一個時間窗內打兩下，A→B 和 B→A 觸發不同的 combo；combo 定義是資料，效果用 Strategy，出招狀態存在伺服器；combo 名稱顯示在 D1 的 action bar。
- 使用者的原則：學長要求的才做，不另外加特效或額外規則。效果先用最簡單的傷害倍率，擊退、緩速放在問題清單讓使用者選。

### 真正的需求

- 本質是一台很小的**狀態機**：每個玩家記住「上一下是什麼輸入、在什麼時候」；這一下打中時，把「上一下 → 這一下」拿去比對 combo 清單，比對到就把效果套在這一下。
- 「輸入」是從**數值**來的：攻擊那一刻讀移動速度（R1 留下的 `StatReader`），決定這一下是走路揮擊還是奔跑揮擊。這就是學長說的「combo 和數值結合」。
- 會變的是：combo 清單（名稱、順序、效果的數字）、效果的種類（傷害倍率之外，之後可能有擊退、緩速）、判斷奔跑的方式和門檻。不會變的是：R1 的數值和戰鬥規則、D1 的顯示方式。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [19](19-r3-combo-open-questions.md)：

1. 「奔跑」怎麼判斷：讀移動速度數值和門檻，還是看原版的疾跑狀態？兩者在拿重劍時結果不同（Q4）
2. combo 清單和效果：幾個、叫什麼、倍率多少、要不要擊退或緩速（Q5）
3. 什麼算「一下」：要打中嗎？要蓄滿嗎？空揮、被迴避、橫掃到的算不算（Q6）
4. combo 屬於誰：所有武器共用，還是每把武器各自有（學長說「武器有 combo」）（Q7）
5. 時間窗多長、觸發後能不能接著連下去、同一種輸入連打兩下算不算（O5～O7）
6. combo 和暴擊怎麼疊、第二下打別隻怪算不算、怪物會不會 combo（O1～O3）
7. action bar 怎麼顯示 combo 名稱、文字語言（O8、O11）

### 這次要做／未來可能變

| 這次要做 | 未來會變，所以要在這裡切邊界 |
|---|---|
| 兩種輸入：走路揮擊、奔跑揮擊，由移動速度數值決定 | **輸入種類會增加**（跳躍揮擊、蹲下揮擊）→ 判斷輸入的規則放在一個類別（`InputClassifier`），輸入是 enum |
| 兩個 combo：走→跑、跑→走 | **combo 清單和數字會改** → combo 是資料（`content/ModCombos`），加一個 combo 只加一筆 |
| 效果：傷害倍率 | **效果種類會增加**（擊退、緩速）→ 效果用 Strategy（`ComboEffect`） |
| 每個玩家的出招狀態，存在伺服器記憶體 | **R5 多人** → 狀態只在伺服器、每個玩家各一份，客戶端不用同步 |
| combo 名稱顯示在 D1 的 action bar | **R6 網頁要改 combo** → 這次不做；定義已經是資料，之後可以搬到 JSON |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下是在 Forge 26.3-66.1.0 和 Minecraft 26.3 原始碼裡確認過的。

### 玩家揮劍的順序

```
Player.attack(target)
   AttackEntityEvent            ← 揮擊開始：此時還在疾跑、蓄力還沒歸零（R1 的 N2 也在這裡記蓄力）
   CriticalHitEvent             ← R1：暴擊（×1.5）
   target.hurtServer(...)
      LivingAttackEvent         ← R1：迴避（取消就沒打中）
      無敵時間：較弱的一下直接結束
      LivingHurtEvent           ← 扣護甲前；R1 對「玩家近戰」不做事（暴擊已經在上面處理）
      LivingDamageEvent         ← D1：讀最終傷害、送 action bar
   打中後：疾跑攻擊有擊退，原版接著呼叫 setSprinting(false)，疾跑狀態和疾跑加速都被拿掉
   橫掃：旁邊的生物也各自走一次 hurtServer（沒有自己的 AttackEntityEvent）
```

### 拿得到什麼

| 要的東西 | 從哪裡拿 | 備註 |
|---|---|---|
| 這一下的移動速度 | R1 的 `EntityStatReader.get(MOVEMENT_SPEED)`，在 `AttackEntityEvent` 讀 | 是**含所有修飾**的值：裝備（重劍 −30%）、藥水、疾跑。原版疾跑時會在移動速度上加一個 ×1.3 的修飾（`LivingEntity.SPEED_MODIFIER_SPRINTING`），打中後的 `setSprinting(false)` 會把它拿掉，所以一定要在揮擊開始時讀 |
| 有沒有在疾跑 | `Player.isSprinting()`，同樣要在 `AttackEntityEvent` 讀 | 和裝備無關 |
| 是否蓄滿 | `getAttackStrengthScale(0.5F) > 0.9`，在 `AttackEntityEvent` 讀 | 和 R1 的 N2 同一個門檻 |
| 這一下有沒有真的打中 | `LivingHurtEvent`（攻擊者是這個玩家、近戰、目標就是 `AttackEntityEvent` 的那一個） | 被迴避、無敵時間內完全沒扣、被取消時不會觸發；橫掃到的生物會觸發，但目標不同，可以排除 |
| 改這一下的傷害 | 同一個 `LivingHurtEvent` 的 `setAmount` | 在扣護甲前乘，和暴擊（已經算進傳進來的數字）自然相乘 |
| 時間 | 伺服器的 `level.getGameTime()`（tick，1 秒 = 20 tick） | 伺服器卡頓時跟著遊戲時間走 |

### 各種裝備下的移動速度（判斷奔跑時要注意）

| 裝備 | 走路 | 疾跑（×1.3） |
|---|---|---|
| 空手 | 0.100 | 0.130 |
| 短刀（＋10%） | 0.110 | 0.143 |
| 重劍（−30%） | 0.070 | 0.091 |
| 重甲（−15%） | 0.085 | 0.1105 |
| 重劍＋重甲 | 0.0595 | 0.0774 |

短刀走路（0.110）和重甲疾跑（0.1105）幾乎一樣快。所以只看數值、用一個固定門檻判斷「奔跑」時，**拿重的裝備就跑不出奔跑揮擊**；只看疾跑狀態時，結果和裝備無關。這是 Q4 要決定的事。

## 3. 變更邊界（提案，確認點會逐項問）

### 分層

```
core/combo/          純 Java，不 import Minecraft（CoreBoundaryTest 自動守住）
  ComboInput           WALK / RUN
  InputClassifier      這一下是走路還是奔跑（Q4 的規則；A 是讀 StatReader 的移動速度和門檻）
  ComboDefinition      一個 combo：id、兩步順序（例如 WALK→RUN）、效果
  ComboCatalog         combo 清單；建立時檢查 id 不重複、順序不重複、剛好兩步
  ComboTracker         一個玩家的出招狀態：上一下的輸入和時間；record(輸入, tick) → 觸發的 combo 或沒有
  ComboEffect          介面（Strategy）：double apply(double damage)
  DamageMultiplier     ComboEffect 的第一個實作：傷害 × 倍率
content/
  ModCombos           這次的 combo 清單（Q5），全部是資料
forge/
  ComboListener       AttackEntityEvent：讀移動速度和蓄力，記下「這一下的輸入」
                      LivingHurtEvent：這一下真的打中主目標 → ComboTracker 比對 → 套用效果、通知顯示
core/feedback/、forge/feedback/（D1）
  HitReport           多一個欄位：這一下觸發的 combo（沒有就是空的）
  HitFeedbackListener 實作新的通知，把 combo 配到這一下的 HitReport
  ActionBarDisplay    在同一行最後加上 combo 名稱
```

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `core/combo/`、`content/ModCombos`、`forge/ComboListener`<br>修改 D1：`core/feedback/HitReport`（加 combo 欄位）、`HitText` 或 `ActionBarDisplay`（顯示名稱）、`HitFeedbackListener`（收 combo 通知）、`forge/CombatObserver`（多一個預設什麼都不做的 `comboTriggered`）<br>`forge/MobArmoryMod`（R2b 合併後的版本，接線兩三行）、`lang/en_us.json`（combo 名稱）<br>測試：`src/test` 的 `core/combo` 測試和 D1 測試的小調整；`src/gametest` 加 `ComboGameTests`、`MobArmoryGameTests` 註冊一行、`test_instance/*.json`<br>文件：`docs/boundary/17～20`、`prompts/` |
| 不准動的相依 | R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat`（`StatEffect`、`CombatOutcome`、`CombatResolver`）和 `CombatEffectListener` 的規則（暴擊 1.5 倍、迴避只閃生物造成的傷害、暴擊要蓄滿）<br>`StatReader` 介面本身（只是使用它）<br>R2b 的 `data/`（武器 JSON）、`web/`、`EquipmentLibrary`、`EquipmentStatApplier`、`ReloadCommand`（若 Q7 選 B 才需要動）<br>`build.gradle`、Minecraft、Forge（不用 Mixin、不用 Access Transformer）<br>`docs/boundary/README.md`、`mod/`、`mod-r4-mobs/`、`PROGRESS.md`、`transcripts/` |
| 保留或新建的抽象介面 | 保留：`StatReader`（combo 透過它讀移動速度）、D1 的 `HitDisplay`、`CombatObserver`<br>新建：`ComboEffect.apply(double damage) → double`（Strategy）<br>新建：`ComboTracker.record(ComboInput input, long tick) → Optional<ComboDefinition>`<br>新建：`InputClassifier.classify(StatReader stats) → ComboInput`（Q4 選 B 時改成看疾跑狀態） |
| 新功能放哪、用什麼形狀 | 新 combo：`content/ModCombos` 加一筆（資料）<br>新效果種類：新增一個 `ComboEffect` 實作（Strategy）<br>新輸入種類（例如跳躍揮擊）：`ComboInput` 加一個值、`InputClassifier` 加規則 |
| 改一處會牽動幾個檔案 | 新增一個 combo（用既有的效果種類）：**2**（`ModCombos` 一筆、語系檔一行）<br>改倍率或時間窗：**1**（`ModCombos`）<br>新增一種「改傷害」的效果：**3**（再加一個 `ComboEffect` 類別）<br>改奔跑門檻：**1** |

`ComboListener` 和 D1 的顯示程式不需要因為新增 combo 而修改。

### SOLID 判斷（第一輪 Q1 要確認）

照最直接的寫法（在 `CombatEffectListener` 裡記「上一下」，用 if 判斷「上一下走路、這一下奔跑就乘 1.5」），預期會出現這些徵兆：

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 每加一個 combo 就在處理器裡多一個 if，順序和倍率寫死在程式裡 | O 開放封閉 | combo 是資料（`ModCombos`），比對用 `ComboTracker`，加 combo 不改程式 |
| R1 的戰鬥處理器同時管暴擊、迴避和 combo，改 combo 會動到 R1 已經確認的程式和測試 | S 單一職責 | combo 有自己的 `ComboListener`，不改 `CombatEffectListener` |
| 「時間窗」「W→R 才算」這些規則直接用 Minecraft 類別寫，不開遊戲就沒辦法測順序比對 | D 依賴反轉 | 狀態機和比對放 `core/combo`，純 Java，用 tick 數字和 `StatReader` 測 |

L 由契約測試保證（每個 `ComboEffect` 對 0、極小、極大、NaN 的傷害都不丟例外、不變成負數）；I 目前的介面都只有一個方法。

**誠實說明**：如果 Q5 選 A（兩個 combo 都只是傷害倍率），`ComboEffect` 這次只有 `DamageMultiplier` 一個實作。照速查表「沒有第二個案例就直接寫」，可以不抽介面；但學長和審查員都要求「效果用 Strategy」，而且擊退、緩速已經是 Q5 B 的候選，所以建議保留這個只有一個方法的小介面。Q2 有「直接寫」的選項。

### Pattern 選擇（第一輪 Q2 要確認）

會變的是：combo 清單和數字、效果種類、判斷奔跑的規則。

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | combo 是資料（`content/ModCombos`，和 R1 的 `ModStats` 一樣用 Java 寫、重開遊戲生效）；效果用 Strategy（`ComboEffect`）；出招狀態是純 Java 的小狀態機（`ComboTracker`），每個玩家一份；`ComboListener` 只負責把 Forge 事件翻成「輸入」和套用結果；名稱經 D1 的 `HitReport` 顯示 | 順序比對、時間窗都能單元測試；加 combo 只加一筆；不碰 R1、R2b 的檔案 | 改 combo 要重開遊戲 |
| B | 同 A，但 combo 定義放 JSON（`config/mobarmory/combos/*.json`，比照 R2b 的武器），之後 R6 網頁可以改 | 遊戲執行中可以改 combo | 多一套讀檔、驗證、錯誤處理，約多半天；R6 這次沒有要改 combo，學長也沒要求 |
| C | 直接寫、不抽象：在 `CombatEffectListener` 裡用兩個 if 判斷順序，倍率寫死 | 今天最快，約 1～2 小時 | 第三個 combo 就要再改處理器；R1 已確認的戰鬥程式會被改；順序比對沒辦法單元測試 |

## 4. 實作順序

每一步做完都跑 `gradlew build`（單元測試）；動到 `forge/` 的步驟再跑 `runGameTestServer`。

1. `core/combo`：`ComboInput`、`InputClassifier`、`ComboEffect`＋`DamageMultiplier`、`ComboDefinition`、`ComboCatalog`、`ComboTracker`，加單元測試和契約測試
2. `content/ModCombos`：這次的 combo 清單，加「清單合法」的契約測試（id 不重複、順序不重複、每個 combo 都有名稱）
3. D1 擴充：`HitReport` 加 combo 欄位、action bar 加名稱、`CombatObserver` 加 `comboTriggered`；**先跑 D1 既有的 54 個單元測試和 9 個遊戲內測試，確認沒壞**
4. `forge/ComboListener`：記輸入、比對、套用、通知；接線
5. 遊戲內系統測試：假玩家真的照順序揮劍（同 D1 的做法），疾跑用 `setSprinting(true)`，時間窗用真的遊戲 tick 等
6. `gradlew clean build runGameTestServer` 全過，寫行為報告 `20-r3-combo-behavior-report.md`

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `InputClassifier`：門檻上下（或疾跑與否）；`ComboTracker`：W→R、R→W 觸發不同 combo，W→W、R→R 不觸發，超過時間窗不觸發、剛好在時間窗邊界，觸發後重新開始；`DamageMultiplier` 的計算；`ComboCatalog` 的驗證（id 重複、順序重複、不是兩步就失敗） |
| Module | [18](18-r3-combo-given-when-then.md) 的成功條件逐一破壞（B 系列裡不需要開遊戲的部分）：沒蓄滿、空揮、被迴避時「不算一下」由 `ComboTracker` 的輸入端決定 |
| 契約 | (1) 每個 `ComboEffect` 實作：傷害 0、極小、極大、NaN 都不丟例外，結果不是負數、不是 NaN<br>(2) `ModCombos` 裡的每個 combo：id 不重複、順序不重複、有名稱、效果合法、時間窗大於 0。新增 combo 或效果時參數列表多一行 |
| System（遊戲內） | 假玩家真的揮劍打牛：<br>・走路打一下、疾跑打一下（1.5 秒內）→ 第二下傷害是倍率後的值，action bar 有 combo 名稱<br>・反過來 → 另一個 combo、另一個倍率（**A→B 和 B→A 不同**）<br>・等超過時間窗再打 → 沒有 combo<br>・沒蓄滿、被迴避、橫掃到的 → 不算一下<br>・（Q4 A）重劍疾跑 → 算走路，打不出奔跑 combo<br>・兩個玩家同時出招互不影響<br>・既有的單元 160、遊戲內 27 照樣通過 |
| 手動（runClient） | 疾跑揮劍的手感、action bar 上的 combo 名稱。視使用者同意再做 |

## 6. 技術風險

| # | 風險 | 處理 |
|---|---|---|
| T1 | 疾跑攻擊打中後，原版會把疾跑和疾跑加速拿掉；如果在打中之後才讀移動速度，奔跑揮擊永遠讀成走路 | 在 `AttackEntityEvent`（揮擊開始）讀，記在「這一下的輸入」，打中時（`LivingHurtEvent`）才拿出來用。遊戲內測試會測「疾跑打中後，這一下仍然算奔跑」 |
| T2 | 橫掃攻擊打到旁邊的生物也會觸發 `LivingHurtEvent`，會被當成多打了好幾下 | 只認 `AttackEntityEvent` 那一個主目標；和 D1 配對暴擊的做法一樣，比對攻擊者、目標和遊戲 tick |
| T3 | 假玩家沒有真的鍵盤，不會自己疾跑 | 測試用 `setSprinting(true)`，原版會照樣加上 ×1.3 的移動速度修飾，和真的疾跑走同一條路 |
| T4 | 時間窗要真的過 1.5 秒（30 tick） | 遊戲內測試用 GameTest 的延遲（`runAfterDelay`）等真的 tick；單元測試直接給 tick 數字 |
| T5 | R2b 先合進 main，`MobArmoryMod` 已經改過 | 這個分支在 R2b 合併、D1 合併之後再接線；本輪只寫文件，不動 `MobArmoryMod`。實作時先把 main 合進來（或在 R2b 合併後的 main 上重新開分支），使用者決定 |
| T6 | D1 的 `HitReport` 要加欄位，D1 的單元測試建構 `HitReport` 的地方要跟著改 | 用工廠方法（`landed`、`dodged`）多一個多載，舊的呼叫不變；第 3 步先跑 D1 的測試確認 |
