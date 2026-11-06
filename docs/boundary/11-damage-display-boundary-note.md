# 邊界說明：砍怪傷害顯示（戰鬥回饋）

> 狀態：**草案**（2026-11-06），等待確認點的回覆，問題在 [13-damage-display-open-questions.md](13-damage-display-open-questions.md)。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [12-damage-display-given-when-then.md](12-damage-display-given-when-then.md)。
> 工作位置：worktree `java_minecraft_model/mod-damage-hud`，分支 `feature/damage-hud`（從 main 的 `cd97723` 分出）。`mod/` 由開發 session 做 JSON 武器設定和 R6，這份功能不碰那裡。

## 1. 需求翻譯

### 真正的需求

- 示範影片要讓觀眾**看得出數字差異**：拿重劍和短刀砍同一隻怪，或用 R6 改了數值以後，畫面上的傷害數字不一樣；暴擊和迴避也要看得出來。
- 所以本質是：**每一下攻擊結算完以後，把「結果」交給一個或多個顯示方式**。結果包括這一下的最終傷害、有沒有暴擊、有沒有被迴避、對方剩多少血。
- 「怎麼顯示」以後一定會變（看過影片才知道浮字好不好看、要不要 action bar、R6 網頁要不要戰鬥紀錄），「結果怎麼算」已經由 R1／R2 決定、不能變。這兩件事要切開。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [13](13-damage-display-open-questions.md)：

1. 顯示方式：頭上浮字、畫面下方的 action bar、聊天訊息，還是自己畫 HUD？（Q4）
2. 誰看得到：只有攻擊者，還是附近所有人？被打的是玩家時，他自己看不到頭上的字，怎麼辦？（Q5）
3. 顯示什麼：最終傷害、暴擊、迴避「MISS」、對方剩餘血量、扣護甲前的傷害？（Q6）
4. 哪些傷害要顯示：只有玩家造成的，還是怪物打玩家、怪物互打也要？摔落、著火要不要？（Q7）
5. 多人時怎麼同步：用原版實體和訊息，還是自訂網路封包？（Q8）
6. 數字格式：小數幾位、用生命值點數還是「幾顆心」（O1、O2）
7. 打死時的溢出傷害、無敵時間內的追加攻擊、盾牌擋下、吸收心（O3～O6）
8. 浮字停多久、怎麼動、連續被打時會不會疊在一起（O7）
9. 文字語言、要不要開關、跳劈和暴擊率暴擊要不要分開標示（O8～O10）

### 這次要做／未來可能變

| 這次要做 | 未來會變，所以要在這裡切邊界 |
|---|---|
| 每一下攻擊結算後產生一份「結果」（`HitReport`） | **結果要多帶什麼**（例如 R3 combo 第幾下）→ 結果是一個 record，加欄位只動它和格式化 |
| 浮字和 action bar 兩種顯示（依 Q4 決定） | **顯示方式會增減**（聊天紀錄、R6 網頁的戰鬥紀錄、客戶端自繪）→ 每種顯示一個類別（Observer） |
| 決定哪些傷害要顯示、誰看得到（依 Q5、Q7） | **規則會改**（看完影片覺得太吵）→ 規則放 `core/`，純 Java，可以單元測試 |
| 數字格式、暴擊和 MISS 的樣式（依 Q6、O1～O10） | **格式會改** → 只在一個格式化類別裡 |
| — | **新的戰鬥效果**（R1 再加一個 `StatEffect`）→ 只要它的結果是「改傷害、取消、暴擊」三種之一，顯示端不用改 |
| — | **R4 自訂怪物** → 顯示對所有生物生效，自訂怪物自動涵蓋 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下是在 Forge 26.3-66.1.0 和它附的 Minecraft 26.3 原始碼裡確認過的。

### 一下攻擊在 26.3 的流程

伺服器端，`LivingEntity.hurtServer`（玩家是 `Player.hurtServer`，結構相同）：

```
（玩家近戰才有）Player.attack
   AttackEntityEvent            ← 我們記下蓄力（N2）
   CriticalHitEvent             ← 我們決定玩家暴擊（O4、N2）
   └→ target.hurtServer(...)
LivingEntity.hurtServer
   LivingAttackEvent            ← 我們的迴避在這裡取消（O3）；此時還沒檢查無敵、創造模式、無敵時間
   無敵／已死亡／抗火 → 結束（不扣血）
   盾牌格擋、凍傷加成、頭盔減傷
   無敵時間：0.5 秒內第二下只扣「比上一下多的部分」，沒有比較大就直接結束
   actuallyHurt
      LivingHurtEvent           ← 我們決定怪物和投射物的暴擊（×1.5）；數值是扣護甲前
      傷害 ≤ 0 → 結束
      扣護甲、扣附魔保護和抗性、扣吸收心
      LivingDamageEvent         ← 最終值：這一下真正要扣的生命值
      setHealth(生命值 − 最終值)
```

