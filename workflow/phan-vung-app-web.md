# PHÂN VÙNG APP ↔ WEB — BẢN ĐỀ XUẤT CHỜ DUYỆT

> **Trạng thái: ĐỀ XUẤT. Chưa áp dụng gì vào code.** Viết 2026-11-02, đo trực tiếp trên DB
> `odoo17_lab` + APK đang cài trên máy tính bảng.
> Mục đích: chốt **ai làm việc trên bề mặt nào** trước khi code thêm, để không phải sửa hai lần.
> Anh duyệt §7 xong tôi mới bắt tay vào §6.

---

## 1. Nguyên tắc phân vùng (4 câu, dùng để tự kiểm mọi việc về sau)

1. **APP = tay chân ngoài hiện trường**: một tay cầm máy, đeo găng, mạng chập chờn, mỗi việc ≤ 5 lần chạm.
2. **WEB = đầu não văn phòng**: nhập nhiều trường, gom nhiều bản ghi vào một chứng từ, duyệt, xem chéo.
3. **Chứng từ tài chính/kho luôn sinh ở nơi có bàn phím** (hoá đơn, debit, xuất kho nhiều dòng) — trừ khi
   nghiệp vụ bắt buộc xác nhận tại chỗ (nhận hàng ở dock).
4. Một việc **chỉ có MỘT bề mặt chính**. Bề mặt còn lại (nếu có) là **chỉ đọc** — không hai nơi cùng sửa.

Ba câu hỏi để phân loại một việc mới:
- Người làm có đang **đứng ngoài đường / trong kho** không? → APP.
- Có phải **nhập ≥ 5 trường** hoặc **chọn nhiều bản ghi** không? → WEB.
- Sai một nút ở đây có **sai sổ sách** (tồn kho/tiền) không? → WEB (trừ khi có nghiệp vụ chặn rõ).

---

## 2. Hiện trạng (đo 2026-11-02)

### 2.1 App đang có 7 màn (theo `Workspace.kt` + API `/lab/api/v1/…`)

| Màn app | Vai trò mở được | API đứng sau |
|---|---|---|
| Tài xế · Việc hôm nay | `taixe01…05` | `GET /driver/jobs`, `POST …/accept`, `…/capture`, `…/fail` |
| Trưởng nhóm tài xế · Phân công | `nhomtruong01` | `GET /cs/jobs`, `POST /cs/jobs/<id>/assign` |
| **CS · Điều phối** | `cs01` | *(dùng chung API `/cs/jobs` + `assign`)* |
| Kho · Nhận hàng | `kho01` | `GET /kho/cho-nhan`, `POST /kho/jobs/<id>/nhan` |
| Sếp · Tổng quan | `sep` | `GET /sep/kpi` |
| Vai trò khác | `manager01`, `ketoan01`, `chungtu01`, `admin` | — (báo "làm việc trên Odoo web") |
| Chuyển tài khoản (admin) | `admin` | `GET /admin/accounts`, `POST /admin/switch` |

### 2.2 Web đang có (menu **Gom hàng lẻ** + quyền)

| Menu web | Ở menu này ai ĐƯỢC GHI (đo từ `ir.model.access`) |
|---|---|
| Yêu cầu lấy hàng | **CS/Sale · Sale Admin · Trưởng phòng** (Kho: đọc) |
| Chuyến | **Kho · Sale Admin · Trưởng phòng** — **CS chỉ ĐỌC** |
| Chuyến quá tải (giảm tải) | như Chuyến |
| Hành trình | **Chứng từ · Trưởng phòng** (CS/Kho/Kế toán đọc) |
| Khách hàng (Shop) | **CS/Sale** (vừa mở ACL 2026-11-02) |
| Bảng điều khiển | Trưởng phòng · BGĐ |

### 2.3 Bốn điểm LỆCH so với logic nghiệp vụ

