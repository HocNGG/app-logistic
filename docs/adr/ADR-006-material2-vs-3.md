# ADR-006 — Material 2 (stable) thay vì Material 3 (alpha) cho app KMP

**Trạng thái:** ACCEPTED · **Đề xuất:** FE-01

## Bối cảnh

Compose Multiplatform **1.12.2** (stable) pin `org.jetbrains.compose.material3:material3:1.12.0-alpha03` — nghĩa là **Material 3 trong CMP vẫn là alpha** (theo Jetpack Material3 1.5.0-alpha22). Material 2 là stable.

Yêu cầu giao diện: "responsive UI", "iPhone 18 duo UI" → cần **adaptive 2-pane**.

## Phương án

- **A.** Dùng Material 3 alpha → giao diện hiện đại, có sẵn `ListDetailPaneScaffold` (material3-adaptive), nhưng API có thể đổi/vỡ giữa các bản vá.
- **B.** **Material 2 stable** + `org.jetbrains.compose.material3.adaptive:adaptive:1.3.0` (adaptive đã stable) cho `WindowSizeClass`/adaptive info; tự viết `DuoScaffold` (~60 dòng).

## Quyết định

**B.**

## Hệ quả

- ✅ Build ổn định — demo không được phép vỡ vì alpha.
- ✅ Kiểm soát hoàn toàn hành vi 3 ngưỡng (split rộng / split hẹp / overlay) và **displacement** (giữ selection, scroll, text đang gõ khi resize) — thứ mà scaffold có sẵn không nhất thiết làm đúng ý.
- ⚠️ Không có `ListDetailPaneScaffold` có sẵn → tự viết (đã có trong skill `agents/fe/skills/03-duo-adaptive-layout.md`).
- ⚠️ Nếu Orchestrator muốn M3: phải **khoá version tuyệt đối** trong `libs.versions.toml` (không dùng `+`), chấp nhận rủi ro, và ghi ADR mới supersede ADR này.
- **Cách nói trong demo:** *"Em chọn Material 2 vì M3 trong Compose Multiplatform vẫn là alpha — demo không được phép vỡ vì một bản alpha."* Đây là **critical thinking** được chấm điểm.
