# 行為報告：R1 可擴充數值系統 ＋ R2 裝備改變玩家數值（週五雛型）

> boundary-first 第 6 步。日期：2026-11-06（第 3 輪審核後更新）。
> 結果：**單元測試 106/106、遊戲內系統測試 18/18 通過**；驗收測試 **T1 通過**（自動系統測試，加上 `runClient` 手動走查並錄影，見第 6 節）。
> 第 3 輪：N5「實驗性設定」警告已找出原因並修正，`runClient` 確認新世界和重新載入都不再跳警告；N2（暴擊要蓄滿）、N3（攻擊速度數值）已實作；N1 暫不處理、N4 維持原版規則（見第 7 節）。

## 1. 邊界說明（已確認的版本）

完整內容見 [01-boundary-note.md](01-boundary-note.md)。實作後的邊界如下：

| 項目 | 內容 |
|---|---|
| 會變的東西 | 數值種類、每種數值的戰鬥效果、裝備清單和數值 |
| 形狀 | 原版 Attribute 當底層，加上純 Java 核心（`core/`）、資料（`content/`）和 Forge 轉接層（`forge/`） |
| 不准動的相依 | Minecraft、Forge 本身（沒有 Mixin、沒有 Access Transformer）；原版移動速度的計算；Forge 版本和 Java 25 toolchain |
| 抽象介面 | `StatEffect`：`CombatOutcome apply(CombatContext, RandomSource)`（Strategy）<br>`StatReader`：`double get(StatId)` |
| 邊界由測試守住 | `CoreBoundaryTest`：`core/`、`content/` 只要出現 `import net.minecraft` 或 `import com.mojang` 就失敗<br>`ContentResourcesTest`：新增裝備或數值時漏了外觀檔或名稱就失敗<br>`ShippedDataTest`：正式資源裡出現 GameTest 的註冊表內容就失敗（N5） |

### 改一處會牽動幾個檔案（實際結果）

| 要做的事 | 要動的檔案 | 數量 |
|---|---|---|
| 新增沒有戰鬥效果的自訂數值（例如「格擋率」） | `content/ModStats.java`、`lang/en_us.json` | 2 |
| 新增對應原版 Attribute 的數值 | `content/ModStats.java`（原版已有名稱翻譯，語系檔不用改） | 1 |
| 新增有戰鬥效果的數值 | 自訂數值的兩個檔案，再加一個 `StatEffect` 實作類別（並在 `ModStats` 的效果清單多一行） | 3 |
| 新增一件裝備 | `content/ModEquipment.java`、`assets/mobarmory/items/<id>.json`、`lang/en_us.json` | 3 |

事件處理器（`CombatEffectListener`、`EquipmentStatApplier`）不需要因為新增數值或裝備而修改。

**實測（N3，第 3 輪）**：新增「攻擊速度」數值、讓重劍攻速 −40%，功能程式只改了 **2 個檔案、6 行**：

| 檔案 | 改動 |
|---|---|
| `content/ModStats.java` | 加一筆 `StatDefinition.vanilla("attack_speed", "minecraft:attack_speed", 4.0, 0, 1024)`，並加進登記表（＋4 行） |
| `content/ModEquipment.java` | 重劍多一個 `percent(ATTACK_SPEED, −0.40)`（＋1／−1 行） |

`core/`、`forge/`、語系檔都沒動。tooltip、`/mobarmory stats`、每種生物的數值、契約測試都自動涵蓋新數值。另外補了測試（`LoadoutCalculatorTest` 一個、遊戲內 `heavy_sword_swings_slower` 一個）。

## 2. 商業決策（已確認）

使用者回覆「Use all recommended」，全部採用建議值，紀錄在 [03-open-questions.md](03-open-questions.md)。

