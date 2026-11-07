# 邊界說明：R4b 怪物改成資料檔，網頁可以設定和新增怪物（A6、A7，T2）

> 狀態：**使用者 2026-11-08 確認：全部照建議**（原話：「全部照建議」）。2026-11-07 主 session 依使用者夜間授權先採用建議值。決定在 [23](23-r4b-mob-admin-open-questions.md)：Q1～Q8 都選 A，O1～O21 都同意，所以本檔內容照建議值，不用改寫。
> 流程：boundary-first 第 1、2 步。第 3 步的 Given / When / Then 在 [22](22-r4b-mob-admin-given-when-then.md)。本檔寫在第一輪（只寫文件）；之後照本檔實作（`949597d`），行為報告是 [28](28-r4b-mob-admin-behavior-report.md)。
> 實際做法和本檔原本的寫法不同的地方，記在 23 號「合併 main `6a3e1fc` 後的調整」一節。2026-11-07 依 review-05 S6，把本檔裡已經不對的幾處改成實際的做法。
> 前提：
> - 沿用 R4（[05](05-r4-boundary-note.md)～[07](07-r4-open-questions.md)、[14](14-r4-behavior-report.md)）和 R2b／R6（[08](08-weapon-data-web-boundary-note.md)～[10](10-weapon-data-web-open-questions.md)、[15](15-weapon-data-web-behavior-report.md)）已確認的架構，不重新設計。
> - 接在架構師段落 A 的唯讀 `GET /api/v1/mobs` 後面。
> - 分支 `feature/r4b-mob-admin`，從 main `061e7a9` 開，寫程式前先合了 main `6a3e1fc`，也就是段落 A 和 E（`403d80b`）。合進 main 前再合了含 combo 的 main `3c4eb42`（`23246ed`）。2026-11-07 由架構師快轉進 main（`ce7beaa`）。

## 1. 需求翻譯

需求來源：

- 學長的 A6：網頁可以設定怪物行為，例如能不能奔跑、衝刺、會不會攻擊玩家、是不是中立。
- 學長的 A7：初期怪物可以改數值。
- 審查 review-02、review-03 的風險 5：武器可以用 JSON 和網頁新增，怪物卻要改 Java 再重開遊戲。
- 使用者 11/07 的主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」
- 使用者 11/06 的原則：學長要求的才做，不另外加特效或額外規則，預設不做（見 [07](07-r4-open-questions.md) 第三輪）。

### 真正的需求

- **怪物和武器一樣，是玩家的資料，不是我們的程式碼。** 每一種怪物是 `config/mobarmory/mobs/<id>.json` 的一個檔案。第一次啟動時，jar 裡的狂戰士、遊擊兵兩個預設檔會被放進去。玩家多放一個檔，重開遊戲就多一種怪物。
- **「怪物種類」和「怪物的定義」分開，和武器一樣。**
  - 生物種類只能在啟動時註冊（平台限制），所以新怪物一定要重開。
  - 定義（名稱、數值、大小、裝備、行為）可以在執行中整份換掉。
- **能設定的只有學長提到的四類。**
  - 外觀：從原版人形模型挑一種，加大小倍率。
  - 數值：走 R1 的登記表。
  - 手上的裝備：用武器 id 指定。
  - 行為：攻擊玩家或中立（被打才反擊），能不能奔跑、衝刺。
- **行為只用原版現成的 AI goal 組合，不寫新 AI、不做新特效。**
- **網頁只是另一個改定義的入口。** 遊戲、`/mobarmory reload`、網頁讀寫的都是同一份檔案，驗證規則只有一套。網頁存檔就寫回檔案。
- **盡量沿用武器那一套。**
  - 讀寫和驗證：`data/`。
  - 可以在執行中替換的快照：`EquipmentLibrary` 的形狀。
  - 網頁：`WeaponAdmin` 的形狀；`WeaponAdmin` 的 Javadoc 已經寫了「Monsters (R4) will get a MobAdmin of the same shape」。
  - 同步封包：`WeaponSyncChannel` 的做法。
  - 可以抽成共用的地方，寫在第 3 節的 SOLID 判斷。
