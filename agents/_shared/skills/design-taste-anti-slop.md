# Skill — Design taste, chống "giao diện AI đại trà"

> **Nguồn (đã kiểm chứng, không phải phỏng đoán)**
> - `https://github.com/Leonxlnx/taste-skill` — skill `design-taste-frontend` (tasteskill v2), nhánh `main`, cập nhật 2026-10-28, **MIT**,
>   103.9k★. Repo tự mô tả: "art direction… instead of generic AI pages", đo trên **247 site** trong gallery
>   (One Page Love, Awwwards SOTD).
> - `https://github.com/addyosmani/agent-skills` — `skills/frontend-ui-engineering/SKILL.md`, nhánh `main`, **MIT**, 111k★.
>   Phần "Avoid the AI Aesthetic" + accessibility/state được giữ lại ở file này.
> - Lý do nhập: UI hiện tại của dự án bị đánh giá là "basic AI" (nền tối + gradient cyan/indigo, card bo tròn, pill badge) — đúng
>   danh sách "tell" mà hai skill này đo được. Đây là **bản rút gọn có chủ đích** (không copy nguyên văn), giữ lại số liệu và luật cứng.
> - Thứ tự ưu tiên: `AGENTS.md` > `docs/` > `agents/<role>/AGENT.md` > `ui-modern-industrial.md` > **file này**.
> - Bản đầy đủ (script `draw.mjs/inspect.mjs`, thư viện 20 hướng, 247 site): cài `npx skills add Leonxlnx/taste-skill` khi cần — skill này
>   chỉ giữ phần *luật*, không cần Node/Chrome.

---

## 1. Khi nào dùng

Bất cứ khi nào: tạo/sửa **landing page, website, portal, backend Odoo, dashboard, màn app**; hoặc người dùng nói
"trông chung chung", "giống AI tạo", "làm cho premium/xịn hơn", "đổi màu trend hơn".

## 2. Chẩn đoán: giao diện "do AI tạo" trông thế nào (đo được)

So sánh trong nghiên cứu 247 site, nhóm **điểm ≥ 7.5** so với nhóm **< 6.5**:

| Dấu hiệu | Site điểm cao | Site điểm thấp | Kết luận |
|---|---|---|---|
| Icon card cho feature | **1%** | 28% | Cấm |
| Pill badge trên hero | **14%** | 34% | Cấm |
| Gradient | **22%** | 52% | Cấm |
| Shadow | **24%** | 48% | Cấm (ở trạng thái nghỉ) |
| Chữ in hoa nhỏ, giãn cách | **16%** | 57% | Cấm |
| Ảnh hero phủ ≥ 50% màn hình đầu | 68% | — | Nên |
| Tỷ lệ chữ tiêu đề / chữ thường | 4.3 (median), tới 8.4 | 5.0 | Tiêu đề phải **to** |

Ngoài ra: hàng card giống nhau, monospace ngoài code, đánh số 01/02/03, đường kẻ 1px, glass/backdrop-blur,
gradient blob, dashboard giả bằng thanh xám, emoji, chữ trang trí trong tiêu đề — đều là "tell".

## 3. Luật cứng (đo được, không thương lượng)

**Bố cục**
1. Mỗi section phải có **heading**; trang có đúng **một `h1`** nêu rõ đây là gì.
2. Hai lề ngoài **bằng nhau**; không có section dồn một phía với nửa màn trống.
3. Không có khoảng trống cao quá ~nửa màn hình. Khoảng cách giữa section (120–240px desktop) **lớn hơn** mọi khoảng cách bên trong.
4. Hai section cạnh nhau **không** dùng cùng một kiểu bố cục. Không có lưới card đồng nhất.

**Chữ**
5. Tiêu đề **≤ 2 dòng ở mọi bề rộng** (an toàn: ~32 ký tự; hero đẹp median 5 từ).
6. Ba cỡ chữ làm việc: một rất lớn, một nhỏ, không có cỡ trung gian vô nghĩa. Tối đa **5 cỡ/trang**.
7. Tối đa **2 font** (thường 1). Display: tracking −0.02…−0.05em, leading 0.85–1.05. Body: 16–20px, leading 1.45–1.6, 45–75 ký tự/dòng.
8. Câu giới thiệu/trích dẫn để lớn phải là `<p>`/`<blockquote>` **≤ 36px desktop / 24px mobile**, ≤ 25 từ — nếu to hơn thì nó là tiêu đề và luật 2 dòng áp dụng.

**Màu**
9. **Một nền, một mực, một màu nhấn.** Màu nhấn chỉ nằm trên hành động chính (+ tối đa 1 chỗ khác).
10. Chữ nội dung dùng **mực đầy**; xám chỉ cho ghi chú phụ. Trang toàn xám = chưa xong.
11. Phân tách bề mặt bằng **một bước chênh tông**, không bằng đường kẻ hay shadow.
12. **Cấm**: gradient (kể cả chữ gradient), glow, glass, vignette; cấm bảng màu "trợ lý AI" (nền kem-vàng + terracotta/rust, tím/indigo).
13. Contrast: ≥ 4.5:1 cho chữ thường, ≥ 3:1 cho chữ lớn. Không dùng màu làm tín hiệu duy nhất.

