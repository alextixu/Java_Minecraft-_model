# 邊界說明：段落 E，數值改成 JSON 定義

> 狀態：**依夜間授權採用建議值，待使用者確認**。問題和決定在 [26](26-stat-files-open-questions.md)，Given / When / Then 在 [25](25-stat-files-given-when-then.md)，行為報告是 [27](27-stat-files-behavior-report.md)。
> 流程：boundary-first 第 1、2 步。
> 前提：
> - 沿用 R1 的數值系統：`core/stat` 的 `StatDefinition`、`StatCatalog`、`StackingRules`，公開介面和規則都不改。
> - 沿用 R2b 武器的做法：一個東西一個 JSON 檔、`config/mobarmory/`、啟動時讀、寫錯就啟動失敗。

## 1. 需求翻譯

需求來源：
- 使用者 2026-11-07 定下的主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。
- 主session 轉達的段落 E 指令：數值改成 `config/mobarmory/stats/<id>.json`。第一次啟動寫出 7 個預設檔；玩家多放一個 JSON（代號、預設值、上下限、要不要同步），重開遊戲就多一個數值，所有生物、所有武器都能用。

### 真正的需求

- **玩家不用改 Java 就能加數值。** 現在數值寫在 `content/ModStats`，玩家沒辦法新增。
- **「數值」和「數值的戰鬥效果」是兩件事。**
  - 數值是一個數字：預設值、上下限，掛在每隻生物身上，武器可以加減。
  - 戰鬥效果是規則，例如暴擊、迴避，還是要寫 Java（`StatEffect`）。
  - 從 JSON 加的「純數值」沒有戰鬥效果，只會被記錄、顯示，也能讓武器加減。
  - 例外：如果 JSON 指向一個**原版的屬性**（例如 `minecraft:jump_strength`），效果就是 Minecraft 自己的，玩家不寫 Java 也能做出有效果的數值。
- **設定檔是唯一的來源**，和武器一樣。
- **新數值一定要重開遊戲**：屬性登記表在啟動後就凍結（平台限制，見第 2 節）。
- **Java 程式還在用的 7 個內建數值不能消失**：
  - 暴擊、迴避的效果，以及兩種怪物的定義，都用 Java 常數引用這 7 個數值。
  - 另外兩個分支（`feature/r3-combo`、之後的怪物 JSON）也是。

## 2. 平台本身已經提供的功能（技術背景，非決策）

在 Forge 26.3-66.1.0、Minecraft 26.3 確認過：

- **屬性（Attribute）登記表在啟動後就凍結。**
  - 自訂數值是一個新的屬性（`mobarmory:<id>`），只能在模組建構時註冊。
  - 模組建構時已經讀得到 `config/`，比註冊早，和武器一樣。
- **生物的屬性在啟動時就決定了**：每種生物身上有哪些屬性、預設值是多少（`EntityAttributeModificationEvent`、`EntityAttributeCreationEvent`）。所以既有數值的預設值，在執行中改了也不會套到已經存在的生物種類上。
- **多人連線時，客戶端和伺服器的屬性登記表要一樣**（和物品清單一樣），否則連不上。
- **翻譯**：屬性的名稱是一個翻譯鍵。客戶端的語系檔會合併所有資源包的 `lang/en_us.json`，所以執行時資源包可以提供新數值的名稱。

## 3. 變更邊界

### 分層

```
content/ModStats       不改常數和公開介面。角色從「遊戲裡所有的數值」改成「Java 程式要用的 7 個內建數值，
                       也是第一次啟動寫出的預設檔」，只改說明文字
data/                  （純 Java＋Gson）StatJson（一個數值檔的格式）、StatEntry（檔案的內容：定義＋名稱＋同步）、
                       StatStore／StatFolder（config/mobarmory/stats）、StatLoader（安裝預設檔、讀全部、檢查）、
                       StatSetup（讀完的結果：登記表、名稱、不同步的、用了內建值的）
forge/                 StatStartup（模組建構時讀檔，寫錯就停）；StatAttributeBridge 多一個建構子（哪些要同步）；
                       MobArmoryMod 改用讀出來的登記表；GeneratedWeaponPack 多提供新數值的名稱（lang）
src/gametest/config/   遊戲內測試用的兩個數值檔，build.gradle 在測試前複製進 run-gametest/
```

設定檔（不進 git）：

```
config/mobarmory/stats/
  attack_damage.json …        第一次啟動寫出 7 個內建數值
  jump.json                   玩家加的（例子）
```

格式（第 1 版，檔名就是 id）：

