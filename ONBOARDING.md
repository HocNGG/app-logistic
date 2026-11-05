# ONBOARDING — Đọc 10 phút để hiểu dự án

> Dành cho người mới vào dự án, hoặc để bạn tự tin trả lời sếp.
> Mọi thuật ngữ tiếng Anh đều được giải thích ngay lần đầu xuất hiện. Glossary đầy đủ ở §7.

---

## 1. Câu chuyện 60 giây

Một **công ty gom hàng lẻ xuất khẩu** (tiếng Anh: *freight forwarder*, chuyên gom nhiều lô hàng nhỏ của nhiều shop lại thành một chuyến bay/tàu) đang làm việc bằng **Zalo + Excel**.

Hệ quả: mất dấu đơn, không ai biết hàng đang ở đâu, gom chuyến bằng tay nên quá tải, Debit (bảng kê cước gửi khách) bị sửa sau khi đã gửi, cuối tháng đối soát không khớp.

**Việc của dự án:** dựng lại toàn bộ quy trình đó trên **Odoo 17** (phần mềm ERP mã nguồn mở), thêm một **app desktop** cho tài xế và CS, và quay một **video demo 3 phút**.

**Quy mô nghiệp vụ:**

| Con số | Nghĩa |
|---|---|
| **19 bước** | Toàn bộ luồng, chia **10 pha** ("phase" = giai đoạn nghiệp vụ) |
| **6 phòng ban** | CS/Sale · Tài xế · Kho · Chứng từ · Kế toán · Sale Admin |
| **10 / 3 / 6** | 10 bước Odoo **đã có sẵn** · 3 bước chỉ **cấu hình** · 6 bước phải **viết code** |
| **3 module** | `lab_chuyen` (chuyến gom lô) · `lab_laixe` (điều tài xế) · `lab_hanhtrinh` (hành trình quốc tế) |
| **6 nhánh ngoại lệ** | Mỗi nhánh 5 bước, cuối nhánh **phải quay về luồng chính** |

**Câu thần chú của dự án:** *Native trước — cấu hình trước — chỉ viết code khi Odoo thật sự không có.*

---

## 2. Sơ đồ tổng thể — ai dùng cái gì

```
                    NGƯỜI DÙNG CUỐI
                           │
     ┌─────────────────────┼──────────────────────┐
     │                     │                      │
 Nhân viên             Tài xế              Khách / đại lý
 (CS, kho, kế toán,    (đi lấy hàng)       (hỏi hàng đang ở đâu)
  chứng từ, sếp)             │                      │
     │                     │                      │
     ▼                     ▼                      ▼
┌──────────────┐   ┌─────────────────┐   ┌────────────────────┐
│  ODOO WEB    │   │  APP KMP        │   │ LINK TRA CỨU       │
│  :8069       │   │  (desktop)      │   │ (portal, có token)  │
│  trình duyệt │   │  gọi API vào Odoo│   │ mở bằng trình duyệt │
└──────┬───────┘   └────────┬────────┘   └─────────┬──────────┘
       │                    │                      │
       └────────────────────┼──────────────────────┘
                            ▼
              ┌──────────────────────────────┐
              │        ODOO 17 CE            │  ← "bộ não" nghiệp vụ
              │  sale · stock · account ·    │
              │  portal · mail · spreadsheet │
              │  + OCA (thư viện cộng đồng)  │
              │  + 5 module của dự án        │
              └──────────────┬───────────────┘
                             ▼
                    ┌────────────────┐
                    │  PostgreSQL    │  ← nơi lưu dữ liệu
                    │  :5432         │
                    └────────────────┘
```

**Ba điều cần nhớ:**

1. **Odoo là trung tâm.** App và link tra cứu chỉ là *cửa vào* — mọi con số, mọi quy tắc nằm trong Odoo.
2. **App KMP không có cổng (port).** Nó là *client* (phía gọi), mở kết nối **ra** Odoo. Muốn xem kết quả thì nhìn cửa sổ app, không nhìn trình duyệt.
3. **Cổng duy nhất để xem nghiệp vụ là `http://localhost:8069`.**

---

## 3. Kiến trúc kỹ thuật — phần nào do ai viết