| # | 決定 |
|---|---|
| Q4 | 百分比修飾相乘、固定值相加；移動速度不低於基礎值的 30%，迴避率最高 75%，暴擊率最高 100% |
| Q5 | 新數值在 `content/ModStats` 加一行，重開遊戲生效 |
| Q6 | 同種裝備共用一份定義，換裝時動態套用，tooltip 也從定義讀 |
| Q7 | 暴擊、迴避在雛型就有效果 |
| O1 | 武器只在主手生效（護甲只在自己的欄位生效） |
| O2 | 定義寫錯就啟動失敗，訊息寫出是哪件裝備、哪個數值 |
| O3 | 只有生物造成的傷害（近戰、投射物）可以迴避 |
| O4 | 暴擊倍率 1.5，和原版跳劈不疊加 |
| O5 | 原版物品不加我們的數值；所有生物都有我們的數值，值為預設值 |
| O6 | tooltip 顯示修飾，另有 `/mobarmory stats [對象]` 指令 |
| O7 | 重劍（攻擊 +6、移速 −30%）、短刀（攻擊 +2、暴擊 +25%、移速 +10%）、迴避胸甲（迴避 +15%）、重甲（護甲 +8、移速 −15%） |
| O8 | 借用原版外觀：下界合金劍、鐵劍、鎖鍊胸甲、鐵胸甲（護甲穿在身上也用原版樣子） |
| O9 | 一個創造模式分頁放全部裝備，不做合成表 |
| N2 | 玩家近戰的暴擊機率要蓄滿才判定（和原版跳劈的條件一致）（第 3 輪） |
| N3 | 新增「攻擊速度」數值，重劍攻速 −40%（4.0 → 2.4）（第 3 輪） |
| N4 | 30% 移速下限只限制裝備，緩速藥水等原版效果維持原版規則（第 3 輪） |

### 實作時採用的假設（沒有另外問，請過目）

這些都在已確認的邊界內，是實作細節。如果和你想的不一樣，改起來都只動一兩個檔案。

| # | 假設 | 影響 |
|---|---|---|
| A1 | 「移動速度不低於 30%」只限制**裝備**造成的減速。疾跑加速、緩速藥水等原版效果照原版規則疊在上面 | 喝緩速藥水仍可能低於 30%（N4 確認維持） |
| A2 | 武器是一般物品，沒有原版劍的橫掃。攻擊冷卻改由「攻擊速度」數值控制（N3） | 空手和短刀攻速 4.0；重劍 2.4 |
| A3 | ~~玩家近戰的暴擊機率判定不要求蓄力滿格~~ | 已由 N2 改掉：要蓄滿才判定 |
| A4 | 苦力怕爆炸、荊棘反傷的來源都是生物，所以算「生物造成的傷害」，可以迴避 | — |
| A5 | 裝備數值用**一個**固定 id 的修飾（`mobarmory:equipment`）套在原版 Attribute 上，值 = 目標值 − 基礎值；這個修飾不存檔 | 重算幾次、重新登入都不會疊加 |
| A6 | ~~系統測試和正式程式一起打包~~ | 已由 N5 改掉：系統測試是獨立的測試用模組，不進正式 jar |

## 3. 結構摘要

```
io.github.alextixu.mobarmory
├─ core/                       純 Java，不 import Minecraft（CoreBoundaryTest 守著）
│  ├─ StatReader               介面：double get(StatId)
│  ├─ stat/                    StatId、StatDefinition、StatCatalog（Builder）、StatModifier、ModifierOperation、StackingRules
│  ├─ equipment/               EquipmentId、EquipmentSlotKind、EquipmentDefinition、EquipmentCatalog（Builder）、LoadoutCalculator
│  └─ combat/                  StatEffect（介面）
│                              ├─ DodgeEffect implements StatEffect
│                              └─ CritEffect  implements StatEffect
│                              CombatContext、CombatOutcome、CombatEffects、CombatResolver、DamageKind、RandomSource
├─ content/                    資料：ModStats（6 個數值 ＋ 效果清單）、ModEquipment（4 件裝備）
└─ forge/                      唯一接觸 Minecraft / Forge 的地方
   ├─ MobArmoryMod             @Mod 進入點，只接線
   ├─ StatAttributeBridge      自訂數值 → RangedAttribute（同步到客戶端）；原版數值 → 原版 Attribute
   ├─ EntityStatBinder         EntityAttributeModificationEvent：每種生物都加上自訂數值
   ├─ EntityStatReader         implements core.StatReader
   ├─ EquipmentItemFactory     每筆裝備定義 → 一個 EquipmentItem（Factory）
   ├─ EquipmentItem extends Item   tooltip 從共用定義讀
   ├─ EquipmentStatApplier     LivingEquipmentChangeEvent → LoadoutCalculator → 套修飾
   ├─ CombatEffectListener     LivingAttackEvent（迴避）、AttackEntityEvent（記下蓄力）＋CriticalHitEvent（玩家暴擊）、LivingHurtEvent（其他暴擊）
   ├─ StatsCommand             /mobarmory stats [對象]
   └─ Slots                    欄位對照

src/gametest/（獨立 source set，只在測試伺服器載入，不進正式 jar）
└─ io.github.alextixu.mobarmory.gametest
   ├─ MobArmoryGameTestMod     測試用模組 mobarmory_gametest 的 @Mod 類別
   ├─ MobArmoryGameTests       在 RegisterEvent 把測試註冊到 Registries.TEST_FUNCTION
   └─ EquipmentGameTests、CombatGameTests、PlayerGameTests
```

