## Dùng khi nào

Làn 5 — mọi handoff của FE-01 (`apps/kmp/**`, `addons/**/views/*.xml`, `addons/**/static/**`), và bắt buộc ở P7 + trước demo.
Đầu vào: app chạy được (`cd apps/kmp; ./gradlew :composeApp:run`) + màn hình Odoo web trên `http://localhost:8069`.
Đầu ra: bảng điểm §12 + issue P1 cho mỗi tiêu chí < 3. RV-01 **không** sửa `.kt`/`.xml` — chỉ mô tả lỗi + vị trí.

---

## 1. Bốn trạng thái bắt buộc cho **mỗi** màn hình

| Trạng thái | Phải thấy | Cách thử | Fail nếu |
|---|---|---|---|
| **Loading** | skeleton/spinner có nhãn tiếng Việt, không nháy trắng | bật throttle mạng hoặc mock delay 2s | màn trắng, hoặc spinner không có gì |
| **Empty** | câu giải thích + 1 hành động ("Chưa có việc nào hôm nay — Tải lại") | login tài xế chưa được gán job | "No data", hoặc bảng trống không lời |
| **Error** | thông báo tiếng Việt + nút **Thử lại** | tắt Odoo (`docker stop`/kill server) rồi mở màn | crash, hoặc message tiếng Anh, hoặc không có retry |
| **Content** | dữ liệu thật từ Odoo | luồng chuẩn | số liệu hardcode |

`UiState` phải là `sealed interface { Loading, Empty, Error(msg, retry), Content(data) }` (`docs/04` §3) — thấy `if (list.isEmpty())` rải rác → P1.

## 2. Ba ngưỡng duo (`docs/04` §4)

| Bề rộng | Arrangement | Kiểm gì |
|---|---|---|
| **≥ 1280 dp** | split rộng | danh sách 360–420dp + chi tiết; cột thứ 3 (thông tin chuyến) nếu có |
| **900–1280 dp** | split hẹp | danh sách ~300dp + chi tiết; ẩn cột phụ **nhưng không mất chức năng** |
| **< 900 dp** | overlay | 1 pane; chọn item → pane chi tiết phủ lên; có nút **Quay lại**; scroll danh sách giữ nguyên |

Cấm: ẩn chức năng ở màn hẹp ("thay đổi cách truy cập, không bỏ chức năng" — `docs/05` I-10); "responsive" bằng `Modifier.weight` mù; dùng `material3` alpha (`docs/04` ADR-006 chốt Material 2 stable).

## 3. Phép thử "displacement" (bắt buộc — cấm bỏ qua)

Chuẩn bị: mở CS Console, chọn 1 chuyến, gõ dở vào ô ghi chú `Kiểm tra kéo cửa sổ 12345`.

1. Ở ≥1280: bấm chọn 1 item, đặt con trỏ cuối ô ghi chú, nhớ vị trí scroll của **cả 2 pane**.
2. Kéo cửa sổ xuống ~1100 (split hẹp) → **kiểm tra**: item vẫn chọn, text còn nguyên, con trỏ còn trong ô, scroll 2 pane không nhảy về đầu.
3. Kéo tiếp xuống ~750 (overlay) → **kiểm tra**: pane chi tiết vẫn là item đó, text còn nguyên, có đường back, tab không bị mất focus.
4. Kéo ngược lên 1600 → **kiểm tra**: về split rộng, item vẫn chọn, text vẫn `…12345`, không re-create màn.
5. Lặp lại nhưng **đang giữa lúc kéo thả** 1 đơn vào chuyến → hoặc chặn kéo thả khi resize, hoặc hoàn tất đúng đơn; **không** rơi đơn.
6. Ghi video 30–60s có **đồng hồ/state hiển thị** để chứng minh không mất dữ liệu (`docs/05` I-11).

Mất bất kỳ thứ nào ở bước 2–4 → **P1**. Mất dữ liệu đang gõ / rơi item → **P0**.

## 4. Reserved-region-safe padding

Không đặt nút quan trọng/nội dung ở vùng có thể bị che: mép giữa khi cửa sổ rất rộng, mép trên/dưới khi OS có thanh, sát ranh giới 2 pane. Kiểm bằng cách kéo cửa sổ **sát mép màn hình** và thử bấm mọi nút chính; dùng `WindowInsets`/padding an toàn. Nút bị cắt hoặc không bấm được → P1.

