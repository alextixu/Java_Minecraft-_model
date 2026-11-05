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
| [01-boundary-note.md](01-boundary-note.md) | 需求翻譯、平台限制、變更邊界、實作順序、測試計畫 | 草案 |
| [02-given-when-then.md](02-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 草案 |
| [03-open-questions.md](03-open-questions.md) | 待使用者決定的問題（設計 3 題、商業規則 4 題、其他 9 項、命名） | 等待回覆 |
| [prompts/](prompts/) | 每一輪交給 AI 的 prompt | 持續更新 |
| 04-behavior-report.md | 行為報告（測試結果表） | 實作完成後產出 |

## 之後的功能

R4 自訂怪物 → R3 combo → R5 多人同步 → R6 網頁與伺服器 API，每一項各跑一次同樣的流程。