資源：`assets/mobarmory/items/*.json`（4 個，指向原版模型）、`assets/mobarmory/lang/en_us.json`。測試用的 `META-INF/mods.toml` 和 `data/mobarmory/test_instance/*.json`（18 個）在 `src/gametest/resources/`。

## 4. 測試報告

### 各層數量

| 層級 | 測試類別 | 數量 | 結果 |
|---|---|---|---|
| Component | `StatCatalogTest` 8、`StackingRulesTest` 11、`EquipmentCatalogTest` 5、`CritEffectTest` 7、`DodgeEffectTest` 5 | 36 | 全過 |
| Module | `LoadoutCalculatorTest` 14（02 的成功條件逐一破壞，加 N3）、`CombatResolverTest` 4 | 18 | 全過 |
| 契約 | `EquipmentContractTest`：4 件裝備 × 6 條 = 24<br>`StatEffectContractTest`：2 個效果 × 7 條 = 14<br>`ContentResourcesTest`：4 件裝備 × 2 ＋ 2 個自訂數值 = 10 | 48 | 全過 |
| 邊界守衛 | `CoreBoundaryTest`（`core/`、`content/` 不 import Minecraft）2、`ShippedDataTest`（N5）2 | 4 | 全過 |
| System（遊戲內） | `gradlew runGameTestServer`，真的伺服器、真的物品和 Attribute | 18 | 全過 |
| **合計** | 單元 106 ＋ 遊戲內 18 | **124** | **全過** |

指令：`gradlew clean build runGameTestServer`。遊戲內測試的結果另存為 `run/gametest-report.xml`（JUnit 格式）。系統測試的機制驗證過：故意把一個期望值改錯時，伺服器回報「1 required tests failed」，Gradle 也跟著失敗。

第 3 輪起，`runGameTestServer` 會轉去跑 `runGametestGameTestServer`（gametest source set 的 run，同時載入 Mob Armory 和測試用模組），所以指令不變；正式 jar 只有 main 的 38 個類別，沒有任何測試內容。

### 契約內容

- **裝備契約**（每件裝備都跑）：只引用已註冊的數值；單獨一件不會碰到上下限；放在自己的欄位時會改到列出的每個數值；放在其他欄位完全沒效果；拿起再放下回到原值；重算不疊加。
- **戰鬥效果契約**（每個 `StatEffect` 都跑）：數值 0 永不觸發；數值在上限且亂數最小時必定觸發（近戰、投射物都是）；超過上限的值等同上限；NaN 和負數不丟例外、視同 0；同一個種子結果一致；環境傷害永不改變；傷害不會變成負數或 NaN。
- **內容資源契約**：每件裝備都有物品外觀檔（指向定義裡的原版外觀）和名稱；每個自訂數值都有名稱。

### Given / When / Then 對照（[02](02-given-when-then.md) 的每一列）

層級：C = Component、M = Module、K = 契約、S = System（遊戲內）。