## 5. Keyboard-only walkthrough

| Phím (`docs/04` §11) | Phải làm được | Fail nếu |
|---|---|---|
| `Ctrl/Cmd+K` | mở search | không mở, hoặc mở nhưng không gõ được |
| `J`/`K`/`↑`/`↓` | di chuyển trong danh sách | focus mất sau mỗi lần đổi |
| `Enter` | mở chi tiết item đang chọn | mở item khác item đang focus |
| `Esc` | đóng overlay | không đóng được pane overlay |
| `Tab`/`Shift+Tab` | đi hết mọi control, focus ring 2dp rõ | có control "nuốt" focus, không thấy focus ở đâu |
| `?` | help overlay phím tắt | phím không tồn tại trong app |

Đi **hết luồng chính** (login → chọn job/chuyến → hành động → xác nhận) **không chạm chuột**, quay video ngắn làm bằng chứng (DoD D7).

## 6. Thuật ngữ tiếng Việt (nguồn chuẩn: `docs/00` §10 + `agents/_shared/skills/vietnamese-business-language.md` §2)

| ĐÚNG | SAI (→ P1) | Ghi chú |
|---|---|---|
| **chuyến** (lab.chuyen) | "vận đơn", "shipment", "lô vận chuyển" | "vận đơn" chỉ đúng cho **AWB/HBL** |
| **kiện** (stock.quant.package) | "package", "thùng", "carton" | "lô" là consolidation, khác kiện |
| **cước** (sale.order.line) | "phí", "giá vận chuyển" trên nhãn | cước = tiền theo kg |
| **Debit** (giữ nguyên) | "bill", "hoá đơn nháp" | Debit là **bảng kê cước**, ≠ hoá đơn (`account.move` chính thức) |
| **khoá Debit** | "lock", "chốt sổ" | chỉ `group_manager` được mở khoá |
| **KHO CHỜ** | "khu vực tạm", "staging" | là `stock.location` trong KHO CHÍNH |
| **giảm tải** | "cắt đơn", "offload" | đúng 2 chữ |
| **tải trọng** / **CBM** | "capacity", "khối" | CBM giữ nguyên |

Sai thuật ngữ ở màn demo = P1 (Luật 6). Không được hạ mức vì "gần đúng".
Nhãn UI: danh từ ngắn, không viết hoa giữa chừng (`Số kiện`, không `Số Kiện`); nút = động từ (`Duyệt`, `Gán tài xế`, `Giảm tải`), không `OK`/`Submit`.

## 7. Không còn chuỗi tiếng Anh của Odoo

```powershell
rg -n ">(New|Save|Confirm|Cancel|Discard|Create|Search\.\.\.|Validate|Action|Print)<" addons/lab_*/views
rg -n "text\s*=\s*\"[A-Z][a-z]+ [a-z]+" apps/kmp/composeApp/src/commonMain --glob "*.kt"
```
Kiểm bằng UI: chụp từng màn, soi cả **tooltip, dialog xác nhận, thông báo lỗi, header cột, tên nút**. Còn "New"/"Save"/"Confirm" trên màn demo → P1.
Chuỗi trong code phải qua resource (KMP) hoặc `_()`/`i18n/vi.po` (Odoo) — hardcode tiếng Việt trong `.kt` cũng là P2 (không i18n được).

## 8. Contrast ≥ 4.5:1 + dark mode

Chạy công thức WCAG cho từng cặp màu nghiệp vụ trong `design/Tokens.kt` (`docs/04` §10):

```powershell
function Lum([int]$r,[int]$g,[int]$b) {
  $c = @($r,$g,$b) | ForEach-Object { $v = $_ / 255.0; if ($v -le 0.03928) { $v/12.92 } else { [math]::Pow(($v+0.055)/1.055, 2.4) } }
  0.2126*$c[0] + 0.7152*$c[1] + 0.0722*$c[2] }
$l1 = Lum 0x17 0x66 0x3A; $l2 = Lum 0xE7 0xF6 0xEC
([math]::Max($l1,$l2)+0.05) / ([math]::Min($l1,$l2)+0.05)   # ghi số này vào review
```
Kiểm **cả 2 chế độ** (sáng/tối) cho: ok, warn, bad, info, chữ phụ, placeholder, trạng thái disabled, badge "Quá tải" đỏ. < 4.5:1 → P1. **Chữ xám trên nền xám** và **placeholder** là 2 chỗ hay trượt nhất.