```
addons/

├── oca/                        ← thư viện cộng đồng OCA
│   ├── base_tier_validation    (duyệt nhiều cấp — từ OCA/server-ux)
│   └── helpdesk_mgmt           (khiếu nại — từ OCA/helpdesk)
│   ⚠️ KHÔNG AI SỬA — vendor read-only

├── lab_core        ← module "keo": nhóm quyền, mixin, field mở rộng
├── lab_chuyen      ← ⑤⑥⑦⑬⑭  chuyến gom lô, tải trọng, giảm tải, Debit
├── lab_laixe       ← ②        điều tài xế, ảnh, chữ ký, xác nhận
├── lab_hanhtrinh   ← ⑨⑩⑪     hàng đi → đến → đại lý giao, AWB/HBL/manifest
└── lab_api         ← cửa API cho app KMP (chỉ 5 endpoint)

apps/kmp/            ← app Kotlin Multiplatform (desktop target)
```

**Thứ tự cài (đừng đảo):** `lab_core` → `lab_chuyen` → `lab_laixe` → `lab_hanhtrinh` → `lab_api`.
Lý do: module sau cần dữ liệu của module trước (`lab_hanhtrinh` trỏ tới `lab.chuyen`).

**Vì sao phải có `lab_core`:** nếu không có nó, ba module kia buộc phải **phụ thuộc lẫn nhau** (dependency vòng) → không cài được, không nâng cấp được. Đây là **thực hành chuẩn của Odoo** (xem `sale_stock`, `stock_delivery` đều là module keo).

### Ai viết file nào (biên giới chống tranh chấp)

| Đuôi file | Chủ sở hữu | Nội dung |
|---|---|---|
| `*.py`, `security/*.csv`, `data/*.xml`, `tests/**` | **BE-01** | Model, quyền, dữ liệu, test |
| `views/*.xml`, `static/**`, `apps/kmp/**` | **FE-01** | Giao diện, app |
| `docs/**`, `workflow/**` | **RV-01** | Tài liệu, review, quy trình |
| `addons/oca/**` | không ai | Thư viện ngoài, chỉ đọc |

Muốn sửa file của người khác → mở **yêu cầu** trong `workflow/task-board.md`, không tự sửa.

---

## 4. Một đơn hàng đi qua hệ thống thế nào

Rút 19 bước thành **8 chặng** để dễ hiểu:

| # | Chặng | Phòng ban | Công cụ / model | Ghi chú |
|---|---|---|---|---|
| 1 | **Nhận đơn** từ shop | CS/Sale | `sale.order` (đơn hàng) | Báo giá → xác nhận |
| 2 | **Đi lấy hàng** | Tài xế | `lab.yeu.cau.lay.hang` | App KMP: ảnh · chữ ký · số kiện. *Hoặc* khách tự mang tới kho → bỏ qua chặng này |
| 3 | **Nhận tại kho** | Kho | `stock.picking` (phiếu kho) | Cân · đo · dán mã kiện vào `stock.quant.package` |
| 4 | **Đóng gói** | Kho | `stock.quant.package` | Kiện hàng vật lý |
| 5 | **Gom vào chuyến** | Kho + Sale Admin | `lab.chuyen` + `base_tier_validation` | Kéo nhiều đơn của nhiều shop vào 1 chuyến bay/tàu. **Quá 100% tải → cảnh báo đỏ, phải có người duyệt mới được gỡ đơn** |
| 6 | **Xuất kho + chứng từ** | Kho + Chứng từ | `stock.picking` + QWeb report | In AWB (vận đơn hàng không) / HBL (vận đơn đường biển) / manifest (bảng kê chuyến) |
| 7 | **Hành trình** | Chứng từ + đại lý | `lab.hanh.trinh` | Hàng đi → đến nơi → đại lý giao khách. Khách tự vào link tra cứu |
| 8 | **Tiền** | Kế toán | `account.move` (Debit) → `account.payment` | Cước tính theo kg → lập Debit → **duyệt rồi khoá** → hoá đơn → thu tiền → khoá tháng |

**Chặng 8 là chặng quan trọng nhất về niềm tin:** sau khi Debit bị khoá, **không ai sửa được** — muốn sửa phải có người duyệt mở khoá và nhập lý do (có ghi vào lịch sử).

