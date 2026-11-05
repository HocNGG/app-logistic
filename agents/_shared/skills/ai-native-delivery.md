# SKILL — AI-Native Delivery (I-01 · I-08)

## Dùng khi nào
Mọi lúc. Đây là skill thi hành yêu cầu *"hoàn thành việc bằng AI… hạn chế thủ công"* và *"anh muốn xem năng lực **hỏi** và làm việc với AI"* của sếp.

---

## 1. Nguyên tắc

> **AI làm phần gõ phím. Bạn làm phần quyết định.**
> Nhưng sếp chấm **cách bạn hỏi** và **cách bạn bắt lỗi AI** — không chấm số lần dùng AI.

Ba điều bất biến:
1. **Không có output AI nào vào sản phẩm mà chưa chạy thật.** (Luật 9)
2. **Không giả lập AI trong demo.** Không có "đang suy nghĩ…" giả, không gõ chữ giả.
3. **Mọi lần AI sai phải được ghi lại.** Đây là bằng chứng đắt nhất.

---

## 2. Việc gì giao cho AI (và việc gì không)

| Giao cho AI 🟢 | Tự làm 🔴 |
|---|---|
| Sinh seed data, fixture, CSV mẫu | Quyết định model/schema |
| Sinh test case từ bảng 19 bước + 6 nhánh | Quyết định native hay custom (L0–L3) |
| Dịch/chuẩn hoá chuỗi `vi.po` | Chọn thuật ngữ nghiệp vụ cuối cùng |
| Đọc & tóm tắt source Odoo (nhưng **phải grep lại**) | Tin AI về tên field/API Odoo |
| Viết QWeb template nháp | Kiểm tra in PDF tiếng Việt thật |
| Sinh kịch bản video, storyboard, lời bình | Quyết định wow moment |
| Review chéo (agent khác soi bài mình) | Quyết định PASS/FAIL |
| Sinh script Playwright quay cảnh | Xem lại video bằng mắt |

---

## 3. Ask-Well Protocol — 5 phần của một câu hỏi tốt

| Phần | Ví dụ tốt | Phản mẫu |
|---|---|---|
| **Ngữ cảnh** (file/version cụ thể) | "Odoo 17.0, `addons/stock/models/stock_move.py`" | "Odoo" |
| **Ràng buộc** | "không được tạo model mới, phải dùng native" | (không nêu) |
| **Kết quả mong muốn** | "trả về đoạn code Python + tên field chính xác" | "hướng dẫn giúp" |
| **Tiêu chí đúng** | "phải chạy được trên Odoo 17.0, không dùng `attrs`" | (không nêu) |
| **Yêu cầu tự kiểm** | "nếu không chắc thì ghi UNVERIFIED, đừng đoán" | (không nêu) |

**So sánh:**

```
❌ "Làm sao để làm chuyến trong Odoo?"
   → AI bịa ra model 'transport.trip' (không tồn tại) → mất 2 giờ.

✅ "Trong Odoo 17.0 Community, tôi cần mô hình 'chuyến gom nhiều đơn của nhiều khách,
    có cut-off và tải trọng'. Ràng buộc: chỉ được thêm module mới, không sửa core.
    Hãy: (1) liệt kê những gì đã có trong addons/stock và addons/stock_picking_batch
    có thể tái dùng; (2) nêu rõ cái gì thiếu. Nếu không chắc về một API, ghi UNVERIFIED."
   → AI đưa danh sách + khoảng trống → 20 phút sau có ADR-003.
```

---

## 4. Bắt buộc: `docs/ai-log.md`

Mỗi lần dùng AI cho việc có ảnh hưởng sản phẩm, ghi **một dòng**:

```markdown
| # | Việc | Công cụ | Câu hỏi (rút gọn) | Kết quả | Đã sửa | Kiểm chứng |
|---|---|---|---|---|---|---|
| 1 | Sinh 8 test transition cho lab.chuyen | deepseek-flash | "...liệt kê transition và case lỗi..." | 8 case, 2 case sai state | 25% | chạy `--test-tags=/lab_chuyen` xanh |
| 2 | Hỏi field done-qty của Odoo 17 | claude-opus-5-5 | "...tên field done quantity ở 17.0..." | trả lời `quantity_done` ❌ | 100% | grep source 17.0 → `quantity` |
```

**Hai mục bắt buộc phải có trong `ai-log.md` khi kết thúc dự án:**
1. **≥ 3 câu hỏi tốt** (đủ 5 phần) — bằng chứng "năng lực hỏi".
2. **≥ 2 lần AI trả lời sai** + cách bạn phát hiện — bằng chứng "kiểm chứng".

Ví dụ lần AI sai (đúng thực tế đã xảy ra trong dự án này):
- AI khẳng định model kiện hàng là `stock.package` → **sai**, 17.0 là `stock.quant.package`.
- AI khẳng định `base_tier_validation` ở `OCA/server-tools` → **sai**, nó ở `OCA/server-ux`.
- Cả hai bị bắt bằng cách **đọc `__manifest__.py` / branch thật**, không tin câu trả lời.

---

## 5. Quy trình AI cho một hạng mục (5 bước)

```
1. HỎI     5 phần đầy đủ, yêu cầu UNVERIFIED khi không chắc
2. KIỂM    grep source / chạy thử — KHÔNG tin lời
3. DÙNG    áp vào sản phẩm, giữ phần đã kiểm chứng
4. GHI     một dòng vào ai-log.md (kể cả khi AI đúng)
5. SỬA     phần AI làm chưa đúng chính tả nghiệp vụ (thuật ngữ Việt)
```

**Chỉ số sức khoẻ:** nếu `ai-log.md` rỗng → bạn đang làm thủ công. Nếu "đã sửa" = 0% ở mọi dòng → bạn đang **không kiểm chứng**, chỉ copy.

---

## 6. AI trong sản phẩm (khác với AI trong quy trình)

Chỉ đưa AI vào sản phẩm khi **có dữ liệu thật**:

| Tính năng | Điều kiện "thật" | Nếu không đủ điều kiện |
|---|---|---|
| Command palette AI (tạo yêu cầu lấy hàng bằng câu lệnh) | LLM gọi được + map đúng sang API Odoo | Khai báo rõ "3 intent mẫu", hoặc cắt |
| Gợi ý giảm tải | Dữ liệu chuyến thật + giải thích được lý do | Cắt |
| Voice tiếng Việt (VieNeu) | Audio **pre-generate**, phát offline | Cắt hoặc dùng TTS khác |
| OCR AWB | Nhận đúng số AWB trên ảnh thật ≥ 8/10 lần | Cắt — đừng diễn |

**Câu tự vấn:** *"Nếu người chấm hỏi 'AI này lấy dữ liệu ở đâu', tôi có trả lời được trong 10 giây không?"*

## Checklist nhanh
- [ ] Câu hỏi AI có đủ 5 phần
- [ ] Đã kiểm chứng output AI bằng grep/chạy thật
- [ ] `docs/ai-log.md` có dòng mới
- [ ] Có ≥ 2 lần AI sai được ghi lại (cả dự án)
- [ ] Không có AI giả lập trong demo
- [ ] Thuật ngữ tiếng Việt do người quyết, không do AI
