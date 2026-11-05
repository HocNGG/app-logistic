## Dùng khi nào

Làn 1 — chạy trong **mọi** lần review (1 commit nhỏ cũng chạy), bắt buộc trước sync point S1/S3 và trước demo.
Đầu vào: commit message + `workflow/handoff.md` của BE-01/FE-01 + cây `addons/`.
Đầu ra: danh sách issue P0/P1 có `file:dòng`. Chưa xong bước 1 thì **không đọc dòng code nào khác**.

---

## 1. Cổng chặn cứng — "Reuse Proof" (Luật 1)

Không có mục Reuse Proof trong commit/handoff → verdict **BLOCKED** ngay, không chấm tiếp.

| # | Phải có | ĐÚNG | SAI → BLOCKED |
|---|---|---|---|
| 1 | Lệnh grep đã chạy + kết quả | `rg "volumetric" addons/` → 0 hit | "đã tìm hiểu trên mạng" |
| 2 | Model/field native đã so sánh | `stock.picking.weight`, `stock.move.line` | để trống |
| 3 | Vì sao native không đủ (cụ thể) | "`picking.weight` chỉ là tổng weight move, không có thể tích" | "cần cho nghiệp vụ" |
| 4 | Bậc L0–L3 (+ ADR nếu L3) | `L3` + `docs/adr/ADR-003-chuyen-vs-batch.md` | "L3 vì cần" |
| 5 | Field inherit thay vì model mới | `sale.order.lab_pickup_mode` | tạo model `pickup` |

Thiếu dòng 1 hoặc dòng 3 → coi như **không có Reuse Proof**.

## 2. Chín lệnh audit bắt buộc

Chạy tại gốc repo. Ký hiệu `<L>` = `addons/lab_core addons/lab_chuyen addons/lab_laixe addons/lab_hanhtrinh addons/lab_api`.

| # | Lệnh | Phải ra | Có hit → kết luận | Mức |
|---|---|---|---|---|
| A1 | `rg -n "^\s*_name\s*=" addons/lab_*` | chỉ model trong whitelist §3 | model lạ → viết lại Odoo | **P0** |
| A2 | `rg -n "attrs=\|states=" addons/lab_*/views` | rỗng | view Odoo ≤16, `_check_xml` sẽ `ValidationError` | **P0** |
| A3 | `rg -n "quantity_done\|qty_done\|reserved_uom_qty" addons/lab_*` | rỗng | API 16, 17 đã đổi tên | **P0** |
| A4 | `rg -n "has_group" addons/lab_*/models` | rỗng (hoặc ADR + comment) | phân quyền bằng `if` thay `ir.rule` | **P0** |
| A5 | `rg -n "sudo\(\)" addons/lab_*` | mỗi dòng có comment lý do | `sudo` không giải trình → mở đường vòng quyền | **P0** |
| A6 | `rg -n "stock\.package[^.]" addons/lab_*` | rỗng | tên model của 18.0; 17 là `stock.quant.package` | **P0** |
| A7 | `rg -n "<list\b" addons/lab_*/views` | rỗng | tag của 18.0; 17.0 dùng `<tree>` | **P0** |
| A8 | `rg -n "read_group\(" addons/lab_*` | rỗng | 17 dùng `_read_group(domain, groupby, aggregates)` | **P1** |
| A9 | `rg -n "hard_lock_date" addons/lab_*` | rỗng | 17 dùng `period_lock_date` / `fiscalyear_lock_date` | **P0** |

Lệnh bổ sung (cùng lần chạy): `rg -n "SavepointCase\|models\.Constraint\|is_storable\|cr\.commit\(\)" addons/lab_*` (phải rỗng) · `rg -n "TODO" addons/lab_*` (mỗi TODO phải là `TODO(BE-01): …`) · `rg -n "Bearer" apps/kmp` (chỉ hợp lệ khi trỏ `/lab/api/v1/*` do ta tự dựng, không trỏ `/web/*`).

