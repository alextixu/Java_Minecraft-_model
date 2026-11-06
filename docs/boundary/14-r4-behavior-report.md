# 行為報告：R4 自訂怪物（驗收 T2）

> boundary-first 第 6 步。日期：2026-11-06。分支 `feature/r4-mobs`（worktree `mod-r4-mobs/`，從 main `d8d6753` 分出），只在本機 commit，沒有 push、沒有合併。
> 結果：**`gradlew clean build runGameTestServer` 全過：單元測試 133/133、遊戲內系統測試 34/34**（R1／R2 原有 106 ＋ 18，R4 新增 27 ＋ 16）。
> 驗收測試 **T2 通過**：實際加第 3 種怪物，只改 3 個檔案、Java 只加一筆定義，測試全過（第 1 節「T2 實測」）。
> 第三輪 Q8 已決定 **B**（使用者 11/06）：怪物的揮擊間隔不跟著攻擊速度，維持原版每秒打一下。程式本來就是原版行為，不用改。
> **還沒做**：`runClient` 手動走查和截圖（依指示和示範影片一起做，劇本在第 6 節）。

## 1. 邊界說明（已確認的版本）

完整內容見 [05-r4-boundary-note.md](05-r4-boundary-note.md)，決定見 [07](07-r4-open-questions.md)（使用者 11/06：全部照建議）。實作後的邊界如下：

| 項目 | 內容 |
|---|---|
| 會變的東西 | 怪物清單和數值、行為的種類、外觀 |
| 形狀 | 怪物是一筆資料（`MobDefinition`），由 `MobEntityFactory` 建出生物種類、基礎屬性、生怪蛋（Factory）；行為是 `Behavior`（資料）＋ `GoalBinder`（Strategy）；外觀從原版人形身體挑，加大小倍率 |
| 接上既有系統 | 數值全部走 R1 的登記表（每個數值都有基礎值，自訂數值也在）；裝備走 R2 的 `EquipmentStatApplier`（換裝事件 → `LoadoutCalculator`），沒有另寫一套 |
| 不准動的相依 | `core/stat`、`core/equipment`、`core/combat`、`EquipmentStatApplier`、`CombatEffectListener`、`EntityStatBinder`：**一行都沒改**（`git diff d8d6753..HEAD` 確認）；Minecraft、Forge 本身（沒有 Mixin、沒有 Access Transformer） |
| 抽象介面 | 新建 `Behavior`（core，只描述做什麼和參數）<br>新建 `GoalBinder<B extends Behavior>`：`void bind(ArmoryMob mob, B behavior, Goals goals)`，`Goals` 是兩個 goal selector（做什麼、打誰） |
| 邊界由測試守住 | `CoreBoundaryTest`：`core/mob` 不 import Minecraft<br>`MobContractTest`：每筆怪物定義跑同一組契約<br>`ContentResourcesTest`：新怪物漏了名稱或生怪蛋外觀檔就失敗<br>遊戲內 `mob_behaviors_all_have_binders`：每個行為都有 binder，binder 的契約 |

### 改一處會牽動幾個檔案（實際結果）

| 要做的事 | 要動的檔案 | 數量 |
|---|---|---|
| 新增一種怪物（沿用既有的四種身體） | `content/ModMobs.java` 一筆定義、`lang/en_us.json` 名稱 1 行、`items/<id>_spawn_egg.json` 生怪蛋外觀 | **3**（Java 1 個） |
| 新增一種行為 | `core/mob` 一個 `Behavior` record、`forge` 一個 `GoalBinder`、`BehaviorGoals.standard()` 1 行；遊戲內契約測試的範例表 1 行 | 3（＋測試 1） |
| 新增一種外觀身體 | `MobLook.Body` 加一個值、`ArmoryMobRenderer.body()` 加一個 case（編譯器會逼你加） | 2 |
| 讓怪物使用一個新數值 | 照 R1 加數值（1～2 個檔案），怪物定義再寫一行 | 照 R1 |