- **T2 的證明改成：** 玩家只放一個 JSON（或在網頁上填表單），重開以後就多一種怪物，**Java 一行都不用改，也不用加任何資源檔**。

### 沒講到、我本來會替你選的事

每一題的建議答案寫在 [23](23-r4b-mob-admin-open-questions.md)：

1. 範圍：只讓網頁改既有怪物，還是怪物也改成 JSON、玩家可以新增（使用者 11/07 的主軸已指向後者）？
2. 「奔跑」「衝刺」「中立」各是什麼意思，用哪個原版機制做？追擊距離要不要開放設定？
3. 「攻擊玩家」和「中立」同時寫了怎麼辦？「衝刺」沒有搭配攻擊時怎麼辦？
4. 網頁改了以後，已經在世界上的怪物哪些立刻變，哪些等下次生成，哪些要重開？
5. 多人：名稱和其他定義怎麼送到其他玩家的電腦？
6. 怪物檔寫錯時，遊戲要不要照樣啟動？
7. 怪物檔被刪掉時怎麼辦？重開以後，世界上那種怪物會怎麼樣？
8. 網頁畫面：清單、編輯表單、新增表單各長什麼樣？
9. 名稱和生怪蛋的外觀從哪裡來（現在是語系檔和 `items/<id>_spawn_egg.json`）？
10. 改了最大生命值，正在受傷的怪物目前的血量怎麼算？

### 這次要做／未來可能變

| 這次要做（R4b） | 未來會變，所以要在這裡切邊界 |
|---|---|
| 怪物定義從 `content/ModMobs` 搬到 JSON 檔，兩個預設檔放進 jar | **玩家會一直加怪物** → 一種怪物一個檔，檔名就是 id，和武器一樣 |
| 網頁：清單、改數值和行為、新增怪物（重開生效） | **能設定的欄位會加** → 檔案格式有 `format` 版本號；欄位的驗證只有一套，檔案和網頁共用 |
| 行為：閒晃、攻擊玩家（已有），加中立、衝刺；奔跑是追擊速度 | **行為種類會加** → 每種行為一個 `Behavior` 加一個 `GoalBinder`，JSON 讀寫查對照表，不寫 switch |
| 名稱、數值、大小立刻套用；同步給其他玩家 | **資料種類會加**（武器、怪物，之後可能還有別的）→ 重新載入、切執行緒、原子寫檔抽成共用的零件 |
| — | **自然生成、掉落物**：這次不做，07 的 Q5、O7 不變 |

## 2. 平台本身已經提供的功能（技術背景，非決策）

以下大多在 R4 和 R2b 時已經在 Forge 26.3-66.1.0 和 Minecraft 26.3 原始碼裡確認過。標「實作時查證」的是這次新推論、還沒實測的。

- **生物種類在啟動後凍結**（[05](05-r4-boundary-note.md) 第 2 節）。模組建構時就讀得到 `config/`，比註冊早；R2b 對物品就是這樣做的，生物種類一樣。
- **每隻生物的基礎數值可以隨時改**（`AttributeInstance.setBaseValue`）。生命、移動速度、大小這些原版屬性會由原版同步到客戶端。生物種類的預設屬性是啟動時固定的，所以改了定義以後，要在每隻生成或載入時把基礎值設成「現在的定義」。
- **原版現成的 goal**：
  - `HurtByTargetGoal`：被打就反擊打牠的對象。
  - `NearestAttackableTargetGoal<Player>`：主動找玩家。
  - `MeleeAttackGoal(speedModifier)`：近戰，追擊時用「移動速度 × 倍率」。
  - `LeapAtTargetGoal`：撲向目標，蜘蛛、狼、山貓用這個。
  - goal 是在生物建立時加上去的（`registerGoals`）。