| # | Hiện trạng | Vì sao sai |
|---|---|---|
| **L1** | **CS có màn app "Điều phối"** | CS là người ngồi bàn: tạo khách, tạo YC, sửa địa chỉ, gom chuyến, xử lý ngoại lệ ①③. Dùng app điện thoại để làm việc nhập liệu là ngược. |
| **L2** | **CS KHÔNG gom chuyến được trên web** (ACL `lab.chuyen` = `1,0,0,0`) | Việc gom chuyến hiện do **Kho** và **Manager** làm trên web — trong khi nghiệp vụ là của CS. |
| **L3** | **Kho phải lên web tạo + Validate phiếu WH/OUT** | Kho làm việc ở dock: nhận hàng, đếm kiện, đóng gói, giao cho tài xế chuyến. Bắt họ về bàn làm 1 phiếu xuất là sai. App kho hiện **chỉ có Nhận hàng**. |
| **L4** | **Gán tài xế chỉ có trên app** (không có nút ở web) | Nếu CS lên web (L1) thì trên web phải gán được tài xế, nếu không CS mất chức năng. |

Hai điểm lệch phụ:
- **L5**: form Yêu cầu lấy hàng trên web **không có nút trạng thái** nào (đếm: 1 `<button>` trong toàn view) ⇒ web không huỷ/không gán được việc.
- **L6**: màn Kho (app) **chỉ thấy việc `Đã lấy được hàng`** ⇒ kho không biết xe nào sắp về để dọn dock.

---

## 3. ĐỀ XUẤT — phân vùng đích

### 3.1 Bảng tổng (đây là bảng tôi xin duyệt)

| Vai trò | APP (bề mặt chính) | WEB (bề mặt chính) | Bề mặt còn lại |
|---|---|---|---|
| **Tài xế** `taixe01…05` | ✓ **duy nhất**: Việc hôm nay · Bắt đầu đi · Chụp ảnh + Ký + Đếm kiện · Báo thất bại | ✗ | — |
| **Trưởng nhóm tài xế** `nhomtruong01` | ✓ Phân công tài xế · xem khung giờ cả ngày | ✗ | web: chỉ đọc (không menu) |
| **Kho** `kho01` | ✓ **Nhận hàng (có)** · **Xuất kho (MỚI)** · Kiểm đếm thiếu/đủ kiện (MỚI) · xem việc **đang về** | ✗ (bỏ khỏi web) | web: chỉ đọc khi cần tra tồn |
| **CS** `cs01` | ✗ **BỎ màn Điều phối** | ✓ Khách hàng · Yêu cầu lấy hàng · **Gán tài xế (MỚI)** · **Gom chuyến (MỚI)** · xử lý ngoại lệ ①③ | — |
| **Manager** `manager01` | ✗ | ✓ Chuyến: Mở · Chốt booking · Khởi hành · Đến nơi · Giao khách · Khoá · **Duyệt debit** | — |
| **Chứng từ** `chungtu01` | ✗ | ✓ Hành trình: AWB/HBL, mốc, POD, sự cố | — |
| **Kế toán** `ketoan01` | ✗ | ✓ Hoá đơn · Debit · đối soát | — |
| **Sếp** `sep` | ✓ (giữ) Tổng quan KPI | ✓ Bảng điều khiển | hai nơi, chỉ đọc |
| **Admin** `admin` | ✗ (giữ màn chuyển tài khoản làm công cụ demo) | ✓ toàn quyền | — |

**Tóm một dòng:** app phục vụ **3 nhóm tay chân** (tài xế · trưởng nhóm tài xế · **kho**) + 1 màn đọc cho sếp.
Web phục vụ **5 nhóm văn phòng** (CS · manager · chứng từ · kế toán · admin). **CS rời app, Kho rời web.**

### 3.2 Vì sao Kho phải ở app (không phải web)

Kho trong mô hình này làm 4 việc, cả 4 đều **tại dock**:
1. Nhận hàng tài xế mang về → đếm kiện, đối chiếu biên nhận → hiện đã có (app).
2. **Chuẩn bị hàng lên chuyến**: đối chiếu số kiện theo chuyến, đóng gói, dán nhãn → cần app.
3. **Xuất kho**: xác nhận hàng đã rời kho (WH/OUT) → hiện phải lên web ⇒ **điểm lệch chính**.
4. Báo thiếu/thừa kiện cho CS/manager → app.

Web của kho chỉ còn giá trị khi **tra tồn / kiểm kê / xem lịch sử phiếu** — tần suất thấp, không phải thao tác demo.

### 3.3 Vì sao CS phải ở web (không phải app)

