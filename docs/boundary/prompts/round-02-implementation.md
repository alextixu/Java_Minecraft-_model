# 第 2 輪 prompt：依確認後的邊界分步實作 R1 ＋ R2

- 日期：2026-11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：開發 session（Claude），在 `mod/` 裡工作
- 使用的 skill：`boundary-first`（第 4～6 步：分步實作、三層測試加契約測試、行為報告）

## Prompt 原文

> 使用者已回覆 03-open-questions.md：全部照建議。使用者原話：「Use all recommended」。
>
> 確定的答案：
> - Q1 A、Q2 A、Q3 A、Q4 A、Q5 A、Q6 A、Q7 A
> - O1～O9：全部照建議值
> - 命名：mod id `mobarmory`，顯示名稱 Mob Armory，主類別 `MobArmoryMod`
> - Java package：`io.github.alextixu.mobarmory`（使用者的 GitHub 帳號是 `alextixu`）
>
> 請做：
> 1. 把答案記到 `03-open-questions.md`（狀態改成「已決定」，每題註明選項），把 01、02 的「草案」狀態改成「已確認」。
> 2. 照 01 第 4 節的實作順序，從第 0 步（改名、刪 MDK 範例）做到第 7 步。每一步做完都跑測試，通過才進下一步，並在本機 commit。不要 push。
> 3. 每一步用的 prompt 存到 `docs/boundary/prompts/`，最後產出行為報告。
> 4. GameTest 如果跑不起來，照 01 第 5 節的備案：改用 `/mobarmory stats` 在遊戲內手動驗證，並在報告裡註明。
> 5. 每完成一步就更新 PROGRESS.md（R1、R2、T1 完成就打勾）。
>
> 做到一半如果遇到需要人決定的新問題，跟上次一樣：先做完不受影響的部分，再把問題清單附上建議回報給我。全部做完後回報：完成的步驟、各層測試結果、T1 是否通過、`runClient` 裡手動驗證的結果。

## 每一步給自己的 prompt

每一步開始前，開發 session 依 [01 第 4 節](../01-boundary-note.md#4-實作順序) 把該步的邊界寫成一段 prompt，只在這個邊界裡動手。各步的 prompt 記在 [round-02-steps.md](round-02-steps.md)。