- **改最大生命值時**，原版會把超過新上限的目前血量降到上限。（實作時查證）
- **渲染器是啟動時依生物種類註冊的**（`EntityRenderersEvent.RegisterRenderers`）。所以「身體」換成別種原版模型，要重開才看得到；大小是屬性，可以立刻變。
- **名稱**：生物種類的名稱是啟動時固定的語系鍵，但每隻生物顯示的名稱可以覆寫（`getTypeName`）。武器的物品名稱已經用同一招（`EquipmentItem.getName` 讀定義）。
- **生怪蛋的外觀**：物品可以用 `item_model` 元件借原版的模型。武器的 `WeaponLooks` 已經這樣做，所以新怪物不用另外放 `items/<id>_spawn_egg.json`。（要用物品的預設元件，實作時查證）
- **多人**：客戶端和伺服器各自從自己的檔案註冊生物種類，所以兩邊的 `mobs/*.json` 要有同一組 id，否則連不上。這和武器的規則一樣。（照物品的規則推論，實作時查證 Forge 的註冊表比對）
- **存檔裡有已經不存在的生物種類**時，Minecraft 載入時會略過那隻，並在 log 寫一行。結果是那隻怪物從世界上消失。

## 3. 變更邊界（提案，第一輪 Q1～Q3 要確認）

### 分層（新增和修改的部分）

```
core/mob/         MobLibrary（不可變快照，和 EquipmentLibrary 同形狀）、MobReloadReport
                  Behavior 加兩種：NeutralBehavior(speed)、LeapBehavior()
                  MobDefinition 多一個 name；行為組合的檢查（攻擊和中立只能選一種；衝刺要搭配其中一種）
data/             （純 Java＋Gson，不 import Minecraft）
                  MobJson（檔案格式 v1；行為的讀寫查對照表）、MobStore／MobFolder（config/mobarmory/mobs/）、
                  MobLoader、MobEditor（先寫檔，再改遊戲）、DefaultMobs（jar 的 mobarmory/default_mobs/*.json）、MobSync
                  （原子寫檔：沒有另外抽 AtomicFiles，沿用 main 上已經有的 JsonFolder，武器、數值、combo、怪物共用）
web/              MobAdmin（網頁唯一認得的怪物介面）、MobApi（/api/v1/mobs*）
forge/            MobStartup（建構時讀檔，像 WeaponStartup）、ServerMobAdmin（Adapter：切到伺服器執行緒、
                  改完套用到世界上的怪物、送同步封包）、MobSyncChannel（mobarmory:mobs 頻道）、
                  NeutralGoalBinder、LeapGoalBinder、ServerThread（從 ServerWeaponAdmin 搬出來，兩個 admin 共用）
resources/        mobarmory/default_mobs/brute.json、skirmisher.json；mobarmory/web/ 的怪物分頁
```

遊戲資料夾（不進 git）：

```
config/mobarmory/
  weapons/…        （R2b，不變）
  textures/…       （R2b，不變）
  mobs/brute.json  一種怪物一個檔，檔名就是 id
  web.json         （R6，不變）
```

### 檔案格式（第 1 版，欄位名稱見 23 的 O1）

```json
{
  "format": 1,
  "name": "Brute",
  "look": { "body": "zombie", "scale": 1.2 },
  "stats": { "max_health": 40, "attack_damage": 4, "movement_speed": 0.25 },
  "equipment": { "mainhand": "heavy_sword" },
  "behaviors": [
    { "type": "wander", "speed": 1.0 },
    { "type": "attack_players", "speed": 1.0 }
  ]
}
```

- `body`：`zombie`、`husk`、`skeleton`、`stray`。
- `stats`：只能寫登記表裡有的數值；沒寫的用預設值（07 的 O4）。
- `equipment`：欄位 → 武器 id。武器不存在或欄位對不上時，生成時不拿那件，只寫 log 警告（07 第四輪）。
- `behaviors`（建議值，見 23 的 Q5）：
  - `wander`：閒晃。已經有。
  - `attack_players`：主動攻擊玩家，被打也反擊。已經有。
  - `neutral`：被打才反擊，不主動找玩家。新增。
  - `leap`：衝刺，撲向目標。新增。
  - 「奔跑」是 `attack_players`、`neutral` 的 `speed`：1.0 是原速，大於 1 追得比較快。

### 沿用武器的哪些東西