**和 05 的估計不同的地方**：05 估計新增一種怪物是 2 個檔案，實際是 3 個。多的是生怪蛋的外觀檔：Minecraft 26.x 的物品外觀一律讀 `items/<物品 id>.json`，物品建立時就固定，沒有辦法由程式指定（查過 `Item.Properties`，`ITEM_MODEL` 會在最後被物品 id 蓋掉）。`ContentResourcesTest` 會在漏掉這個檔案時失敗。生怪蛋的名稱則由怪物名稱組成（`"%s Spawn Egg"`），所以語系檔只要 1 行。

### T2 實測：加第 3 種怪物只要加一筆定義

做法：在 `content/ModMobs.java` 加一隻「哨兵 `sentinel`」，刻意和前兩隻都不同：骷髏身體、主手短刀、**胸前穿重甲**（證明怪物也能穿護甲）、閒晃 0.8 倍速、追擊 1.2 倍速。

commit `cbb38b5`（示範）：

| 檔案 | 改動 |
|---|---|
| `content/ModMobs.java` | 加一筆 `MobDefinition.builder("sentinel")…build()`（＋11 行），並加進 `ALL` 清單（＋1／−1 行） |
| `assets/mobarmory/lang/en_us.json` | `"entity.mobarmory.sentinel": "Sentinel"`（＋1 行） |
| `assets/mobarmory/items/sentinel_spawn_egg.json` | 新檔，借原版骷髏生怪蛋外觀（6 行） |
| **合計** | **3 個檔案，＋19／−1 行**；`core/`、`forge/`、測試程式都沒動 |

`gradlew clean build runGameTestServer` 的結果：

- 單元測試 140/140：比 133 多 7 個，全部是自動多出來的（`MobContractTest` 5 條、`ContentResourcesTest` 2 條各多跑一次哨兵）。
- 遊戲內 34/34：`t2_every_mob_spawns_as_defined` 走過登記表裡**每一隻**怪物，哨兵也被檢查：生怪蛋指向牠的種類；生成後每個數值都等於「定義 ＋ 裝備」算出來的值（攻擊 3＋2＝5、移動速度 0.23×1.1×0.85＝0.21505、護甲 8、生命 24）；滿血；拿著短刀、穿著重甲；有行為對應的 AI goal。`h7_custom_mobs_have_every_stat`、`mob_behaviors_all_have_binders` 也自動涵蓋哨兵。
- 啟動 log：`Mob Armory: 7 stats, 4 pieces of equipment, 3 mobs`。

**示範完已移除**（commit `ddfefe6`，revert `cbb38b5`）：07 的 O1 決定雛型只有狂戰士和遊擊兵，第 3 隻是 T2 的證明，不是使用者決定要的內容。要保留時 `git revert ddfefe6` 即可。

## 2. 商業決策（已確認）

使用者 11/06：「全部照建議」。紀錄在 [07-r4-open-questions.md](07-r4-open-questions.md) 的決定總表。

| # | 決定 |
|---|---|
| Q1～Q3 | 同意 SOLID 判斷、Pattern A（每筆定義一個生物種類；行為 Strategy；外觀挑原版身體）、變更邊界 |
| Q4 | 外觀從原版人形身體挑：殭屍、屍殼、骷髏、流髑，加大小倍率 |
| Q5 | 只用生怪蛋（Mob Armory 分頁）和 `/summon`，不自然生成 |
| Q6 | 數值立刻生效；行為在怪物建立時決定（下次生成或重新載入才換） |
| Q7 | 主動攻擊 16 格內生存／冒險模式玩家；被任何生物打會反擊；不主動攻擊其他生物 |
| O1 | 狂戰士（殭屍 1.2 倍、生命 40、攻擊 4、速度 0.25、重劍）、遊擊兵（屍殼、生命 16、攻擊 2、速度 0.32、暴擊 0.25、迴避 0.30、短刀）；都閒晃＋攻擊玩家 |
| O2 | 定義寫錯就啟動失敗，訊息寫出怪物和錯在哪 |
| O3 | 行為清單可以是空的（站著不動的木樁） |
| O4 | 定義沒寫的數值用登記表的預設值 |
| O5 | 白天不會燒 |
| O6 | 和平難度像原版怪物一樣消失 |
| O7 | 不掉身上的裝備、掉 5 點經驗值、不掉其他物品 |
| O8 | 追擊距離 16 格 |
| O9 | 「最大生命值」加進 `ModStats`（玩家和所有生物都看得到） |
| O10 | 英文名稱 Brute、Skirmisher，生怪蛋「Brute Spawn Egg」等 |
| Q8（第三輪） | 怪物的揮擊間隔不跟著攻擊速度，維持原版每秒一下（B） |

