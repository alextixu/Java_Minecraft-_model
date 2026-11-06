# 行為報告：砍怪傷害顯示（D1，action bar）

> boundary-first 第 6 步。日期：2026-11-06。分支 `feature/damage-hud`（worktree `mod-damage-hud`，從 main 的 `cd97723` 分出），只在本機，沒有 push、沒有合併。
> 結果：**單元測試 160/160**（原本 106 ＋ 新增 54）、**遊戲內系統測試 27/27**（原本 18 ＋ 新增 9；伺服器報告寫「All 28 required tests passed」，多的一個是 Minecraft 自帶的 `minecraft:always_pass`）。
> 指令：`gradlew clean build runGameTestServer`，結果另存在 `run/gametest-report.xml`（28 個 testcase、0 個 failure）。
> 範圍：使用者照審查員的建議縮小，只做 action bar（[13](13-damage-display-open-questions.md) Q4 C）。
> **2026-11-07 合併**：已把 R4 分支頭 `96b9262`（含 main `40ee556` 的 R2b＋R6 最終版）合進來，加了一個遊擊兵的遊戲內測試。合併後是**單元 322/322**、**遊戲內 57/57**（伺服器報告 58，含 `always_pass`）。見[第 8 節](#8-合併到-main2026-11-07)。

## 1. 邊界說明（已確認的版本）

完整內容見 [11-damage-display-boundary-note.md](11-damage-display-boundary-note.md)。

| 項目 | 內容 |
|---|---|
| 會變的東西 | 顯示方式（之後 R3 combo 名稱、R6 網頁戰鬥紀錄）、顯示規則、文字格式 |
| 形狀 | Observer：`CombatEffectListener` 把結算結果交給 `CombatObserver`；`HitFeedbackListener` 組成 `HitReport`，交給每個 `HitDisplay`。規則（`HitFilter`）和格式（`HitText`）在 `core/feedback`，純 Java |
| 不准動的相依 | R1／R2 的 `core/stat`、`core/equipment`、`core/combat` 公開介面和規則（暴擊 1.5 倍、迴避只閃生物造成的傷害、暴擊要蓄滿）；`content/`；`EquipmentStatApplier`、`StatsCommand`；`build.gradle`；Minecraft、Forge（沒有 Mixin、沒有 Access Transformer）；`docs/boundary/README.md` |
| 抽象介面 | 保留：`StatEffect`、`StatReader`（沒改）<br>新建：`HitDisplay.show(HitReport, LivingEntity defender, LivingEntity attacker)`<br>新建：`CombatObserver.attackResolved(Entity, LivingEntity, CombatOutcome)`、`CombatObserver.hitCancelled(LivingEntity, DamageSource)` |
| 邊界由測試守住 | `CoreBoundaryTest` 自動涵蓋新的 `core/feedback`（沒有 import Minecraft）；`HitTextContractTest` 守住「每個 action bar 字串都在 `en_us.json` 裡」 |

### 實際改到的檔案

| 類型 | 檔案 | 改動 |
|---|---|---|
| 新增（核心，純 Java） | `core/feedback/`：`HitReport`、`HitResult`、`Party`、`Viewer`、`HitFilter`、`HitText`、`HitLine`、`Tone` | 8 個檔案 |
| 新增（Forge） | `forge/CombatObserver`；`forge/feedback/`：`HitFeedbackListener`、`HitDisplay`、`ActionBarDisplay` | 4 個檔案 |
| 修改 | `forge/CombatEffectListener` | ＋17／−2：多一個建構子參數（舊建構子傳 `CombatObserver.NONE`）、三處通知、`kindOf` 改成 public。**規則沒有改**，既有 106 個單元測試和 18 個遊戲內測試在接上通知後先跑過一次，全過 |
| 修改 | `forge/MobArmoryMod` | ＋7／−1：建立 `HIT_FEEDBACK`、把它交給 `CombatEffectListener`、註冊 |
| 修改 | `lang/en_us.json` | ＋6 行 action bar 字串 |
| 測試 | `src/test/.../core/feedback/`：`HitTextTest`、`HitFilterTest`、`HitTextContractTest`；`src/gametest/.../FeedbackGameTests`、`MobArmoryGameTests` ＋1 行、`test_instance/hud_*.json` 9 個 | — |

`content/` 完全沒動，系統測試也沒有引用 `ModEquipment`（R2b 會刪掉它）。正式 jar 54 個類別（原本 38 ＋ 新增 16，含 record 和 switch 產生的內部類別），沒有任何測試檔。

### 改一處會牽動幾個檔案

| 要做的事 | 要動的檔案 | 數量 |
|---|---|---|
| 新增一種顯示方式（例如 R6 網頁的戰鬥紀錄） | 新的 `HitDisplay` 類別、`MobArmoryMod` 加一行 | 2 |
| 改數字格式（例如改成整數） | `core/feedback/HitText` | 1 |
| 改文字（例如 MISS 改成 DODGE） | `lang/en_us.json`（若要加新的參數，再加 `HitText`） | 1～2 |
| 改顯示規則（例如也顯示環境傷害） | `core/feedback/HitFilter` | 1 |
| R1 新增一個戰鬥效果 | 不用改（只要它的結果是改傷害、取消或暴擊） | 0 |

## 2. 商業決策（已確認）

使用者 11/06 的決定，紀錄在 [13](13-damage-display-open-questions.md)，prompt 在 [prompts/damage-display-round-02-decisions.md](prompts/damage-display-round-02-decisions.md)。

| # | 決定 |
|---|---|
| Q1 | 同意 SOLID 判斷 |
| Q2 | Observer；`CombatEffectListener` 只加通知掛勾，不改行為 |
| Q3 | 劃掉浮字（`FloatingTextDisplay`）和 `HitJournal`，其他同意 |
| Q4 | 只做 action bar |
| Q5 | 只給攻擊的玩家和被打的玩家 |
| Q6 | 目標名稱、最終傷害、CRIT 或 MISS、剩餘血量／最大血量 |
| Q7 | 生物造成的近戰和投射物；環境傷害不顯示 |
| Q8 | 伺服器判定，原版系統訊息封包，沒有自訂封包和客戶端程式 |
| O1 | 小數 1 位、四捨五入，有扣血最少 0.1 |
| O2 | 生命值點數 |
| O3 | 溢出時顯示整下傷害，剩餘血量 0 |
| O4 | 無敵時間內照實顯示差額，沒扣血不顯示 |
| O5 | 創造模式、盾牌全擋不顯示，連 MISS 也不顯示 |
| O6 | 吸收心擋掉的不算 |
| O7 | 不適用（沒有浮字） |
| O8 | 只做英文 `en_us`；目標和攻擊者名稱由客戶端依語言顯示 |
| O9 | 一律開啟，不做開關 |
| O10 | 跳劈和暴擊率暴擊顯示一樣 |

實際的 action bar（`en_us.json`）：

| 情況 | 攻擊的玩家看到 | 被打的玩家看到 |
|---|---|---|
| 命中 | `Cow -7.0 ❤ 3.0/10`（白） | `Zombie hit you -2.2 ❤ 17.8/20`（紅） |
| 暴擊 | `Cow -4.5 CRIT ❤ 5.5/10`（金） | `Zombie hit you -3.3 CRIT ❤ 16.7/20`（紅） |
| 迴避 | `Cow MISS ❤ 10.0/10`（灰） | `Dodged Zombie ❤ 20.0/20`（灰） |

### 實作時採用的假設（沒有另外問，請過目）

這些都在已確認的邊界內，是實作細節。如果和你想的不一樣，改起來都只動一兩個檔案。

| # | 假設 | 影響 |
|---|---|---|
| A1 | **無敵時間前半（受傷後 0.5 秒內）的迴避不顯示 MISS**。Minecraft 記「上一下多大」的欄位（`lastHurt`）不是公開的，看不出被迴避的這一下會不會比上一下大，所以一律當作「本來就不會受傷」 | 無敵時間內一下比較強的攻擊剛好被迴避時，畫面上沒有 MISS |
| A2 | 顏色：攻擊者一般白、暴擊金、MISS 灰；被打的玩家紅（被暴擊也是紅，字裡有 CRIT）、迴避灰 | 只改 `ActionBarDisplay.color` 一處 |
| A3 | 負號用一般的 `-`，不用數學負號 `−`（U+2212）。後者要靠 Minecraft 的備用字型，字形會和其他字不一致。愛心用 `❤` | 審查時看到的例子是 `−6.9`，畫面上會是 `-6.9` |
| A4 | 自己打到自己（例如往上射箭掉下來）時，只送「被打」那一行 | — |
| A5 | 某個顯示方式出錯（丟例外）時只寫 log，不影響戰鬥 | 示範時不會因為顯示的 bug 讓攻擊失效 |
| A6 | 剩餘血量活著時最少顯示 0.1（不會顯示 0.0），死了顯示 `0`；最大血量是整數時不寫小數（`20`） | — |
| A7 | NaN 傷害不顯示；無限大顯示 `∞`；數字永遠不會出現 NaN 或負號（契約測試守住） | — |
| A8 | 系統測試用攻擊力 1、3、7 代表空手、短刀、重劍，不拿真的物品 | 物品給的攻擊力由 R2 的測試證明；D1 不依賴 R2b 要刪的 `ModEquipment` |
| A9 | `CombatEffectListener.kindOf` 改成 public，讓 `forge/feedback` 用同一套「近戰／投射物／環境」判斷 | 判斷規則沒改 |
| A10 | `LivingDamageEvent` 用最低優先序的一般監聽，不用監看式監聽（原因見第 6 節） | 和原本的設計效果一樣 |

## 3. 結構摘要

```
io.github.alextixu.mobarmory
├─ core/feedback/                純 Java，不 import Minecraft（CoreBoundaryTest 守著）
│  ├─ HitReport (record)         傷害種類、攻擊者、被打者、結果、最終傷害、剩餘血量、最大血量
│  ├─ HitResult                  HIT / CRITICAL / DODGED
│  ├─ Party                      PLAYER / MOB / NONE
│  ├─ Viewer                     ATTACKER / DEFENDER
│  ├─ HitFilter                  shows(report)：要不要顯示；viewers(report)：給誰
│  ├─ HitText                    line(report, viewer) → HitLine（語系字串、顏色、三個數字）
│  ├─ HitLine (record)、Tone
├─ forge/
│  ├─ CombatObserver             介面：attackResolved、hitCancelled（預設什麼都不做；NONE）
│  ├─ CombatEffectListener       （R1）結算後通知 CombatObserver；規則不變
│  ├─ feedback/
│  │  ├─ HitFeedbackListener implements CombatObserver
│  │  │     LivingDamageEvent（LOWEST）→ HitReport → 每個 HitDisplay
│  │  │     記每個目標最近一次的攻擊結算，用來判斷這一下是不是暴擊
│  │  ├─ HitDisplay              介面（Observer）
│  │  └─ ActionBarDisplay implements HitDisplay   ServerPlayer.sendOverlayMessage；送出方式可注入
│  └─ MobArmoryMod               HIT_FEEDBACK = new HitFeedbackListener(List.of(new ActionBarDisplay()))
```

一下攻擊怎麼走：

```
CombatEffectListener ──attackResolved(target, attacker, outcome)──▶ HitFeedbackListener 記下「這個目標最近一次結算：暴擊？」
                     ──hitCancelled(defender, source)────────────▶ 清掉記錄；本來會受傷 → HitReport(DODGED)
LivingDamageEvent（最終值）────────────────────────────────────────▶ 取用一次記錄 → HitReport(HIT / CRITICAL)
HitReport ─▶ HitFilter.shows ─▶ 每個 HitDisplay ─▶ ActionBarDisplay ─▶ HitFilter.viewers ─▶ HitText.line ─▶ 玩家的 action bar
```

## 4. 測試報告

### 各層數量

| 層級 | 測試類別 | 數量 | 結果 |
|---|---|---|---|
| Component | `HitTextTest` 29（四捨五入 10、float 1、剩餘血量 5、最大血量 5、H2／H4／H5／H6／H7＋H8／B11／B12／B17 各 1）、`HitFilterTest` 13（H1、H7、H8、H9、B1＋B7、B5、B6、B15、B17、B18，加上三種傷害種類 × 所有攻擊者、被打者、結果的組合） | 42 | 全過 |
| 契約 | `HitTextContractTest`：2 種觀看者 × 3 種結果，每個都跑 0、極小、0.04、溢出、無限大、負無限大、NaN、負數等 12 種數值的所有組合（不丟例外、不印 NaN、數字沒有負號、有扣血不會是 0.0）6 個；每個語系字串都在 `en_us.json` 裡 6 個 | 12 | 全過 |
| System（遊戲內） | `FeedbackGameTests` 9 個，真的揮劍、真的扣護甲、真的 Forge 事件 | 9 | 全過 |
| 既有 | R1／R2 的單元 106、遊戲內 18 | 124 | 全過（沒有改任何既有測試） |
| **合計** | 單元 160 ＋ 遊戲內 27 | **187** | **全過** |

**測試真的抓得到錯**：把兩條最細的規則故意改壞再跑一次——(1) 只在暴擊時才記錄攻擊結算（讓舊的暴擊標記留下來）、(2) 迴避一律當作「本來會受傷」——`hud_cooldown_and_stale_crit`（「B9: the next hit is not marked CRIT」）和 `hud_creative_player_never_sees_miss`（「got 43 reports」）各自失敗，Gradle 也跟著失敗。改回來後全過。

### 遊戲內系統測試在測什麼

| 測試 | 對應 | 內容 |
|---|---|---|
| `hud_weapons_show_different_damage` | H1、H2、H3、H10 | 攻擊力 1、3、7、5 各打一隻新的牛：結果 = 牛實際少掉的血 = 1.0、3.0、7.0、5.0；攻擊者收到一行 `mobarmory.hit.dealt`，數字 `1.0 / 9.0 / 10`、`3.0 / 7.0 / 10`、`7.0 / 3.0 / 10`、`5.0 / 5.0 / 10`，白色，名稱是牛 |
| `hud_crit_and_miss` | H4、H5、B8、O10 | 攻擊力 3、暴擊率 100%：金色 `dealt.crit`、`4.5 / 5.5 / 10`；暴擊率 0 的跳劈也是 CRIT、4.5；牛迴避在上限時，被迴避的那一下是灰色 `dealt.miss`、牛沒扣血、不是 CRIT，沒被迴避的都是 CRIT |
| `hud_damage_is_after_armor` | H6 | 攻擊力 7 打護甲 10 的殭屍：結果 = 殭屍少掉的血 ≈ 5.18（7 × 0.74），顯示 `5.2 / 14.8 / 20` |
| `hud_mob_hits_player` | H7、H8、B7 | 殭屍打玩家：只有玩家收到紅色 `taken`，名稱是殭屍，數字 = 玩家少掉的血；吸收心全吸收時什麼都不顯示；玩家迴避在上限時收到灰色 `taken.miss` |
| `hud_creative_player_never_sees_miss` | B3 | 創造模式（`abilities.invulnerable`）的玩家被打 60 下、迴避 75%：沒受傷，也沒有任何結果或訊息 |
| `hud_player_hits_player` | B14、B15 | 玩家 A 打玩家 B：A 收到 `dealt`、B 收到 `taken`，數字都是 `1.0 / 19.0 / 20`；旁邊的玩家 C 收不到 |
| `hud_environment_and_mob_fights_reach_nobody` | B5、B6 | 摔落、一般傷害不顯示；殭屍打牛有結果，但沒有任何玩家收到訊息 |
| `hud_cooldown_and_stale_crit` | B1、B2、B9 | 攻擊力 1 再 7（無敵時間內）：顯示差額 6.0；再用 1 打：沒扣血、不顯示；暴擊被無敵時間吃掉：不顯示，下一下（暴擊率 0）是 HIT 不是 CRIT |
| `hud_reads_damage_after_other_mods` | B16 | 測試裡另外掛一個一般監聽把牛受到的傷害減半：顯示 3.5，等於牛實際少掉的血 |

測試用的兩個記錄器接在遊戲用的同一條管線上：一個收 `HitReport`，另一個是 `ActionBarDisplay`，只是把「送出」換成「記下來」（假玩家沒有畫面）。正式遊戲裡接的是真的 `ActionBarDisplay`，這些測試裡它也照樣在跑。

### Given / When / Then 對照（[12](12-damage-display-given-when-then.md) 的每一列）

層級：C = Component、K = 契約、S = System（遊戲內）。

| # | Given | When | Then | 測試 | 結果 |
|---|---|---|---|---|---|
| H1 | 空手、蓄滿 | 打牛 | `Cow -1.0 ❤ 9.0/10` | C `h1PlayerHitsMob`、S `hud_weapons_show_different_damage` | 通過 |
| H2 | 攻擊力 7 | 打牛 | `Cow -7.0 ❤ 3.0/10` | C `h2PlainHit`、S `hud_weapons_show_different_damage` | 通過 |
| H3 | 攻擊力 3 | 打牛 | `Cow -3.0 ❤ 7.0/10` | S `hud_weapons_show_different_damage` | 通過 |
| H4 | 攻擊力 3、暴擊 100% | 打牛 | 金色 `Cow -4.5 CRIT ❤ 5.5/10` | C `h4Crit`、S `hud_crit_and_miss` | 通過 |
| H5 | 牛迴避成功 | 打牛 | 灰色 `Cow MISS`；沒扣血 | C `h5Miss`、S `hud_crit_and_miss` | 通過 |
| H6 | 殭屍護甲 10 | 攻擊力 7 | `Zombie -5.2 ❤ 14.8/20` | C `h6AfterArmor`、S `hud_damage_is_after_armor` | 通過 |
| H7 | 玩家迴避成功 | 殭屍攻擊 | 玩家收到灰色 `Dodged Zombie` | C `h7DodgeShows`、C `defenderSide`、S `hud_mob_hits_player` | 通過 |
| H8 | 殭屍打中玩家 | 殭屍攻擊 | 玩家收到紅色 `Zombie hit you -x.x` | C `h8MobHitsPlayer`、C `defenderSide`、S `hud_mob_hits_player` | 通過 |
| H9 | 玩家射箭 | 命中殭屍 | 照樣顯示 | C `h9Projectile` | 通過* |
| H10 | 攻擊力改成 5 | 打牛 | `Cow -5.0 ❤ 5.0/10` | S `hud_weapons_show_different_damage` | 通過 |
| B1 | 無敵時間內 | 較弱的一下 | 不顯示 | C `b1b7NothingTaken`、S `hud_cooldown_and_stale_crit` | 通過 |
| B2 | 無敵時間內 | 較強的一下 | 顯示差額 6.0 | S `hud_cooldown_and_stale_crit` | 通過 |
| B3 | 創造模式、迴避成功 | 被打 | 不顯示 MISS | S `hud_creative_player_never_sees_miss` | 通過 |
| B4 | 舉盾 | 完全擋下 | 不顯示 | —（原版在扣血之前就結束，不會發出 `LivingDamageEvent`，見第 5 節） | 未測 |
| B5 | 環境傷害 | 扣血 | 不顯示 | C `b5Environment`、C `everyCombination`、S `hud_environment_and_mob_fights_reach_nobody` | 通過 |
| B6 | 殭屍打牛 | 打中 | 誰都收不到 | C `b6NoPlayerInvolved`、S `hud_environment_and_mob_fights_reach_nobody` | 通過 |
| B7 | 吸收心全吸收 | 被打 | 不顯示 | C `b1b7NothingTaken`、S `hud_mob_hits_player` | 通過 |
| B8 | 暴擊後被迴避 | 打牛 | MISS，不是 CRIT | S `hud_crit_and_miss` | 通過 |
| B9 | 暴擊被無敵時間吃掉 | 下一下 | 不被誤標 CRIT | S `hud_cooldown_and_stale_crit`（並用故意改壞的版本確認會失敗） | 通過 |
| B10 | 橫掃打中三隻 | 揮劍 | 只留最後一則（取捨） | — | 未測（取捨） |
| B11 | 牛剩 3 血 | 攻擊力 7 打死 | `Cow -7.0 ❤ 0/10` | C `b11Overkill`、C `healthAfter` | 通過 |
| B12 | 最終傷害約 0.04 | 打中 | 顯示 0.1 | C `b12TinyDamage`、C `damageRounding`、K `anyLineIsReadable` | 通過 |
| B13 | 三支箭連續命中 | 命中 | 後一則蓋掉前一則（取捨） | — | 未測（取捨） |
| B14 | A、B 在旁邊 | A 打殭屍 | 只有 A 收到 | C `h1PlayerHitsMob`、S `hud_player_hits_player`（旁觀的 C 收不到） | 通過 |
| B15 | A 打 B | 打中 | A、B 各收到自己那一行，數字一樣 | C `b15PlayerHitsPlayer`、S `hud_player_hits_player` | 通過 |
| B16 | 其他模組減半 | 打中 | 顯示減半後的值 | S `hud_reads_damage_after_other_mods` | 通過 |
| B17 | NaN、無限大 | 打中 | 不丟例外；NaN 不顯示、∞ | C `b17Extremes`（兩個類別各一）、K `anyLineIsReadable` | 通過 |
| B18 | 射手已登出 | 箭命中 | 不顯示 | C `b18NoAttacker` | 通過* |

\* H9、B18 只有單元測試。射箭的「近戰／投射物」判斷沿用 R1 的 `kindOf`，R1 已有怪物投射物暴擊的遊戲內測試；這次沒有在遊戲內真的射一支箭。

## 5. 沒有覆蓋的範圍

測試只證明已定義、已覆蓋的行為。以下沒有被自動測試證明：

- **客戶端畫面**：action bar 實際出現的位置、顏色、`❤` 字形、停留時間都沒有在 `runClient` 確認（這輪沒有開客戶端）。自動測試證明的是伺服器送出了哪個語系字串、哪些數字、哪個顏色、送給誰。
- **真正的多人**：沒有開兩個客戶端；假玩家只證明伺服器把訊息送給了誰。
- **B4 盾牌**：沒有測。原版在 `hurtServer` 裡格擋掉以後，傳進 `actuallyHurt` 的傷害是 0，`LivingHurtEvent` 之後就結束，不會有 `LivingDamageEvent`，所以照程式結構不會顯示，但沒有實際舉盾驗證。
- **B10、B13**：action bar 一次一行，下一則會蓋掉上一則。這是 Q4 C 的取捨，沒有寫測試。
- **和平難度**：怪物對玩家的傷害在和平難度會變成 0，但我們的迴避判定在那之前。和平難度下玩家迴避成功時仍可能看到 MISS（A1 的判斷沒有涵蓋難度）。和平難度怪物會消失，實際很少遇到。
- **抗火**：`couldBeHurt` 有判斷「火焰傷害＋抗火」不顯示 MISS，但沒有測。
- **配對的 tick 檢查**：暴擊標記「同一個 tick、同一個攻擊者」的檢查沒有單獨測到；B8、B9 測到的是「迴避會清掉」「每一次結算都會蓋掉上一次」這兩條路。
- **效能**：每一下攻擊多建一個小 record、查一次 `WeakHashMap`，沒有量測。

## 6. 實作時發現的問題

**EventBus 7.0.6 的監看式監聽在沒有一般監聽的事件上不會被呼叫。** 第一次跑完整測試時，9 個新測試有 7 個失敗，都是「收不到結果」。用暫時的 log 查到：整場只有一下傷害進到我們的監聽，就是 B16 那個測試自己另外掛了一般監聽的時候。反組譯 `CancellableEventBusImpl.invalidateInvoker()` 確認：它只看一般監聽的清單（`backingList`）是不是空的，空的就把整個事件設成什麼都不做，不管監看式監聽的清單（`monitorBackingList`）。Mob Armory 原本沒有任何東西監聽 `LivingDamageEvent`，所以碰到了。

處理：改用 `Priority.LOWEST` 的一般監聽。排在其他監聽之後讀數值（B16 驗證），被取消的事件不會傳過來，效果和原本的設計一樣。暫時的 log 已移除，原因寫在 `HitFeedbackListener.register()` 的註解，11 號文件第 6 節 T5 也補上了。

## 7. 合併注意（給監控 session）

> 11/07 已經合併，實際情況見第 8 節。下面是合併前寫的預估。

- **R2b 先合進 main**：可能和這個分支改到相鄰行的地方只有 `MobArmoryMod`（`HIT_FEEDBACK` 欄位、`CombatEffectListener` 建構子那一行、`HIT_FEEDBACK.register()`）、`CombatEffectListener`（三處通知和建構子）、`lang/en_us.json`（最後 6 行）、`MobArmoryGameTests`（註冊一行）。都是加行，衝突容易解。`content/` 沒動，D1 的測試也不引用 `ModEquipment`。合併後要再跑一次 `gradlew clean build runGameTestServer`。
- **給 R3 combo**：combo 名稱要顯示在同一行時，`HitReport` 加一個欄位、`HitText` 加一個語系字串變體、`en_us.json` 加字串，`ActionBarDisplay` 和 `HitFeedbackListener` 不用改。
- `docs/boundary/README.md` 沒有改（由開發 session 維護），需要時請在索引加上 11～13、16。

## 8. 合併到 main（2026-11-07）

使用者 11/07 決定「合併」。D1 排在 R4 之後合（review-02 6-4）。每一步的 prompt 和 SOLID／Pattern 檢查見 [prompts/damage-display-round-03-merge.md](prompts/damage-display-round-03-merge.md)。

- **做法**：在 `feature/damage-hud` 執行 `git merge --no-ff 96b9262`。`96b9262` 是 `feature/r4-mobs` 的分支頭，已經包含 main `40ee556`（R2b＋R6 最終版）和 R4，架構師之後會把 main 快轉到這個分支。沒有 rebase，沒有動 main，沒有 push。`feature/r3-combo` 停在 `74bd43b`，沒有動。
- **測試**：`gradlew clean build runGameTestServer` 全過。

| | 合併前：D1（`ed05c6b`） | 合併前：R4 分支頭（`96b9262`） | 合併後 |
|---|---|---|---|
| 單元 | 160 | 268 | **322**（268 ＋ D1 新增的 54） |
| 遊戲內 | 27 | 47 | **57**（47 ＋ D1 的 9 ＋ 這次新增的 1） |
| 伺服器報告 | 28 | 48 | **58**（多的一個都是原版 `always_pass`） |

R4 說的「遊戲內 48」是伺服器報告的數字。遊戲內測試的報告改放在 `run-gametest/gametest-report.xml`：R2b 讓遊戲內測試用自己的資料夾，每次執行前清空。這次是 58 個 testcase、0 個 failure。jar 裡沒有任何遊戲內測試的類別或 `test_instance`。

### 衝突怎麼解（照 review-02 6-1：兩邊各加幾行，全部保留）

| 檔案 | 衝突區塊 | 怎麼解 |
|---|---|---|
| `forge/MobArmoryMod.java` | 3（import、欄位、接線） | **import**：兩邊都留，照字母順序排<br>**欄位**：裝備用 R4 分支（＝main）的寫法：`WEAPON_FILES`、`EQUIPMENT_LIBRARY`、`WEAPON_SYNC`、`WEAPON_ADMIN`、`MOB_EQUIPMENT`、`MOBS`。D1 那邊的 `EQUIPMENT`／`EQUIPMENT_STATS` 還是用 `ModEquipment.CATALOG`，R2b 已經刪掉 `ModEquipment`，所以不能留。後面加上 D1 的 `HIT_FEEDBACK`<br>**接線**：R4 分支的 reload 指令、同步、網頁、客戶端註冊全部保留；`CombatEffectListener` 那一行換成 D1 帶 `HIT_FEEDBACK` 的建構子，後面加 `HIT_FEEDBACK.register()` |
| `lang/en_us.json` | 1 | 審查的試算是「自動合併」，但 R2b 的 reload 和網頁字串也加在最後，所以實際上有衝突。兩邊都留：R2b 的 8 行在前、D1 的 6 行在後，R2b 原本的最後一行補上逗號。用 JSON 解析器確認合法（23 個字串）；`HitTextContractTest` 也確認 D1 的 6 個字串都在 |
| `gametest/MobArmoryGameTests.java` | 1 | 兩邊都留，`FeedbackGameTests.registerAll()` 放最後 |

自動合併、要確認的地方：

- `CombatEffectListener`：main 和 R4 都沒動過，合併後就是 D1 的版本（三處通知、新的建構子）。
- D1 的正式程式：`core/feedback`、`forge/feedback`、`CombatEffectListener` 合併後和 `ed05c6b` 一模一樣。
- R2b、R4 的類別：D1 一行都沒改。

### 6-3 e：遊擊兵和 action bar（新增遊戲內測試）

`hud_skirmisher_crit_and_dodge`（`FeedbackGameTests`，在 `mobarmory:arena` 裡跑）：

- **用真的遊擊兵**：經由 `MobArmoryMod.MOBS` 生成，和生怪蛋走同一條路，會拿到短刀。用的是它自己的數值，不覆寫：暴擊 0.25 ＋ 短刀 0.25 = 0.5、迴避 0.30、攻擊 2 ＋ 短刀 2 = 4。測試先確認這三個數字。
- **D1 對 R4 的怪物沒有任何特例**，正式程式沒改就通過。
- 三個情境：

| 情境 | 預期 | 結果 |
|---|---|---|
| 遊擊兵打玩家（直到暴擊和一般各出現一次，最多 60 下） | 只有玩家收到一行，紅色，名稱是 `Skirmisher`。暴擊那一下是 `mobarmory.hit.taken.crit`（`Skirmisher hit you -x CRIT ❤ …`），一般是 `mobarmory.hit.taken`。數字都等於玩家實際少掉的血，**暴擊 = 一般 × 1.5**（用比例比，所以和難度無關） | 通過 |
| 玩家打遊擊兵（直到被迴避，最多 60 下） | 被迴避的那一下是灰色 `mobarmory.hit.dealt.miss`（`Skirmisher MISS ❤ …`），遊擊兵沒扣血；打中的是一般的 `dealt` | 通過 |
| 玩家迴避調到上限 0.75，遊擊兵打玩家（直到迴避，最多 60 下） | 灰色 `mobarmory.hit.taken.miss`（`Dodged Skirmisher ❤ …`），玩家沒扣血 | 通過 |

隨機造成誤判的機率：

- 60 下裡沒有暴擊或沒有一般：約 2 × 0.5⁶⁰。
- 60 下都沒被迴避：0.7⁶⁰ ≈ 5 × 10⁻¹⁰。
- 60 下都沒迴避成功：0.25⁶⁰。

### 手動走查（錄影用，`runClient`）

這一段沒有實際在客戶端跑過，是照自動測試寫的預期畫面。

1. `gradlew runClient`，開新世界（生存、普通難度）。
2. 打開創造模式物品欄的 Mob Armory 分頁，拿遊擊兵的生怪蛋放出一隻，再 `/gamemode survival`。
3. 站著讓它打：畫面下方出現紅色的 `Skirmisher hit you -x ❤ …/20`，大約一半會是 `… CRIT …`，數字是一般的 1.5 倍。
4. 空手打它：大約三成出現灰色的 `Skirmisher MISS ❤ 16.0/16`，它的血不會少；打中時出現白色的 `Skirmisher -1.0 ❤ 15.0/16`。
5. `/attribute @s mobarmory:dodge_chance base set 0.75`，再讓它打：大部分是灰色的 `Dodged Skirmisher ❤ …/20`。

## 9. SOLID／Pattern 檢查

這一節是 2026-11-07 由架構師補的，依長期規定第 3 點：每份行為報告都要有這一節（review-03 P3-9）。上面各節是連招小弟寫的，沒有改動。內容逐條對照當天 main 上的 D1 程式。合併時每一步的檢查在 [prompts/damage-display-round-03-merge.md](prompts/damage-display-round-03-merge.md)。

### SOLID

| 原則 | 對照的程式 | 判斷 |
|---|---|---|
| S 單一職責 | `HitFilter`：哪些傷害要顯示、給誰看。`HitText`：顯示的文字。`HitReport`：一次命中的結果，只是資料。`HitFeedbackListener`：從遊戲事件組出 `HitReport`，並記住暴擊的判定。`ActionBarDisplay`：顯示在 action bar。規則都在純 Java 的 `core/feedback` | 符合 |
| O 開放封閉 | 加一種顯示方式（例如之後的 combo 名稱、網頁的戰鬥紀錄）：實作 `HitDisplay`，在 `MobArmoryMod` 加一行，既有的顯示和規則都不用改 | 符合 |
| O（注意） | `HitText` 用 `switch` 判斷 `HitResult`（命中、暴擊、迴避），`ActionBarDisplay` 用 `switch` 判斷 `Tone`。兩個都是封閉集合，加一種結果要改這兩處 | 刻意的取捨 |
| L 替換 | 用 `CombatObserver.NONE` 時，`CombatEffectListener` 的行為和 D1 之前完全一樣，舊的建構子就是這樣。`HitDisplay` 有正式的 `ActionBarDisplay`，還有遊戲內測試用 `addDisplay` 加上的觀察用 display，兩者收到同樣的 `HitReport`。`HitText` 有契約測試（`HitTextContractTest`） | 符合 |
| I 介面隔離 | `CombatObserver` 有三個 default 方法（R3 合進 main 後多了 `comboTriggered`），實作的一方只要覆寫需要的那個；`HitDisplay` 只有 `show` | 符合 |
| D 依賴反轉 | `CombatEffectListener` 依賴 `CombatObserver` 介面，不知道 action bar 存在。`core/feedback` 只 import `core/combat` 和 JDK（`CoreBoundaryTest`）。顯示出錯時，`HitFeedbackListener` 會接住例外，不會影響戰鬥 | 符合 |

### Pattern

| Pattern | 在哪裡 | 為什麼 |
|---|---|---|
| Observer | `CombatObserver`（戰鬥 → 回饋）、`HitDisplay`（回饋 → 各種顯示） | 只聽不改，所以加了 D1 之後戰鬥規則照舊 |
| 規則和顯示分開 | `HitFilter`、`HitText` 在 `core/feedback`；`ActionBarDisplay` 在 `forge/feedback` | 規則可以不開遊戲就測（單元 +54）；換顯示方式時規則不動 |

### 結論

沒有發現違反。唯一的「注意」是用 `switch` 判斷結果和色調，屬於刻意的取捨。