CS làm 6 việc: tạo khách, tạo YC (nhiều trường: địa chỉ, liên hệ, khung giờ, số kiện), sửa YC sai,
**gán tài xế**, **gom chuyến** (chọn nhiều đơn), xử lý ngoại lệ (khách chưa xác định, thiếu địa chỉ).
Tất cả đều là **nhập liệu nhiều trường + nhìn nhiều dòng** ⇒ bàn phím và màn hình lớn. Trong app, cùng
việc đó phải chạm 8–12 lần và không thấy được toàn cảnh.

---

## 4. Chi tiết từng vai trò sau khi sắp xếp

### 4.1 Tài xế — APP (không đổi)
`Việc hôm nay` (lọc khung giờ hôm nay) → mở việc → **Bắt đầu đi** → **Chụp ảnh + Ký + Số kiện/kg** →
`Đã lấy được hàng` · nhánh lỗi: **Báo thất bại + lý do**.
State: `assigned → en_route → picked_up` (hoặc `failed`).

### 4.2 Trưởng nhóm tài xế — APP (không đổi)
`Phân công` → chọn việc `Mới` → chọn tài xế → **Gán tài xế** (nút khoá thành "Đã gán" sau khi xong —
đã sửa 2026-11-02). Thấy khung giờ cả ngày để chia việc.

### 4.3 Kho — APP (mở rộng; đây là phần phải code)
| Màn | Việc | State |
|---|---|---|
| **Nhận hàng** (có) | việc tài xế mang về → **Nhận hàng** → tự sinh phiếu nhập `WH/IN` | `picked_up → confirmed` |
| **Đang về** (MỚI, nhỏ) | danh sách `en_route`/`picked_up` hôm nay để chuẩn bị dock | — |
| **Xuất kho** (MỚI) | phiếu `WH/OUT` của các chuyến **Đã chốt booking / Đã khởi hành** → mở → đối chiếu kiện → **Xác nhận xuất** | picking `assigned → done` |
| **Kiểm đếm** (MỚI, P1) | khi nhận: nhập **số kiện thực tế** + ghi chú lệch | ghi vào `so_kien_thuc_te`/`ghi_chu` |

### 4.4 CS — WEB (bỏ app; đây là phần phải code)
1. **Khách hàng (Shop)** — có sẵn.
2. **Yêu cầu lấy hàng**: Tạo/Sửa (có sẵn) + **2 nút mới trên form**: **Huỷ việc**, **Gán tài xế**.
3. **Chuyến**: mở chuyến → tab *Đơn hàng trong chuyến* → thêm đơn bán (đã có) → **Mở chuyến**.
   (Cấp quyền ghi `lab.chuyen` cho nhóm CS; nút *Chốt booking/Khởi hành…* vẫn chỉ manager thấy.)
4. **Ngoại lệ**: sửa `partner_id` (khách chưa xác định), bổ sung địa chỉ/khung giờ.

### 4.5 Manager — WEB (không đổi)
Chuyến: Mở → **Chốt booking** → **Khởi hành** → **Đã đến nơi** → **Đã giao khách** → **Khoá chuyến**;
duyệt tier validation (chuyến 2 cấp, debit 1 cấp).

### 4.6 Chứng từ / Kế toán / Sếp / Admin — WEB (không đổi)
Chứng từ: Hành trình (AWB/HBL, mốc, POD, sự cố). Kế toán: hoá đơn + debit. Sếp: Bảng điều khiển +
app Tổng quan. Admin: quản trị.

---

## 5. Luồng 1 đơn sau khi sắp xếp (16 bước, cột "Nơi" là bề mặt chính)