```json
{ "format": 1, "name": "Luck", "default": 0, "min": 0, "max": 1, "sync": true }
{ "format": 1, "attribute": "minecraft:jump_strength", "default": 0.42, "min": 0, "max": 32 }
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `format` | 是 | 固定是 1 |
| `default`、`min`、`max` | 是 | 預設值和上下限，`min ≤ default ≤ max` |
| `attribute` | 否 | 有：對應這個原版屬性，效果是 Minecraft 的。沒有：新的自訂屬性 `mobarmory:<id>`，純數值 |
| `floor` | 否 | 不低於基礎值的幾倍（0～1），移動速度用 0.3；預設 0 |
| `name` | 否 | 只對自訂屬性有用：tooltip 和 `/mobarmory stats` 顯示的名稱。內建數值的名稱在模組的語系檔 |
| `sync` | 否 | 只對自訂屬性有用：要不要同步給客戶端；預設 `true` |

### 給其他模組用的穩定介面（R3 combo、怪物 JSON 合併時依這個）

- `ModStats.ATTACK_DAMAGE` … `ModStats.DODGE_CHANCE`、`ModStats.CATALOG`、`ModStats.COMBAT_EFFECTS`：**全部保留，型別不變**。`ModStats.CATALOG` 現在的意思是「7 個內建數值」，單元測試照用。
- 遊戲裡實際的登記表（內建＋玩家加的）：`MobArmoryMod.STATS.catalog()`。要列出所有數值的地方，例如怪物 JSON 的驗證、網頁，請用這個，不要用 `ModStats.CATALOG`。
- `StatAttributeBridge(StatCatalog, String)` 舊的建構子保留，所以 combo 分支的 `MobArmoryMod` 不會因為建構子而編譯失敗；合併時只有 `MobArmoryMod` 開頭幾行會有文字上的衝突。

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建 `data/` 的 `StatJson`、`StatEntry`、`StatStore`、`StatFolder`、`StatLoader`、`StatSetup`<br>新建 `forge/StatStartup`<br>修改：`forge/StatAttributeBridge`（加建構子）、`forge/MobArmoryMod`（接線）、`forge/client/GeneratedWeaponPack`（多一個語系檔）、`content/ModStats`（只改說明）<br>`build.gradle`：遊戲內測試前把 `src/gametest/config/` 複製進 `run-gametest/`<br>測試、README、文件 |
| 不准動的相依 | `core/stat` 的公開介面和規則；`ModStats` 的常數、型別和 `COMBAT_EFFECTS`；`StatEffect` 和戰鬥規則；R4、D1 的類別；`feature/r3-combo`；Minecraft 和 Forge 本身；不加外部相依 |
| 保留或新建的抽象介面 | 保留：`StatCatalog`、`StatDefinition`、`StatAttributeBridge` 舊建構子<br>新建：`StatStore`（資料夾版、記憶體版，跑同一組契約測試） |
| 新功能放哪、用什麼形狀 | 新數值：`config/mobarmory/stats/<id>.json`，重開遊戲<br>有戰鬥效果的新數值：JSON 加上 Java 的 `StatEffect` 和 `ModStats.COMBAT_EFFECTS` 一行（和以前一樣） |
| 改一處會牽動幾個檔案 | 加一個純數值或對應原版屬性的數值：**1 個 JSON，0 行 Java** |

### SOLID 判斷

- **為什麼這次要動 `content/ModStats`**（之前列為不能動）：
  - 它原本的職責是「遊戲裡所有的數值」。玩家要能加數值，這個職責就必須搬到設定檔，否則每加一個數值都要改 Java、重新 build，違反 O。
  - 搬走以後，`ModStats` 剩下兩個職責：
    - Java 程式要引用的 7 個內建數值，給戰鬥效果、怪物定義，以及其他分支用。
    - 第一次啟動時寫出的預設檔。
  - 職責變小（S），但常數和型別都不變，所以引用它的程式不用改。實際只改說明文字。
- **S**：
  - `StatJson` 只管一個檔案的格式；`StatLoader` 只管讀整個資料夾、檢查、補上缺的內建值；`StatFolder` 只管檔案。
  - `StatStartup` 只管「啟動時讀、錯了就停」。
  - 名稱只在 `StatSetup` 產生語系內容，執行時資源包只負責把它交給 Minecraft。
- **O**：加數值是加資料，不改程式。
- **L**：`StatStore` 的兩個實作跑同一組契約測試；讀檔產生的 `StatCatalog` 和 Java 建的完全是同一種東西，下游（武器、怪物、橋接、網頁）不用分辨。
- **I**：`StatStore` 只有三個方法。
- **D**：
  - `StatLoader` 依賴 `StatStore` 介面，可以用記憶體版做單元測試。
  - `forge/` 只依賴讀出來的 `StatCatalog`，不知道它來自檔案。
- **結論**：不違反。

### Pattern

- 沿用 R2b：
  - 一個東西一個 JSON。
  - Strategy：`StatStore` 介面加上資料夾、記憶體兩種實作。
  - Builder：用 `StatCatalog.builder()` 建登記表，重複的 id、兩個數值對應同一個原版屬性都由它擋下。
- 不可變快照：讀完以後，`StatCatalog` 不再改變。

## 4. 實作順序

1. `data/`：格式、資料夾、讀取和檢查，加單元測試。
2. `forge/`：啟動時讀檔、接線；橋接器的同步選項；執行時資源包的名稱。
3. 遊戲內測試：準備兩個測試用的數值檔（一個自訂、一個對應原版屬性），確認每隻生物都有、武器加減有效。
4. README、行為報告 27。全部測試通過才 commit。

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `StatJson`：每個欄位、錯誤時指出欄位；寫出再讀回一樣 |
| Module | `StatLoader`：第一次安裝 7 個預設檔、內建檔可以改數字、不能改種類、刪掉就用內建值、新數值（自訂和原版）、重複的原版屬性、寫錯的檔名和內容 |
| 契約 | `StatStore` 兩個實作；預設檔讀回來等於 `ModStats.CATALOG` |
| System | 遊戲內：檔案加的數值出現在登記表和每隻生物身上；武器可以加減自訂數值和原版屬性數值；資料夾裡沒有的內建數值用內建值 |
| 手動 | `runClient` 啟動正常、預設檔寫出來；新數值的名稱顯示在 tooltip（步驟寫在 27） |