（來源：`LivingEntity.java` 的 `hurtServer`、`actuallyHurt`，`Player.java` 的 `attack`、`actuallyHurt`，Forge 的 `LivingDamageEvent` 說明「armor, potion and absorption modifiers have already been applied … this is FINAL value」。）

### 拿得到什麼

| 要顯示的東西 | 從哪裡拿 | 備註 |
|---|---|---|
| 最終傷害 | `LivingDamageEvent.getAmount()`，用最低的 `MONITOR` 優先序監聽，拿到其他模組都改完以後的值 | 吸收心擋掉的部分**不**算在內；無敵時間內的追加攻擊只會是差額 |
| 對方剩餘血量 | 同一個事件裡：`getHealth() − 最終值`，最低 0；最大血量 `getMaxHealth()` | 此時血量還沒扣 |
| 有沒有被迴避 | 我們自己的 `CombatEffectListener` 在 `LivingAttackEvent` 取消時就知道 | EventBus 7 也能用監看式監聽（`addListener(ObjBooleanBiConsumer)`）看到「這個事件被取消了」，但分不出是我們的迴避還是別的原因 |
| 有沒有暴擊 | 玩家近戰：`CriticalHitEvent`；怪物和投射物：我們的 `LivingHurtEvent` 處理 | 兩者都在 `CombatEffectListener` 裡決定。怪物暴擊是直接把數字乘 1.5，外部監聽**看不出來** |
| 攻擊者、被打的是誰 | `DamageSource.getEntity()`（近戰是本人，射箭也是射手）、事件的 `getEntity()` | 和 R1 的 `CombatEffectListener.kindOf` 同一套判斷 |

**要注意的時間差**：玩家近戰是「先決定暴擊，再呼叫 `hurtServer`」，所以會有「判定暴擊、結果被迴避」或「判定暴擊、但對方在無敵時間內沒扣血」的情況。暴擊標記必須只對同一下、同一個 tick 有效，不能留給下一下（見 [12](12-damage-display-given-when-then.md) B8、B9）。

另外，原版本來就有兩種跟傷害有關的粒子：玩家暴擊時的暴擊粒子和音效（我們的暴擊率暴擊也會觸發，因為走同一個 `CriticalHitEvent`），以及玩家打中造成 2 點以上傷害時的「深色愛心」粒子（數量 = 傷害 × 0.5）。兩者都**不能顯示數字**。

### 顯示方式的技術比較

| 方式 | 誰看得到 | 多人同步 | 要不要客戶端程式 | 能不能用 GameTest 自動測 | 限制 |
|---|---|---|---|---|---|
| **浮字**：原版 `minecraft:text_display` 實體 | 附近所有人（預設約 64 格） | 原版實體同步，自動 | 不用 | 能（查實體在不在、文字是什麼） | 26.3 的 `setText` 等設定是 private，要用和 `/summon` 相同的 NBT 方式建立（`text`、`billboard`、`teleport_duration` 等）；位置用公開的 `setPos` 移動。伺服器關閉時如果字還在飄，會被存進區塊，要處理（見第 6 節） |
| **action bar**：`ServerPlayer.sendOverlayMessage` | 只有收到的那個玩家 | 原版封包，自動 | 不用 | 部分（假玩家的封包讀不到，改用「記錄用顯示」驗證內容） | 一次一行，下一則會蓋掉上一則，約 3 秒後淡出 |
| **聊天訊息**：`sendSystemMessage` | 只有收到的那個玩家 | 原版封包 | 不用 | 同上 | 會洗版，影片畫面左下角會被數字塞滿；好處是可以往回看 |
| **粒子** | 附近所有人 | 原版 | 不用 | 很難 | 不能顯示數字，只能當暴擊或 MISS 的點綴 |
| **客戶端自繪**（自訂網路封包＋客戶端畫浮字或 HUD 面板） | 可以只給攻擊者 | 要自己寫封包（Forge `SimpleChannel`） | 要（本模組目前沒有任何客戶端程式） | 不能，客戶端畫面只能手動驗證 | 最自由，但多約半天到一天 |

## 3. 變更邊界（提案，確認點會逐項問）

### 分層