| # | Việc | Ai | **Nơi** | Khác gì hiện tại |
|---|---|---|---|---|
| ① | Tạo khách | CS | WEB | — |
| ② | Tạo Yêu cầu lấy hàng | CS | WEB | — |
| ~~②b~~ | ~~Gán tài xế~~ | ~~leader / CS~~ | **APP (leader)** hoặc **WEB (CS)** | **CS gán được trên web (mới)** |
| ③ | Bắt đầu đi | Tài xế | APP | — |
| ④ | Ảnh + ký + đếm kiện | Tài xế | APP | — |
| ⑤ | Kho nhận hàng → `WH/IN` | Kho | APP | — |
| ⑥ | Gom việc vào chuyến | **CS** | WEB | **đổi chủ từ Kho/Manager → CS** |
| ⑦ | Mở chuyến | CS | WEB | **CS làm được (ACL mới)** |
| ⑧ | Chốt booking | Manager | WEB | — |
| ⑨ | **Xuất kho `WH/OUT`** | **Kho** | **APP (mới)** | **đổi từ WEB → APP** |
| ⑩ | Khởi hành (+AWB/HBL) | Manager / Chứng từ | WEB | — |
| ⑪ | Đến nơi | Manager | WEB | — |
| ⑫ | Giao khách + POD | Manager / Chứng từ | WEB | — |
| ⑬ | Khoá chuyến | Manager | WEB | — |
| ⑭ | Hoá đơn | Kế toán | WEB | — |
| ⑮ | Debit + duyệt | Kế toán → Manager | WEB | — |
| ⑯ | Đối soát | Sếp | WEB (+app đọc) | — |

---

## 6. Việc phải sửa để đạt §3 — xếp theo ưu tiên

### P0 — phải xong mới demo được theo phân vùng mới (≈ 1,5 ngày)

| # | Việc | Sửa ở đâu | Ước lượng |
|---|---|---|---|
| P0-1 | **Web: nút + wizard "Gán tài xế"** trên form YC lấy hàng (chọn tài xế → ghi `driver_id`, state `assigned`) | `lab_laixe/wizard/` mới + view + 1 test | 3h |
| P0-2 | **Cấp quyền CS ghi chuyến**: ACL `lab.chuyen`/`lab.chuyen.line` `1,0,0,0 → 1,1,1,0`; các nút Chốt/Khởi hành/Đến/Giao/Khoá thêm `groups=` chỉ manager+quản lý | `lab_chuyen` security + view | 2h |
| P0-3 | **Bỏ màn CS trên app**: role `cs` → màn "Vai trò này làm việc trên Odoo web"; giữ màn Phân công cho `nhomtruong01` | `Workspace.kt`, `App.kt` | 2h |
| P0-4 | **App: màn "Kho · Xuất kho"** + 2 API (`GET /kho/cho-xuat`, `POST /kho/pickings/<id>/xuat`) | `lab_api/controllers/kho.py` + app screen | 5–6h |
| P0-5 | **App: Kho thấy "Đang về"** (en_route + picked_up) để chuẩn bị dock | API `cho-nhan` mở rộng + UI tab | 1,5h |
| P0-6 | **Web: dọn menu theo vai trò** (bỏ "Chuyến/Hành trình" khỏi menu của Kho & tài xế; CS bỏ "Bảng điều khiển"; Kho chỉ còn YC + Chuyến(đọc) + Inventory) | `menuitems.xml` groups | 1h |
| P0-7 | **Web: nút "Huỷ việc"** trên YC (CS) — ngoại lệ ③ | view + `action_cancel` có sẵn | 1h |

### P1 — nên có, không chặn demo (≈ 1 ngày)
| # | Việc | Ghi chú |
|---|---|---|
| P1-1 | Kho nhập **số kiện thực tế** khi nhận (lệch kiện) | API `nhan` nhận thêm `soKienThucTe`, `ghiChu` |
| P1-2 | CS: nút **"Tạo đơn bán từ YC"** (giải quyết bước ⑦ đang phải tạo tay) | `sale.order` create + gắn `sale_order_id` |
| P1-3 | Kho: **kiểm đếm theo chuyến** (đối chiếu số kiện dự kiến vs thực tế) | màn app + API |
| P1-4 | Web: domain + quick-view cho cột "Đơn hàng của shop" | đã thêm domain 2026-11-02 |

### P2 — không làm trong phạm vi demo
Thông báo push, chat trong app, chế độ offline, in phiếu giao hàng từ app, bản đồ tuyến.

---

## 7. Những gì tôi xin anh DUYỆT (tick từng dòng)

