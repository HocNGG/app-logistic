# -*- coding: utf-8 -*-
# Controller của lab_api — hợp đồng docs/02-kien-truc.md §6.2 (ADR-004).
#
#   POST /lab/api/v1/auth/login                  auth='public'  -> token 8 giờ + roles
#   GET  /lab/api/v1/driver/jobs                 auth='user' + X-Lab-Token + nhóm Tài xế
#   POST /lab/api/v1/driver/jobs/<id>/accept     auth='user' + X-Lab-Token + nhóm Tài xế
#   POST /lab/api/v1/driver/jobs/<id>/capture    auth='user' + X-Lab-Token (ảnh + chữ ký)
#   POST /lab/api/v1/driver/jobs/<id>/fail       auth='user' + X-Lab-Token + nhóm Tài xế
#   GET  /lab/api/v1/cs/jobs                     auth='user' + X-Lab-Token + CS/Trưởng nhóm tài xế
#   POST /lab/api/v1/cs/jobs/<id>/assign         auth='user' + X-Lab-Token + CS/Trưởng nhóm tài xế
#   GET  /lab/api/v1/kho/cho-nhan                auth='user' + X-Lab-Token + nhóm Kho
#   POST /lab/api/v1/kho/jobs/<id>/nhan          auth='user' + X-Lab-Token + nhóm Kho
#   GET  /lab/api/v1/sep/kpi?range=              auth='user' + X-Lab-Token + Trưởng phòng/BGĐ
#   GET  /lab/api/v1/admin/accounts              auth='user' + X-Lab-Token + QUẢN TRỊ VIÊN
#   POST /lab/api/v1/admin/switch                auth='user' + X-Lab-Token + QUẢN TRỊ VIÊN
#   GET  /lab/api/v1/track/<access_token>        auth='public'  (whitelist field)
#
# Mọi endpoint trả envelope {ok, data, error}; quyền record do ir.rule quyết định
# (Luật 3). Odoo 17 KHÔNG có Authorization: Bearer — token này do ta cài (ADR-004).
#
# Nhóm quyền ở mỗi namespace (Tài xế / CS / Kho / Dashboard) được kiểm ở TẦNG
# CONTROLLER và trả 403 — xem ``helpers.kiem_nhom`` + ``helpers.kiem_mot_trong_nhom``
# (có giải thích vì sao không dùng ACL được và vì sao không viết ``has_group``).
# Namespace ``/admin/*`` ("chuyển tài khoản" cho demo) kiểm bằng
# ``helpers.kiem_admin`` — cùng lý do, khác câu hỏi ("có phải admin không").
from . import admin
from . import auth
from . import cs
from . import driver
from . import kho
from . import kho_xuat
from . import scan
from . import sep
from . import track
