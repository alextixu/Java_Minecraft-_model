# 邊界行為：Given / When / Then（段落 E，數值改成 JSON 定義）

> 狀態：**依夜間授權採用建議值，待使用者確認**（見 [26](26-stat-files-open-questions.md)）。每一列都會變成一個測試，或寫明為什麼只能手動驗證；對應的測試列在 [27](27-stat-files-behavior-report.md)。
> 邊界說明在 [24](24-stat-files-boundary-note.md)。

## 成功條件（一個從 JSON 來的數值正常運作，要同時成立的事）

1. 檔案在 `config/mobarmory/stats/`，檔名是合法的 id，內容格式正確。
2. 遊戲啟動時讀到它，它出現在遊戲的數值登記表裡。
3. 自訂數值有對應的屬性，掛在每種生物身上，值是預設值。對應原版屬性的數值，指向的屬性存在。
4. 武器可以加減它，規則和其他數值一樣（R1 的疊加規則）。
5. Java 程式要用的 7 個內建數值都在。
6. 多人：客戶端和伺服器有同一組數值檔。

## H：正常情況

| # | Given | When | Then | 對應 |
|---|---|---|---|---|
| H1 | 全新的遊戲資料夾，沒有 `config/mobarmory/stats/` | 第一次啟動 | 寫出 7 個檔案；讀回來的登記表和 `ModStats.CATALOG` 完全一樣，遊戲的行為和以前一樣 | Q4、O1 |
| H2 | 資料夾裡多一個 `luck.json`（自訂，預設 0.25，上限 1） | 重開遊戲 | 登記表多一個 `luck`；每隻生物都有 `mobarmory:luck` 屬性，值 0.25；`/mobarmory stats` 會列出它 | Q6 |
| H3 | 同上 | 讓重劍多一個 `luck` +0.5 的修飾（網頁或 JSON） | 拿著重劍的玩家，`luck` 是 0.75 | 成功條件 4 |
| H4 | 資料夾裡多一個 `jump.json`（`"attribute": "minecraft:jump_strength"`） | 重開遊戲，讓重劍加 `jump` +50% | 拿著重劍的玩家，原版跳躍力從 0.42 變成 0.63，真的跳得比較高 | Q6 A |
| H5 | 把內建的 `crit_chance.json` 預設值改成 0.05 | 重開遊戲 | 每隻生物的暴擊率是 5%；暴擊的規則照舊 | Q4 A |
| H6 | `luck.json` 有 `"name": "Luck"` | 客戶端看拿著加了 luck 的武器的 tooltip | 顯示「Luck」，不是 `attribute.name.mobarmory.luck` | Q7 A |
| H7 | 網頁打開 | 看武器表單的數值選單 | 多了 `luck`、`jump`，可以選來加減 | Q10 A |

## B：成功條件被破壞時

| # | 破壞的條件 | Given | When | Then | 決定 |
|---|---|---|---|---|---|
| B1 | 1 格式 | 一個檔案不是 JSON、少了 `default`、數字寫成文字、多了不認得的欄位、`format` 不是 1 | 啟動 | **啟動失敗**，log 寫出檔名和欄位，所有問題一次列出 | Q9 A |
| B2 | 1 範圍 | `default` 不在 `min`～`max` 之間，或 `min > max`、`floor` 不在 0～1 | 啟動 | 啟動失敗，寫出檔名和原因 | Q9 A |
| B3 | 1 檔名 | 檔名不是合法的 id（例如 `Jump.json`、`2x.json`） | 啟動 | 啟動失敗，寫出檔名 | Q9 A |
| B4 | 5 內建 | 刪掉了 `crit_chance.json` | 啟動 | 照常啟動，用內建的值，log 寫一行警告；檔案不會自動寫回 | Q5 A |
| B5 | 5 內建 | 把 `attack_damage.json` 改成自訂屬性，或改指向另一個原版屬性；把 `crit_chance.json` 改成指向原版屬性 | 啟動 | 啟動失敗：「內建數值不能改種類」 | Q4 A |
| B6 | 3 屬性 | 兩個檔案指向同一個原版屬性 | 啟動 | 啟動失敗（`StatCatalog` 本來就擋） | 沿用 R1 |
| B7 | 3 屬性 | `"attribute": "minecraft:laser_power"`（不存在） | 啟動 | 啟動失敗，寫出數值和屬性名稱（R1 的 `verifyAll`，在 common setup 檢查） | 沿用 R1 |
| B8 | 4 武器 | 刪掉了 `luck.json`，但有武器檔還在用 `luck` | 啟動 | 啟動失敗，寫出是哪個武器檔、哪個數值（R2b 的 O7） | 沿用 R2b |
| B9 | 2 執行中 | 遊戲執行中改了 `stats/` 裡的檔案 | `/mobarmory reload` 或網頁「從檔案重新載入」 | 數值不變（只重新載入武器），重開遊戲才生效 | Q8 A |
| B10 | 6 多人 | 客戶端少了伺服器有的 `luck.json` | 連線 | 連不上（屬性登記表不同，平台限制）。不做自動測試，寫進 README | Q11 A |
| B11 | — 同步 | `luck.json` 寫 `"sync": false` | 客戶端看自己的數值 | 客戶端不會收到這個屬性，伺服器端的規則照常。只有手動驗證 | O2 |
| B12 | — 檔案 | 資料夾裡有 `notes.txt` | 啟動 | 不理它，只讀 `*.json` | O4 |

## 還沒列進來的（之後的階段才處理）

- 在網頁上新增或修改數值（Q10 B）。
- 在執行中改既有數值的預設值（Q8 B）。
- 不寫 Java 就加有戰鬥效果的數值：規則要寫 `StatEffect`，或者用 `attribute` 借原版屬性的效果。