| 武器（R2b／R6） | 怪物（R4b） | 怎麼沿用 |
|---|---|---|
| `WeaponJson.parse`（嚴格 JSON、Windows BOM、錯誤寫出行和欄） | `MobJson` | **直接呼叫**，不複製 |
| `WeaponFormatException(field, reason)`、`WeaponLoadException(problems)` | 同上 | **直接用**。名稱有 Weapon，但內容是通用的「哪個欄位、什麼問題」；要不要改名留給架構師 |
| `EquipmentLibrary`（啟動時決定哪些是物品；定義整份換；待重開；檔案已刪除） | `MobLibrary` | **同形狀，另一個類別**。不抽共用的泛型基底，理由見下方 SOLID 表 |
| `WeaponStore`／`WeaponFolder`＋記憶體版 | `MobStore`／`MobFolder`＋記憶體版 | 另一個小介面（怪物沒有貼圖，I）；原子寫檔原本要抽成 `data/AtomicFiles`，實際沿用 main 上的 `data/JsonFolder`，兩邊共用（見 23 號「合併後的調整」） |
| `WeaponLoader`（一次列出所有檔案的問題） | `MobLoader` | 同形狀 |
| `WeaponEditor`（先寫檔，成功才改遊戲；後存蓋先存） | `MobEditor` | 同規則 |
| `DefaultWeapons.installIfMissing` | `DefaultMobs.installIfMissing` | 同規則：`mobs/` 資料夾不存在才放預設檔，不覆蓋玩家的檔案 |
| `WeaponAdmin`／`ServerWeaponAdmin` | `MobAdmin`／`ServerMobAdmin` | 同形狀。「切到伺服器執行緒、等 10 秒、還原例外」**抽成** `forge/ServerThread`，兩個 admin 共用 |
| `WeaponApi` | `MobApi` | 同樣的錯誤格式 `{"error", "message", "fields"}`、同樣的狀態 `active`／`pending_restart`／`file_missing` |
| `WeaponSyncChannel`（登入時、每次改動後送全部定義） | `MobSyncChannel` | **另開頻道** `mobarmory:mobs`，同樣的時機和做法；不改武器的頻道 |
| `ReloadCommand`、網頁「從檔案重新載入」 | 同一個指令、同一顆按鈕 | 重新載入改成一串「可以重新載入的資料」（武器、怪物） |
| `AdminHttpServer` 的安全檢查（只綁 127.0.0.1、Host、Origin、token） | 不變 | `/api/v1/mobs*` 交給 `MobApi`，其他照舊 |

### 網頁 API（加在 v1）

| 方法與路徑 | 用途 | 回應 |
|---|---|---|
| `GET /api/v1/mobs` | **架構師的唯讀清單**。R4b 不另做一份清單，只在每一筆加 `status` 和檔案格式的 `mob` | 200 |
| `GET /api/v1/mobs/{id}` | 一種怪物 | 200／404 |
| `PUT /api/v1/mobs/{id}` | 改名稱、大小、數值、裝備、行為；後存的蓋掉先存的 | 200／400／404 |
| `POST /api/v1/mobs` | 新增怪物，存成 JSON，重開後才能用 | 202（待重開）／400 |
| `GET /api/v1/mob-options` | 表單用：身體清單、行為種類、速度上下限（不叫 `/mobs/options`，避免和 id `options` 撞名） | 200 |
| `POST /api/v1/reload` | 同一顆按鈕，武器和怪物都重新載入。回應保留原本的欄位，多一段 `mobs` | 200／400 |

### 和架構師段落 A 的接點

- 架構師正在把 `GET /api/v1/mobs` 改成唯讀清單，也在改網頁的怪物分頁。R4b 只接在後面：清單的資料來源從啟動時的 `MOBS.catalog()` 改成 `MobAdmin.view()`，**原本的欄位全部保留**，每一筆再加 `status` 和 `mob`。分頁上加「編輯」和「新增怪物」。
- 架構師的唯讀清單放在哪個類別（現在 `/api/v1/mobs` 在 `WeaponApi` 回 501），合併後照實際位置調整：怪物的路由搬進 `MobApi`，輸出格式不變。
- 架構師今晚也在改 `WeaponSyncChannel`（送 PNG 前跑 `PngCheck`）、`ClientTextures`。R4b **不碰這兩個**，怪物的同步另開頻道。
- 要動到的 R2b 檔案都只是搬移、行為不變（見下方四件事），合併架構師的段落 A 之後才動，先避開衝突。

