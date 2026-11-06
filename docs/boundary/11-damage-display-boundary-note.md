# 邊界說明：砍怪傷害顯示（戰鬥回饋，action bar）

> 狀態：**已確認**（2026-11-06）。Q1 A、Q2 A、**Q3 B（劃掉浮字和 `HitJournal`）**、**Q4 C（只做 action bar）**、Q5～Q8 照建議的 action bar 部分、O1～O10 照建議（O7 不適用）。決定紀錄在 [13-damage-display-open-questions.md](13-damage-display-open-questions.md)。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [12-damage-display-given-when-then.md](12-damage-display-given-when-then.md)。
> 工作位置：worktree `java_minecraft_model/mod-damage-hud`，分支 `feature/damage-hud`（從 main 的 `cd97723` 分出）。

## 1. 需求翻譯

### 真正的需求

- 示範時要讓人**看得出數字差異**：拿重劍和短刀砍同一隻怪，或用 R6 改了數值以後，畫面上的傷害數字不一樣；暴擊和迴避也要看得出來。審查員的評語是：這個功能的價值在於讓「暴擊和迴避有效果」看得見。
- 本質是：**每一下攻擊結算完以後，把「結果」交給顯示方式**。結果包括最終傷害、有沒有暴擊、有沒有被迴避、對方剩多少血。
- 這次只做一種顯示方式：畫面下方的 **action bar**（Q4 C）。之後 R3 combo 的名稱也要顯示在同一行。
- 「怎麼顯示」以後會變（combo 名稱、R6 網頁的戰鬥紀錄），「結果怎麼算」已經由 R1／R2 決定、不能變。這兩件事要切開。

### 沒講到、我本來會替你選的事（已決定）

| 題目 | 決定 |
|---|---|
| 顯示方式 | 只有 action bar（Q4 C） |
| 誰看得到 | 攻擊的玩家和被打的玩家（Q5） |
| 顯示什麼 | 目標名稱、最終傷害、CRIT 或 MISS、對方剩餘血量／最大血量（Q6） |
| 哪些傷害 | 生物造成的近戰和投射物，環境傷害不顯示（Q7 A） |
| 多人同步 | 伺服器判定，原版系統訊息封包（Q8 A） |
| 數字格式、溢出、無敵時間、創造模式、吸收心、語言、開關、跳劈 | O1～O6、O8～O10 照建議 |

### 這次要做／未來可能變

| 這次要做 | 未來會變，所以要在這裡切邊界 |
|---|---|
| 每一下攻擊結算後產生一份「結果」（`HitReport`） | **結果要多帶什麼**（R3 combo 名稱）→ 結果是一個 record，加欄位只動它和格式化 |
| action bar 一種顯示 | **顯示方式會增加**（R6 網頁的戰鬥紀錄、聊天紀錄）→ 每種顯示一個類別（Observer） |
| 決定哪些傷害要顯示、給誰看（Q5、Q7） | **規則會改** → 規則放 `core/`，純 Java，可以單元測試 |
| 數字格式、CRIT 和 MISS 的文字（Q6、O1～O3、O10） | **格式會改** → 只在一個格式化類別裡 |
| — | **新的戰鬥效果**（R1 再加一個 `StatEffect`）→ 只要它的結果是「改傷害、取消、暴擊」三種之一，顯示端不用改 |
| — | **R4 自訂怪物** → 顯示對所有生物生效，自訂怪物自動涵蓋 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下是在 Forge 26.3-66.1.0 和它附的 Minecraft 26.3 原始碼裡確認過的。

### 一下攻擊在 26.3 的流程

伺服器端，`LivingEntity.hurtServer`（玩家是 `ServerPlayer.hurtServer` → `Player.hurtServer` → 同一條路）：

```
（玩家近戰才有）Player.attack
   AttackEntityEvent            ← 我們記下蓄力（N2）
   CriticalHitEvent             ← 我們決定玩家暴擊（O4、N2）
   └→ target.hurtServer(...)
（被打的是玩家時）伺服器端無敵、PvP 關閉 → 結束
LivingEntity.hurtServer
   LivingAttackEvent            ← 我們的迴避在這裡取消（O3）；此時還沒檢查創造模式、無敵時間
   無敵／創造模式／已死亡／抗火 → 結束（不扣血）
   盾牌格擋、凍傷加成、頭盔減傷
   無敵時間：0.5 秒內第二下只扣「比上一下多的部分」，沒有比較大就直接結束
   actuallyHurt
      LivingHurtEvent           ← 我們決定怪物和投射物的暴擊（×1.5）；數值是扣護甲前
      傷害 ≤ 0 → 結束
      扣護甲、扣附魔保護和抗性、扣吸收心
      LivingDamageEvent         ← 最終值：這一下真正要扣的生命值
      setHealth(生命值 − 最終值)
```

