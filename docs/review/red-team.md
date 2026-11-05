# RED-TEAM — "Cái gì sẽ nổ trước?"

> Artifact bắt buộc của **I-12 Critical Thinking** (`docs/05`). RV-01 duy trì file này; BE-01/FE-01 phải trả lời được từng dòng khi được hỏi trong demo.
> Mỗi dòng: **kịch bản** → **chỗ nổ** → **phát hiện sớm** → **trạng thái xử lý**.

| # | Kịch bản | Chỗ sẽ nổ | Phát hiện sớm | Xử lý | Trạng thái |
|---|---|---|---|---|---|
| 1 | Chuyến vượt tải 120% | `lab.chuyen.line` gán vượt `tai_trong_max_kg`, cước sai, hãng từ chối | compute `ti_le_tai` + warning đồng hồ | nhánh ②: wizard giảm tải + tier validation | ⏳ P6 |
| 2 | Debit đã khoá, khách yêu cầu sửa | không sửa được; nếu cho sửa → mất kiểm soát | test `test_debit_lock` | mở khoá có duyệt + lý do + chatter, hoặc credit note | ⏳ P5 |
| 3 | Gỡ đơn khỏi chuyến bằng `unlink` | mất vết, báo cáo sản lượng thiếu, Debit đã lập vẫn tính đơn đó | so sánh số dòng `lab.chuyen.line` trước/sau | **cấm unlink**; dùng `state='waitlist'` (OQ-05) | ✅ đã chốt |
| 4 | Xuất kho xong mới phát hiện thiếu kiện | chuyến đã đi, khách nhận thiếu | kiểm đếm package khớp manifest trước khi validate | backorder + tìm/bổ sung + bồi thường (nhánh ④) | ⏳ P6 |
| 5 | Km/kg lệch giữa picking và Debit | cước sai, khách khiếu nại, đối soát không khớp | `test_weight_consistency` so 3 chứng từ | chặn tạo Debit khi picking chưa done | ⏳ P5 |
| 6 | Tài xế upload ảnh 2 lần (offline retry) | trùng attachment, trùng xác nhận | `client_uuid` idempotency | worker tuần tự + dedupe theo uuid | ⏳ P3 |
| 7 | Tài xế A đọc được job của tài xế B | lộ địa chỉ/số điện thoại khách | test security 2 user | `ir.rule` theo `driver_id.user_id` | ⏳ P2 |
| 8 | Đổi chuyến air → sea sau khi đã lập Debit | divisor 6000 → 1000, cước đổi, tiền lệch | chặn chuyển khi Debit đã post | yêu cầu mở khoá trước (nhánh ⑤ + ⑭) | ⏳ P6 |
| 9 | Khách không bao giờ lấy hàng (30 ngày) | hàng treo ở KHO CHỜ, tồn kho ảo | báo cáo tuổi hàng chờ | quy trình xử lý hàng tồn — **ngoài phạm vi**, ghi backlog | 📋 backlog |
| 10 | 1.000 đơn/ngày thay vì 10 | list chậm, `read_group` N+1, attachment phình | đo thời gian load list 500 dòng | `_read_group`, phân trang, index `_order` | ⏳ P5/P8 |
| 11 | Mất mạng giữa lúc tài xế confirm | mất dữ liệu, không biết đã gửi chưa | outbox badge + trạng thái pending | outbox bền vững trên đĩa, retry tuần tự | ⏳ P3 |
| 12 | Tier validation chưa cấu hình | không ai duyệt được → kẹt toàn luồng | test: tạo chuyến và duyệt bằng user manager | cấu hình trong data XML, có test | ⏳ P2 |
| 13 | Font PDF tiếng Việt lỗi (ô vuông) | AWB/HBL in ra không đọc được — lỗi demo nổi bật nhất | in thử PDF ở P4, không để đến P9 | nhúng font DejaVu vào module + test in | ⏳ P4 |
| 14 | Session/token hết hạn giữa demo | app trắng màn hình | test token 8h + refresh path | refresh token im lặng, fallback login nhanh | ⏳ P3 |
| 15 | Sếp/nhà tuyển dụng hỏi "AI ở đâu?" | AI chỉ là voice-over → mất điểm | tự hỏi trước: AI có dữ liệu thật không? | nếu không đủ → **cắt khỏi demo**, không diễn (Luật 9) | ⏳ P9 |
| 16 | **`.ps1` thiếu BOM UTF-8** (đã xảy ra 2 lần trong dự án này) | `infra/scripts/reset-demo.ps1` (hiện **đang thiếu**): PS 5.1 đọc ANSI → `LỖI` thành `Lá»–I`; so sánh/path có dấu sai; log demo không đọc được | kiểm 3 byte đầu mọi `.ps1` phải là `EF BB BF`; đọc thử bằng `powershell` 5.1 (không phải pwsh 7) | lưu UTF-8 **có BOM**; thêm bước kiểm BOM vào `check.ps1`; cấm xuất `.ps1` từ editor mặc định | ⏳ **P0** — 1/4 file thiếu (`P0-baseline.md` P1-02) |
| 17 | **`bootstrap.ps1` chạy trên máy có JDK 25 mặc định** | `bootstrap.ps1:24` hard-code `jdk-21.0.10`; `build.gradle.kts:12` đặt `jvmToolchain(21)`; wrapper lấy `java` đầu PATH = JDK 25 → `Unsupported class file major version 69` hoặc Gradle đòi tải toolchain (máy demo không mạng) | `.\gradlew.bat --version` phải in `JVM: 21.x`; `java -version` trên **máy demo**, không phải máy dev | bootstrap đặt `JAVA_HOME` (đã có) + **in JVM ra** cuối script; test trên máy lạ trước P3; ghi 1 dòng vào runbook §6 | ⏳ **P0** |
| 18 | **Gradle cache lạnh / máy không mạng** (CI hoặc máy người chấm) | `:composeApp:compileKotlinDesktop` đỏ vì phải tải Kotlin/Compose từ Maven; CI P0-06 chưa tồn tại → "xanh trên máy tôi" | `.\gradlew.bat --offline :composeApp:compileKotlinDesktop` trên máy mới; kiểm `~/.gradle/caches/modules-2` có sẵn | wrapper jar + `libs.versions.toml` khoá tuyệt đối (đã có); `check.ps1 -Offline`; phương án B: build sẵn `packageUberJarForCurrentOS` để demo không cần JDK | ⏳ **P0** |
| 19 | **Module thiếu `depends` → cài sai thứ tự** | `-i lab_hanhtrinh` một mình → `KeyError`/field m2o trỏ `lab.chuyen` chưa tồn tại; `docs/02:46` và `:48` đang **mâu thuẫn** về việc `lab_hanhtrinh` có depends `lab_chuyen` không; `lab_api` phải sau 3 module nghiệp vụ | cài **từng module riêng** trên DB trắng (5 lượt); đọc 5 `__manifest__.py` đối chiếu `docs/02` §1 | chốt thứ tự `lab_core → lab_chuyen → lab_laixe → lab_hanhtrinh → lab_api`; test cài lẻ; sửa `docs/02` §1 | ⏳ **P0** — chặn bởi P1-06 |
| 20 | **Hai agent cùng sửa 1 file** (không có git để diff) | `__manifest__.py` do BE sở hữu nhưng phải liệt kê `views/*.xml` của FE + `security/` + `data/`; FE thêm view mà manifest không đổi → view **im lặng không load**; 2 người cùng ghi → mất phần của nhau, không rollback được | đối chiếu file XML có trên đĩa với danh sách `data` trong manifest (đưa vào `check.ps1`); hash snapshot trước/sau mỗi handoff | quy ước **chỉ BE-01 sửa `__manifest__.py`**, FE mở cross-request; `git init` hoặc hash manifest (P1-05) | ⏳ **P0** — phụ thuộc P1-05 |

