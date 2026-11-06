# Mob Armory：自訂怪物與武器系統（Forge 模組）

封豪學長的**題目二**。

## 主軸：讓玩家自己新增東西

這個模組的重點不是我們做出很厲害的怪物和武器，而是**一套讓玩家以後可以自己新增東西的系統**。武器、怪物、combo、數值都朝「寫一個 JSON 檔（或用網頁），重開遊戲就有」的方向做：

| 要新增的東西 | 玩家怎麼做 | 狀態 |
|---|---|---|
| 武器、護甲 | 在 `config/mobarmory/weapons/` 放一個 JSON 檔，或在網頁後台用「新增武器」表單；外觀可以借用原版物品、用 7 張內建貼圖，或上傳自己的 PNG。見[加一把武器](#加一把武器不用寫-java) | **已完成** |
| 數值（例如「跳躍力」「幸運」） | 在 `config/mobarmory/stats/` 放一個 JSON 檔，重開遊戲，所有生物和武器都能用。可以是純數值，也可以對應 Minecraft 的屬性（例如跳躍力）。有戰鬥規則的數值（像暴擊、迴避）還是要寫 Java。見[加一個數值](#加一個數值不用寫-java) | **已完成** |
| 怪物 | 目前要改 Java（見[加一隻怪物](#加一隻怪物3-個檔案)）；之後改成 JSON 加網頁 | **進行中** |
| combo | 之後改成 JSON | **進行中** |

## 現在有什麼

- 一套可以擴充的數值系統：攻擊力、移動速度、護甲、攻擊速度、最大生命、暴擊率、迴避率。
- 用 JSON 設定的武器和護甲，可以換外觀，也可以上傳 PNG 貼圖。
- 自訂怪物：狂戰士（brute）、遊擊兵（skirmisher）。
- 網頁後台：在網頁上改武器的數值和外觀、新增武器、查看怪物。
- 打中生物時，在 action bar 顯示傷害、暴擊（CRIT）和迴避（MISS）。

| 項目 | 版本 |
|---|---|
| Minecraft | Java 版 26.3 |
| Forge | 26.3-66.1.0 |
| JDK（自己 build 才需要） | 25 |
| 模組 id | `mobarmory` |

程式全部由 AI 產生，開發流程是 boundary-first：人先定義邊界，AI 在邊界內實作，再由測試證明行為正確。每個功能的邊界文件、Given/When/Then、prompt 和行為報告都放在 [`docs/boundary/`](docs/boundary/)，索引見文末。

## 安裝

1. 安裝 Minecraft 26.3 和 Forge 26.3-66.1.0。
2. 把 `mobarmory-1.0.0.jar` 放進遊戲資料夾的 `mods/`。
3. 啟動遊戲。第一次啟動時，模組會建立這些檔案：
   - `config/mobarmory/weapons/`：4 件預設武器，每把一個 JSON 檔。
   - `config/mobarmory/stats/`：7 個內建數值，每個一個 JSON 檔。
   - `config/mobarmory/web.json`：網頁後台的設定。

要自己 build jar 的話：

```bash
./gradlew build
```

產出的 jar 在 `build/libs/mobarmory-1.0.0.jar`。

## 玩法

### 拿武器和護甲

建立模式的物品欄有一個 **Mob Armory** 分頁，裡面有所有武器、護甲和生怪蛋。也可以用指令拿：

| 指令 | 物品 | 效果 |
|---|---|---|
| `/give @p mobarmory:heavy_sword` | 重劍（主手） | 攻擊力 +6、移動速度 −30%、攻擊速度 −40% |
| `/give @p mobarmory:dagger` | 短刀（主手） | 攻擊力 +2、暴擊率 +25%、移動速度 +10% |
| `/give @p mobarmory:evasion_chestplate` | 迴避胸甲 | 迴避率提高 |
| `/give @p mobarmory:heavy_armor` | 重甲 | 護甲提高、移動速度降低 |

這四件的實際數值以 `config/mobarmory/weapons/` 裡的 JSON 為準。在網頁或檔案改過的話，就是改過的數字。

### 怪物

| 怪物 | 生怪蛋 | 指令 | 特色 |
|---|---|---|---|
| 狂戰士 Brute | `mobarmory:brute_spawn_egg` | `/summon mobarmory:brute` | 生命 40、拿重劍、體型是殭屍的 1.2 倍 |
| 遊擊兵 Skirmisher | `mobarmory:skirmisher_spawn_egg` | `/summon mobarmory:skirmisher` | 生命 16、拿短刀、暴擊率 25%、迴避率 30% |

兩種怪物都會閒晃，看到生存模式的玩家就會追上去攻擊。

### 指令

| 指令 | 用途 |
|---|---|
| `/mobarmory stats` | 看自己的數值，包括基礎值和加上裝備後的值。後面可以接一個目標，例如 `/mobarmory stats @e[type=mobarmory:brute,limit=1,sort=nearest]` |
| `/mobarmory reload` | 改完 `config/mobarmory/weapons/` 裡的檔案後，不用重開遊戲就重新載入。需要 OP |

### 網頁後台

- 網址：`http://127.0.0.1:8765/`。只有開遊戲的這台電腦連得到。
- 用 OP 身分進世界時，聊天欄會出現可以點的連結。
- 可以做的事：
  - 列出伺服器上的武器。
  - 修改名稱、數值和外觀，存檔後立刻套用到所有同種武器，並寫回 JSON。
  - 上傳 PNG 貼圖。
  - 新增武器：存檔後清單標「待重開」，重開遊戲才能用。
  - 從檔案重新載入。
- 「怪物」分頁：列出每隻怪物的外觀、數值、裝備和行為，只能看，不能改（`GET /api/v1/mobs`）。
- 設定在 `config/mobarmory/web.json`：

  ```json
  { "enabled": true, "host": "127.0.0.1", "port": 8765, "token": "" }
  ```

  `host` 改成其他電腦也連得到的位址時，一定要設定 `token`（一組很長的亂數密碼），否則網頁不會啟動。

## 加一把武器（不用寫 Java）

在 `config/mobarmory/weapons/` 新增一個 JSON 檔。**檔名就是武器的 id**，只能用小寫英文、數字和底線，而且要用英文字母開頭。下面這個例子就是內建的重劍 `heavy_sword.json`：

```json
{
  "format": 1,
  "name": "Heavy Sword",
  "slot": "mainhand",
  "look": { "item": "minecraft:netherite_sword" },
  "modifiers": [
    { "stat": "attack_damage", "add": 6 },
    { "stat": "movement_speed", "percent": -30 },
    { "stat": "attack_speed", "percent": -40 }
  ]
}
```

| 欄位 | 可以填的值 |
|---|---|
| `slot` | `mainhand`、`offhand`、`head`、`chest`、`legs`、`feet` |
| `look` | 下面三種外觀選一種：<br>• 借用原版物品：`{ "item": "minecraft:iron_sword" }`<br>• 7 張內建貼圖：`{ "texture": "scythe_blood" }`。可用的名稱有 `dagger_iron`、`dagger_ruby`、`spear_iron`、`pitchfork_gold`、`halberd_netherite`、`halberd_wooden`、`scythe_blood`<br>• 自己的 PNG：`{ "texture": "my_blade" }`，圖片放在 `config/mobarmory/textures/my_blade.png` |
| `modifiers` 的 `stat` | `attack_damage`、`movement_speed`、`armor`、`attack_speed`、`max_health`、`crit_chance`、`dodge_chance` |
| `add`／`percent` | `add` 加固定值；`percent` 加百分比，`-30` 就是 −30%。暴擊率、迴避率用 `add`，`0.25` 就是 25% |

**生效的時機**：

- 新的 id：要重開遊戲。Minecraft 只在啟動時建立物品種類。
- 既有的武器改數值、名稱或外觀：打 `/mobarmory reload`，或在網頁存檔，馬上生效。
- 改裝備欄位：要重開遊戲。

**PNG 的規則**：

- 必須是 PNG 檔、正方形，邊長是 16、32、64、128 或 256 像素，檔案最大 256 KB。
- 名稱只能用小寫英文、數字和底線，不能和 7 張內建貼圖同名。
- 不合規則的圖片，上傳時會被擋下；放在資料夾裡的話，遊戲啟動或 reload 時會被擋下。

檔案寫錯時，遊戲不會啟動，log 會寫出是哪個檔案、哪個欄位錯了。在網頁或用 reload 改錯時，則是什麼都不會改。

## 加一個數值（不用寫 Java）

在 `config/mobarmory/stats/` 放一個 JSON 檔，然後**重開遊戲**。**檔名就是數值的 id**，規則和武器一樣。之後每一種生物都有這個數值，武器的 `modifiers` 也可以加減它，網頁的武器表單會自動列出它。

數值有兩種：

**1. 純數值**（不寫 `attribute`）：一個新的數字，例如「幸運」。它會掛在每一隻生物身上，可以被武器加減，也會出現在 tooltip 和 `/mobarmory stats`。但它**本身不會造成任何效果**，要有戰鬥規則就要寫 Java（見下）。

```json
{ "format": 1, "name": "Luck", "default": 0, "min": 0, "max": 1 }
```

**2. 對應 Minecraft 的屬性**（寫 `attribute`）：效果是 Minecraft 自己的，不用寫 Java。例如讓武器提高跳躍力：

```json
{ "format": 1, "attribute": "minecraft:jump_strength", "default": 0.42, "min": 0, "max": 32 }
```

存成 `jump.json`，在武器裡寫 `{ "stat": "jump", "percent": 50 }`，拿著那把武器的玩家就跳得比較高。其他可以用的屬性，例如 `minecraft:knockback_resistance`（擊退抗性）、`minecraft:scale`（體型）、`minecraft:step_height`（能走上去的高度）。

| 欄位 | 必填 | 說明 |
|---|---|---|
| `format` | 是 | 固定是 `1` |
| `default`、`min`、`max` | 是 | 預設值和上下限，`min ≤ default ≤ max` |
| `attribute` | 否 | 對應的 Minecraft 屬性；不寫就是純數值 |
| `floor` | 否 | 0～1，裝備再怎麼減，都不會低於基礎值的這個比例（移動速度用 0.3） |
| `name` | 否 | 純數值在 tooltip 顯示的名稱；不寫就顯示翻譯鍵 |
| `sync` | 否 | 純數值要不要同步給客戶端，預設 `true` |

**規則**：

- **新數值一定要重開遊戲**：Minecraft 只在啟動時建立屬性。`/mobarmory reload` 和網頁只會重新載入武器。
- **7 個內建數值**（`attack_damage`、`movement_speed`、`armor`、`attack_speed`、`max_health`、`crit_chance`、`dodge_chance`）：
  - 可以改數字，例如把 `crit_chance.json` 的 `default` 改成 0.05，每隻生物就有 5% 暴擊率。
  - 不能改種類：純數值不能改成對應屬性，原版屬性也不能改指向別的屬性。改了遊戲不會啟動。
  - 內建數值的檔案被刪掉，就用內建的值，log 會寫一行警告。整個 `stats/` 資料夾刪掉，下次啟動會重新寫出 7 個檔案。
- **有戰鬥規則的數值要寫 Java**：例如暴擊（傷害 ×1.5）、迴避（擋下攻擊），規則寫在 `core/combat` 的 `StatEffect`，並在 `content/ModStats` 的 `COMBAT_EFFECTS` 加一行。JSON 只能加「純數值」或「對應原版屬性的數值」。
- **檔案寫錯**（格式、數字範圍、檔名、兩個數值對應同一個屬性），遊戲不會啟動，log 會一次列出所有錯誤和檔名。

## 加一隻怪物（3 個檔案）

怪物用 Java 寫成一筆定義。實體種類、數值、AI、渲染和生怪蛋，都會從這筆定義自動產生。commit `cbb38b5` 實際加過第 3 種怪物「哨兵」，只改了下面 3 個檔案（之後為了維持雛型的兩種怪物，又移除了）：

1. `src/main/java/io/github/alextixu/mobarmory/content/ModMobs.java`：加一筆定義，並放進 `ALL`。

   ```java
   public static final MobDefinition SENTINEL = MobDefinition.builder("sentinel")
       .look(MobLook.of(MobLook.Body.SKELETON))   // 借用的身體：ZOMBIE、HUSK、SKELETON、STRAY
       .stat(ModStats.MAX_HEALTH, 24)
       .stat(ModStats.ATTACK_DAMAGE, 3)
       .stat(ModStats.MOVEMENT_SPEED, 0.23)
       .equip(EquipmentSlotKind.MAINHAND, "dagger")       // 用武器的 id
       .equip(EquipmentSlotKind.CHEST, "heavy_armor")
       .behavior(new WanderBehavior(0.8))
       .behavior(new AttackPlayersBehavior(1.2))
       .build();

   public static final List<MobDefinition> ALL = List.of(BRUTE, SKIRMISHER, SENTINEL);
   ```

2. `src/main/resources/assets/mobarmory/lang/en_us.json`：加怪物的名稱，`"entity.mobarmory.sentinel": "Sentinel"`。
3. `src/main/resources/assets/mobarmory/items/sentinel_spawn_egg.json`：生怪蛋的圖示。

   ```json
   { "model": { "type": "minecraft:model", "model": "minecraft:item/skeleton_spawn_egg" } }
   ```

改完後 build、重開遊戲。如果怪物用到的武器檔不存在，或欄位對不上，怪物照常生成，只是不拿那件，log 會寫一行警告。

## 多人遊戲的規則

伺服器說了算。下面這些內容，伺服器會在玩家登入時、以及每次修改之後，同步給每位玩家：

- 數值：由原版的屬性同步。
- 名稱和 tooltip：由模組的封包同步。
- 上傳的 PNG：用封包送給玩家，客戶端會自動重新載入資源。
- 外觀：武器帶著目前外觀的 `item_model`，由原版同步。

但是**有兩樣東西每位玩家都要自己準備**，否則連不上或開不起來：

1. **同一組武器檔**：每位玩家的 `config/mobarmory/weapons/` 要和伺服器有同一組檔名，也就是同一組 id。Minecraft 規定客戶端和伺服器的物品清單必須相同，少一把或多一把都會連不上。檔案裡的數值、名稱、外觀可以不同，連上後一律照伺服器的。
2. **這些武器用到的上傳 PNG**：如果某把武器的外觀是上傳的貼圖，每位玩家的 `config/mobarmory/textures/` 也要有同名的 PNG。客戶端啟動時會檢查武器用到的貼圖在不在，不在就開不起來。圖片內容可以不同，連上後會換成伺服器的。
3. **同一組數值檔**：每位玩家的 `config/mobarmory/stats/` 要和伺服器有同一組檔名。純數值是新的屬性，客戶端和伺服器的屬性清單不同就連不上。內容也最好一樣：上下限不同時，客戶端顯示的數字可能和伺服器不同。

所以在網頁新增武器之後，要照這個順序：

1. 重開伺服器。
2. 把 `weapons/<id>.json` 複製給每位玩家。如果這把武器用上傳的貼圖，連 `textures/<名稱>.png` 也要複製。
3. 玩家重開遊戲，再連線。

另一種做法：玩家把自己那份 JSON 的外觀改成原版物品，就不需要那張 PNG。

玩家離開伺服器（中斷連線或登出）時，客戶端會丟掉那台伺服器送來的定義和貼圖，改回讀自己的 `config/mobarmory/weapons/`，所以之後開單人世界或連另一台伺服器，都從自己的設定開始。伺服器送貼圖前會再檢查一次 PNG 的規則，不合格的圖不會送出，以免玩家連線時被斷線。

## 已知限制

- **R3 combo 還沒進 main**：正在 `feature/r3-combo` 分支實作。
- **網頁上的怪物只能看，不能改**：怪物是 Java 定義，要改怪物就得改 `content/ModMobs.java` 再重開。
- **數值不能在網頁新增或修改**，也不能在執行中改；改了數值檔要重開遊戲。網頁的武器表單會列出新數值，但顯示的是它的 id（中文名稱只有內建的 7 個有）。
- **從 JSON 加的純數值沒有戰鬥效果**：只會被記錄、顯示，也能被武器加減。要有效果就對應 Minecraft 的屬性，或寫 Java 的 `StatEffect`。
- **多人還沒用兩台客戶端實測**：獨立伺服器（`runServer`）也還沒跑過。目前的證據是伺服器端和封包編碼解碼的自動測試。手動測試步驟在 [15 號報告第 3 節](docs/boundary/15-weapon-data-web-behavior-report.md)。
- **遠端玩家會看到載入畫面**：伺服器上有武器用上傳的貼圖時，遠端玩家每次連進來都會看到幾秒的資源載入畫面。
- **貼圖一律做成手持的樣子**：上傳的 PNG 都做成「拿在手上」的模型，護甲的物品圖示也一樣。護甲穿在身上的樣子是註冊物品時決定的，執行中不能換。
- **怪物的揮擊間隔維持原版**：每秒一下。攻擊速度這個數值只影響玩家（R4 Q8 B）。

## 跑測試

需要 JDK 25。

```bash
./gradlew clean build runGameTestServer
```

- `build`：跑單元測試，不用開遊戲。結果在 `build/test-results/test/`。
- `runGameTestServer`：開一個沒有畫面的遊戲伺服器跑系統測試。結果在 `run-gametest/gametest-report.xml`，有測試失敗時指令會失敗。
- 遊戲內測試在獨立的 `run-gametest/` 資料夾跑，每次先清空，所以不受 `run/config/` 裡改過的設定影響。
- 2026-11-07 段落 E（數值改成 JSON）之後的 main：單元 375/375，遊戲內 61/61。遊戲內的 61 個是我們的 60 個，加上 Minecraft 自帶的 `always_pass`。
- 遊戲內測試開始前，`src/gametest/config/` 的兩個數值檔會被複製進 `run-gametest/config/`，用來測試玩家加的數值。

## 文件索引

每個需求從邊界到報告的完整紀錄。檔案都在 [`docs/boundary/`](docs/boundary/)，總索引在 [`docs/boundary/README.md`](docs/boundary/README.md)。

| 需求 | 邊界說明 | Given / When / Then | 決定（確認點） | prompt | 行為報告 |
|---|---|---|---|---|---|
| R1 數值系統、R2 裝備改變數值、T1 | [01](docs/boundary/01-boundary-note.md) | [02](docs/boundary/02-given-when-then.md) | [03](docs/boundary/03-open-questions.md) | [round-01～03](docs/boundary/prompts/) | [04](docs/boundary/04-behavior-report.md) |
| R4 自訂怪物、T2 | [05](docs/boundary/05-r4-boundary-note.md) | [06](docs/boundary/06-r4-given-when-then.md) | [07](docs/boundary/07-r4-open-questions.md) | [round-03、round-r4-02、round-r4-03](docs/boundary/prompts/) | [14](docs/boundary/14-r4-behavior-report.md) |
| R2b 資料驅動武器、R6 網頁後台 | [08](docs/boundary/08-weapon-data-web-boundary-note.md) | [09](docs/boundary/09-weapon-data-web-given-when-then.md) | [10](docs/boundary/10-weapon-data-web-open-questions.md) | [round-04、05、07](docs/boundary/prompts/) | [15](docs/boundary/15-weapon-data-web-behavior-report.md) |
| 段落 E 數值改成 JSON | [24](docs/boundary/24-stat-files-boundary-note.md) | [25](docs/boundary/25-stat-files-given-when-then.md) | [26](docs/boundary/26-stat-files-open-questions.md) | [round-08](docs/boundary/prompts/round-08-stat-files.md) | [27](docs/boundary/27-stat-files-behavior-report.md) |
| D1 傷害顯示 | [11](docs/boundary/11-damage-display-boundary-note.md) | [12](docs/boundary/12-damage-display-given-when-then.md) | [13](docs/boundary/13-damage-display-open-questions.md) | [damage-display-round-01～03](docs/boundary/prompts/) | [16](docs/boundary/16-damage-display-behavior-report.md) |
| 合併到 main | — | — | — | [round-06-merge](docs/boundary/prompts/round-06-merge.md) | 總索引的「合併到 main」一節 |
| R3 combo | 17～19（在 `feature/r3-combo`） | — | — | — | 20（之後） |

## 素材來源

武器貼圖來自 HanhanPx 的 [Minecraft Weapon Pack](https://hanhanpx.itch.io/minecraft-weapon-pack)。作者頁面寫「Feel free to use and modify it!」並要求標註作者。這個 repo 只放模組用到的 7 張貼圖，內容沒有修改，也不轉發整個素材包。每個檔案對應素材包裡的哪個檔案，見 [`CREDITS.md`](src/main/resources/mobarmory/CREDITS.md)。