### 第三輪：Q8（已決定）

實作中發現、07 沒涵蓋：06 寫「狂戰士拿重劍攻擊速度 −40%」，數值會變成 2.4，但原版怪物的近戰 goal 固定每秒打一下、不讀攻擊速度。問了使用者，決定 **B：不要**。怪物維持原版每秒打一下，攻擊速度只影響玩家；狂戰士身上的攻擊速度數值仍照 R2 的規則算成 2.4（`/mobarmory stats` 看得到，`h1_h2_prototype_mob_stats` 有測），只是不改變揮擊間隔。06 的數字前提已照這個改寫。

使用者原話：「這些是為了這個武器去做出來的特效，目前先不用做到。我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點。」

同時定下的原則：之後再遇到「要不要多做某個效果」，預設不做，在報告記一行；只有會影響數值或 T2 的設計問題才停下來問。照這個原則，R4 **沒有做**的效果：

- 怪物的揮擊間隔跟著攻擊速度（Q8 B）
- 流髑身體的外層衣服（R4-A10）
- 亡靈特性：神聖附魔加傷、治療藥水造成傷害（R4-A3）
- 自然生成、白天燃燒（07 的 Q5、O5 本來就決定不做）

### 實作時採用的假設（沒有另外問，請過目）

都在已確認的邊界內，是實作細節；如果和你想的不一樣，改起來都只動一兩個檔案。

| # | 假設 | 影響 |
|---|---|---|
| R4-A1 | 生怪蛋借原版同身體的生怪蛋外觀（狂戰士＝殭屍蛋、遊擊兵＝屍殼蛋），和 O8「裝備借原版外觀」同一個做法 | 分頁裡狂戰士的蛋和殭屍蛋長得一樣，靠名稱分辨 |
| R4-A2 | 生怪蛋名稱由怪物名稱組成（`item.mobarmory.spawn_egg` = `"%s Spawn Egg"`） | 加怪物時語系檔只要 1 行 |
| R4-A3 | 新怪物是一般怪物（和掠奪者一樣），不算亡靈 | 神聖附魔不加傷害；治療藥水是補血不是傷害 |
| R4-A4 | 追擊距離固定 16 格：拿掉原版每隻怪物生成時隨機 ±11% 的追蹤距離加成 | O8 的 16 格是確定值，測試也不會偶爾失敗 |
| R4-A5 | 經驗值固定 5：原版拿著物品的怪物每件多給 1～3 點，這裡照 O7 不加 | 拿重劍的狂戰士也是 5 點 |
| R4-A6 | `/summon` 帶 NBT（例如 `{NoAI:1b}`）時，和原版怪物一樣不跑生成初始化，**不會自動拿裝備** | 示範時用不帶 NBT 的 `/summon mobarmory:brute`，或用生怪蛋 |
| R4-A7 | 「閒晃」包含隨機走動、看附近的玩家、隨機轉頭（和原版殭屍一樣） | — |
| R4-A8 | 攻擊玩家要「看得到」玩家才會鎖定（和原版殭屍一樣）；被打時反擊不需要看得到 | 隔著牆的玩家不會被發現 |
| R4-A9 | 怪物的大小用原版的 scale 屬性，碰撞箱一起變大 | 1.2 倍的狂戰士高 2.34 格，**鑽不過 2 格高的門洞** |
| R4-A10 | 流髑身體只用流髑貼圖，沒有原版流髑的外層衣服 | 看起來是淺色骷髏（目前沒有怪物用流髑身體） |
| R4-A11 | 生怪蛋和 `/summon` 生出的怪物不是「永久存在」，和原版怪物一樣：附近 128 格沒有玩家會被移除，取名後不會 | 示範時人站在旁邊即可 |
| R4-A12 | 生怪蛋放在既有的 Mob Armory 分頁，排在 4 件裝備後面 | — |

