# 邊界說明：R1 可擴充數值系統 ＋ R2 裝備改變玩家數值（週五雛型）

> 狀態：**草案，等待使用者確認**。要決定的問題整理在 [03-open-questions.md](03-open-questions.md)，確認後依答案改寫本檔，再開始實作。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [02-given-when-then.md](02-given-when-then.md)。

## 1. 需求翻譯

### 真正的需求

- **R1 可擴充的數值系統**：「數值」是一份可以一直往上加的登記表，不是寫死在程式裡的一組欄位。加一種數值就是加一筆定義（代號、預設值、上下限）。加完之後，所有生物自動擁有這個數值，所有裝備也都能修改它。
- **R2 裝備改變玩家數值**：裝備本身就是一組數值修飾。拿起或穿上時加上，放下或脫掉時移除。「重武器」不需要寫成一個 `HeavySword` 類別，它就是一筆帶有「移動速度 −30%」的裝備定義。
- **T1 驗收**：拿起重武器後，移動速度要等於預期值；放下後要恢復原值。這要能用測試證明，不能只靠在遊戲裡目測。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [03-open-questions.md](03-open-questions.md)：

1. 「隨時注入新數值」的意思：改程式或設定檔後重開遊戲生效，還是遊戲執行中就能新增？
2. 數值定義放在哪裡：Java 程式碼，還是 JSON 設定檔？
3. 同一種數值有多個修飾時，要相加還是相乘？有沒有上限或下限？
4. 武器在哪個欄位生效（只限主手，還是副手也算）？
5. 數值是同種裝備共用一份，還是每一把各自不同？這會影響 R6 網頁改數值後，玩家手上那把要不要立刻跟著變。
6. 暴擊率和迴避率在週五雛型就要有實際效果嗎？倍率、上限是多少？哪些傷害可以迴避？
7. 原版武器要不要有我們的數值？原版生物要不要有？
8. 雛型要做哪幾件裝備？外觀借用原版貼圖，還是另外畫？
9. 模組名稱、Java package、主類別名稱（取代 MDK 的 `examplemod`）。

### 這次要做／未來可能變

| 這次要做（週五雛型） | 未來會變，所以要在這裡切邊界 |
|---|---|
| 數值登記表，加上 4 個數值：攻擊力、移動速度（沿用原版）、暴擊率、迴避率（自訂） | **數值種類會一直增加** → 登記表，新增時只加一筆定義 |
| 3～4 件裝備（重劍、短刀、迴避胸甲、重甲） | **裝備清單和數值大小會改**（R6 要能在網頁上改）→ 裝備用資料描述，不寫成各自的類別 |
| 暴擊、迴避的戰鬥效果（若 Q7 決定要做） | **每種數值的效果都不一樣** → 每種效果各寫一個類別（Strategy） |
| 數值套用到所有生物 | **之後會加自訂怪物**（R4）→ 對所有生物生效，不只限玩家 |
| — | **combo 要讀數值**（R3）→ 提供一個唯讀的查詢介面 |
| — | **多人同步**（R5）→ 數值走原版 Attribute，由原版負責同步到客戶端 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下是在 Forge 26.3-66.1.0 原始碼裡確認過的：

- 原版 **Attribute** 系統可以註冊新數值，每個數值有預設值、上下限，也能設定是否同步到客戶端。移動速度、攻擊力本來就是原版 Attribute。
- 修飾值 `AttributeModifier` 有三種運算：`ADD_VALUE`（加固定值）、`ADD_MULTIPLIED_BASE`（以基礎值為準加百分比，多個修飾會相加）、`ADD_MULTIPLIED_TOTAL`（乘上 1＋百分比，多個修飾會相乘）。
- Forge 的 `EntityAttributeModificationEvent` 可以把新數值加到**每一種**生物身上。Forge 自己的 `swim_speed` 就是用這個做法。
- Forge 的 `LivingEquipmentChangeEvent`（只在伺服器端）會在換裝時觸發；`LivingHurtEvent` 可以修改或取消傷害；`CriticalHitEvent` 可以決定這一下是否暴擊。
- **限制 1**：Attribute 登記表在遊戲啟動後就會凍結，所以「新的數值種類」只能在啟動時加入。數值的「大小」則可以在遊戲中修改。
- **限制 2**：Forge 66 的物品沒有動態計算屬性的掛勾。物品上的數值要嘛在註冊時寫死（原版做法），要嘛由我們在換裝事件裡自己套用。

## 3. 變更邊界（提案，待確認）

### 分層

