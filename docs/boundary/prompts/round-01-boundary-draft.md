# 第 1 輪 prompt：R1 ＋ R2 邊界草案

- 日期：2026-11-05～11-06
- 下指令的一方：監控 session（轉達使用者的決定）
- 執行者：開發 session（Claude），在 `mod/` 裡工作
- 使用的 skill：`boundary-first`

## Prompt 原文

> 第 2 階段開始：用 boundary-first 定義系統邊界，並實作週五雛型。
>
> 使用者已確認：學長要求的 AI coding 規格就是 `boundary-first` skill。所有功能開發都要透過它，在 `mod/` 裡進行。
>
> 範圍與順序（學長希望週五 11/6 先看雛型）：
> 1. 這一輪只做雛型範圍：R1 可擴充的數值系統，加上 R2 武器／護甲改變玩家數值（例：重武器降低移動速度），對應驗收測試 T1。
> 2. 之後的順序（這一輪先不做）：R4 自訂怪物（T2）→ R3 combo → R5 多人同步 → R6 網頁與伺服器 API。
>
> 規則：
> - 呼叫 `boundary-first` skill，照它的流程走：什麼會變 → 用 SOLID／Design Pattern 標出 AI 可以改的範圍 → Given/When/Then → 分步實作 → 三層測試加契約測試 → 行為報告。
> - skill 需要「人」決定邊界時，不要替使用者決定。把要決定的問題整理成清單（每題附上你的建議選項），先結束這一輪回報給我，我轉給使用者，再把答案傳回來。可以先完成不需要人決定的部分。
> - MDK 的 `examplemod`（mod id、package、類別名）要改成正式名稱，請在回報裡提出建議名稱，跟邊界問題一起讓使用者決定。
> - 邊界文件、Given/When/Then、每一步用的 prompt、行為報告都存到 `mod/docs/boundary/`。這些是交付物，要進 git。
> - 可以在本機 commit，但不要 push。
> - 每完成一步就更新 PROGRESS.md。

需求來源：會議重點整理第 3、4 節（題目二：自訂怪物與武器系統），以及 PROGRESS.md 的 R1～R6、T1～T2。

## 呼叫 skill 時給的參數

> 在 Forge 26.3 模組上做週五雛型：R1 可擴充的數值系統 ＋ R2 武器／護甲改變玩家數值（驗收 T1：重武器降低移動速度）。需要人決定的邊界要整理成問題清單，不要替使用者決定。

## 這一輪的產出

- 讀過 Forge 26.3-66.1.0 原始碼，確認 Attribute、換裝事件、受傷事件、暴擊事件、GameTest 的介面（結論寫在 01 第 2 節）
- [01-boundary-note.md](../01-boundary-note.md)：第 1、2 步草案
- [02-given-when-then.md](../02-given-when-then.md)：第 3 步草案
- [03-open-questions.md](../03-open-questions.md)：確認點的問題清單
- 不需要人決定、先做好的部分：`build.gradle` 加入 JUnit 6.1.3 並驗證可以執行（用臨時測試確認後已刪除）；`.gitattributes` 固定換行符號
- **還沒實作任何功能**，等確認點的答案