### 四件事

| 項目 | 內容 |
|---|---|
| 可以改的模組 | 新建：上面分層裡的 `core/mob`、`data/`、`web/`、`forge/` 新類別；`resources/mobarmory/default_mobs/`<br>修改 `core/mob`：`MobDefinition` 加 `name` 和行為組合的檢查、`Behavior` 的速度加上限（23 的 O7）<br>修改 `forge/`：`ArmoryMob`（用 id 向 `MobLibrary` 拿現在的定義；生成和載入時套用基礎數值；名稱讀定義）、`MobEntityFactory`（依 `MobLibrary.registered()` 註冊；生怪蛋借原版外觀）、`ArmorySpawnEggItem`（名稱讀定義）、`BehaviorGoals`（加 2 行）、`MobArmoryClient`、`MobArmoryMod`（接線）<br>網頁：`index.html`、`app.js`、`style.css` 的怪物分頁和新增怪物表單<br>刪除：`content/ModMobs`（改成 `DefaultMobs`）、語系檔的 `entity.mobarmory.brute`／`skirmisher`、`items/brute_spawn_egg.json`、`items/skirmisher_spawn_egg.json`<br>`build.gradle`：遊戲內測試啟動前，把預設怪物檔和一個測試用怪物檔放進 `run-gametest/config/mobarmory/mobs/`（證明 T2）<br>測試：R4 的測試只改「怎麼拿到狂戰士」，預期的數字一個都不改 |
| 要動到的 R2b 檔案（只搬移，行為不變；不同意請劃掉） | `ServerWeaponAdmin`：改用 `ServerThread`<br>`WeaponFolder`：原本要改用 `AtomicFiles`，實際沒有動，因為架構師和 combo 已經改用 `JsonFolder`<br>`ReloadCommand`：改成收一串可以重新載入的資料<br>`AdminHttpServer`：`/api/v1/mobs*` 交給 `MobApi`<br>`WeaponApi`：拿掉怪物的路由，`/api/v1/reload` 的回應多一段 `mobs`<br>既有的武器測試（`WeaponApiTest`、`LiveEditGameTests`、`WebAdminGameTests` 等）要全部照舊通過，數字不改 |
| 不准動的相依 | `core/stat`、`core/equipment`（含 `EquipmentLibrary`）、`core/combat` 的公開介面和規則<br>武器檔案格式 `WeaponJson`<br>`WeaponSyncChannel`、`ClientTextures`（架構師今晚在改）<br>`EquipmentStatApplier`、`CombatEffectListener`、`EntityStatBinder`、`MobEquipmentSource` 的行為<br>D1 的 `HitFeedbackListener`；R3 combo（`feature/r3-combo`）<br>07 已確認的 R4 規則（O3 空行為是木樁、O5～O8、第四輪的裝備警告），除非 23 另外決定<br>Minecraft 和 Forge 本身（不用 Mixin、不用 Access Transformer）；不加外部相依<br>`java_minecraft_model/mod/`、`mod-damage-hud/`、`PROGRESS.md`、`transcripts/`、`docs/boundary/README.md` |
| 保留或新建的抽象介面 | 保留：`Behavior`、`GoalBinder`、`MobEquipmentSource`、`WeaponAdmin`、`WeaponStore`<br>新建：`MobAdmin`（web 層唯一認得的怪物介面）、`MobStore`（資料夾版、記憶體版）、可以重新載入的資料（指令和網頁按鈕共用） |
| 新功能放哪、用什麼形狀 | 新怪物：`config/mobarmory/mobs/` 加一個 JSON，或在網頁新增，重開遊戲<br>改既有怪物：改 JSON 後 `/mobarmory reload`，或在網頁改<br>新行為：`core/mob` 一個 record、`forge` 一個 binder、`BehaviorGoals` 一行、`MobJson` 對照表一行、網頁一個控制項 |
| 改一處會牽動幾個檔案 | 新增一種怪物：**1 個 JSON，0 行 Java，0 個資源檔**（T2）<br>改一種怪物的數值：**1 個 JSON**（或網頁一次存檔）<br>新增一種行為：**5**（record、binder、`BehaviorGoals`、`MobJson`、`app.js`） |