| # | Given | When | Then | 測試 | 結果 |
|---|---|---|---|---|---|
| H1 | 玩家空手，移動速度 0.1 | 主手拿起重劍 | 0.07 | C `heavySwordPercent`、M `h1HeavySwordSlowsDown`、S `t1_heavy_sword_slows_player`、S `t1_real_player_and_stats_command` | 通過 |
| H2 | 主手拿重劍（0.07） | 換回空手 | 回到 0.1 | M `h2EmptyHandRestoresSpeed`、S `t1_heavy_sword_slows_player`、S `t1_real_player_and_stats_command` | 通過 |
| H3 | 登記表新增一個數值 | 遊戲啟動 | 玩家、殭屍、骷髏都有，等於預設值 | C `newCustomStatIsListedForAllMobs`、S `custom_stats_on_every_mob`（玩家、殭屍、骷髏、牛） | 通過 |
| H4 | 玩家空手，迴避率 0 | 穿上迴避胸甲 | 0.15 | M `h4EvasionChestplate`、S `evasion_chestplate_gives_dodge` | 通過 |
| H5 | 攻擊者暴擊率 100%，傷害 10 | 打中 | 15 | C `h5CertainCrit`、S `mob_crit_multiplies_damage`（4 → 6）、S `player_crit_uses_one_multiplier`（倍率 1.5） | 通過 |
| H6 | 迴避率在上限、亂數必定迴避 | 被殭屍打 | 不受傷害 | C `h6DodgeAtCap`、S `dodge_avoids_some_mob_hits`（75% 時 200 下有一部分被閃掉，0% 時全部打中） | 通過 |
| H7 | 主手拿短刀 | 查攻擊力 | 3.0 | C `daggerFlat`、M `h7DaggerAttack`、S `dagger_attack_damage` | 通過 |
| B1 | 重劍在主手 | 移到副手 | 減速不生效 | M `b1OffhandDoesNotCount`、M `b1ChestplateInHandDoesNotCount`、K `doesNothingInAnyOtherSlot`、S `heavy_sword_in_offhand_does_nothing` | 通過 |
| B2 | 主手重劍、身穿重甲 | 算移動速度 | 相乘 0.0595 | C `percentagesMultiply`、M `b2SlowdownsMultiply`、S `slowdowns_multiply` | 通過 |
| B3 | 很多減速裝備 | 低於下限 | 夾在 0.03 | C `movementSpeedFloor`、C `floorFollowsBase`、M `b3SpeedFloor` | 通過 |
| B4 | 很多迴避裝備 | 超過上限 | 夾在 75%；暴擊最高 100% | C `chanceCaps`、M `b4DodgeCap`、C `capIsEnforced` | 通過 |
| B5 | 裝備引用不存在的數值 | 啟動 | 失敗，寫出裝備和數值 | C `unknownStatFails`（原版 Attribute 名稱打錯則由 `StatAttributeBridge.verifyAll` 在啟動時擋下） | 通過 |
| B6 | 兩個數值同 id | 啟動 | 失敗，寫出 id | C `duplicateIdFails` | 通過 |
| B7 | 預設值超出範圍 | 啟動 | 失敗，寫出數值 | C `defaultOutsideRangeFails` | 通過 |
| B8 | 迴避率 75% | 環境傷害 | 不能迴避 | C `b8EnvironmentCannotBeDodged`、K `environmentDamageIsNeverChanged`、S `environment_damage_is_never_dodged`（摔落、一般傷害各 50 次） | 通過 |
| B9 | 暴擊判定成功＋原版跳劈 | 算傷害 | 只乘一次 1.5 | C `b9NoDoubleCrit`、S `player_crit_uses_one_multiplier` | 通過 |
| B10 | 主手拿重劍 | 丟到地上 | 回到 0.1，不殘留 | M `h2EmptyHandRestoresSpeed`、S `t1_heavy_sword_slows_player`（確認修飾已移除） | 通過* |
| B11 | 拿重劍時死亡 | 重生 | 依重生後的裝備重算 | M `b11b12RecomputeDoesNotStack`、S `fresh_player_entity_does_not_stack` | 通過* |
| B12 | 拿重劍時登出 | 再登入 | 0.07，不是 0.049 | M `b11b12RecomputeDoesNotStack`、S `refresh_does_not_stack`、S `fresh_player_entity_does_not_stack`、S `t1_real_player_and_stats_command`（確認修飾不存檔） | 通過* |
| B13 | 主手拿原版鑽石劍 | 查暴擊、迴避 | 都是 0 | M `b13VanillaItemAddsNothing`、S `vanilla_sword_adds_no_stats` | 通過 |
| B14 | 同一件裝備重複修飾同一個數值 | 啟動 | 失敗 | C `sameStatTwiceFails` | 通過 |
| N2 | 玩家暴擊率 100%，這一下沒蓄滿 | 打中牛 | 不暴擊：0.208（暴擊會是 0.312） | C `n2WeakSwingNeverCrits`、C `n2FullStrengthRolls`、S `player_crit_needs_full_charge` | 通過 |
| N3 | 玩家空手，攻擊速度 4.0 | 主手拿重劍 | 2.4；放下回 4.0 | M `n3HeavySwordSwingsSlower`、S `heavy_sword_swings_slower` | 通過 |
| N5 | 正式 jar | 建立或載入世界 | 不跳實驗性設定警告 | 守衛 `mainResourcesHaveNoGameTestEntries`、`gameTestInstancesLiveInTheGametestSourceSet`；`runClient` 手動確認（第 6 節） | 通過 |

