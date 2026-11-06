# 行為報告：R3 combo（最小版，combo 定義是 JSON 檔）

> boundary-first 第 6 步。日期：2026-11-07（夜間）。分支 `feature/r3-combo`（worktree `mod-damage-hud`），先合了 main `061e7a9`（含 R2b、R6、R4、D1）。
> **合併到 main**：依 review-04 X1，再合了 main `56423bd`（合併 commit `130240d`）。合併後單元 450/450、遊戲內 68/68（含原版 1 個），由架構師快轉 main，見[第 8 節](#8-合併到-main2026-11-07-夜間review-04-x1)。下面第 1～7 節是合併前的數字和內容。
> 結果：**單元測試 396/396**（基準 322 ＋ 新增 74）、**遊戲內系統測試 64/64**（基準 57 ＋ 新增 7；伺服器報告「All 65 required tests passed」，多的一個是原版的 `always_pass`）。
> 指令：`gradlew clean build runGameTestServer`，遊戲內報告在 `run-gametest/gametest-report.xml`（65 個 testcase、0 個 failure）。
> **決策狀態**：使用者沒有回答 [19](19-r3-combo-open-questions.md) 的問題。主 session 依使用者夜間授權**採用建議值**；同一晚又依使用者主軸把 Q2 改成 JSON 檔。全部**待使用者確認**，見 19 的決定紀錄。
> 每一步的 prompt 和 SOLID／Pattern 檢查：[prompts/r3-combo-round-02-implementation.md](prompts/r3-combo-round-02-implementation.md)。

## 1. 邊界說明（採用的版本）

完整內容見 [17](17-r3-combo-boundary-note.md)，其中第 0 節是 JSON 版的分層。

| 項目 | 內容 |
|---|---|
| 學長的要求 | B2「武器有 combo：出招順序不同就是不同 combo」；A2「combo 要和數值結合…所以要讀得到移動速度」 |
| 使用者主軸 | 「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」→ 玩家多放一個 JSON 就多一種 combo |
| 會變的東西 | combo 清單、名稱、順序、時間窗、倍率：放在 JSON 檔。效果種類：Strategy。輸入種類：enum 加分類規則 |
| 形狀 | `core/combo` 純 Java 規則；`data/` 讀 combo 檔，沿用武器那一套；`forge/ComboListener` 把 Forge 事件翻成「輸入」並套用效果；名稱經 D1 的 `CombatObserver` 和 `HitReport` 接到 action bar |
| 不准動的相依 | R1 的戰鬥規則和 `CombatEffectListener`（一行沒改）、`StatReader`、R2b 的 `web/`、`EquipmentLibrary`、`WeaponEditor`、武器檔格式、`build.gradle`、Minecraft 和 Forge（沒有 Mixin、沒有 Access Transformer）、`docs/boundary/README.md` |
| 邊界由測試守住 | `CoreBoundaryTest` 自動涵蓋 `core/combo`；`ComboEffectContractTest` 守住每一種效果；`HitTextContractTest` 守住 combo 後綴字串在 `en_us.json` 裡 |

### 實際改到的檔案

| 類型 | 檔案 | 改動 |
|---|---|---|
| 新增（核心，純 Java） | `core/combo/`：`ComboInput`、`InputClassifier`、`ComboEffect`、`DamageMultiplier`、`ComboDefinition`、`ComboCatalog`、`ComboTracker`、`ComboLibrary` | 8 個檔案 |
| 新增（讀檔） | `data/`：`ComboJson`、`ComboEffectTypes`、`ComboLoader`、`DefaultCombos`、`ComboFiles`；共用的 `JsonFolder`、`ShippedFiles` | 7 個檔案 |
| 新增（資料） | `content/ModCombos`（奔跑門檻 0.12、蓄滿 0.9）；`resources/mobarmory/default_combos/lunge.json`、`follow_up.json` | 3 個檔案 |
| 新增（Forge） | `forge/ComboListener` | 1 個檔案 |
| 修改 R2b（為了共用，行為不變） | `WeaponFolder`（列檔和原子寫入改用 `JsonFolder`）、`DefaultWeapons`（讀內附檔改用 `ShippedFiles`）、`WeaponJson`（`string`、`number` 改 package-private）、`WeaponLoadException`（多一個建構子，訊息可寫「combo files」） | 共 ＋25／−56 行，既有測試全過 |
| 修改 R2b（功能） | `ReloadCommand`：同一個 `/mobarmory reload` 也重讀 combo 檔 | ＋28／−1 行 |
| 修改 D1 | `HitReport`（多 `combo` 欄位，舊的 7 參數建構子保留）、`HitLine`（同）、`HitText`（`COMBO_KEY`）、`ActionBarDisplay`（後綴）、`HitFeedbackListener`（配對 combo）、`CombatObserver`（預設什麼都不做的 `comboTriggered`） | D1 既有的 54 個單元測試、10 個遊戲內測試照樣通過 |
| 修改接線 | `MobArmoryMod`：`COMBO_FILES`、`ComboListener`、`ReloadCommand` 多傳一個參數、啟動 log 印 combo 數 | ＋16／−3 行 |
| 修改語系 | `en_us.json`：`mobarmory.hit.combo`（「 · %s」）、reload 的 3 行 combo 訊息 | ＋4 行 |
| 測試 | 見第 4 節 | — |

### 改一處會牽動幾個檔案

| 要做的事 | 檔案數 | 要不要改 Java |
|---|---|---|
| 新增一個 combo（用既有的效果種類） | **1**（`config/mobarmory/combos/` 一個 JSON 檔），`/mobarmory reload` 立刻生效 | 不用 |
| 改某個 combo 的倍率、時間窗、名稱、順序 | **1**（那個 JSON 檔） | 不用 |
| 新增一種效果（例如擊退） | **2**（`core/combo` 一個 `ComboEffect` 類別、`ComboEffectTypes.STANDARD` 一行）＋契約測試樣本一行 | 要 |
| 改奔跑門檻或蓄滿門檻 | **1**（`content/ModCombos`） | 要 |

`ComboListener`、`ComboTracker`、`ComboLoader` 和 D1 的顯示程式，都不會因為新增 combo 或新增效果種類而修改。

## 2. 商業決策（採用建議值，待使用者確認）

| # | 決定 | 內容 |
|---|---|---|
| Q1 | A | SOLID 判斷照 17 第 3 節 |
| Q2 | **JSON 檔**（11/07 依使用者主軸從 A 改） | `config/mobarmory/combos/<id>.json`；效果是 Strategy，檔案只能挑已有的種類填參數 |
| Q3 | A | 變更邊界照 17（JSON 版見 17 第 0 節） |
| Q4 | A | 揮擊開始時讀移動速度，≥ 0.12 是奔跑揮擊 |
| Q5 | A | Lunge（走→跑）×1.5、Follow-up（跑→走）×1.3：現在是兩個內附檔的內容 |
| Q6 | A | 打中主目標、而且蓄滿的近戰揮擊才算一下 |
| Q7 | A | 所有武器和空手共用 |
| O1～O12 | 照建議 | 時間窗 30 tick 和英文名稱變成內附檔的值，玩家可以改 |

### 實作時採用的假設（沒有另外問，請過目）

| # | 假設 | 為什麼 | 不同意時改哪裡 |
|---|---|---|---|
| C1 | 啟動時有 combo 檔寫錯：**遊戲開不起來**，訊息寫出檔名和欄位 | 沿用武器檔的 O7，同一套規則；寫錯的檔不會默默消失 | `ComboFiles.load`：改成印警告、跳過那個檔 |
| C2 | `/mobarmory reload` 讀 combo 檔：全有或全無；**新增、刪除的 combo 檔立刻生效** | 武器要等重開是因為物品要註冊；combo 不用註冊任何東西 | `ComboFiles.reload` |
| C3 | 武器和 combo 在同一個指令裡**各自**全有或全無：武器檔錯了，combo 照樣重讀，反過來也一樣 | 兩種檔沒有互相引用 | `ReloadCommand.reload` |
| C4 | 檔案格式：`effect` 是一個物件 `{"type": "damage_multiplier", "value": 1.5}`，不是主 session 例子裡的平鋪寫法 `"effect": "damage_multiplier", "value": 1.5` | 之後的效果可能有好幾個參數（擊退力道＋緩速秒數），放在 `effect` 裡不會和 combo 本身的欄位混在一起 | `ComboJson`、`ComboEffectTypes` |
| C5 | 時間窗用 tick（`window_ticks`），1～1200（1 分鐘）；名稱 1～32 字；格式 1 只有兩步 | 和 O5 的單位一致、整數比較不會有誤差；名稱長度和武器一樣 | `ComboDefinition` |
| C6 | combo 名稱照檔案寫的直接顯示，不翻譯 | 和武器名稱一樣（R2b 也是直接顯示 JSON 的 `name`） | — |
| C7 | 奔跑門檻、蓄滿門檻留在 Java（`ModCombos`），不放進每個 combo 檔 | 這是「什麼算一下」的共用規則，不屬於某一個 combo；主 session 列的 JSON 內容是順序、時間窗、名稱 | 之後可以放進 `config/mobarmory/combos.json` 之類的設定檔 |
| C8 | 蓄滿是「大於 0.9」 | 和 R1 的暴擊（N2）程式一樣；18 寫的「≥ 0.9」是口語 | `ModCombos.FULL_CHARGE` |
| C9 | 無敵時間內較強的一下（只扣差額）算一下，倍率乘在差額上 | 原版只把差額交給 `LivingHurtEvent`；不另外處理 | `ComboListener.onHurt` |
| C10 | 完成 combo 的那一下被吸收心全部吸收：combo 算觸發（重新開始），但畫面沒有顯示（D1 的 B7） | 這一下確實打中了，只是沒扣血 | — |
| C11 | 效果收到 NaN 或負數的傷害時回傳 0 | 契約要求結果不是 NaN、不是負數 | `DamageMultiplier.apply` |

## 3. 結構摘要

```
core/combo/                                  純 Java（CoreBoundaryTest 守住）
  ComboInput            enum WALK, RUN
  InputClassifier       classify(StatReader) → ComboInput           移動速度 ≥ 門檻是 RUN
  ComboEffect           interface: double apply(double damage)      Strategy
    └ DamageMultiplier  record(factor)
  ComboDefinition       record(id, name, first, second, windowTicks, effect)
  ComboCatalog          of(List) / find(first, second) / all()      id、順序不重複
  ComboTracker          record(ComboCatalog, ComboInput, tick) → Optional<ComboDefinition>   狀態機
  ComboLibrary          current() / replace(ComboCatalog)           快照，整份換掉
data/
  JsonFolder            exists() / files() / write(id, json)        武器和 combo 共用（WeaponFolder 改用它）
  ShippedFiles          text(folder, id)                            DefaultWeapons、DefaultCombos 共用
  ComboJson             read(id, text, ComboEffectTypes) → ComboDefinition   用 WeaponJson.parse/string/number
  ComboEffectTypes      read(effect) → ComboEffect；STANDARD = { damage_multiplier }   用名稱查的 Factory
  ComboLoader           read(files) / load(folder) → ComboCatalog；錯誤 → WeaponLoadException("combo files", ...)
  DefaultCombos         IDS = lunge, follow_up；installIfMissing(folder)；catalog()
  ComboFiles            load(folder, loader)（啟動）/ reload()（指令）/ library()
content/ModCombos       RUN_SPEED = 0.12、INPUTS、FULL_CHARGE = 0.9
forge/
  ComboListener         AttackEntityEvent → Swing(目標, 輸入, 蓄滿, tick)
                        LivingHurtEvent（最低優先序）→ 同一個目標、同一 tick、蓄滿 → ComboTracker
                          → 觸發：setAmount(effect.apply(...))、observer.comboTriggered(...)
                        LivingDeathEvent、PlayerLoggedOutEvent → 忘掉這個玩家
  ReloadCommand         /mobarmory reload：武器（原本的）＋ combo（新的）
  CombatObserver        ＋ default comboTriggered(target, attacker, combo)
  feedback/HitFeedbackListener   comboTriggered → 記下；LivingDamageEvent → HitReport.withCombo(名稱)
  feedback/ActionBarDisplay      有 combo → 後面接 translatable("mobarmory.hit.combo", 名稱)
```

一下攻擊的流程：

```
揮擊開始 AttackEntityEvent ─▶ ComboListener 記下：目標、走或跑（讀移動速度）、蓄滿了沒
CriticalHitEvent ─▶ R1 暴擊（×1.5）
LivingAttackEvent ─▶ R1 迴避（取消就到此為止，這一下不算）
LivingHurtEvent（最低優先序）─▶ ComboListener：是主目標、同一 tick、蓄滿 → ComboTracker.record
                                  完成 combo → 傷害 × 倍率，通知 CombatObserver
LivingDamageEvent ─▶ D1：HitReport（含 combo 名稱）─▶ action bar「Cow -1.5 ❤ 7.5/10 · Lunge」
打中之後 ─▶ 原版停止疾跑（所以輸入一定要在揮擊開始時讀）
```

## 4. 測試報告

### 各層數量

| 層級 | 測試類別 | 數量 | 結果 |
|---|---|---|---|
| Component | `InputClassifierTest` 14（17 的速度表 7 列、門檻邊界、速度藥水的原版算法、NaN／0／負數、門檻寫錯）、`ComboCatalogTest` 6（用順序找、id 重複、順序重複、定義的規則、倍率、`ComboLibrary` 整份換掉）、`JsonFolderTest` 4 | 24 | 全過 |
| Module | `ComboTrackerTest` 15（18 的 H1～H3、B1～B4、B11、B15、B16，加上同一 tick、時間倒退、每個 combo 自己的時間窗、reload 後下一下就用新清單、沒有 combo 檔）、`ComboLoaderTest` 21（內附檔就是決定好的 combo、**多一個檔就多一種 combo**、刪光、格式欄位可省略、14 種寫錯的檔各自的訊息、檔名、兩個檔同一個順序、一次列出所有問題）、`ComboFilesTest` 4（第一次啟動、啟動時寫錯、reload 新增／刪除檔、reload 時寫錯） | 40 | 全過 |
| 契約 | `ComboEffectContractTest` 7：每一種效果（`STANDARD` 的每個名稱一個樣本）和每個內附 combo 的效果都跑同一組：12 種怪數字不丟例外、不變 NaN、不變負數；0.1～100 的傷害結果是正數而且不遞減；每種效果都有樣本 | 7 | 全過 |
| D1 擴充 | `HitTextTest` ＋2（兩邊都帶 combo 名稱、沒有 combo 時和 D1 一模一樣、暴擊＋combo）；`HitTextContractTest` ＋1（`mobarmory.hit.combo` 在 `en_us.json` 裡） | 3 | 全過 |
| System（遊戲內） | `ComboGameTests` 7 個，真的揮擊、真的疾跑、真的遊戲 tick、真的 `/mobarmory reload` | 7 | 全過 |
| 既有 | 單元 322、遊戲內 57（R1、R2、R2b、R4、R6、D1） | 379 | 全過（D1 的測試只把幾個 helper 從 private 改成 package-private，給 combo 測試共用） |
| **合計** | 單元 396 ＋ 遊戲內 64 | **460** | **全過** |

**測試真的抓得到錯**：

- 把 `ComboListener` 故意改壞兩次再跑：
  - 拿掉「沒蓄滿不算」：`combo_counts_only_landed_full_swings` 失敗，訊息是「B5: a weak swing is no combo, got Lunge」。
  - 拿掉「只算主目標、用過就丟」：同一個測試失敗，訊息是「B10: the walk after Lunge is plain: expected 1.0 but was 1.3」，表示橫掃到的旁邊那隻被當成一下奔跑揮擊。
- 兩次都改回後全過。

### 遊戲內系統測試在測什麼

假玩家真的用 `player.attack` 揮擊。奔跑揮擊是揮擊前 `setSprinting(true)`：原版會加上 ×1.3 的移動速度修飾，和按疾跑鍵走同一條路。combo 是從遊戲資料夾 `config/mobarmory/combos/` 讀進來的內附檔。

| 測試 | 對應 | 內容 |
|---|---|---|
| `combo_order_decides_the_combo` | H1、H2、H3、H7 | 空手打牛：走 1.0 → 跑 **1.5 · Lunge**；跑 1.0 → 走 **1.3 · Follow-up**。action bar 是 `1.5 / 7.5 / 10` 後接 Lunge、`1.3 / 7.7 / 10` 後接 Follow-up。奔跑揮擊打中後原版已經停止疾跑，仍然算奔跑 |
| `combo_window_in_real_ticks` | O5、B1 | 真的等遊戲 tick：走後 20 tick 跑 → Lunge；走後 40 tick 跑 → 1.0 沒有 combo，而且這一下變成新的第一下（接著走 → Follow-up） |
| `combo_heavy_sword_is_too_slow_to_run` | B12、Q4 A | 拿重劍：走路 0.07、疾跑 0.091，低於 0.12 → 走、「跑」兩下傷害一樣，沒有 combo。放下重劍：同樣的動作是 Lunge |
| `combo_counts_only_landed_full_swings` | B5、B8、B19、B10 | 走之後：沒蓄滿的跑（弱的一下）、揮擊前就被取消的跑（像迴避）、被另一個模組在 `LivingHurtEvent` 取消的跑，都不算，也不取代第一下；接著蓄滿的跑 → Lunge。同一 tick 模擬橫掃打到旁邊的牛，不算一下（下一個走是普通的 1.0） |
| `combo_per_player_any_target_no_chain` | B15、B14、B11 | A 走、B 跑 → B 沒有 combo；A 跑另一隻牛 → Lunge；接著 A 走 → 沒有 Follow-up（不連鎖） |
| `combo_stacks_with_crit` | B13、O3 | 暴擊率 100%：走 1.5（暴擊）→ 跑 1 × 1.5 × 1.5 = **2.25**，金色 `CRIT` 行後接 Lunge（`2.3 / 6.3 / 10`） |
| `combo_new_file_adds_a_combo` | J1、O7 | 走→走原本不是 combo。在 `combos/` 放 `double_slash.json`（走→走 ×2），執行真的 `/mobarmory reload` → 走 1.0 → 走 **2.0 · Double Slash**。刪檔再 reload 後回到兩個 combo。在自己的批次、一個 tick 內做完，其他測試看不到多出來的 combo |

### Given / When / Then 對照（[18](18-r3-combo-given-when-then.md) 的每一列）

層級：C = Component、M = Module、K = 契約、S = System（遊戲內）。

| # | 情境 | 預期 | 測試 | 結果 |
|---|---|---|---|---|
| H1 | 走 → 1 秒內跑 | Lunge，1.0 × 1.5 = 1.5，action bar 有名稱 | M `h1WalkThenRunIsLunge`、S `combo_order_decides_the_combo` | 通過 |
| H2 | 跑 → 1 秒內走 | Follow-up，1.3，和 H1 不同 | M `h2RunThenWalkIsFollowUp`、S `combo_order_decides_the_combo` | 通過 |
| H3 | 只打一下 | 沒有 combo | M `h3OneSwingIsNoCombo`、S 同上 | 通過 |
| H4 | 短刀 走→跑 | 4.5 | C `damageMultiplier`（倍率對任何攻擊力都一樣） | 通過* |
| H5 | 速度藥水走路（0.12） | 算奔跑 | C `speedTable`、`speedPotionUsesMinecraftsArithmetic` | 通過* |
| H6 | 打 R4 怪物 | 照樣觸發 | —（combo 不看目標種類；B14 測到換目標也算） | 未在遊戲內測 |
| H7 | 疾跑打中後原版停止疾跑 | 仍然算奔跑 | S `combo_order_decides_the_combo`（檢查打中後 `isSprinting()` 是 false，仍然觸發 Lunge） | 通過 |
| B1 | 2 秒後才跑 | 不觸發，這一下變成新的第一下 | M `b1TooLateStartsOver`、S `combo_window_in_real_ticks` | 通過 |
| B2 | 剛好 30 tick | 觸發；31 tick 不觸發 | M `b2WindowEdge` | 通過 |
| B3 | 走→走 | 不觸發，第二下取代第一下 | M `b3WalkWalkReplacesTheFirst`、S `combo_new_file_adds_a_combo`（加檔之前） | 通過 |
| B4 | 跑→跑 | 不觸發 | M `b4RunRunIsNoCombo` | 通過 |
| B5 | 第二下沒蓄滿 | 不算，不取代第一下 | S `combo_counts_only_landed_full_swings`（並用故意改壞的版本確認會失敗） | 通過 |
| B6 | 第一下沒蓄滿 | 不算 | 和 B5 同一條程式路徑 | 未單獨測 |
| B7 | 對空氣揮 | 不算 | —（原版對空氣揮擊不會呼叫 `attack`，不會有 `LivingHurtEvent`） | 未測 |
| B8 | 第二下被迴避 | 不算，也不消耗 | S `combo_counts_only_landed_full_swings`（用 `LivingAttackEvent` 取消，和迴避同一個時間點） | 通過 |
| B9 | 無敵時間內完全沒扣血 | 不算 | —（原版在 `LivingHurtEvent` 之前就結束） | 未測 |
| B10 | 橫掃打到旁邊的 | 只有主目標算 | S `combo_counts_only_landed_full_swings`（模擬橫掃；故意改壞的版本會失敗） | 通過 |
| B11 | Lunge 後馬上走 | 不連鎖 | M `b11NoChaining`、S `combo_per_player_any_target_no_chain` | 通過 |
| B12 | 重劍疾跑 0.091 | 算走路，打不出奔跑 combo | C `speedTable`、S `combo_heavy_sword_is_too_slow_to_run` | 通過 |
| B13 | 暴擊＋Lunge | 相乘 | S `combo_stacks_with_crit` | 通過 |
| B14 | 第二下打別隻 | 照樣觸發 | S `combo_per_player_any_target_no_chain` | 通過 |
| B15 | A 走、B 跑 | 都不觸發 | M `b15TrackersAreSeparate`、S `combo_per_player_any_target_no_chain` | 通過 |
| B16 | 死亡、登出後 | 重新開始 | M `resetForgetsTheLastSwing`；`ComboListener` 在 `LivingDeathEvent`、`PlayerLoggedOutEvent` 忘掉這個玩家 | 遊戲內未測 |
| B17 | 射箭 | 不算 | —（`ComboListener` 只認近戰） | 未測 |
| B18 | 怪物連續攻擊 | 沒有 combo | —（`ComboListener` 只認玩家） | 未測 |
| B19 | 另一個模組取消第二下 | 不算 | S `combo_counts_only_landed_full_swings` | 通過 |
| B20 | Lunge 剛好打死 | 照樣觸發，顯示 0 | —（D1 的 B11 測過打死時顯示 0） | 未測 |
| J1 | 多放一個 JSON | 多一種 combo，不用改 Java | M `oneMoreFileIsOneMoreCombo`、S `combo_new_file_adds_a_combo` | 通過 |
| J2 | 第一次啟動 | 裝兩個預設檔 | C `firstStartInstallsTheDefaults`、M `firstStart` | 通過 |
| J3 | 資料夾已存在、檔案刪光 | 不補回，沒有 combo | C `anExistingFolderIsNeverTouched`、M `noFilesIsNoCombos`、`noCombosNeverTrigger` | 通過 |
| J4 | 啟動時寫錯 | 開不起來，寫出檔名 | M `aWrongFileStopsTheStart` | 通過 |
| J5 | reload 時寫錯 | 全部不變 | M `aWrongFileOnReloadChangesNothing` | 通過 |
| J6 | reload 新增／刪除檔 | 立刻生效 | M `reloadAppliesNewAndDeletedFiles`、S `combo_new_file_adds_a_combo` | 通過 |
| J7 | 各種寫錯的欄位 | `檔名: 欄位: 原因` | M `wrongFileNamesFileAndField`（14 種）、`badFileName`、`everyProblemAtOnce` | 通過 |
| J8 | 兩個檔同一個順序 | 拒絕，寫出兩個 id | C `duplicateOrderFails`、M `twoFilesWithTheSameOrder` | 通過 |
| J9 | 每個檔自己的時間窗 | 10 tick 的 combo 第 11 tick 不觸發 | M `eachComboHasItsOwnWindow` | 通過 |

\* H4、H5 只有單元測試，數字規則和遊戲內測過的 H1、B12 是同一條路。

## 5. SOLID／Pattern 檢查

每一步的檢查在 prompt 紀錄。這裡是整體的結論。

| 原則 | 有沒有違反 | 說明 |
|---|---|---|
| S | 沒有 | 每個類別只有一個改變的理由。<br>**規則**：`ComboTracker`（出招狀態）、`InputClassifier`（走或跑）、`ComboCatalog`（清單合不合法）<br>**檔案**：`ComboJson`（格式）、`ComboLoader`（收集問題）、`ComboFiles`（啟動和 reload 的時機）、`JsonFolder`（I/O）<br>**接上遊戲**：`ComboListener`（Forge 事件）<br>**顯示**：還是 D1 的類別<br>R1 的 `CombatEffectListener` 一行沒改，combo 有自己的監聽 |
| O | 沒有 | 新 combo 是一個 JSON 檔，Java 不用改。新效果種類是一個類別加 `ComboEffectTypes` 一行，讀檔、監聽、顯示都不改。D1 只多一個欄位和一個預設方法，舊的呼叫不變。R2b 的類別只為了共用而改，行為不變，既有測試全過 |
| L | 沒有 | `ComboEffectContractTest`：每一種效果、每個內附檔的效果跑同一組檢查。`WeaponFolder` 改用 `JsonFolder` 後，`WeaponStoreContractTest` 照樣通過。`CombatObserver.NONE` 和 `HitFeedbackListener` 都能交給 `ComboListener` |
| I | 沒有，有一點要說明 | `ComboEffect`、`ComboEffectTypes.Reader` 都只有一個方法。`CombatObserver` 從兩個方法變三個，但三個都有什麼都不做的預設，實作者只覆寫自己要的，這是 17 第 3 節（Q3 A）定的做法。如果之後觀察者的種類變多，可以把 combo 通知拆成自己的介面 |
| D | 沒有 | `core/combo` 不知道 JSON、檔案和 Minecraft（`CoreBoundaryTest`）。`ComboTracker` 每一下拿到的是 `ComboCatalog`，不知道它從哪裡來。`ComboListener` 依賴 `ComboLibrary`、`InputClassifier`、`CombatObserver`，都由 `MobArmoryMod` 注入。它也依賴具體類別 `StatAttributeBridge`（用來讀玩家的移動速度），和 R1 的 `CombatEffectListener` 一樣。兩個都在 `forge/` 層，都由 `MobArmoryMod` 注入同一個 `STATS`。這裡沒有抽介面，因為 `forge/` 本來就是接 Minecraft 的那一層（2026-11-07 依 review-04 S6 補上） |

| Pattern | 用在哪裡 | 理由 |
|---|---|---|
| **Strategy** | `ComboEffect`／`DamageMultiplier` | 學長和審查員要求；擊退、緩速是下一個候選 |
| **用名稱查的 Factory** | `ComboEffectTypes`：檔案寫 `type`，它找到對應的 `Reader` 建出效果 | 玩家只能挑已經寫好的效果種類（主 session 第 2 點），新種類只加一行 |
| **狀態機** | `ComboTracker`：上一下＋時間 → 觸發或換成新的第一下 | 順序比對可以不開遊戲測 |
| **快照** | `ComboLibrary`：reload 時整份換掉 | 和 R2b 的 `EquipmentLibrary` 同一個做法，讀的人不會拿到一半新一半舊 |
| **Observer** | `CombatObserver.comboTriggered` → `HitFeedbackListener` | 沿用 D1；combo 不用知道有沒有人在顯示 |
| **沿用 R2b 的讀檔規則** | `JsonFolder`、`ShippedFiles`、`WeaponJson.parse`、`WeaponLoadException` | 主 session 要求「不要另外寫一套」；通用的部分抽出來共用，武器也改用同一份 |

**命名的債**：`WeaponFormatException`、`WeaponLoadException` 現在也用在 combo 檔，名稱是 R2b 時取的，見第 7 節。

## 6. 沒有覆蓋的範圍

測試只證明已定義、已覆蓋的行為。以下沒有被自動測試證明：

- **客戶端畫面**：action bar 上「· Lunge」的位置和顏色沒有在 `runClient` 確認。自動測試證明的是伺服器送出的語系字串、數字和 combo 名稱。
- **真的按疾跑鍵**：遊戲內測試用 `setSprinting(true)`，和按鍵走同一條原版程式。但真人操作時「疾跑中揮擊」的時機感沒有測。
- **真的橫掃**：B10 是在同一 tick 用玩家的近戰傷害打旁邊那隻來模擬，沒有拿原版劍真的觸發橫掃。
- **B6、B7、B9、B16～B18、B20、H6**：見第 4 節的表。B16 的「忘掉玩家」在程式裡，只有單元層的 `reset` 被測到。
- **真正的多人**：沒有開兩個客戶端；B15 用兩個假玩家。
- **效能**：每一下多建一個小 record、查兩次 `WeakHashMap`，沒有量測。

## 6-1. 實作時發現的問題（不是 R3 的程式，給架構師）

**R2b 的 `web_admin_saves_through_the_game` 在機器忙的時候會失敗，而且會連帶弄壞後面的測試。**

- 經過：最後確認時連跑兩次 `clean build runGameTestServer`，各有 3～4 個遊戲內測試失敗：
  - `web_admin_saves_through_the_game`：等網頁伺服器回應，等到第 202 tick 還沒等到。
  - 接著 `h1_h2_prototype_mob_stats`、`mob_reload_keeps_stats_once` 讀到狂戰士攻擊 20，預期是 10。
  - 第一次還有 `mob_wanders_without_players`。
- 程式沒改、只重跑：連續 3 次全過，最後一次就是本報告的數字。
- 原因：
  - 遊戲內測試的伺服器會盡量快地跑 tick，這個測試等 200 tick，實際上只有約 1 秒（log 的時間戳記：同一秒內開始和失敗）。
  - 同時間其他 session 也在這台機器跑 Java，HTTP 來回一旦超過 1 秒就失敗。
  - 失敗時，網頁那次存檔（重劍攻擊 +16）其實已經生效，但還原（`LiveEditGameTests.restore()`）寫在等待成功之後的那一步，沒有執行到。後面的批次就讀到被改過的重劍：4 ＋ 16 = 20。
- 和 R3 無關：失敗的那兩次，combo 的批次都排在網頁批次前面，所以我一度懷疑是 combo 測試執行的 `/mobarmory reload` 造成的。但之後兩次同樣是 combo 在前，都全過，排除了這個可能。
- **合併 main 後已解決**：架構師在 main `3789ffb` 修了這個測試的時間，合併後用的是修過的版本（第 8 節）。
- 原本的建議（R2b 的檔案，這次沒有改）：
  - 等待改成看實際時間，例如等到 10 秒。
  - `restore()` 改成不管成功或失敗都會執行，避免一個測試失敗連帶弄壞其他測試。

## 7. 之後（這次不做）

| 項目 | 說明 |
|---|---|
| 網頁編輯 combo（R6） | 主 session 第 4 點：這次不做。combo 已經是 JSON 檔，網頁要做時加 `ComboJson.write`、API 和表單，讀檔、驗證和 reload 都已經有了 |
| 共用的例外改名 | `WeaponFormatException` → `DataFormatException`、`WeaponLoadException` → `DataLoadException`。會動到架構師的 `web/`、`WeaponEditor` 和測試，今晚架構師也在 main 上改東西，所以這次不改 |
| 新的效果種類 | 擊退、緩速（19 的 Q5 B）：各一個 `ComboEffect` 類別＋`ComboEffectTypes` 一行 |
| 新的輸入、三步以上的 combo | 跳躍揮擊、蹲下揮擊：`ComboInput` 加值、`InputClassifier` 加規則；三步要改成檔案格式 2 |
| 每把武器自己的 combo（Q7 B） | 可以在 combo 檔加一個「哪些武器能用」的欄位，不用改武器檔 |
| 奔跑門檻放進設定檔 | 目前在 `ModCombos`（C7） |

## 8. 合併到 main（2026-11-07 夜間，review-04 X1）

- **做法**：在 `feature/r3-combo` 執行 `git merge --no-ff 56423bd`（main：段落 E 的 `6a3e1fc` 加上架構師修的文件）。合併 commit 是 `130240d`，架構師之後把 main 快轉過來。沒有 rebase，沒有動 main，沒有 push。每一步的 SOLID／Pattern 檢查見 [prompts/r3-combo-round-03-merge.md](prompts/r3-combo-round-03-merge.md)。
- **測試**：`gradlew clean build runGameTestServer` 全過。

| | main `56423bd` | 分支（合併前 `7c8b082`） | 合併後 |
|---|---|---|---|
| 單元 | 375 | 396 | **450**（375 ＋ combo 的 74 ＋ 合併時新增的 1） |
| 遊戲內 | 60 | 64 | **67**（基準 57 ＋ main 的 3 ＋ combo 的 7） |
| 伺服器報告 | 61 | 65 | **68**（含原版 `always_pass`） |

review-04 預估的是單元 449。多出的 1 個是合併時新增的 `JsonFolderTest.aMissingFolderListsNothing`：數值資料夾不存在時要回空的，這個行為現在由共用的 `JsonFolder` 負責，所以補一個測試守住。

### 文字衝突（3 個檔案、5 處，照 review-04 3-1）

| 檔案 | 怎麼解 |
|---|---|
| `G/MobArmoryGameTests.java` | 兩行註冊都留：`StatFileGameTests`、`ComboGameTests` |
| `M/forge/MobArmoryMod.java`，import 區 | 全留：`ComboEffectTypes`、`ComboFiles`、`ComboLoader`、`JsonFolder`、`StatFolder`、`StatSetup` |
| `M/forge/MobArmoryMod.java`，啟動 log | 合成一行：`{} stats from {}, {} pieces of equipment from {}, {} mobs, {} combos from {}`。數值數量用 main 的 `STATS.catalog().size()`，玩家加的數值才會算進去 |
| `M/data/WeaponFolder.java`（2 處） | 用分支的版本（`JsonFolder`） |

### 語意衝突：兩份一樣的寫檔程式（照 review-04 3-2）

main 的段落 E 從 `WeaponFolder` 抽出了一個 package-private 的共用類別，分支則抽出了 `JsonFolder`，兩者是同一段程式抽出來的兩份。合併後收成一份：

- `StatFolder` 改成持有 `new JsonFolder(root.resolve("stats"))`：讀用 `files()`，寫用 `write(id.value(), json)`。`StatStoreContractTest` 照樣通過。
- 刪掉 main 抽出的那個共用類別。程式碼裡（`src/`）搜尋它的名字沒有結果，原子寫入只剩 `JsonFolder` 一份。
- `JsonFolder.list` 在資料夾不存在時也回空的，和原本那份一樣，所以三種資料夾的行為一致。新增 `JsonFolderTest.aMissingFolderListsNothing` 守住這一點。
- `JsonFolder.write` 的 id 規則從 `[a-z0-9_]{1,64}` 改成 `[a-z][a-z0-9_]*`，和 `EquipmentId`、`StatId` 一樣，沒有長度上限。
- 同時把 combo id（`ComboDefinition`）也改成同一條規則，否則會有 combo 讀得進來、卻寫不出去的 id，例如 `2hit`。影響：以數字或底線開頭的 combo 檔名會被拒絕，訊息寫出檔名。`ComboCatalogTest`、`JsonFolderTest` 加了這兩種例子。

### 自動合併、看過一眼的檔案

- `ReloadCommand`、`WeaponJson`、`WeaponLoadException`、`DefaultWeapons`、`CombatObserver`、D1 的 6 個類別、`en_us.json`、`FeedbackGameTests`：只有分支改過，內容就是分支的版本。
- `build.gradle`、`GeneratedWeaponPack`、`WebAdminGameTests`：只有 main 改過。`WebAdminGameTests` 用的是 main `3789ffb` 修過的版本，第 6-1 節的逾時問題跟著解決。
- combo 的遊戲內測試讀 `run-gametest/config/mobarmory/combos/`。main 的 `build.gradle` 只複製 `src/gametest/config/` 的數值檔，combos 資料夾一開始不存在，預設檔照常裝上。這次 7 個 combo 測試都通過。

### 文件（照 review-04 3-3）

- `README.md`：
  - combo 那一列改「已完成」，並新增「加一個 combo」一節。
  - `/mobarmory reload` 也重新載入 combo；網頁的重新載入只載入武器。
  - 多人規則補一段：combo 只有伺服器需要。
  - 刪掉「R3 combo 還沒進 main」，改成「combo 不能在網頁新增或修改」。
  - 更新測試數字，索引改連到 17～20。
- `docs/boundary/README.md`：加 R3 一節。
- 16 號第 9 節 I 列：`CombatObserver` 改成三個方法。
- 27 號：S 列和結構表改成 `JsonFolder`。