## 3. 結構摘要

```
io.github.alextixu.mobarmory
├─ core/mob/                    純 Java（CoreBoundaryTest 守著）
│  ├─ MobId                     怪物 id，也是生物種類名稱（/summon mobarmory:<id>）
│  ├─ MobDefinition             record：id、look、stats（數值 id → 基礎值）、equipment（欄位 → 裝備 id 字串）、behaviors
│  │                            ＋ Builder；spawnStats(StatCatalog)：每個數值的生成值（定義的值或預設值）
│  ├─ MobLook                   record：Body（ZOMBIE／HUSK／SKELETON／STRAY）＋ scale
│  ├─ MobCatalog                Builder，用 StatCatalog 和 EquipmentCatalog 驗證（B1～B3、B5）
│  └─ Behavior（介面）
│     ├─ WanderBehavior(speed)        record
│     └─ AttackPlayersBehavior(speed) record
├─ content/
│  ├─ ModStats                  ＋ MAX_HEALTH（對應 minecraft:max_health，O9）
│  └─ ModMobs                   BRUTE、SKIRMISHER、ALL；catalog(EquipmentCatalog)
└─ forge/
   ├─ MobArmoryMod              ＋ MOBS（R2b 合併點，見第 7 節）、分頁加生怪蛋、客戶端接線
   ├─ MobEntityFactory          每筆定義 → EntityType、AttributeSupplier（EntityAttributeCreationEvent）、生怪蛋（Factory）
   ├─ ArmoryMob extends Monster 所有自訂怪物都是這個類別；registerGoals 交給 BehaviorGoals；finalizeSpawn 穿上定義的裝備
   ├─ ArmorySpawnEggItem extends SpawnEggItem   名稱由怪物名稱組成
   ├─ GoalBinder<B>（介面）＋ record Goals(goals, targets)
   │  ├─ WanderGoalBinder        WaterAvoidingRandomStrollGoal、LookAtPlayerGoal、RandomLookAroundGoal
   │  └─ AttackPlayersGoalBinder MeleeAttackGoal、HurtByTargetGoal、NearestAttackableTargetGoal<Player>
   ├─ BehaviorGoals             行為種類 → binder 的對照表；verify(MobCatalog)（B4）；重複綁定不加第二份
   └─ client/
      ├─ MobArmoryClient        只在客戶端：每種怪物註冊一個渲染器
      └─ ArmoryMobRenderer extends HumanoidMobRenderer   依 MobLook 選原版身體模型、護甲模型、貼圖

src/gametest/（測試用模組，不進正式 jar）
├─ MobGameTests                 R4 的 16 個系統測試
└─ resources/data/mobarmory/
   ├─ structure/arena.nbt       24×4×24 的空場地（見第 4 節「測試環境」）
   ├─ test_environment/         alone_attack、alone_chase、alone_creative、alone_spawn、alone_wander、noon、difficulty_restored
   └─ test_instance/            ＋16 個
```

`ArmoryMob` 的建構子在呼叫 `super(...)` 之前先設好定義和行為表（Java 25 的 flexible constructor bodies），因為 Minecraft 在 `Mob` 的建構子裡就呼叫 `registerGoals()`。

資源：`lang/en_us.json` ＋3 行（兩隻怪物的名稱、生怪蛋名稱格式）；`items/brute_spawn_egg.json`、`items/skirmisher_spawn_egg.json`。

## 4. 測試報告

### 各層數量