\* B10～B12 在遊戲內是用「把物品從欄位拿掉」和「新的玩家實體帶著同一把劍 tick」模擬；真正按 Q 丟出、死亡重生、存檔離開再進入，留在第 6 節的手動走查。

### 換裝事件真的有接上

- `zombie_equipment_event`：一隻會 tick 的殭屍拿到重劍，Minecraft 自己偵測到換裝並發出事件，殭屍的速度變成基礎值的 70%。
- `t1_real_player_and_stats_command`：用 `makeMockServerPlayerInLevel()` 建立一個經由 PlayerList 加入世界的伺服器端玩家。拿重劍、tick 一次後速度是 0.07；以這個玩家的身分執行 `/mobarmory stats`，輸出的移動速度是 `0.0700`、基礎值 `0.1000`。
- `player_crit_uses_one_multiplier`、`player_crit_needs_full_charge`（第 3 輪改寫）：同樣的伺服器端玩家用真正的 `Player.attack` 打牛，先 tick 讓攻擊條蓄滿。跳劈、100% 暴擊、兩者同時都是 1.5 傷害；剛揮完立刻再揮（沒蓄滿）就不暴擊。

## 5. 沒有覆蓋的範圍

測試只證明已定義、已覆蓋的行為。以下沒有被自動測試證明：

- **客戶端畫面**：tooltip、物品圖示、護甲穿在身上的外觀沒有自動測試，改由第 6 節的手動走查截圖確認；暴擊粒子沒有確認。
- **玩家實際移動**：測試證明的是 Attribute 數值；玩家在客戶端走得多快由原版依這個數值計算，沒有量測實際位移。
- **玩家揮劍的客戶端部分**：第 3 輪起玩家暴擊改用伺服器端的 `Player.attack` 測；客戶端的攻擊條、揮劍動畫、暴擊粒子沒有確認。
- **N2、N3 的遊戲畫面**：重劍 tooltip 多了「−40% Attack Speed」、攻擊條變慢，這兩項只有自動測試，沒有重新在 `runClient` 截圖。
- **真正的死亡重生**：用等效情境模擬（見上表 \*）。存檔重登和按 Q 丟出物品已在第 6 節手動確認。
- **啟動失敗的畫面**：B5～B7、B14 在單元測試證明會丟出寫明原因的例外；沒有實際開一個寫錯定義的遊戲看錯誤畫面。
- **多人同步（R5）**：自訂數值設定為同步，但沒有測兩個客戶端。
- **效能**：每次換裝重算 6 個數值，沒有量測。

## 6. 遊戲內手動驗證（runClient）

2026-11-06 由開發 session 在使用者同意下操作 `gradlew runClient`：建立創造模式、超平坦的單人世界「Mob Armory Demo」，用 NVIDIA 錄下整段走查（約 5 分鐘，存在使用者電腦上，沒有放進 repo）。截圖在 [screenshots/](screenshots/)。

