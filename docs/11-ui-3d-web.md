# 11 — UI PREMIUM & 3D (web + Odoo + app)

> Yêu cầu: *"giao diện web, app nhìn phải xịn, có logo đàng hoàng, banner, hero, trang giới thiệu bắt mắt, animation 3D là điểm cộng lớn — UI 3D là điểm nổi bật."*
> File này chốt: **3D đặt ở đâu, bằng công nghệ gì, ai làm, và nghiệm thu thế nào.**
> Skill thi hành: `agents/_shared/skills/ui-3d-premium.md`.

---

## 1. Quyết định kiến trúc (ADR-007)

### Ba tầng UI — 3D chỉ đặt ở tầng A

| Tầng | Sản phẩm | Người dùng | 3D? | Công nghệ |
|---|---|---|---|---|
| **A. Landing / giới thiệu** | trang marketing + hero | người chấm, BGĐ, khách mới | ✅ **trọng tâm** | **CSS 3D** (0 KB) → nâng lên WebGL nếu còn thời gian |
| **B. Odoo** | form chuyến, picking, Debit | nhân viên | ⚠️ nhẹ, chỉ ở portal/website | OWL 2 + CSS 3D + SCSS |
| **C. App KMP** | tài xế, CS console | tài xế | ⚠️ **giả-3D** | `graphicsLayer` + `cameraDistance` |

### Vì sao không WebGL ngay từ đầu
| | CSS 3D | Three.js/WebGL |
|---|---|---|
| Kích thước | 0 KB | ~600 KB phải vendor |
| Offline | ✅ | cần script tải riêng |
| Rủi ro trên máy demo (GPU ảo, Remote Desktop) | thấp | **cao** — WebGL có thể không khởi tạo |
| Đủ "wow" cho 1 hero + card stack | ✅ | ✅✅ |

**Luật:** có bản CSS 3D chạy tốt **trước**; WebGL là **lớp nâng cấp có công tắc**, không phải đường chính.
→ Nếu WebGL lỗi, trang **vẫn đẹp** (đường lui bắt buộc).

---

## 2. Bản mẫu đã dựng (xem được ngay)

**`demo/landing/index.html`** — mở trực tiếp bằng trình duyệt (double-click), **không cần server, không cần mạng**.

Có gì:
| Thành phần | Nội dung |
|---|---|
| **Logo** | SVG tự vẽ (`assets/brand/logo.svg`) — khối kiện isometric + đường hành trình |
| **Hero** | tiêu đề gradient, lede, 2 CTA, 4 số liệu đếm động (19 · 6 · 3 · 10) |
| **Scene 3D** | khối lập phương 6 mặt, 2 vòng quỹ đạo quay, 4 chip nghiệp vụ (⑤ chuyến · ④ kiện · ⑬ Debit · ⑨ hành trình) **nghiêng theo chuột** |
| **8 thẻ chặng** | mỗi chặng 1 thẻ, **tilt 3D** khi hover, gắn tag native/tự viết |
| **Kiến trúc** | panel "6 bước tự viết" vs "13 bước dùng sẵn" |
| **Tra cứu** | ô nhập mã vận đơn (mô phỏng, sẽ nối `access_token` thật) |
| **Footer** | logo + tech chips |

Kỹ thuật: `perspective` + `transform-style: preserve-3d`, easing bằng `requestAnimationFrame` (không giật), `IntersectionObserver` reveal, tôn trọng `prefers-reduced-motion`, responsive bỏ scene dưới 980px.

---

## 3. Logo & brand

| File | Dùng ở | Kích thước |
|---|---|---|
| `assets/brand/logo-mark.svg` | favicon · app icon · nav · avatar | 64×64 |
| `assets/brand/logo.svg` | header web · slide video · tài liệu | 320×72 |
| `assets/brand/*.png` | icon app KMP (Compose load PNG) | 128 · 256 · 512 |

**Ngữ nghĩa** (kể được trong demo):
- **Khối isometric** = kiện hàng — đơn vị nghiệp vụ cơ bản.
- **Đường nét đứt + chấm sáng** = hành trình (⑨ → ⑩ → ⑪), không phải trang trí.
- **Ba sắc độ** (sky → indigo → cyan) = ba module tự viết.
- **Palette**: `#070B14` nền · `#38BDF8` sky · `#22D3EE` cyan · `#6366F1` indigo · ink `#F8FAFC`.

---

## 4. Lộ trình đưa vào sản phẩm thật

| Bước | Việc | Ai | Phụ thuộc |
|---|---|---|---|
| **1** | Bản mẫu landing (xong) | Orchestrator | — |
| **2** | Chụp landing vào **video demo** cảnh 1–2 (thay ảnh tĩnh) | RV-01 | P9 |
| **3** | Port sang **Odoo `website`**: QWeb page + snippet `lab_hero` trong module `lab_web` | FE-01 | `website` module cài được |
| **4** | App KMP: màn **Welcome/hero** có logo + card tilt + parallax nhẹ | FE-01 | Wave B |
| **5** | (tuỳ chọn) WebGL thật: `vendor-three.ps1` + scene hạt/ánh sáng | FE-01 | nếu còn thời gian |

**Vì sao port sang Odoo `website` chứ không để HTML rời:** native-first — Odoo có sẵn website builder, menu, SEO, form liên hệ, và **cùng một domain với portal tra cứu** → demo một mạch: landing → tra cứu → Odoo backend.

---

## 5. Nghiệm thu (RV-01 chấm)

| # | Tiêu chí | Cách đo |
|---|---|---|
| 1 | 3D có nghĩa nghiệp vụ | mỗi phần tử 3D gắn nhãn bước (⑤⑬…) — không có khối xoay vô hồn |
| 2 | Chạy offline | mở bằng `file://`; grep không có `https://` trong `src`/`href` |
| 3 | Có đường lui | bật `prefers-reduced-motion` → không animation, vẫn đọc được |
| 4 | Không sụt khung hình | DevTools Performance khi cuộn: ≥ 55fps |
| 5 | Tương phản | chữ ≥ 4.5:1 (dùng `contrastRatio` bên FE skill 07) |
| 6 | Logo đúng | đọc được ở 24px và 128px; favicon hiện trên tab |
| 7 | Tiếng Việt | không tràn chữ ở 375px; thuật ngữ đúng glossary |
| 8 | Responsive | 375 / 768 / 1366 / 1920 px không vỡ |

---

## 6. Rủi ro & phương án

| Rủi ro | Phương án |
|---|---|
| WebGL không khởi tạo trên máy demo | Bản CSS 3D là mặc định; WebGL chỉ bật qua công tắc |
| Máy chấm bật reduced-motion | nội dung vẫn đủ đẹp, có ảnh tĩnh |
| Port sang Odoo `website` lệch giao diện | giữ **cùng markup** — chỉ đổi vỏ QWeb, không viết lại |
| Làm đẹp quá lâu, trễ backend | 3D chỉ ở tầng A; **không** được cắt ngoại lệ/test để làm đẹp (Luật 5, 7) |
| Font ngoài không tải được | chỉ dùng `system-ui`/Segoe UI — đã làm |

---

## 7. Một câu để trả lời sếp

> *"3D chạy bằng CSS 3D thuần — 0 KB thư viện, mở offline được, và **mỗi phần tử 3D đều là một bước nghiệp vụ** (chuyến, kiện, Debit, hành trình). Nếu muốn WebGL thật, em có sẵn công tắc và script vendor — nhưng em không đặt cược demo vào driver đồ hoạ của máy lạ."*
