# 傷害顯示第 1 輪 prompt：邊界草案和待決定問題

- 日期：2026-11-06
- 下指令的一方：監控 session（主 session）
- 執行者：傷害顯示 session（Claude），在 worktree `mod-damage-hud`、分支 `feature/damage-hud` 裡工作
- 使用的 skill：`boundary-first`（第 1～3 步和確認點）

## Prompt 原文（監控 session 的整理）

> 任務：用 `boundary-first` skill 在 Mob Armory 模組加上「砍怪傷害顯示」。玩家（或任何生物）打中生物時，遊戲裡要看得到：這一下造成多少傷害、有沒有暴擊、有沒有被迴避。目的是之後的示範影片要清楚看出「拿不同武器、或改了數值以後，砍怪的傷害不一樣」。
>
> **工作位置**：`mod/` 由開發 session 同時在做 JSON 武器設定和網頁後台，不要改 `mod/` 裡的任何檔案。先建立自己的 worktree：`git -C java_minecraft_model/mod worktree add ../mod-damage-hud -b feature/damage-hud`，之後所有修改、build、測試都只在 `mod-damage-hud/` 做。只在本機 commit 到 `feature/damage-hud`，不要 push、不要合併到 main。不要改 `PROGRESS.md`，也不要碰 `transcripts/`。
>
> **流程**：
>
> 1. 先讀 `01-boundary-note.md` 和 `04-behavior-report.md`（分層、`CombatEffectListener`、`StatEffect`、GameTest 的做法），再查 Forge 26.3 的原始碼：哪個事件拿得到扣完護甲後的最終傷害、怎麼知道這一下被迴避；有哪些顯示方式（TextDisplay 浮字、粒子、action bar、聊天訊息）。
> 2. 寫三份文件，編號用 11～13（08～10 留給開發 session）：`11-damage-display-boundary-note.md`、`12-damage-display-given-when-then.md`、`13-damage-display-open-questions.md`。問題清單比照 `07-r4-open-questions.md`，每題第一個選項是建議值，至少要問：顯示方式、誰看得到、顯示什麼、只顯示玩家造成的傷害還是所有傷害、多人時怎麼同步。
> 3. 問題寫完就停下來回報。**使用者回覆以前，不要寫功能程式。**
> 4. 收到答案後分步實作：純 Java 放 `core/`（`CoreBoundaryTest` 要繼續通過），Forge 的轉接放 `forge/`；不改 R1／R2 已確認的公開介面和規則；加單元測試和 `src/gametest` 的系統測試；`gradlew clean build runGameTestServer` 全過（基準：單元 106、遊戲內 18）；最後寫行為報告。

## 這一輪的產出

- [11-damage-display-boundary-note.md](../11-damage-display-boundary-note.md)：需求翻譯、Forge 26.3 原始碼查證、顯示方式比較、變更邊界、實作順序、測試計畫、技術風險
- [12-damage-display-given-when-then.md](../12-damage-display-given-when-then.md)：成功條件、H1～H10、B1～B20
- [13-damage-display-open-questions.md](../13-damage-display-open-questions.md)：設計 3 題、商業規則 5 題、其他 10 項

功能程式還沒寫，等使用者回覆 13 的問題。
