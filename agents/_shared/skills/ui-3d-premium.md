# SKILL — UI premium & 3D (web + app)

> **Nguồn đã kiểm chứng (10/2026):** không bịa repo. Những cái dưới đây có thật, đọc được:
> - [full-stack-skills/threejs-skills](https://github.com/full-stack-skills/threejs-skills) — skill Three.js: scene · camera · lights · materials · geometries. Dùng khi cần **WebGL thật**.
> - [cuohuatang/CHT-UI-SKILLS](https://github.com/cuohuatang/CHT-UI-SKILLS) — chuỗi skill thiết kế UI/động: `oil-ui` (ra định hướng) → `draw-ui` (ra spec) → sinh code. Dùng để **lấy quy trình**, không chép code.
> - Odoo Apps: [bsi_relievo](https://apps.odoo.com/apps/modules/16.0/bsi_relievo) · [theme_lusion](https://apps.odoo.com/apps/themes/19.0/theme_lusion) — theme/3D có sẵn cho Odoo. **Chỉ để tham khảo**: khác phiên bản, không kiểm được license → không vendor.
> - Compose: [3D card flip](https://doveletter.dev/docs/compose-animations/card-flip-3d) · [spherical projection](https://daily.dev/posts/3d-spherical-projection-animation-jetpack-compose-xpyblbe0a) — pattern `graphicsLayer` + `cameraDistance`.

## Dùng khi nào
Khi làm: landing page, hero 3D, logo/brand, animation, "làm cho nó xịn" — tức là mọi việc **thẩm mỹ có rủi ro chậm/vỡ demo**.

---

## 1. Luật trước khi làm đẹp (quan trọng hơn code)

| Luật | Vì sao |
|---|---|
| **Đẹp không được làm chậm quá 60fps** | Demo sụt khung hình = "chưa xong" trong mắt người chấm |
| **Không phụ thuộc CDN** | Phòng demo có thể không có mạng. Mọi thứ phải nằm trên đĩa |
| **Phải có đường lui** (`prefers-reduced-motion`, ảnh tĩnh) | Người chấm bật reduced-motion → thấy trang trắng thì mất điểm |
| **3D phải có nghĩa nghiệp vụ** | Xoay một khối lập phương vô hồn = trang trí. Khối = **kiện hàng**, vòng = **chuyến**, chip = **bước ⑤⑬** |
| **Một điểm nhấn duy nhất** | 3D ở hero + thẻ tilt nhẹ. Đừng 3D mọi nơi → nhiễu |

---

## 2. Ba tầng UI của dự án (đừng làm lẫn)

```
TẦNG A — LANDING (web, marketing)        → demo/landing/index.html (bản mẫu)
TẦNG B — ODOO UI (nghiệp vụ)             → view XML + SCSS + OWL
TẦNG C — APP KMP (tài xế / CS)           → Compose Desktop
```

| Tầng | 3D được không | Cách | Rủi ro |
|---|---|---|---|
| **A. Landing** | ✅ Nên — đây là chỗ "wow" | **CSS 3D** (0 dependency) hoặc WebGL/Three.js | Thấp nếu CSS; trung bình nếu WebGL |
| **B. Odoo** | ⚠️ Chỉ ở portal/website, **không** trong form nghiệp vụ | OWL component + CSS 3D; Three.js chỉ ở trang giới thiệu | Odoo backend phải giữ nhanh, đừng nhồi WebGL vào form |
| **C. App KMP** | ⚠️ Giả-3D | `Modifier.graphicsLayer { rotationX/Y, cameraDistance }` | WebView để có WebGL là **quá nặng** → không làm |

**Kết luận kiến trúc:** 3D thật chỉ ở **tầng A**. Tầng B dùng CSS 3D nhẹ ở portal. Tầng C dùng **perspective/depth giả** — vẫn "xịn" mà không kéo engine 3D vào desktop app.

---

## 3. Công thức CSS 3D (đã dựng, dùng lại được)

### 3.1 Scene nghiêng theo chuột — 0 dependency
```css
.scene-wrap{ perspective:1200px; perspective-origin:50% 45% }
.scene{
  transform-style:preserve-3d;
  transform: rotateX(calc(58deg + var(--my) * -10deg))
             rotateZ(calc(-42deg + var(--mx) * 14deg));
  transition: transform .18s ease-out;
}
```
```js
// easing bằng rAF, KHÔNG set biến trực tiếp trong pointermove (gây giật)
let tx=0,ty=0,cx=0,cy=0;
addEventListener('pointermove', e=>{
  tx=(e.clientX/innerWidth -.5)*2; ty=(e.clientY/innerHeight-.5)*2;
  if(!raf) raf=requestAnimationFrame(tick);
},{passive:true});
function tick(){
  cx+=(tx-cx)*.08; cy+=(ty-cy)*.08;
  root.style.setProperty('--mx',cx.toFixed(4));
  root.style.setProperty('--my',cy.toFixed(4));
  raf = (Math.abs(tx-cx)>.001||Math.abs(ty-cy)>.001) ? requestAnimationFrame(tick) : 0;
}
```

### 3.2 Khối kiện hàng isometric (6 mặt, CSS thuần)
```css
.cube{position:absolute;left:50%;top:50%;width:150px;height:150px;margin:-75px 0 0 -75px;
      transform-style:preserve-3d;animation:float 7s ease-in-out infinite}
.face{position:absolute;width:150px;height:150px;border-radius:6px;
      border:1px solid rgba(56,189,248,.35);
      background:linear-gradient(150deg,rgba(56,189,248,.20),rgba(99,102,241,.12));
      box-shadow:inset 0 0 42px rgba(56,189,248,.16)}
.face.top{transform:translateZ(75px)}   .face.bottom{transform:translateZ(-75px);opacity:.35}
.face.front{transform:translateY(75px) rotateX(90deg)}  .face.back{transform:translateY(-75px) rotateX(90deg)}
.face.left{transform:translateX(-75px) rotateY(90deg)}  .face.right{transform:translateX(75px) rotateY(90deg)}
```

### 3.3 Thẻ nghiêng theo chuột (rất rẻ, hiệu quả cao)
```js
card.addEventListener('pointermove', e=>{
  const r=card.getBoundingClientRect();
  const px=(e.clientX-r.left)/r.width-.5, py=(e.clientY-r.top)/r.height-.5;
  card.style.transform=`perspective(800px) rotateY(${px*9}deg) rotateX(${-py*9}deg) translateZ(6px)`;
});
card.addEventListener('pointerleave', ()=> card.style.transform='');
```

### 3.4 Vì sao KHÔNG dùng Three.js mặc định
| | CSS 3D | Three.js (WebGL) |
|---|---|---|
| Kích thước | 0 KB | ~600 KB |
| Offline | ✅ | cần vendor |
| Đủ để "wow"? | ✅ với khối/vòng/parallax | ✅✅ nếu cần ánh sáng, hạt, mô hình |
| Rủi ro demo | Thấp | Cao (GPU ảo trên máy demo, driver) |

**Quy tắc:** bắt đầu bằng CSS 3D. Chỉ nâng lên Three.js nếu **đã** có bản CSS chạy tốt và còn thời gian.

---

## 4. Nếu cần WebGL thật: quy trình vendor

```powershell
powershell -ExecutionPolicy Bypass -File infra\scripts\vendor-three.ps1
```
Sau đó trong HTML:
```html
<script src="../../vendor/three/three.min.js"></script>
<script>
  if (window.THREE) { /* scene thật */ }
  else { console.warn('Thiếu three.min.js — giữ CSS 3D'); }
</script>
```
**Cấm:** `<script src="https://cdn...">` (mất mạng là trắng trang).

---

## 5. Compose Desktop "giả-3D" (tầng C)

```kotlin
// Thẻ lật / nghiêng — không cần engine 3D
Modifier.graphicsLayer {
    rotationY = tiltY            // -12f..12f theo con trỏ
    rotationX = tiltX
    cameraDistance = 12f * density  // mặc định 8f; tăng = phối cảnh nhẹ hơn
    transformOrigin = TransformOrigin(0.5f, 0.5f)
}
```
Quy tắc:
- Chỉ dùng cho **hero/welcome + card**, không dùng cho list dài (tốn recomposition).
- `cameraDistance` phải nhân `density`, nếu không sẽ méo trên màn HiDPI.
- Animation bằng `animateFloatAsState`, không tự tính frame.

---

## 6. Logo & brand (không cần designer)

**Quy tắc:** logo phải **vẽ được bằng hình học** → xuất được SVG → nhúng được vào Compose bằng Canvas (không cần asset nhị phân).

Bộ đã có trong `assets/brand/`:
| File | Dùng ở đâu |
|---|---|
| `logo-mark.svg` | favicon, app icon, avatar, góc nav |
| `logo.svg` | header web, slide, video |
| `*.png` (sinh từ script) | icon app KMP (Compose load PNG) |

**Ngữ nghĩa mark:** khối isometric = **kiện hàng**; đường nét đứt + chấm = **hành trình**; 3 sắc độ = 3 module tự viết.

---

## 7. Checklist trước khi khoe "UI xịn"

- [ ] Mở bằng `file://` (không server) vẫn chạy — chứng minh không phụ thuộc CDN
- [ ] Không có `https://` trong `<link>`/`<script>`/`url()` 
- [ ] Bật `prefers-reduced-motion` → không có animation, nội dung vẫn đọc được
- [ ] Thu nhỏ cửa sổ còn 375px → không tràn ngang, không chồng chữ
- [ ] Tương phản chữ ≥ 4.5:1 (kiểm bằng hàm `contrastRatio` bên FE skill 07)
- [ ] Đo FPS khi cuộn (DevTools → Performance): ≥ 55fps
- [ ] Chuỗi tiếng Việt dài nhất không bị cắt ("Đơn thiếu thông tin người nhận")
- [ ] Logo hiển thị đúng ở 24px (favicon) và 128px (app icon) — không nhoè

## Checklist nhanh
- [ ] 3D có nghĩa nghiệp vụ, không phải khối xoay vô hồn
- [ ] 0 dependency ngoài (hoặc đã vendor vào đĩa)
- [ ] Có đường lui reduced-motion + ảnh tĩnh
- [ ] Chỉ **một** điểm nhấn 3D mỗi trang
- [ ] Ảnh PNG/SVG của brand nằm trong `assets/brand/`
- [ ] Đã mở thử bằng `file://` và trên màn 1366×768
- [ ] Không dùng WebGL trong form nghiệp vụ Odoo
