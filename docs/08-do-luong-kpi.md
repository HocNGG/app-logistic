# 08 — ĐO LƯỜNG & TÁC ĐỘNG (KPI / Impact)

> Tín hiệu FDE thứ 6: *"Quantifies impact (time saved, steps removed) and demos it, not just describes it."*
> File này trả lời: **đo cái gì, đo bằng gì, và con số nào được nói trong demo.**
> Nguyên tắc: **chỉ nói con số đo được từ dữ liệu thật** — không bịa "giảm 80% thời gian".

---

## 1. Trước và sau — đo cái gì

| # | Việc | Trước (Zalo + Excel) | Sau (hệ thống) | Đo bằng |
|---|---|---|---|---|
| 1 | Tạo & xác nhận 1 yêu cầu lấy hàng | ~12 phút chat qua lại, không ai chịu trách nhiệm | 1 form + `mail.activity` có người phụ trách | Đếm bước: 6 → 3 |
| 2 | Tài xế nhận việc | 2–3 cuộc gọi, trùng chuyến | App tự hiện danh sách việc hôm nay | Số cuộc gọi: >0 → 0 |
| 3 | Bằng chứng đã lấy hàng | Ảnh trong Zalo, dễ mất | Ảnh + chữ ký lưu `ir.attachment` gắn chứng từ | Có/không |
| 4 | Gom 1 chuyến 20 đơn | ~2 giờ Excel, dễ trùng/sót | Kéo thả vào chuyến, cảnh báo tải trọng | Thời gian: 2h → vài phút |
| 5 | Phát hiện chuyến quá tải | Sau khi hãng từ chối | Thanh tải đỏ ngay khi vượt 100% | Phát hiện: trước/ sau khi gửi hãng |
| 6 | Khách hỏi "hàng đang ở đâu" | CS gọi điện, ~5 phút/ lần | Khách tự mở link timeline | Số lần CS phải trả lời: giảm |
| 7 | Lập Debit 1 chuyến | Excel + copy tay, dễ sót đơn | Wizard từ `lab.chuyen.line`, 1 Debit/ shop | Thời gian: 30 phút → < 2 phút |
| 8 | Debit bị sửa sau khi gửi khách | Không kiểm soát được | Khoá + tier validation + audit chatter | Số lần: không đo được → **0** |
| 9 | Đối soát tháng | Excel đối chiếu tay | `period_lock_date` + payment reconciliation | Số ngày chốt sổ |
| 10 | Tìm lại lịch sử 1 đơn | Lục Zalo/ Excel | Chatter + smart button + search | Thời gian: ? → < 30 giây |

**Quy tắc trung thực:** cột "Trước" **không có số đo thật** (vì công ty là giả định) → khi demo phải nói rõ:
> *"Đây là con số ước lượng từ phỏng vấn, không phải số đo. Số đo được là các con số ở cột 'Sau' — chúng đếm được trực tiếp trên hệ thống."*

Đây là **critical thinking** — không được nói "giảm 80% thời gian" như thể đã đo.

---

## 2. KPI đo được từ dữ liệu thật trong Odoo

| KPI | Công thức (Odoo) | View | Đo bằng |
|---|---|---|---|
| Số đơn / ngày | `count(sale.order)` theo `date_order` | `_read_group` hoặc pivot | native |
| Sản lượng kg / tuần | `sum(lab_weight_kg)` trên `stock.move.line` done | pivot | native |
| Hệ số sử dụng tải chuyến | `tai_trong_da_dung_kg / tai_trong_max_kg` trung bình | graph | compute trên `lab.chuyen` |
| Số chuyến quá tải | `count(lab.chuyen)` với `ti_le_tai > 100` | filter | compute |
| Thời gian gom chuyến | `booked_date - chuyen.create_date` | pivot | field `create_date` (native) |
| Tỷ lệ đơn phải giảm tải | `count(line.state='waitlist') / count(line)` | pivot | native |
| Ngoại lệ đang mở | `count(picking)` với `lab_is_cho_khach=True` | filter | field |
| Thời gian lấy hàng (đi → về kho) | `picking.done_date - layhang.ngay_giao` | pivot | 2 field |
| Cước / kg theo tuyến | `amount_total / lab_khoi_luong` | pivot | native |
| DSO (số ngày thu tiền) | `(receivable / revenue) × 30` | báo cáo | `account` native |
| Doanh thu / shop | `sum(amount_total)` group by `partner_id` | pivot/graph | native |
| Số khiếu nại / 1000 đơn | `count(helpdesk.ticket) / count(sale.order) × 1000` | pivot | OCA |