| 層級 | 測試類別 | R4 新增 | 結果 |
|---|---|---|---|
| Component | `MobCatalogTest`：合法定義、B1～B3、B5～B7、定義本身矛盾、行為參數、外觀倍率和 id 格式 | 11 | 全過 |
| Module | `ModMobsTest`：狂戰士、遊擊兵的數值＋裝備（O1、06 的數字前提） | 2 | 全過 |
| 契約 | `MobContractTest`：2 隻怪物 × 5 條 = 10<br>`ContentResourcesTest`：2 隻怪物 × 2 條（名稱、生怪蛋外觀）= 4 | 14 | 全過 |
| System（遊戲內） | `MobGameTests`：真的伺服器、真的生物種類、AI、生怪蛋、`/summon` | 16 | 全過 |
| **合計** | 單元 133（原 106 ＋ 27）、遊戲內 34（原 18 ＋ 16） | **＋43** | **全過** |

指令：`gradlew clean build runGameTestServer`（2026-11-06 22:25，`BUILD SUCCESSFUL`、`All 35 required tests passed`；35 含 Minecraft 自己的 `always_pass`）。R1／R2 原有的 106 ＋ 18 個測試都沒改，照樣全過。

### 契約內容

- **怪物定義契約**（每筆定義都跑）：被登記表接受；只設定已註冊的數值，而且在上下限內；裝備是已註冊的裝備，放在它生效的欄位；生成時每個數值都有值；穿上裝備後沒有數值被上下限默默夾住。
- **行為契約**（遊戲內 `mob_behaviors_all_have_binders`）：每隻怪物的每個行為都有 binder；拿掉 binder 時 `verify` 失敗並寫出怪物名稱（B4，正式啟動時 `MobEntityFactory` 的建構子就會呼叫）；每個 binder 綁定後至少多一個 goal；同一份定義綁兩次不會多出 goal。新增行為時在測試的範例表多一行，漏了會失敗。
- **內容資源契約**：每隻怪物都有名稱，生怪蛋外觀檔指向牠的身體對應的原版生怪蛋。

### 測試環境（遊戲內）

實作時發現兩件事，影響怎麼寫系統測試：

1. **GameTest 會用屏障方塊把每個測試的結構圍起來**（四面牆，加天花板）。原本用的 `minecraft:empty` 只有 1×1×1，1.2 倍的狂戰士頭卡進天花板會窒息，也走不出去、看不到玩家。所以 R4 的測試用自己的空場地 `mobarmory:arena`（24×4×24，只在測試用模組裡）。GameTest 把測試排成格子、每格之間留 5～6 格，所以場地中央離相鄰測試超過 16 格的追擊距離。
2. **原版規則**：怪物附近 32 格內沒有玩家時就不閒晃；128 格內沒有玩家時，非永久的怪物會立刻被移除；測試世界預設是創造模式。所以需要「真的玩家」的測試（攻擊、閒晃、生怪蛋、追擊、無視創造模式玩家）各自放在一個空的測試環境裡（自成一批，不和其他測試同時跑），玩家用真正的生存／創造模式加入世界，測試通過時移除，不會被後面的測試看到。

### Given / When / Then 對照（[06](06-r4-given-when-then.md) 的每一列）

層級：C = Component、M = Module、K = 契約、S = System（遊戲內）。

