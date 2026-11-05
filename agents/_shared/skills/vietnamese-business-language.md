# SKILL — Tiếng Việt nghiệp vụ (thuật ngữ & cách viết)

## Dùng khi nào
Mỗi lần đặt tên field, viết nhãn UI, viết thông báo lỗi, viết email template, sinh report, hoặc viết lời bình video.

---

## 1. Vì sao đây là việc nghiêm túc

Người chấm là dân logistics Việt Nam. Một nhãn sai thuật ngữ ("vận đơn" thay cho "chuyến", "kiện hàng" viết hoa lung tung) làm mất uy tín ngay lập tức — nó chứng minh bạn **chưa từng nói chuyện với người dùng thật**.

Quy tắc: **dùng đúng từ mà nhân viên trong công ty này dùng**, không dùng từ trong sách giáo khoa.

---

## 2. Glossary bắt buộc (nguồn chuẩn: `docs/00` §10)

| Thuật ngữ | Viết đúng | KHÔNG viết | Ghi chú |
|---|---|---|---|
| Chuyến | **chuyến** (chuyến bay / chuyến tàu) | vận đơn, lô vận chuyển, shipment | đơn vị gom hàng — khái niệm trung tâm |
| Gom hàng lẻ | **gom hàng lẻ** | consolidation, LCL (chỉ dùng khi giải thích) | |
| Kiện | **kiện** | thùng, package, carton | 1 đơn vị vật lý đóng gói |
| Cước | **cước** (cước vận chuyển) | phí, giá vận chuyển | |
| Debit | **Debit** (giữ nguyên, hoặc "phiếu ghi nợ" khi giải thích lần đầu) | bill, hoá đơn nháp | ⚠️ Debit ≠ hoá đơn: Debit là bảng kê cước, hoá đơn là `account.move` chính thức |
| Khoá Debit | **khoá Debit** | lock, chốt sổ | |
| Kho chờ | **KHO CHỜ** | khu vực tạm, staging | location nội bộ |
| Giảm tải | **giảm tải** | offload, cắt đơn | nhánh ngoại lệ ② |
| Tải trọng | **tải trọng** | capacity, sức chứa (dùng được khi giải thích) | |
| Cân thực tế | **kg thực tế** | actual weight | |
| Kg thể tích | **kg thể tích** | volumetric, kg quy đổi | |
| AWB | **AWB** (đọc "ây-đắp-liu-bi") hoặc **vận đơn hàng không** | air bill | |
| HBL | **HBL** hoặc **vận đơn đường biển** | bill of lading | |
| Manifest | **manifest** (đọc "ma-ni-fét") | bảng kê (dùng khi giải thích) | |
| POD | **POD** hoặc **biên nhận giao hàng** | proof of delivery | |
| Đại lý | **đại lý** (đại lý nước ngoài) | agent, đối tác | |
| Tài xế | **tài xế** | driver, shipper | |
| Chứng từ | **chứng từ** | docs, hồ sơ | phòng ban Chứng từ |
| Đối soát | **đối soát** | reconciliation | |
| Khoá tháng | **khoá tháng** | close period, chốt kỳ | |

---

## 3. Quy tắc viết nhãn UI

1. **Ngắn, danh từ, không viết hoa giữa chừng:** `Số kiện`, `Kg thực tế`, `Tải trọng tối đa` — không `SỐ KIỆN`, không `Số Kiện`.
2. **Tránh "của" khi không cần:** `Ngày đi` tốt hơn `Ngày đi của chuyến`.
3. **Nút = động từ:** `Duyệt`, `Gửi`, `Gán tài xế`, `Giảm tải`, `Khoá`. Không `OK`, không `Submit`.
4. **Trạng thái = tính từ/động từ đã xong:** `Nháp`, `Đã mở`, `Đã chốt`, `Đã đi`, `Đã đến`, `Đã giao`.
5. **Thông báo lỗi: nói người dùng làm gì tiếp**, không nói lỗi kỹ thuật:
   - ❌ `ValidationError: field not set`
   - ✅ `Chuyến đã quá tải 120%. Hãy gỡ bớt đơn hoặc tăng tải trọng trước khi duyệt.`
6. **Không lộ mặc định Odoo:** `Save`, `New`, `Confirm`, `Validate`, `Create`, `Discard`, `Total` → phải dịch trong `vi.po` hoặc override view.

---

## 4. Văn phong thông báo (chuẩn của dự án)

| Ngữ cảnh | Mẫu |
|---|---|
| Lỗi chặn thao tác | `Không thể gỡ đơn: chuyến đang ở trạng thái "Đã chốt booking". Hãy huỷ chốt trước.` |
| Cảnh báo (vẫn cho làm) | `Chuyến đã dùng 118% tải trọng. Vẫn lưu?` |
| Thành công | `Đã gán 5 đơn vào chuyến CH/AIR/26/0001.` |
| Việc cần làm (activity) | `Bổ sung thông tin người nhận cho đơn SO/26/0042` |
| Email cho khách | `Kính gửi anh/chị, lô hàng AWB 738-12345678 đã đến sân bay Tân Sơn Nhất lúc 14:20 ngày 12/03.` |

**Nguyên tắc:** nói **cái gì** + **vì sao** + **làm gì tiếp**. Không dùng từ "quý khách" trong UI nội bộ (chỉ dùng trong email/portal).

---

## 5. Trong code

```python
from odoo import _

# ✅ chuỗi bọc _(), tiếng Việt có dấu
raise UserError(_("Chuyến %(name)s đã quá tải %(pct).0f%%. Hãy giảm tải trước khi duyệt.",
                  name=self.name, pct=self.ti_le_tai))

# ❌ hardcode tiếng Anh
raise UserError("Trip is overloaded")

# ❌ nối chuỗi (không dịch được)
raise UserError(_("Chuyến ") + self.name + _(" đã quá tải"))
```

- Field `help` và `string` cũng phải tiếng Việt (hoặc để trống rồi dịch trong `vi.po`).
- **Không** đặt tên field kỹ thuật bằng tiếng Việt không dấu dài dòng — xem `docs/02` (đã chốt: `tai_trong_max_kg`, `ly_do_go`…). Nhất quán với bảng model.

---

## 6. Kiểm tra trước demo (5 phút)

1. Đổi ngôn ngữ sang **English** → màn hình demo còn chữ Việt nào không? (nếu có → chuỗi chưa bọc `_()`)
2. Đổi sang **tiếng Việt** → còn chữ Anh nào? (nếu có → thiếu `vi.po`)
3. Chụp màn hình 6 màn chính → soi từng nhãn theo glossary §2.
4. In 1 PDF (AWB) → kiểm tra dấu tiếng Việt không bị mất/ô vuông.
5. Đọc to lời bình video → nghe có giống người trong ngành nói không.

## Checklist nhanh
- [ ] Mọi nhãn UI dùng đúng thuật ngữ glossary
- [ ] Không còn `Save/New/Confirm/Validate` trên màn demo
- [ ] Chuỗi Python bọc `_()`, có trong `vi.po`
- [ ] Thông báo lỗi nói "làm gì tiếp", không nói lỗi kỹ thuật
- [ ] PDF in đúng dấu tiếng Việt
- [ ] Lời bình video đọc như người trong ngành