（來源：`LivingEntity.java` 的 `hurtServer`、`actuallyHurt`，`Player.java` 的 `attack`、`hurtServer`、`actuallyHurt`，`ServerPlayer.java` 的 `hurtServer`；Forge 的 `LivingDamageEvent` 說明「armor, potion and absorption modifiers have already been applied … this is FINAL value」。）

### 拿得到什麼

| 要顯示的東西 | 從哪裡拿 | 備註 |
|---|---|---|
| 最終傷害 | `LivingDamageEvent.getAmount()`，用**最低優先序（`Priority.LOWEST`）的一般監聽**拿到其他模組都改完以後的值。原本打算用監看式監聽（`addListener(ObjBooleanBiConsumer)`），實作時發現不能用，見第 6 節 T5 | 吸收心擋掉的部分**不**算在內；無敵時間內的追加攻擊只會是差額；被其他監聽取消的事件不會傳到最低優先序的監聽 |
| 對方剩餘血量 | 同一個事件裡：`getHealth() − 最終值`，最低 0；最大血量 `getMaxHealth()` | 此時血量還沒扣 |
| 有沒有被迴避 | 我們自己的 `CombatEffectListener` 在 `LivingAttackEvent` 取消時就知道 | 外部的監看式監聽也看得到「被取消」，但分不出是不是我們的迴避 |
| 有沒有暴擊 | 玩家近戰：`CriticalHitEvent`；怪物和投射物：我們的 `LivingHurtEvent` 處理 | 兩者都在 `CombatEffectListener` 裡決定。怪物暴擊是直接把數字乘 1.5，外部監聽**看不出來** |
| 攻擊者、被打的是誰 | `DamageSource.getEntity()`（近戰是本人，射箭也是射手）、事件的 `getEntity()` | 和 R1 的 `CombatEffectListener.kindOf` 同一套判斷 |
| 送 action bar | `ServerPlayer.sendOverlayMessage(Component)`（原版 `ClientboundSystemChatPacket`，overlay） | 一次一行，下一則蓋掉上一則，約 3 秒後淡出 |

**要注意的時間差**：玩家近戰是「先決定暴擊，再呼叫 `hurtServer`」，所以會有「判定暴擊、結果被迴避」或「判定暴擊、但對方在無敵時間內沒扣血」的情況。暴擊標記必須只屬於同一下，不能留給下一下（見 [12](12-damage-display-given-when-then.md) B8、B9）。

另外，原版本來就有玩家暴擊時的暴擊粒子和音效（我們的暴擊率暴擊也會觸發），以及打中時的「深色愛心」粒子，兩者都**不能顯示數字**，這次不另外加粒子。

## 3. 變更邊界（已確認）

### 分層

```
core/feedback/      純 Java，不 import Minecraft（CoreBoundaryTest 會自動守住新資料夾）
  HitReport           一下攻擊的結果：傷害種類、攻擊者和被打者是玩家還是生物、結果（命中／暴擊／迴避）、最終傷害、剩餘血量、最大血量
  HitFilter           哪些結果要顯示（Q7、O4、O6）、給誰看（Q5）
  HitText             把結果變成 action bar 的一行：用哪個語系字串、數字格式、顏色（Q6、O1～O3、O10）
  Party、Viewer、HitLine、Tone   上面三個用到的小型別
forge/
  CombatObserver      新的小介面：CombatEffectListener 結算完一下就通知它（攻擊結算、攻擊被取消）
  CombatEffectListener  只加「通知 observer」的幾行，規則和行為不變；不傳 observer 時和現在完全一樣
  feedback/
    HitFeedbackListener  實作 CombatObserver；監聽 LivingDamageEvent 拿最終傷害，配對這一下的暴擊和迴避，組成 HitReport，交給每個 HitDisplay
    HitDisplay           介面：void show(HitReport report, 被打的生物, 攻擊者)
    ActionBarDisplay     送 action bar 給攻擊的玩家和被打的玩家
  MobArmoryMod        接線兩行
content/            不動（這個功能沒有新的資料）
```

