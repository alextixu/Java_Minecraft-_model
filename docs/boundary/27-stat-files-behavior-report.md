# 行為報告：段落 E，數值改成 JSON 定義

> boundary-first 第 6 步的產出。邊界在 [24](24-stat-files-boundary-note.md)，行為在 [25](25-stat-files-given-when-then.md)，決定在 [26](26-stat-files-open-questions.md)，prompt 和每一步的 SOLID／Pattern／變更邊界在 [prompts/round-08-stat-files.md](prompts/round-08-stat-files.md)。
> **決定的狀態**：主 session 依夜間授權採用建議值（Q1～Q11 都是 A，O1～O5 照建議），**待使用者確認**。

## 1. 結果一覽

| 項目 | 結果 |
|---|---|
| 單元測試 | **375 / 375 通過**（段落 A 之後是 333，新增 42） |
| 遊戲內系統測試 | **61 / 61 通過**（60 個我們的加原版 `always_pass`；新增 2） |
| 玩家加一個數值 | **1 個 JSON 檔、0 行 Java**，重開遊戲。每一隻生物都有，武器可以加減 |
| 對應原版屬性 | 可以，例如 `minecraft:jump_strength`，效果是 Minecraft 的。遊戲內測試：拿著加了 +50% 的劍，跳躍力從 0.42 變成 0.63 |
| 既有行為 | 不變：第一次啟動寫出的 7 個檔案，讀回來和 `ModStats.CATALOG` 完全一樣；原本的 333 個單元、59 個遊戲內測試，預期數字一個都沒改 |
| `runClient` | 正常啟動，在 `run/config/mobarmory/stats/` 寫出 7 個檔案；log 寫「7 stats from …」；執行時資源包（含語系檔）載入，沒有錯誤 |
| 執行中改數值 | **不行，要重開**（Q8 A）。原因見第 4 節 |

## 2. 改了什麼

| 層 | 檔案 | 內容 |
|---|---|---|
| `data/`（新） | `StatJson` | 一個數值檔的格式（format 1）。錯誤訊息以欄位開頭 |
| | `StatEntry` | 一個檔案的內容：`StatDefinition`，加上名稱、要不要同步，這兩樣是 core 沒有的 |
| | `StatStore`、`StatFolder` | 數值檔放哪裡；介面加資料夾版，測試用記憶體版 |
| | `StatLoader` | 第一次寫出 7 個檔；讀全部；檢查內建數值的種類；缺的內建數值用內建值；所有問題一次列出 |
| | `StatSetup`、`StatLoadException` | 讀完的結果（登記表、名稱、不同步的、用了內建值的），以及產生語系檔；讀不了時的錯誤 |
| | `JsonFolder`（合併 combo 後） | 「先寫暫存檔再改名」和「列出檔案」，武器、數值、combo 三個資料夾共用。段落 E 時另外從 `WeaponFolder` 抽了一份，合併 combo 時收成這一份（20 號第 8 節） |
| `forge/` | `StatStartup`（新） | 模組建構時讀檔，寫錯就停，和 `WeaponStartup` 同一個形狀 |
| | `StatAttributeBridge` | 多一個建構子，決定哪些自訂屬性要同步；舊的建構子保留 |
| | `MobArmoryMod` | 多 `STAT_FILES`、`STAT_SETUP`；武器、載入器都改吃 `STATS.catalog()` |
| `forge/client/` | `GeneratedWeaponPack` | 多交給 Minecraft 一個 `lang/en_us.json`，放新數值的名稱 |
| `content/` | `ModStats` | **只改說明文字**。常數、`CATALOG`、`COMBAT_EFFECTS` 不變 |
| 測試 | `StatJsonTest`、`StatLoaderTest`、`StatStoreContractTest`、`InMemoryStatStore`、`StatFileGameTests`、`src/gametest/config/` 的兩個數值檔 | 見第 5 節 |
| 建置 | `build.gradle` | 遊戲內測試前，把 `src/gametest/config/` 複製進 `run-gametest/` |

