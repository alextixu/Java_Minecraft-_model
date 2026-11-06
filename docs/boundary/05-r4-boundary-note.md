# 邊界說明：R4 自訂怪物（驗收 T2）

> 狀態：**已確認**（2026-11-06）。使用者 11/06 回覆「全部照建議」：第一輪 Q1～Q3 都選 A，第二輪 Q4～Q7 都選 A，O1～O10 同意，所以本檔內容不用改寫。決定紀錄在 [07-r4-open-questions.md](07-r4-open-questions.md)。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [06-r4-given-when-then.md](06-r4-given-when-then.md)。
> 前提：沿用 R1／R2 已確認的架構（`core/`、`content/`、`forge/` 分層，數值登記表、`StatEffect`、`LoadoutCalculator`），不重新設計。

## 1. 需求翻譯

需求來源：會議重點第 3 節，加上驗收測試 T2。

- 自訂怪物的**外觀和行為可以設定**；行為先做「閒晃」和「攻擊玩家」。
- 雛型只要能改數值、統一攻擊玩家。
- 之後網頁（R6）要能設定怪物的行為：能不能奔跑、衝刺、攻擊玩家，以及是不是中立生物。
- **T2**：能把不同的生物或怪物加進系統。

### 真正的需求

- **怪物是一筆資料，不是一個類別。** 跟 R2 的「重劍不是 `HeavySword` 類別」一樣，「狂戰士」是一筆定義，內容包括：代號、外觀、基礎數值、行為清單、身上的裝備。加一種怪物等於加一筆定義。
- **行為是可以組合、可以替換的零件（Strategy）。** 閒晃、攻擊玩家是第一批；奔跑、衝刺、中立是 R6 會加的。每種行為各自是一個類別，加新行為不改既有的怪物，也不改既有的行為。
- **怪物接上已經做好的系統，不另外做一套。** 數值用 R1 的登記表（所以怪物自動有暴擊率、迴避率，戰鬥效果也自動生效）；手上的裝備用 R2 的換裝計算（狂戰士拿重劍一樣會變慢）。
- **T2 要證明兩件事。**
  1. 加一種新怪物只要加一筆定義，它就完整可用：有數值、會動、會打人、可以生成。
  2. 系統裡所有生物都有我們的數值：原版的、其他模組的、我們自己的。第 2 點 R1 已經做到（`EntityStatBinder` 走過每一種生物）。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [07](07-r4-open-questions.md)：

1. 新的怪物**種類**要不要能在遊戲執行中新增（R6 網頁直接建立新怪物），還是重開遊戲生效？
2. 外觀怎麼「設定」：從原版模型挑，還是自己畫貼圖、自己做模型？
3. 怪物怎麼出現：生怪蛋、`/summon`，還是自然生成？在哪裡、什麼時間？
4. 網頁改了行為以後，已經在世界上的怪物要不要立刻改變？
5. 白天會不會燒起來？和平難度要不要消失？
6. 死掉掉什麼？手上的武器會不會掉出來（會不會變成刷武器的方法）？
7. 攻擊誰：只有玩家，還是被打了也會反擊其他生物？創造模式的玩家要不要被攻擊？
8. 雛型要做幾種怪物、各是什麼數值？
9. 定義寫錯（引用不存在的數值或裝備、數值超出範圍、沒有任何行為）時怎麼辦？

### 這次要做／未來可能變

| 這次要做（R4 雛型） | 未來會變，所以要在這裡切邊界 |
|---|---|
| 2～3 種怪物，各一筆定義 | **怪物清單和數值會一直改**（R6 網頁）→ 用資料描述，所有同種怪物共用一份定義 |
| 行為：閒晃、攻擊玩家 | **行為會一直加**（奔跑、衝刺、中立）→ 每種行為一個 Strategy |
| 外觀：沿用原版人形模型 | **外觀會改**（R6 網頁設定外觀）→ 外觀是定義裡的一個欄位，渲染器照欄位畫 |
| 怪物有 R1 的數值，可以拿 R2 的裝備 | **數值種類會加**（R1）→ 怪物定義只寫「數值 id → 基礎值」，不寫死欄位 |
| 生怪蛋放進 Mob Armory 分頁 | **生成規則會改**（自然生成、生態域）→ 這次不做，留一個獨立的位置 |
| — | **多人同步**（R5）→ 外觀、數值都要能同步到客戶端 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下在 Forge 26.3-66.1.0 和 Minecraft 26.3 原始碼裡確認過：

