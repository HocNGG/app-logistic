# SKILL IMPORT — nhập skill từ ngoài vào dự án

> Dùng khi bạn muốn thêm skill cho BE-01 / FE-01 / RV-01 từ GitHub, marketplace, hay từ đồng nghiệp.
> Quy tắc gốc: **skill ngoài không bao giờ được đè lên `AGENTS.md`.** Xem §5.

---

## 1. Chuẩn thật của một "skill"

Từ 2026 có một chuẩn mở: **Agent Skills** ([agentskills.io](https://agentskills.io)). Cấu trúc:

```
<skill-name>/
└── SKILL.md          ← YAML frontmatter + markdown hướng dẫn
    (tuỳ chọn: scripts/, references/, assets/)
```

`SKILL.md` bắt buộc có frontmatter 2 field:

```markdown
---
name: my-skill-name          # chữ thường, gạch nối
description: Mô tả rõ skill LÀM GÌ và KHI NÀO dùng (đây là thứ agent dùng để quyết định có nạp hay không)
---

# My Skill Name

Hướng dẫn cho agent…
```

**Vì sao `description` quan trọng nhất:** agent (và harness) quyết định nạp skill nào dựa trên dòng này. Viết `description` mơ hồ → skill không bao giờ được dùng.

Nguồn tham chiếu chính thức: [anthropics/skills](https://github.com/anthropics/skills) — có thư mục `spec/` (đặc tả) và `template/` (mẫu). Lưu ý license: phần lớn Apache-2.0, **riêng** `docx`/`pdf`/`pptx`/`xlsx` là *source-available*, **không** phải open source.

---

## 2. Quy ước của repo này

Repo này dùng markdown phẳng, không dùng folder-per-skill (vì DSH nạp bằng `read`, không cần `SKILL.md` chính danh):

```
agents/<role>/skills/NN-slug.md      ← skill riêng của BE/FE/RV
agents/_shared/skills/slug.md        ← skill dùng chung 3 agent
```

Mọi skill phải có:

| Phần | Bắt buộc | Ghi chú |
|---|---|---|
| `# Tiêu đề` | ✅ | |
| `## Dùng khi nào` | ✅ | 1–2 dòng, càng cụ thể càng tốt |
| Nội dung | ✅ | 60–170 dòng, có code mẫu nếu là skill kỹ thuật |
| `## Checklist nhanh` | ✅ | 5–8 checkbox |
| Header nguồn (nếu nhập ngoài) | ✅ | xem §6 |

**Và bắt buộc:** đăng ký vào bảng **§5 Bộ skill của bạn** trong `agents/<role>/AGENT.md`. File không được đăng ký = **file chết**, agent không biết nó tồn tại.

---

## 3. Nguồn đã kiểm chứng (10/2026)

| Nguồn | Dùng cho | Trạng thái | Ghi chú |
|---|---|---|---|
| **[anthropics/skills](https://github.com/anthropics/skills)** | Mẫu & đặc tả skill, skill tài liệu (PDF/Excel/PPT) | ✅ đã đọc README | `spec/` + `template/` đáng đọc trước khi tự viết skill |
| **[trobz/public-skills](https://github.com/trobz/public-skills)** | **Odoo**: `odooly` (query Odoo qua CLI), `access-rights-groups`, `list-users`; **utils**: `pdf`, `pptx` | ✅ đã đọc README | Trobz là đơn vị Odoo VN → sát ngữ cảnh. `odooly` **rất hữu ích cho BE + RV** (kiểm chứng dữ liệu thật thay vì đoán). Cần cài `odooly` trong `$PATH` + `~/odooly.ini` |
| **[fhidalgodev/odoo-development-skill](https://github.com/fhidalgodev/odoo-development-skill)** | Odoo 14–20, OCA standards, có code indexing | ⚠️ **chưa đọc nội dung** — phải review trước khi dùng | Hứa hẹn nhất cho BE-01 nhưng **chưa xác minh chất lượng** |
| **[OpenSPP/openspp-modules · `.cursor/rules` (nhánh 17.0)](https://github.com/OpenSPP/openspp-modules/tree/17.0/.cursor/rules)** | Rule Odoo 17 dùng thật trong một dự án lớn | ⚠️ chưa đọc hết | Đây là *rules*, không phải skill — dùng làm **tham khảo cách viết rule** |
| **[haidrrrry/compose-kotlin-agent-skills](https://github.com/haidrrrry/compose-kotlin-agent-skills)** | Compose & Kotlin cho agent | ⚠️ chưa đọc nội dung | Ứng viên cho FE-01 — nhưng có thể lệch sang **Android/Jetpack**, trong khi ta cần **desktop KMP**. Phải lọc |
| **[ranbot-ai/awesome-skills](https://github.com/ranbot-ai/awesome-skills)** | Danh sách tổng hợp skill | ✅ tồn tại | Để **tìm**, không để tin |
| [skills.rest](https://skills.rest) · [claude-plugins.dev](https://claude-plugins.dev) | Thư mục skill có đánh giá | ✅ tồn tại | Có `odoo-code-reviewer` — ứng viên cho RV-01 |
| [vercel-labs/skills](https://github.com/vercel-labs/skills) (`npx skills`) | CLI cài skill vào nhiều agent | ✅ theo README Trobz | `npx skills add <repo> --list` để xem trước |
| **[Leonxlnx/taste-skill](https://github.com/Leonxlnx/taste-skill)** | **Anti-slop UI** (`design-taste-frontend`, tasteskill v2): art direction, luật cứng đo trên 247 site, cổng `inspect.mjs` | ✅ **đã đọc toàn bộ SKILL.md**, 103.9k★, **MIT**, nhánh `main` | **ĐÃ VENDOR** (bản rút gọn) → `agents/_shared/skills/design-taste-anti-slop.md`. Kèm script `draw.mjs/inspect.mjs` cần Node 22 + Chrome — chỉ cài khi cần chạy cổng đo, không bắt buộc |
| **[addyosmani/agent-skills](https://github.com/addyosmani/agent-skills)** | `skills/frontend-ui-engineering/SKILL.md`: kiến trúc component, WCAG AA, state loading/empty/error, "Avoid the AI Aesthetic" | ✅ **đã đọc toàn bộ SKILL.md**, 111k★, **MIT** | **ĐÃ VENDOR** (phần luật) — gộp vào `design-taste-anti-slop.md` §2/§3/§5 |
| **[Dammyjay93/interface-design](https://github.com/Dammyjay93/interface-design)** | Design engineering plugin cho Claude Code: craft, memory, enforcement | ✅ repo tồn tại, 5.9k★, **MIT**; cấu trúc là plugin + `reference/` (không phải SKILL.md phẳng) | **REFERENCE** — chưa vendor; chỉ đọc khi cần quy trình design-system dài hạn |
| **[mart337i/odoo-skills](https://github.com/mart337i/odoo-skills)** | Skill cộng đồng Odoo (manifest, asset bundle, migration, 17.0 rules) | ⚠️ repo tồn tại (60★) nhưng **KHÔNG có LICENSE** | **KHÔNG được vendor** (§5: license chưa rõ → chỉ reference). Muốn dùng phải xin phép tác giả trước |

**Chưa kiểm chứng** (đừng tin vội): mọi repo không có trong bảng trên; mọi repo "awesome-…" tự nhận.

---

## 4. Cách tìm trên GitHub (search syntax thật)

```text
# 1. Tìm file SKILL.md có nội dung về Odoo (code search)
path:**/SKILL.md odoo

# 2. Tìm theo topic
topic:agent-skills
topic:claude-skills

# 3. Tìm skill cho KMP/Compose
filename:SKILL.md "compose multiplatform"

# 4. Tìm repo có rule cho Odoo
path:.cursor/rules odoo 17

# 5. Tìm AGENTS.md theo chủ đề (nhiều repo dùng AGENTS.md như ta)
filename:AGENTS.md odoo

# 6. Tìm repo chứa cả rules lẫn Odoo
odoo "SKILL.md" in:readme
```

**Bộ lọc chất lượng (dùng trước khi đọc code):**

- ⭐ ≥ 50 **và** commit trong 6 tháng gần nhất.
- Có **license** rõ ràng (Apache-2.0/MIT/LGPL-3 dùng được; không license = không nhập).
- Nội dung **có code/command thật**, không chỉ "hãy làm tốt".
- Không lệch phiên bản: skill cho Odoo 18/19 dễ dạy `stock.package`, `<list>`, `models.Constraint` — **sai với 17**.
- Không mâu thuẫn `AGENTS.md` (xem §5).

---

## 5. Xung đột: skill ngoài vs luật của dự án

**Thứ tự ưu tiên tuyệt đối:**

```
AGENTS.md  >  docs/  >  agents/<role>/AGENT.md  >  skill của repo  >  skill nhập từ ngoài
```

Nghĩa là: nếu một skill ngoài bảo dùng `stock.package`, `attrs=`, `material3`, hay tạo model `debit` → **cắt bỏ đoạn đó**, không thương lượng. Skill ngoài được dùng để **học cách làm**, không phải để **ghi đè hợp đồng**.

**Quy trình khi có xung đột:**
1. Sửa đoạn xung đột trong bản copy (không sửa bản gốc trên mạng).
2. Ghi `⚠️ Sửa so với bản gốc: <lý do>` ngay tại đoạn đó.
3. Nếu xung đột là *quan điểm* (không phải sự thật kỹ thuật) → mở ADR.

---

## 6. Header nguồn (bắt buộc với skill nhập ngoài)

Đặt ngay dưới tiêu đề H1:

```markdown
> **Nguồn:** <repo URL> · <đường dẫn SKILL.md> · commit `<sha>` · license `<giấy phép>`
> **Nhập ngày:** yyyy-mm-dd · **Người nhập:** <role>
> **Đã sửa so với bản gốc:** <liệt kê, hoặc "không">
> **Lý do nhập:** <1 dòng — nếu không viết được dòng này thì đừng nhập>
```

Lý do: 6 tháng sau phải biết đoạn nào là của mình, đoạn nào của người khác, và có được phép sửa không.

---

## 7. Quy trình nhập — 7 bước

| # | Bước | Ghi chú |
|---|---|---|
| 1 | **Xác định lỗ hổng** | "BE-01 cần gì mà skill pack hiện tại chưa có?" — nếu không trả lời được, không nhập |
| 2 | **Tìm** theo §4 | Chọn 2–3 ứng viên |
| 3 | **Đọc toàn bộ** trước khi nhập | Không nhập theo README |
| 4 | **Chọn cách: vendor hay reference** | Xem bảng dưới |
| 5 | **Sửa xung đột** theo §5 + gắn header §6 | |
| 6 | **Đăng ký** vào `agents/<role>/AGENT.md` §5 | Bắt buộc, nếu không = file chết |
| 7 | **Đo hiệu quả** sau 1 sprint | Skill không dùng → xoá. Trần ~15 skill/agent |

| Cách | Khi nào | Hệ quả |
|---|---|---|
| **Reference** (chỉ ghi link vào AGENT.md) | Nguồn tốt nhưng ngắn/đổi nhanh, hoặc license chưa rõ | Nhẹ, không phình repo, nhưng agent phải fetch mạng (không dùng được offline) |
| **Vendor** (copy vào `skills/`) | Nội dung cốt lõi, cần offline, cần sửa cho phù hợp dự án | Phải giữ header nguồn + license; phải review khi bản gốc đổi |

---

## 8. Khi nào **KHÔNG** nhập

- Skill nói những gì `AGENTS.md` đã nói chắc hơn → **trùng lặp**, gây nhiễu.
- Skill chung chung ("viết code sạch", "hãy cẩn thận") → tốn context, vô dụng.
- Skill dạy API **sai phiên bản** (Odoo 18/19, Android thay vì desktop) → nguy hiểm hơn không có.
- Không có license, hoặc license cấm dùng thương mại mà không rõ.
- Skill > 300 dòng không có mục lục → agent đọc hết mỗi lượt, đốt context.

## Checklist nhanh
- [ ] Trả lời được "lỗ hổng nào cần lấp" bằng 1 câu
- [ ] Đã đọc toàn bộ skill gốc (không chỉ README)
- [ ] Đã đối chiếu với `AGENTS.md` — không mâu thuẫn, hoặc đã sửa + ghi rõ
- [ ] Có header nguồn (URL + commit + license + lý do)
- [ ] Đã đăng ký trong `agents/<role>/AGENT.md` §5
- [ ] Đã chạy `infra/scripts/audit-native-first.ps1` sau khi nhập (skill độc hại sẽ lộ ra ở code sau này)
- [ ] Số skill của agent vẫn ≤ 15