```
core/        純 Java，不 import 任何 Minecraft 類別，不開遊戲就能單元測試
  stat/        StatDefinition、StatCatalog、StatModifier、ModifierOperation、StackingRules
  equipment/   EquipmentDefinition、EquipmentSlotKind、EquipmentCatalog
  combat/      StatEffect（介面）、DodgeEffect、CritEffect、CombatContext、CombatOutcome、RandomSource
  StatReader   唯讀查詢介面：double get(StatId)，之後 combo（R3）透過它讀數值
forge/       轉接層，只有這一層會接觸 Minecraft 或 Forge
  StatAttributeBridge    把 StatDefinition 註冊成 Attribute；原版已有的數值直接對應原版 Attribute
  EntityStatBinder       把所有自訂數值加到每一種生物
  EquipmentItemFactory   依 EquipmentDefinition 產生並註冊物品（Factory）
  EquipmentStatApplier   換裝時套用或移除修飾（Q6 選「共用」時才需要）
  CombatEffectListener   受傷事件 → 呼叫 StatEffect → 把結果套回去
  <Mod 主類別>           @Mod 進入點，只負責接線
content/     雛型的內容：有哪些數值、有哪些裝備，全部都是資料
  ModStats、ModEquipment（Q5 若選 JSON，就改成讀設定檔）
```

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `core/`、`forge/`、`content/`；`build.gradle`（只加測試相依，**已先做**）；`mods.toml`、語系檔、物品模型 json；刪除 MDK 範例（`ExampleMod`、`Config`、`example_block`、`example_item`、`example_tab`） |
| 不准動的相依 | Minecraft 和 Forge 本身（不用 Mixin，也不用 Access Transformer）；原版移動速度的計算方式（不自己重寫）；`build.gradle` 裡的 Forge 版本和 Java 25 toolchain；專案外的 `transcripts/` |
| 保留或新建的抽象介面 | `StatEffect`：`CombatOutcome apply(CombatContext ctx, RandomSource rng)`<br>`StatReader`：`double get(StatId id)` |
| 新功能放哪、用什麼形狀 | 新數值：`content/ModStats` 加一筆（登記表）<br>有戰鬥效果的新數值：再新增一個 `StatEffect` 實作（Strategy）<br>新裝備：`content/ModEquipment` 加一筆，由 `EquipmentItemFactory` 建成物品（Factory） |
| 改一處會牽動幾個檔案 | 新增沒有特殊效果的數值：**2**（定義 1 行、語系 1 行）<br>新增有效果的數值：**3**（再加 1 個 `StatEffect` 類別）<br>新增一件裝備：**3**（定義 1 行、物品模型 json、語系 1 行） |

### SOLID 判斷（第一輪 Q1 要確認）

這是新專案，沒有既有的腐敗，以下是**照最直接的寫法，預期會出現的徵兆**：

| 預期徵兆 | 原則 | 處方 |
|---|---|---|
| 每加一種數值，就要到戰鬥事件處理器裡加一個 `if` | O 開放封閉 | 抽出 `StatEffect` 介面，新數值只新增實作 |
| 核心邏輯直接用 Minecraft 類別，不開遊戲就沒辦法測試 | D 依賴反轉 | `core/` 不 import Minecraft，由 `forge/` 轉接 |
| 一個事件處理器同時管移速、暴擊、迴避 | S 單一職責 | 每種效果一個類別；「套用裝備」和「戰鬥效果」分開 |

L（里氏替換）由契約測試保證；I（介面隔離）目前的介面已經夠小，不需要拆。

### Pattern 選擇（第一輪 Q2 要確認）

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 原版 Attribute 當底層，加上純 Java 核心（登記表、裝備定義、效果 Strategy）和 Forge 轉接層 | 同步、存檔、移動速度效果都由原版負責；核心可以單元測試 | 新的數值種類要重開遊戲才生效（平台限制） |
| B | 完全自製數值系統：自己把數值存在實體上、自己寫同步封包、自己套用移動速度 | 彈性最大，連數值種類都能在執行中新增 | 工作量最大，R5 同步全部要自己做，週五來不及 |
| C | 直接寫、不抽象：每把武器一個類別、各自寫屬性修飾；暴擊和迴避寫在同一個事件處理器的 `if` 裡 | 今天最快 | 不符合 R1「隨時注入新數值」，做 R4 和 R6 時要大改 |

## 4. 實作順序（確認後才開始）

每一步做完都跑一次測試，才進下一步。

0. 改名（`examplemod` → 正式名稱），刪掉 MDK 範例內容，跑 `build` 確認沒有壞掉
1. `core/stat`：`StatDefinition`、`StatCatalog`、`StatModifier`、`StackingRules`，加單元測試
2. `core/equipment`：`EquipmentDefinition`、`EquipmentCatalog`，加驗證測試
3. `core/combat`：先寫 `StatEffect` 介面，再寫 `DodgeEffect`、`CritEffect`，加契約測試（Q7 決定要做才做）
4. `forge/`：`StatAttributeBridge`、`EntityStatBinder`，讓數值掛到所有生物身上
5. `forge/`：`EquipmentItemFactory`、`EquipmentStatApplier`，讓裝備改變數值（T1）
6. `forge/`：`CombatEffectListener`，讓暴擊和迴避生效
7. 系統測試（GameTest），再到遊戲裡手動驗證，最後產出行為報告

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `StatCatalog` 的註冊與查詢、`StackingRules` 的計算、`EquipmentCatalog` 的驗證、各 `StatEffect` 的輸入輸出 |
| Module | 「裝備改變數值」的成功條件逐一破壞，見 [02-given-when-then.md](02-given-when-then.md) 的 B 系列 |
| 契約 | (1) 所有 `StatEffect` 實作跑同一組契約：數值為 0 時永不觸發、到達上限時必定觸發、超出範圍會被夾住、不會丟例外、同一個亂數種子結果一致<br>(2) 所有裝備定義跑同一組契約：只引用已註冊的數值、修飾值在範圍內、欄位合法、拿起再放下後數值回到原值<br>新增效果或新增裝備時，只要在清單多一行 |
| System | 在 `gameTestServer` 裡用 GameTest 和 mock player 測試：拿重武器後移速等於預期（T1）；新增數值後玩家和殭屍都擁有它（T2 的前置） |

**技術風險**：Forge 66 的 `@GameTest` 會由誰自動收集，在原始碼裡還沒找到呼叫端，實作第 7 步時要先驗證。如果跑不起來，System 層改成遊戲內指令（例如 `/<modid> stats`）手動驗證並截圖，報告裡會註明。