## 3. Whitelist model — chỉ 5 module, không hơn

Được phép: model khai báo trong `docs/02` §2. Tất cả nằm trong `addons/lab_{core,chuyen,laixe,hanhtrinh,api}`.

`lab.flow.mixin` (abstract) · `lab.chuyen`, `lab.chuyen.line` · `lab.yeu.cau.lay.hang`, `lab.pickup.line` · `lab.hanh.trinh`, `lab.hanh.trinh.line` · `lab.config.settings` + wizard **transient** (`lab.giam.tai.wizard`, `lab.gan.khach.wizard`, `lab.chuyen.kho.wizard`, `lab.lap.debit.wizard`).

Whitelist trong `infra/scripts/audit-native-first.ps1` là **bản thi hành** của danh sách này — lệch nhau là P2: `rg "lab\.[a-z.]+" infra/scripts/audit-native-first.ps1` và đối chiếu `docs/00` §6 + `docs/07`.

| Gặp `_name` | Xử lý |
|---|---|
| Thuộc whitelist trên | OK, sang làn 2 |
| Transient wizard mới, không có trong `docs/07` | P1: thiếu ADR + thiếu dòng trong `docs/02` |
| `debit` `cuoc` `kien` `lo_hang` `khoi_luong` `tai_xe` `khieu_nai` | **P0 BLOCKED**, trích `docs/01` §4 |
| Model **persist** mới ngoài 5 module | **P0 BLOCKED**, đòi ADR + Orchestrator duyệt |
| Sửa `addons/oca/**` | **P0 BLOCKED** — vendor read-only (Luật 10) |

## 4. Họ viết X → native Y đã làm rồi

| Họ viết | Native đã có | Bước | Nói gì |
|---|---|---|---|
| model `cuoc`, `phi_van_chuyen` | `sale.order.line` + product UoM kg + `product.pricelist` | ⑫ | "Cước là dòng đơn hàng, không phải bảng riêng" |
| model `debit` | `account.move` journal Debit, `move_type='out_invoice'` | ⑬ | "Viết lại Debit = mất hạch toán, thuế, đối soát" |
| model `kien_hang` | `stock.quant.package` + `stock.package.type` | ④ | "Kiện là package của `stock`" |
| model `can_hang`, `khoi_luong` | field trên `stock.move.line` + `stock.picking.weight` | ③ | "Cân nặng là field, không là bảng" |
| state mới trên `stock.picking` cho hành trình | `lab.hanh.trinh` (picking đã `done` khi hàng rời kho) | ⑨⑩⑪ | "Picking không theo được hàng ngoài kho" |
| field `duyet_1`, `duyet_2` | `base_tier_validation` (OCA **server-ux**) | ⑥⑭ | "Duyệt nhiều cấp là config" |
| `if user.has_group(...)` | `ir.rule` + field `groups` + `menuitem groups` | — | Luật 3, không thương lượng |
| cron Python nhắc việc | `mail.activity` + `base_automation` | ③ | "Nhắc việc là native" |
| Python xuất Excel/PDF | pivot view + `spreadsheet` / `ir.actions.report` + QWeb | ⑧ | "Report QWeb, không ReportLab" |
| controller tự viết cho tra cứu khách | `portal.mixin` + `access_token` | ⑪ | "Token có sẵn, đừng tự chế" |
| JWT tự chế cho app | `/web/session/authenticate` + API key (`res.users.apikeys`) | ② | "17.0: API key thay password" |
| model `tai_xe` | `hr.employee` + `fleet.vehicle` | ② | "Tài xế là employee" |
| model `khieu_nai` | `helpdesk_mgmt` (OCA **helpdesk**) | ⑥ | "Khiếu nại là ticket" |
| HTML dashboard tự vẽ | `spreadsheet.dashboard` (CE 17) + pivot/graph | Báo cáo | "Dashboard là CE, không cần code" |

## 5. Từ vựng cờ đỏ — thấy là dừng, đọc kỹ