## 3. 給其他分支的介面（R3 combo、怪物 JSON 合併時看這裡）

- `ModStats.ATTACK_DAMAGE` … `DODGE_CHANCE`、`ModStats.CATALOG`、`ModStats.COMBAT_EFFECTS`：**沒有改**。combo 分支的 `ModStats.MOVEMENT_SPEED.id()` 照用。
- 遊戲裡**所有的**數值，包括玩家加的：`MobArmoryMod.STATS.catalog()`。`ModStats.CATALOG` 只有 7 個內建數值。
  - 怪物 JSON 驗證數值時，請用 `STATS.catalog()`，玩家加的數值才能用在怪物身上。
- `StatAttributeBridge(StatCatalog, String)`、`WeaponStartup.load(store, catalog)` 都還在。
- 合併 combo 分支時的文字衝突，實際是 **3 個檔案、5 處**（2026-11-07 依 review-04 E3 更正；原本只寫了 `MobArmoryMod` 開頭，不正確）：
  - `MobArmoryGameTests` 的註冊：兩行都留。
  - `MobArmoryMod`：import 區全部留；common setup 的啟動 log 合成一行，數值數量用 `STATS.catalog().size()`。
  - `WeaponFolder`：import 和檔案結尾兩處，用分支的 `JsonFolder` 版本，再把段落 E 抽出的那份共用寫檔程式收掉。已在合併時完成，見 20 號第 8 節。
  - 細節和語意上的衝突（兩份一樣的寫檔程式），見 review-04（`java_minecraft_model/reviews/review-04-overnight.md`，不在 repo 裡）第 3 節。
  - `STATS`、`EQUIPMENT_LIBRARY` 那幾行分支沒有動，保留 main 的寫法。

## 4. 現有數值的預設值能不能在執行中改（指令要求說明）

**不能，要重開遊戲**（Q8 A，最小範圍）：

1. 新數值本來就一定要重開：自訂數值是一個新的屬性，屬性登記表在啟動後就凍結。
2. 既有數值的預設值：每種生物身上有哪些屬性、預設值是多少，是在啟動時的 `EntityAttributeCreationEvent`、`EntityAttributeModificationEvent` 決定的。之後改了數值檔，已經存在的生物種類也不會跟著變。
3. 只改「上下限」的話，技術上可以只影響武器的疊加計算，但規則會分成兩半，一部分生效、一部分不生效，容易誤會，所以不做（Q8 B，沒有採用）。

所以 `/mobarmory reload` 和網頁「從檔案重新載入」只重新載入武器，數值檔只在啟動時讀一次。

## 5. Given / When / Then 對照

