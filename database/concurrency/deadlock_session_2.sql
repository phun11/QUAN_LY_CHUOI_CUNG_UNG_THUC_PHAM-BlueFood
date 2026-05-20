-- ============================================================
-- Deadlock demo - Session 2
-- ============================================================
-- Session 2 cố tình khóa ngược thứ tự với Session 1:
-- - Session 1: PRODUCTS -> QR_CODES
-- - Session 2: QR_CODES -> PRODUCTS
-- Đây là mẫu gây deadlock kinh điển.
-- Cách xử lý trong hệ thống thật:
-- - Chuẩn hóa thứ tự khóa trong procedure/service.
-- - Giữ transaction ngắn.
-- - Không để người dùng nhập liệu trong lúc đang giữ lock.
-- ============================================================

-- Session 2 khóa QR_CODES trước.
UPDATE QR_CODES
SET STATUS = 'SUSPICIOUS'
WHERE PRODUCT_ID = 1;

-- Sau khi Session 1 đang giữ PRODUCTS, dòng này sẽ chờ PRODUCTS.
UPDATE PRODUCTS
SET QUALITY_SUMMARY = 'Session 2 waits product'
WHERE PRODUCT_ID = 1;

COMMIT;