| # | Given | When | Then | 測試 | 結果 |
|---|---|---|---|---|---|
| H1 | `ModMobs` 多一筆 `brute` | 啟動，生成 | 最大生命 40；有暴擊率、迴避率；有生怪蛋 | C `registerAndLookUp`、M `bruteWithHeavySword`、S `h1_h2_prototype_mob_stats`、S `t2_every_mob_spawns_as_defined`、S `mob_spawns_from_egg_and_summon` | 通過* |
| H2 | 剛生成的狂戰士 | 讀數值 | 攻擊 10、速度 0.175 | M `bruteWithHeavySword`、S `h1_h2_prototype_mob_stats`、S `brute_heavy_sword_slows_it`（拿掉重劍回到 0.25） | 通過 |
| H3 | 16 格內有生存模式玩家 | 經過幾秒 | 鎖定、走過去攻擊；玩家扣血 | S `mob_attacks_survival_player`（3 格外的玩家被鎖定並扣血） | 通過 |
| H4 | 附近沒有可攻擊的玩家 | 經過一段時間 | 隨機走動 | S `mob_wanders_without_players`（旁邊只有創造模式玩家；位置改變、沒有目標） | 通過 |
| H5 | 遊擊兵（迴避 0.30） | 被打 200 下 | 約 30% 被閃掉 | M `skirmisherWithDagger`、S `skirmisher_dodges_and_crits`（被殭屍的攻擊打 200 下，閃掉 25～100 下才算過） | 通過* |
| H6 | 遊擊兵（暴擊 0.5） | 打中目標 | 有一部分是 1.5 倍 | M `skirmisherWithDagger`、S `skirmisher_dodges_and_crits`（打牛 100 下，每下是 4 或 6，6 的有 25～75 下） | 通過* |
| H7 | 原版殭屍和狂戰士 | 查數值 | 兩隻都在同一套數值系統 | S `h7_custom_mobs_have_every_stat` | 通過* |
| B1 | 定義寫了不存在的數值 | 啟動 | 失敗，寫出怪物和數值 | C `unknownStatFails` | 通過 |
| B2 | 迴避率寫 0.9 | 啟動 | 失敗，不默默夾到 0.75 | C `statOutOfRangeFails` | 通過 |
| B3 | 引用不存在的裝備，或重劍放胸甲欄 | 啟動 | 失敗，寫出怪物和裝備 | C `unknownEquipmentFails`、C `equipmentInWrongSlotFails`、K `equipsRegisteredPiecesInTheirOwnSlot` | 通過 |
| B4 | 行為沒有 binder | 啟動 | 失敗，寫出行為 | S `mob_behaviors_all_have_binders` | 通過 |
| B5 | 兩隻怪物同 id | 啟動 | 失敗 | C `duplicateIdFails` | 通過 |
| B6 | 行為清單是空的 | 生成 | 允許（木樁） | C `noBehaviorsIsAllowed` | 通過* |
| B7 | 定義沒寫暴擊率 | 生成 | 用預設值 0 | C `unsetStatsUseCatalogDefaults`、K `spawnsWithEveryStat`、S `h1_h2_prototype_mob_stats`（狂戰士暴擊、迴避都是 0） | 通過 |
| B8 | 玩家是創造模式 | 在狂戰士旁邊 | 不攻擊 | S `mob_ignores_creative_player_and_animals`（5 秒內沒有目標、玩家沒扣血） | 通過 |
| B9 | 牛打了狂戰士 | 之後 | 反擊；但不主動攻擊其他生物 | S `mob_retaliates_against_attacker`（目標變成那頭牛）、S `mob_ignores_creative_player_and_animals`（旁邊的牛沒被打） | 通過 |
| B10 | 白天、沒有遮蔽 | 狂戰士在太陽下 | 不燒 | S `mob_does_not_burn_in_daylight`（正午環境；旁邊的殭屍先燒起來，證明太陽在，狂戰士沒燒） | 通過 |
| B11 | 和平難度 | 生成或已經在世界上 | 消失 | S `mob_removed_on_peaceful`（切成和平後已在世界上的被移除，也生不出新的；測試環境結束時把難度還原） | 通過 |
| B12 | 狂戰士死掉 | 掉落物 | 不掉裝備；經驗 5 | S `mob_drops_no_equipment_and_5_xp` | 通過 |
| B13 | 拿重劍的狂戰士 | 存檔再載入 | 速度仍是 0.175，不是 0.1225 | S `mob_reload_keeps_stats_once`（存成 NBT 再讀回：速度 0.175、基礎 0.25、生命 30/40、還拿著重劍、掉落機率仍是 0） | 通過 |
| B14 | R6 網頁改攻擊力 | 世界上已有狂戰士 | 數值立刻生效；行為下次生成才生效 | — | **沒有覆蓋**（R6 還沒做，沒有「改定義」的入口） |
| B15 | 玩家跑到 16 格外 | 狂戰士正在追 | 放棄追擊 | S `mob_gives_up_beyond_16_blocks`（追擊距離正好 16；先鎖定 4 格外的玩家，玩家移到 24 格外後目標清空） | 通過 |

