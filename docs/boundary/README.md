# 系統邊界文件（boundary-first）

這個資料夾記錄每個功能怎麼依 `boundary-first` 流程開發：先由人定義邊界，AI 只在邊界內實作，最後由測試證明行為正確。這些文件是交付物的一部分。

## 流程

1. 把需求翻成「什麼會變」
2. 寫下變更邊界：可以改哪些模組、哪些相依不准動、抽象介面、新功能放哪
3. 用 Given / When / Then 定義邊界行為
4. **確認點**：設計和商業規則由人決定
5. 分步實作，每步一個邊界，每步都跑測試
6. 三層測試（Component／Module／System）加契約測試
7. 跑到全部通過，產出行為報告

## R1 ＋ R2：可擴充數值系統、裝備改變玩家數值（週五雛型）

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [01-boundary-note.md](01-boundary-note.md) | 需求翻譯、平台限制、變更邊界、實作順序、測試計畫 | 已確認 |
| [02-given-when-then.md](02-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 已確認 |
| [03-open-questions.md](03-open-questions.md) | 確認點的問題與決定（設計 3 題、商業規則 4 題、其他 9 項、命名） | 已決定（全部照建議） |
| [prompts/](prompts/) | 每一輪交給 AI 的 prompt | 持續更新 |
| [screenshots/](screenshots/) | 遊戲內手動走查的截圖（行為報告第 6 節） | 2026-11-06 |
| [04-behavior-report.md](04-behavior-report.md) | 行為報告：結構摘要、各層測試數量、Given / When / Then 對照、沒有覆蓋的範圍、待決定的新問題 | 已產出（第 3 輪更新：單元 106/106、遊戲內 18/18） |

## R4：自訂怪物（驗收 T2）

R4 由另一個 session 在 `feature/r4-mobs` 分支做；行為報告是 14。

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [05-r4-boundary-note.md](05-r4-boundary-note.md) | 需求翻譯、平台限制、變更邊界、實作順序、測試計畫 | 草案（在 `feature/r4-mobs` 更新） |
| [06-r4-given-when-then.md](06-r4-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 草案（在 `feature/r4-mobs` 更新） |
| [07-r4-open-questions.md](07-r4-open-questions.md) | 待使用者決定的問題（設計 3 題、商業規則 4 題、其他 10 項） | 在 `feature/r4-mobs` 處理 |

## R2b ＋ R6：武器改成資料驅動、網頁後台

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [08-weapon-data-web-boundary-note.md](08-weapon-data-web-boundary-note.md) | 需求翻譯、平台限制、變更邊界、給其他模組的穩定介面、網頁 API、實作順序、測試計畫 | 已確認（最終版） |
| [09-weapon-data-web-given-when-then.md](09-weapon-data-web-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 已確認（最終版） |
| [10-weapon-data-web-open-questions.md](10-weapon-data-web-open-questions.md) | 確認點的問題與決定，含決定的經過（先「全部照建議」，後改「照審查建議版」） | 已決定 |
| [15-weapon-data-web-behavior-report.md](15-weapon-data-web-behavior-report.md) | 行為報告：第一版和最終版的差別、結構、給 R4／D1 的穩定介面、各層測試數量、Given / When / Then 對照、實測紀錄、沒有覆蓋的範圍、示範影片劇本 | 已產出（單元 237/237、遊戲內 29/29） |

## 文件編號

| 編號 | 內容 | 誰寫 |
|---|---|---|
| 01～04 | R1＋R2 | `mc-dev` |
| 05～07、14 | R4 自訂怪物（邊界、行為報告） | R4 的 session |
| 08～10、15 | R2b＋R6（邊界、行為報告） | `mc-dev` |
| 11～13、16 | D1 砍怪傷害顯示（邊界、行為報告） | `damage-hud` |
| 17～19、20 | R3 combo（邊界、行為報告） | 之後決定 |

本索引由 `mc-dev` 維護。

## 之後的功能

R5 多人同步，跑同樣的流程。

## 怎麼跑測試

```bash
./gradlew clean build runGameTestServer
```

- `build` 會跑單元測試（`core/`、`content/`，不用開遊戲）。
- `runGameTestServer` 會開一個無畫面的遊戲伺服器跑系統測試（實際執行的是 `runGametestGameTestServer`，載入 `src/gametest` 的測試用模組），結果寫在 `run-gametest/gametest-report.xml`，有測試失敗時指令會失敗。測試不會打包進正式 jar。
- 遊戲內測試在獨立的 `run-gametest/` 資料夾跑，每次先清空，所以永遠用模組內建的 4 件預設武器，不受 `run/config/mobarmory/` 裡改過的設定影響（R2b 的 O14）。
