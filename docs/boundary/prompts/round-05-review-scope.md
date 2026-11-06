# 第 5 輪 prompt：依審查建議縮小 R2b＋R6 的範圍

- 日期：2026-11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：開發 session（Claude，`mc-dev`），在 `mod/` 的 main 上工作
- 使用的 skill：`boundary-first`（確認點的答案改了，第 2、3 步依新答案重寫）

## 使用者的決定

1. 第一次（第 4 輪）：使用者在開發 session 回「全部照建議」。開發 session 照這個版本做完了 R2b 和 R6（commit `a6bd8bc`～`905554d`）。
2. 審查員的 review-01 指出：照第一版做，遠端客戶端的名稱、tooltip、貼圖會和伺服器不同（風險 4），建議縮小範圍（第 6 節建議 2）。
3. 使用者在主 session 確認：「照審查建議版」。主 session 說明了兩版的差別、以及一般 Forge 模組的做法（貼圖放進 jar、數值由伺服器同步）。
4. Q7 再調整，使用者原話：「我們只要做到學長說的可以上傳武器，並且還有數值的部分才是重點。」→ 網頁有新增武器的表單，但不上傳 PNG。

最終答案記在 [10](../10-weapon-data-web-open-questions.md) 的「決定總表（最終版）」。

## Prompt 原文（監控 session 的整理，節錄）

> **10 號的答案**：Q1 A、Q2 A、Q3 B（劃掉 `GeneratedWeaponPack`；補上「武器定義同步到客戶端」的小封包；貼圖 PNG 放進 jar 的 `assets/mobarmory/textures/item/`；「不能動」加上 `content/ModStats`）、Q4 A、Q5 B（INVENTORY 建議的 7 張放進 jar 並 commit，加作者標註；網頁換外觀只能從「原版物品＋這幾張」選，不上傳）、Q6 A、Q7「新增武器表單，不上傳 PNG」；O1～O16 照建議，例外 O10（後存的蓋掉先存的，不做版本號和 409）、O11（外觀只在內建清單裡切換，需要重新載入資源就自動做，做不到就退回 F3+T 並寫進報告）、O15（7 張 commit 進 repo，加作者標註）。
>
> **範圍和順序**：先做 R2b（JSON 武器、內建貼圖、定義同步），每一步都跑測試，既有測試的預期數字一個都不改。再做 R6 最小版（列出武器、改數值、從內建清單換外觀、新增武器、寫回 JSON；`/api/v1/mobs` 先回 501）。R2b、R6 各自完成時都先停下來回報。
>
> **和其他 session 的關係**：R4 恢復了（`feature/r4-mobs`），它會用字串 id 從 `EquipmentCatalog` 查裝備，也會在 `ModStats` 加最大生命值。合併順序是 R2b 先合進 main，所以「用 id 查裝備定義」的 API 要保持穩定，寫進行為報告。文件編號：14 是 R4 的行為報告，15 是 R2b＋R6 的行為報告，16 是 D1，17～20 是 R3 combo。`docs/boundary/README.md` 的索引由 `mc-dev` 維護。
>
> 如果已經照「全部照建議」做了執行時資源包之類的程式，請刪掉，並在回報裡說明。
>
> 不要為了武器另外加特效或額外規則。之後遇到「要不要多做某個效果」的問題，預設選「不做」，在報告裡記一行就好。

## 給自己的 prompt

> 先改文件，再改程式。
>
> - 10：寫下決定的經過（全部照建議 → 照審查建議版 → Q7 調整，附使用者原話）和最終的決定總表。
> - 08、09：依最終版重寫有變的部分；08 加一節「給其他模組用的穩定介面」。
> - 行為報告改名為 15，README 索引加上文件編號表。
>
> 程式（R2b）：
>
> 1. 7 張貼圖放進 `assets/mobarmory/textures/item/`，每張配一份模型和物品模型定義（`items/look/<名稱>.json`），旁邊放 `CREDITS.md`。`EquipmentLook.Texture` 只接受這 7 張（`BuiltInTextures`），寫錯時列出可以用的名稱。
> 2. 刪掉第一版的執行時資源包和上傳貼圖：`GeneratedWeaponPack`、`WeaponAssets`、`ClientResourceReload`、`WeaponStore` 的貼圖方法、`WeaponEditor.saveTexture`、API 的 `PUT /api/v1/textures`、網頁的上傳。放回 4 件預設的物品模型檔。
> 3. 外觀：物品的預設 `item_model` 在註冊時固定，所以讓每把武器的物品堆疊帶著「目前外觀」的 `item_model`。在玩家背包每個 tick 對齊、創造模式分頁的物品先設好、重算持有者時也對齊生物手上的。不重新載入資源。
> 4. 定義同步：`WeaponSync`（純 Java：定義 ↔ id → JSON，客戶端套用）＋ Forge SimpleChannel 封包。玩家登入時送一次，每次改動後送給所有人。單人遊戲的客戶端和伺服器共用同一份定義，不重複套用。
> 5. 每一步跑 `gradlew clean build runGameTestServer`；既有測試的預期數字不改。完成後本機 commit、更新 PROGRESS.md，停下來回報。
>
> 不碰 `content/ModStats`、`mod-damage-hud/`、`mod-r4-mobs/`；不 push。