```
core/feedback/      純 Java，不 import Minecraft（CoreBoundaryTest 會自動守住新資料夾）
  HitReport           一下攻擊的結果：最終傷害、暴擊、迴避、剩餘血量、最大血量、傷害種類、攻擊者和被打者是玩家還是生物
  HitFilter           哪些結果要顯示、給誰看（Q5、Q7、O4、O5 的規則）
  HitText             把結果變成要顯示的文字和樣式：數字格式、暴擊、MISS（Q6、O1～O3、O10）
  HitJournal<K>       同一個 tick 內，把「暴擊、迴避」的標記和之後的最終傷害配對；用泛型 key，不認識 Minecraft 實體
forge/
  CombatObserver      新的小介面：CombatEffectListener 結算完一下就通知它（迴避、暴擊）
  CombatEffectListener  只加「通知 observer」的幾行，規則和行為不變；不傳 observer 時和現在完全一樣
  feedback/
    HitFeedbackListener  實作 CombatObserver；監聽 LivingDamageEvent（MONITOR）拿最終傷害，組成 HitReport，交給每個 HitDisplay
    HitDisplay           介面：void show(HitReport report, 被打的生物, 攻擊者)
    FloatingTextDisplay  浮字：在被打的生物頭上生成 text_display，約 1 秒往上飄後移除
    ActionBarDisplay     action bar：送給攻擊的玩家和被打的玩家
  MobArmoryMod        接線兩行
content/            不動（這個功能沒有新的資料）
```

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `core/feedback/`、`forge/feedback/`、`forge/CombatObserver`<br>修改 `forge/CombatEffectListener`（只加通知，不改規則）、`forge/MobArmoryMod`（接線）、`lang/en_us.json`（加 MISS、CRIT 等字串）<br>測試：`src/test` 加 `core/feedback` 的測試；`src/gametest` 加一個測試類別、`MobArmoryGameTests` 註冊一行、`test_instance/*.json`<br>文件：`docs/boundary/11～13`、之後的行為報告 |
| 不准動的相依 | R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat`（`StatEffect`、`CombatOutcome`、`CombatContext`、`CombatResolver`）的公開介面和規則；暴擊 1.5 倍、迴避只閃生物造成的傷害（O3、O4）、暴擊要蓄滿（N2）<br>`content/ModStats`、`content/ModEquipment`（開發 session 正在改成 JSON 設定）、`EquipmentStatApplier`、`StatsCommand`<br>`build.gradle`、Forge 和 Minecraft 本身（不用 Mixin、不用 Access Transformer）<br>`mod/` 資料夾、`PROGRESS.md`、`transcripts/` |
| 保留或新建的抽象介面 | 保留：`StatEffect`、`StatReader`（不改）<br>新建：`HitDisplay.show(HitReport, LivingEntity defender, LivingEntity attacker)`（Observer，每種顯示方式一個實作）<br>新建：`CombatObserver.resolved(Entity target, LivingEntity attacker, CombatOutcome outcome)`（`CombatEffectListener` 把已經算出來的結果交出去） |
| 新功能放哪、用什麼形狀 | 新的顯示方式：新增一個 `HitDisplay` 實作，在 `MobArmoryMod` 加一行（Observer）<br>改顯示內容或格式：只改 `core/feedback/HitText`<br>改「哪些傷害顯示、誰看得到」：只改 `core/feedback/HitFilter` |
| 改一處會牽動幾個檔案 | 新增一種顯示方式（例如聊天紀錄）：**2**（新類別、接線一行）<br>改數字格式或樣式：**1**（若加新的文字，再加語系檔 1 行）<br>改顯示規則：**1**<br>R1 新增一個戰鬥效果：**0**（顯示端不用改） |

### SOLID 判斷（第一輪 Q1 要確認）

照最直接的寫法（在 `CombatEffectListener` 裡算完就順手生成浮字），預期會出現這些徵兆：

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 戰鬥規則和畫面混在同一個類別，改浮字顏色也要動 R1／R2 的戰鬥程式和它的測試 | S 單一職責 | 戰鬥只「交出結果」，顯示是另一個模組 |
| 每多一種顯示方式（action bar、聊天、網頁紀錄），就在同一個處理器裡多一段 `if` | O 開放封閉 | 抽 `HitDisplay` 介面，新方式只新增實作 |
| 「誰看得到」「小數幾位」這些商業規則直接用 Minecraft 類別寫，不開遊戲就沒辦法測 | D 依賴反轉 | 規則和格式放 `core/feedback`，純 Java |

L 由測試保證（每種 `HitDisplay` 拿到任何結果都不丟例外、不顯示給不該看的人）；I 目前的介面只有一個方法，不需要拆。

### Pattern 選擇（第一輪 Q2 要確認）

會變的是：**顯示方式**、**顯示規則和格式**。結果怎麼算不變。

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | Observer：`CombatEffectListener` 多一個「結果通知」掛勾；新的 `HitFeedbackListener` 在 `LivingDamageEvent` 拿最終傷害，組成 `HitReport`，通知每個 `HitDisplay`。規則和格式在 `core/feedback` | 怪物和投射物的暴擊也標得出來；新增顯示方式只加一個類別；R6 網頁之後要戰鬥紀錄也是多一個 observer | 要在 `CombatEffectListener` 加幾行（不改行為，既有測試會守住） |
| B | 完全不碰 `CombatEffectListener`，只用 Forge 的監看式監聽推斷：`LivingAttackEvent` 被取消就當作 MISS，`CriticalHitEvent` 的結果當作玩家暴擊 | 既有檔案零改動 | **怪物和投射物的暴擊看不出來**（我們是直接把數字乘 1.5）；其他原因取消的攻擊也會被當成 MISS |
| C | 直接寫、不抽象：顯示程式寫進 `CombatEffectListener` | 檔案最少，今天最快 | 戰鬥規則和畫面混在一起；改顏色、加 action bar 都要動戰鬥程式 |

## 4. 實作順序

每一步做完都跑 `gradlew build`（單元測試）；動到 `forge/` 的步驟再跑 `runGameTestServer`。

1. `core/feedback`：`HitReport`、`HitText`、`HitFilter`、`HitJournal`，加單元測試（Component、Module）
2. `forge/CombatObserver`，`CombatEffectListener` 加通知；**先跑既有的 106 個單元測試和 18 個遊戲內測試，確認沒壞**
3. `forge/feedback/HitFeedbackListener`：拿最終傷害、配對暴擊和迴避、組成 `HitReport`；先只接一個「記錄用」的顯示，用遊戲內測試確認結果正確（真的揮劍、真的扣護甲）
4. `FloatingTextDisplay`：生成浮字、往上飄、移除、不進存檔
5. `ActionBarDisplay`（若 Q4 要）
6. 接線、遊戲內系統測試、`runClient` 手動看畫面並截圖（使用者同意後），最後寫行為報告

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `HitText`：四捨五入、暴擊樣式、MISS、溢出傷害、極小值不顯示 0.0、NaN 和無限大不丟例外<br>`HitFilter`：每種傷害種類 × 攻擊者（玩家、生物、無）× 被打者（玩家、生物）要不要顯示、給誰看<br>`HitJournal`：標記只配對同一下、同一個 tick，不殘留 |
| Module | [12](12-damage-display-given-when-then.md) 的成功條件逐一破壞（B 系列裡不需要開遊戲的部分） |
| 契約 | 所有 `HitDisplay` 實作跑同一組：攻擊者是 null（環境）、被打者已經死亡、數值極端時都不丟例外；只顯示給 `HitFilter` 說可以看的人。新增顯示方式時參數列表多一行 |
| System（遊戲內） | 用假玩家真的揮劍（同 R1 的 `player_crit_uses_one_multiplier` 做法）：<br>・空手、短刀、重劍打牛，報告的傷害分別是 1.0、3.0、7.0（**影片要的「武器不同、數字不同」**）<br>・重劍打穿重甲的殭屍，報告的傷害 = 殭屍實際少掉的血，而且小於 7（證明是扣護甲後）<br>・暴擊率 100% 時報告標記暴擊；迴避在上限時有 MISS、沒扣血<br>・摔落等環境傷害不顯示（若 Q7 選 A）<br>・浮字真的生成在被打的生物上方，約 1 秒後消失；從存檔載入的殘留浮字會被移除<br>・既有 18 個遊戲內測試照樣通過 |
| 手動（runClient） | 浮字和 action bar 的實際畫面、顏色、動畫，截圖放 `screenshots/`。客戶端畫面沒有自動測試 |

## 6. 技術風險

| # | 風險 | 處理 |
|---|---|---|
| T1 | `text_display` 的設定方法在 26.3 是 private，不能直接呼叫 | 用和 `/summon` 相同的 NBT 建立（`EntityType.loadEntityRecursive`），只用公開 API，不需要 Access Transformer。浮字往上飄用公開的 `setPos`，客戶端靠原版的 `teleport_duration` 補間，看起來是平滑的 |
| T2 | 伺服器關閉或區塊卸載時，還在飄的浮字會被存進區塊，之後永遠留在世界上 | 浮字加上我們的 tag；`EntityJoinLevelEvent` 看到「從存檔載入、帶這個 tag」的實體就不讓它進世界。遊戲內測試會測 |
| T3 | 假玩家的 action bar 封包讀不到 | 系統測試改成在管線裡多接一個「記錄用」的 `HitDisplay`，驗證送出去的內容；畫面本身用 `runClient` 手動確認 |
| T4 | 既有的遊戲內測試（例如 200 下殭屍攻擊的迴避測試）現在也會生成浮字 | 浮字 1 秒後自己消失，不影響既有斷言；第 2 步會先跑一次確認 |
| T5 | 開發 session 在 `mod/` 同時開發，合併時可能衝突 | 只有 `MobArmoryMod`（接線）、`lang/en_us.json`、`MobArmoryGameTests`（註冊一行）可能和對方改到相鄰的行，都是加行，衝突容易解。`content/` 和 `core/combat` 不動 |