- **生物種類**用 `EntityType.Builder.of(...).build(key)` 建立，經 `DeferredRegister` 註冊。和 Attribute 一樣，**註冊表在啟動後凍結**，新的生物種類只能在啟動時加入。
- **基礎屬性**用 Forge 的 `EntityAttributeCreationEvent` 給。這個事件比 R1 用的 `EntityAttributeModificationEvent` 早觸發，所以 R1 的 `EntityStatBinder` 會自動把自訂數值加到我們的新怪物上。
- **AI**：原版有現成的 goal 可以組合。`RandomStrollGoal`、`WaterAvoidingRandomStrollGoal` 負責閒晃；`MeleeAttackGoal` 加 `NearestAttackableTargetGoal<Player>` 負責攻擊玩家；`HurtByTargetGoal` 負責反擊；`LeapAtTargetGoal` 可以做衝刺。這些 goal 在生物建立時（`registerGoals`）加入。
- **外觀**：客戶端用 `EntityRenderersEvent.RegisterRenderers` 註冊渲染器。原版的人形渲染器（`HumanoidMobRenderer`；殭屍、屍殼、骷髏各有一個）可以沿用，只換貼圖位置。
- **生怪蛋**：26.x 的生怪蛋是資料驅動的，`Item.Properties.spawnEgg(type)` 就能做。
- **對玩家的傷害會依難度縮放**（簡單、普通、困難不同）。測試怪物打玩家的傷害時要固定難度。

## 3. 變更邊界（已確認）

### 分層（新增的部分）

```
core/
  mob/          MobId、MobDefinition、MobCatalog（Builder，依 StatCatalog 和 EquipmentCatalog 驗證）
                Behavior（介面，只放資料）
                ├─ WanderBehavior(speed)
                └─ AttackPlayersBehavior(speed)
                MobLook（外觀：借用的原版模型＋貼圖＋大小倍率）
content/
  ModMobs       雛型的怪物清單（資料）
  ModStats      加一筆「最大生命值」（對應原版 max_health；怪物定義要能設血量）
forge/
  MobEntityFactory   每筆 MobDefinition → 註冊 EntityType、基礎屬性、生怪蛋（Factory）
  ArmoryMob          extends Monster：從共用定義讀行為和裝備；本身不含任何怪物特有的邏輯
  BehaviorGoals      行為 → 原版 goal 的對照：每種 Behavior 一個 GoalBinder（Strategy）
  client/ArmoryMobRenderer   依 MobLook 選原版人形模型和貼圖
```

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `core/mob/`、`content/ModMobs`、`forge/` 下的上述類別、`forge/client/`；`content/ModStats` 加一筆 `max_health`；`MobArmoryMod` 加接線；語系檔；`src/gametest` 加 R4 的系統測試 |
| 不准動的相依 | R1／R2 已確認的 `core/stat`、`core/equipment`、`core/combat` 的公開介面和規則；`EquipmentStatApplier`、`CombatEffectListener`、`EntityStatBinder` 的行為（怪物要直接用它們，不另寫一套）；Minecraft 和 Forge 本身（不用 Mixin） |
| 保留或新建的抽象介面 | 保留：`StatReader`、`StatEffect`<br>新建：`Behavior`（core，只描述「做什麼」和參數）<br>新建：`GoalBinder<B extends Behavior>`（forge）：`void bind(ArmoryMob mob, B behavior, Goals goals)`，把一種行為變成原版 goal |
| 新功能放哪、用什麼形狀 | 新怪物：`content/ModMobs` 加一筆（資料），由 `MobEntityFactory` 建出生物種類（Factory）<br>新行為：core 加一個 `Behavior` record，forge 加一個 `GoalBinder`，在對照表登記一行（Strategy） |
| 改一處會牽動幾個檔案 | 新增一種怪物（沿用既有外觀）：**2**（`ModMobs` 一筆、語系 1 行）<br>新增一種行為：**2～3**（`Behavior` record、`GoalBinder`、對照表 1 行）<br>新增一個數值讓怪物使用：照 R1，**1～2** |