**Ảnh**
14. **Một ảnh chủ đạo mạnh hơn sáu ảnh nhỏ.** Ảnh cùng tỷ lệ crop, cùng cách xử lý, góc vuông (73% site đẹp để vuông).
15. Không có ảnh thật thì **vẽ bằng code** theo ngôn ngữ hình của chính đối tượng, hoặc dùng ảnh thật (đã credit); **không** dùng ảnh stock vô nghĩa, icon, hay thanh xám giả dashboard.
16. Không đặt chữ đè lên tiêu đề/câu tuyên bố.

**Thành phần**
17. Button: phẳng, một màu, **chọn một kiểu góc rồi giữ** (vuông hoặc bo tròn hẳn) — tránh "bo 9–16px" (mặc định của site yếu).
18. Nav: wordmark + 3–5 link thường + **một** hành động. Không viền bar, không pill nav.
19. Footer là cái kết được thiết kế, không phải sitemap: tên thương hiệu trở lại full-width.
20. Trạng thái: **loading (skeleton, không spinner cho nội dung), empty, error, success, permission** — phải có đủ.

**Chuyển động**
21. Motion là **bắt buộc** với trang công khai và phải có thật: trang cần **≥ 8 phần tử** thực sự chuyển động khi tải/cuộn (warn dưới 16).
22. Bốn phần: một **signature move**; entrance cho từng phần của mỗi section (heading → text → ảnh, **stagger 60–90ms**, dời 16–32px + opacity, chạy 1 lần khi vào khung); feedback cho **mọi** control (hover/press/focus); một ảnh động (clip loop hoặc drift chậm).
23. Chỉ `transform` + `opacity`. **Luôn** có nhánh `prefers-reduced-motion`: giữ nội dung, bỏ đường đi.
24. Không animation vô hạn; không hy sinh tốc độ bảng/form để lấy hiệu ứng.

## 4. Quy tắc riêng cho màn hình làm việc (app screen, backend Odoo)

- Được phép (và nên): viền mảnh, nhãn nhỏ, panel lặp lại, mật độ dày — đây là công cụ làm việc.
- **Không** được: gradient, glow, icon trong ô màu tinted, chart cầu vồng, "số liệu trang trí" tự bịa, KPI card quá khổ.
- Ưu tiên: quét mắt nhanh (bảng gọn, số tabular, cột thẳng), tìm kiếm/lọc rõ, bàn phím dùng được, focus nhìn thấy.
- Hiệu ứng chỉ ở: màn đăng nhập, dashboard tổng quan, thẻ thống kê, phản hồi tương tác. **Danh sách/nhập liệu: phẳng, nhanh.**

## 5. Quy trình bắt buộc khi làm UI mới

1. **Đọc bối cảnh**: stack thật (Odoo 17? Compose? HTML tĩnh?), brand có sẵn, ảnh thật có gì.
2. **Viết "argument"**: liệt kê trang thành các *claim + bằng chứng*, mỗi dòng một section. Section nào không có claim → **xoá**.
3. **Chọn hướng** từ `ui-modern-industrial.md`; không tự phát minh bảng màu thứ hai.
4. **Viết copy trước khi viết markup** (đếm ký tự tiêu đề!).
5. **Dựng token trước**, mọi thứ downstream chỉ tham chiếu token.
6. Dựng **hero trước**, đúng ở 1440 / 768 / 390px.
7. **Câu "ý tưởng"**: "Trên trang này, **[thứ chỉ thuộc về sản phẩm này]** trở thành **[thiết bị thị giác]**." Nếu câu đó vẫn đúng khi thay bằng đối thủ → đó là ý tưởng của cả ngành, chưa phải của mình.
8. **Một thứ sống**: một tương tác thật, dùng dữ liệu thật, chạy được bằng ngón tay, có fallback không cần JS.

## 6. Cổng nghiệm thu (bắt buộc trước khi báo xong)

Chụp **3 bề rộng: 1440, 768, 390** và tự trả lời bằng ảnh, không bằng code:

- [ ] 0 FAIL theo luật mục 3; mỗi WARN phải bảo vệ được trong 1 câu.
- [ ] Người lạ đọc 5 giây nói được "đây là gì, bấm vào đâu".
- [ ] Mọi section có heading; lề hai bên bằng nhau; không dải trống.
- [ ] Hero: tiêu đề ≤ 2 dòng ở **cả ba** bề rộng.
- [ ] Bàn phím đi hết được; focus nhìn thấy; control có tên; contrast đạt.
- [ ] Có ≥ 8 phần tử chuyển động + fallback `prefers-reduced-motion`.
- [ ] Không còn: gradient, glass, glow, icon card, pill badge, 01/02/03, monospace nhãn, emoji.
- [ ] Nội dung đầy đủ (không "lorem", không "đang cập nhật").

**Câu tự vấn cuối**: nếu bỏ toàn bộ hiệu ứng, trang này còn được thiết kế không? Nếu không → chưa xong.
