-- ============================================================
-- 03_triggers.sql - Triggers for integrity, audit, append-only logs
-- ============================================================
-- Mục tiêu khi trình bày DBMS:
-- 1) Trigger bảo vệ bảng log không bị sửa/xóa để giữ tính toàn vẹn lịch sử.
-- 2) Trigger tự cập nhật UPDATED_AT, tránh quên cập nhật thời gian chỉnh sửa ở Java/UI.
-- 3) Trigger tự ghi lịch sử khi trạng thái lô hàng đổi, giúp trace không phụ thuộc hoàn toàn backend.
-- Lưu ý: Trigger không COMMIT. Transaction do procedure/service gọi bên ngoài quyết định.
-- ============================================================

-- TRG_AUDIT_APPEND_ONLY
-- Chức năng:
-- - Chặn UPDATE hoặc DELETE trên AUDIT_LOGS.
-- - AUDIT_LOGS là bảng nhật ký kiểm toán, chỉ được INSERT thêm dòng mới.
-- Ý nghĩa:
-- - Tránh việc người dùng/admin chỉnh sửa hoặc xóa dấu vết nghiệp vụ sau khi thao tác.
-- - Phù hợp yêu cầu truy xuất nguồn gốc: lịch sử phải minh bạch, không bị ghi đè.
CREATE OR REPLACE TRIGGER TRG_AUDIT_APPEND_ONLY
BEFORE UPDATE OR DELETE ON AUDIT_LOGS
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'AUDIT_LOGS is append-only. Update/Delete is not allowed.');
END;
/

-- TRG_SCAN_LOG_APPEND_ONLY
-- Chức năng:
-- - Chặn UPDATE hoặc DELETE trên QR_SCAN_LOGS.
-- - QR_SCAN_LOGS lưu từng lần quét QR của khách hàng/cửa hàng/admin.
-- Ý nghĩa:
-- - Nếu QR bị copy/cloned, số lần quét, thời điểm quét, IP/user-agent là bằng chứng quan trọng.
-- - Vì vậy bảng scan log chỉ được thêm mới, không cho sửa/xóa.
CREATE OR REPLACE TRIGGER TRG_SCAN_LOG_APPEND_ONLY
BEFORE UPDATE OR DELETE ON QR_SCAN_LOGS
BEGIN
    RAISE_APPLICATION_ERROR(-20002, 'QR_SCAN_LOGS is append-only. Update/Delete is not allowed.');
END;
/

-- TRG_PRODUCTS_UPDATED_AT
-- Chức năng:
-- - Trước mỗi UPDATE trên PRODUCTS, tự gán UPDATED_AT = CURRENT_TIMESTAMP.
-- Ý nghĩa:
-- - Đảm bảo dữ liệu có thời điểm cập nhật cuối cùng chính xác.
-- - Backend không cần nhớ set UPDATED_AT ở từng API.
CREATE OR REPLACE TRIGGER TRG_PRODUCTS_UPDATED_AT
BEFORE UPDATE ON PRODUCTS
FOR EACH ROW
BEGIN
    :NEW.UPDATED_AT := CURRENT_TIMESTAMP;
END;
/

-- TRG_PRODUCTS_STATUS_AUDIT
-- Chức năng:
-- - Sau khi STATUS của PRODUCTS thay đổi, tự insert:
--   + BATCH_STATUS_HISTORY: lịch sử trạng thái phục vụ trace timeline.
--   + AUDIT_LOGS: nhật ký kiểm toán phục vụ kiểm tra sau này.
-- Ý nghĩa:
-- - Dù trạng thái đổi từ Java service, SQL script hay procedure, DB vẫn ghi nhận lịch sử.
-- - Hỗ trợ truy xuất các bước: CREATED -> READY_FOR_TRANSPORT -> IN_TRANSIT -> AVAILABLE_FOR_SALE.
-- Lưu ý:
-- - Procedure cũng có thể ghi history/audit chi tiết hơn. Trigger này là lớp bảo vệ bổ sung.
CREATE OR REPLACE TRIGGER TRG_PRODUCTS_STATUS_AUDIT
AFTER UPDATE OF STATUS ON PRODUCTS
FOR EACH ROW
BEGIN
    INSERT INTO BATCH_STATUS_HISTORY(PRODUCT_ID, OLD_STATUS, NEW_STATUS, CHANGED_AT, NOTE)
    VALUES(:OLD.PRODUCT_ID, :OLD.STATUS, :NEW.STATUS, CURRENT_TIMESTAMP, 'Auto status audit by trigger');

    INSERT INTO AUDIT_LOGS(TABLE_NAME, RECORD_ID, ACTION_TYPE, OLD_DATA, NEW_DATA, PERFORMED_BY)
    VALUES('PRODUCTS', TO_CHAR(:OLD.PRODUCT_ID), 'STATUS_CHANGE', :OLD.STATUS, :NEW.STATUS, 'DB_TRIGGER');
END;
/