---

## 5. Đội 3 agent vận hành thế nào

### 5.1 Khái niệm (giải thích từ gốc)

| Thuật ngữ | Nghĩa dễ hiểu |
|---|---|
| **LLM** | Mô hình ngôn ngữ — "động cơ" sinh chữ. **Không tự sửa được file**, không có tay |
| **Harness** | Phần mềm bọc quanh động cơ: cấp tool, kiểm quyền, giữ bộ nhớ, chạy lệnh. **Đây là thứ thật sự sửa file** |
| **Agent** | Một vòng lặp: đọc → nghĩ → gọi tool → xem kết quả → lặp. Có context (trí nhớ) riêng |
| **Subagent** | Agent con do agent mẹ spawn. Chạy nền, độc lập, xong thì báo về |
| **Orchestrator** | Người/agent điều phối — **chính là bạn + tôi (agent mẹ)** |
| **Sandbox** | Hàng rào quyền ghi file (dự án này: chỉ ghi trong `AppLogictics`) |

### 5.2 Luồng vận hành

```
BẠN  ──ra lệnh / duyệt ADR / hỏi──►  TÔI (agent mẹ = orchestrator)
                                      │
                     gọi 3 subagent trong 1 lượt (chạy nền, song song)
                                      │
      ┌───────────────────────────────┼───────────────────────────────┐
      ▼                               ▼                               ▼
   BE-01                           FE-01                           RV-01
 (Odoo backend)              (app KMP + UI)                   (review, chấm điểm)
      │                               │                               │
      └──────────────► CÙNG MỘT THƯ MỤC ◄─────────────────────────────┘
                     (kênh liên lạc duy nhất = file)
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
    workflow/handoff.md   workflow/task-board.md   docs/review/*.md
    (báo xong + bằng chứng) (yêu cầu chéo)          (verdict: PASS/BLOCKED)
```

### 5.3 Ba điều bắt buộc phải biết

1. **Subagent không nói chuyện được với nhau.** Chúng chỉ "thấy" nhau qua file. Vì vậy mới có `task-board.md` và `handoff.md`.
2. **Ba con dùng chung một credential** (khoá model) vì credential thuộc **harness**, không thuộc từng agent.
3. **RV-01 là "cổng" (gate), không chạy song song kiểu làm việc.** Nhịp đúng là: **BE + FE song song → xong → RV review**. RV review code chưa tồn tại là đốt thời gian.

### 5.4 Chu trình mỗi phase

```
① Đọc luật (AGENTS.md)  →  ② Làm 1 "vertical slice" (lát cắt dọc nhỏ chạy được)
→ ③ Test  →  ④ Handoff (kèm bằng chứng chạy thật + Reuse Proof)
→ ⑤ RV review (PASS / CHANGES-REQUESTED / BLOCKED)  →  ⑥ Sang phase tiếp
```

---

## 6. Cách xem kết quả (câu hỏi hay bị hỏi nhất)

| Muốn xem gì | Mở cái gì |
|---|---|
| Màn hình Odoo, đơn hàng, chuyến, Debit | `http://localhost:8069` |
| App tài xế / CS | `cd apps/kmp; ./gradlew :composeApp:run` → **cửa sổ desktop** |
| Link khách tra cứu | `http://localhost:8069/lab/track/<token>` |
| Dashboard cho sếp | menu *Dashboard* trong Odoo |
| Kết quả test | terminal, dòng `0 failed, 0 error(s)` |
| Video demo | `demo/out/applogictics-demo.mp4` |

Bảng đầy đủ + lệnh copy-paste: **`workflow/runbook.md`**.

---

## 7. Glossary — thuật ngữ hay gặp

### 7.1 Nghiệp vụ logistics