### SOLID 判斷（第一輪 Q1 要確認）

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 每加一種怪物就多一個 `XxxEntity` 類別和一個渲染器 | O 開放封閉 | 怪物是資料，`MobEntityFactory` 依定義建出生物種類；渲染器依 `MobLook` 選模型 |
| 每加一種行為，就要在怪物類別的 `registerGoals` 裡加 `if (canRun) … if (neutral) …` | O 開放封閉 | 行為各自是 `Behavior` ＋ `GoalBinder`，怪物類別只是「把定義裡的每個行為交給對應的 binder」 |
| 怪物自己寫一套血量、速度、暴擊的欄位 | S、D | 數值全部走 R1 的登記表和原版 Attribute，怪物定義只寫「數值 id → 基礎值」 |
| 核心的定義驗證需要開遊戲才能測 | D 依賴反轉 | `core/mob` 不 import Minecraft，`CoreBoundaryTest` 自動守住 |

L（里氏替換）由契約測試保證：所有怪物定義跑同一組契約，所有 `GoalBinder` 跑同一組契約。

### Pattern 選擇（第一輪 Q2 要確認）

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 每筆定義註冊一個生物種類（Factory）；行為用 Strategy（`Behavior` ＋ `GoalBinder`）；外觀從原版人形模型挑 | 和原版一樣：每種怪物有自己的 `/summon mobarmory:<id>`、生怪蛋、名稱；最好測試；R6 改數值、改行為都可以 | 新的怪物**種類**要重開遊戲才生效（平台限制，和 R1 的數值種類一樣） |
| B | 只註冊**一個**通用生物種類，用「變體 id」決定是哪種怪物；定義可以在遊戲執行中新增 | R6 網頁可以直接建立新怪物，不用重開 | 變體要自己存檔和同步；渲染器要依變體切換；`/summon` 要帶參數；約多半天 |
| C | 直接寫、不抽象：每種怪物一個類別（例如 `BruteEntity extends Zombie`），行為寫在各自的 `registerGoals` | 今天最快 | 每加一種怪物就多兩三個類別；R6 網頁設定行為時要大改 |

## 4. 實作順序

每一步做完都跑一次測試，才進下一步。

1. `content/ModStats` 加 `max_health`（照 R1 的規則，示範加一個原版數值）
2. `core/mob`：`MobDefinition`、`Behavior`、`MobLook`、`MobCatalog` 加驗證，附單元測試和怪物定義的契約測試
3. `content/ModMobs`：雛型怪物（數量和數值見 07 的 O 系列）
4. `forge/`：`MobEntityFactory` 註冊生物種類、基礎屬性、生怪蛋；`ArmoryMob`；生成時穿上定義裡的裝備（交給 R2 的 `EquipmentStatApplier`）
5. `forge/`：`GoalBinder` 介面、閒晃、攻擊玩家，附 binder 的契約測試
6. `forge/client/`：渲染器（依 `MobLook`）
7. 系統測試（GameTest）、`runClient` 手動走查（截圖）、行為報告

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `MobCatalog` 的驗證（id 重複、引用不存在的數值或裝備、數值超出範圍、行為參數不合法）；`MobDefinition` 算出的生成數值 |
| Module | 「一種自訂怪物能正常運作」的成功條件逐一破壞，見 [06](06-r4-given-when-then.md) 的 B 系列 |
| 契約 | (1) 所有怪物定義跑同一組：只引用已註冊的數值和裝備、裝備放在對的欄位、基礎數值在範圍內、行為都有對應的 binder<br>(2) 所有 `GoalBinder` 跑同一組：綁定後至少加了一個 goal、參數照定義、重複綁定不會加兩份 |
| System | GameTest：每種自訂怪物都能生成、有定義的數值和裝備效果（T2）；附近有生存模式玩家時會鎖定並攻擊；沒有玩家時會移動（閒晃）；存檔再載入數值不疊加；再加一種測試用怪物，證明只加一筆定義就能用（T2） |
| 手動 | `runClient`：生怪蛋生成、外觀、追著玩家打、拿重劍的怪物明顯比較慢 |
