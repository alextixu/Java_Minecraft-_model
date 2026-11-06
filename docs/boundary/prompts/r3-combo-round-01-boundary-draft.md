# R3 combo 第 1 輪 prompt：邊界草案和待決定問題

- 日期：2026-11-06
- 下指令的一方：監控 session（主 session）
- 執行者：傷害顯示 session（Claude），在 worktree `mod-damage-hud`、新分支 `feature/r3-combo`（從 `feature/damage-hud` 的 `ed05c6b` 分出）
- 使用的 skill：`boundary-first`（第 1～3 步和確認點）

## Prompt 原文（監控 session 的整理）

> D1 收到了（`ed05c6b`，單元 160、遊戲內 27）。接下來請開始 R3 combo 的 boundary-first 第 1～3 步，這輪只寫文件和問題清單，不寫功能程式。
>
> **位置**：在你的 worktree 從 `feature/damage-hud`（`ed05c6b`）開新分支：`git switch -c feature/r3-combo`。`feature/damage-hud` 停在 `ed05c6b`，不要再動它。一樣只在本機 commit，不 push、不合併。
>
> **範圍**：最小範圍見 `reviews/review-01-requirements-gap.md` 第 3 節 P1-3：
>
> - 兩種輸入：走路揮擊、奔跑揮擊。從 `StatReader` 讀移動速度，或判斷是否在疾跑。
> - 在一個時間窗內打兩下，A→B 和 B→A 要觸發不同的 combo。
> - combo 定義是資料，效果用 Strategy，出招狀態存在伺服器。
> - combo 名稱顯示在你剛做好的 action bar。
>
> 使用者的原則：學長要求的才做，不另外加特效或額外規則。combo 的效果先用最簡單的，例如傷害倍率；要不要加擊退、緩速等，放在問題清單裡讓使用者選。
>
> **產出**：`17-r3-combo-boundary-note.md`、`18-r3-combo-given-when-then.md`、`19-r3-combo-open-questions.md`（格式比照 07，每題第一個選項是建議值）、prompt 記錄。寫完就停下來回報。之後的行為報告是 20。
>
> **注意**：mc-dev 的 R2b 已經在 main 上做完，會先合進 main：`content/ModEquipment` 會換成 JSON 定義，裝備要用 id 查；`MobArmoryMod` 也有改動。文件裡的「變更邊界」請照這個前提寫。

## 執行者做的查證

- 學長的需求原文從審查報告引用：B2「武器有 combo：出招順序不同就是不同 combo」、A2「combo 要和數值結合，例如奔跑攻擊和走路攻擊是不同 combo，所以要讀得到移動速度」。沒有讀 `transcripts/`。
- R2b 的介面：只用 `git show main:...` 讀 `mod/` 的 main（沒有改 `mod/`）。`MobArmoryMod` 改成從 `config/mobarmory/` 讀武器（`EquipmentLibrary`、`WeaponFolder`），`CombatEffectListener` 在 main 上沒有被改。
- Forge 26.3：疾跑攻擊打中後，`Player.causeExtraKnockback` 會呼叫 `setSprinting(false)`，拿掉移動速度上 ×1.3 的疾跑修飾（`LivingEntity.SPEED_MODIFIER_SPRINTING`），所以「這一下是不是奔跑」要在 `AttackEntityEvent` 讀。

## 這一輪的產出

- [17-r3-combo-boundary-note.md](../17-r3-combo-boundary-note.md)：需求翻譯、平台查證、各裝備的移動速度、變更邊界、SOLID、Pattern、實作順序、測試計畫、技術風險
- [18-r3-combo-given-when-then.md](../18-r3-combo-given-when-then.md)：成功條件、H1～H7、B1～B20
- [19-r3-combo-open-questions.md](../19-r3-combo-open-questions.md)：設計 3 題、商業規則 4 題、其他 12 項

功能程式還沒寫，等使用者回覆 19 的問題。
