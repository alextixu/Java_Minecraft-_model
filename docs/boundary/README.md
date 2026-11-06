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
| [04-behavior-report.md](04-behavior-report.md) | 行為報告：結構摘要、各層測試數量、Given / When / Then 對照、沒有覆蓋的範圍、待決定的新問題、SOLID／Pattern 檢查（第 8 節，2026-11-07 補） | 已產出（第 3 輪更新：單元 106/106、遊戲內 18/18） |

## R4：自訂怪物（驗收 T2）

R4 由另一個 session 在 `feature/r4-mobs` 分支做，2026-11-07 合進 main（`96b9262`）。

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [05-r4-boundary-note.md](05-r4-boundary-note.md) | 需求翻譯、平台限制、變更邊界、實作順序、測試計畫 | 已確認 |
| [06-r4-given-when-then.md](06-r4-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 已確認 |
| [07-r4-open-questions.md](07-r4-open-questions.md) | 確認點的問題與決定（設計 3 題、商業規則 4 題、其他 10 項，加上第三輪的 Q8、第四輪的 O2 裝備部分） | 已決定（全部照建議；Q8 選 B；O2 的裝備部分 11/07 改成 B） |
| [14-r4-behavior-report.md](14-r4-behavior-report.md) | 行為報告：結構摘要、各層測試數量、Given / When / Then 對照、T2 實測（加第 3 種怪物）、合併到 main 的紀錄（第 8 節）、SOLID／Pattern 檢查（第 9 節，架構師 2026-11-07 補） | 已產出（合併 main 後：單元 268/268、遊戲內 48/48，含原版 1 個） |
| [prompts/round-r4-02-decisions.md](prompts/round-r4-02-decisions.md)、[round-r4-03-merge.md](prompts/round-r4-03-merge.md) | R4 的決定和合併 main 時的 prompt | 2026-11-07 |

## D1：砍怪傷害顯示（action bar）

D1 由另一個 session 在 `feature/damage-hud` 分支做，2026-11-07 合進 main（`d2382b9`）。

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [11-damage-display-boundary-note.md](11-damage-display-boundary-note.md) | 需求翻譯、平台限制、變更邊界、實作順序、測試計畫 | 已確認（只做 action bar，Q4 C） |
| [12-damage-display-given-when-then.md](12-damage-display-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 已確認 |
| [13-damage-display-open-questions.md](13-damage-display-open-questions.md) | 確認點的問題與決定（照審查員 review-01 X3 縮小成只做 action bar） | 已決定 |
| [16-damage-display-behavior-report.md](16-damage-display-behavior-report.md) | 行為報告：結構摘要、各層測試數量、Given / When / Then 對照、沒有覆蓋的範圍、合併到 main 的紀錄（第 8 節）、SOLID／Pattern 檢查（第 9 節，架構師 2026-11-07 補） | 已產出（合併 R4 後：單元 322/322、遊戲內 58/58，含原版 1 個） |
| [prompts/damage-display-round-01～03](prompts/) | D1 的邊界草案、決定、合併的 prompt | 2026-11-07 |

## R3：combo（出招順序不同就是不同 combo）

R3 由連招小弟在 `feature/r3-combo` 分支做。2026-11-07 夜間依使用者主軸把 combo 定義改成 `config/mobarmory/combos/<id>.json`，玩家放一個檔、打 `/mobarmory reload`，就多一種 combo。分支先合了 main `56423bd`（合併 commit `130240d`），再由架構師把 main 快轉過去。主 session 依夜間授權採用建議值，**待使用者確認**。

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [17-r3-combo-boundary-note.md](17-r3-combo-boundary-note.md) | 需求翻譯、平台查證（疾跑打中後會停止）、各裝備的移動速度、變更邊界（第 0 節是 JSON 版）、SOLID、Pattern、測試計畫 | 依夜間授權採用建議值 |
| [18-r3-combo-given-when-then.md](18-r3-combo-given-when-then.md) | 成功條件，H1～H7、B1～B20、J1～J9（combo 檔） | 同上 |
| [19-r3-combo-open-questions.md](19-r3-combo-open-questions.md) | Q1～Q7、O1～O12，以及決定紀錄（採用建議值、Q2 改成 JSON） | 待使用者確認 |
| [20-r3-combo-behavior-report.md](20-r3-combo-behavior-report.md) | 行為報告：實作時的假設 C1～C11、結構摘要、各層測試數量、Given / When / Then 對照、SOLID／Pattern 檢查（第 5 節）、之後（第 7 節）、合併到 main（第 8 節） | 已產出（合併 main 後：單元 450/450、遊戲內 68/68，含原版 1 個） |
| [prompts/r3-combo-round-01～03](prompts/) | 邊界草案、實作（每一步的 SOLID／Pattern）、合併 main | 2026-11-07 |

## R2b ＋ R6：武器改成資料驅動、網頁後台

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [08-weapon-data-web-boundary-note.md](08-weapon-data-web-boundary-note.md) | 需求翻譯、平台限制、變更邊界、給其他模組的穩定介面、網頁 API、實作順序、測試計畫 | 已確認（最終版） |
| [09-weapon-data-web-given-when-then.md](09-weapon-data-web-given-when-then.md) | 成功條件，以及逐一破壞後的 Given / When / Then | 已確認（最終版） |
| [10-weapon-data-web-open-questions.md](10-weapon-data-web-open-questions.md) | 確認點的問題與決定，含決定的經過（先「全部照建議」，後改「照審查建議版」） | 已決定 |
| [15-weapon-data-web-behavior-report.md](15-weapon-data-web-behavior-report.md) | 行為報告：第一版和最終版的差別、結構、給 R4／D1 的穩定介面、各層測試數量、Given / When / Then 對照、實測紀錄、沒有覆蓋的範圍、示範影片劇本、SOLID／Pattern 檢查（第 11 節）、交付前的追加（第 12 節） | 已產出（單元 237/237、遊戲內 29/29；第 12 節之後是 333/333、59/59） |

## 合併到 main（2026-11-07）

使用者 11/07 決定「合併」。架構師把 main 依序快轉，`40ee556`（R2b＋R6）→ `96b9262`（R4）→ `d2382b9`（D1），然後補這份索引。每一步的 SOLID、Pattern、變更邊界檢查見 [prompts/round-06-merge.md](prompts/round-06-merge.md)，結論是沒有衝突。

| 項目 | 結果（main 重跑 `gradlew clean build runGameTestServer`） |
|---|---|
| 單元測試 | **322 / 322** |
| 遊戲內系統測試 | **58 / 58**（我們的 57 個加原版 `always_pass`） |

## 交付前的準備（2026-11-07 夜間段落 A）

使用者睡前授權（「繼續把所有內容補齊……我睡醒再回來驗收」）。主 session 依審查員 review-03 第 7 節下指令，架構師在 main 上做了下面這些事。指令和每一步的 SOLID、Pattern、變更邊界見 [prompts/round-07-delivery-prep.md](prompts/round-07-delivery-prep.md)，結果見 15 號第 12 節。

| 項目 | commit |
|---|---|
| 根目錄 [README](../../README.md)：安裝、玩法、加武器、加怪物、多人規則、已知限制 | `d50f14f` |
| 網頁的怪物分頁：唯讀清單，`GET /api/v1/mobs` | `012a2de` |
| 多人的兩個小修正：離開伺服器後還原、送 PNG 前檢查 | `6c01cfd` |
| 04、14、16 補 SOLID／Pattern 檢查；15 號的過時文字；`CREDITS.md` 搬家；網頁遊戲內測試的時間問題 | 這一份索引所在的 commit |

結果：`gradlew clean build runGameTestServer` 單元 **333/333**、遊戲內 **59/59**（58 個我們的加原版 1 個）。

## 段落 E：數值改成 JSON 定義（2026-11-07 夜間）

使用者定下主軸：「主軸在我們這個系統可以未來讓玩家新增東西而不是我們自己要開發出很厲害的怪物與武器」。數值從 Java 的 `content/ModStats` 改成 `config/mobarmory/stats/<id>.json`，玩家加一個檔、重開遊戲，就多一個數值。主 session 依夜間授權採用建議值，**待使用者確認**。

| 檔案 | 內容 | 狀態 |
|---|---|---|
| [24-stat-files-boundary-note.md](24-stat-files-boundary-note.md) | 需求翻譯、平台限制、檔案格式、變更邊界、給其他分支的介面、SOLID（含為什麼要動 `ModStats`） | 依夜間授權採用建議值 |
| [25-stat-files-given-when-then.md](25-stat-files-given-when-then.md) | 成功條件，H1～H7、B1～B12 | 同上 |
| [26-stat-files-open-questions.md](26-stat-files-open-questions.md) | Q1～Q11、O1～O5，每題第一個選項是建議值 | 待使用者確認 |
| [27-stat-files-behavior-report.md](27-stat-files-behavior-report.md) | 行為報告：改了什麼、給其他分支的介面、為什麼執行中不能改、Given / When / Then 對照、手動步驟、SOLID／Pattern 檢查 | 已產出（單元 375/375、遊戲內 61/61） |
| [prompts/round-08-stat-files.md](prompts/round-08-stat-files.md) | 指令和每一步的 SOLID、Pattern、變更邊界 | 2026-11-07 |

## 文件編號

| 編號 | 內容 | 誰寫 |
|---|---|---|
| 01～04 | R1＋R2 | 架構師（原 `mc-dev`） |
| 05～07、14 | R4 自訂怪物（邊界、行為報告） | R4 的 session |
| 08～10、15 | R2b＋R6（邊界、行為報告） | 架構師（原 `mc-dev`） |
| 11～13、16 | D1 砍怪傷害顯示（邊界、行為報告） | `damage-hud` |
| 17～19、20 | R3 combo（邊界、行為報告） | 連招小弟 |
| 21～23 | （保留） | — |
| 24～26、27 | 段落 E：數值改成 JSON（邊界、行為報告） | 架構師 |

本索引由架構師維護。

## 之後的功能

R5 多人同步，跑同樣的流程。

## 怎麼跑測試

```bash
./gradlew clean build runGameTestServer
```

- `build` 會跑單元測試（`core/`、`content/`，不用開遊戲）。
- `runGameTestServer` 會開一個無畫面的遊戲伺服器跑系統測試（實際執行的是 `runGametestGameTestServer`，載入 `src/gametest` 的測試用模組），結果寫在 `run-gametest/gametest-report.xml`，有測試失敗時指令會失敗。測試不會打包進正式 jar。
- 遊戲內測試在獨立的 `run-gametest/` 資料夾跑，每次先清空，所以永遠用模組內建的 4 件預設武器，不受 `run/config/mobarmory/` 裡改過的設定影響（R2b 的 O14）。