## 9. Chuỗi dài nhất (overflow)

Seed data phải có: shop 60 ký tự có dấu, địa chỉ 120 ký tự, mã chuyến dài nhất (`CH/AIR/26/0001`), tên tài xế có 4 từ. Kiểm ở ngưỡng **900 dp** và **< 900 dp**: tiêu đề 1 dòng (ellipsis) + tooltip, không xuống dòng vỡ layout, không cắt mất chữ số kg/tiền. Tràn/cắt chữ số → P1.

## 10. Nhất quán xuyên màn hình

Cùng 1 khái niệm → cùng 1 cách hiển thị: nút chính luôn bên phải dưới; badge trạng thái cùng bảng màu; cùng đơn vị (`kg`, `CBM`, `VND`); cùng cách format số (`1.234,5`); nút nguy hiểm (huỷ/gỡ đơn) luôn dùng màu `bad`. Hai màn dùng 2 kiểu nút khác nhau → P2.

## 11. Bảng chấm điểm (0–5 mỗi mục, dưới 3 = phải sửa)

| # | Tiêu chí | Trọng số |
|---|---|---|
| 1 | 4 trạng thái ở tất cả màn | ×2 |
| 2 | 3 ngưỡng duo đúng arrangement | ×2 |
| 3 | Displacement: không mất text/selection/scroll | ×3 |
| 4 | Reserved-region-safe + không nút bị che | ×1 |
| 5 | Keyboard-only đi hết luồng | ×2 |
| 6 | Thuật ngữ Việt đúng glossary | ×2 |
| 7 | Không lộ chuỗi tiếng Anh | ×2 |
| 8 | Contrast ≥ 4.5:1 (2 chế độ) | ×1 |
| 9 | Chuỗi dài không tràn | ×1 |
| 10 | Nhất quán + dark mode | ×1 |

Điểm = Σ(điểm × trọng số) / 17. **< 3.0 → CHANGES-REQUESTED**; mục 1, 3, 8 ở mức 0 → **BLOCKED** (không vào demo).

## 12. Kịch bản test tay 10 phút

| Phút | Việc |
|---|---|
| 0–1 | Login driver + CS; ghi lại màn đầu tiên thấy |
| 1–2 | 4 trạng thái trên 2 màn (bật/tắt server 1 lần) |
| 2–4 | Phép thử displacement §3, bật quay màn hình |
| 4–5 | Keyboard-only §5, chỉ dùng bàn phím |
| 5–6 | Contrast + dark mode §8 (chụp 2 ảnh) |
| 6–7 | Chuỗi dài nhất §9 + kéo sát ngưỡng 900/1280 |
| 7–8 | Thuật ngữ §6 + soi chuỗi Anh §7 |
| 8–9 | Nhất quán §10 giữa 2 màn |
| 9–10 | Ghi bảng điểm §11 + mở issue; verdict |

## Checklist nhanh

- [ ] Mỗi màn có 4 trạng thái, Empty và Error có tiếng Việt + nút thử lại
- [ ] Duo: split ≥1280 / split hẹp 900–1280 / overlay <900, không ẩn chức năng
- [ ] Displacement qua 3 ngưỡng: không mất text, selection, scroll (có video)
- [ ] Không nút/nội dung quan trọng ở vùng bị che khi kéo sát mép
- [ ] Đi hết luồng chính chỉ bằng bàn phím; có help overlay `?`
- [ ] Thuật ngữ đúng glossary (chuyến/kiện/cước/debit/kho chờ/giảm tải)
- [ ] Không còn "New/Save/Confirm"; contrast ≥ 4.5:1 cả 2 chế độ; chuỗi dài không tràn
- [ ] Bảng điểm §11 đã điền, mọi mục < 3 đã thành issue P1