**Không KPI nào cần model mới.** Nếu phải tạo field để đo → field tính (compute, `store=True`) trên model có sẵn.

---

## 3. Con số được nói trong demo (chốt trước, không bịa sau)

| Con số | Ý nghĩa | Nguồn |
|---|---|---|
| **19 bước / 10 pha / 6 phòng ban** | Phạm vi nghiệp vụ | Sơ đồ PDF (đề bài) |
| **10 + 3 + 6** | 10 bước native · 3 config · 6 phải viết code | PDF trang 1 |
| **3 module** (+1 keo) | `lab_chuyen`, `lab_laixe`, `lab_hanhtrinh` | ADR-001 |
| **6/6 nhánh ngoại lệ** có test điểm quay về | Chất lượng nghiệp vụ | `docs/07` §0 |
| **~2.000 dòng code** (điền sau khi xong) | Quy mô thật | `cloc addons/lab_*` |
| **0 model trùng Odoo** | Native-first | `infra/scripts/audit-native-first.ps1` |
| **1 chuyến 20 đơn: từ ~2 giờ Excel → X phút** | Tác động | Quay video bấm đồng hồ |

**Bắt buộc:** con số "dòng code" và "thời gian gom chuyến" phải **đo thật trước khi nói** (dán log/lệnh vào `docs/review/`).

---

## 4. Cách đo (không đòi hỏi thêm tool)

```powershell
# Quy mô code (sau khi có addons/)
(Get-ChildItem addons\lab_* -Recurse -Include *.py,*.xml,*.csv -File |
  Get-Content | Measure-Object -Line).Lines

# Đếm model mới so với kế hoạch
rg -n "^\s*_name\s*=" addons\lab_* | Measure-Object -Line

# Audit native-first (0 P0 mới được nói "0 model trùng")
powershell -File infra/scripts/audit-native-first.ps1
```

Đo KPI trong UI: **pivot view** (native) — không viết dashboard riêng (ADR-002).

---

## 5. Đo trong demo như thế nào (kỹ thuật quay)

Nếu muốn cảnh "gom 20 đơn nhanh" gây ấn tượng:
1. Quay **đồng hồ bấm giây** trên màn hình (Playwright overlay hoặc đồng hồ góc màn hình).
2. Bắt đầu trước khi kéo đơn đầu tiên, kết thúc khi chuyến đủ 20 đơn.
3. **Cắt bất kỳ đoạn chờ/tua nhanh** — và **nói rõ** "đã tua 8x" nếu có tua. Nếu tua mà không nói → mất uy tín ngay.

Tốt hơn: đừng khoe tốc độ tay. Khoe **số bước bị loại bỏ** ("bỏ 4 lần nhập lại dữ liệu") — con số này thật, không cần quay nhanh.

---

## 6. Ba KPI để trả lời câu hỏi "nếu 1.000 đơn/ngày"

| Câu hỏi | Trả lời bằng số |
|---|---|
| List có chịu được? | đo thời gian load 500 dòng: mục tiêu < 1.5s với `limit` + index |
| Gom chuyến có tự động được? | % đơn được gợi ý chuyến phù hợp (backlog: AI gợi ý) |
| Đối soát có chịu? | số dòng `account.move.line`/ tháng; pivot `_read_group` thay `read_group` |

Ghi kết quả vào `docs/review/red-team.md` dòng #10.

## Checklist nhanh
- [ ] Mọi con số nói trong demo có nguồn (lệnh/file/dữ liệu)
- [ ] Con số "trước" được nói rõ là ước lượng, không phải đo
- [ ] Có ít nhất 3 KPI đo được bằng pivot native
- [ ] Không có model mới chỉ để đo
- [ ] Đã chạy audit-native-first và dán log
- [ ] Đã đo "thời gian gom chuyến" 1 lần thật (không đoán)
