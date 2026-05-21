-- ============================================================
-- SELECT FOR UPDATE demo
-- ============================================================
-- Chức năng:
-- - Khóa bi quan dòng PRODUCTS để transaction hiện tại được quyền cập nhật trước.
-- Khi nào dùng:
-- - Đổi trạng thái lô hàng.
-- - Bán hàng/POS đánh dấu QR đã bán.
-- - Xác nhận vận chuyển/giao hàng.
-- Ý nghĩa quản lý đồng thời:
-- - Transaction khác update cùng dòng sẽ phải chờ đến khi transaction này COMMIT/ROLLBACK.
-- - Tránh hai người cùng xác nhận một lô ở hai trạng thái khác nhau.
-- Lưu ý:
-- - Chỉ khóa khi thật sự cần, và COMMIT/ROLLBACK càng nhanh càng tốt.
-- ============================================================

SELECT PRODUCT_ID, STATUS, VERSION_NO
FROM PRODUCTS
WHERE PRODUCT_ID = 1
FOR UPDATE;

-- Sau khi lock, có thể update an toàn trong cùng transaction.
-- UPDATE PRODUCTS SET STATUS = 'READY_FOR_TRANSPORT' WHERE PRODUCT_ID = 1;
-- COMMIT;