| # | 操作 | 預期 | 畫面上的結果 | 截圖 |
|---|---|---|---|---|
| 1 | 空手，`/mobarmory stats` | 速度 0.1 | `Speed: 0.1000 (base 0.1000)`，另外列出暴擊率、迴避率 | [01](screenshots/01-empty-hand.png) |
| 2 | `/give @s mobarmory:heavy_sword`（在主手） | 速度 0.07（T1、H1） | `Speed: 0.0700`、`Attack Damage: 7.0000`；手上是下界合金劍外觀 | [02](screenshots/02-heavy-sword-t1.png) |
| 3 | 開物品欄，Mob Armory 分頁，滑鼠停在每件裝備上 | 4 件都在；tooltip 和定義一致（Q6、O6、O9） | 重劍「+6 Attack Damage／−30% Speed」、短刀「+2／+25% Crit Chance／+10% Speed」、迴避胸甲「+15% Dodge Chance」、重甲「+8 Armor／−15% Speed」 | [03](screenshots/03-tooltip-heavy-sword.png)～[06](screenshots/06-tooltip-heavy-armor.png) |
| 4 | 按 F 把重劍換到副手 | 速度 0.1（B1） | `Speed: 0.1000` | [07](screenshots/07-offhand-b1.png) |
| 5 | 換回主手，`/item replace entity @s armor.chest with mobarmory:heavy_armor` | 速度 0.0595（B2） | `Speed: 0.0595`、`Armor: 8.0000` | [08](screenshots/08-sword-and-armor-b2.png) |
| 6 | 切第三人稱 | 穿著原版鐵胸甲外觀（O8） | 身上是鐵胸甲、手拿重劍 | [09](screenshots/09-third-person.png) |
| 7 | 按 Q 丟掉重劍 | 只剩重甲：0.1 × 0.85 = 0.085（B10） | `Speed: 0.0850`、`Attack Damage: 1.0000` | [10](screenshots/10-dropped-sword-b10.png) |
| 8 | `/summon husk ^ ^ ^3 {NoAI:1b}`，查屍殼的數值 | 怪物也有暴擊率、迴避率（H3） | `Speed: 0.2300`、`Crit Chance`、`Dodge Chance` 都在 | [11](screenshots/11-husk-stats-h3.png) |
| 9 | 給屍殼重劍 | 0.23 × 0.7 = 0.161 | `Speed: 0.1610`、`Attack Damage: 9.0000`（3 ＋ 6） | [12](screenshots/12-husk-heavy-sword.png) |
| 10 | 重劍＋重甲，Save and Quit to Title，再進同一個世界 | 仍是 0.0595，不是更低（B12） | 離開前 `0.0595`，重新進入後 `0.0595` | [13](screenshots/13-before-relog.png)、[14](screenshots/14-after-relog-b12.png) |

全部符合預期。屍殼用的是白天不會燒起來的殭屍變種，`NoAI` 只是讓它站著不動；它的換裝事件照樣會觸發。

**發現的問題**：建立世界時跳出「These settings are using experimental features」，載入既有世界時跳出「Worlds using Experimental Settings are not supported」（[15](screenshots/15-experimental-warning.png)）。這是給玩家看的畫面，演示前要處理，見第 7 節 N5。

### 第 3 輪：N5 修正後再確認

修正 N5 後，同樣用 `runClient`（視窗模式，事先確認前景是 Claude、沒有其他全螢幕程式）建立創造模式、超平坦的新世界「N5 Check」：

| # | 操作 | 結果 | 截圖 |
|---|---|---|---|
| 1 | 主選單 | 「3 mods loaded」：minecraft、forge、mobarmory，測試用模組沒有載入 | — |
| 2 | Create New World | 沒有警告，直接進入世界 | [16](screenshots/16-n5-new-world-no-warning.png) |
| 3 | 拿重劍、`/mobarmory stats`，Save and Quit，再載入同一個世界 | 載入時沒有警告；速度仍是 `0.0700` | [17](screenshots/17-n5-reload-no-warning.png) |

## 7. 第 3 輪：N1～N5 的決定與結果

使用者原話：「2.github目前當作儲存倉庫 先不用寫任何授權其他同意」，之後補充：「寫的授權都先刪除」。

| # | 問題 | 決定 | 結果 |
|---|---|---|---|
| N1 | 推到 GitHub 的授權 | **刪除所有授權檔，GitHub 目前只當儲存庫** | 刪除 `LICENSE.txt`；`mods.toml` 的 `license` 欄位因為 Forge 必填而保留為 `"All Rights Reserved"`，見下方 |
| N2 | 暴擊機率要不要像原版跳劈一樣要求蓄滿 | **要**（照建議） | 已實作，見下方 |
| N3 | 重武器要不要揮得比較慢 | **要，重劍 −40% 攻速**（照建議） | 已實作，見第 1 節「實測」 |
| N4 | 30% 移速下限要不要擋住緩速藥水等原版效果 | **不要，維持原版規則**（照建議） | 不改程式 |
| N5 | 建立／載入世界時的「實驗性設定」警告 | **查證並修正**（照建議） | 已修正，見下方 |

