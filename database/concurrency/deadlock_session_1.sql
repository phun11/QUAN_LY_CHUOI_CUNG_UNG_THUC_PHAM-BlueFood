-- ============================================================
-- Deadlock demo - Session 1
-- ============================================================
-- Mục tiêu:
-- - Minh họa deadlock khi 2 transaction khóa tài nguyên theo thứ tự ngược nhau.
-- Cách chạy:
-- 1) Mở Session 1, chạy lệnh UPDATE PRODUCTS bên dưới, chưa COMMIT.
-- 2) Mở Session 2, chạy lệnh UPDATE QR_CODES trong deadlock_session_2.sql, chưa COMMIT.
-- 3) Quay lại Session 1, chạy UPDATE QR_CODES bên dưới.
-- 4) Quay lại Session 2, chạy UPDATE PRODUCTS bên file session 2.
-- Kết quả:
-- - Oracle phát hiện deadlock và rollback một statement.
-- Cách phòng tránh trong đồ án:
-- - Trong mọi procedure, luôn khóa theo một thứ tự thống nhất, ví dụ PRODUCTS trước rồi QR_CODES.
-- ============================================================

-- Session 1 khóa dòng PRODUCTS trước.
UPDATE PRODUCTS
SET QUALITY_SUMMARY = 'Session 1 locked product'
WHERE PRODUCT_ID = 1;

-- Sau khi Session 2 đã khóa QR_CODES, dòng này sẽ chờ QR_CODES.
UPDATE QR_CODES
SET STATUS = 'SUSPICIOUS'
WHERE PRODUCT_ID = 1;

COMMIT;