| Thuật ngữ | Nghĩa |
|---|---|
| **Gom hàng lẻ** (consolidation) | Gom nhiều lô nhỏ của nhiều khách vào 1 chuyến |
| **Chuyến** | Một chuyến bay hoặc chuyến tàu cụ thể, có hạn chốt (cut-off) và tải trọng |
| **Kiện** | Đơn vị đóng gói vật lý (thùng, bao) |
| **Debit** | Bảng kê cước gửi khách — **tiền đề** của hoá đơn, không phải hoá đơn |
| **AWB / HBL** | Vận đơn hàng không / vận đơn đường biển |
| **Manifest** | Bảng kê toàn bộ hàng của một chuyến |
| **CBM** | Mét khối (thể tích) |
| **Chargeable weight** | Kg dùng để tính cước = `max(kg thực, kg thể tích)` |
| **Kho chờ** | Khu vực để hàng chưa xác định được của shop nào |
| **Giảm tải** | Gỡ bớt đơn khỏi chuyến khi vượt sức chứa |

### 7.2 Odoo / kỹ thuật backend

| Thuật ngữ | Nghĩa |
|---|---|
| **ERP** | Hệ thống quản trị doanh nghiệp (bán hàng, kho, kế toán… trong 1 chỗ) |
| **Odoo** | Phần mềm ERP mã nguồn mở; bản **CE** = Community (miễn phí), **EE** = Enterprise (trả tiền) |
| **OCA** | Odoo Community Association — cộng đồng viết module mở rộng |
| **Model** | Bảng dữ liệu trong Odoo (ví dụ `sale.order` = đơn hàng) |
| **ORM** | Lớp giúp lập trình viên làm việc với DB bằng Python thay vì SQL |
| **View** | Định nghĩa giao diện (danh sách, form, kanban…) |
| **`picking`** | Phiếu kho (nhập/xuất/chuyển) |
| **UoM** | Đơn vị đo (kg, m³…) |
| **Pricelist** | Bảng giá |
| **Journal** | Sổ kế toán (mỗi loại nghiệp vụ 1 sổ) |
| **Tier validation** | Duyệt nhiều cấp (nhân viên → trưởng nhóm → manager) |
| **QWeb** | Ngôn ngữ mẫu để in PDF/báo cáo trong Odoo |
| **Portal** | Cổng cho người ngoài (khách) đăng nhập/xem |
| **`access_token`** | Chuỗi bí mật trong link để khách xem hàng **không cần đăng nhập** |
| **Native-first** | Nguyên tắc: dùng cái có sẵn trước khi viết mới |
| **Reuse Proof** | Bằng chứng "tôi đã kiểm tra Odoo chưa có cái này" |
| **ADR** | Architecture Decision Record — ghi lại quyết định kiến trúc **và phương án bị loại** |
| **DoD** | Definition of Done — danh sách điều kiện để coi là "xong" |

### 7.3 App / frontend

| Thuật ngữ | Nghĩa |
|---|---|
| **KMP** | Kotlin Multiplatform — viết 1 lần, chạy nhiều nền tảng (ở đây: desktop) |
| **Compose** | Bộ công cụ vẽ giao diện của Kotlin |
| **Duo UI** | Bố cục 2 khung cạnh nhau (như iPhone Duo gập đôi màn hình), tự đổi khi cửa sổ hẹp |
| **Displacement** | Khi đổi kích thước, nội dung **không** bị mất (giữ nguyên đang chọn/đang gõ) |
| **Offline-first** | Mất mạng vẫn làm được, có mạng thì tự đồng bộ |
| **Outbox** | "Hộp thư đi" — hàng chờ gửi lên server khi có mạng |
| **Idempotency** | Gửi 2 lần vẫn chỉ tính 1 lần (chống trùng) |

### 7.4 Tích hợp

| Thuật ngữ | Nghĩa |
|---|---|
| **JSON-RPC** | Cách gọi hàm từ xa qua HTTP bằng JSON (Odoo dùng cái này) |
| **API key** | Khoá thay mật khẩu cho app — **không** lưu mật khẩu người dùng |
| **Session cookie** | "Thẻ ra vào" server trả về sau khi đăng nhập (Odoo 17 trả **chỉ** qua cookie) |
| **Endpoint** | Một địa chỉ API cụ thể (ví dụ `/lab/api/v1/driver/jobs`) |

### 7.5 Vận hành đội agent

| Thuật ngữ | Nghĩa |
|---|---|
| **Spawn** | Khởi tạo (một agent) |
| **Handoff** | Bàn giao — báo xong kèm bằng chứng |
| **Verdict** | Kết luận review: PASS / CHANGES-REQUESTED / BLOCKED / REJECT-SCOPE |
| **P0 / P1** | Mức độ lỗi: P0 = chặn ngay, P1 = phải sửa trước bàn giao |