### N1：刪除授權

- **刪除 `LICENSE.txt`**：這是 Forge 範本專案附的 Forge LGPL 2.1 授權，不是我們的。
- **`mods.toml` 的 `license` 欄位保留為 `"All Rights Reserved"`**：Forge 66 的 `ModLoader` 會把 `license` 是空的模組檔轉成載入錯誤。實測拿掉這一行，測試伺服器直接失敗：`ModLoadingException: Missing License Information in file …/build/sourceSets/main/`。`"All Rights Reserved"` 沒有給任何授權，和完全不寫授權時的法律效果相同。檔案裡加了註解說明為什麼不能刪。測試用模組（`src/gametest`）的 `mods.toml` 同樣處理。
- **其他授權宣告**：`gradle.properties`、我們的原始碼、文件裡都沒有。`gradlew`、`gradlew.bat` 開頭的 Apache-2.0 註解，是 Gradle 對它自己這兩個啟動腳本的著作權聲明，不是本專案的授權；Apache-2.0 要求轉散布時保留這些聲明，所以沒有刪。`changelog.txt` 是 Forge 的更新紀錄，不是授權檔。

### N5：原因與處理

**原因（已查證）**：

1. Forge 26.3 的 `ResourceManagerRegistryLoadTask`：從資料包載入的「資料驅動註冊表」內容，只要資料包不是原版，一律標成 `Lifecycle.experimental()`。
2. `test_instance`（GameTest 的測試實例）就在原版 `RegistryDataLoader` 的世界註冊表清單裡。
3. 我們把 16 個 `data/mobarmory/test_instance/*.json` 放在主程式資源裡，跟著模組資料包一起載入，世界就被判定為實驗性。
4. demo 世界的 `level.dat` 有 `confirmedExperimentalSettings`（按過確認），但 Forge 自己的 `forgeLifecycle` 是 `stable`，可見警告不是 Forge 本身造成的。
5. 修正後只拿掉了這些測試檔，新世界和重新載入就都不再跳警告，原因得到確認。

**處理**：

- GameTest 搬到獨立的 `src/gametest` source set，當成一個測試用模組 `mobarmory_gametest`（自己的 `mods.toml`，依賴 `mobarmory`），用 `RegisterEvent` 自己註冊測試，Mob Armory 不再引用任何測試程式。
- Forge 66 是用「classpath 上有 `META-INF/mods.toml` 的位置」來認模組，不支援把兩個資料夾合成一個模組，所以測試必須是獨立模組。ForgeGradle 會替每個 source set 各建一組 run，`runGametestGameTestServer` 的 classpath 同時有兩個模組；`runGameTestServer` 改成轉去跑它。
- 正式 jar 只打包 main：38 個類別、0 個測試檔。`ShippedDataTest` 守住主程式資源不能再出現 `test_instance`、`test_environment`。

**順便修掉的 build 問題**：MDK 預設開啟 build cache，而且 `merge-source-sets` 讓 class 和資源共用同一個輸出資料夾。在一次 clean build 裡，`compileJava` 從 cache 還原後，資料夾裡的 class 全部不見，連帶正式 jar 也會是空殼。現在 `compileJava`、`processResources` 不走 build cache（這兩個 task 很快），clean build 已驗證正常。

### N2：暴擊要蓄滿

- 規則放在核心：`CombatContext` 多一個 `fullStrength`（生物、投射物沒有蓄力，一律算蓄滿），`CritEffect` 只在蓄滿時擲暴擊。原版跳劈本來就要求蓄滿，所以兩種暴擊的條件一致。
- Minecraft 在觸發 `CriticalHitEvent` 之前就把蓄力歸零（`Player.attack` 先呼叫 `onAttack()`）。所以 `CombatEffectListener` 改在更早的 `AttackEntityEvent` 記下這一擊的蓄力，暴擊事件再讀出來；門檻和原版一樣是 0.9。
- 測試：`CritEffectTest` 多 2 個；遊戲內 `player_crit_needs_full_charge` 用真正的揮劍確認沒蓄滿不暴擊。