### SOLID 判斷（第一輪 Q1 要確認）

| 看到的徵兆 | 原則 | 處方 |
|---|---|---|
| 怪物寫在 Java（`content/ModMobs`）：玩家加一隻要改 Java、重新編譯 | O 開放封閉 | 怪物定義搬到 JSON 檔，`ModMobs` 改成 jar 裡的預設檔；和 R2b 刪掉 `ModEquipment` 一樣 |
| `MobEntityFactory` 註冊時把一份定義包進建構子，之後網頁改了也到不了怪物身上 | D、S | 怪物用 id 向 `MobLibrary` 拿**現在的**定義，和武器向 `EquipmentLibrary` 拿一樣 |
| 把怪物的清單、存檔、路由塞進 `WeaponAdmin`／`WeaponApi` | I、S | `WeaponAdmin` 的使用者（重新載入指令、網頁生命週期、測試用的實作）會被迫認得怪物。改成新建 `MobAdmin`、`MobApi` |
| 兩個 admin 各抄一份「切到伺服器執行緒、等 10 秒、還原例外」 | S（容易錯的程式重複） | **這次抽**：`forge/ServerThread`，`ServerWeaponAdmin` 只改呼叫，既有遊戲內測試守住行為 |
| 原子寫檔要再抄一份 | S | 原本寫「這次抽成 `data/AtomicFiles`」。實際沒有另外抽，沿用 main 上已經抽好的 `data/JsonFolder`，`WeaponStore` 的契約測試守住行為 |
| `/mobarmory reload` 只認得武器，每多一種資料就要改一次指令 | O | **這次抽**：指令和網頁按鈕都走一串「可以重新載入的資料」，加一種資料只在接線加一項 |
| 行為的 JSON 讀寫寫成一個大 switch | O | 行為種類 → 讀寫方式的對照表，和 `BehaviorGoals` 的 binder 對照表同一種做法 |
| 中立、衝刺寫成 `AttackPlayersBehavior` 裡的 `if` 旗標 | O、S | 各自一個 `Behavior` record 和 `GoalBinder`；`AttackPlayersGoalBinder` 一行都不改 |
| 通用的 `DefinitionLibrary<I, D>`：武器之後，怪物是第二個例子，可以抽 | （O，可選） | **這次不抽**（23 的 Q2 B 才抽）。兩邊「只能重開才改的欄位」不同（武器是欄位，怪物是身體），驗證也不同；要改 R2b 測過的核心類別，架構師今晚也在 main 上改。第三種資料出現時再抽 |
| 核心的規則要開遊戲才能測 | D | `core/mob`、`data/` 不 import Minecraft；`CoreBoundaryTest` 自動守住 |

L（里氏替換）由契約測試保證：

- `MobStore` 的兩個實作跑同一組。
- `MobAdmin` 的兩個實作：網頁 API 的測試用記憶體版，遊戲內測試用 `ServerMobAdmin`。
- 所有預設怪物檔、測試用怪物檔跑同一組怪物契約。
- 所有 `GoalBinder`（含中立、衝刺）跑同一組 binder 契約。

### Pattern 選擇（第一輪 Q2 要確認）

會變的是：怪物清單（玩家加）、每種怪物的定義（網頁改）、行為種類、資料種類（武器、怪物）。