---

## 8. Mười câu sếp sẽ hỏi — và câu trả lời mẫu

| # | Câu hỏi | Trả lời trong 30 giây |
|---|---|---|
| 1 | *"Bài toán là gì?"* | Gom hàng lẻ xuất khẩu: 19 bước / 6 phòng ban, đang làm bằng Zalo + Excel. Em dựng trên Odoo 17 + app cho tài xế. |
| 2 | *"Sao không mua phần mềm sẵn?"* | Vì nghiệp vụ cốt lõi — **gom nhiều đơn nhiều khách vào 1 chuyến bay/tàu** — Odoo không có. Nhưng **10/19 bước** Odoo đã làm sẵn, em chỉ viết **3 module**. |
| 3 | *"Sao chỉ 3 module?"* | Vì phần còn lại là cấu hình native: `sale.order`, `stock.picking`, `account.move`, `base_tier_validation`, `spreadsheet_dashboard`. Em có **audit script** chứng minh 0 model trùng. |
| 4 | *"AI ở đâu hay chỉ demo?"* | Có 2 lớp: AI trong quy trình (sinh test, seed data, kịch bản video, review chéo — có `docs/ai-log.md`) và AI trong sản phẩm (voice tiếng Việt, command palette). Không có AI giả lập. |
| 5 | *"Chạy được chưa, xem ở đâu?"* | `http://localhost:8069` cho Odoo; app desktop chạy `./gradlew :composeApp:run`. Bảng cổng ở `workflow/runbook.md`. |
| 6 | *"Nếu 1.000 đơn/ngày?"* | Nêu đúng 3 chỗ sẽ nổ trước: danh sách chuyến (cần index + phân trang), báo cáo pivot, dung lượng ảnh attachment. Cách vá có sẵn trong `docs/08` §6. |
| 7 | *"Ai được thấy gì?"* | 8 nhóm quyền theo phòng ban, phân quyền bằng `ir.rule` của Odoo (không phải `if` trong code). Tài xế A **không** thấy việc của tài xế B — có test chứng minh. |
| 8 | *"Nếu chuyến quá tải?"* | Hệ thống chặn: hiện đỏ > 100%, muốn gỡ đơn phải có **trưởng nhóm duyệt**, đơn bị gỡ về danh sách "chờ chuyến sau" chứ không biến mất. Có test. |
| 9 | *"Mất bao lâu?"* | 10 phase, ~17 ngày-người, 6–8 ngày lịch nếu 3 agent chạy đúng nhịp. Sổ rủi ro ở `docs/03` §6. |
| 10 | *"Em dùng AI thế nào?"* | Nêu 1 câu hỏi tốt cụ thể + 1 lần AI trả lời sai và cách phát hiện (có trong `docs/ai-log.md`). Đây là câu được chấm nhất. |

---

## 9. Bản đồ tài liệu (muốn đào sâu thì đọc gì)

| Muốn hiểu | Đọc |
|---|---|
| Nghiệp vụ chi tiết | `docs/00-nghiep-vu.md` |
| Cái gì dùng sẵn, cái gì phải viết | `docs/01-tai-su-dung-odoo17.md` |
| Kiến trúc, model, phân quyền | `docs/02-kien-truc.md` |
| Kế hoạch 10 phase | `docs/03-ke-hoach.md` |
| Đặc tả app desktop | `docs/04-kmp-app.md` |
| Yêu cầu của sếp → 12 instruction | `docs/05-yeu-cau-cua-sep.md` |
| Kịch bản video demo | `docs/06-demo-video.md` |
| 6 nhánh ngoại lệ | `docs/07-exception-flows.md` |
| Đo lường, con số nói với sếp | `docs/08-do-luong-kpi.md` |
| Câu hỏi mở + giả định | `docs/09-open-questions.md` |
| Quyết định kiến trúc | `docs/adr/` |
| Luật chung của đội | `AGENTS.md` |
| Quy trình làm việc | `workflow/` (handoff · DoD · task-board · runbook · skill-import) |
