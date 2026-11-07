# Mob Armory：自訂怪物與武器系統（Forge 模組）

封豪學長的**題目二**。

## 主軸：讓玩家自己新增東西

這個模組的重點不是我們做出很厲害的怪物和武器，而是**一套讓玩家以後可以自己新增東西的系統**。武器、怪物、combo、數值都朝「寫一個 JSON 檔（或用網頁），重開遊戲就有」的方向做：

| 要新增的東西 | 玩家怎麼做 | 狀態 |
|---|---|---|
| 武器、護甲 | 在 `config/mobarmory/weapons/` 放一個 JSON 檔，或在網頁後台用「新增武器」表單；外觀可以借用原版物品、用 7 張內建貼圖，或上傳自己的 PNG。見[加一把武器](#加一把武器不用寫-java) | **已完成** |
| 數值（例如「跳躍力」「幸運」） | 在 `config/mobarmory/stats/` 放一個 JSON 檔，重開遊戲，所有生物和武器都能用。可以是純數值，也可以對應 Minecraft 的屬性（例如跳躍力）。有戰鬥規則的數值（像暴擊、迴避）還是要寫 Java。見[加一個數值](#加一個數值不用寫-java) | **已完成** |
| 怪物 | 在 `config/mobarmory/mobs/` 放一個 JSON 檔，或在網頁後台用「新增怪物」表單，重開遊戲就能用生怪蛋或 `/summon` 生出來。身體從 4 種原版人形裡挑，可以放大縮小；數值、裝備、行為（閒晃、攻擊玩家、中立、撲擊）都寫在檔案裡。既有的怪物也能在網頁上改。見[加一隻怪物](#加一隻怪物1-個-json) | **已完成** |
| combo | 在 `config/mobarmory/combos/` 放一個 JSON 檔，寫出招順序（走、跑）、時間窗、名稱和效果，打 `/mobarmory reload` 就生效。效果從已經寫好的種類裡挑（目前有傷害倍率）；新的效果種類才要寫 Java。見[加一個 combo](#加一個-combo不用寫-java) | **已完成** |

## 現在有什麼

- 一套可以擴充的數值系統：攻擊力、移動速度、護甲、攻擊速度、最大生命、暴擊率、迴避率。
- 用 JSON 設定的武器和護甲，可以換外觀，也可以上傳 PNG 貼圖。
- 自訂怪物：狂戰士（brute）、遊擊兵（skirmisher），都是 JSON 檔。
- 網頁後台：在網頁上改武器和怪物、新增武器和怪物、上傳武器貼圖。
- 打中生物時，在 action bar 顯示傷害、暴擊（CRIT）和迴避（MISS）。
- combo：走路揮擊、奔跑揮擊依順序打兩下會觸發 combo（內附 Lunge、Follow-up），名稱接在 action bar 那一行後面。

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
   - `config/mobarmory/mobs/`：2 種預設怪物，每種一個 JSON 檔。
   - `config/mobarmory/combos/`：2 個預設 combo，每個一個 JSON 檔。
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

兩種怪物都會閒晃，看到生存模式的玩家就會追上去攻擊。它們的設定是 `config/mobarmory/mobs/brute.json`、`skirmisher.json`，可以直接改檔案，也可以在網頁的「怪物」分頁改。

### 指令

| 指令 | 用途 |
|---|---|
| `/mobarmory stats` | 看自己的數值，包括基礎值和加上裝備後的值。後面可以接一個目標，例如 `/mobarmory stats @e[type=mobarmory:brute,limit=1,sort=nearest]` |
| `/mobarmory reload` | 改完 `config/mobarmory/weapons/`、`combos/` 或 `mobs/` 裡的檔案後，不用重開遊戲就重新載入。依序重新載入**武器、combo、怪物**，三種各自全有或全無，每一種印一段結果。新增或刪除的 combo 檔立刻生效；新增的武器和怪物要重開。數值檔不會重新載入（要重開）。需要 OP |

### 網頁後台

- 網址：`http://127.0.0.1:8765/`。只有開遊戲的這台電腦連得到，而且要世界（伺服器）開著；離開世界時網頁會關掉。
- 用 OP 身分進世界時，聊天欄會出現可以點的連結。web.json 設了 token 時，這個連結已經帶著 token，點了就能用；聊天欄顯示的文字不含 token。
- 武器（「武器」「新增武器」分頁）：
  - 列出伺服器上的武器。
  - 修改名稱、數值和外觀，存檔後立刻套用到所有同種武器，並寫回 JSON。
  - 上傳 PNG 貼圖。
  - 新增武器：存檔後清單標「待重開」，重開遊戲才能用。
- 怪物（「怪物」「新增怪物」分頁）：
  - 列出伺服器上的怪物：外觀、數值、裝備、行為。
  - 修改怪物，存檔後寫回 `config/mobarmory/mobs/<id>.json`：
    - 名稱、數值、大小：立刻套用到世界上的同種怪物。改最大生命不會補血。
    - 行為、裝備：下一次生成的怪物才生效。
    - 身體：要重開遊戲才能換。
  - 新增怪物：存檔後清單標「待重開」，重開遊戲才能用生怪蛋或 `/summon` 生出來。
- 「從檔案重新載入」按鈕：重新載入武器和怪物。**combo 檔要用 `/mobarmory reload`**，數值檔要重開遊戲。
- 武器和怪物都沒有刪除按鈕，要刪就刪檔案，再重開遊戲。
- 設定在 `config/mobarmory/web.json`：

  ```json
  { "enabled": true, "host": "127.0.0.1", "port": 8765, "token": "", "allowNetworkWithoutToken": false }
  ```

  `host` 改成其他電腦也連得到的位址時，要設定 `token`（一組很長的亂數密碼），否則網頁不會啟動。例外是 `allowNetworkWithoutToken`，見下面。

  - `host` 寫 `0.0.0.0`（或 `::`）時，區網裡的電腦可以用這台電腦的任一個 IP 連進來，例如 `http://192.168.1.52:8765/`。log 和聊天欄顯示的網址，會是這台電腦的區網 IP。用其他名稱連進來一律擋下。網卡的 IP 在世界啟動時讀一次，換了網路要重開世界。
  - 不是從聊天欄的連結打開時，在網址後面加上 `?token=<web.json 裡的 token>`。網頁讀到 token 後會把它從網址列拿掉，重新整理仍然可以用。
  - `allowNetworkWithoutToken` 改成 `true`、`token` 留空時，區網的電腦用 IP 就能直接打開網頁，不用 token。預設是 `false`。
    - **風險**：連得到這個位址和埠的人，都能改武器和怪物的數值、上傳貼圖，也能新增武器和怪物。只在信任的區網裡打開，不要讓網路外面連得到這個埠。
    - 打開時，每次世界（伺服器）啟動都會在 log 寫一行警告。
    - 別的網站偷送請求的保護（`Host`、`Origin` 檢查）照樣有效。設了 `token` 的話，一律照 token 的規則。

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
| `modifiers` 的 `stat` | `attack_damage`、`movement_speed`、`armor`、`attack_speed`、`max_health`、`crit_chance`、`dodge_chance`，以及 `config/mobarmory/stats/` 裡任何數值的 id（見[加一個數值](#加一個數值不用寫-java)） |
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

上限（`max`）≤ 1 的純數值會被當成機率，tooltip 用百分比顯示。例如上限是 1 的 `luck`，武器加 0.5 時，tooltip 顯示「+50% Luck」；暴擊率、迴避率也是這樣。

```json
{ "format": 1, "name": "Luck", "default": 0, "min": 0, "max": 1 }
```

**2. 對應 Minecraft 的屬性**（寫 `attribute`）：效果是 Minecraft 自己的，不用寫 Java。例如讓武器提高跳躍力：

```json
{ "format": 1, "attribute": "minecraft:jump_strength", "default": 0.42, "min": 0, "max": 32 }
```

存成 `jump.json`，在武器裡寫 `{ "stat": "jump", "percent": 50 }`，拿著那把武器的玩家就跳得比較高。其他可以用的屬性，例如 `minecraft:knockback_resistance`（擊退抗性）、`minecraft:scale`（體型）、`minecraft:step_height`（能走上去的高度）。

這種數值的 `default` 只影響本模組的怪物（`config/mobarmory/mobs/` 裡的怪物）。玩家和原版生物照樣用 Minecraft 自己的預設值，例如玩家的跳躍力一樣是 0.42。`min`、`max` 則限制武器加減之後的結果。

| 欄位 | 必填 | 說明 |
|---|---|---|
| `format` | 是 | 固定是 `1` |
| `default`、`min`、`max` | 是 | 預設值和上下限，`min ≤ default ≤ max` |
| `attribute` | 否 | 對應的 Minecraft 屬性；不寫就是純數值 |
| `floor` | 否 | 0～1，裝備再怎麼減，都不會低於基礎值的這個比例（移動速度用 0.3） |
| `name` | 否 | 純數值在 tooltip 顯示的名稱；不寫就顯示翻譯鍵 |
| `sync` | 否 | 純數值要不要同步給客戶端，預設 `true` |

**規則**：

- **新數值一定要重開遊戲**：Minecraft 只在啟動時建立屬性。`/mobarmory reload`（武器、combo、怪物）和網頁的重新載入（武器、怪物）都不會讀數值檔。
- 新數值也可以寫在怪物檔的 `stats` 裡（見[加一隻怪物](#加一隻怪物1-個-json)）。
- **7 個內建數值**（`attack_damage`、`movement_speed`、`armor`、`attack_speed`、`max_health`、`crit_chance`、`dodge_chance`）：
  - 可以改數字，例如把 `crit_chance.json` 的 `default` 改成 0.05，每隻生物就有 5% 暴擊率。
  - 不能改種類：純數值不能改成對應屬性，原版屬性也不能改指向別的屬性。改了遊戲不會啟動。
  - 內建數值的檔案被刪掉，就用內建的值，log 會寫一行警告。整個 `stats/` 資料夾刪掉，下次啟動會重新寫出 7 個檔案。
- **有戰鬥規則的數值要寫 Java**：例如暴擊（傷害 ×1.5）、迴避（擋下攻擊），規則寫在 `core/combat` 的 `StatEffect`，並在 `content/ModStats` 的 `COMBAT_EFFECTS` 加一行。JSON 只能加「純數值」或「對應原版屬性的數值」。
- **檔案寫錯**（格式、數字範圍、檔名、兩個數值對應同一個屬性），遊戲不會啟動，log 會一次列出所有錯誤和檔名。

## 加一個 combo（不用寫 Java）

combo 是「依順序打兩下」。每一下是走路揮擊或奔跑揮擊：揮擊開始時移動速度 ≥ 0.12 算奔跑（空手疾跑是 0.13，拿重劍疾跑只有 0.091，跑不出奔跑揮擊）。只有打中、而且蓄滿的近戰揮擊才算一下。

在 `config/mobarmory/combos/` 放一個檔，檔名就是 id（小寫英文開頭，再接小寫英文、數字、底線）。例如 `double_slash.json`：

```json
{
  "format": 1,
  "name": "Double Slash",
  "sequence": ["walk", "walk"],
  "window_ticks": 20,
  "effect": { "type": "damage_multiplier", "value": 2 }
}
```

打 `/mobarmory reload`，走路連打兩下，第二下傷害 ×2，action bar 那一行後面會出現「· Double Slash」。

| 欄位 | 必填 | 說明 |
|---|---|---|
| `format` | 否 | 固定是 `1` |
| `name` | 是 | 顯示名稱，1～32 字，照寫的顯示 |
| `sequence` | 是 | 兩下的順序，每一下是 `walk` 或 `run` |
| `window_ticks` | 是 | 第二下最晚在第一下之後幾 tick（20 tick = 1 秒），1～1200，剛好那麼多也算 |
| `effect` | 是 | 效果種類和參數。目前有 `{"type": "damage_multiplier", "value": 倍率}`，倍率要大於 0 |

**規則**：

- 第一次啟動會建立兩個預設檔：`lunge.json`（走→跑 ×1.5）、`follow_up.json`（跑→走 ×1.3），時間窗都是 30 tick。資料夾已經存在就不會補回來，全部刪掉就沒有 combo。
- 兩個檔不能是同一個出招順序（後面那個永遠不會觸發）。
- 觸發後重新開始，完成 combo 的那一下不會當成下一個 combo 的第一下。所有武器和空手共用；只有玩家能 combo；第二下可以打別隻；combo 和暴擊相乘。
- 檔案寫錯時：啟動時遊戲不會啟動，`/mobarmory reload` 時什麼都不改。兩種情況都會寫出檔名和欄位。
- 新的效果種類（例如擊退、緩速）要寫 Java：在 `core/combo` 加一個 `ComboEffect`，在 `data/ComboEffectTypes` 登記一行。之後所有 combo 檔都能用。

## 加一隻怪物（1 個 JSON）

在 `config/mobarmory/mobs/` 放一個 JSON 檔，然後**重開遊戲**；或者在網頁的「新增怪物」表單填寫，會寫出同一個檔。**檔名就是怪物的 id**，規則和武器一樣。

重開之後，這種怪物就有自己的生物種類（`/summon mobarmory:<id>`）、生怪蛋（`mobarmory:<id>_spawn_egg`，在 Mob Armory 分頁）、名稱、數值、AI 和外觀，**不用改任何 Java，也不用 build**。下面這個例子就是內建的狂戰士 `brute.json`：

```json
{
  "format": 1,
  "name": "Brute",
  "look": { "body": "zombie", "scale": 1.2 },
  "stats": { "max_health": 40, "attack_damage": 4, "movement_speed": 0.25 },
  "equipment": { "mainhand": "heavy_sword" },
  "behaviors": [
    { "type": "wander", "speed": 1 },
    { "type": "attack_players", "speed": 1 }
  ]
}
```

| 欄位 | 必填 | 說明 |
|---|---|---|
| `format` | 否 | 固定是 `1` |
| `name` | 是 | 遊戲裡顯示的名稱，1～64 字，照寫的顯示（不用語系檔）。生怪蛋顯示「`<名稱>` Spawn Egg」 |
| `look.body` | 是 | 借用的原版身體：`zombie`（殭屍）、`husk`（屍殼）、`skeleton`（骷髏）、`stray`（流髑）。生怪蛋的圖示也借同一種身體的蛋 |
| `look.scale` | 否 | 大小倍率，碰撞箱也跟著變，0.0625～16；預設 1 |
| `stats` | 否 | 數值的 id → 基礎值，例如 `"max_health": 40`。可以用 7 個內建數值，也可以用 `config/mobarmory/stats/` 裡加的數值。沒寫的用預設值 |
| `equipment` | 否 | 欄位 → 武器的 id，例如 `"mainhand": "heavy_sword"`、`"chest": "heavy_armor"`。欄位和武器檔的 `slot` 一樣 |
| `behaviors` | 否 | 行為清單，見下表。沒寫就站著不動 |

**行為（`behaviors`）有四種**：

| `type` | 做什麼 | 參數 |
|---|---|---|
| `wander` | 沒事時到處閒晃 | `speed`：速度倍率，乘在移動速度上 |
| `attack_players` | 看到生存模式的玩家就追上去打 | `speed`：追擊時的速度倍率，大於 1 就是「奔跑」 |
| `neutral` | 平常不理人，被打了才反擊 | `speed`：反擊時的速度倍率 |
| `leap` | 追到附近時撲向目標（衝刺） | 沒有參數 |

- `speed` 要大於 0、最多 3，不寫就是 1。
- `attack_players` 和 `neutral` 最多只能有一個。
- `leap` 要搭配 `attack_players` 或 `neutral` 其中一個，沒有目標就撲不了。

**生效的時機**：

| 改了什麼 | 什麼時候生效 |
|---|---|
| 新的怪物檔（新的 id） | 重開遊戲。Minecraft 只在啟動時建立生物種類 |
| 名稱、數值、大小 | 網頁存檔或 `/mobarmory reload` 後，世界上的同種怪物**立刻**變。改最大生命不會補血 |
| 行為、裝備 | 下一次生成的怪物，或從存檔載入的怪物 |
| 身體 | 重開遊戲。網頁會擋下來並說明；改檔案再 reload 的話，會列在「重開後生效」 |
| 刪掉怪物檔 | 撐到重開為止；重開後，世界上那種怪物會消失 |

**其他規則**：

- 怪物用到的武器檔不存在，或武器的欄位對不上時，怪物照常生成，只是不拿那件，log 會寫一行警告。
- 怪物檔寫錯（格式、身體、數值、行為的組合），遊戲不會啟動，log 會寫出檔名和欄位。在網頁或用 reload 改錯時，什麼都不會改。
- 第一次啟動會寫出 `brute.json`、`skirmisher.json`。資料夾已經存在就不會補回來。
- 新的行為種類（第五種）要寫 Java：`core/mob` 一個行為、一個 goal binder，再各登記一行。28 號報告第 6 節實測過，共 5 個檔案。
- 新的身體種類也要寫 Java（渲染器）。怪物不能像武器那樣上傳自己的貼圖。

## 多人遊戲的規則

伺服器說了算。下面這些內容，伺服器會在玩家登入時、以及每次修改之後，同步給每位玩家：

- 數值：由原版的屬性同步。
- 武器的名稱和 tooltip、怪物的名稱：由模組的封包同步。
- 上傳的 PNG：用封包送給玩家，客戶端會自動重新載入資源。
- 外觀：武器帶著目前外觀的 `item_model`，由原版同步。

但是**有四樣東西每位玩家都要自己準備**，否則連不上或開不起來：

1. **同一組武器檔**：每位玩家的 `config/mobarmory/weapons/` 要和伺服器有同一組檔名，也就是同一組 id。Minecraft 規定客戶端和伺服器的物品清單必須相同，少一把或多一把都會連不上。檔案裡的數值、名稱、外觀可以不同，連上後一律照伺服器的。
2. **這些武器用到的上傳 PNG**：如果某把武器的外觀是上傳的貼圖，每位玩家的 `config/mobarmory/textures/` 也要有同名的 PNG。客戶端啟動時會檢查武器用到的貼圖在不在，不在就開不起來。圖片內容可以不同，連上後會換成伺服器的。
3. **同一組數值檔**：每位玩家的 `config/mobarmory/stats/` 要和伺服器有同一組檔名。
   - 純數值是新的屬性，客戶端和伺服器的屬性清單不同就連不上。
   - 少的是對應原版屬性的數值檔時，還是連得上，因為屬性是 Minecraft 本來就有的。但伺服器送來的武器定義裡用到了客戶端不認得的數值，客戶端會整份不套用。所以這時所有武器的名稱和 tooltip 都是玩家本機的版本，數值本身還是照伺服器。
   - 內容也最好一樣：上下限不同時，客戶端顯示的數字可能和伺服器不同。
4. **同一組怪物檔**：每位玩家的 `config/mobarmory/mobs/` 要和伺服器有同一組檔名。
   - 每種怪物都是一個生物種類，和物品一樣，客戶端和伺服器的清單不同就連不上。這是推論，還沒有實測。
   - **身體以每台電腦自己的檔案為準**：伺服器把狂戰士換成骷髏的身體，玩家沒有換檔案的話，看到的還是殭屍。
   - 名稱由伺服器同步；數值、大小由原版同步；行為只在伺服器跑。這幾樣的檔案內容可以不同。

**combo 不用準備**：combo 檔只有伺服器需要。出招狀態和傷害都在伺服器算，combo 名稱用語系鍵加上文字送給玩家，玩家的 `config/mobarmory/combos/` 有什麼都不影響。

所以在網頁新增武器或怪物之後，要照這個順序：

1. 重開伺服器。
2. 把 `weapons/<id>.json` 或 `mobs/<id>.json` 複製給每位玩家。如果新武器用上傳的貼圖，連 `textures/<名稱>.png` 也要複製。
3. 玩家重開遊戲，再連線。

另一種做法：玩家把自己那份 JSON 的外觀改成原版物品，就不需要那張 PNG。

玩家離開伺服器（中斷連線或登出）時，客戶端會丟掉那台伺服器送來的武器定義、貼圖和怪物名稱，改回讀自己的檔案，所以之後開單人世界或連另一台伺服器，都從自己的設定開始。伺服器送貼圖前會再檢查一次 PNG 的規則，不合格的圖不會送出，以免玩家連線時被斷線。

## 已知限制

- **combo 不能在網頁新增或修改**：要改 `config/mobarmory/combos/` 裡的檔案，再打 `/mobarmory reload`。
- **怪物的外觀只能從 4 種原版身體裡挑**，可以縮放，但不能上傳自己的貼圖；換身體要重開。
- **怪物的行為只有 4 種**（閒晃、攻擊玩家、中立、撲擊），新的行為要寫 Java。
- **網頁沒有刪除按鈕**（武器和怪物都是），要刪就刪檔案再重開。
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
- 2026-11-08（第 12 輪之後）：單元 528/528，遊戲內 78/78。遊戲內的 78 個是我們的 77 個，加上 Minecraft 自帶的 `always_pass`。要確定單元測試真的重跑、不是拿快取的結果，加 `--rerun`（`./gradlew test --rerun`）。
- **更正**：這裡原本寫「2026-11-07 … 單元 505/505」，是錯的。那次 Gradle 拿了快取的舊結果，沒有真的重跑。實際上從 `27765a1` 起有 12 個單元測試失敗（`7c4eff0` 跑了 489 個，12 個失敗），第 12 輪已修好。
- 遊戲內測試開始前，`src/gametest/config/` 會被複製進 `run-gametest/config/`。裡面放的是玩家可能會寫的檔案，用來測試「只靠 JSON 就能加」：兩個數值檔（`gametest_luck`、`gametest_jump`），以及一隻只有 JSON 的怪物 `goblin`（T2）。

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
| R4b 怪物改成 JSON、網頁改怪物（A6、A7、T2） | [21](docs/boundary/21-r4b-mob-admin-boundary-note.md) | [22](docs/boundary/22-r4b-mob-admin-given-when-then.md) | [23](docs/boundary/23-r4b-mob-admin-open-questions.md) | [round-r4b-01～03](docs/boundary/prompts/)、[round-09](docs/boundary/prompts/round-09-r4b-main.md) | [28](docs/boundary/28-r4b-mob-admin-behavior-report.md) |
| R3 combo | [17](docs/boundary/17-r3-combo-boundary-note.md) | [18](docs/boundary/18-r3-combo-given-when-then.md) | [19](docs/boundary/19-r3-combo-open-questions.md) | [r3-combo-round-01～03](docs/boundary/prompts/) | [20](docs/boundary/20-r3-combo-behavior-report.md) |

## 素材來源

武器貼圖來自 HanhanPx 的 [Minecraft Weapon Pack](https://hanhanpx.itch.io/minecraft-weapon-pack)。作者頁面寫「Feel free to use and modify it!」並要求標註作者。這個 repo 只放模組用到的 7 張貼圖，內容沒有修改，也不轉發整個素材包。每個檔案對應素材包裡的哪個檔案，見 [`CREDITS.md`](src/main/resources/mobarmory/CREDITS.md)。
