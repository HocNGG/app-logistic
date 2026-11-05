"""
Sinh asset logo PNG/ICO từ cùng hình học với logo SVG (assets/brand/logo-mark.svg).

Vì sao có script này thay vì để file PNG "tay":
  - Ảnh sinh lại được bất cứ lúc nào (đổi màu = sửa 3 hằng số).
  - Không phải commit nhiều file nhị phân không rõ nguồn.
  - Compose KMP cần PNG (không đọc SVG); jpackage trên Windows cần .ico.

Chạy:  python infra/scripts/gen-brand-assets.py
"""

from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path(__file__).resolve().parents[2] / "assets" / "brand"
OUT.mkdir(parents=True, exist_ok=True)

# Cùng bộ màu với SVG (assets/brand/logo-mark.svg).
#
# ĐỔI 2026-11-01 — vì sao: bộ cũ là gradient sky/indigo/cyan (#38BDF8 · #4F46E5 · #22D3EE),
# đúng ba mã màu `agents/_shared/skills/ui-modern-industrial.md` §2 liệt kê là CẤM, và
# gradient là *tell* số 1 của "giao diện AI đại trà" (design-taste-anti-slop.md §3.12).
# Bộ mới = ba bước tông forest + một nhấn hổ phách: PHẲNG, cùng họ màu, không gradient.
# Nguồn: design-system/lab-logistics/MASTER.md §2.1.
# ⚠️ Tên có tiền tố C_ vì `TOP`/`LEFT`/`RIGHT` bên dưới là TOẠ ĐỘ đa giác, không phải màu.
C_TOP = (47, 107, 87)     # #2F6B57  — mặt trên (forest sáng)
C_LEFT = (31, 74, 61)     # #1F4A3D  — mặt trái  (forest, = --lab-forest)
C_RIGHT = (23, 57, 47)    # #17392F  — mặt phải (forest đậm, = --lab-forest-2)
DARK = (15, 21, 18)       # #0F1512  — nền app icon (= Brand.ground của app KMP)
AMBER = (184, 118, 31)    # #B8761F  — chấm + cung hành trình (= --lab-amber)

# Toạ độ trong hệ 64×64 (khớp logo-mark.svg)
TOP = [(32, 6), (56, 19), (32, 32), (8, 19)]
LEFT = [(8, 19), (32, 32), (32, 58), (8, 45)]
RIGHT = [(56, 19), (56, 45), (32, 58), (32, 32)]


def scale(pts, size, pad=0.0):
    """Đưa toạ độ 64×64 sang kích thước thật, giữ chỗ trống `pad` (tỉ lệ)."""
    inner = size * (1 - 2 * pad)
    off = size * pad
    return [((x / 64) * inner + off, (y / 64) * inner + off) for x, y in pts]


def draw_mark(size, pad=0.06):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon(scale(LEFT, size, pad), fill=C_LEFT)
    d.polygon(scale(RIGHT, size, pad), fill=C_RIGHT)
    d.polygon(scale(TOP, size, pad), fill=C_TOP)
    # đường hành trình: cung nét mảnh màu hổ phách (đơn giản hoá từ cung nét đứt của SVG)
    w = max(1, round(size / 22))
    d.arc(
        [size * 0.14, size * 0.30, size * 0.46, size * 0.78],
        start=200, end=330, fill=AMBER, width=w,
    )
    r = max(1, round(size / 28))
    cx, cy = size * 0.235, size * 0.375
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=AMBER)
    return img


def rounded_bg(img, radius_ratio=0.22, bg=DARK):
    """Nền tối bo góc — icon app nhìn 'app' hơn là SVG trần."""
    size = img.size[0]
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=size * radius_ratio, fill=bg)
    canvas.alpha_composite(img)
    return canvas


def main():
    made = []

    # 1) Mark trong suốt, nhiều kích thước (dùng cho web, README, video)
    for size in (64, 128, 256, 512):
        p = OUT / f"logo-mark-{size}.png"
        draw_mark(size).save(p)
        made.append(p)

    # 2) App icon có nền tối (dùng cho cửa sổ app, installer)
    for size in (256, 512):
        p = OUT / f"appicon-{size}.png"
        rounded_bg(draw_mark(size, pad=0.16)).save(p)
        made.append(p)

    # 3) .ico nhiều kích thước cho jpackage (Windows cần icon .ico)
    ico = OUT / "appicon.ico"
    rounded_bg(draw_mark(256, pad=0.16)).save(
        ico, sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    )
    made.append(ico)

    # 4) Ảnh xem trước thương hiệu (để chèn vào tài liệu)
    prev = Image.new("RGBA", (760, 260), (7, 11, 20, 255))
    prev.alpha_composite(draw_mark(160, pad=0.06), (40, 50))
    prev.save(OUT / "preview-dark.png")
    made.append(OUT / "preview-dark.png")

    for p in made:
        print(f"  {p.name:24} {p.stat().st_size:>7,} bytes")
    print(f"\nXong {len(made)} file trong {OUT}")


if __name__ == "__main__":
    main()