| # | 情境 | 測試 | 結果 |
|---|---|---|---|
| H1 | 第一次啟動寫出 7 個檔，讀回來和以前一樣 | `StatLoaderTest.firstStartIsTheSameAsBefore`、`StatStoreContractTest.firstStartWritesEveryBuiltInStatThatReadsBackTheSame`（兩個實作）、`StatJsonTest.builtInsRoundTrip`（7 個）；`runClient` 實際寫出 7 個檔 | 通過 |
| H2 | 多一個自訂數值 → 每隻生物都有 | `StatLoaderTest.extraFilesAddStats`；遊戲內 `stat_files_reach_every_mob`（殭屍和玩家都是 0.25） | 通過 |
| H3 | 武器加減自訂數值 | 遊戲內 `weapon_uses_stats_from_files`（0.25 + 0.5 = 0.75） | 通過 |
| H4 | 對應原版跳躍力的數值，武器 +50% | `StatJsonTest.readsAVanillaBackedStat`；遊戲內 `weapon_uses_stats_from_files`（0.42 → 0.63） | 通過 |
| H5 | 改內建數值的預設值 | `StatLoaderTest.builtInFilesCanChangeTheNumbers` | 通過 |
| H6 | 新數值的名稱顯示在 tooltip | 語系檔的內容：`StatLoaderTest.namesBecomeALanguageFile`；名稱留下來：遊戲內 `stat_files_reach_every_mob`。**畫面上的 tooltip：手動**（第 6 節） | 部分自動 |
| H7 | 網頁武器表單列出新數值 | 由結構保證：`GET /api/v1/stats` 列出 `admin.stats()`，也就是讀出來的登記表。沒有另外的測試 | 未直接測 |
| B1 | 格式錯誤，錯誤訊息以欄位開頭 | `StatJsonTest.formatMistakesNameTheField`（11 種） | 通過 |
| B2 | 數字不合理 | `StatJsonTest.badNumbersAreRefused`（4 種） | 通過 |
| B3 | 檔名不是 id | `StatLoaderTest.everyProblemIsListedWithItsFile` | 通過 |
| B4 | 內建數值的檔案不見 → 用內建值 | `StatLoaderTest.missingBuiltInUsesTheBuiltInValue`、`emptyFolderUsesEveryBuiltIn`；遊戲內 `stat_files_reach_every_mob`（測試伺服器的資料夾裡沒有內建檔，7 個都用內建值，log 有警告） | 通過 |
| B5 | 內建數值改種類 → 拒絕 | `StatLoaderTest.builtInsKeepTheirKind` | 通過 |
| B6 | 兩個數值對應同一個屬性 | `StatLoaderTest.twoStatsOnOneAttributeAreRefused` | 通過 |
| B7 | 對應的原版屬性不存在 | 沿用 R1 的 `StatAttributeBridge.verifyAll`，在 common setup 檢查。這次沒有新增測試 | 沿用 |
| B8 | 武器用了被刪掉的數值 | 沿用 R2b：`WeaponLoaderTest.unknownStat` | 沿用 |
| B9 | reload 不改數值 | 由結構保證：`WeaponEditor.reload` 只讀武器檔，數值只在 `MobArmoryMod` 建構時讀一次 | 未直接測 |
| B10 | 客戶端少了數值檔 → 連不上 | 平台限制，寫進 README 的多人規則 | 只有文件 |
| B11 | `sync: false` | 讀到設定：`StatLoaderTest.syncCanBeTurnedOff`；客戶端真的收不到：手動 | 部分自動 |
| B12 | 不是 `.json` 的檔案不理 | `StatStoreContractTest.folderIgnoresFilesThatAreNotJson`、`StatLoaderTest.emptyFolderUsesEveryBuiltIn` | 通過 |

各層數量（新增的部分）：

| 層級 | 數量 | 內容 |
|---|---|---|
| Component | 25 | `StatJsonTest` |
| Module | 10 | `StatLoaderTest` |
| 契約 | 7 | `StatStoreContractTest`（兩個實作各 3 個，加上資料夾版 1 個） |
| System | 2 | `StatFileGameTests` |

## 6. 沒有覆蓋的範圍、手動驗證步驟

- **H6 名稱顯示在畫面上**：
  1. 在 `run/config/mobarmory/stats/` 放 `luck.json`：`{ "format": 1, "name": "Luck", "default": 0, "min": 0, "max": 1 }`。
  2. 在 `weapons/heavy_sword.json` 的 `modifiers` 加 `{ "stat": "luck", "add": 0.5 }`。
  3. 開 `runClient`，`/give @p mobarmory:heavy_sword`，tooltip 應該顯示「+50% Luck」。`luck` 是純數值，上限 ≤ 1，`EquipmentItem` 會當成機率，用百分比顯示（2026-11-07 依 review-04 E2 更正，原本誤寫成「+0.5 Luck」）。`/mobarmory stats` 也列出 Luck，值是 0.5000。
  4. 做完把兩個檔還原。這會在世界存檔留下 `mobarmory:luck` 屬性，所以要用測試世界做。
- **B11 `sync: false`**：只能用兩台客戶端看，和 R5 一起驗。
- **B10 數值檔不同時連不上**：平台限制，和 R5 一起驗。
- **網頁**：新數值在武器表單裡顯示 id，不是名稱（Q10 A 的取捨）。

## 7. SOLID／Pattern 檢查

依長期規定第 3 點，逐條對照這次的程式。