> **Bổ sung 2026-10-28 (RV-01, Wave A):** #16–#20 là 5 kịch bản **riêng của P0** — chúng nổ ở giai đoạn dựng móng, không nằm ở nghiệp vụ 19 bước. Bằng chứng hiện có: `docs/review/P0-baseline.md` §11.B (mojibake thật) và §11.C (Docker tắt, không git, không `rg`). Kịch bản #16 **đã xảy ra** (1/4 file `.ps1` đang thiếu BOM) — không còn là giả định.

## Câu hỏi phản biện bắt buộc trả lời trong demo

> **"Nếu công ty này có 1.000 đơn/ngày thay vì 10, cái gì sẽ nổ trước?"**

Câu trả lời đúng phải **cụ thể** (nêu 2–3 điểm + cách vá), không được trả lời "em sẽ scale bằng cloud".

## Cách dùng file này

1. **Trước mỗi phase:** đọc các dòng liên quan phase đó.
2. **Trong phase:** dòng nào chuyển `⏳` → `✅` thì ghi bằng chứng (test/lệnh).
3. **Trước demo (P9):** không còn dòng `⏳` nào ở các nhánh **được demo**. Dòng nào còn → nói trước là "chưa làm", không giấu.
| 21 | Script `.ps1` dùng `$PSScriptRoot` trong `param()` default | Crash ngay dòng 1 dưới PS 5.1 (`$PSScriptRoot` rỗng), tưởng là lỗi mạng | Chạy script với tham số sai chủng loại; đọc dòng lỗi đầu tiên | Tính `$Repo` trong **thân** script, không trong `param()` | ✅ đã sửa 2 script 2026-10 |
| 22 | Test OCA import thư viện ngoài (`odoo_test_helper`) khi bật `--test-tags` | Odoo dựng suite cho MỌI module trong graph → ModuleNotFoundError → CRITICAL, **test của dự án không bao giờ chạy** | Chạy test 1 lần trước khi tin "code xong" | `infra/Dockerfile.odoo` cài sẵn dependency test; image riêng đã pin | ✅ đã sửa + verify 2026-10 |
| 23 | `_inherit = ["model.goc", "mixin"]` thiếu `_name` | Odoo coi là model MỚI (cảnh báo "has no _description"), field không vào registry → One2many ở model khác ném `KeyError` | grep class có `_inherit = [` mà không có `_name` | Thêm `_name = "model.goc"`; nhớ: 3 class kia có `_name` TRƯỚC `_inherit` nên đừng quét chỉ phía sau | ✅ sửa 2026-10 |
| 24 | Field compute không `store` nhưng dùng trong search/decorations | `Unsearchable field` khi validate view | rà mọi `compute=` thiếu `store=True` rồi grep view dùng nó | `store=True` khi field tham gia domain/sort/decoration | ✅ sửa 2026-10 |
| 25 | Portal user (base.group_portal) đọc model internal (`hr.employee`, `ir.attachment`) | 403 trước khi vào logic; test HTTP đỏ hàng loạt | chạy 1 test HttpCase là đủ lộ | Cột **stored** để lọc (vd `driver_user_id`) thay vì traverse; hoặc `sudo()` + `# sudo-ok:` chỉ cho đọc đúng bản ghi đã qua `ir.rule` | ✅ sửa 2026-10 |
| 26 | User đăng nhập bằng browser báo tz `Asia/Saigon` | Postgres trong image **không có alias đó** (chỉ có `Asia/Ho_Chi_Minh`) → báo cáo `sale.report` của user ấy lỗi `time zone "Asia/Saigon" not recognized`; Odoo vẫn chạy nên rất dễ bỏ qua | `docker compose logs odoo | Select-String Saigon` | `update res_partner set tz='Asia/Ho_Chi_Minh' where tz like 'Asia/Saigon%'` — đã tự động hoá ở `setup.ps1` bước 6b | ✅ sửa 2026-10-28 |