\* 說明：
- H1：「Mob Armory 分頁有牠的生怪蛋」只有程式碼（分頁的 `displayItems`），沒有自動測試，留給手動走查。生怪蛋本身有測試：每個蛋指向對的種類，用蛋真的生得出來。
- H5、H6：06 寫的是「被玩家打」「打中玩家」。測試用殭屍的攻擊和牛當對象，因為迴避和暴擊對「生物造成的傷害」一視同仁（R1 的 O3），而且這樣傷害數字精確（牛沒有護甲，玩家的傷害會依難度縮放）。
- H7：自訂數值（暴擊、迴避）兩者都有；原版數值方面，原版殭屍本來就沒有「攻擊速度」屬性，`/mobarmory stats` 對它顯示預設值，這是 R1 的既有行為。自訂怪物則每個數值都有自己的屬性。
- B6：目前兩隻怪物都有行為，所以「木樁」只在 Component 層驗證定義會被接受；遊戲內 `t2_every_mob_spawns_as_defined` 檢查「有行為才有 goal」，加一隻木樁時會自動涵蓋。

### 測試機制有在抓錯

開發過程中這些系統測試真的抓到問題（都已修正，記在這裡證明測試不是空轉）：場地太小讓狂戰士窒息、`/summon` 的座標被 Java 印成 `1.37E7` 而解析失敗、模擬玩家只改了回報的遊戲模式卻還是無敵、怪物在生成的第一個 tick 被打時原版的反擊 goal 不會觸發、生怪蛋生出的怪物因為附近沒有玩家被原版規則立刻移除。

## 5. 沒有覆蓋的範圍

測試只證明已定義、已覆蓋的行為。以下沒有被自動測試證明：

- **客戶端畫面**：渲染器（身體、貼圖、1.2 倍大小、殭屍身體攻擊時舉手、手上的武器和身上的護甲）、生怪蛋圖示、Mob Armory 分頁裡的蛋，都沒有自動測試，留給第 6 節的手動走查。
- **B14（R6 改定義）**：要等 R6 有「改怪物定義」的入口。目前的設計支援 Q6 A：行為在 `registerGoals` 時決定（重新載入才換）；數值的「立刻生效」需要 R6 那邊在改定義時重算世界上同種怪物的基礎屬性，這部分還沒寫。
- **Q8**：決定不做（B），所以沒有測「怪物揮擊間隔」；狂戰士的攻擊速度數值 2.4 有測。
- **啟動失敗的畫面**：B1～B5 在單元測試和遊戲內測試證明會丟出寫明原因的例外；沒有實際開一個寫錯定義的遊戲看錯誤畫面。
- **真正的玩家操作**：攻擊、追擊用的是伺服器端的模擬玩家（經由 PlayerList 加入世界、真的生存模式），沒有用客戶端實際被追著打。
- **多人同步（R5）**：怪物的數值、外觀走原版同步，沒有用兩個客戶端測。
- **長時間行為**：閒晃、追擊只看了幾秒到幾十秒；路徑規劃在複雜地形（門、水、樓梯）沒有測。
- **效能**：沒有量測大量怪物時的負擔。

## 6. 手動走查劇本（還沒做）

依指示，`runClient` 的手動走查和截圖之後和示範影片一起做。建議劇本（創造模式、超平坦）：

| # | 操作 | 預期 |
|---|---|---|
| 1 | 開物品欄，Mob Armory 分頁 | 4 件裝備後面有「Brute Spawn Egg」「Skirmisher Spawn Egg」（R4-A1：外觀是殭屍蛋、屍殼蛋） |
| 2 | 用狂戰士蛋生一隻 | 殭屍外觀、比殭屍大一圈（1.2 倍）、右手拿下界合金劍外觀的重劍 |
| 3 | `/mobarmory stats @e[type=mobarmory:brute,limit=1]` | Max Health 40、Attack Damage 10、Speed 0.175（base 0.25）、Attack Speed 2.4、Crit／Dodge 0 |
| 4 | `/gamemode survival`，走近 | 狂戰士舉手追過來打人，玩家扣血（H3） |
| 5 | 跑遠（16 格外） | 牠放棄追擊，回到閒晃（B15） |
| 6 | `/summon mobarmory:skirmisher`（不帶 NBT，R4-A6） | 屍殼外觀、拿短刀，跑得比狂戰士快；`/mobarmory stats` 攻擊 4、暴擊 0.5、迴避 0.3、速度 0.352 |
| 7 | `/time set noon` | 兩隻都不會燒起來（B10） |
| 8 | 殺死狂戰士 | 不掉重劍，掉經驗（B12） |
| 9 | `/difficulty peaceful` | 怪物消失（B11） |

