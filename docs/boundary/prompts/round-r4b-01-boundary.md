> **11/08 使用者已確認**：19、20 號（combo）、23 號（怪物）、26 號（數值）的夜間決定，使用者回覆「全部照建議」。下面是當時的紀錄，原文不改。

# R4b 第 1 輪 prompt：怪物資料檔＋網頁設定怪物，只寫邊界（A6、A7）

- 日期：2026-11-07 夜間
- 下指令的一方：監控 session（轉達使用者睡前的「R4 也開始做」和 11/07 的主軸）
- 執行者：R4 開發 session（Claude），在 worktree `mod-r4-mobs/`，分支 `feature/r4b-mob-admin`（從 main `061e7a9` 開）
- 使用的 skill：`boundary-first` 第 1～3 步（寫完停下來，等使用者決定）
- 產出：[21](../21-r4b-mob-admin-boundary-note.md)、[22](../22-r4b-mob-admin-given-when-then.md)、[23](../23-r4b-mob-admin-open-questions.md)。沒有寫功能程式

## 使用者的話

- 睡前：「R4 也開始做」。
- 夜間授權：「繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收」。
- 11/07 的主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。

## Prompt 原文（監控 session 的兩則訊息，排版和條列略整理，內容不變）

### 第一則：段落 D

> 【夜間段落 D：R4b 用網頁設定怪物，先寫邊界】
>
> R4 的基本功能（狂戰士、遊擊兵、T2）已經在 main。還沒做的是學長要求的這兩項：A6 網頁可以設定怪物行為，例如能不能奔跑、衝刺、會不會攻擊玩家、是不是中立。A7 初期怪物可以改數值。審查報告 review-02、review-03 的風險 5 也提過：武器可以用 JSON 和網頁新增，怪物卻要改 Java 再重開遊戲。
>
> 這一輪只寫邊界文件，不寫功能程式：
> 1. 從 main 的 `061e7a9` 開新分支：`git switch -c feature/r4b-mob-admin 061e7a9`。`feature/r4-mobs` 已經合進 main，不要再改它。
> 2. 先讀 main 上 R2b／R6 的做法，怪物盡量沿用同一套：武器 JSON（`data/`）、執行中替換（`EquipmentLibrary`）、網頁（`web/`、`WeaponAdmin`）、同步到客戶端（`WeaponSyncChannel`）。
> 3. 架構師今晚也在 main 上做：它正在把 `GET /api/v1/mobs` 改成唯讀清單，也在改網頁的怪物分頁。你的設計要接在這個唯讀清單後面，用來修改，不要另外做一套清單。它做完、合進 main 以後，我會通知你把 main 合進你的分支，到那時才開始寫程式。
> 4. 用 boundary-first 寫三份文件：21 邊界說明、22 Given/When/Then、23 問題清單（格式比照 07，每題第一個選項是建議值）。
> 5. 問題清單至少要問到：範圍；行為有哪些選項（攻擊玩家／中立／被打才反擊、能不能奔跑或衝刺、追擊距離）；已經在世界上的怪物要不要立刻套用（07 的 Q6 選 A）；多人同步；網頁畫面要怎麼做。照使用者的原則「學長要求的才做，不另外加特效」，建議值以最小範圍為準。
>
> 寫完就停下來回報。使用者在睡覺，主 session 會依夜間授權採用建議值，並記下來讓他早上確認，再通知你開始實作。不要 push，不要動 main。

### 第二則：補充方向

> 補充 R4b 的方向，這是使用者剛定下的主軸（原話見上）。
> 1. 重點是讓玩家能自己新增怪物，不是把現有怪物做得更強。怪物定義改成和武器一樣，放在 `config/mobarmory/mobs/<id>.json`，第一次啟動時自動建立狂戰士、遊擊兵兩個預設檔。新增怪物只要多放一個 JSON，重開遊戲就有。網頁要能列出怪物、修改既有怪物的數值和行為，也要能新增怪物（填表單，存成 JSON，標「待重開」）。
> 2. 能讓玩家設定的欄位以學長提的為準：外觀（原版人形模型加大小倍率）、數值（R1 的登記表）、手上的裝備（武器 id）、行為（攻擊玩家／中立、能不能奔跑或衝刺）。不另外做新特效或新 AI。
> 3. 盡量沿用武器那一套機制，不要另起爐灶。可以抽成共用介面的地方，在 SOLID 判斷裡寫出來。
> 4. T2 的證明改成：玩家只放一個 JSON（或在網頁上填表單）就多一種怪物，Java 一行都不用改。

第二則到的時候，三份文件還沒開始寫，所以直接照第二則的方向寫，沒有改寫舊版。

## 每一步給自己的 prompt 和 SOLID／Pattern 檢查

### 第 1 步：讀現況

Prompt：先讀 main 上 R2b／R6 的武器那一套和 R4 的怪物程式，再決定怪物要沿用哪些東西。重點看：

- 哪些東西是啟動時固定的、哪些可以在執行中換。
- 網頁、指令、同步各從哪裡進來。
- 架構師的唯讀清單會接在哪裡。

讀到的重點：