## 8. SOLID／Pattern 檢查

這一節是 2026-11-07 補的，依長期規定第 3 點：每份行為報告都要有這一節（review-03 P3-9）。內容逐條對照當天 main 上的程式。R1、R2 之後有兩處變動：
- R2b 把裝備改成 JSON 檔，`content/ModEquipment` 已經刪除。
- R4 在 `ModStats` 加了 `max_health`。

### SOLID

| 原則 | 對照的程式 | 判斷 |
|---|---|---|
| S 單一職責 | `StatCatalog` 只列出有哪些數值；`StackingRules` 只管多個修飾怎麼合起來（固定值相加、百分比相乘、上下限）；`DodgeEffect`、`CritEffect` 各管一種戰鬥效果；`CombatResolver` 只管依序執行效果；`LoadoutCalculator` 只管「身上的裝備 → 目標數值」；`StatAttributeBridge` 只管數值和原版 Attribute 的對應；`EntityStatBinder` 只管把自訂數值掛到每種生物；`EquipmentStatApplier` 只管換裝時套用修飾 | 符合 |
| O 開放封閉 | 加一種數值：`ModStats` 一行，自訂數值再加語系檔一行。有戰鬥效果的話，再加一個 `StatEffect` 實作和效果清單一行。N3 實測加「攻擊速度」：2 個檔案、6 行，事件處理器都沒改（第 1 節）。加一件裝備：R2b 之後是 1 個 JSON 檔、0 行 Java；第 1 節表格裡的 3 個檔案是 R2b 之前的做法 | 符合 |
| O（注意） | `StackingRules` 用 `switch` 判斷 `ModifierOperation`（`ADD`、`MULTIPLY`）。疊加方式是 Q4 決定的封閉集合，加第三種疊加方式要改 enum、`StackingRules` 和武器 JSON 的格式（`WeaponJson`） | 刻意的取捨 |
| L 替換 | `StatEffect` 的兩個實作跑同一組契約測試（`StatEffectContractTest`）：數值 0 不觸發、超出範圍會夾住、怪數字不丟例外、同樣的亂數得到同樣的結果、環境傷害不變、傷害不會變負數。裝備定義也有契約測試（`EquipmentContractTest`） | 符合 |
| I 介面隔離 | `StatEffect` 只有 `apply` 一個方法；`StatReader` 只有 `get`；`RandomSource` 只有擲骰子用的方法 | 符合 |
| D 依賴反轉 | `core/` 只依賴自己的 `StatReader`、`RandomSource` 介面，不 import Minecraft（`CoreBoundaryTest`）；`forge/EntityStatReader` 實作 `StatReader`，遊戲裡擲骰用生物自己的亂數；`CombatEffectListener` 只認得 `CombatEffects` 裡的 `StatEffect`，不知道有暴擊、迴避 | 符合 |

### Pattern

| Pattern | 在哪裡 | 為什麼 |
|---|---|---|
| Strategy | `StatEffect`：`DodgeEffect`、`CritEffect` | 數值的戰鬥效果會一直加（Q7）；事件處理器不用跟著改 |
| 依序執行的效果鏈 | `CombatResolver`：每個效果看到前一個的結果，被取消就停 | 迴避在前、暴擊在後；新的效果只要排進清單 |
| Builder | `StatCatalog.builder()`、`EquipmentCatalog.builder()` | 建好之後唯讀；建的時候檢查 id 重複、數值不存在（O2） |
| Adapter | `StatAttributeBridge`（數值 → 原版 Attribute）、`EntityStatReader`（生物 → `StatReader`） | 核心是純 Java，可以不開遊戲就測 |
| Factory | `EquipmentItemFactory`：每個定義做一個物品 | 新裝備不用新的類別 |
| 固定 id、每次從頭算 | `EquipmentStatApplier` 只用一個固定 id 的修飾（`mobarmory:equipment`），值是「目標值 − 基礎值」 | 重生、重新登入、換多少次裝都不會疊加（B11、B12） |

### 結論

沒有發現違反。唯一的「注意」是疊加方式用 `switch`，屬於刻意的取捨。