| # | Quyết định | Đề xuất của tôi |
|---|---|---|
| D1 | **Bỏ màn "CS · Điều phối" khỏi app** — CS làm hoàn toàn trên web | ✅ nên bỏ |
| D2 | **Cấp quyền CS ghi `lab.chuyen` + `lab.chuyen.line`** để CS gom chuyến trên web | ✅ nên cấp (giữ Chốt booking cho manager) |
| D3 | **Kho rời web**: thêm màn "Xuất kho" trên app, kho không cần vào web nữa | ✅ nên làm (P0-4) |
| D4 | **Gán tài xế có 2 nơi**: web (CS, kế hoạch) + app (trưởng nhóm, điều chỉnh trong ngày) | ✅ giữ cả hai, cùng 1 API |
| D5 | **Giữ app "Sếp · Tổng quan"** (bản đọc, cho demo có app của sếp) | ✅ nên giữ |
| D6 | **Kho vẫn được xem web ở mức chỉ đọc** (tra tồn/kiểm kê khi cần) | ✅ nên giữ quyền đọc |
| D7 | Làm **P1-2 "Tạo đơn bán từ YC"** ngay trong đợt này? | ⬜ tuỳ anh — nếu bỏ thì demo vẫn phải tạo đơn bán tay |
| D8 | **Thứ tự thi công**: P0-3 (bỏ CS app) → P0-1/P0-2 (CS lên web) → P0-4/P0-5 (Kho app) → P0-6/P0-7 | ✅ theo thứ tự này |

---

## 8. Kịch bản demo 15 phút sau khi áp dụng (để thấy phân vùng có lý)

| Phút | Cảnh | Bề mặt | Tài khoản |
|---|---|---|---|
| 0–2 | Tạo khách + YC lấy hàng, **gán tài xế ngay trên form YC** | WEB (màn lớn) | `cs01` |
| 2–4 | Trưởng nhóm **phân công lại** việc cho tài xế khác | APP (máy tính bảng) | `nhomtruong01` |
| 4–7 | Tài xế bấm Bắt đầu đi → chụp ảnh → ký → đếm kiện | APP | `taixe01` |
| 7–9 | Kho thấy việc **đang về**, bấm **Nhận hàng** → sinh `WH/IN` | APP | `kho01` |
| 9–11 | CS **gom vào chuyến**, **Mở chuyến** | WEB | `cs01` |
| 11–12 | Manager **Chốt booking** | WEB | `manager01` |
| 12–13 | Kho mở **Xuất kho** trên app → **Xác nhận xuất** (`WH/OUT`) | **APP** | `kho01` |
| 13–14 | Manager Khởi hành → Đến nơi → Giao khách → Khoá | WEB | `manager01` |
| 14–15 | Sếp xem Bảng điều khiển + app Tổng quan | WEB + APP | `sep` |

Câu chốt khi demo: *"Ba người ngoài hiện trường chỉ cầm máy tính bảng; năm người văn phòng chỉ mở web.
Không ai phải làm việc của mình ở sai chỗ."*

---

## 9. Rủi ro & cách giảm

| Rủi ro | Giảm thế nào |
|---|---|
| Bỏ màn CS làm app "kém oai" khi demo | App vẫn 3 vai trò thật + màn sếp; phần kể chuyện nhấn "ai cũng dùng đúng công cụ" |
| Kho bấm Xuất kho sai → sai tồn | App chỉ cho validate phiếu `assigned` thuộc chuyến `Đã chốt booking`; log ai bấm; test 3 case (đúng, sai state, bấm 2 lần) |
| CS gom chuyến làm lệch tỉ lệ tải | `ti_le_tai` là compute stored, có cảnh báo quá tải sẵn |
| Hai nơi cùng gán tài xế gây tranh chấp | Cùng 1 API `assign`, server là nguồn sự thật; app khoá nút sau khi gán |
| Quên `restart odoo` sau khi sửa quyền | Đã ghi vào `workflow/chay-du-an.md` §1.4 |

---

## 10. Trạng thái tài liệu này

- [ ] Anh duyệt §7 (D1–D8)
- [ ] Tôi thi công theo thứ tự D8
- [ ] Sau mỗi P0: 1 ảnh chụp + 1 câu SQL/API kiểm chứng (không nói "xong" bằng lời)

Liên quan: `workflow/chay-du-an.md` (lệnh chạy), `workflow/flow-app-vs-web.md` (luồng 16 bước bản hiện tại),
`workflow/runbook.md` (sự cố đã đo).