### SOLID

| 原則 | 對照的程式 | 判斷 |
|---|---|---|
| S 單一職責 | `StatJson` 只管格式；`StatFolder` 只管檔案；`StatLoader` 只管讀、檢查、補內建值；`StatSetup` 只是結果，加上產生語系檔；`StatStartup` 只管「啟動時讀，錯了就停」；`JsonFolder` 是寫檔細節的唯一一處（合併 combo 後，武器、數值、combo 共用） | 符合 |
| S（`ModStats`） | 職責從「遊戲裡所有的數值」縮小成「Java 程式要用的 7 個內建數值，也是預設檔的內容」，常數、型別都不變。這就是這次要動它的理由：不動它，玩家每加一個數值都要改 Java（違反 O） | 符合 |
| S（注意） | `GeneratedWeaponPack` 原本只放武器的模型和貼圖，現在也放數值名稱的語系檔，角色變成「玩家內容的執行時資源包」，類別名稱沒有跟著改。`MobArmoryMod` 的共用元件欄位從 10 個變成 12 個（多了 `STAT_FILES`、`STAT_SETUP`），15 號第 11 節「組裝根變大」的注意事項更明顯 | 注意，沒有違反 |
| O 開放封閉 | 加一個數值：1 個 JSON、0 行 Java（以前要改 `ModStats` 和語系檔）。有戰鬥效果的數值，還是要加一個 `StatEffect` 和 `COMBAT_EFFECTS` 一行，規則本來就是程式 | 符合 |
| L 替換 | `StatStore` 兩個實作跑同一組契約測試。讀檔產生的 `StatCatalog` 和 Java 建的是同一種型別，第一次啟動時內容也完全一樣（契約測試），下游的武器、怪物、橋接、網頁不用分辨 | 符合 |
| I 介面隔離 | `StatStore` 只有 `exists`、`statFiles`、`writeStat` | 符合 |
| D 依賴反轉 | `StatLoader` 只依賴 `StatStore` 介面和 `core/stat`，是純 Java（`CoreBoundaryTest` 守住 `data/`）。`forge/` 只依賴讀出來的 `StatCatalog`，不知道它來自檔案。`core/stat` 的公開介面沒有改：名稱、同步放在 `data/StatEntry`，沒有加進 `StatDefinition` | 符合 |

### Pattern

| Pattern | 在哪裡 | 為什麼 |
|---|---|---|
| Strategy（介面＋可替換實作） | `StatStore`：資料夾版、記憶體版，沿用 `WeaponStore` | 測試不碰使用者的設定資料夾 |
| Builder | 用 `StatCatalog.builder()` 建登記表，沿用 R1 | 重複的 id、兩個數值對應同一個屬性，由它擋下（B6），不用另外寫檢查 |
| 不可變快照 | `StatSetup`、`StatCatalog` 讀完就不變 | 屬性在啟動後不能變，資料也不給變 |
| Adapter | `StatAttributeBridge`（數值 → 屬性）、`GeneratedWeaponPack`（名稱 → Minecraft 的語系檔），沿用 | 純 Java 的 `data/` 不用知道 Minecraft |
| 沒用的 | 沒有為「純數值」和「對應原版屬性」做兩個類別，沿用 R1 `StatDefinition` 的 `vanillaAttribute` 是否存在 | core 不改；兩種只差在屬性從哪來 |

### 結論

沒有發現違反。「注意」是兩個已知的取捨：組裝根又大了一點，以及執行時資源包的名字比它現在的角色窄。

## 8. 小決定（照「最小範圍」自己決定的，記一行）

- 內建數值的預設檔不寫 `name`，名稱在模組自己的語系檔（O5）。
- `name` 最長 64 字元。
- 寫出的檔案裡，整數不帶 `.0`，`floor` 是 0 和 `sync` 是 true 時不寫，讓檔案和 README 的例子長得一樣。
- 遊戲內測試的數值資料夾只放兩個測試檔，所以 7 個內建數值都走「用內建值」，正好讓遊戲內測試也涵蓋 B4。