```powershell
rg -n "_name\s*=\s*['\"](debit|cuoc|phi_van_chuyen|kien|kien_hang|lo_hang|khoi_luong|tai_xe|khieu_nai)" addons
rg -o --no-filename "_name\s*=\s*['\"][a-z_.]+" addons --glob "*.py" | Sort-Object -Unique   # đối chiếu whitelist §3
rg -n "if demo|is_demo|# tạm|magic|hardcode|HACK|XXX" addons/lab_* apps/kmp
```

Cờ đỏ phi-kỹ-thuật (đọc trong handoff/PR): *"tạm thời"*, *"cho kịp demo"*, *"sau này sửa"*, *"Odoo không có"* (không kèm lệnh grep).

## 6. Script audit — dùng bản có sẵn, không viết lại

Cả 9 lệnh §2 đã được đóng gói trong `infra/scripts/audit-native-first.ps1` (luật A1–A13, fallback `Select-String` khi chưa có `rg`):

```powershell
powershell -File infra/scripts/audit-native-first.ps1
# exit 0 = không có P0/P1 · exit 1 = có vi phạm · log: docs/review/audit-native-first.log
```

RV-01 **chỉ chạy và đọc log** (`PASS/FAIL <Id> [P0|P1] …` + dòng `KET LUAN:`), không sửa script, không sửa code. Log này là bằng chứng bắt buộc trong `docs/review/P<x>-<verdict>.md`.

Nếu script chưa tồn tại → chạy tối thiểu 4 lệnh và ghi log bằng tay, rồi mở issue P1 "thiếu script audit":

```powershell
"# audit tay $(Get-Date -Format s)" | Out-File docs/review/audit-native-first.log -Encoding utf8
rg -n "^\s*_name\s*=" addons --glob "*.py"  | Tee-Object -Append docs/review/audit-native-first.log   # A1
rg -n "\battrs\s*=|<list\b" addons --glob "*.xml" | Tee-Object -Append docs/review/audit-native-first.log  # A2 A7
rg -n "quantity_done|qty_done" addons --glob "*.py" | Tee-Object -Append docs/review/audit-native-first.log  # A3
rg -n "has_group\(" addons --glob "*.py"          | Tee-Object -Append docs/review/audit-native-first.log   # A4
```

Chưa có `rg` → cài `winget install BurntSushi.ripgrep.MSVC`, hoặc thay bằng `Select-String -Path`; ghi rõ trong log là dùng công cụ nào.

## 7. Mẫu issue khi vi phạm (theo `agents/review/AGENT.md` §5)

Mỗi phát hiện **phải** có đủ 7 ô, không có ngoại lệ: `[P0-P3]` tiêu đề · **File:** `addons/...:dòng` · **Vi phạm:** Luật số mấy + `docs/01` bước nào · **Vì sao sai** (1-3 câu, hệ quả nghiệp vụ) · **Đề xuất** (thay bằng gì) · **Bằng chứng cần có** (test/lệnh) · **Chặn** phạm vi nào.
RV-01 **không sửa code**: issue nằm ở `docs/review/P<x>-<verdict>.md`, người khác áp dụng.

## Checklist nhanh

- [ ] Có mục Reuse Proof, trả lời đủ 5 dòng §1
- [ ] A1: mọi `_name` nằm trong whitelist §3 (hoặc có ADR)
- [ ] A2–A9 chạy đủ, kết quả dán vào `docs/review/audit-native-first.log`
- [ ] Không có model trùng ngữ nghĩa native (§4 đã đối chiếu từng dòng)
- [ ] `addons/oca/**` không bị sửa (`git diff --stat addons/oca` rỗng)
- [ ] Mỗi `sudo()` có comment lý do; mỗi `TODO` có tên người
- [ ] Không có cờ đỏ phi-kỹ-thuật trong handoff ("tạm thời", "cho kịp demo")
- [ ] Mỗi vi phạm đã thành issue 7 ô, có mức P0–P3 và phạm vi chặn