| 選項 | 形狀 | 優點 | 缺點 |
|---|---|---|---|
| **A（建議）** | 和武器同一套形狀，平行一組怪物類別：快照式 `MobLibrary`、`MobAdmin`＋`ServerMobAdmin`（Adapter）、`MobSyncChannel`。沿用 R4 的 Factory（生物種類）和 Strategy（行為）。只抽三個純搬移的共用零件：`ServerThread`、原子寫檔（實際沿用 `JsonFolder`）、可以重新載入的資料 | 武器的程式幾乎不動，已通過的測試當保險；看過武器那套的人一看就懂；約 1～1.5 天 | `MobLibrary` 和 `EquipmentLibrary` 有一段形狀相同的程式（約 100 行） |
| B | 泛型化：`DefinitionLibrary<I, D>`、`DefinitionStore`、`DefinitionAdmin`，武器和怪物都是它的實作，一個 API 處理兩種 | 重複最少；第三種資料最省 | 要改 R2b 的核心類別和全部武器測試，和架構師今晚的修改衝突；多約 1 天，風險高 |
| C | 直接寫進武器的類別：`WeaponAdmin` 加怪物的方法，`WeaponFolder` 也讀 `mobs/` | 今天最快 | 每個認得武器的地方都被迫認得怪物（I、S）；之後要拆很痛 |

## 4. 實作順序（等決定、等架構師的段落 A 進 main 之後）

每一步做完都跑一次測試，才進下一步。

1. 把 main（含架構師的段落 A）合進 `feature/r4b-mob-admin`，跑全部測試。
2. 抽共用零件：`ServerThread`、可以重新載入的資料；原子寫檔沿用 main 上的 `JsonFolder`，沒有另外抽 `AtomicFiles`。只搬移，跑既有測試確認沒壞。
3. `core/mob`：`MobDefinition` 加 `name` 和行為組合的檢查；`NeutralBehavior`、`LeapBehavior`；`MobLibrary`。附單元測試。
4. `data/`：`MobJson`、`MobStore`／`MobFolder`、`MobLoader`、`MobEditor`、`DefaultMobs`；兩個預設檔的內容和 R4 的狂戰士、遊擊兵**數字完全一樣**。附契約測試。
5. `forge/`：`MobStartup` 在建構時讀檔；`MobEntityFactory`、`ArmoryMob` 改用 `MobLibrary`；刪掉 `ModMobs`、兩行語系、兩個生怪蛋外觀檔。**R4 的測試只改怎麼拿到狂戰士，全部照舊通過。**
6. `forge/`：`NeutralGoalBinder`、`LeapGoalBinder`，附 binder 契約測試。
7. `web/`、`forge/`：`MobAdmin`、`MobApi`、`ServerMobAdmin`（改完套用到世界上的怪物）、`MobSyncChannel`。
8. 網頁：怪物分頁的編輯表單、新增怪物表單。
9. 系統測試（GameTest）、行為報告 [28](28-r4b-mob-admin-behavior-report.md)（24～27 已被架構師的段落 E 用掉）。

## 5. 測試計畫

| 層級 | 內容 |
|---|---|
| Component | `MobJson`（每個欄位的錯誤、存讀一致）、`MobDefinition` 的行為組合檢查、`MobLibrary`（整份換、待重開、檔案已刪除、身體要重開）、`MobSync`、`MobApi`（每條路由、錯誤格式） |
| Module | `MobLoader`（一次列出所有問題）、`MobEditor`（先寫檔、寫檔失敗不改遊戲、後存蓋先存、新增）、`DefaultMobs`（只在沒有資料夾時放） |
| 契約 | 怪物契約跑過所有預設檔和測試用檔；`MobStore` 兩個實作；所有 `GoalBinder`（含中立、衝刺）；預設檔的狂戰士、遊擊兵和 R4 的數字相同 |
| System | GameTest：**只有 JSON 的測試用怪物**（`run-gametest/config/mobarmory/mobs/` 裡放的）可以生成、數值和裝備照檔案（T2）；網頁存檔改狂戰士的攻擊力，世界上那隻立刻變；改行為後新生成的才變；中立的不主動攻擊、被打才反擊；`reload` 讀到手改的檔；同步封包的內容；R4 既有的遊戲內測試照舊通過 |
| 手動 | `runClient`＋瀏覽器：在網頁新增一種怪物，重開，用生怪蛋生出來；改狂戰士的數值和行為。這一輪不做，實作完再做 |