- `WeaponAdmin` 的 Javadoc 已經預告「Monsters (R4) will get a MobAdmin of the same shape」。
- `/api/v1/mobs` 現在在 `WeaponApi` 回 501，網頁的怪物分頁只有一顆查詢按鈕。
- `MobEntityFactory` 在註冊時把一份定義包進 `ArmoryMob` 的建構子，所以網頁改了也到不了怪物身上。
- 怪物的基礎數值是生物種類的預設屬性，啟動時固定。
- 渲染器是每種生物啟動時註冊一個，身體換了要重開。
- `ServerWeaponAdmin` 的「切到伺服器執行緒」、`WeaponFolder` 的原子寫檔、`ReloadCommand` 都只認得武器。
- 架構師的段落 A 在寫這份文件時還沒 commit（`mod/` 工作目錄乾淨，main 停在 `061e7a9`）。

### 第 2 步：邊界說明（21）

Prompt：照使用者的主軸，怪物搬到 JSON 檔、玩家可以新增；能設定的欄位只有學長提的四類；行為只用原版 goal。每一個武器的零件都要說清楚，對怪物是「直接用」「同形狀另寫」還是「抽成共用」。動到 R2b 的地方只能是搬移、行為不變，而且列出來讓使用者劃掉。

| 原則 | 判斷 |
|---|---|
| S | 怪物的清單、存檔、路由不塞進 `WeaponAdmin`／`WeaponApi`，另建 `MobAdmin`、`MobApi`。切執行緒、原子寫檔各只有一份，抽成 `ServerThread`、`AtomicFiles` |
| O | 怪物從 Java 搬到 JSON，加怪物 0 行 Java。重新載入改成一串「可以重新載入的資料」，加一種資料不用改指令。行為的讀寫用對照表，中立、衝刺是新的 `Behavior`＋`GoalBinder`，不加 `if` |
| L | `MobStore`、`MobAdmin` 各兩個實作跑同一組契約；所有怪物檔跑同一組怪物契約；所有 `GoalBinder` 跑同一組 binder 契約 |
| I | `MobStore` 不繼承 `WeaponStore`（怪物沒有貼圖）；`ReloadCommand` 和網頁按鈕只認得「可以重新載入」這一件事 |
| D | 怪物向 `MobLibrary` 拿現在的定義，不再把定義包死在生物種類裡；`core/mob`、`data/` 不 import Minecraft |
| Pattern | 沿用 Factory（生物種類）、Strategy（行為）、快照式 Library（同 `EquipmentLibrary`）、Adapter（`ServerMobAdmin`，同 `ServerWeaponAdmin`）。考慮過泛型的 `DefinitionLibrary<I, D>`：怪物是第二個例子，可以抽，但兩邊「只能重開才改的欄位」和驗證都不同，要改 R2b 測過的核心，又和架構師今晚的修改衝突，所以列為 Q2 B，這次不建議 |

### 第 3 步：Given / When / Then（22）

Prompt：先列「玩家自己加的一種怪物正常運作」的成功條件，再逐一破壞推出 B 系列。

- 數字沿用 R4，預設檔的狂戰士、遊擊兵和 R4 一模一樣，R4 的測試就是遷移的保險。
- T2 用一隻只有 JSON 的 `goblin` 證明。
- 武器已經有的規則（寫檔失敗、後存蓋先存、遊戲暫停）照抄，標「同武器」。
- 要人決定的標「請確認」，對到 23 的題號。

### 第 4 步：問題清單（23）

Prompt：格式比照 07。監控 session 指定的五個主題（範圍、行為選項、已在世界上的怪物、多人同步、網頁畫面）各一題，放在第二輪。其他細節放 O 表。建議值以最小範圍為準，學長沒提的（追擊距離、刪除按鈕、叫同伴）都建議不做。

## 決定（監控 session 的第三則訊息）

> R4b 的 21～23 收到。依使用者的夜間授權（「繼續把所有內容補齊 每做完一個段落就給審查審核並且自動批改 我睡醒再回來驗收」），主 session **採用 23 的全部建議值**：Q1～Q8 選 A，O1～O21 同意。
>
> 1. 在 23 記下決定：狀態寫「主 session 依使用者夜間授權採用建議值（2026-11-07），待使用者確認」，附上使用者的原話，包括主軸那句。prompt 也一起記。然後把 21～23 commit 起來。
> 2. 把最新的 main 合進 `feature/r4b-mob-admin`。main 現在是 `6a3e1fc`，比你分支的 `061e7a9` 多了架構師的這些改動：README；唯讀的 `GET /api/v1/mobs` 和怪物分頁（`012a2de`）；登出時還原武器、送 PNG 前先檢查（`6c01cfd`）；文件修正，`CREDITS.md` 搬了位置（`3789ffb`）；數值改成 JSON 定義（`6a3e1fc`，`config/mobarmory/stats/`）。你的設計要接在唯讀的怪物清單和 JSON 數值上面。如果 21～23 有地方要跟著調整，改完在 23 記一行就好，不用停下來問。
> 3. 照 boundary-first 分步實作。每一步都要寫 SOLID／Pattern 檢查，`clean build runGameTestServer` 全部通過才 commit。T2 的證明照 O20：只靠放一個 JSON 檔就能生出 goblin。
> 4. 寫行為報告 `docs/boundary/28-r4b-mob-admin-behavior-report.md`，要有「SOLID／Pattern 檢查」一節。24～27 已經被架構師的段落 E 用掉了。
>
> 不要 push，也不要動 main。做完回報 commit 和測試數字，主 session 會直接送審查小弟。

這一輪（第 1 輪）只到寫好 21～23 並記下決定。實作的 prompt 和每一步的 SOLID／Pattern 檢查記在 [round-r4b-02-implementation.md](round-r4b-02-implementation.md)。