## 7. 合併到 main 時要注意的地方

`mc-dev` 的 R2b 會先合進 main：刪掉 `content/ModEquipment`，裝備改從 JSON 讀。R4 已照約束寫成「用字串 id 引用裝備」，查裝備的地方只有兩處：

| # | 位置 | 現在 | R2b 合併後要改成 |
|---|---|---|---|
| 1 | `forge/MobArmoryMod.java` 的 `MOBS` | `new MobEntityFactory(ModMobs.catalog(ModEquipment.CATALOG), STATS, BehaviorGoals.standard(), EQUIPMENT::item, MODID)` | 把 `ModEquipment.CATALOG` 換成 R2b 從 JSON 載入的 `EquipmentCatalog`；`EQUIPMENT::item`（用 id 查物品）若 R2b 保留 `EquipmentItemFactory.item(EquipmentId)` 就不用改。若 R2b 把裝備的載入移到 mod 建構子裡（不再是 static），`MOBS` 也一起移過去、放在裝備載入之後（`DeferredRegister` 在建構子裡建立仍來得及） |
| 2 | `src/test/.../content/MobTestCatalog.java` | `ModMobs.catalog(ModEquipment.CATALOG)` | 換成 R2b 讀內建預設 JSON 得到的 `EquipmentCatalog`（單元測試的唯一一處） |

其他：

- **裝備 id 要對得上**：`ModMobs` 用 `"heavy_sword"`、`"dagger"`（都在主手），`MobGameTests` 也用這兩個 id。R2b 的預設 JSON 要保留這兩個 id 和欄位；對不上時遊戲會在啟動時失敗並寫出怪物和裝備（B3），不會默默生出空手的怪物。
- **`/mobarmory reload`（R2b）**：怪物定義只在啟動時對裝備驗證一次。若 reload 把重劍的欄位改掉，已生成的狂戰士還拿著劍，但照 R2 的規則（只在自己的欄位生效）就沒有數值效果。是否要在 reload 時重新驗證怪物定義，交給 R2b／R6 決定。
- **會衝突的檔案**：`forge/MobArmoryMod.java`（R4 加 `MOBS`、註冊、分頁放生怪蛋、客戶端接線、log；R2b 會改裝備接線和分頁 icon 的 `ModEquipment.HEAVY_SWORD`）、`lang/en_us.json`（R4 加 3 行；R2b 刪 4 件裝備的名稱）、`test/.../ContentResourcesTest.java`（R4 加怪物的兩條；R2b 會改裝備的部分）、`gametest/.../MobArmoryGameTests.java`（R4 加 1 行）、`content/ModStats.java`（R4 加 `MAX_HEALTH`）。`damage-hud` 也會改 `MobArmoryMod`、`lang/en_us.json`。
- **新數值 `max_health`**：R6 網頁的數值表單會自動多出這一項。如果之後有裝備修改最大生命值，現有生命不會跟著調整，要另外決定。
- **遊戲內測試數**：18 → 34。R4 的測試用自己的場地結構和測試環境（都在 `src/gametest/resources`，不進正式 jar，`ShippedDataTest` 照樣守著）。R2b 把遊戲內測試改到 `run-gametest/` 跑不影響它們。
- **R6 的 `/api/v1/mobs`**：R2b 預留 501。之後的 `MobAdmin` 可以直接讀 `MobArmoryMod.MOBS.catalog()`。