`HitJournal`（Q3 B 劃掉）原本負責「暴擊標記只屬於這一下」。這件事改由 `HitFeedbackListener` 用一個 `WeakHashMap` 記「每個目標最近一次的攻擊結算」：每一次結算（不論有沒有暴擊）都會蓋掉上一次，迴避時清掉，扣血時取用一次就刪掉，另外比對攻擊者和遊戲 tick。B8、B9 由遊戲內測試守住。

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `core/feedback/`、`forge/feedback/`、`forge/CombatObserver`<br>修改 `forge/CombatEffectListener`（只加通知；`kindOf` 改成 public 給 `forge/feedback` 共用，不改規則）、`forge/MobArmoryMod`（接線）、`lang/en_us.json`（加 6 個 action bar 字串）<br>測試：`src/test` 加 `core/feedback` 的測試；`src/gametest` 加 `FeedbackGameTests`、`MobArmoryGameTests` 註冊一行、`test_instance/*.json`<br>文件：`docs/boundary/11～13`、`16-damage-display-behavior-report.md`、`prompts/` |
| 不准動的相依 | R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat`（`StatEffect`、`CombatOutcome`、`CombatContext`、`CombatResolver`）的公開介面和規則；暴擊 1.5 倍、迴避只閃生物造成的傷害（O3、O4）、暴擊要蓄滿（N2）<br>`content/`（R2b 會刪掉 `ModEquipment`，系統測試也不引用它）、`EquipmentStatApplier`、`StatsCommand`<br>`build.gradle`、Forge 和 Minecraft 本身（不用 Mixin、不用 Access Transformer）<br>`docs/boundary/README.md`（開發 session 維護）、`mod/`、`mod-r4-mobs/`、`PROGRESS.md`、`transcripts/` |
| 保留或新建的抽象介面 | 保留：`StatEffect`、`StatReader`（不改）<br>新建：`HitDisplay.show(HitReport, LivingEntity defender, LivingEntity attacker)`（Observer，每種顯示方式一個實作）<br>新建：`CombatObserver.attackResolved(Entity target, LivingEntity attacker, CombatOutcome outcome)`、`CombatObserver.hitCancelled(LivingEntity defender, DamageSource source)`（`CombatEffectListener` 把已經算出來的結果交出去） |
| 新功能放哪、用什麼形狀 | 新的顯示方式：新增一個 `HitDisplay` 實作，在 `MobArmoryMod` 加一行（Observer）<br>改顯示內容或格式：只改 `core/feedback/HitText`（若加新文字，再加語系檔）<br>改「哪些傷害顯示、給誰看」：只改 `core/feedback/HitFilter` |
| 改一處會牽動幾個檔案 | 新增一種顯示方式（例如 R6 網頁的戰鬥紀錄）：**2**（新類別、接線一行）<br>改數字格式：**1**；改文字：**2**（`HitText`、語系檔）<br>改顯示規則：**1**<br>R1 新增一個戰鬥效果：**0**（顯示端不用改） |

### SOLID 判斷（第一輪 Q1，已確認）

照最直接的寫法（在 `CombatEffectListener` 裡算完就順手送訊息），預期會出現這些徵兆：

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 戰鬥規則和畫面混在同一個類別，改顯示文字也要動 R1／R2 的戰鬥程式和它的測試 | S 單一職責 | 戰鬥只「交出結果」，顯示是另一個模組 |
| 每多一種顯示方式（combo、網頁紀錄），就在同一個處理器裡多一段 `if` | O 開放封閉 | 抽 `HitDisplay` 介面，新方式只新增實作 |
| 「誰看得到」「小數幾位」這些商業規則直接用 Minecraft 類別寫，不開遊戲就沒辦法測 | D 依賴反轉 | 規則和格式放 `core/feedback`，純 Java |

L 由測試保證（`HitText` 對任何結果都不丟例外、不印出 NaN 或負數）；I 目前的介面只有一兩個方法，不需要拆。

### Pattern 選擇（第一輪 Q2，已確認：A）

會變的是：**顯示方式**、**顯示規則和格式**。結果怎麼算不變。

| 選項 | 形狀 | 決定 |
|---|---|---|
| **A** | Observer：`CombatEffectListener` 多一個「結果通知」掛勾；`HitFeedbackListener` 在 `LivingDamageEvent` 拿最終傷害，組成 `HitReport`，通知每個 `HitDisplay`。規則和格式在 `core/feedback` | **採用** |
| B | 完全不碰 `CombatEffectListener`，只用 Forge 的監看式監聽推斷 | 怪物和投射物的暴擊看不出來 |
| C | 直接寫、不抽象 | 戰鬥和畫面混在一起 |

## 4. 實作順序

每一步做完都跑 `gradlew build`（單元測試）；動到 `forge/` 的步驟再跑 `runGameTestServer`。

1. `core/feedback`：`HitReport`、`HitFilter`、`HitText`，加單元測試（Component、契約）
2. `forge/CombatObserver`，`CombatEffectListener` 加通知；**先跑既有的 106 個單元測試和 18 個遊戲內測試，確認沒壞**
3. `forge/feedback`：`HitFeedbackListener`（拿最終傷害、配對暴擊和迴避、組成 `HitReport`）、`HitDisplay`、`ActionBarDisplay`，接線
4. 遊戲內系統測試：真的揮劍、真的扣護甲，用「記錄用」的顯示和記錄用的 action bar 送出方式驗證
5. `gradlew clean build runGameTestServer` 全過，寫行為報告 [16](16-damage-display-behavior-report.md)

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `HitText`：四捨五入、最少 0.1、溢出時剩餘 0、無限大和 NaN、最大血量的寫法、每種結果 × 每種觀看者用哪個語系字串和顏色<br>`HitFilter`：每種傷害種類 × 攻擊者（玩家、生物、無）× 被打者（玩家、生物）× 結果，要不要顯示、給誰看 |
| 契約 | `HitText` 對所有結果、所有觀看者、極端數值（0、極小、溢出、無限大、NaN、負數）都不丟例外，印出來的數字不會是 NaN、不會有負號、有扣血時不會是 0.0 |
| System（遊戲內） | 用假玩家真的揮劍（同 R1 的 `player_crit_uses_one_multiplier` 做法），攻擊力直接設成 1、3、7（等於空手、短刀、重劍；裝備給的攻擊力已經由 R2 的測試證明，這裡不引用 `ModEquipment`，避免和 R2b 衝突）：<br>・三種攻擊力打牛，結果分別是 1.0、3.0、7.0，action bar 的字串和數字正確（**示範要的「武器不同、數字不同」**）<br>・攻擊力 7 打護甲 10 的殭屍，結果 = 殭屍實際少掉的血 ≈ 5.18，顯示 5.2（證明是扣護甲後）<br>・暴擊率 100% 時是 CRIT；牛迴避在上限時有 MISS、沒扣血，而且 MISS 優先於暴擊（B8）<br>・暴擊被無敵時間吃掉後，下一下不會被誤標成 CRIT（B9）；無敵時間內只顯示差額（B1、B2）<br>・殭屍打玩家：被打的玩家收到「Zombie hit you」；迴避時收到「Dodged Zombie」；創造模式不顯示 MISS（B3）；吸收心全吸收時不顯示（B7）<br>・玩家打玩家：雙方各收到自己的那一行，旁邊的第三個玩家收不到（B14、B15）<br>・摔落等環境傷害不顯示；怪物打怪物沒有人收到 action bar（B5、B6）<br>・其他模組在 `LivingDamageEvent` 改了數值，顯示的是改過的值（B16）<br>・既有 18 個遊戲內測試照樣通過 |
| 手動（runClient） | action bar 的實際畫面和顏色。這輪沒有做（見行為報告「沒有覆蓋的範圍」） |

## 6. 技術風險

| # | 風險 | 處理 |
|---|---|---|
| T1 | 假玩家的 action bar 封包讀不到 | `ActionBarDisplay` 可以注入「送出方式」，遊戲內測試換成記錄用的送出方式，驗證真正組出來的語系字串和參數；正式遊戲用 `ServerPlayer::sendOverlayMessage` |
| T2 | 26.3 的假玩家（`makeMockServerPlayerInLevel`）一律回報創造模式，而且客戶端「還沒載入完成」時伺服器把他當成無敵 | 被打的玩家測試先送一個 `ServerboundPlayerLoadedPacket` 給假玩家、關掉 `abilities.invulnerable`，創造模式的測試則打開它 |
| T3 | 既有的遊戲內測試（例如 200 下殭屍攻擊的迴避測試）現在也會產生結果 | 沒有玩家參與時不送任何訊息，不影響既有斷言；第 2 步先跑一次確認 |
| T4 | 開發 session 在 `mod/` 同時開發，R2b 會先合進 main | 只有 `MobArmoryMod`（接線兩行）、`CombatEffectListener`（通知幾行）、`lang/en_us.json`、`MobArmoryGameTests`（註冊一行）可能和對方改到相鄰的行，都是加行；`content/` 不動，測試不引用 `ModEquipment` |
| T5 | **實作時發現**：EventBus 7.0.6 裡，一個事件如果只有監看式監聽、沒有任何一般監聽，整個事件會被設成「什麼都不做」，監看式監聽永遠不會被呼叫（`CancellableEventBusImpl.invalidateInvoker()` 只看一般監聽的清單是不是空的）。Mob Armory 原本沒有任何東西監聽 `LivingDamageEvent`，所以第一次跑遊戲內測試時 7 個新測試都收不到結果；只有 B16 那個測試自己掛了一般監聽，那一下才收得到 | 改用 `Priority.LOWEST` 的一般監聽。效果一樣：排在其他監聽之後讀數值（B16 驗證），被取消的事件不會傳過來。程式註解寫了原因 |
